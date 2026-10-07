package me.bnfy.blanc.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAO for tab entities (session persistence).
 * Manages tabs, windows, and tab groups for session restore.
 */
@Dao
interface TabDao {

    // ===== Tab Operations =====

    /** Inserts a tab. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tab: TabEntity): Long

    /** Inserts multiple tabs. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tabs: List<TabEntity>): List<Long>

    /** Updates a tab. */
    @Update
    suspend fun update(tab: TabEntity): Int

    /** Deletes a tab. */
    @Delete
    suspend fun delete(tab: TabEntity): Int

    /** Deletes a tab by ID. */
    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** Gets a tab by ID. */
    @Query("SELECT * FROM tabs WHERE id = :id")
    suspend fun getById(id: String): TabEntity?

    /** Gets a tab by ID as Flow. */
    @Query("SELECT * FROM tabs WHERE id = :id")
    fun getByIdFlow(id: String): Flow<TabEntity?>

    /** Gets all tabs for a window in order. */
    @Query("SELECT * FROM tabs WHERE windowId = :windowId ORDER BY position ASC")
    suspend fun getByWindow(windowId: String): List<TabEntity>

    /** Gets all tabs for a window as Flow. */
    @Query("SELECT * FROM tabs WHERE windowId = :windowId ORDER BY position ASC")
    fun getByWindowFlow(windowId: String): Flow<List<TabEntity>>

    /** Gets the active tab for a window. */
    @Query("SELECT * FROM tabs WHERE windowId = :windowId AND isActive = 1 LIMIT 1")
    suspend fun getActiveTab(windowId: String): TabEntity?

    /** Gets the active tab as Flow. */
    @Query("SELECT * FROM tabs WHERE windowId = :windowId AND isActive = 1 LIMIT 1")
    fun getActiveTabFlow(windowId: String): Flow<TabEntity?>

    /** Gets all tabs for a profile. */
    @Query("SELECT * FROM tabs WHERE profileId = :profileId ORDER BY windowId, position ASC")
    suspend fun getByProfile(profileId: String): List<TabEntity>

    /** Gets all regular (non-private) tabs for a profile. */
    @Query("SELECT * FROM tabs WHERE profileId = :profileId AND isPrivate = 0 ORDER BY windowId, position ASC")
    suspend fun getRegularTabs(profileId: String): List<TabEntity>

    /** Gets all private tabs for a profile. */
    @Query("SELECT * FROM tabs WHERE profileId = :profileId AND isPrivate = 1 ORDER BY windowId, position ASC")
    suspend fun getPrivateTabs(profileId: String): List<TabEntity>

    /** Updates tab URL and title. */
    @Query("UPDATE tabs SET url = :url, title = :title, favicon = :favicon, lastActiveTime = :lastActiveTime WHERE id = :id")
    suspend fun updateUrlAndTitle(id: String, url: String, title: String?, favicon: String?, lastActiveTime: Long): Int

    /** Updates tab navigation history. */
    @Query("UPDATE tabs SET navigationHistory = :history, historyIndex = :historyIndex, canGoBack = :canGoBack, canGoForward = :canGoForward WHERE id = :id")
    suspend fun updateNavigationState(
        id: String,
        history: String,
        historyIndex: Int,
        canGoBack: Boolean,
        canGoForward: Boolean
    ): Int

