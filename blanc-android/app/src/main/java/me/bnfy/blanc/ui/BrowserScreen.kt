package me.bnfy.blanc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.OnGloballyPositionedModifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.launch
import me.bnfy.blanc.storage.Favorite
import java.util.UUID
import me.bnfy.blanc.bridge.BridgeProtocol
import me.bnfy.blanc.storage.Repository
import me.bnfy.blanc.tab.Tab
import me.bnfy.blanc.tab.TabGroup
import me.bnfy.blanc.tab.TabManager
import me.bnfy.blanc.adblock.AdblockEngine
import me.bnfy.blanc.bridge.BlancBridge
import me.bnfy.blanc.R
import me.bnfy.blanc.ui.pages.NewTabPage
import me.bnfy.blanc.ui.pages.SettingsPage
import me.bnfy.blanc.ui.pages.BookmarksPage
import me.bnfy.blanc.ui.pages.HistoryPage
import me.bnfy.blanc.ui.pages.DownloadsPage
import me.bnfy.blanc.storage.DownloadEntity

fun buildSearchUrl(query: String, engine: String): String {
    val encoded = try {
        java.net.URLEncoder.encode(query, "UTF-8")
    } catch (e: Exception) {
        query
    }
    return when (engine.lowercase()) {
        "google" -> "https://www.google.com/search?q=$encoded"
        "bing" -> "https://www.bing.com/search?q=$encoded"
        "brave" -> "https://search.brave.com/search?q=$encoded"
        "ecosia" -> "https://www.ecosia.org/search?q=$encoded"
        else -> "https://duckduckgo.com/?q=$encoded"
    }
}

/**
 * Resolves a user input string from the address bar or search bar into a valid URL.
 * Automatically wraps search queries with the selected search engine and prefixes bare domains with https://.
 */
fun resolveUrlOrSearch(input: String, searchEngine: String = "duckduckgo"): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return "blanc://newtab"
    if (trimmed.startsWith("blanc://") || trimmed.startsWith("about:") || trimmed.startsWith("data:") || trimmed.startsWith("javascript:")) {
        return trimmed
    }
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }
    val hasWhitespace = trimmed.any { it.isWhitespace() }
    val isDomain = !hasWhitespace && (
        trimmed.startsWith("localhost") ||
        (trimmed.contains(".") && !trimmed.startsWith(".") && !trimmed.endsWith("."))
    )
    return if (isDomain) {
        "https://$trimmed"
    } else {
        buildSearchUrl(trimmed, searchEngine)
    }
}

/**
 * Main browser screen with navigation state management.
 */
