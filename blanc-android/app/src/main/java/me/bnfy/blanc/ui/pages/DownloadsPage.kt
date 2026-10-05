package me.bnfy.blanc.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.bnfy.blanc.R
import me.bnfy.blanc.storage.DownloadEntity

/**
 * Downloads page
 */
@Composable
fun DownloadsPage(
    onBack: () -> Unit,
    downloads: List<DownloadEntity> = getSampleDownloads(),
    onOpenFile: (DownloadEntity) -> Unit,
    onShowInFolder: (DownloadEntity) -> Unit,
    onCancel: (DownloadEntity) -> Unit,
    onRetry: (DownloadEntity) -> Unit,
    onClearCompleted: () -> Unit
) {
    val filteredDownloads = downloads.filter { it.state != DownloadEntity.STATE_PENDING }
    
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
                                .combinedClickable(onClick = onClearCompleted)
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
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredDownloads) { download ->
                        DownloadItemCard(
                            download = download,
                            onOpen = { onOpenFile(download) },
                            onShowInFolder = { onShowInFolder(download) },
                            onCancel = { onCancel(download) },
                            onRetry = { onRetry(download) }
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
        modifier = Modifier
            .fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = download.url,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                ProgressIndicator(
                    progress = progress / 100f,
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
                        Text("Open")
                    }
                    Button(onClick = onShowInFolder) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = null)
                        Text("Show in folder")
                    }
                } else if (isFailed) {
                    Button(onClick = onRetry) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = null)
                        Text("Retry")
                    }
                    Button(onClick = onCancel, colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )) {
                        Text("Dismiss")
                    }
                } else if (isCancelled) {
                    Button(onClick = onRetry) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = null)
                        Text("Retry")
                    }
                }
                
                if (isComplete || isFailed || isCancelled) {
                    Button(onClick = onCancel, colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                        Text("Remove")
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
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
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

fun getSampleDownloads(): List<DownloadEntity> = listOf(
    DownloadEntity(
        id = "1",
        windowId = "default",
        tabId = "tab1",
        url = "https://example.com/file.pdf",
        fileName = "document.pdf",
        mimeType = "application/pdf",
        totalBytes = 2048576,
        receivedBytes = 2048576,
        targetPath = "/storage/emulated/0/Download/Blanc/document.pdf",
        state = DownloadEntity.STATE_COMPLETED,
        startedAt = System.currentTimeMillis() - 3600000,
        completedAt = System.currentTimeMillis() - 3500000
    ),
    DownloadEntity(
        id = "2",
        windowId = "default",
        tabId = "tab2",
        url = "https://example.com/image.png",
        fileName = "photo.png",
        mimeType = "image/png",
        totalBytes = 1048576,
        receivedBytes = 524288,
        state = DownloadEntity.STATE_IN_PROGRESS,
        startedAt = System.currentTimeMillis() - 60000
    ),
    DownloadEntity(
        id = "3",
        windowId = "default",
        tabId = "tab3",
        url = "https://example.com/broken.zip",
        fileName = "archive.zip",
        mimeType = "application/zip",
        totalBytes = 0,
        state = DownloadEntity.STATE_FAILED,
        error = "Connection timeout",
        startedAt = System.currentTimeMillis() - 7200000
    )
)