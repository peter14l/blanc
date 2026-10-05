package me.bnfy.blanc.tab

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.annotation.Keep
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
    private val bridge: BlancBridge,
    private val webViewFactory: WebViewFactory,
    private val adblockEngine: AdblockEngine? = null
) {

    private val tabs = ConcurrentHashMap<String, Tab>()
    private val tabOrder = mutableListOf<String>()
    private var activeTabId: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var onTabCountChanged: ((Int) -> Unit)? = null

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
        val tab = if (isPrivate) Tab.createPrivate() else Tab.create()

        // Create WebView on main thread
        mainHandler.post {
            try {
                val factory = if (isPrivate) {
                    WebViewFactory.createPrivate(context)
                } else {
                    WebViewFactory.createRegular(context)
                }

                val webViewClient = TabWebViewClient(tab, bridge, adblockEngine)
                val webChromeClient = TabWebChromeClient(tab, bridge, getActivity(), this@TabManager)

                val webView = factory.createWebView(webViewClient, webChromeClient)
                factory.configurePrivateCookies(webView)
                factory.applyAdblockSettings(webView)

                tab.webView = webView
                tab.url = initialUrl

                // Add to collections
                tabs[tab.id] = tab
                tabOrder.add(tab.id)

                // Load initial URL
                webView.loadUrl(initialUrl)

                // Notify bridge
                bridge.onTabCreated(tab.toBridgeTab())

                // If this is the first tab, make it active
                if (activeTabId == null) {
                    switchTab(tab.id)
                }

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
    private var activityReference: android.app.Activity? = null

    fun setActivity(activity: android.app.Activity) {
        activityReference = activity
    }

    private fun getActivity(): android.app.Activity {
        return activityReference ?: throw IllegalStateException("Activity not set on TabManager")
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
                tabs[prevId]?.webView?.visibility = WebView.INVISIBLE
            }

            // Show new active tab
            tab.webView?.visibility = WebView.VISIBLE
            activeTabId = tabId

            // Bring WebView to front if in a container
            tab.webView?.bringToFront()

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

            // Destroy WebView
            tab.webView?.apply {
                clearHistory()
                loadUrl("about:blank")
                stopLoading()
                destroy()
            }
            tab.webView = null

            // Notify bridge
            bridge.onTabClosed(tabId)

            // If we closed the active tab, select another
            if (activeTabId == tabId) {
                activeTabId = null
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
        }
    }

    /**
     * Closes all tabs.
     */
    fun closeAllTabs() {
        mainHandler.post {
            val tabIds = tabs.keys.toList()
            tabIds.forEach { closeTab(it) }
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
            tab.webView?.loadUrl(url)
            true
        }
    }

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
        return activeTabId?.let {
            val tab = tabs[it]
            val url = tab?.goBack()
            url?.let { tab?.webView?.loadUrl(it) }
            url != null
        } ?: false
    }

    /**
     * Navigates forward in the active tab's history.
     */
    fun goForward(): Boolean {
        return activeTabId?.let {
            val tab = tabs[it]
            val url = tab?.goForward()
            url?.let { tab?.webView?.loadUrl(it) }
            url != null
        } ?: false
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
                    canGoForward = tab.canGoForward
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

                val webViewClient = TabWebViewClient(Tab(), bridge, adblockEngine)
                val webChromeClient = TabWebChromeClient(Tab(), bridge, getActivity(), this@TabManager)

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
                    canGoForward = state.canGoForward
                )

                // Re-create clients with correct tab reference
                val newWebViewClient = TabWebViewClient(tab, bridge, adblockEngine)
                val newWebChromeClient = TabWebChromeClient(tab, bridge, getActivity(), this@TabManager)
                webView.webViewClient = newWebViewClient
                webView.webChromeClient = newWebChromeClient

                tabs[tab.id] = tab
                tabOrder.add(tab.id)

                webView.loadUrl(state.url)
            }

            // Activate first tab
            tabOrder.firstOrNull()?.let { switchTab(it) }
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
        val canGoForward: Boolean
    )
}