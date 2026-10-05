package me.bnfy.blanc.tab

import me.bnfy.blanc.storage.TabGroupEntity

/**
 * Bridge interface for communicating tab events from native Kotlin to React Native layer.
 * All callbacks are invoked on the main thread.
 * 
 * This interface is implemented by the React Native bridge (BlancBridge in bridge package)
 * and also extended by TabWebChromeClient for additional Chrome client events.
 */
interface BlancBridge {

    // ===== Tab Events =====

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

    // ===== Tab Group Events =====

    /**
     * Called when a new tab group is created.
     */
    fun onGroupCreated(group: TabGroup)

    /**
     * Called when a tab group is updated (renamed, collapsed, tabs moved).
     */
    fun onGroupUpdated(group: TabGroup)

    /**
     * Called when a tab group is closed.
     */
    fun onGroupClosed(groupId: String)

    // ===== Window Events =====

    /**
     * Called when a window is closed.
     */
    fun onWindowClosed(windowId: String)

    /**
     * Called when a window is focused.
     */
    fun onWindowFocused(windowId: String)

    // ===== Surface Events =====

    /**
     * Called when a utility surface is closed.
     */
    fun onSurfaceClosed(windowId: String)

    // ===== Toast/Notification Events =====

    /**
     * Called to show a toast message.
     */
    fun onToast(message: String)

    // ===== Permission Events =====

    /**
     * Called when a permission is requested by a web page.
     */
    fun onPermissionRequested(
        tabId: String,
        origin: String,
        type: String,
        callback: PermissionCallback
    )

    /**
     * Called when a file chooser is requested by a web page.
     */
    fun onFileChooserRequested(
        tabId: String,
        mode: Int,
        acceptTypes: Array<String>?,
        capture: Boolean,
        callback: FileChooserCallback
    )
}

/**
 * Callback for permission request results.
 */
interface PermissionCallback {
    fun onResult(granted: Boolean)
}

/**
 * Callback for file chooser results.
 */
interface FileChooserCallback {
    fun onResult(uris: Array<android.net.Uri>?)
    fun onResultSingle(uri: android.net.Uri?)
}