@Composable
fun BrowserScreen(
    tabManager: TabManager,
    repository: Repository,
    adblockEngine: AdblockEngine,
    blancBridge: BlancBridge,
    onFileChooser: FileChooserHandler,
    onRequestPermission: (String, String, (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current

    // Navigation state
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Browser) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFindInPage by remember { mutableStateOf(false) }

    // Persistent default search engine
    val defaultSearchEngine by repository.defaultSearchEngine.collectAsState(initial = "duckduckgo")

    // Android System Back Navigation Handler
    BackHandler(enabled = true) {
        when {
            showFindInPage -> showFindInPage = false
            showMenu -> showMenu = false
            showTabSwitcher -> showTabSwitcher = false
            currentScreen != Screen.Browser -> currentScreen = Screen.Browser
            tabManager.canGoBack() -> tabManager.goBack()
            else -> (context as? android.app.Activity)?.finish()
        }
    }
    
    // Window size for responsive layout
    val configuration = LocalConfiguration.current
    val isTabletOrLarge = configuration.screenWidthDp >= 600
    
    // Collect reactive state from TabManager
    val tabs by tabManager.tabsFlow.collectAsState(initial = emptyList())
    val activeTabId by tabManager.activeTabIdFlow.collectAsState(initial = null)
    val tabCount by tabManager.tabCountFlow.collectAsState(initial = 0)
    val groups by tabManager.groupsFlow.collectAsState(initial = emptyList())
    val windows by tabManager.windowsFlow.collectAsState(initial = emptyList())
    val activeWindowId by tabManager.activeWindowIdFlow.collectAsState(initial = "default")
    
    val activeTab = tabs.find { it.id == activeTabId }
    
    // Per-tab loading state (would come from individual tab observation in real implementation)
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var blockedCount by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var addressBarText by remember { mutableStateOf("") }
    var isAddressBarFocused by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val handleBookmarkToggle: () -> Unit = {
        val currentUrl = activeTab?.url ?: ""
        if (currentUrl.isNotEmpty() && !currentUrl.startsWith("blanc://") && currentUrl != "about:blank") {
            coroutineScope.launch {
                val existing = repository.favoriteDao.getByUrl(currentUrl, activeTab?.profileId ?: "personal")
                if (existing != null) {
                    repository.favoriteDao.delete(existing)
                    Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                } else {
                    val fav = Favorite(
                        id = UUID.randomUUID().toString(),
                        url = currentUrl,
                        title = activeTab?.title?.ifBlank { currentUrl } ?: currentUrl,
                        profileId = activeTab?.profileId ?: "personal"
                    )
                    repository.favoriteDao.insert(fav)
                    Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val handleShare: () -> Unit = {
        val currentUrl = activeTab?.url ?: ""
        if (currentUrl.isNotEmpty() && !currentUrl.startsWith("blanc://") && currentUrl != "about:blank") {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, currentUrl)
                putExtra(Intent.EXTRA_TITLE, activeTab?.title?.ifBlank { "Web page" } ?: "Web page")
            }
            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    val handleCopyUrl: () -> Unit = {
        val currentUrl = activeTab?.url ?: ""
        if (currentUrl.isNotEmpty() && !currentUrl.startsWith("blanc://") && currentUrl != "about:blank") {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("URL", currentUrl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    val handleDesktopSiteToggle: () -> Unit = {
        val currentMode = activeTab?.isDesktopMode ?: false
        val newMode = !currentMode
        activeTab?.isDesktopMode = newMode
        activeTab?.webView?.let { wv ->
            wv.settings.userAgentString = if (newMode) {
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            } else {
                val baseUa = android.webkit.WebSettings.getDefaultUserAgent(context)
                val versionName = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    "1.0"
                }
                "$baseUa Blanc/$versionName"
            }
            wv.settings.useWideViewPort = newMode
            wv.settings.loadWithOverviewMode = newMode
            wv.reload()
        }
        Toast.makeText(context, if (newMode) "Desktop site requested" else "Mobile site requested", Toast.LENGTH_SHORT).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isTabletOrLarge && currentScreen == Screen.Browser) {
            // Two-pane layout for tablet/large screens
            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar - Tab switcher / Bookmarks / History
                SidebarPane(
                    tabs = tabs,
                    activeTabId = activeTabId,
                    groups = groups,
                    onTabClick = { tabId -> tabManager.switchTab(tabId) },
                    onTabClose = { tabId -> tabManager.closeTab(tabId) },
                    onNewTab = { tabManager.createTab() },
                    onNewPrivateTab = { tabManager.createPrivateTab() },
                    onShowBookmarks = { currentScreen = Screen.Bookmarks },
                    onShowHistory = { currentScreen = Screen.History },
                    onShowDownloads = { currentScreen = Screen.Downloads },
                    modifier = Modifier.width(320.dp)
                )
                
                // Main content
                BrowserContent(
                    tabManager = tabManager,
                    activeTab = activeTab,
                    tabs = tabs,
                    activeTabId = activeTabId,
                    onTabClick = { tabId -> tabManager.switchTab(tabId) },
                    onTabClose = { tabId -> tabManager.closeTab(tabId) },
                    onNavigate = { url ->
                        val resolved = resolveUrlOrSearch(url, defaultSearchEngine)
                        activeTabId?.let { tabManager.navigate(it, resolved) } ?: tabManager.createTab(resolved)
                    },
                    onBack = { activeTabId?.let { tabManager.goBack(it) } },
                    onForward = { activeTabId?.let { tabManager.goForward(it) } },
                    onReload = { 
                        if (activeTab?.isLoading == true) tabManager.stopActiveTab() else tabManager.reloadActiveTab() 
                    },
                    onHome = { activeTabId?.let { tabManager.navigateTo(it, "blanc://newtab") } ?: tabManager.createTab("blanc://newtab") },
                    onBookmark = handleBookmarkToggle,
                    onShare = handleShare,
                    onMenu = { showMenu = true },
                    onTabSwitcher = { showTabSwitcher = true },
                    onNewTab = { tabManager.createTab() },
                    onNewPrivateTab = { tabManager.createPrivateTab() },
                    onFileChooser = onFileChooser,
                    onRequestPermission = onRequestPermission,
                    onShowBookmarks = { currentScreen = Screen.Bookmarks },
                    onShowHistory = { currentScreen = Screen.History },
                    onShowDownloads = { currentScreen = Screen.Downloads },
                    onShowSettings = { currentScreen = Screen.Settings },
                    onShowFindInPage = { showFindInPage = true },
                    onCopyUrl = handleCopyUrl,
                    onDesktopSite = handleDesktopSiteToggle,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Single-pane layout for phone
            when (currentScreen) {
                Screen.Browser -> {
                    BrowserContent(
                        tabManager = tabManager,
                        activeTab = activeTab,
                        tabs = tabs,
                        activeTabId = activeTabId,
                        onTabClick = { tabId -> tabManager.switchTab(tabId) },
                        onTabClose = { tabId -> tabManager.closeTab(tabId) },
                        onNavigate = { url ->
                            val resolved = resolveUrlOrSearch(url, defaultSearchEngine)
                            activeTabId?.let { tabManager.navigate(it, resolved) } ?: tabManager.createTab(resolved)
                        },
                        onBack = { activeTabId?.let { tabManager.goBack(it) } },
                        onForward = { activeTabId?.let { tabManager.goForward(it) } },
                        onReload = { 
                            if (activeTab?.isLoading == true) tabManager.stopActiveTab() else tabManager.reloadActiveTab() 
                        },
                        onHome = { activeTabId?.let { tabManager.navigateTo(it, "blanc://newtab") } ?: tabManager.createTab("blanc://newtab") },
                        onBookmark = handleBookmarkToggle,
                        onShare = handleShare,
                        onMenu = { showMenu = true },
                        onTabSwitcher = { showTabSwitcher = true },
                        onNewTab = { tabManager.createTab() },
                        onNewPrivateTab = { tabManager.createPrivateTab() },
                        onFileChooser = onFileChooser,
                        onRequestPermission = onRequestPermission,
                        onShowBookmarks = { currentScreen = Screen.Bookmarks },
                        onShowHistory = { currentScreen = Screen.History },
                        onShowDownloads = { currentScreen = Screen.Downloads },
                        onShowSettings = { currentScreen = Screen.Settings },
                        onShowFindInPage = { showFindInPage = true },
                        onCopyUrl = handleCopyUrl,
                        onDesktopSite = handleDesktopSiteToggle
                    )
                }
            Screen.NewTab -> NewTabPage(
                tabManager = tabManager,
                repository = repository,
                onNavigate = { url ->
                    val resolved = resolveUrlOrSearch(url, defaultSearchEngine)
                    activeTabId?.let { tabManager.navigate(it, resolved) } ?: tabManager.createTab(resolved)
                    currentScreen = Screen.Browser
                },
                onNewTab = { tabManager.createTab() },
                onNewPrivateTab = { tabManager.createPrivateTab() },
                onOpenBookmarks = { currentScreen = Screen.Bookmarks },
                onOpenHistory = { currentScreen = Screen.History }
            )
            Screen.Bookmarks -> BookmarksPage(
                onBack = { currentScreen = Screen.Browser },
                repository = repository,
                onBookmarkClick = { url ->
                    val resolved = resolveUrlOrSearch(url, defaultSearchEngine)
                    activeTabId?.let { tabManager.navigate(it, resolved) } ?: tabManager.createTab(resolved)
                    currentScreen = Screen.Browser
                }
            )
            Screen.History -> HistoryPage(
                onBack = { currentScreen = Screen.Browser },
                repository = repository,
                onItemClick = { url ->
                    val resolved = resolveUrlOrSearch(url, defaultSearchEngine)
                    activeTabId?.let { tabManager.navigate(it, resolved) } ?: tabManager.createTab(resolved)
                    currentScreen = Screen.Browser
                }
            )
            Screen.Downloads -> DownloadsPage(
                onBack = { currentScreen = Screen.Browser },
                repository = repository
            )
            Screen.Settings -> SettingsPage(
                repository = repository,
                onBack = { currentScreen = Screen.Browser }
            )
        }
    }
        
    // Tab switcher overlay
    if (showTabSwitcher) {
        TabSwitcherOverlay(
            tabs = tabs,
            activeTabId = activeTabId,
            onTabClick = { tabId ->
                tabManager.switchTab(tabId)
                showTabSwitcher = false
            },
            onTabClose = { tabId ->
                tabManager.closeTab(tabId)
            },
            onNewTab = { 
                tabManager.createTab()
                showTabSwitcher = false
            },
            onNewPrivateTab = {
                tabManager.createPrivateTab()
                showTabSwitcher = false
            },
            onDismiss = { showTabSwitcher = false }
        )
    }
    
    // Menu overlay
    if (showMenu) {
        MenuOverlay(
            activeTab = activeTab,
            onDismiss = { showMenu = false },
            onNewTab = { tabManager.createTab(); showMenu = false },
            onNewPrivateTab = { tabManager.createPrivateTab(); showMenu = false },
            onBookmarks = { currentScreen = Screen.Bookmarks; showMenu = false },
            onHistory = { currentScreen = Screen.History; showMenu = false },
            onDownloads = { currentScreen = Screen.Downloads; showMenu = false },
            onSettings = { currentScreen = Screen.Settings; showMenu = false },
            onFindInPage = { showFindInPage = true; showMenu = false },
            onBookmark = { handleBookmarkToggle(); showMenu = false },
            onShare = { handleShare(); showMenu = false },
            onCopyUrl = { handleCopyUrl(); showMenu = false },
            onDesktopSite = { handleDesktopSiteToggle(); showMenu = false }
        )
    }
    
    // Find in page overlay
    if (showFindInPage) {
        FindInPageOverlay(
            activeTab = activeTab,
            onDismiss = { showFindInPage = false }
        )
    }
    }
}

// Sidebar for tablet layout
@Composable
fun SidebarPane(
    tabs: List<Tab>,
    activeTabId: String?,
    groups: List<TabGroup>,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onShowBookmarks: () -> Unit,
    onShowHistory: () -> Unit,
    onShowDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tabs",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onNewTab) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "New tab")
                }
            }
            
            // Tab list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tabs) { tab ->
                    val isActive = tab.id == activeTabId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTabClick(tab.id) },
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = if (isActive) 2.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (tab.isPrivate) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_private_tab),
                                    contentDescription = "Private",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = tab.title.ifBlank { tab.url },
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = tab.url,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { onTabClose(tab.id) }) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                            }
                        }
                    }
                }
            }
            
            // Quick actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onNewPrivateTab) {
                    Icon(painterResource(R.drawable.ic_private_tab), contentDescription = "Private")
                    Text("New Private Tab")
                }
                OutlinedButton(onClick = onShowBookmarks) {
                    Icon(painterResource(R.drawable.ic_bookmark), contentDescription = "Favorites")
                    Text("Favorites")
                }
                OutlinedButton(onClick = onShowHistory) {
                    Icon(painterResource(R.drawable.ic_history), contentDescription = "History")
                    Text("History")
                }
                OutlinedButton(onClick = onShowDownloads) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = "Downloads")
                    Text("Downloads")
                }
            }
        }
    }
}

