package me.bnfy.blanc.ui.pages

import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.launch
import me.bnfy.blanc.BlancApplication
import me.bnfy.blanc.R
import me.bnfy.blanc.download.DownloadService
import me.bnfy.blanc.storage.DownloadEntity
import me.bnfy.blanc.storage.Repository
import java.io.File

/**
 * Downloads page backed by Room database and foreground DownloadService.
 */
@Composable
fun DownloadsPage(
    onBack: () -> Unit,
    repository: Repository = BlancApplication.getInstance().repository,
    downloads: List<DownloadEntity>? = null,
    onOpenFile: ((DownloadEntity) -> Unit)? = null,
    onShowInFolder: ((DownloadEntity) -> Unit)? = null,
    onCancel: ((DownloadEntity) -> Unit)? = null,
    onRetry: ((DownloadEntity) -> Unit)? = null,
    onClearCompleted: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val dbDownloads by repository.downloadDao.getByProfileFlow("personal").collectAsState(initial = emptyList())
    val actualDownloads = downloads ?: dbDownloads

    val handleOpenFile: (DownloadEntity) -> Unit = onOpenFile ?: { download ->
        val path = download.targetPath.ifEmpty { download.savePath }
        val file = File(path)
        if (file.exists()) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, download.mimeType.ifEmpty { "*/*" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "File not found: ${download.fileName}", Toast.LENGTH_SHORT).show()
        }
    }

    val handleShowInFolder: (DownloadEntity) -> Unit = onShowInFolder ?: {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Saved to Downloads folder", Toast.LENGTH_SHORT).show()
        }
    }

    val handleCancel: (DownloadEntity) -> Unit = onCancel ?: { download ->
        coroutineScope.launch {
            repository.downloadDao.cancel(download.id, System.currentTimeMillis())
        }
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, download.id)
        }
        context.startService(intent)
    }

    val handleRetry: (DownloadEntity) -> Unit = onRetry ?: { download ->
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RETRY
            putExtra(DownloadService.EXTRA_DOWNLOAD_JSON, Gson().toJson(download))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    val handleClearCompleted: () -> Unit = onClearCompleted ?: {
        coroutineScope.launch {
            repository.downloadDao.clearCompletedByProfile("personal")
        }
    }

    val filteredDownloads = actualDownloads.filter { it.state != DownloadEntity.STATE_PENDING }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Toolbar
            Toolbar(
                title = "Downloads",
                onBack = onBack,
                actions = {
                    if (filteredDownloads.any { it.state == DownloadEntity.STATE_COMPLETED }) {
                        Text(
                            text = "Clear completed",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp)
                                .wrapContentWidth()
                                .clickable(onClick = handleClearCompleted)
                        )
                    }
                }
            )

            // Downloads list
            if (filteredDownloads.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_download,
                    title = "No downloads",
                    subtitle = "Your downloaded files will appear here"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(vertical = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredDownloads, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            onOpen = { handleOpenFile(download) },
                            onShowInFolder = { handleShowInFolder(download) },
                            onCancel = { handleCancel(download) },
                            onRetry = { handleRetry(download) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    download: DownloadEntity,
    onOpen: () -> Unit,
    onShowInFolder: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val isComplete = download.state == DownloadEntity.STATE_COMPLETED
    val isInProgress = download.state == DownloadEntity.STATE_IN_PROGRESS
    val isFailed = download.state == DownloadEntity.STATE_FAILED
    val isCancelled = download.state == DownloadEntity.STATE_CANCELLED

    val progress = if (download.totalBytes > 0) {
        (download.receivedBytes * 100 / download.totalBytes).toInt()
    } else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = download.url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                // Status badge
                DownloadStatusBadge(
                    state = download.state,
                    progress = progress
                )
            }

            // Progress bar (for in-progress)
            if (isInProgress) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatBytes(download.receivedBytes) + " / " + formatBytes(download.totalBytes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    IconButton(onClick = onCancel) {
                        Icon(painterResource(R.drawable.ic_cancel), contentDescription = "Cancel")
                    }
                }
            }

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isComplete) {
                    Button(onClick = onOpen) {
                        Icon(painterResource(R.drawable.ic_launch), contentDescription = null)
                        Text("Open", modifier = Modifier.padding(start = 4.dp))
                    }
                    Button(onClick = onShowInFolder) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = null)
                        Text("Show in folder", modifier = Modifier.padding(start = 4.dp))
                    }
                } else if (isFailed) {
                    Button(onClick = onRetry) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = null)
                        Text("Retry", modifier = Modifier.padding(start = 4.dp))
                    }
                } else if (isCancelled) {
                    Button(onClick = onRetry) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = null)
                        Text("Retry", modifier = Modifier.padding(start = 4.dp))
                    }
                }

                if (isComplete || isFailed || isCancelled) {
                    Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                        Text("Remove", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadStatusBadge(state: Int, progress: Int) {
    val (text, color) = when (state) {
        DownloadEntity.STATE_IN_PROGRESS -> "$progress%" to MaterialTheme.colorScheme.primary
        DownloadEntity.STATE_COMPLETED -> "Complete" to MaterialTheme.colorScheme.tertiary
        DownloadEntity.STATE_FAILED -> "Failed" to MaterialTheme.colorScheme.error
        DownloadEntity.STATE_CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "Pending" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp)),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}