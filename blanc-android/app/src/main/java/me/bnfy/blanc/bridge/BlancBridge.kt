package me.bnfy.blanc.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.bnfy.blanc.storage.ClosedTabEntity
import me.bnfy.blanc.storage.DownloadEntity
import me.bnfy.blanc.storage.Favorite
import me.bnfy.blanc.storage.HistoryEntry
import me.bnfy.blanc.storage.PermissionDecisionEntity
import me.bnfy.blanc.tab.Tab
import me.bnfy.blanc.tab.TabGroup
import me.bnfy.blanc.tab.TabManager
import me.bnfy.blanc.adblock.AdblockEngine
import me.bnfy.blanc.storage.Repository
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * Complete @JavascriptInterface for Blanc Android.
 * Exposes all commands matching PARITY_IPC_CONTRACT.md to the React UI.
 * 
 * All methods run on the WebView's JavaScript thread. Use CoroutineScope for async operations.
 * Returns JSON strings for async operations; throws are caught and returned as error JSON.
 */
class BlancBridge(
    private val context: Context,
    private val repository: Repository,
    private val tabManager: TabManager,
    private val adblockEngine: AdblockEngine,
    private val lifecycleOwner: LifecycleOwner
) : me.bnfy.blanc.tab.BlancBridge {

    private val gson = Gson()
    private val pendingCallbacks = ConcurrentHashMap<String, (String) -> Unit>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ===== Helper Methods =====

    private fun <T> asyncResult(block: suspend () -> T): String = runBlocking(Dispatchers.IO) {
        try {
            val result = block()
            gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString(), result))
        } catch (e: Exception) {
            gson.toJson(BridgeProtocol.Response(
                java.util.UUID.randomUUID().toString(),
                null,
                BridgeProtocol.ErrorInfo("ERROR", e.message ?: "Unknown error")
            ))
        }
    }

    private fun <T> asyncVoid(block: suspend () -> T) {
        scope.launch { block() }
    }

    private suspend fun getActiveProfileId(): String {
        return repository.activeProfileId.first()
    }

    // ===== State Commands =====

    /**
     * Gets the full state projection for all windows.
     * Returns JSON string of StateProjection.
     */
    @JavascriptInterface
    fun getStateProjection(): String {
        return asyncResult {
            val profileId = getActiveProfileId()
            // Build state projection from repository and tabManager
            val windows = tabManager.getAllTabs().groupBy { it.windowId ?: "default" }.map { (windowId, tabs) ->
                val activeTabId = tabs.firstOrNull { it.id == tabManager.getActiveTabId() }?.id
                val groups = tabManager.getGroupsForWindow(windowId)
                WindowProjectionBuilder.build(windowId, tabs, groups, activeTabId)
            }
            val focusedWindowId = tabManager.getActiveWindowId()
            val adblockEnabled = repository.adblockEnabled.first()
            val blockingReady = adblockEngine.isInitialized
            val totalBlocked = adblockEngine.getStats().totalBlocked
            val blockingError = if (blockingReady) null else "Blocking engine not ready"

            BridgeProtocol.StateProjection(
                windows = windows,
                focusedWindowId = focusedWindowId,
                adblockEnabled = adblockEnabled,
                blockingReady = blockingReady,
                totalBlocked = totalBlocked,
                blockingError = blockingError
            )
        }
    }

    /**
     * Gets state projection for a specific window.
     */
    @JavascriptInterface
    fun getWindowProjection(windowId: String): String {
        return asyncResult {
            val tabs = tabManager.getTabsForWindow(windowId)
            val groups = tabManager.getGroupsForWindow(windowId)
            val activeTabId = tabManager.getActiveTabId()
            WindowProjectionBuilder.build(windowId, tabs, groups, activeTabId)
        }
    }

    // ===== Tab Commands =====

    /**
     * Creates a new tab.
     * @param url Optional URL to load (default: blanc://newtab)
     * @param privateTab Whether the tab should be private
     * @param group Optional group ID to add the tab to
     * @return JSON string of TabRecord
     */
    @JavascriptInterface
    fun createTab(url: String?, privateTab: Boolean, group: String?): String {
        val finalUrl = url ?: (if (privateTab) "blanc://newtab?private=1" else "blanc://newtab")
        return asyncResult {
            val tab = if (privateTab) {
                tabManager.createPrivateTab(finalUrl)
            } else {
                tabManager.createTab(finalUrl)
            }
            tab?.let { newTab ->
                group?.let { tabManager.addTabToGroup(newTab.id, it) }
                TabRecordBuilder.fromTab(newTab)
            } ?: throw IllegalStateException("Failed to create tab")
        }
    }

    /** Overload without group parameter. */
    @JavascriptInterface
    fun createTab(url: String?, privateTab: Boolean): String = createTab(url, privateTab, null)

    /** Overload with defaults. */
    @JavascriptInterface
    fun createTab(): String = createTab(null, false, null)

    /**
     * Closes a tab by ID.
     * @param tabId The ID of the tab to close
     * @return JSON string of the newly active TabRecord, or null
     */
    @JavascriptInterface
    fun closeTab(tabId: String): String {
        return asyncResult {
            val activeTabBefore = tabManager.getActiveTab()
            val success = tabManager.closeTab(tabId)
            if (!success) throw IllegalArgumentException("Tab not found: $tabId")

            val newActiveTab = tabManager.getActiveTab()
            newActiveTab?.let { TabRecordBuilder.fromTab(it) }
        }
    }

    /**
     * Closes all tabs in a window.
     * @param windowId The window ID (Android: single window, use "default")
     */
    @JavascriptInterface
    fun closeAllTabs(windowId: String): String {
        asyncVoid { tabManager.closeAllTabs() }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Switches to a tab by ID.
     * @param tabId The ID of the tab to activate
     */
    @JavascriptInterface
    fun switchTab(tabId: String): String {
        asyncVoid { tabManager.switchTab(tabId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Navigates a tab to a URL.
     * @param tabId The tab ID
     * @param url The URL to navigate to (raw user input, native handles classification)
     * @param privateTab Whether the tab should be private (for new tabs created via navigation)
     */
    @JavascriptInterface
    fun navigate(tabId: String, url: String, privateTab: Boolean): String {
        asyncVoid {
            val tab = tabManager.getTab(tabId)
            if (tab != null) {
                tabManager.navigateTo(tabId, url)
            } else if (privateTab) {
                tabManager.createPrivateTab(url)
            } else {
                tabManager.createTab(url)
            }
        }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /** Overload without private parameter. */
    @JavascriptInterface
    fun navigate(tabId: String, url: String): String = navigate(tabId, url, false)

    /**
     * Reloads a tab.
     * @param tabId The tab ID
     */
    @JavascriptInterface
    fun reloadTab(tabId: String): String {
        asyncVoid { tabManager.reloadTab(tabId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Goes back in tab history.
     * @param tabId The tab ID
     */
    @JavascriptInterface
    fun goBack(tabId: String): String {
        asyncVoid { tabManager.goBack(tabId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Goes forward in tab history.
     * @param tabId The tab ID
     */
    @JavascriptInterface
    fun goForward(tabId: String): String {
        asyncVoid { tabManager.goForward(tabId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Duplicates a tab.
     * @param tabId The tab ID to duplicate
     * @return JSON string of the new TabRecord
     */
    @JavascriptInterface
    fun duplicateTab(tabId: String): String {
        return asyncResult {
            val tab = tabManager.getTab(tabId) ?: throw IllegalArgumentException("Tab not found: $tabId")
            val newTab = if (tab.isPrivate) {
                tabManager.createPrivateTab(tab.url)
            } else {
                tabManager.createTab(tab.url)
            }
            newTab?.let { TabRecordBuilder.fromTab(it) } ?: throw IllegalStateException("Failed to duplicate tab")
        }
    }

    /**
     * Sets tab pinned state.
     * @param tabId The tab ID
     * @param pinned Whether to pin the tab
     */
    @JavascriptInterface
    fun setTabPinned(tabId: String, pinned: Boolean): String {
        asyncVoid { tabManager.setTabPinned(tabId, pinned) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Sets tab muted state.
     * @param tabId The tab ID
     * @param muted Whether to mute the tab
     */
    @JavascriptInterface
    fun setTabMuted(tabId: String, muted: Boolean): String {
        asyncVoid { tabManager.setTabMuted(tabId, muted) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    // ===== Tab Group Commands =====

    /**
     * Creates a new tab group.
     * @param windowId The window ID
     * @param name The group name
     * @return JSON string of GroupProjection
     */
    @JavascriptInterface
    fun createGroup(windowId: String, name: String): String {
        return asyncResult {
            val group = tabManager.createGroup(windowId, name)
            GroupProjectionBuilder.fromGroup(group)
        }
    }

    /**
     * Renames a tab group.
     * @param groupId The group ID
     * @param name The new name
     */
    @JavascriptInterface
    fun renameGroup(groupId: String, name: String): String {
        asyncVoid { tabManager.renameGroup(groupId, name) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Sets group collapsed state.
     * @param groupId The group ID
     * @param collapsed Whether to collapse the group
     */
    @JavascriptInterface
    fun setGroupCollapsed(groupId: String, collapsed: Boolean): String {
        asyncVoid { tabManager.setGroupCollapsed(groupId, collapsed) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Moves a tab to a group (or ungroups if groupId is null).
     * @param tabId The tab ID
     * @param groupId The target group ID, or null to ungroup
     */
    @JavascriptInterface
    fun moveTabToGroup(tabId: String, groupId: String?): String {
        asyncVoid { tabManager.moveTabToGroup(tabId, groupId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Closes a tab group (closes all tabs in the group).
     * @param groupId The group ID
     */
    @JavascriptInterface
    fun closeGroup(groupId: String): String {
        asyncVoid { tabManager.closeGroup(groupId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Focuses a tab group (activates first tab in group).
     * @param groupId The group ID
     */
    @JavascriptInterface
    fun focusGroup(groupId: String): String {
        asyncVoid { tabManager.focusGroup(groupId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    // ===== Closed Tab Commands =====

    /**
     * Reopens a closed tab entry.
     * @param entryId The closed tab entry ID
     * @param windowId The window ID to reopen in
     * @return JSON string of TabRecord
     */
    @JavascriptInterface
    fun reopenClosedTab(entryId: String, windowId: String): String {
        return asyncResult {
            val entry = repository.closedTabDao.getById(entryId)
                ?: throw IllegalArgumentException("Closed tab entry not found: $entryId")
            val tab = tabManager.reopenClosedTab(entry, windowId)
                ?: throw IllegalStateException("Failed to reopen tab $entryId")
            TabRecordBuilder.fromTab(tab)
        }
    }

    /**
     * Forgets (permanently removes) a closed tab entry.
     * @param entryId The closed tab entry ID
     */
    @JavascriptInterface
    fun forgetClosedTab(entryId: String): String {
        asyncVoid { repository.closedTabDao.deleteById(entryId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Clears all closed tabs for a window.
     * @param windowId The window ID
     */
    @JavascriptInterface
    fun clearClosedTabs(windowId: String): String {
        asyncVoid { repository.closedTabDao.clearByWindow(windowId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    // ===== Window Commands =====

    /**
     * Creates a new window (no-op on Android single-activity).
     * @param privateWindow Whether the window should be private
     * @param profile Optional profile ID
     * @return JSON string of WindowRecord
     */
    @JavascriptInterface
    fun createWindow(privateWindow: Boolean, profile: String?): String {
        return asyncResult {
            // On Android, we simulate a new window by creating a new tab group or just a new tab
            val windowId = "window-${System.currentTimeMillis()}"
            val tab = if (privateWindow) tabManager.createPrivateTab() else tabManager.createTab()
            WindowRecordBuilder.build(windowId, "Window ${tabManager.getAllWindows().size + 1}", profile ?: getActiveProfileId(), tab?.id)
        }
    }

    /**
     * Closes a window (destroys all tabs in it).
     * @param windowId The window ID
     */
    @JavascriptInterface
    fun closeWindow(windowId: String): String {
        asyncVoid { tabManager.closeWindow(windowId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Focuses a window (no-op on Android).
     * @param windowId The window ID
     */
    @JavascriptInterface
    fun focusWindow(windowId: String): String {
        asyncVoid { tabManager.focusWindow(windowId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Lists all windows.
     * @return JSON array of WindowRecord
     */
    @JavascriptInterface
    fun listWindows(): String {
        return asyncResult {
            tabManager.getAllWindows().map { WindowRecordBuilder.build(it.id, it.label, it.profileId, it.activeTabId) }
        }
    }

    /**
     * Sets window viewport (maps to UI WebView layout on Android).
     * @param windowId The window ID
     * @param x X position
     * @param y Y position
     * @param width Width
     * @param height Height
     * @param hidden Whether hidden
     */
    @JavascriptInterface
    fun setViewport(windowId: String, x: Double, y: Double, width: Double, height: Double, hidden: Boolean): String {
        asyncVoid { tabManager.setViewport(windowId, x, y, width, height, hidden) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /** Window control commands (minimize, maximize, etc.) - no-op on Android. */
    @JavascriptInterface
    fun minimizeWindow(windowId: String): String = gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))

    @JavascriptInterface
    fun maximizeWindow(windowId: String): String = gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))

    @JavascriptInterface
    fun toggleMaximizeWindow(windowId: String): String = gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))

    @JavascriptInterface
    fun closeWindowUi(windowId: String): String = closeWindow(windowId)

    // ===== History Commands =====

    /**
     * Lists history entries with pagination.
     * @param limit Max entries to return
     * @param offset Pagination offset
     * @param query Optional search query
     * @return JSON string of HistoryPage
     */
    @JavascriptInterface
    fun historyList(limit: Int, offset: Int, query: String?): String {
        return asyncResult {
            val profileId = getActiveProfileId()
            val entries = repository.historyDao.getHistoryPage(profileId, limit, offset, query)
            val total = repository.historyDao.getTotalCount(profileId, query)
            BridgeProtocol.HistoryPage(
                entries.map { HistoryEntryBuilder.fromEntry(it) },
                total
            )
        }
    }

    /**
     * Records a history visit (called by native, not React).
     * @param url The URL
     * @param title The page title
     * @return JSON string of HistoryEntry
     */
    @JavascriptInterface
    fun historyRecord(url: String, title: String): String {
        return asyncResult {
            val profileId = getActiveProfileId()
            repository.recordHistoryVisit(url, title, null, profileId)
            val entry = repository.historyDao.getByUrl(url, profileId)
            entry?.let { HistoryEntryBuilder.fromEntry(it) }
        }
    }

    /**
     * Removes a history entry by ID.
     * @param id The history entry ID
     */
    @JavascriptInterface
    fun historyRemove(id: String): String {
        asyncVoid { repository.historyDao.deleteById(id.toLong()) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Clears all history for the active profile.
     */
    @JavascriptInterface
    fun historyClear(): String {
        asyncVoid { repository.historyDao.clearHistory(getActiveProfileId()) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    // ===== Favorites Commands =====

    /**
     * Lists all favorites for the active profile.
     * @return JSON array of Favorite
     */
    @JavascriptInterface
    fun favoritesList(): String {
        return asyncResult {
            val profileId = getActiveProfileId()
            repository.favoriteDao.getAll(profileId).map { FavoriteBuilder.fromFavorite(it) }
        }
    }

    /**
     * Adds a favorite.
     * @param favoriteJson JSON string of Favorite
     * @return JSON string of the created Favorite
     */
    @JavascriptInterface
    fun favoritesAdd(favoriteJson: String): String {
        return asyncResult {
            val type = object : TypeToken<BridgeProtocol.Favorite>() {}.type
            val favorite = gson.fromJson<BridgeProtocol.Favorite>(favoriteJson, type)
                ?: throw IllegalArgumentException("Invalid favorite JSON")

            val entity = Favorite(
                id = favorite.id,
                url = favorite.url,
                title = favorite.title,
                favicon = favorite.favicon,
                folderId = favorite.folderId,
                position = favorite.position,
                createdAt = favorite.createdAt,
                updatedAt = favorite.updatedAt,
                profileId = getActiveProfileId(),
                isPinned = favorite.isPinned
            )
            repository.favoriteDao.insert(entity)
            favorite
        }
    }

    /**
     * Updates a favorite.
     * @param id The favorite ID
     * @param favoriteJson JSON string of Favorite
     * @return JSON string of the updated Favorite
     */
    @JavascriptInterface
    fun favoritesUpdate(id: String, favoriteJson: String): String {
        return asyncResult {
            val type = object : TypeToken<BridgeProtocol.Favorite>() {}.type
            val favorite = gson.fromJson<BridgeProtocol.Favorite>(favoriteJson, type)
                ?: throw IllegalArgumentException("Invalid favorite JSON")

            val entity = Favorite(
                id = id,
                url = favorite.url,
                title = favorite.title,
                favicon = favorite.favicon,
                folderId = favorite.folderId,
                position = favorite.position,
                createdAt = favorite.createdAt,
                updatedAt = System.currentTimeMillis(),
                profileId = getActiveProfileId(),
                isPinned = favorite.isPinned
            )
            repository.favoriteDao.update(entity)
            favorite.copy(id = id, updatedAt = entity.updatedAt)
        }
    }

    /**
     * Removes a favorite.
     * @param id The favorite ID
     */
    @JavascriptInterface
    fun favoritesRemove(id: String): String {
        asyncVoid { repository.favoriteDao.deleteById(id) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    // ===== Settings Commands =====

    /**
     * Gets all user settings.
     * @return JSON string of UserSettings
     */
    @JavascriptInterface
    fun getSettings(): String {
        return asyncResult { repository.getSettings() }
    }

    /**
     * Saves user settings.
     * @param settingsJson JSON string of UserSettings
     * @return JSON string of the saved UserSettings
     */
    @JavascriptInterface
    fun saveSettings(settingsJson: String): String {
        return asyncResult {
            val type = object : TypeToken<Repository.UserSettings>() {}.type
            val settings = gson.fromJson<Repository.UserSettings>(settingsJson, type)
                ?: throw IllegalArgumentException("Invalid settings JSON")
            repository.saveSettings(settings)
            settings
        }
    }

    /**
     * Gets sync eligibility status.
     * @return JSON string of SyncEligibility
     */
    @JavascriptInterface
    fun getSyncEligibility(): String {
        return asyncResult {
            val patronActive = repository.patronActive.first()
            BridgeProtocol.SyncEligibility(
                canSync = patronActive,
                reason = if (patronActive) null else "Patron subscription required for sync"
            )
        }
    }

    // ===== Ad Blocking Commands =====

    /**
     * Gets adblock status.
     * @return JSON string of BlockingStatus
     */
    @JavascriptInterface
    fun adblockStatus(): String {
        return asyncResult {
            val stats = adblockEngine.getStats()
            BridgeProtocol.BlockingStatus(
                enabled = stats.isEnabled,
                ready = adblockEngine.isInitialized,
                totalBlocked = stats.totalBlocked,
                error = if (adblockEngine.isInitialized) null else "Engine not initialized"
            )
        }
    }

    /**
     * Toggles adblock enabled state.
     * @param enabled Whether to enable adblock
     * @return JSON string of BlockingStatus
     */
    @JavascriptInterface
    fun toggleAdblock(enabled: Boolean): String {
        return asyncResult {
            repository.setAdblockEnabled(enabled)
            adblockEngine.setEnabled(enabled)
            val stats = adblockEngine.getStats()
            BridgeProtocol.BlockingStatus(
                enabled = stats.isEnabled,
                ready = adblockEngine.isInitialized,
                totalBlocked = stats.totalBlocked,
                error = null
            )
        }
    }

    /**
     * Adds an adblock exception (allowlist hostname).
     * @param hostname The hostname to except
     */
    @JavascriptInterface
    fun addAdblockException(hostname: String): String {
        asyncVoid { repository.addAdblockException(hostname) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Removes an adblock exception.
     * @param hostname The hostname to remove
     */
    @JavascriptInterface
    fun removeAdblockException(hostname: String): String {
        asyncVoid { repository.removeAdblockException(hostname) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Checks if the active tab's URL is excepted from adblock.
     * @param url The URL to check
     * @return JSON boolean
     */
    @JavascriptInterface
    fun adblockExceptActive(url: String): String {
        return asyncResult {
            val host = try { java.net.URL(url).host.lowercase() } catch (e: Exception) { "" }
            adblockEngine.isException(host)
        }
    }

    // ===== Permission Commands =====

    /**
     * Lists all permission decisions.
     * @return JSON array of PermissionDecisionRecord
     */
    @JavascriptInterface
    fun permissionListDecisions(): String {
        return asyncResult {
            val profileId = getActiveProfileId()
            repository.profileDao.getPermissionsByProfile(profileId).map { PermissionBuilder.fromDecision(it) }
        }
    }

    /**
     * Sets a permission decision for an origin.
     * @param origin The origin (e.g., "https://example.com")
     * @param resource The resource (e.g., "camera", "microphone")
     * @param decision The decision (0=Default, 1=Allow, 2=Deny, 3=Ask)
     */
    @JavascriptInterface
    fun permissionSetDecision(origin: String, resource: String, decision: Int): String {
        asyncVoid {
            val profileId = getActiveProfileId()
            val entity = PermissionDecisionEntity(
                origin = origin,
                resource = resource,
                decision = decision,
                isRemembered = decision != 0,
                profileId = profileId
            )
            repository.profileDao.insertPermission(entity)
        }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Responds to a permission request.
     * @param id The request ID
     * @param allow Whether to allow
     * @param remember Whether to remember the decision
     */
    @JavascriptInterface
    fun permissionRespond(id: String, allow: Boolean, remember: Boolean): String {
        asyncVoid {
            // This would be handled by the TabWebChromeClient's permission callback
            // The bridge just acknowledges; actual grant/deny happens in the callback
            tabManager.respondToPermissionRequest(id, allow, remember)
        }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    // ===== Download Commands =====

    /**
     * Lists all downloads.
     * @return JSON array of DownloadRecord
     */
    @JavascriptInterface
    fun downloadsList(): String {
        return asyncResult {
            val profileId = getActiveProfileId()
            repository.downloadDao.getByProfile(profileId).map { DownloadBuilder.fromDownload(it) }
        }
    }

    /**
     * Opens a downloaded file.
     * @param id The download ID
     */
    @JavascriptInterface
    fun downloadsOpen(id: String): String {
        asyncVoid { tabManager.openDownload(id) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Shows a downloaded file in the system file manager.
     * @param id The download ID
     */
    @JavascriptInterface
    fun downloadsShowInFolder(id: String): String {
        asyncVoid { tabManager.showDownloadInFolder(id) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Cancels a download.
     * @param id The download ID
     */
    @JavascriptInterface
    fun downloadsCancel(id: String): String {
        asyncVoid { tabManager.cancelDownload(id) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Clears completed downloads.
     */
    @JavascriptInterface
    fun downloadsClearCompleted(): String {
        asyncVoid { repository.downloadDao.clearCompletedByProfile(getActiveProfileId()) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    // ===== Surface Commands =====

    /**
     * Opens an internal surface (utility page).
     * @param surface The surface name (favorites, history, downloads, settings, shortcuts, newtab)
     * @param privateTab Whether to open in private mode
     */
    @JavascriptInterface
    fun openSurface(surface: String, privateTab: Boolean): String {
        asyncVoid {
            val url = when (surface) {
                "favorites" -> "blanc://bookmarks"
                "history" -> "blanc://history"
                "downloads" -> "blanc://downloads"
                "settings" -> "blanc://settings"
                "shortcuts" -> "blanc://shortcuts"
                "newtab" -> "blanc://newtab"
                else -> throw IllegalArgumentException("Unknown surface: $surface")
            }
            if (privateTab) {
                tabManager.createPrivateTab(url)
            } else {
                tabManager.createTab(url)
            }
        }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    /**
     * Closes the current utility surface.
     * @param windowId The window ID
     */
    @JavascriptInterface
    fun surfaceClose(windowId: String): String {
        asyncVoid { tabManager.closeSurface(windowId) }
        return gson.toJson(BridgeProtocol.Response(java.util.UUID.randomUUID().toString()))
    }

    // ===== Callback Registration (for async responses) =====

    /**
     * Registers a callback for a command response.
     * React calls this with a callback ID, native invokes it when async operation completes.
     * @param callbackId The callback ID
     * @param resultJson The result JSON
     */
    @JavascriptInterface
    fun onCallback(callbackId: String, resultJson: String) {
        pendingCallbacks.remove(callbackId)?.invoke(resultJson)
    }

    // ===== Event Emission (Native -> React) =====

    /**
     * Emits an event to the React UI via evaluateJavascript.
     * Called by native code when state changes.
     */
    fun emitEvent(event: String, data: Any?) {
        val json = BridgeProtocol.createEvent(event, data)
        val js = "window.__BLANC__.onEvent('$event', $json)"
        tabManager.evaluateJavascriptOnUi(js)
    }

    // ===== Additional Helper Methods for TabManager Integration =====

    /**
     * Gets the TabManager for advanced operations.
     */
    fun getTabManager(): TabManager = tabManager

    /**
     * Gets the Repository for direct data access.
     */
    fun getRepository(): Repository = repository

    /**
     * Gets the AdblockEngine.
     */
    fun getAdblockEngine(): AdblockEngine = adblockEngine
}

/**
 * Builders for converting internal objects to bridge protocol objects.
 */
object TabRecordBuilder {
    fun fromTab(tab: Tab): BridgeProtocol.TabRecord {
        return BridgeProtocol.TabRecord(
            id = tab.id,
            windowId = tab.windowId ?: "default",
            url = tab.url,
            title = tab.title,
            favicon = tab.favicon,
            isPrivate = tab.isPrivate,
            isPinned = tab.isPinned,
            isMuted = tab.isMuted,
            groupId = tab.groupId,
            position = tab.position,
            canGoBack = tab.canGoBack,
            canGoForward = tab.canGoForward,
            isLoading = tab.isLoading,
            progress = tab.progress,
            blockedCount = tab.blockedCount,
            isActive = tab.isActive
        )
    }
}

object WindowProjectionBuilder {
    fun build(
        windowId: String,
        tabs: List<Tab>,
        groups: List<TabGroup>,
        activeTabId: String?
    ): BridgeProtocol.WindowProjection {
        return BridgeProtocol.WindowProjection(
            id = windowId,
            label = "Window",
            profileId = tabs.firstOrNull()?.profileId ?: "personal",
            activeTabId = activeTabId,
            tabIds = tabs.map { it.id },
            groups = groups.map { GroupProjectionBuilder.fromGroup(it) },
            overlay = null, // Overlay state managed by React
            closedTabs = emptyList(), // Would come from ClosedTabDao
            workspaceId = tabs.firstOrNull()?.workspaceId,
            permissionPromptOpen = false
        )
    }
}

object GroupProjectionBuilder {
    fun fromGroup(group: TabGroup): BridgeProtocol.GroupProjection {
        return BridgeProtocol.GroupProjection(
            id = group.id,
            name = group.name,
            collapsed = group.isCollapsed,
            tabIds = group.tabIds
        )
    }
}

object HistoryEntryBuilder {
    fun fromEntry(entry: HistoryEntry): BridgeProtocol.HistoryEntry {
        return BridgeProtocol.HistoryEntry(
            id = entry.id,
            url = entry.url,
            title = entry.title,
            visitTime = entry.visitTime,
            favicon = entry.favicon,
            isPrivate = entry.isPrivate
        )
    }
}

object FavoriteBuilder {
    fun fromFavorite(fav: Favorite): BridgeProtocol.Favorite {
        return BridgeProtocol.Favorite(
            id = fav.id,
            url = fav.url,
            title = fav.title,
            favicon = fav.favicon,
            folderId = fav.folderId,
            position = fav.position,
            createdAt = fav.createdAt,
            updatedAt = fav.updatedAt,
            isPinned = fav.isPinned
        )
    }
}

object PermissionBuilder {
    fun fromDecision(decision: PermissionDecisionEntity): BridgeProtocol.PermissionDecisionRecord {
        return BridgeProtocol.PermissionDecisionRecord(
            origin = decision.origin,
            resource = decision.resource,
            decision = decision.decision,
            remembered = decision.isRemembered
        )
    }
}

object DownloadBuilder {
    fun fromDownload(download: DownloadEntity): BridgeProtocol.DownloadRecord {
        return BridgeProtocol.DownloadRecord(
            id = download.id,
            windowId = download.windowId,
            tabId = download.tabId,
            url = download.url,
            fileName = download.fileName,
            mimeType = download.mimeType,
            totalBytes = download.totalBytes,
            receivedBytes = download.receivedBytes,
            targetPath = download.targetPath,
            state = download.state,
            error = download.error,
            startedAt = download.startedAt,
            completedAt = download.completedAt
        )
    }
}

object WindowRecordBuilder {
    fun build(id: String, label: String, profileId: String, activeTabId: String?): BridgeProtocol.WindowRecord {
        return BridgeProtocol.WindowRecord(
            id = id,
            label = label,
            profileId = profileId,
            activeTabId = activeTabId,
            tabCount = 0, // Would need to query
            workspaceId = null
        )
    }
}