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
import me.bnfy.blanc.storage.Repository

/**
 * Settings page
 */
@Composable
fun SettingsPage(
    repository: Repository,
    onBack: () -> Unit
) {
    val settings = remember { mutableStateOf<SettingsState>(getDefaultSettings()) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Toolbar
            Toolbar(title = "Settings", onBack = onBack)
            
            // Settings list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // General section
                SettingsSection(title = "General") {
                    SettingsItem(
                        title = "Search engine",
                        subtitle = settings.value.searchEngine,
                        icon = R.drawable.ic_search,
                        onClick = { /* Open search engine picker */ }
                    )
                    
                    SettingsItem(
                        title = "Homepage",
                        subtitle = settings.value.homepage,
                        icon = R.drawable.ic_home,
                        onClick = { /* Open homepage picker */ }
                    )
                    
                    SettingsItem(
                        title = "Startup behavior",
                        subtitle = settings.value.startupBehavior,
                        icon = R.drawable.ic_launch,
                        onClick = { /* Open startup picker */ }
                    )
                    
                    SettingsItem(
                        title = "Quiet tabs",
                        subtitle = settings.value.quietTabsDelay,
                        icon = R.drawable.ic_tab,
                        onClick = { /* Open quiet tabs picker */ }
                    )
                }
                
                // Privacy section
                SettingsSection(title = "Privacy & Security") {
                    SettingsToggle(
                        title = "Block ads & trackers",
                        subtitle = "Block ads, trackers, and malicious scripts",
                        icon = R.drawable.ic_shield,
                        isEnabled = settings.value.adblockEnabled,
                        onChange = { settings.value = settings.value.copy(adblockEnabled = it) }
                    )
                    
                    SettingsItem(
                        title = "Adblock exceptions",
                        subtitle = "${settings.value.adblockExceptions.size} sites",
                        icon = R.drawable.ic_shield,
                        onClick = { /* Open exceptions list */ }
                    )
                    
                    SettingsToggle(
                        title = "Block third-party cookies",
                        subtitle = "Prevent cross-site tracking",
                        icon = R.drawable.ic_cookie,
                        isEnabled = settings.value.blockThirdPartyCookies,
                        onChange = { settings.value = settings.value.copy(blockThirdPartyCookies = it) }
                    )
                    
                    SettingsToggle(
                        title = "Do Not Track",
                        subtitle = "Send DNT header with requests",
                        icon = R.drawable.ic_shield,
                        isEnabled = settings.value.doNotTrack,
                        onChange = { settings.value = settings.value.copy(doNotTrack = it) }
                    )
                    
                    SettingsToggle(
                        title = "Clear on exit",
                        subtitle = "Clear browsing data when closing Blanc",
                        icon = R.drawable.ic_clear,
                        isEnabled = settings.value.clearOnExit,
                        onChange = { settings.value = settings.value.copy(clearOnExit = it) }
                    )
                }
                
                // Appearance section
                SettingsSection(title = "Appearance") {
                    SettingsItem(
                        title = "Theme",
                        subtitle = settings.value.theme,
                        icon = R.drawable.ic_palette,
                        onClick = { /* Open theme picker */ }
                    )
                    
                    SettingsItem(
                        title = "App icon",
                        subtitle = settings.value.appIcon,
                        icon = R.drawable.ic_launch,
                        onClick = { /* Open app icon picker */ }
                    )
                }
                
                // Advanced section
                SettingsSection(title = "Advanced") {
                    SettingsItem(
                        title = "Sync",
                        subtitle = if (settings.value.syncEnabled) "Enabled" else "Disabled",
                        icon = R.drawable.ic_sync,
                        onClick = { /* Open sync settings */ }
                    )
                    
                    SettingsItem(
                        title = "Data & privacy",
                        subtitle = "Manage your data",
                        icon = R.drawable.ic_data,
                        onClick = { /* Open data management */ }
                    )
                    
                    SettingsItem(
                        title = "About Blanc",
                        subtitle = "Version 1.0.0",
                        icon = R.drawable.ic_info,
                        onClick = { /* Open about */ }
                    )
                }
            }
        }
    }
}

@Composable
fun Toolbar(title: String, onBack: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            // Spacer
            androidx.compose.foundation.layout.Box(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .combinedClickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsToggle(
    title: String,
    subtitle: String,
    icon: Int,
    isEnabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = isEnabled,
                onCheckedChange = onChange,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

data class SettingsState(
    val searchEngine: String = "DuckDuckGo",
    val homepage: String = "blanc://newtab",
    val startupBehavior: String = "New Tab",
    val quietTabsDelay: String = "1 hour",
    val adblockEnabled: Boolean = true,
    val adblockExceptions: List<String> = emptyList(),
    val blockThirdPartyCookies: Boolean = true,
    val doNotTrack: Boolean = true,
    val clearOnExit: Boolean = false,
    val theme: String = "System",
    val appIcon: String = "Sunrise",
    val syncEnabled: Boolean = false
)

fun getDefaultSettings(): SettingsState = SettingsState()