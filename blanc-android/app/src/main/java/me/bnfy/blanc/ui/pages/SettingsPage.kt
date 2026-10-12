package me.bnfy.blanc.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.bnfy.blanc.BlancApplication
import me.bnfy.blanc.BuildConfig
import me.bnfy.blanc.R
import me.bnfy.blanc.storage.Repository
import me.bnfy.blanc.util.BookmarksHtmlImporter
import me.bnfy.blanc.tab.TabManager
import me.bnfy.blanc.ui.WALLPAPER_PRESETS
import me.bnfy.blanc.ui.wallpaperBrushFor
import me.bnfy.blanc.ui.wallpaperLabelFor

/**
 * Settings page with interactive options and containerized layout.
 */
@Composable
fun SettingsPage(
    repository: Repository,
    onBack: () -> Unit,
    tabManager: TabManager = BlancApplication.getInstance().tabManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Persistent settings state collected from Repository
    val searchEngine by repository.defaultSearchEngine.collectAsState(initial = "duckduckgo")
    val homepage by repository.homepage.collectAsState(initial = "blanc://newtab")
    val startupBehavior by repository.startupBehavior.collectAsState(initial = "newtab")
    val quietTabsDelay by repository.quietTabsDelay.collectAsState(initial = "1h")
    val adblockEnabled by repository.adblockEnabled.collectAsState(initial = true)
    val adblockExceptions by repository.adblockExceptions.collectAsState(initial = emptySet())
    val blockThirdPartyCookies by repository.blockThirdPartyCookies.collectAsState(initial = true)
    val doNotTrack by repository.doNotTrack.collectAsState(initial = true)
    val clearOnExit by repository.clearOnExit.collectAsState(initial = false)
    val theme by repository.theme.collectAsState(initial = "system")
    val appIcon by repository.appIcon.collectAsState(initial = "sunrise")
    val syncEnabled by repository.syncEnabled.collectAsState(initial = false)
    val sitePermissions by repository.profileDao.getPermissionsByProfileFlow("personal")
        .collectAsState(initial = emptyList())
    val patronActive by repository.patronActive.collectAsState(initial = false)
    val workspaces by repository.getWorkspaces("personal").collectAsState(initial = emptyList())
    val textZoom by repository.textZoom.collectAsState(initial = 100)
    val fontFamily by repository.fontFamily.collectAsState(initial = "system")
    val wallpaper by repository.wallpaper.collectAsState(initial = "none")

    // Dialog display states
    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showHomepageDialog by remember { mutableStateOf(false) }
    var showStartupDialog by remember { mutableStateOf(false) }
    var showQuietTabsDialog by remember { mutableStateOf(false) }
    var showExceptionsDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showWorkspacesDialog by remember { mutableStateOf(false) }
    var showPatronDialog by remember { mutableStateOf(false) }
    var showTextZoomDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showDataBackupDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAppIconDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

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

            // Settings sections with container spacing
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // General section
                SettingsSection(title = "General") {
                    SettingsContainerCard {
                        SettingsItem(
                            title = "Search engine",
                            subtitle = getSearchEngineDisplay(searchEngine),
                            icon = R.drawable.ic_search,
                            onClick = { showSearchEngineDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "Homepage",
                            subtitle = homepage,
                            icon = R.drawable.ic_home,
                            onClick = { showHomepageDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "Startup behavior",
                            subtitle = getStartupDisplay(startupBehavior),
                            icon = R.drawable.ic_launch,
                            onClick = { showStartupDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "Quiet tabs",
                            subtitle = getQuietTabsDisplay(quietTabsDelay),
                            icon = R.drawable.ic_tab,
                            onClick = { showQuietTabsDialog = true }
                        )
                    }
                }

                // Privacy section
                SettingsSection(title = "Privacy & Security") {
                    SettingsContainerCard {
                        SettingsToggle(
                            title = "Block ads & trackers",
                            subtitle = "Intercept ads, malware, and trackers at the network layer",
                            icon = R.drawable.ic_shield,
                            isEnabled = adblockEnabled,
                            onChange = { enabled ->
                                coroutineScope.launch {
                                    repository.setAdblockEnabled(enabled)
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "Adblock exceptions",
                            subtitle = if (adblockExceptions.isEmpty()) "No sites excepted" else "${adblockExceptions.size} sites excepted",
                            icon = R.drawable.ic_shield,
                            onClick = { showExceptionsDialog = true }
                        )
                        SettingsDivider()
                        SettingsToggle(
                            title = "Block third-party cookies",
                            subtitle = "Prevent cross-site trackers from following your session",
                            icon = R.drawable.ic_cookie,
                            isEnabled = blockThirdPartyCookies,
                            onChange = { enabled ->
                                coroutineScope.launch {
                                    repository.setBlockThirdPartyCookies(enabled)
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsToggle(
                            title = "Do Not Track",
                            subtitle = "Transmit DNT request header with web traffic",
                            icon = R.drawable.ic_shield,
                            isEnabled = doNotTrack,
                            onChange = { enabled ->
                                coroutineScope.launch {
                                    repository.setDoNotTrack(enabled)
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsToggle(
                            title = "Clear on exit",
                            subtitle = "Automatically wipe cookies and browsing data on close",
                            icon = R.drawable.ic_clear,
                            isEnabled = clearOnExit,
                            onChange = { enabled ->
                                coroutineScope.launch {
                                    repository.setClearOnExit(enabled)
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "Site permissions",
                            subtitle = if (sitePermissions.isEmpty()) {
                                "No saved decisions"
                            } else {
                                "${sitePermissions.size} saved decisions"
                            },
                            icon = R.drawable.ic_shield,
                            onClick = { showPermissionsDialog = true }
                        )
                    }
                }

                // Appearance section
                SettingsSection(title = "Appearance") {
                    SettingsContainerCard {
                        SettingsItem(
                            title = "Theme",
                            subtitle = getThemeDisplay(theme),
                            icon = R.drawable.ic_palette,
                            onClick = { showThemeDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "App icon",
                            subtitle = getAppIconDisplay(appIcon),
                            icon = R.drawable.ic_launch,
                            onClick = { showAppIconDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "Text scaling",
                            subtitle = "$textZoom%",
                            icon = R.drawable.ic_find,
                            onClick = { showTextZoomDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "Typography",
                            subtitle = getFontDisplay(fontFamily),
                            icon = R.drawable.ic_info,
                            onClick = { showFontDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "New tab background",
                            subtitle = wallpaperLabelFor(wallpaper),
                            icon = R.drawable.ic_image,
                            onClick = { showWallpaperDialog = true }
                        )
                    }
                }

                // Workspaces & Patron section
                SettingsSection(title = "Workspaces") {
                    SettingsContainerCard {
                        SettingsItem(
                            title = "Workspaces",
                            subtitle = if (workspaces.isEmpty()) {
                                "No workspaces yet"
                            } else {
                                "${workspaces.size} saved"
                            },
                            icon = R.drawable.ic_tab,
                            onClick = { showWorkspacesDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "Blanc Patron",
                            subtitle = if (patronActive) "Active" else "Unlock named workspaces",
                            icon = R.drawable.ic_launch,
                            onClick = { showPatronDialog = true }
                        )
                    }
                }

                // Advanced section
                SettingsSection(title = "Advanced & Data") {
                    SettingsContainerCard {
                        SettingsItem(
                            title = "Blanc Sync",
                            subtitle = if (syncEnabled) "Enabled (Encrypted)" else "Disabled",
                            icon = R.drawable.ic_sync,
                            onClick = { showSyncDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "Clear browsing data",
                            subtitle = "Clear history, cache, cookies, and downloads",
                            icon = R.drawable.ic_data,
                            onClick = { showClearDataDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "About Blanc",
                            subtitle = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            icon = R.drawable.ic_info,
                            onClick = { showAboutDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = "Data backup",
                            subtitle = "Export or import favorites, history & settings",
                            icon = R.drawable.ic_data,
                            onClick = { showDataBackupDialog = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ==========================================
    // Interactive Dialog Implementations
    // ==========================================

    // 1. Search Engine Dialog
    if (showSearchEngineDialog) {
        val engines = listOf(
            "duckduckgo" to "DuckDuckGo (Privacy-first)",
            "google" to "Google",
            "bing" to "Microsoft Bing",
            "brave" to "Brave Search",
            "ecosia" to "Ecosia"
        )
        SingleChoiceDialog(
            title = "Default Search Engine",
            options = engines,
            selectedKey = searchEngine,
            onSelect = { selected ->
                coroutineScope.launch {
                    repository.setDefaultSearchEngine(selected)
                    showSearchEngineDialog = false
                }
            },
            onDismiss = { showSearchEngineDialog = false }
        )
    }

    // 2. Homepage Dialog
    if (showHomepageDialog) {
        var homepageInput by remember { mutableStateOf(homepage) }
        AlertDialog(
            onDismissRequest = { showHomepageDialog = false },
            title = { Text("Set Homepage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a web address or use Blanc New Tab:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = homepageInput,
                        onValueChange = { homepageInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://example.com") }
                    )
                    OutlinedButton(
                        onClick = { homepageInput = "blanc://newtab" },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Reset to New Tab")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val resolved = if (homepageInput.isBlank()) "blanc://newtab" else homepageInput.trim()
                            repository.setHomepage(resolved)
                            showHomepageDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHomepageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Startup Behavior Dialog
    if (showStartupDialog) {
        val startupOptions = listOf(
            "newtab" to "Open New Tab",
            "continue" to "Continue previous session",
            "homepage" to "Open Homepage"
        )
        SingleChoiceDialog(
            title = "On Startup",
            options = startupOptions,
            selectedKey = startupBehavior,
            onSelect = { selected ->
                coroutineScope.launch {
                    repository.setStartupBehavior(selected)
                    showStartupDialog = false
                }
            },
            onDismiss = { showStartupDialog = false }
        )
    }

    // 4. Quiet Tabs Dialog
    if (showQuietTabsDialog) {
        val quietOptions = listOf(
            "15m" to "15 minutes",
            "30m" to "30 minutes",
            "1h" to "1 hour (Recommended)",
            "2h" to "2 hours",
            "never" to "Never discard background tabs"
        )
        SingleChoiceDialog(
            title = "Quiet Tabs Sleep Delay",
            options = quietOptions,
            selectedKey = quietTabsDelay,
            onSelect = { selected ->
                coroutineScope.launch {
                    repository.setQuietTabsDelay(selected)
                    showQuietTabsDialog = false
                }
            },
            onDismiss = { showQuietTabsDialog = false }
        )
    }

    // 5. Adblock Exceptions Dialog
    if (showExceptionsDialog) {
        var newDomainInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExceptionsDialog = false },
            title = { Text("Adblock Exceptions") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sites on this list will bypass ad and tracker filtering.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDomainInput,
                            onValueChange = { newDomainInput = it },
                            placeholder = { Text("domain.com") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val clean = newDomainInput.trim().lowercase().removePrefix("https://").removePrefix("http://")
                                if (clean.isNotBlank()) {
                                    coroutineScope.launch {
                                        repository.addAdblockException(clean)
                                        newDomainInput = ""
                                    }
                                }
                            }
                        ) {
                            Text("Add")
                        }
                    }

                    if (adblockExceptions.isEmpty()) {
                        Text(
                            text = "No exceptions added yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(adblockExceptions.toList().sorted()) { domain ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = domain,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                repository.removeAdblockException(domain)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExceptionsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 5b. Site Permissions Dialog
    if (showPermissionsDialog) {
        val groupedPermissions = sitePermissions.groupBy { it.origin }
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            title = { Text("Site Permissions") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Decisions Blanc stored when sites asked for camera, microphone, " +
                            "location or notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (groupedPermissions.isEmpty()) {
                        Text(
                            text = "No site permissions saved yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(groupedPermissions.keys.toList().sorted()) { origin ->
                                val decisions = groupedPermissions[origin].orEmpty()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = origin,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    repository.profileDao
                                                        .deletePermissionsForOrigin(origin, "personal")
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_close),
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    decisions.sortedBy { it.resource }.forEach { decision ->
                                        Text(
                                            text = "${permissionResourceLabel(decision.resource)} · " +
                                                permissionDecisionLabel(decision.decision),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissionsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 5c. Workspaces Dialog
    if (showWorkspacesDialog) {
        var newWorkspaceName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWorkspacesDialog = false },
            title = { Text("Workspaces") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Workspaces keep sets of tabs together. Tap one to focus its tabs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newWorkspaceName,
                            onValueChange = { newWorkspaceName = it },
                            placeholder = { Text("Workspace name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val clean = newWorkspaceName.trim()
                                if (clean.isNotBlank() && patronActive) {
                                    coroutineScope.launch {
                                        repository.createWorkspace(clean)
                                        newWorkspaceName = ""
                                    }
                                }
                            },
                            enabled = patronActive && newWorkspaceName.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    }
                    if (!patronActive) {
                        Text(
                            text = "Creating named workspaces requires Blanc Patron.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (workspaces.isEmpty()) {
                        Text(
                            text = "No workspaces yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(workspaces, key = { it.id }) { ws ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            tabManager.switchWorkspace(ws.id)
                                            showWorkspacesDialog = false
                                            onBack()
                                        }
                                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ws.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Tap to switch",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                repository.deleteWorkspace(ws.id)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = "Delete workspace",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWorkspacesDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 5d. Patron Dialog
    if (showPatronDialog) {
        AlertDialog(
            onDismissRequest = { showPatronDialog = false },
            title = { Text("Blanc Patron") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Blanc Patron supports development and unlocks named workspaces.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (patronActive) "Patron active" else "Not activated",
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = patronActive,
                            onCheckedChange = { active ->
                                coroutineScope.launch {
                                    repository.setPatronActive(active)
                                    repository.setPatronEntitlements(
                                        if (active) "{\"workspaces\":true}" else "{}"
                                    )
                                }
                            }
                        )
                    }
                    Text(
                        text = "Patron is support for Blanc, not a source-code restriction.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPatronDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // 5e. Text Zoom Dialog
    if (showTextZoomDialog) {
        val zoomOptions = listOf(
            "75" to "Smaller (75%)",
            "85" to "Compact (85%)",
            "100" to "Default (100%)",
            "115" to "Larger (115%)",
            "130" to "Large (130%)",
            "150" to "Extra large (150%)"
        )
        SingleChoiceDialog(
            title = "Text Scaling",
            options = zoomOptions,
            selectedKey = textZoom.toString(),
            onSelect = { selected ->
                coroutineScope.launch {
                    val percent = selected.toIntOrNull() ?: 100
                    repository.setTextZoom(percent)
                    tabManager.applyTextZoomToAll(percent)
                    showTextZoomDialog = false
                }
            },
            onDismiss = { showTextZoomDialog = false }
        )
    }

    // 5g. Typography Dialog
    if (showFontDialog) {
        val fontOptions = listOf(
            "system" to "System default",
            "sans" to "Sans-serif",
            "serif" to "Serif",
            "mono" to "Monospace"
        )
        SingleChoiceDialog(
            title = "Typography",
            options = fontOptions,
            selectedKey = fontFamily,
            onSelect = { selected ->
                coroutineScope.launch {
                    repository.setFontFamily(selected)
                    tabManager.applyFontToAll(selected)
                    showFontDialog = false
                }
            },
            onDismiss = { showFontDialog = false }
        )
    }

    // 5h. Wallpaper Dialog
    if (showWallpaperDialog) {
        val isDarkDialog = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = { Text("New Tab Background") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Gradient presets replace the desktop nature-wallpaper set. " +
                            "They adapt to light and dark themes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WALLPAPER_PRESETS.forEach { preset ->
                        val selected = preset.id == wallpaper
                        val brush = wallpaperBrushFor(preset.id, isDarkDialog)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        repository.setWallpaper(preset.id)
                                        showWallpaperDialog = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (brush != null) brush else SolidColor(
                                            MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (brush == null) {
                                    Text(
                                        text = "−",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = preset.label,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Medium
                            )
                            RadioButton(selected = selected, onClick = null)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWallpaperDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 5f. Data Backup Dialog
    if (showDataBackupDialog) {
        var importText by remember { mutableStateOf("") }
        var exporting by remember { mutableStateOf(false) }
        var importing by remember { mutableStateOf(false) }
        var importingBookmarks by remember { mutableStateOf(false) }
        val bookmarksHtmlLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null && !importingBookmarks) {
                importingBookmarks = true
                coroutineScope.launch {
                    val outcome = runCatching {
                        val content = context.contentResolver
                            .openInputStream(uri)?.use { it.readBytes() }
                            ?.toString(Charsets.UTF_8)
                        if (content.isNullOrBlank()) return@runCatching null
                        val entries = BookmarksHtmlImporter.parse(content)
                        if (entries.isEmpty()) return@runCatching null
                        repository.importBookmarks(entries)
                    }
                    importingBookmarks = false
                    val message = when {
                        outcome.isFailure ->
                            "Import failed - ${outcome.exceptionOrNull()?.message ?: "could not read file"}"
                        outcome.getOrNull() == null ->
                            "No bookmarks found in that file"
                        else -> {
                            val (imported, skipped) = outcome.getOrThrow()!!
                            if (skipped > 0) "Imported $imported bookmarks ($skipped duplicates skipped)"
                            else "Imported $imported bookmarks"
                        }
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showDataBackupDialog = false },
            title = { Text("Data Backup") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Export a JSON archive of favorites, bookmarks, history, " +
                            "workspaces and settings. Import a previously exported archive " +
                            "to merge it back in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            exporting = true
                            coroutineScope.launch {
                                val json = repository.exportData()
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Blanc backup", json))
                                exporting = false
                                Toast.makeText(
                                    context,
                                    "Backup copied to clipboard",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (exporting) "Exporting…" else "Export & copy to clipboard")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text("Paste a Blanc backup JSON…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp),
                        minLines = 3
                    )
                    Button(
                        onClick = {
                            importing = true
                            coroutineScope.launch {
                                val ok = repository.importData(importText.trim())
                                importing = false
                                Toast.makeText(
                                    context,
                                    if (ok) "Backup imported" else "Import failed — invalid archive",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (ok) {
                                    importText = ""
                                    showDataBackupDialog = false
                                }
                            }
                        },
                        enabled = !importing && importText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (importing) "Importing…" else "Import backup")
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Import from a browser",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bring bookmarks from Chrome, Edge, Brave or Firefox via their " +
                            "\"Export bookmarks\" HTML file. Android cannot read another installed " +
                            "browser's data directly, so the export file is the supported route.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { bookmarksHtmlLauncher.launch("*/*") },
                        enabled = !importingBookmarks,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (importingBookmarks) "Importing…" else "Import bookmarks from HTML…")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDataBackupDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 6. Theme Dialog
    if (showThemeDialog) {
        val themes = listOf(
            "system" to "System Default",
            "sunrise" to "Sunrise (Gold & Ivory)",
            "light" to "Light",
            "dark" to "Dark",
            "paper" to "Paper (Warm Cream)",
            "ink" to "Ink (Pure Black)"
        )
        SingleChoiceDialog(
            title = "Appearance Theme",
            options = themes,
            selectedKey = theme,
            onSelect = { selected ->
                coroutineScope.launch {
                    repository.setTheme(selected)
                    showThemeDialog = false
                }
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // 7. App Icon Dialog
    if (showAppIconDialog) {
        val icons = listOf(
            "sunrise" to "Sunrise Hero Mark (Default)",
            "dark" to "Midnight Noir",
            "minimal" to "Minimal Outline"
        )
        SingleChoiceDialog(
            title = "App Icon",
            options = icons,
            selectedKey = appIcon,
            onSelect = { selected ->
                coroutineScope.launch {
                    repository.setAppIcon(selected)
                    showAppIconDialog = false
                }
            },
            onDismiss = { showAppIconDialog = false }
        )
    }

    // 8. Sync Dialog
    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = { Text("Blanc Sync") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Blanc Sync securely encrypts your bookmarks, quiet tabs, and settings on-device before syncing with your devices.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (syncEnabled) "Sync is active" else "Sync is paused",
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = syncEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    repository.setSyncEnabled(enabled)
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSyncDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // 9. Clear Data & Privacy Dialog
    if (showClearDataDialog) {
        var clearHistorySelected by remember { mutableStateOf(true) }
        var clearCookiesSelected by remember { mutableStateOf(true) }
        var clearCacheSelected by remember { mutableStateOf(true) }
        var clearDownloadsSelected by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear Browsing Data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Select data to permanently delete:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    DataClearCheckbox(
                        label = "Browsing History",
                        checked = clearHistorySelected,
                        onCheckedChange = { clearHistorySelected = it }
                    )
                    DataClearCheckbox(
                        label = "Cookies & Site Data",
                        checked = clearCookiesSelected,
                        onCheckedChange = { clearCookiesSelected = it }
                    )
                    DataClearCheckbox(
                        label = "Cached Images & Files",
                        checked = clearCacheSelected,
                        onCheckedChange = { clearCacheSelected = it }
                    )
                    DataClearCheckbox(
                        label = "Downloads History",
                        checked = clearDownloadsSelected,
                        onCheckedChange = { clearDownloadsSelected = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                if (clearHistorySelected) {
                                    repository.historyDao.clearHistory("personal")
                                }
                                if (clearCookiesSelected) {
                                    CookieManager.getInstance().removeAllCookies(null)
                                }
                                if (clearCacheSelected) {
                                    WebStorage.getInstance().deleteAllData()
                                }
                                if (clearDownloadsSelected) {
                                    repository.downloadDao.clearByProfile("personal")
                                }
                                Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error clearing data: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            showClearDataDialog = false
                        }
                    }
                ) {
                    Text("Clear Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 10. About Blanc Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Blanc") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Blanc Browser",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Package: ${BuildConfig.APPLICATION_ID}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Minimal, privacy-focused browser featuring custom Island Chrome, network-level ad/tracker blocking, quiet tabs, and local profiles.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Open Source under the MIT License.\nBananify Creative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// ==========================================
// Reusable Dialogs & Components
// ==========================================

@Composable
fun SingleChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { (key, label) ->
                    val isSelected = key.equals(selectedKey, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(key) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(key) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DataClearCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun Toolbar(title: String, onBack: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Box(modifier = Modifier.size(48.dp))
        }
    }
}

/**
 * Section container with label header and spacing.
 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
        )
        content()
    }
}

/**
 * Card container wrapping a group of settings items with generous spacing and elevation.
 */
@Composable
fun SettingsContainerCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 0.8.dp
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_forward),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!isEnabled) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

// Helpers for formatted display values
private fun getSearchEngineDisplay(key: String): String = when (key.lowercase()) {
    "duckduckgo" -> "DuckDuckGo"
    "google" -> "Google"
    "bing" -> "Microsoft Bing"
    "brave" -> "Brave Search"
    "ecosia" -> "Ecosia"
    else -> key.replaceFirstChar { it.uppercase() }
}

private fun getStartupDisplay(key: String): String = when (key.lowercase()) {
    "newtab" -> "Open New Tab"
    "continue" -> "Continue previous session"
    "homepage" -> "Open Homepage"
    else -> key
}

private fun getQuietTabsDisplay(key: String): String = when (key.lowercase()) {
    "15m" -> "15 minutes"
    "30m" -> "30 minutes"
    "1h" -> "1 hour"
    "2h" -> "2 hours"
    "never" -> "Never"
    else -> key
}

private fun getThemeDisplay(key: String): String = when (key.lowercase()) {
    "system" -> "System Default"
    "sunrise" -> "Sunrise (Gold & Ivory)"
    "light" -> "Light"
    "dark" -> "Dark"
    "paper" -> "Paper"
    "ink" -> "Ink"
    else -> key.replaceFirstChar { it.uppercase() }
}

private fun getAppIconDisplay(key: String): String = when (key.lowercase()) {
    "sunrise" -> "Sunrise Gold"
    "dark" -> "Midnight Noir"
    "minimal" -> "Minimal"
    else -> key.replaceFirstChar { it.uppercase() }
}

private fun getFontDisplay(key: String): String = when (key.lowercase()) {
    "system" -> "System default"
    "sans" -> "Sans-serif"
    "serif" -> "Serif"
    "mono" -> "Monospace"
    else -> key.replaceFirstChar { it.uppercase() }
}

private fun permissionResourceLabel(resource: String): String = when (resource.lowercase()) {
    "camera" -> "Camera"
    "microphone" -> "Microphone"
    "geolocation", "location" -> "Location"
    "notifications" -> "Notifications"
    else -> resource.replaceFirstChar { it.uppercase() }
}

private fun permissionDecisionLabel(decision: Int): String = when (decision) {
    1 -> "Allowed"
    2 -> "Blocked"
    3 -> "Ask every time"
    else -> "Default"
}