enum class Screen {
    Browser, NewTab, Bookmarks, History, Downloads, Settings
}

/**
 * Main browser content area with address bar and WebView.
 */
@Composable
fun BrowserContent(
    tabManager: TabManager,
    activeTab: Tab?,
    tabs: List<Tab>,
    activeTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onMenu: () -> Unit,
    onTabSwitcher: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onFileChooser: FileChooserHandler,
    onRequestPermission: (String, String, (Boolean) -> Unit) -> Unit,
    onShowBookmarks: () -> Unit,
    onShowHistory: () -> Unit,
    onShowDownloads: () -> Unit,
    onShowSettings: () -> Unit,
    onShowFindInPage: () -> Unit,
    onCopyUrl: () -> Unit,
    onDesktopSite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPrivate = activeTab?.isPrivate == true
    val isLoading = activeTab?.isLoading == true
    val progress = activeTab?.progress ?: 0
    val blockedCount = activeTab?.blockedCount ?: 0
    val canGoBack = activeTab?.canGoBack == true
    val canGoForward = activeTab?.canGoForward == true
    
    val currentUrl = activeTab?.url ?: ""
    val isNewTab = currentUrl.isEmpty() || currentUrl == "about:blank" || currentUrl.startsWith("blanc://newtab")
    
    var addressBarInput by remember(activeTab?.id, currentUrl) {
        mutableStateOf(if (isNewTab) "" else currentUrl)
    }
    var isAddressBarFocused by remember { mutableStateOf(false) }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Content area takes full screen
        if (!isNewTab && activeTab != null) {
            ContentWebViewContainer(
                tabManager = tabManager,
                activeTab = activeTab,
                onFileChooser = onFileChooser,
                onRequestPermission = onRequestPermission,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            )
        } else {
            // New tab page - show Compose NewTabPage
            NewTabPage(
                tabManager = tabManager,
                onNavigate = onNavigate,
                onNewTab = onNewTab,
                onNewPrivateTab = onNewPrivateTab,
                onOpenBookmarks = onShowBookmarks,
                onOpenHistory = onShowHistory
            )
        }

        // Bottom floating pill directly on top of the content of the screen
        BottomFloatingIslandBar(
            activeTab = activeTab,
            tabs = tabs,
            activeTabId = activeTabId,
            isLoading = isLoading,
            progress = progress,
            blockedCount = blockedCount,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            addressBarText = addressBarInput,
            isAddressBarFocused = isAddressBarFocused,
            onAddressBarTextChange = { addressBarInput = it },
            onAddressBarFocusChange = { isAddressBarFocused = it },
            onNavigate = { target ->
                isAddressBarFocused = false
                onNavigate(target)
            },
            onBack = onBack,
            onForward = onForward,
            onReload = onReload,
            onHome = onHome,
            onBookmark = onBookmark,
            onShare = onShare,
            onMenu = onMenu,
            onTabSwitcher = onTabSwitcher,
            onNewTab = onNewTab,
            onNewPrivateTab = onNewPrivateTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/**
 * Bottom floating Island Chrome pill with controls, tab counter, and command address bar.
 */
@Composable
fun BottomFloatingIslandBar(
    activeTab: Tab?,
    tabs: List<Tab>,
    activeTabId: String?,
    isLoading: Boolean,
    progress: Int,
    blockedCount: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    addressBarText: String,
    isAddressBarFocused: Boolean,
    onAddressBarTextChange: (String) -> Unit,
    onAddressBarFocusChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onMenu: () -> Unit,
    onTabSwitcher: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPrivate = activeTab?.isPrivate == true
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp > 600

    Surface(
        modifier = modifier
            .then(if (isLargeScreen) Modifier.width(600.dp) else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(32.dp)),
        color = if (isPrivate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            1.dp,
            if (isPrivate) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Loading indicator across the top edge of the floating pill
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Back button
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "Back",
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Forward button (only shown if forward history exists)
                if (canGoForward) {
                    IconButton(
                        onClick = onForward,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_forward),
                            contentDescription = "Forward",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Center Island Address / Search Pill
                IslandAddressBar(
                    isPrivate = isPrivate,
                    isLoading = isLoading,
                    progress = progress,
                    blockedCount = blockedCount,
                    text = addressBarText,
                    isFocused = isAddressBarFocused,
                    onTextChange = onAddressBarTextChange,
                    onFocusChange = onAddressBarFocusChange,
                    onNavigate = onNavigate,
                    onReload = onReload,
                    onBookmark = onBookmark,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                )

                // Tab Switcher Button (with stylish tab count badge)
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onTabSwitcher),
                    color = if (isPrivate) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.5.dp,
                        if (isPrivate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${tabs.size.coerceAtLeast(1)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPrivate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Menu button
                IconButton(
                    onClick = onMenu,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu),
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Address bar pill with URL display, shield badge, and quick reload.
 */
@Composable
fun IslandAddressBar(
    isPrivate: Boolean,
    isLoading: Boolean,
    progress: Int,
    blockedCount: Int,
    text: String,
    isFocused: Boolean,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(isFocused) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isFocused) {
        isEditing = isFocused
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore if not attached yet
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp)),
        color = if (isPrivate) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shield or Private Icon
            if (isPrivate) {
                Icon(
                    painter = painterResource(R.drawable.ic_private_tab),
                    contentDescription = "Private",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 2.dp)
                )
            } else if (blockedCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shield),
                            contentDescription = "Shield",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "$blockedCount",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // URL or Search field
            if (isEditing) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            isEditing = false
                            onFocusChange(false)
                            onNavigate(text)
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Search or enter URL",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                )

                // Clear button when editing
                if (text.isNotEmpty()) {
                    IconButton(
                        onClick = { onTextChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = if (text.isBlank()) "Search or enter URL" else text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            isEditing = true
                            onFocusChange(true)
                        }
                )

                // Bookmark button
                IconButton(
                    onClick = onBookmark,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bookmark),
                        contentDescription = "Bookmark",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Reload button
                IconButton(
                    onClick = onReload,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = "Reload",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * Tab switcher overlay - shows all tabs in a grid.
 */
@Composable
fun TabSwitcherOverlay(
    tabs: List<Tab>,
    activeTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss)
        )
        
        // Tab switcher panel
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tabs",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { onNewPrivateTab(); onDismiss() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_private_tab),
                                contentDescription = "New private tab",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Private")
                        }
                        
                        IconButton(onClick = { onNewTab(); onDismiss() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tab),
                                contentDescription = "New tab",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text("New Tab")
                        }
                        
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                // Tab list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabs) { tab ->
                        val isActive = tab.id == activeTabId
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onTabClick(tab.id) },
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = if (isActive) 2.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail placeholder
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Gray)
                                ) {
                                    // TODO: Load tab thumbnail
                                }
                                
                                // Tab info
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (tab.isPrivate) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_private_tab),
                                                contentDescription = "Private",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = tab.title.ifBlank { tab.url },
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    
                                    Text(
                                        text = tab.url,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                                
                                // Close button
                                IconButton(
                                    onClick = { onTabClose(tab.id) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Menu overlay - shows browser menu options.
 */
@Composable
fun MenuOverlay(
    activeTab: Tab?,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onFindInPage: () -> Unit,
    onBookmark: () -> Unit = {},
    onShare: () -> Unit,
    onCopyUrl: () -> Unit,
    onDesktopSite: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onDismiss)
        )
        
        // Menu panel
        Surface(
            modifier = Modifier
                .width(280.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 76.dp, end = 16.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                // Tab actions
                if (activeTab != null) {
                    MenuItem(
                        icon = R.drawable.ic_tab,
                        text = "New tab",
                        onClick = { onNewTab(); onDismiss() }
                    )
                    
                    MenuItem(
                        icon = R.drawable.ic_private_tab,
                        text = "New private tab",
                        onClick = { onNewPrivateTab(); onDismiss() },
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                }
                
                // Navigation actions
                MenuItem(
                    icon = R.drawable.ic_bookmark,
                    text = "Favorites",
                    onClick = { onBookmarks(); onDismiss() }
                )
                
                MenuItem(
                    icon = R.drawable.ic_history,
                    text = "History",
                    onClick = { onHistory(); onDismiss() }
                )
                
                MenuItem(
                    icon = R.drawable.ic_download,
                    text = "Downloads",
                    onClick = { onDownloads(); onDismiss() }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                )
                
                // Page actions
                if (activeTab != null && !activeTab.url.startsWith("blanc://") && activeTab.url != "about:blank") {
                    MenuItem(
                        icon = R.drawable.ic_bookmark,
                        text = "Bookmark page",
                        onClick = { onBookmark(); onDismiss() }
                    )
                }

                MenuItem(
                    icon = R.drawable.ic_share,
                    text = "Share",
                    onClick = { onShare(); onDismiss() }
                )
                
                MenuItem(
                    icon = R.drawable.ic_info,
                    text = "Copy URL",
                    onClick = { onCopyUrl(); onDismiss() }
                )
                
                MenuItem(
                    icon = R.drawable.ic_find,
                    text = "Find in page",
                    onClick = { onFindInPage(); onDismiss() }
                )
                
                MenuItem(
                    icon = R.drawable.ic_launch,
                    text = if (activeTab?.isDesktopMode == true) "Request mobile site" else "Request desktop site",
                    onClick = { onDesktopSite(); onDismiss() }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                )
                
                // Settings
                MenuItem(
                    icon = R.drawable.ic_settings,
                    text = "Settings",
                    onClick = { onSettings(); onDismiss() }
                )
            }
        }
    }
}

@Composable
fun MenuItem(
    icon: Int,
    text: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Find in page overlay with live WebView search integration.
 */
@Composable
fun FindInPageOverlay(
    activeTab: Tab?,
    onDismiss: () -> Unit
) {
    val query = remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_find),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                TextField(
                    value = query.value,
                    onValueChange = { 
                        query.value = it
                        if (it.isNotEmpty()) {
                            activeTab?.webView?.findAllAsync(it)
                        } else {
                            activeTab?.webView?.clearMatches()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Find in page") },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { 
                            if (query.value.isNotEmpty()) {
                                activeTab?.webView?.findNext(true)
                            }
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
                
                IconButton(onClick = { activeTab?.webView?.findNext(false) }) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Previous")
                }
                
                IconButton(onClick = { activeTab?.webView?.findNext(true) }) {
                    Icon(painterResource(R.drawable.ic_forward), contentDescription = "Next")
                }
                
                IconButton(onClick = {
                    activeTab?.webView?.clearMatches()
                    onDismiss()
                }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                }
            }
        }
    }
}