package me.bnfy.blanc.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
    // Navigation state
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Browser) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFindInPage by remember { mutableStateOf(false) }
    
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
                        tabManager.navigate(activeTabId!!, url)
                    },
                    onBack = { tabManager.goBack(activeTabId!!) },
                    onForward = { tabManager.goForward(activeTabId!!) },
                    onReload = { 
                        if (activeTab?.isLoading == true) tabManager.stopActiveTab() else tabManager.reloadActiveTab() 
                    },
                    onHome = { tabManager.navigateTo(activeTabId!!, "blanc://newtab") },
                    onBookmark = { /* Toggle bookmark */ },
                    onShare = { /* Share current page */ },
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
                    onCopyUrl = { /* Copy URL */ },
                    onDesktopSite = { /* Toggle desktop site */ },
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
                            tabManager.navigate(activeTabId!!, url)
                        },
                        onBack = { tabManager.goBack(activeTabId!!) },
                        onForward = { tabManager.goForward(activeTabId!!) },
                        onReload = { 
                            if (activeTab?.isLoading == true) tabManager.stopActiveTab() else tabManager.reloadActiveTab() 
                        },
                        onHome = { tabManager.navigateTo(activeTabId!!, "blanc://newtab") },
                        onBookmark = { /* Toggle bookmark */ },
                        onShare = { /* Share current page */ },
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
                        onCopyUrl = { /* Copy URL */ },
                        onDesktopSite = { /* Toggle desktop site */ }
                    )
                }
            Screen.NewTab -> NewTabPage(
                tabManager = tabManager,
                onNavigate = { url ->
                    tabManager.navigate(activeTabId!!, url)
                    currentScreen = Screen.Browser
                },
                onNewTab = { tabManager.createTab() },
                onNewPrivateTab = { tabManager.createPrivateTab() }
            )
            Screen.Bookmarks -> BookmarksPage(
                onBack = { currentScreen = Screen.Browser },
                onAddBookmark = { /* Add bookmark */ },
                onBookmarkClick = { url ->
                    tabManager.navigate(activeTabId!!, url)
                    currentScreen = Screen.Browser
                }
            )
            Screen.History -> HistoryPage(
                onBack = { currentScreen = Screen.Browser },
                onItemClick = { url ->
                    tabManager.navigate(activeTabId!!, url)
                    currentScreen = Screen.Browser
                },
                onClearHistory = { /* Clear history */ }
            )
            Screen.Downloads -> DownloadsPage(
                onBack = { currentScreen = Screen.Browser },
                downloads = emptyList(), // Would come from repository
                onOpenFile = { /* Open file */ },
                onShowInFolder = { /* Show in folder */ },
                onCancel = { /* Cancel */ },
                onRetry = { /* Retry */ },
                onClearCompleted = { /* Clear completed */ }
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
            onShare = { /* Share */ showMenu = false },
            onCopyUrl = { /* Copy URL */ showMenu = false },
            onDesktopSite = { /* Toggle desktop site */ showMenu = false }
        )
    }
    
    // Find in page overlay
    if (showFindInPage) {
        FindInPageOverlay(
            activeTab = activeTab,
            onDismiss = { showFindInPage = false },
            onFindNext = { /* Find next */ },
            onFindPrevious = { /* Find previous */ }
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
    val addressBarText = activeTab?.url ?: ""
    val isAddressBarFocused = false // Would track focus state
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        // Top app bar
        BrowserTopBar(
            activeTab = activeTab,
            tabs = tabs,
            activeTabId = activeTabId,
            isLoading = isLoading,
            progress = progress,
            blockedCount = blockedCount,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            addressBarText = addressBarText,
            isAddressBarFocused = isAddressBarFocused,
            onAddressBarTextChange = { /* Update text */ },
            onAddressBarFocusChange = { /* isAddressBarFocused = it */ },
            onNavigate = onNavigate,
            onBack = onBack,
            onForward = onForward,
            onReload = onReload,
            onHome = onHome,
            onBookmark = onBookmark,
            onShare = onShare,
            onMenu = onMenu,
            onTabSwitcher = onTabSwitcher,
            onNewTab = onNewTab,
            onNewPrivateTab = onNewPrivateTab
        )
        
        // Content area
        Box(modifier = Modifier.fillMaxSize()) {
            if (activeTab != null) {
                ContentWebViewContainer(
                    tabManager = tabManager,
                    activeTab = activeTab,
                    onFileChooser = onFileChooser,
                    onRequestPermission = onRequestPermission,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // No active tab - show new tab page
                NewTabPage(
                    tabManager = tabManager,
                    onNavigate = onNavigate,
                    onNewTab = onNewTab,
                    onNewPrivateTab = onNewPrivateTab
                )
            }
        }
    }
}

/**
 * Top app bar with address bar and controls.
 */
@Composable
fun BrowserTopBar(
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
    onNewPrivateTab: () -> Unit
) {
    val isPrivate = activeTab?.isPrivate == true
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isPrivate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            
            // Forward button
            IconButton(
                onClick = onForward,
                enabled = canGoForward,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_forward),
                    contentDescription = "Forward",
                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            
            // Home button
            IconButton(
                onClick = onHome,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_home),
                    contentDescription = "Home",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Address bar
            AddressBar(
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
                    .padding(horizontal = 8.dp)
            )
            
            // Menu button
            IconButton(
                onClick = onMenu,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Tab switcher button
            IconButton(
                onClick = onTabSwitcher,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tab),
                    contentDescription = "Tabs (${tabs.size})",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Address bar with URL display, progress, and blocked count.
 */
@Composable
fun AddressBar(
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
    val isEditing = remember { mutableStateOf(isFocused) }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp)),
        color = if (isPrivate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lock/private icon
            Icon(
                painter = if (isPrivate) painterResource(R.drawable.ic_private_tab) else painterResource(R.drawable.ic_info),
                contentDescription = if (isPrivate) "Private" else "Secure",
                tint = if (isPrivate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 12.dp)
            )
            
            // URL text field or display
            if (isEditing.value) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { onNavigate(text) }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            } else {
                Text(
                    text = if (text.isBlank()) "Search or enter address" else text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .fillMaxHeight()
                        .wrapContentWidth()
                        .clickable {
                            isEditing.value = true
                            onFocusChange(true)
                        }
                )
            }
            
            // Progress indicator / Reload / Stop
            if (isLoading) {
                // Progress bar
                CircularProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                // Reload button
                IconButton(
                    onClick = onReload,
                    modifier = Modifier.size(40.dp).padding(end = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = "Reload",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Blocked count badge
            if (blockedCount > 0) {
                Surface(
                    modifier = Modifier
                        .height(20.dp)
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "$blockedCount",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
            
            // Bookmark button
            IconButton(
                onClick = onBookmark,
                modifier = Modifier.size(40.dp).padding(end = 4.dp)
            ) {
                Icon(
                    painter = if (false /* check if bookmarked */) 
                        painterResource(R.drawable.ic_bookmark_filled) 
                    else painterResource(R.drawable.ic_bookmark),
                    contentDescription = "Bookmark",
                    tint = MaterialTheme.colorScheme.onSurface
                )
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
    onShare: () -> Unit,
    onCopyUrl: () -> Unit,
    onDesktopSite: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
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
                .padding(top = 8.dp, end = 8.dp)
                .clip(RoundedCornerShape(16.dp)),
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
                    text = "Request desktop site",
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
 * Find in page overlay.
 */
@Composable
fun FindInPageOverlay(
    activeTab: Tab?,
    onDismiss: () -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit
) {
    val query = remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                    onValueChange = { query.value = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Find in page") },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { /* Find */ }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
                
                IconButton(onClick = onFindPrevious) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Previous")
                }
                
                IconButton(onClick = onFindNext) {
                    Icon(painterResource(R.drawable.ic_forward), contentDescription = "Next")
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                }
            }
        }
    }
}