    /** Updates tab pinned state. */
    @Query("UPDATE tabs SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean): Int

    /** Updates tab muted state. */
    @Query("UPDATE tabs SET isMuted = :muted WHERE id = :id")
    suspend fun setMuted(id: String, muted: Boolean): Int

    /** Updates tab group assignment. */
    @Query("UPDATE tabs SET groupId = :groupId, position = :position WHERE id = :id")
    suspend fun setGroup(id: String, groupId: String?, position: Int): Int

    /** Updates tab position. */
    @Query("UPDATE tabs SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int): Int

    /** Updates blocked count. */
    @Query("UPDATE tabs SET blockedCount = :count WHERE id = :id")
    suspend fun updateBlockedCount(id: String, count: Int): Int

    /** Sets active tab for a window. */
    @Transaction
    suspend fun setActiveTab(windowId: String, tabId: String?) {
        // Clear previous active
        clearActiveTab(windowId)
        // Set new active
        tabId?.let { updateActiveState(it, true) }
    }

    /** Clears active tab for a window. */
    @Query("UPDATE tabs SET isActive = 0 WHERE windowId = :windowId AND isActive = 1")
    suspend fun clearActiveTab(windowId: String): Int

    /** Updates tab active state. */
    @Query("UPDATE tabs SET isActive = :active WHERE id = :id")
    suspend fun updateActiveState(id: String, active: Boolean): Int

    /** Updates last active time. */
    @Query("UPDATE tabs SET lastActiveTime = :time WHERE id = :id")
    suspend fun updateLastActiveTime(id: String, time: Long): Int

    /** Deletes all tabs for a window. */
    @Query("DELETE FROM tabs WHERE windowId = :windowId")
    suspend fun deleteByWindow(windowId: String): Int

    /** Deletes all tabs for a profile. */
    @Query("DELETE FROM tabs WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String): Int

    /** Deletes all private tabs for a profile. */
    @Query("DELETE FROM tabs WHERE profileId = :profileId AND isPrivate = 1")
    suspend fun deletePrivateTabs(profileId: String): Int

    /** Gets tabs in a group. */
    @Query("SELECT * FROM tabs WHERE groupId = :groupId ORDER BY position ASC")
    suspend fun getByGroup(groupId: String): List<TabEntity>

    /** Gets pinned tabs for a window. */
    @Query("SELECT * FROM tabs WHERE windowId = :windowId AND isPinned = 1 ORDER BY position ASC")
    suspend fun getPinnedTabs(windowId: String): List<TabEntity>

    // ===== Window Operations =====

    /** Inserts a window. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWindow(window: WindowEntity): Long

    /** Updates a window. */
    @Update
    suspend fun updateWindow(window: WindowEntity): Int

    /** Deletes a window. */
    @Delete
    suspend fun deleteWindow(window: WindowEntity): Int

    /** Deletes a window by ID. */
    @Query("DELETE FROM windows WHERE id = :id")
    suspend fun deleteWindowById(id: String): Int

    /** Gets a window by ID. */
    @Query("SELECT * FROM windows WHERE id = :id")
    suspend fun getWindowById(id: String): WindowEntity?

    /** Gets a window by ID as Flow. */
    @Query("SELECT * FROM windows WHERE id = :id")
    fun getWindowByIdFlow(id: String): Flow<WindowEntity?>

    /** Gets all windows for a profile. */
    @Query("SELECT * FROM windows WHERE profileId = :profileId ORDER BY createdAt ASC")
    suspend fun getWindowsByProfile(profileId: String): List<WindowEntity>

    /** Gets all windows as Flow. */
    @Query("SELECT * FROM windows WHERE profileId = :profileId ORDER BY createdAt ASC")
    fun getWindowsByProfileFlow(profileId: String): Flow<List<WindowEntity>>

    /** Gets the focused window (most recently active). */
    @Query("SELECT * FROM windows WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getFocusedWindow(profileId: String): WindowEntity?

    /** Updates window active tab. */
    @Query("UPDATE windows SET activeTabId = :activeTabId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateWindowActiveTab(id: String, activeTabId: String?, updatedAt: Long): Int

    /** Updates window bounds. */
    @Query("UPDATE windows SET boundsX = :x, boundsY = :y, boundsWidth = :width, boundsHeight = :height, " +
           "isMaximized = :maximized, isHidden = :hidden, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateWindowBounds(
        id: String,
        x: Float, y: Float, width: Float, height: Float,
        maximized: Boolean, hidden: Boolean, updatedAt: Long
    ): Int

    /** Updates window workspace. */
    @Query("UPDATE windows SET workspaceId = :workspaceId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateWindowWorkspace(id: String, workspaceId: String?, updatedAt: Long): Int

    // ===== Tab Group Operations =====

    /** Inserts a tab group. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: TabGroupEntity): Long

    /** Updates a tab group. */
    @Update
    suspend fun updateGroup(group: TabGroupEntity): Int

    /** Deletes a tab group. */
    @Delete
    suspend fun deleteGroup(group: TabGroupEntity): Int

    /** Deletes a tab group by ID. */
    @Query("DELETE FROM tab_groups WHERE id = :id")
    suspend fun deleteGroupById(id: String): Int

    /** Gets a tab group by ID. */
    @Query("SELECT * FROM tab_groups WHERE id = :id")
    suspend fun getGroupById(id: String): TabGroupEntity?

    /** Gets all groups for a window. */
    @Query("SELECT * FROM tab_groups WHERE windowId = :windowId ORDER BY position ASC")
    suspend fun getGroupsByWindow(windowId: String): List<TabGroupEntity>

    /** Gets all groups for a window as Flow. */
    @Query("SELECT * FROM tab_groups WHERE windowId = :windowId ORDER BY position ASC")
    fun getGroupsByWindowFlow(windowId: String): Flow<List<TabGroupEntity>>

    /** Gets all groups for a profile. */
    @Query("SELECT * FROM tab_groups WHERE profileId = :profileId ORDER BY windowId, position ASC")
    suspend fun getGroupsByProfile(profileId: String): List<TabGroupEntity>

    /** Updates group name. */
    @Query("UPDATE tab_groups SET name = :name WHERE id = :id")
    suspend fun updateGroupName(id: String, name: String): Int

    /** Updates group collapsed state. */
    @Query("UPDATE tab_groups SET isCollapsed = :collapsed WHERE id = :id")
    suspend fun setGroupCollapsed(id: String, collapsed: Boolean): Int

    /** Updates group position. */
    @Query("UPDATE tab_groups SET position = :position WHERE id = :id")
    suspend fun updateGroupPosition(id: String, position: Int): Int

    /** Deletes all groups for a window. */
    @Query("DELETE FROM tab_groups WHERE windowId = :windowId")
    suspend fun deleteGroupsByWindow(windowId: String): Int

    /** Deletes all groups for a profile. */
    @Query("DELETE FROM tab_groups WHERE profileId = :profileId")
    suspend fun deleteGroupsByProfile(profileId: String): Int

    // ===== Session Operations =====

    /** Gets full session state for a profile (windows + tabs + groups). */
    @Transaction
    suspend fun getSessionState(profileId: String): SessionState {
        val windows = getWindowsByProfile(profileId)
        val result = mutableListOf<WindowWithTabs>()
        for (window in windows) {
            val tabs = getByWindow(window.id)
            val groups = getGroupsByWindow(window.id)
            result.add(WindowWithTabs(window, tabs, groups))
        }
        return SessionState(result)
    }

    /** Saves all session data for a profile. */
    @Transaction
    suspend fun saveSession(state: SessionState) {
        state.windows.forEach { windowWithTabs ->
            insertWindow(windowWithTabs.window)
            insertAll(windowWithTabs.tabs)
            windowWithTabs.groups.forEach { insertGroup(it) }
        }
    }

    /** Clears all session data for a profile. */
    @Transaction
    suspend fun clearSession(profileId: String) {
        deleteByProfile(profileId)
        deleteGroupsByProfile(profileId)
        deleteWindowsByProfile(profileId)
    }

    /** Deletes all windows for a profile. */
    @Query("DELETE FROM windows WHERE profileId = :profileId")
    suspend fun deleteWindowsByProfile(profileId: String): Int
}

/**
 * Session state container for full session restore.
 */
data class SessionState(
    val windows: List<WindowWithTabs>
)

/**
 * Window with its tabs and groups.
 */
data class WindowWithTabs(
    val window: WindowEntity,
    val tabs: List<TabEntity>,
    val groups: List<TabGroupEntity>
)