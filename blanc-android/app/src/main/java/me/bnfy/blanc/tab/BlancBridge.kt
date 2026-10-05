package me.bnfy.blanc.tab

/**
 * Bridge interface for communicating tab events from native Kotlin to React Native layer.
 * All callbacks are invoked on the main thread.
 */
interface BlancBridge {

    /**
     * Called when a new tab is created and ready for use.
     */
    fun onTabCreated(tab: Tab)

    /**
     * Called when an existing tab's state changes (URL, title, loading, navigation).
     */
    fun onTabUpdated(tab: Tab)

    /**
     * Called when a tab is closed and removed from the manager.
     */
    fun onTabClosed(tabId: String)

    /**
     * Called when the active tab changes.
     */
    fun onTabSwitched(tabId: String)

    /**
     * Called when a navigation occurs within a tab.
     * @param tabId The ID of the tab that navigated.
     * @param url The new URL.
     * @param title The page title, if available.
     */
    fun onNavigation(tabId: String, url: String, title: String?)

    /**
     * Called when page loading progress changes.
     * @param tabId The ID of the tab.
     * @param progress Progress percentage (0-100).
     */
    fun onProgress(tabId: String, progress: Int)

    /**
     * Called when a tab requests to create a new window (e.g., target="_blank").
     * The implementation should create a new tab and return it.
     * @param url The URL to load in the new tab.
     * @param isPrivate Whether the new tab should be private.
     * @return The newly created Tab, or null if creation failed.
     */
    fun onCreateWindowRequested(url: String, isPrivate: Boolean): Tab?
}