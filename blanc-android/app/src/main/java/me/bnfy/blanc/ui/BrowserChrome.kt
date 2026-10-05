package me.bnfy.blanc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Rect
import androidx.compose.ui.graphics.toRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.OnGloballyPositionedModifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import me.bnfy.blanc.bridge.BridgeProtocol
import me.bnfy.blanc.storage.Repository
import me.bnfy.blanc.tab.Tab
import me.bnfy.blanc.tab.TabManager
import me.bnfy.blanc.adblock.AdblockEngine
import me.bnfy.blanc.bridge.BlancBridge
import kotlinx.coroutines.flow.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.collectAsStateWithLifecycle

/**
 * Main browser chrome screen - composes the address bar, tab strip, and content area.
 */
@Composable
fun BrowserChromeScreen(
    tabManager: TabManager,
    repository: Repository,
    adblockEngine: AdblockEngine,
    blancBridge: BlancBridge,
    onFileChooser: (ValueCallback<Array<android.net.Uri>>, List<String>) -> Unit,
    onRequestPermission: (String, String, (Boolean) -> Unit) -> Unit
) {
    // Collect state from TabManager
    val activeTabId by remember { mutableStateOf<String?>(null) }
    val tabs by remember { mutableStateOf<List<Tab>>(emptyList()) }
    val isLoading by remember { mutableStateOf<Boolean>(false) }
    val progress by remember { mutableStateOf<Int>(0) }
    val blockedCount by remember { mutableStateOf<Int>(0) }
    val canGoBack by remember { mutableStateOf<Boolean>(false) }
    val canGoForward by remember { mutableStateOf<Boolean>(false) }
    val showAddressBar by remember { mutableStateOf<Boolean>(false) }
    val addressBarText by remember { mutableStateOf<String>("") }
    val showTabSwitcher by remember { mutableStateOf<Boolean>(false) }
    val showMenu by remember { mutableStateOf<Boolean>(false) }
    
    // Observe tab state
    val activeTab = tabs.find { it.id == activeTabId }
    
    // Update state from TabManager (in real implementation, this would be a StateFlow)
    // For now, we'll use a simple update mechanism
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Content area - WebView container
        ContentWebViewContainer(
            tabManager = tabManager,
            activeTabId = activeTabId,
            tabs = tabs,
            onFileChooser = onFileChooser,
            onRequestPermission = onRequestPermission,
            modifier = Modifier.fillMaxSize()
        )
        
        // Top chrome (address bar, tabs)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Address bar / Top app bar
            BrowserTopBar(
                activeTab = activeTab,
                tabs = tabs,
                activeTabId = activeTabId,
                isLoading = isLoading,
                progress = progress,
                blockedCount = blockedCount,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                showAddressBar = showAddressBar,
                addressBarText = addressBarText,
                onAddressBarTextChange = { addressBarText = it },
                onNavigate = { url ->
                    tabManager.navigate(activeTabId!!, url)
                    showAddressBar = false
                },
                onBack = { tabManager.goBack(activeTabId!!) },
                onForward = { tabManager.goForward(activeTabId!!) },
                onReload = { 
                    if (isLoading) tabManager.stopActiveTab() else tabManager.reloadActiveTab() 
                },
                onHome = { tabManager.navigateTo(activeTabId!!, "blanc://newtab") },
                onBookmark = { /* Toggle bookmark */ },
                onShare = { /* Share current page */ },
                onMenu = { showMenu = true },
                onTabSwitcher = { showTabSwitcher = true },
                onNewTab = { tabManager.createTab() },
                onNewPrivateTab = { tabManager.createPrivateTab() }
            )
            
            // Tab strip (if tabs exist)
            if (tabs.isNotEmpty()) {
                TabStrip(
                    tabs = tabs,
                    activeTabId = activeTabId,
                    onTabClick = { tabId -> tabManager.switchTab(tabId) },
                    onTabClose = { tabId -> tabManager.closeTab(tabId) },
                    onTabLongClick = { tabId -> /* Show tab menu */ }
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
                onBookmarks = { /* Open bookmarks */ showMenu = false },
                onHistory = { /* Open history */ showMenu = false },
                onDownloads = { /* Open downloads */ showMenu = false },
                onSettings = { /* Open settings */ showMenu = false },
                onFindInPage = { /* Open find */ showMenu = false },
                onShare = { /* Share */ showMenu = false },
                onCopyUrl = { /* Copy URL */ showMenu = false },
                onDesktopSite = { /* Toggle desktop site */ showMenu = false }
            )
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
    showAddressBar: Boolean,
    addressBarText: String,
    onAddressBarTextChange: (String) -> Unit,
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
                    painter = painterResource(id = R.drawable.ic_back),
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
                    painter = painterResource(id = R.drawable.ic_forward),
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
                    painter = painterResource(id = R.drawable.ic_home),
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
                onTextChange = onAddressBarTextChange,
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
                    painter = painterResource(id = R.drawable.ic_menu),
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
                    painter = painterResource(id = R.drawable.ic_tab),
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
    onTextChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditing = remember { mutableStateOf(false) }
    
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
                painter = if (isPrivate) painterResource(id = R.drawable.ic_private_tab) else painterResource(id = R.drawable.ic_info),
                contentDescription = if (isPrivate) "Private" else "Secure",
                tint = if (isPrivate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 12.dp)
            )
            
            // URL text field or display
            if (isEditing.value) {
                androidx.compose.material3.TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions.Default.copy(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Go
                    ),
                    keyboardActions = androidx.compose.ui.text.input.KeyboardActions(
                        onDone = { onNavigate(text) }
                    ),
                    colors = androidx.compose.material3.TextFieldDefaults.textFieldColors(
                        containerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            } else {
                Text(
                    text = if (text.isBlank()) activeTab?.url ?: "Search or enter address" else text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .fillMaxHeight()
                        .wrapContentWidth()
                        .combinedClickable(
                            onClick = { isEditing.value = true },
                            onLongClick = { /* Copy URL */ }
                        )
                )
            }
            
            // Progress indicator / Reload / Stop
            if (isLoading) {
                // Progress bar
                androidx.compose.material.ProgressIndicator(
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
                        painter = painterResource(id = R.drawable.ic_refresh),
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
                    painter = if (activeTab?.let { /* check if bookmarked */ false } == true) 
                        painterResource(id = R.drawable.ic_bookmark_filled) 
                    else painterResource(id = R.drawable.ic_bookmark),
                    contentDescription = "Bookmark",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Tab strip showing tab thumbnails/dots.
 */
@Composable
fun TabStrip(
    tabs: List<Tab>,
    activeTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                val isPrivate = tab.isPrivate
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.2f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { onTabClick(tab.id) },
                            onLongClick = { onTabLongClick(tab.id) }
                        ),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = if (isActive) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favicon
                        Box(
                            modifier = Modifier.size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Gray)
                        ) {
                            // TODO: Load favicon
                        }
                        
                        // Title
                        Text(
                            text = tab.title.ifBlank { tab.url },
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Close button
                        if (isActive || !isPrivate) {
                            IconButton(
                                onClick = { onTabClose(tab.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Close tab",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Private indicator
                        if (isPrivate) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_private_tab),
                                contentDescription = "Private",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                            )
                        }
                    }
                }
            }
            
            // New tab button
            IconButton(
                onClick = { /* New tab */ },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_tab),
                    contentDescription = "New tab",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                .combinedClickable(onClick = onDismiss)
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
                                painter = painterResource(id = R.drawable.ic_private_tab),
                                contentDescription = "New private tab",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Private")
                        }
                        
                        IconButton(onClick = { onNewTab(); onDismiss() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_tab),
                                contentDescription = "New tab",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text("New Tab")
                        }
                        
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                // Tab list
                androidx.compose.foundation.lazy.LazyColumn(
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
                                .combinedClickable(
                                    onClick = { onTabClick(tab.id) },
                                    onLongClick = { /* Show tab menu */ }
                                ),
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
                                                painter = painterResource(id = R.drawable.ic_private_tab),
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
                                        painter = painterResource(id = R.drawable.ic_close),
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
                .combinedClickable(onClick = onDismiss)
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
                    
                    androidx.compose.material.Divider(
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
                
                androidx.compose.material.Divider(
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
                
                androidx.compose.material.Divider(
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
    iconTint: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp)
            .combinedClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
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