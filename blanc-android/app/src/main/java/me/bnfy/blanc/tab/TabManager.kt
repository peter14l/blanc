package me.bnfy.blanc.tab

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.annotation.Keep
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.bnfy.blanc.adblock.AdblockEngine
import me.bnfy.blanc.storage.ClosedTabEntity
import me.bnfy.blanc.ui.ContentWebView
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the lifecycle of browser tabs.
 *
 * TabManager is the central coordinator for all tab operations:
 * - Creating new tabs (regular and private)
 * - Switching between tabs (showing/hiding WebViews)
 * - Closing tabs and cleaning up resources
 * - Maintaining tab order and active tab state
 * - Handling tab state persistence/restoration
 *
 * Only one tab's WebView is visible at a time. Inactive tabs' WebViews
 * are hidden but kept in memory for fast switching. Private tabs use
 * a separate cookie store and don't persist history.
 */
@Keep
class TabManager(
    private val context: Context,
    private val webViewFactory: WebViewFactory,
    private val adblockEngine: AdblockEngine? = null,
    initialBridge: BlancBridge? = null
) {

    // Overload constructor for backwards compatibility with TabManager(context, bridge, webViewFactory)
    constructor(
        context: Context,
        bridge: BlancBridge?,
        webViewFactory: WebViewFactory
    ) : this(context, webViewFactory, null, bridge)

    private var currentBridge: BlancBridge = initialBridge ?: object : BlancBridge {}

    val bridge: BlancBridge
        get() = currentBridge

    fun setBridge(bridge: BlancBridge) {
        this.currentBridge = bridge
    }

    private val bridgeDelegate = object : BlancBridge {
        override fun onTabCreated(tab: Tab) = currentBridge.onTabCreated(tab)
        override fun onTabUpdated(tab: Tab) = currentBridge.onTabUpdated(tab)
        override fun onTabClosed(tabId: String) = currentBridge.onTabClosed(tabId)
        override fun onTabSwitched(tabId: String) = currentBridge.onTabSwitched(tabId)
        override fun onNavigation(tabId: String, url: String, title: String?) = currentBridge.onNavigation(tabId, url, title)
        override fun onProgress(tabId: String, progress: Int) = currentBridge.onProgress(tabId, progress)
        override fun onCreateWindowRequested(url: String, isPrivate: Boolean) = currentBridge.onCreateWindowRequested(url, isPrivate)
        override fun onGroupCreated(group: TabGroup) = currentBridge.onGroupCreated(group)
        override fun onGroupUpdated(group: TabGroup) = currentBridge.onGroupUpdated(group)
        override fun onGroupClosed(groupId: String) = currentBridge.onGroupClosed(groupId)
        override fun onWindowClosed(windowId: String) = currentBridge.onWindowClosed(windowId)
        override fun onWindowFocused(windowId: String) = currentBridge.onWindowFocused(windowId)
        override fun onSurfaceClosed(windowId: String) = currentBridge.onSurfaceClosed(windowId)
        override fun onToast(message: String) = currentBridge.onToast(message)
        override fun onPermissionRequested(tabId: String, origin: String, type: String, callback: PermissionCallback) =
            currentBridge.onPermissionRequested(tabId, origin, type, callback)
        override fun onFileChooserRequested(tabId: String, mode: Int, acceptTypes: Array<String>?, capture: Boolean, callback: FileChooserCallback) =
            currentBridge.onFileChooserRequested(tabId, mode, acceptTypes, capture, callback)
    }

    private val tabs = ConcurrentHashMap<String, Tab>()
    private val tabOrder = mutableListOf<String>()
    private var activeTabId: String? = null

    // ===== Reactive State (for Compose) =====
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabsFlow: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabIdFlow: StateFlow<String?> = _activeTabId.asStateFlow()

    private val _tabCount = MutableStateFlow<Int>(0)
    val tabCountFlow: StateFlow<Int> = _tabCount.asStateFlow()

    private val _groups = MutableStateFlow<List<TabGroup>>(emptyList())
    val groupsFlow: StateFlow<List<TabGroup>> = _groups.asStateFlow()

    private val _windows = MutableStateFlow<List<Window>>(emptyList())
    val windowsFlow: StateFlow<List<Window>> = _windows.asStateFlow()

    private val _activeWindowId = MutableStateFlow<String>("default")
    val activeWindowIdFlow: StateFlow<String> = _activeWindowId.asStateFlow()

    val mainHandler = Handler(Looper.getMainLooper())

    private var onTabCountChanged: ((Int) -> Unit)? = null

    // Thumbnail capture
    private val thumbnailExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var thumbnailCaptureRunnable: Runnable? = null

    // Update reactive state
    private fun updateReactiveState() {
        _tabs.value = tabOrder.mapNotNull { tabs[it] }
        _tabCount.value = tabs.size
        _groups.value = tabGroups.values.toList()
        _windows.value = windowOrder.mapNotNull { windows[it] }
    }

    /**
     * Captures thumbnails for all visible tabs.
     * Called when app goes to background or periodically.
     */
    fun captureAllThumbnails() {
        thumbnailExecutor.execute {
            tabs.values.forEach { tab ->
                tab.webView?.let { webView ->
                    if (webView is ContentWebView) {
                        val thumbnail = webView.captureThumbnail()
                        if (thumbnail != null) {
                            mainHandler.post {
                                tab.thumbnail = thumbnail
                                updateReactiveState()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Captures thumbnail for a specific tab.
     */
    fun captureTabThumbnail(tabId: String) {
        tabs[tabId]?.webView?.let { webView ->
            if (webView is ContentWebView) {
                thumbnailExecutor.execute {
                    val thumbnail = webView.captureThumbnail()
                    if (thumbnail != null) {
                        mainHandler.post {
                            tabs[tabId]?.thumbnail = thumbnail
                            updateReactiveState()
                        }
                    }
                }
            }
        }
    }

    /**
     * Sets a callback for tab count changes.
     */
    fun setOnTabCountChanged(listener: (Int) -> Unit) {
        onTabCountChanged = listener
    }

    // ===== Tab Creation =====

    /**
     * Creates a new regular tab.
     * @param url Initial URL to load (default: blanc://newtab)
     * @return The created Tab, or null if creation failed.
     */
    fun createTab(url: String = "blanc://newtab"): Tab? {
        return createTabInternal(url, false)
    }

    /**
     * Creates a new private/incognito tab.
     * @param url Initial URL to load (default: blanc://newtab?private=1)
     * @return The created Tab, or null if creation failed.
     */
    fun createPrivateTab(url: String = "blanc://newtab?private=1"): Tab? {
        return createTabInternal(url, true)
    }

    /**
     * Internal tab creation logic.
     */
    private fun createTabInternal(initialUrl: String, isPrivate: Boolean): Tab? {
        val windowId = activeWindowId
        val position = tabs.size
        val profileId = windows[windowId]?.profileId ?: "personal"
        
        val tab = if (isPrivate) {
            Tab.createPrivate().copy(
                windowId = windowId,
                position = position,
                profileId = profileId
            )
        } else {
            Tab.create().copy(
                windowId = windowId,
                position = position,
                profileId = profileId
            )
        }

        // Create WebView on main thread
        mainHandler.post {
            try {
                val factory = if (isPrivate) {
                    WebViewFactory.createPrivate(context)
                } else {
                    WebViewFactory.createRegular(context)
                }

                val webViewClient = TabWebViewClient(tab, bridgeDelegate, adblockEngine) { url ->
                    captureTabThumbnail(tab.id)
                }
                val webChromeClient = TabWebChromeClient(tab, bridgeDelegate, getActivity(), this@TabManager)

                val webView = factory.createWebView(webViewClient, webChromeClient)
                factory.configurePrivateCookies(webView)
                factory.applyAdblockSettings(webView)

                tab.webView = webView
                tab.url = initialUrl

                // Add to collections
                tabs[tab.id] = tab
                tabOrder.add(tab.id)
                
                // Add to window
                val window = windows[windowId]
                window?.tabIds?.add(tab.id)

                // Load initial URL if not an internal surface
                if (!initialUrl.startsWith("blanc://") && initialUrl != "about:blank") {
                    webView.loadUrl(initialUrl)
                }

                // Update reactive state
                updateReactiveState()

                // Notify bridge
                bridge.onTabCreated(tab.toBridgeTab())

                // Automatically switch to the newly created tab
                switchTab(tab.id)

                onTabCountChanged?.invoke(tabs.size)
            } catch (e: Exception) {
                Log.e("TabManager", "Failed to create tab", e)
            }
        }

        return tab
    }

    /**
     * Gets the Activity for WebChromeClient. In a real app, this would be passed in.
     * For now, we'll need to set it separately.
     */
    private var activityReference: java.lang.ref.WeakReference<android.app.Activity>? = null

    fun setActivity(activity: android.app.Activity) {
        activityReference = java.lang.ref.WeakReference(activity)
    }

    private fun getActivity(): android.app.Activity? {
        return activityReference?.get()
    }

    // ===== Tab Switching =====

    /**
     * Switches to the specified tab, making it visible and active.
     * @param tabId The ID of the tab to activate.
     * @return true if the tab was found and switched to.
     */
    fun switchTab(tabId: String): Boolean {
        return mainHandler.run {
            val tab = tabs[tabId]
            if (tab == null) return@run false

            // Hide previously active tab
            activeTabId?.let { prevId ->
                tabs[prevId]?.apply {
                    webView?.visibility = WebView.INVISIBLE
                    isActive = false
                }
            }

            // Show new active tab
            tab.webView?.visibility = WebView.VISIBLE
            tab.isActive = true
            activeTabId = tabId
            _activeTabId.value = tabId

            // Update window's active tab
            val window = windows[tab.windowId]
            window?.let {
                val updatedWindow = it.copy(activeTabId = tabId, updatedAt = System.currentTimeMillis())
                windows[tab.windowId] = updatedWindow
            }

            // Bring WebView to front if in a container
            tab.webView?.bringToFront()

            // Update reactive state
            updateReactiveState()

            // Notify bridge
            bridge.onTabSwitched(tabId)
            bridge.onTabUpdated(tab.toBridgeTab())

            true
        }
    }

    /**
     * Gets the currently active tab.
     */
    fun getActiveTab(): Tab? {
        return activeTabId?.let { tabs[it] }
    }

    /**
     * Gets the ID of the currently active tab.
     */
    fun getActiveTabId(): String? = activeTabId

    // ===== Tab Closing =====

    /**
     * Closes a tab by ID.
     * @param tabId The ID of the tab to close.
     * @return true if the tab was found and closed.
     */
    fun closeTab(tabId: String): Boolean {
        return mainHandler.run {
            val tab = tabs.remove(tabId)
            if (tab == null) return@run false

            // Remove from order
            tabOrder.remove(tabId)

            // Remove from window's tabIds
            val window = windows[tab.windowId]
            window?.tabIds?.remove(tabId)

            // Remove from group
            tab.groupId?.let { groupId ->
                tabGroups[groupId]?.tabIds?.remove(tabId)
            }

            // Destroy WebView
            tab.webView?.apply {
                clearHistory()
                loadUrl("about:blank")
                stopLoading()
                destroy()
            }
            tab.webView = null

            // Clear active tab ID if this was the active tab
            if (activeTabId == tabId) {
                activeTabId = null
                _activeTabId.value = null
            }

            // Update reactive state
            updateReactiveState()

            // Notify bridge
            bridge.onTabClosed(tabId)

            // If we closed the active tab, select another
            if (activeTabId == null) {
                selectAdjacentTab(tabId)
            }

            onTabCountChanged?.invoke(tabs.size)
            true
        }
    }

    /**
     * Closes the currently active tab.
     */
    fun closeActiveTab(): Boolean {
        activeTabId?.let { closeTab(it) } ?: return false
        return true
    }

    /**
     * Selects an adjacent tab after the current one is closed.
     * Prefers the next tab, falls back to previous.
     */
    private fun selectAdjacentTab(closedTabId: String) {
        val index = tabOrder.indexOf(closedTabId)
        if (index >= 0 && tabOrder.isNotEmpty()) {
            // Try next tab first
            var nextIndex = index
            if (nextIndex >= tabOrder.size) {
                nextIndex = tabOrder.size - 1
            }
            val nextTabId = tabOrder[nextIndex]
            switchTab(nextTabId)
        } else if (tabOrder.isNotEmpty()) {
            // Fallback: select first tab
            val firstTabId = tabOrder.first()
            switchTab(firstTabId)
        }
    }

    /**
     * Closes all tabs.
     */
    fun closeAllTabs() {
        mainHandler.post {
            val tabIds = tabs.keys.toList()
            tabIds.forEach { closeTab(it) }
            updateReactiveState()
        }
    }

    // ===== Tab Queries =====

    /**
     * Gets a tab by ID.
     */
    fun getTab(tabId: String): Tab? = tabs[tabId]

    /**
     * Gets all tabs in order.
     */
    fun getAllTabs(): List<Tab> = tabOrder.mapNotNull { tabs[it] }

    /**
     * Gets all tab IDs in order.
     */
    fun getAllTabIds(): List<String> = tabOrder.toList()

    /**
     * Gets the number of open tabs.
     */
    fun getTabCount(): Int = tabs.size

    /**
     * Checks if a tab exists.
     */
    fun hasTab(tabId: String): Boolean = tabs.containsKey(tabId)

    /**
     * Moves a tab to a new position in the tab order.
     */
    fun moveTab(tabId: String, newIndex: Int): Boolean {
        return mainHandler.run {
            val currentIndex = tabOrder.indexOf(tabId)
            if (currentIndex < 0) return@run false

            val clampedIndex = newIndex.coerceIn(0, tabOrder.size - 1)
            if (currentIndex == clampedIndex) return@run true

            tabOrder.removeAt(currentIndex)
            tabOrder.add(clampedIndex, tabId)
            updateReactiveState()
            true
        }
    }

    // ===== Tab State Updates =====

    /**
     * Updates a tab's URL and reloads.
     */
    fun navigateTo(tabId: String, url: String): Boolean {
        return mainHandler.run {
            val tab = tabs[tabId] ?: return@run false
            tab.url = url
            if (!url.startsWith("blanc://") && url != "about:blank") {
                tab.webView?.loadUrl(url)
            } else {
                tab.title = if (url.startsWith("blanc://newtab")) "New Tab" else ""
                tab.webView?.loadUrl("about:blank")
            }
            updateReactiveState()
            true
        }
    }

    fun navigate(tabId: String, url: String): Boolean = navigateTo(tabId, url)

    /**
     * Reloads the active tab.
     */
    fun reloadActiveTab(): Boolean {
        return activeTabId?.let { navigateTo(it, tabs[it]?.url ?: "about:blank") } ?: false
    }

    /**
     * Stops loading in the active tab.
     */
    fun stopActiveTab(): Boolean {
        return activeTabId?.let {
            tabs[it]?.webView?.stopLoading()
            true
        } ?: false
    }

    /**
     * Navigates back in the active tab's history.
     */
    fun goBack(): Boolean {
        return activeTabId?.let { goBack(it) } ?: false
    }

    /**
     * Navigates forward in the active tab's history.
     */
    fun goForward(): Boolean {
        return activeTabId?.let { goForward(it) } ?: false
    }

    /**
     * Checks if the active tab can go back.
     */
    fun canGoBack(): Boolean = getActiveTab()?.canGoBack ?: false

    /**
     * Checks if the active tab can go forward.
     */
    fun canGoForward(): Boolean = getActiveTab()?.canGoForward ?: false

    // ===== Private Mode Helpers =====

    /**
     * Checks if the active tab is private.
     */
    fun isActiveTabPrivate(): Boolean = getActiveTab()?.isPrivate ?: false

    /**
     * Gets all private tabs.
     */
    fun getPrivateTabs(): List<Tab> = tabs.values.filter { it.isPrivate }.toList()

    /**
     * Gets all regular (non-private) tabs.
     */
    fun getRegularTabs(): List<Tab> = tabs.values.filter { !it.isPrivate }.toList()

    // ===== Cleanup =====

    /**
     * Destroys all tabs and releases resources.
     * Should be called when the app is closing.
     */
    fun destroy() {
        mainHandler.post {
            closeAllTabs()
            tabs.clear()
            tabOrder.clear()
            activeTabId = null
        }
    }

    /**
     * Called when the app goes to background.
     * Can be used to pause timers, etc.
     */
    fun onPause() {
        // Pause any ongoing operations
        tabs.values.forEach { tab ->
            tab.webView?.onPause()
        }
        // Capture thumbnails for tab switcher
        captureAllThumbnails()
    }

    /**
     * Called when the app comes to foreground.
     */
    fun onResume() {
        tabs.values.forEach { tab ->
            tab.webView?.onResume()
        }
    }

    /**
     * Recreates a tab after its renderer process crashed.
     * Preserves tab state (URL, history, private mode, etc.)
     */
    fun recreateTabAfterCrash(tabId: String, url: String, isPrivate: Boolean) {
        mainHandler.post {
            val oldTab = tabs[tabId]
            if (oldTab == null) return@post
            
            // Preserve state
            val history = oldTab.history.toMutableList()
            val historyIndex = oldTab.historyIndex
            val canGoBack = oldTab.canGoBack
            val canGoForward = oldTab.canGoForward
            val title = oldTab.title
            val windowId = oldTab.windowId
            val isPinned = oldTab.isPinned
            val isMuted = oldTab.isMuted
            val groupId = oldTab.groupId
            val position = oldTab.position
            val profileId = oldTab.profileId
            val workspaceId = oldTab.workspaceId
            val favicon = oldTab.favicon
            
            // Create new tab with same ID to preserve identity
            val factory = if (isPrivate) {
                WebViewFactory.createPrivate(context)
            } else {
                WebViewFactory.createRegular(context)
            }

            val webViewClient = TabWebViewClient(oldTab, bridgeDelegate, adblockEngine) { url ->
                captureTabThumbnail(oldTab.id)
            }
            val webChromeClient = TabWebChromeClient(oldTab, bridgeDelegate, getActivity(), this@TabManager)

            val webView = factory.createWebView(webViewClient, webChromeClient)
            factory.configurePrivateCookies(webView)
            factory.applyAdblockSettings(webView)

            // Create new tab with preserved state
            val newTab = Tab(
                id = tabId,
                webView = webView,
                url = url,
                title = title,
                isPrivate = isPrivate,
                history = history,
                historyIndex = historyIndex,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                windowId = windowId,
                isPinned = isPinned,
                isMuted = isMuted,
                groupId = groupId,
                position = position,
                profileId = profileId,
                workspaceId = workspaceId,
                favicon = favicon
            )

            // Replace in collections
            tabs[tabId] = newTab
            
            // Update window
            val window = windows[windowId]
            window?.tabIds?.remove(tabId)
            window?.tabIds?.add(tabId)
            
            // Reload URL
            webView.loadUrl(url)
            
            updateReactiveState()
            
            // If this was the active tab, switch to it
            if (activeTabId == tabId) {
                switchTab(tabId)
            }
        }
    }

    /**
     * Handles low memory warning.
     * Could destroy background tabs to free memory.
     */
    fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // Could implement tab hibernation here
        }
    }

    // ===== Session Persistence =====

    /**
     * Serializes current tab state for session restoration.
     */
    fun saveSession(): List<TabState> {
        return tabOrder.mapNotNull { tabId ->
            tabs[tabId]?.let { tab ->
                TabState(
                    id = tab.id,
                    url = tab.url,
                    title = tab.title,
                    isPrivate = tab.isPrivate,
                    history = tab.history.toList(),
                    historyIndex = tab.historyIndex,
                    canGoBack = tab.canGoBack,
                    canGoForward = tab.canGoForward,
                    windowId = tab.windowId,
                    isPinned = tab.isPinned,
                    isMuted = tab.isMuted,
                    groupId = tab.groupId,
                    position = tab.position,
                    profileId = tab.profileId,
                    workspaceId = tab.workspaceId
                )
            }
        }
    }

    /**
     * Restores tabs from saved session state.
     */
    fun restoreSession(states: List<TabState>) {
        mainHandler.post {
            closeAllTabs()
            states.forEach { state ->
                val factory = if (state.isPrivate) {
                    WebViewFactory.createPrivate(context)
                } else {
                    WebViewFactory.createRegular(context)
                }

                val webViewClient = TabWebViewClient(Tab(), bridgeDelegate, adblockEngine) { url ->
                    captureTabThumbnail(state.id)
                }
                val webChromeClient = TabWebChromeClient(Tab(), bridgeDelegate, getActivity(), this@TabManager)

                val webView = factory.createWebView(webViewClient, webChromeClient)
                factory.configurePrivateCookies(webView)

                val tab = Tab(
                    id = state.id,
                    webView = webView,
                    url = state.url,
                    title = state.title,
                    isPrivate = state.isPrivate,
                    history = state.history.toMutableList(),
                    historyIndex = state.historyIndex,
                    canGoBack = state.canGoBack,
                    canGoForward = state.canGoForward,
                    windowId = state.windowId,
                    isPinned = state.isPinned,
                    isMuted = state.isMuted,
                    groupId = state.groupId,
                    position = state.position,
                    profileId = state.profileId,
                    workspaceId = state.workspaceId
                )

                // Re-create clients with correct tab reference
                val newWebViewClient = TabWebViewClient(tab, bridgeDelegate, adblockEngine) { url ->
                    captureTabThumbnail(tab.id)
                }
                val newWebChromeClient = TabWebChromeClient(tab, bridgeDelegate, getActivity(), this@TabManager)
                webView.webViewClient = newWebViewClient
                webView.webChromeClient = newWebChromeClient

                tabs[tab.id] = tab
                tabOrder.add(tab.id)

                // Add to window
                val window = windows[state.windowId]
                window?.tabIds?.add(tab.id)

                webView.loadUrl(state.url)
            }

            // Activate first tab
            tabOrder.firstOrNull()?.let { switchTab(it) }
            updateReactiveState()
            onTabCountChanged?.invoke(tabs.size)
        }
    }

    /**
     * Data class for session persistence.
     */
    data class TabState(
        val id: String,
        val url: String,
        val title: String,
        val isPrivate: Boolean,
        val history: List<String>,
        val historyIndex: Int,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
        val windowId: String = "default",
        val isPinned: Boolean = false,
        val isMuted: Boolean = false,
        val groupId: String? = null,
        val position: Int = 0,
        val profileId: String = "personal",
        val workspaceId: String? = null
    )

    // ===== Additional Methods for Bridge =====

    /**
     * Reloads a specific tab.
     */
    fun reloadTab(tabId: String): Boolean {
        return mainHandler.run {
            val tab = tabs[tabId] ?: return@run false
            tab.webView?.reload()
            true
        }
    }

    /**
     * Navigates back in a specific tab's history.
     */
    fun goBack(tabId: String): Boolean {
        return mainHandler.run {
            val tab = tabs[tabId] ?: return@run false
            if (tab.webView?.canGoBack() == true) {
                tab.webView?.goBack()
                true
            } else {
                val url = tab.goBack()
                url?.let { tab.webView?.loadUrl(it) }
                url != null
            }
        }
    }

    /**
     * Navigates forward in a specific tab's history.
     */
    fun goForward(tabId: String): Boolean {
        return mainHandler.run {
            val tab = tabs[tabId] ?: return@run false
            if (tab.webView?.canGoForward() == true) {
                tab.webView?.goForward()
                true
            } else {
                val url = tab.goForward()
                url?.let { tab.webView?.loadUrl(it) }
                url != null
            }
        }
    }

    /**
     * Sets tab pinned state.
     */
    fun setTabPinned(tabId: String, pinned: Boolean): Boolean {
        return mainHandler.run {
            val tab = tabs[tabId] ?: return@run false
            tab.isPinned = pinned
            if (pinned) {
                moveTabToPinnedSection(tabId)
            } else {
                moveTabToUnpinnedSection(tabId)
            }
            updateReactiveState()
            bridge.onTabUpdated(tab.toBridgeTab())
            true
        }
    }

    /**
     * Sets tab muted state.
     */
    fun setTabMuted(tabId: String, muted: Boolean): Boolean {
        return mainHandler.run {
            val tab = tabs[tabId] ?: return@run false
            tab.isMuted = muted
            updateReactiveState()
            bridge.onTabUpdated(tab.toBridgeTab())
            true
        }
    }

    // ===== Tab Group Methods =====

    private val tabGroups = ConcurrentHashMap<String, TabGroup>()
    private val windows = ConcurrentHashMap<String, Window>()
    private val windowOrder = mutableListOf<String>()
    private var activeWindowId: String = "default"

    init {
        // Create default window
        val defaultWindow = Window(id = "default", label = "Window 1", profileId = "personal")
        windows["default"] = defaultWindow
        windowOrder.add("default")
        activeWindowId = "default"
    }

    fun createGroup(windowId: String, name: String): TabGroup {
        return mainHandler.run {
            val window = windows[windowId] ?: return@run TabGroup(windowId = windowId, name = name)
            val group = TabGroup(
                windowId = windowId,
                name = name,
                position = window.groupIds.size
            )
            tabGroups[group.id] = group
            window.groupIds.add(group.id)
            window.updatedAt = System.currentTimeMillis()
            updateReactiveState()
            bridge.onGroupCreated(group)
            group
        }
    }

    fun renameGroup(groupId: String, name: String): Boolean {
        return mainHandler.run {
            val group = tabGroups[groupId] ?: return@run false
            val updatedGroup = group.copy(name = name)
            tabGroups[groupId] = updatedGroup
            updateReactiveState()
            bridge.onGroupUpdated(updatedGroup)
            true
        }
    }

    fun setGroupCollapsed(groupId: String, collapsed: Boolean): Boolean {
        return mainHandler.run {
            val group = tabGroups[groupId] ?: return@run false
            val updatedGroup = group.copy(isCollapsed = collapsed)
            tabGroups[groupId] = updatedGroup
            updateReactiveState()
            bridge.onGroupUpdated(updatedGroup)
            true
        }
    }

    fun moveTabToGroup(tabId: String, groupId: String?): Boolean {
        return mainHandler.run {
            val tab = tabs[tabId] ?: return@run false
            val oldGroupId = tab.groupId
            
            oldGroupId?.let { oldId ->
                tabGroups[oldId]?.tabIds?.remove(tabId)
                oldId.let { tabGroups[it]?.let { bridge.onGroupUpdated(it) } }
            }

            groupId?.let { newId ->
                tabGroups[newId]?.tabIds?.add(tabId)
                tabGroups[newId]?.let { bridge.onGroupUpdated(it) }
            }

            tab.groupId = groupId
            updateReactiveState()
            bridge.onTabUpdated(tab.toBridgeTab())
            true
        }
    }

    fun closeGroup(groupId: String): Boolean {
        return mainHandler.run {
            val group = tabGroups.remove(groupId) ?: return@run false
            val window = windows[group.windowId]
            window?.groupIds?.remove(groupId)
            
            group.tabIds.forEach { tabId ->
                closeTab(tabId)
            }
            
            updateReactiveState()
            bridge.onGroupClosed(groupId)
            true
        }
    }

    fun focusGroup(groupId: String): Boolean {
        return mainHandler.run {
            val group = tabGroups[groupId] ?: return@run false
            group.tabIds.firstOrNull()?.let { tabId ->
                switchTab(tabId)
            }
            true
        }
    }

    fun getGroupsForWindow(windowId: String): List<TabGroup> {
        return mainHandler.run {
            val window = windows[windowId] ?: return@run emptyList()
            window.groupIds.mapNotNull { tabGroups[it] }
        }
    }

    fun addTabToGroup(tabId: String, groupId: String): Boolean {
        return mainHandler.run {
            val tab = tabs[tabId] ?: return@run false
            val group = tabGroups[groupId] ?: return@run false
            
            tab.groupId?.let { oldId ->
                tabGroups[oldId]?.tabIds?.remove(tabId)
            }
            
            group.tabIds.add(tabId)
            tab.groupId = groupId
            bridge.onTabUpdated(tab.toBridgeTab())
            bridge.onGroupUpdated(group)
            true
        }
    }

    // ===== Window Methods (simulated on Android) =====

    fun getActiveWindowId(): String = activeWindowId

    fun getAllWindows(): List<Window> = windowOrder.mapNotNull { windows[it] }

    fun getTabsForWindow(windowId: String): List<Tab> {
        return mainHandler.run {
            val window = windows[windowId] ?: return@run emptyList()
            window.tabIds.mapNotNull { tabs[it] }
        }
    }

    fun createWindow(label: String, profileId: String): Window {
        return mainHandler.run {
            val window = Window(
                label = label,
                profileId = profileId
            )
            windows[window.id] = window
            windowOrder.add(window.id)
            activeWindowId = window.id
            _activeWindowId.value = window.id
            updateReactiveState()
            window
        }
    }

    fun closeWindow(windowId: String): Boolean {
        return mainHandler.run {
            val window = windows.remove(windowId) ?: return@run false
            windowOrder.remove(windowId)
            
            window.tabIds.forEach { tabId ->
                closeTab(tabId)
            }
            
            window.groupIds.forEach { groupId ->
                tabGroups.remove(groupId)
            }
            
            if (activeWindowId == windowId) {
                activeWindowId = windowOrder.firstOrNull() ?: "default"
                _activeWindowId.value = activeWindowId
                if (!windows.containsKey(activeWindowId)) {
                    val defaultWindow = Window(id = "default", label = "Window 1", profileId = "personal")
                    windows["default"] = defaultWindow
                    windowOrder.add(0, "default")
                    activeWindowId = "default"
                    _activeWindowId.value = "default"
                }
            }
            
            updateReactiveState()
            bridge.onWindowClosed(windowId)
            true
        }
    }

    fun focusWindow(windowId: String): Boolean {
        return mainHandler.run {
            val window = windows[windowId] ?: return@run false
            activeWindowId = windowId
            _activeWindowId.value = windowId
            window.activeTabId?.let { switchTab(it) }
            updateReactiveState()
            bridge.onWindowFocused(windowId)
            true
        }
    }

    fun setViewport(windowId: String, x: Double, y: Double, width: Double, height: Double, hidden: Boolean): Boolean {
        return mainHandler.run {
            val window = windows[windowId] ?: return@run false
            val updatedWindow = window.copy(
                boundsX = x.toFloat(),
                boundsY = y.toFloat(),
                boundsWidth = width.toFloat(),
                boundsHeight = height.toFloat(),
                isHidden = hidden,
                updatedAt = System.currentTimeMillis()
            )
            windows[windowId] = updatedWindow
            updateReactiveState()
            true
        }
    }

    fun closeSurface(windowId: String): Boolean {
        bridge.onSurfaceClosed(windowId)
        return true
    }

    /**
     * Reopens a closed tab from a closed tab entry.
     */
    fun reopenClosedTab(entry: ClosedTabEntity, windowId: String): Tab? {
        return mainHandler.run {
            val isPrivate = entry.isPrivate
            val profileId = entry.profileId
            val factory = if (isPrivate) {
                WebViewFactory.createPrivate(context)
            } else {
                WebViewFactory.createRegular(context)
            }

            val webViewClient = TabWebViewClient(Tab(), bridgeDelegate, adblockEngine) { url ->
                    captureTabThumbnail(entry.tabId)
                }
                val webChromeClient = TabWebChromeClient(Tab(), bridgeDelegate, getActivity(), this@TabManager)
            val webView = factory.createWebView(webViewClient, webChromeClient)
            factory.configurePrivateCookies(webView)

            val historyList = stringToHistory(entry.navigationHistory)
            val tab = Tab(
                id = entry.tabId,
                webView = webView,
                url = entry.url,
                title = entry.title ?: "",
                isPrivate = isPrivate,
                isPinned = entry.isPinned,
                groupId = entry.groupId,
                position = entry.position,
                history = historyList,
                historyIndex = entry.historyIndex,
                canGoBack = entry.historyIndex > 0,
                canGoForward = entry.historyIndex < historyList.size - 1,
                windowId = windowId,
                profileId = profileId,
                workspaceId = windows[windowId]?.workspaceId
            )

            val newWebViewClient = TabWebViewClient(tab, bridgeDelegate, adblockEngine)
            val newWebChromeClient = TabWebChromeClient(tab, bridgeDelegate, getActivity(), this@TabManager)
            webView.webViewClient = newWebViewClient
            webView.webChromeClient = newWebChromeClient

            tabs[tab.id] = tab
            tabOrder.add(tab.position, tab.id)
            
            val window = windows[windowId]
            window?.tabIds?.add(tab.position, tab.id)

            webView.loadUrl(entry.url)
            
            if (entry.isGroup) {
                bridge.onToast("Reopened group: ${entry.groupName}")
            } else {
                switchTab(tab.id)
            }
            
            updateReactiveState()
            tab
        }
    }

    private fun stringToHistory(json: String): MutableList<String> {
        return try {
            json.trim()
                .removePrefix("[")
                .removeSuffix("]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
                .toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    /**
     * Responds to a permission request from a tab.
     */
    fun respondToPermissionRequest(requestId: String, allow: Boolean, remember: Boolean) {
        // Callback stored in TabWebChromeClient
    }

    /**
     * Opens a downloaded file.
     */
    fun openDownload(downloadId: String) {
        bridge.onToast("Open download: $downloadId")
    }

    /**
     * Shows a downloaded file in the system file manager.
     */
    fun showDownloadInFolder(downloadId: String) {
        bridge.onToast("Show in folder: $downloadId")
    }

    /**
     * Cancels a download.
     */
    fun cancelDownload(downloadId: String) {
        bridge.onToast("Cancelled download: $downloadId")
    }

    /**
     * Evaluates JavaScript on the UI thread.
     */
    fun evaluateJavascriptOnUi(script: String) {
        mainHandler.post {
            // UI WebView would evaluate this
        }
    }

    private fun moveTabToPinnedSection(tabId: String) {
        val currentIndex = tabOrder.indexOf(tabId)
        if (currentIndex >= 0) {
            tabOrder.removeAt(currentIndex)
            var insertIndex = 0
            for (i in tabOrder.indices) {
                val t = tabs[tabOrder[i]]
                if (t?.isPinned == true) {
                    insertIndex = i + 1
                } else {
                    break
                }
            }
            tabOrder.add(insertIndex, tabId)
        }
    }

    private fun moveTabToUnpinnedSection(tabId: String) {
        val currentIndex = tabOrder.indexOf(tabId)
        if (currentIndex >= 0) {
            tabOrder.removeAt(currentIndex)
            var insertIndex = tabOrder.size
            for (i in tabOrder.indices) {
                val t = tabs[tabOrder[i]]
                if (t?.isPinned != true) {
                    insertIndex = i
                    break
                }
            }
            tabOrder.add(insertIndex.coerceAtMost(tabOrder.size), tabId)
        }
    }
}

/**
 * Tab group data class for organizing tabs.
 */
@Keep
data class TabGroup(
    val id: String = UUID.randomUUID().toString(),
    val windowId: String,
    val name: String,
    val isCollapsed: Boolean = false,
    val tabIds: MutableList<String> = mutableListOf(),
    val position: Int = 0,
    val profileId: String = "personal",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Window data class for multi-window support (simulated on Android).
 */
@Keep
data class Window(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val profileId: String,
    var activeTabId: String? = null,
    val tabIds: MutableList<String> = mutableListOf(),
    val groupIds: MutableList<String> = mutableListOf(),
    var workspaceId: String? = null,
    val boundsX: Float = 0f,
    val boundsY: Float = 0f,
    val boundsWidth: Float = 0f,
    val boundsHeight: Float = 0f,
    var isMaximized: Boolean = false,
    var isHidden: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)