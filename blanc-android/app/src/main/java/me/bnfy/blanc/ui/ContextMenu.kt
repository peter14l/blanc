package me.bnfy.blanc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.bnfy.blanc.R

/**
 * The long-press target, resolved from the WebView hit-test result.
 */
data class ContextMenuTarget(
    val linkUrl: String? = null,
    val imageUrl: String? = null
)

/**
 * Mobile-adapted long-press context menu, mirroring the desktop ContextMenu's
 * action set (navigation, link/image actions, tab + page actions, settings).
 */
@Composable
fun ContextMenuOverlay(
    target: ContextMenuTarget,
    pageUrl: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onNewTab: () -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onCopy: (String) -> Unit,
    onSaveImage: (String) -> Unit,
    onDuplicateTab: () -> Unit,
    onCloseTab: () -> Unit,
    onPrint: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContextQuickAction(R.drawable.ic_back, "Back", canGoBack) {
                        onBack(); onDismiss()
                    }
                    ContextQuickAction(R.drawable.ic_forward, "Forward", canGoForward) {
                        onForward(); onDismiss()
                    }
                    ContextQuickAction(R.drawable.ic_refresh, "Reload", true) {
                        onReload(); onDismiss()
                    }
                    ContextQuickAction(R.drawable.ic_add, "New tab", true) {
                        onNewTab(); onDismiss()
                    }
                }
                ContextDivider()

                val link = target.linkUrl
                if (!link.isNullOrBlank()) {
                    ContextMenuItem(R.drawable.ic_launch, "Open link in new tab") {
                        onOpenInNewTab(link); onDismiss()
                    }
                    ContextMenuItem(R.drawable.ic_copy, "Copy link address") {
                        onCopy(link); onDismiss()
                    }
                    ContextDivider()
                }

                val image = target.imageUrl
                if (!image.isNullOrBlank()) {
                    ContextMenuItem(R.drawable.ic_image, "Open image in new tab") {
                        onNavigate(image); onDismiss()
                    }
                    ContextMenuItem(R.drawable.ic_download, "Save image") {
                        onSaveImage(image); onDismiss()
                    }
                    ContextDivider()
                }

                if (pageUrl.isNotBlank()) {
                    ContextMenuItem(R.drawable.ic_copy, "Copy page URL") {
                        onCopy(pageUrl); onDismiss()
                    }
                }
                ContextMenuItem(R.drawable.ic_tab, "Duplicate tab") {
                    onDuplicateTab(); onDismiss()
                }
                ContextMenuItem(R.drawable.ic_print, "Print…") {
                    onPrint(); onDismiss()
                }
                ContextMenuItem(R.drawable.ic_close, "Close tab") {
                    onCloseTab(); onDismiss()
                }
                ContextDivider()
                ContextMenuItem(R.drawable.ic_settings, "Settings") {
                    onOpenSettings(); onDismiss()
                }
            }
        }
    }
}

@Composable
private fun ContextDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun ContextQuickAction(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Column(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = tint
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun ContextMenuItem(
    icon: Int,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
