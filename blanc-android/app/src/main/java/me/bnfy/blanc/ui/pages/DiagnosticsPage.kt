package me.bnfy.blanc.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.bnfy.blanc.BuildConfig
import me.bnfy.blanc.R
import me.bnfy.blanc.adblock.AdblockEngine
import me.bnfy.blanc.storage.Repository
import me.bnfy.blanc.tab.Tab

/**
 * Diagnostics page — read-only runtime report mirroring the Tauri
 * `DiagnosticsPage`. Surfaces tab/engine/settings state so support requests can
 * be answered without guessing. Purely observational: no side effects.
 */
@Composable
fun DiagnosticsPage(
    repository: Repository,
    adblockEngine: AdblockEngine,
    tabs: List<Tab>,
    onBack: () -> Unit
) {
    val adblockEnabled by repository.adblockEnabled.collectAsState(initial = true)
    val searchEngine by repository.defaultSearchEngine.collectAsState(initial = "duckduckgo")
    val theme by repository.theme.collectAsState(initial = "system")
    val startupBehavior by repository.startupBehavior.collectAsState(initial = "newtab")
    val quietTabsDelay by repository.quietTabsDelay.collectAsState(initial = "1h")
    val doNotTrack by repository.doNotTrack.collectAsState(initial = true)
    val blockThirdPartyCookies by repository.blockThirdPartyCookies.collectAsState(initial = true)
    val clearOnExit by repository.clearOnExit.collectAsState(initial = false)
    val syncEnabled by repository.syncEnabled.collectAsState(initial = false)
    val patronActive by repository.patronActive.collectAsState(initial = false)

    val stats = remember(tabs, adblockEnabled) { adblockEngine.getStats() }

    val privateCount = tabs.count { it.isPrivate }
    val pinnedCount = tabs.count { it.isPinned }
    val mutedCount = tabs.count { it.isMuted }
    val sleepingCount = tabs.count { it.isSleeping }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                DiagnosticSection("Engine") {
                    DiagnosticRow("Ad blocking", if (adblockEnabled) "Enabled" else "Disabled")
                    DiagnosticRow("Engine initialized", if (adblockEngine.isInitialized) "Yes" else "No")
                    DiagnosticRow("Network filters", stats.networkFilterCount.toString())
                    DiagnosticRow("Exception filters", stats.exceptionFilterCount.toString())
                    DiagnosticRow("Cosmetic filters", stats.cosmeticFilterCount.toString())
                    DiagnosticRow("Total blocked", stats.totalBlocked.toString())
                    DiagnosticRow("Blocked today", stats.todayBlocked.toString())
                }
            }
            item {
                DiagnosticSection("Tabs") {
                    DiagnosticRow("Open tabs", tabs.size.toString())
                    DiagnosticRow("Private tabs", privateCount.toString())
                    DiagnosticRow("Pinned tabs", pinnedCount.toString())
                    DiagnosticRow("Muted tabs", mutedCount.toString())
                    DiagnosticRow("Quiet (sleeping)", sleepingCount.toString())
                }
            }
            item {
                DiagnosticSection("Settings") {
                    DiagnosticRow("Search engine", searchEngine)
                    DiagnosticRow("Theme", theme)
                    DiagnosticRow("Startup", startupBehavior)
                    DiagnosticRow("Quiet tabs delay", quietTabsDelay)
                    DiagnosticRow("Do Not Track", if (doNotTrack) "On" else "Off")
                    DiagnosticRow(
                        "Block 3rd-party cookies",
                        if (blockThirdPartyCookies) "On" else "Off"
                    )
                    DiagnosticRow("Clear on exit", if (clearOnExit) "On" else "Off")
                    DiagnosticRow("Blanc Sync", if (syncEnabled) "Enabled" else "Disabled")
                    DiagnosticRow("Patron", if (patronActive) "Active" else "Inactive")
                }
            }
            item {
                DiagnosticSection("About") {
                    DiagnosticRow(
                        "Version",
                        "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}
