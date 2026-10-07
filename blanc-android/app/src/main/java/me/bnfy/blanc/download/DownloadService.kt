package me.bnfy.blanc.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bnfy.blanc.BuildConfig
import me.bnfy.blanc.R
import me.bnfy.blanc.BlancApplication
import me.bnfy.blanc.bridge.BridgeProtocol
import me.bnfy.blanc.storage.DownloadEntity
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Foreground service for handling downloads.
 * Provides progress notifications and manages download lifecycle.
 */
class DownloadService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val gson = Gson()
    private var notificationManager: NotificationManagerCompat? = null
    private var currentDownloadId: String? = null
    private var currentNotificationId = 1000
    private var isCancelled = false
    private var currentOutputStream: FileOutputStream? = null
    private var currentConnection: HttpURLConnection? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_NOT_STICKY
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action ?: return
        
        when (action) {
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_DOWNLOAD_JSON)
                val downloadEntity = json?.let { gson.fromJson(it, DownloadEntity::class.java) }
                downloadEntity?.let { startDownload(it) }
            }
            ACTION_CANCEL -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                downloadId?.let { cancelDownload(it) }
            }
            ACTION_PAUSE -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                downloadId?.let { pauseDownload(it) }
            }
            ACTION_RETRY -> {
                val json = intent.getStringExtra(EXTRA_DOWNLOAD_JSON)
                val downloadEntity = json?.let { gson.fromJson(it, DownloadEntity::class.java) }
                downloadEntity?.let { retryDownload(it) }
            }
        }
    }

    private fun startDownload(entity: DownloadEntity) {
        currentDownloadId = entity.id
        isCancelled = false
        var current = entity

        executor.execute {
            try {
                val url = URL(current.url)
                val connection = url.openConnection() as HttpURLConnection
                currentConnection = connection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 30000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    
                    // Add cookies if available
                    val cookieManager = CookieManager.getInstance()
                    val cookies = cookieManager.getCookie(current.url)
                    if (!cookies.isNullOrBlank()) {
                        setRequestProperty("Cookie", cookies)
                    }
                    
                    // Add user agent
                    setRequestProperty("User-Agent", "Blanc/Android")
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("HTTP $responseCode")
                }

                val contentLength = connection.contentLengthLong
                val contentDisposition = connection.getHeaderField("Content-Disposition")
                val fileName = extractFileName(contentDisposition, current.url, current.fileName)
                
                // Determine download directory
                val downloadDir = getDownloadDirectory()
                val targetFile = File(downloadDir, fileName).also { 
                    if (it.exists()) it.delete() 
                }
                
                // Update entity with target path
                current = current.copy(
                    targetPath = targetFile.absolutePath,
                    totalBytes = contentLength,
                    state = DownloadEntity.STATE_IN_PROGRESS
                )
                updateDownloadInRepo(current)
                
                // Open streams
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(targetFile)
                currentOutputStream = outputStream
                
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var lastProgressUpdate = 0L
                
                while (!isCancelled) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    
                    outputStream.write(buffer, 0, read)
                    downloaded += read
                    
                    // Update progress every ~100ms or 1MB
                    if (System.currentTimeMillis() - lastProgressUpdate > 100 || downloaded - current.receivedBytes > 1024 * 1024) {
                        current = current.copy(receivedBytes = downloaded)
                        updateDownloadInRepo(current)
                        updateNotificationProgress(current)
                        lastProgressUpdate = System.currentTimeMillis()
                    }
                }
                
                if (isCancelled) {
                    targetFile.delete()
                    current = current.copy(state = DownloadEntity.STATE_CANCELLED)
                    updateDownloadInRepo(current)
                    hideNotification()
                } else {
                    outputStream.flush()
                    current = current.copy(
                        receivedBytes = downloaded,
                        state = DownloadEntity.STATE_COMPLETED,
                        completedAt = System.currentTimeMillis()
                    )
                    updateDownloadInRepo(current)
                    showCompletionNotification(current)
                    
                    // Notify bridge
                    mainHandler.post {
                        BlancApplication.getInstance().blancBridge.emitEvent(
                            BridgeProtocol.Events.DOWNLOAD_UPDATED,
                            DownloadBuilder.fromDownload(current)
                        )
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Download failed: ${current.id}")
                if (!isCancelled) {
                    current = current.copy(
                        state = DownloadEntity.STATE_FAILED,
                        error = e.message
                    )
                    updateDownloadInRepo(current)
                    showErrorNotification(current)
                }
            } finally {
                closeResources()
                currentDownloadId = null
            }
        }
    }

    private fun cancelDownload(downloadId: String) {
        if (downloadId == currentDownloadId) {
            isCancelled = true
        }
    }

    private fun pauseDownload(downloadId: String) {
        if (downloadId == currentDownloadId) {
            isCancelled = true
        }
    }

    private fun retryDownload(entity: DownloadEntity) {
        val reset = entity.copy(
            state = DownloadEntity.STATE_PENDING,
            receivedBytes = 0,
            error = null,
            startedAt = System.currentTimeMillis()
        )
        updateDownloadInRepo(reset)
        startDownload(reset)
    }

    private fun extractFileName(contentDisposition: String?, url: String, fallback: String): String {
        contentDisposition?.let {
            val filenameRegex = """filename\*?=([^;]+)""".toRegex()
            val match = filenameRegex.find(it)
            match?.groupValues?.get(1)?.let { filename ->
                return URLDecoder.decode(filename.trim().trim('"'), "UTF-8")
            }
        }
        
        // Try to extract from URL
        val urlFileName = URLUtil.guessFileName(url, null, null)
        return if (urlFileName.isNotBlank()) urlFileName else fallback
    }

    private fun getDownloadDirectory(): File {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(publicDir, "Blanc")
        if (!appDir.exists()) appDir.mkdirs()
        return appDir
    }

    private fun updateDownloadInRepo(entity: DownloadEntity) {
        serviceScope.launch {
            BlancApplication.getInstance().repository.downloadDao.insert(entity)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "downloads",
                "Downloads",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotificationProgress(entity: DownloadEntity) {
        val progress = if (entity.totalBytes > 0) {
            (entity.receivedBytes * 100 / entity.totalBytes).toInt()
        } else {
            0
        }
        
        val formattedSize = formatBytes(entity.receivedBytes)
        val totalSize = if (entity.totalBytes > 0) formatBytes(entity.totalBytes) else "Unknown"
        
        val notification = NotificationCompat.Builder(this, "downloads")
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(entity.fileName)
            .setContentText("$formattedSize / $totalSize")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_cancel,
                    "Cancel",
                    createCancelPendingIntent(entity.id)
                ).build()
            )
            .build()
        
        notificationManager?.notify(currentNotificationId, notification)
    }

    private fun showCompletionNotification(entity: DownloadEntity) {
        val notification = NotificationCompat.Builder(this, "downloads")
            .setSmallIcon(R.drawable.ic_download_complete)
            .setContentTitle("Download complete")
            .setContentText(entity.fileName)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(createOpenFilePendingIntent(entity))
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_folder,
                    "Show in folder",
                    createShowInFolderPendingIntent(entity)
                ).build()
            )
            .build()
        
        notificationManager?.notify(currentNotificationId, notification)
    }

    private fun showErrorNotification(entity: DownloadEntity) {
        val notification = NotificationCompat.Builder(this, "downloads")
            .setSmallIcon(R.drawable.ic_download_error)
            .setContentTitle("Download failed")
            .setContentText(entity.fileName)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_retry,
                    "Retry",
                    createRetryPendingIntent(entity)
                ).build()
            )
            .build()
        
        notificationManager?.notify(currentNotificationId, notification)
    }

    private fun hideNotification() {
        notificationManager?.cancel(currentNotificationId)
    }

    private fun createCancelPendingIntent(downloadId: String): android.app.PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        return android.app.PendingIntent.getService(
            this, 0, intent, 
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createOpenFilePendingIntent(entity: DownloadEntity): android.app.PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val file = File(entity.targetPath ?: "")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this@DownloadService,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )
            setDataAndType(uri, entity.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createShowInFolderPendingIntent(entity: DownloadEntity): android.app.PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val file = File(entity.targetPath ?: "").parentFile!!
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this@DownloadService,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createRetryPendingIntent(entity: DownloadEntity): android.app.PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_RETRY
            putExtra(EXTRA_DOWNLOAD_JSON, gson.toJson(entity))
        }
        return android.app.PendingIntent.getService(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun closeResources() {
        try {
            currentOutputStream?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing output stream")
        }
        try {
            currentConnection?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting")
        }
        currentOutputStream = null
        currentConnection = null
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "me.bnfy.blanc.download.START"
        const val ACTION_CANCEL = "me.bnfy.blanc.download.CANCEL"
        const val ACTION_PAUSE = "me.bnfy.blanc.download.PAUSE"
        const val ACTION_RETRY = "me.bnfy.blanc.download.RETRY"
        
        const val EXTRA_DOWNLOAD_JSON = "download_json"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}