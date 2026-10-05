package me.bnfy.blanc.download

import android.app.DownloadManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import me.bnfy.blanc.BlancApplication
import me.bnfy.blanc.bridge.BlancBridge
import me.bnfy.blanc.bridge.BridgeProtocol
import me.bnfy.blanc.download.DownloadBuilder
import me.bnfy.blanc.storage.DownloadEntity
import me.bnfy.blanc.storage.Repository
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Foreground service for handling downloads.
 * Provides progress notifications and manages download lifecycle.
 */
class DownloadService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
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
            DownloadService.ACTION_START -> {
                val downloadEntity = intent.getParcelableExtra<DownloadEntity>(EXTRA_DOWNLOAD)
                downloadEntity?.let { startDownload(it) }
            }
            DownloadService.ACTION_CANCEL -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                downloadId?.let { cancelDownload(it) }
            }
            DownloadService.ACTION_PAUSE -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                downloadId?.let { pauseDownload(it) }
            }
            DownloadService.ACTION_RETRY -> {
                val downloadEntity = intent.getParcelableExtra<DownloadEntity>(EXTRA_DOWNLOAD)
                downloadEntity?.let { retryDownload(it) }
            }
        }
    }

    private fun startDownload(entity: DownloadEntity) {
        currentDownloadId = entity.id
        isCancelled = false

        executor.execute {
            try {
                val url = URL(entity.url)
                currentConnection = url.openConnection() as HttpURLConnection
                currentConnection.apply {
                    requestMethod = "GET"
                    connectTimeout = 30000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    
                    // Add cookies if available
                    val cookieManager = CookieManager.getInstance()
                    val cookies = cookieManager.getCookie(entity.url)
                    if (!cookies.isNullOrBlank()) {
                        setRequestProperty("Cookie", cookies)
                    }
                    
                    // Add user agent
                    setRequestProperty("User-Agent", "Blanc/Android")
                }

                val responseCode = currentConnection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("HTTP $responseCode")
                }

                val contentLength = currentConnection.contentLengthLong
                val contentDisposition = currentConnection.getHeaderField("Content-Disposition")
                val fileName = extractFileName(contentDisposition, entity.url, entity.fileName)
                
                // Determine download directory
                val downloadDir = getDownloadDirectory()
                val targetFile = File(downloadDir, fileName).also { 
                    if (it.exists()) it.delete() 
                }
                
                // Update entity with target path
                entity.targetPath = targetFile.absolutePath
                entity.totalBytes = contentLength
                entity.state = DownloadEntity.STATE_IN_PROGRESS
                
                // Persist update
                updateDownloadInRepo(entity)
                
                // Open streams
                val inputStream = currentConnection.inputStream
                currentOutputStream = FileOutputStream(targetFile)
                
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var lastProgressUpdate = 0L
                
                while (!isCancelled) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    
                    currentOutputStream.write(buffer, 0, read)
                    downloaded += read
                    
                    // Update progress every ~100ms or 1MB
                    if (System.currentTimeMillis() - lastProgressUpdate > 100 || downloaded - entity.receivedBytes > 1024 * 1024) {
                        entity.receivedBytes = downloaded
                        updateDownloadInRepo(entity)
                        updateNotificationProgress(entity)
                        lastProgressUpdate = System.currentTimeMillis()
                    }
                }
                
                if (isCancelled) {
                    targetFile.delete()
                    entity.state = DownloadEntity.STATE_CANCELLED
                    updateDownloadInRepo(entity)
                    hideNotification()
                } else {
                    currentOutputStream?.flush()
                    entity.receivedBytes = downloaded
                    entity.state = DownloadEntity.STATE_COMPLETED
                    entity.completedAt = System.currentTimeMillis()
                    updateDownloadInRepo(entity)
                    showCompletionNotification(entity)
                    
                    // Notify bridge
                    mainHandler.post {
                        BlancApplication.getInstance().blancBridge.emitEvent(
                            BridgeProtocol.Events.DOWNLOAD_UPDATED,
                            DownloadBuilder.fromDownload(entity)
                        )
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Download failed: ${entity.id}")
                if (!isCancelled) {
                    entity.state = DownloadEntity.STATE_FAILED
                    entity.error = e.message
                    updateDownloadInRepo(entity)
                    showErrorNotification(entity)
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
        // For simplicity, treat pause as cancel (can be resumed via retry)
        if (downloadId == currentDownloadId) {
            isCancelled = true
            // State will be updated to CANCELLED, user can retry
        }
    }

    private fun retryDownload(entity: DownloadEntity) {
        // Reset entity for retry
        entity.state = DownloadEntity.STATE_PENDING
        entity.receivedBytes = 0
        entity.error = null
        entity.startedAt = System.currentTimeMillis()
        updateDownloadInRepo(entity)
        startDownload(entity)
    }

    private fun extractFileName(contentDisposition: String?, url: String, fallback: String): String {
        contentDisposition?.let {
            val filenameRegex = """filename\*?=([^;]+)""".toRegex()
            val match = filenameRegex.find(it)
            match?.groupValues?.get(1)?.let { filename ->
                return URLUtil.decode(filename.trim().trim('"'))
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
        mainHandler.post {
            BlancApplication.getInstance().scope.launch {
                BlancApplication.getInstance().repository.downloadDao.insert(entity)
            }
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
            action = DownloadService.ACTION_CANCEL
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
            action = DownloadService.ACTION_RETRY
            putExtra(EXTRA_DOWNLOAD, entity)
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
        
        const val EXTRA_DOWNLOAD = "download"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}