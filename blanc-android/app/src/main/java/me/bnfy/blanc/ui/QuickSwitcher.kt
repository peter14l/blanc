package me.bnfy.blanc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.bnfy.blanc.R
import me.bnfy.blanc.storage.Favorite
import me.bnfy.blanc.storage.HistoryEntry
import me.bnfy.blanc.tab.Tab

/**
 * A single row in the command palette. Rows are rebuilt from the current query,
 * so the [action] lambda always closes over the correct target.
 */
private data class PaletteEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: Int,
    val action: () -> Unit
)

/**
 * Quick Switcher / command palette, the Compose counterpart of the Tauri
 * `QuickSwitcher`. Matches across open tabs, favorites, history and slash
 * commands. Substring matching only — no ranking heuristics, matching the
 * rest of the Android surface.
 */
@Composable
fun QuickSwitcherOverlay(
    tabs: List<Tab>,
    favorites: List<Favorite>,
    history: List<HistoryEntry>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onRunCommand: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val normalized = query.trim().lowercase()

    val entries: List<PaletteEntry> = remember(normalized, tabs, favorites, history, activeTabId) {
        val matches: (String?, String) -> Boolean = { haystack, needle ->
            needle.isBlank() || (haystack ?: "").lowercase().contains(needle)
        }

        if (normalized.startsWith("/")) {
            // Slash-command mode: only commands.
            SlashCommands.filter(normalized).map { cmd ->
                PaletteEntry(
                    key = "cmd_${cmd.command}",
                    title = cmd.command,
                    subtitle = cmd.description,
                    icon = R.drawable.ic_launch,
                    action = { onRunCommand(cmd.command) }
                )
            }
        } else {
            val rows = ArrayList<PaletteEntry>()

            // 1. Open tabs.
            val tabRows = tabs
                .filter { matches(it.title, normalized) || matches(it.url, normalized) }
                .take(if (normalized.isBlank()) 8 else 20)
                .map { tab ->
                    PaletteEntry(
                        key = "tab_${tab.id}",
                        title = tab.title.ifBlank { tab.url.ifBlank { "New tab" } },
                        subtitle = if (tab.id == activeTabId) "Current tab" else tab.url,
                        icon = if (tab.isPrivate) R.drawable.ic_private_tab else R.drawable.ic_tab,
                        action = { onSelectTab(tab.id) }
                    )
                }
            rows.addAll(tabRows)

            // 2. Favorites.
            val favoriteRows = favorites
                .filter { matches(it.title, normalized) || matches(it.url, normalized) }
                .take(if (normalized.isBlank()) 5 else 15)
                .map { fav ->
                    PaletteEntry(
                        key = "fav_${fav.id}",
                        title = fav.title.ifBlank { fav.url },
                        subtitle = fav.url,
                        icon = R.drawable.ic_bookmark,
                        action = { onNavigate(fav.url) }
                    )
                }
            rows.addAll(favoriteRows)

            // 3. History.
            val historyRows = history
                .filter { matches(it.title, normalized) || matches(it.url, normalized) }
                .take(if (normalized.isBlank()) 6 else 15)
                .map { entry ->
                    PaletteEntry(
                        key = "hist_${entry.id}",
                        title = entry.title?.ifBlank { entry.url } ?: entry.url,
                        subtitle = entry.url,
                        icon = R.drawable.ic_history,
                        action = { onNavigate(entry.url) }
                    )
                }
            rows.addAll(historyRows)

            rows
        }
    }

    val runEntry: (PaletteEntry) -> Unit = { entry ->
        entry.action()
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 48.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { /* Consume taps so the scrim doesn't dismiss. */ },
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 10.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Search field.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        placeholder = { Text("Search tabs, favorites, history or /commands") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = { entries.firstOrNull()?.let(runEntry) }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Close"
                        )
                    }
                }

                HorizontalDivider()

                if (entries.isEmpty()) {
                    Text(
                        text = if (normalized.isBlank()) {
                            "Start typing to search, or type / for commands"
                        } else {
                            "No results"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        items(entries, key = { it.key }) { entry ->
                            PaletteRow(entry = entry, onClick = { runEntry(entry) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(
    entry: PaletteEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(entry.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
        }
    }
}
