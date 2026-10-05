package me.bnfy.blanc.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey
import java.util.Date

/**
 * History entry entity for Room database.
 * Stores visited URLs with timestamps for history tracking.
 */
@Entity(
    tableName = "history_entries",
    indices = [
        Index(value = ["url"]),
        Index(value = ["visitTime"]),
        Index(value = ["isPrivate"]),
    ]
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String?,
    val visitTime: Long = System.currentTimeMillis(),
    val favicon: String? = null,
    val isPrivate: Boolean = false,
    val profileId: String = "personal"
)

/**
 * Bookmark/Favorite entity for Room database.
 * Stores user bookmarks with metadata.
 */
@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["url"]),
        Index(value = ["profileId"]),
        Index(value = ["createdAt"]),
    ]
)
data class Bookmark(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val favicon: String? = null,
    val folderId: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val profileId: String = "personal"
)

/**
 * Favorite entity - alias for Bookmark with additional UI state.
 * In Blanc, "Favorites" is the user-facing term for Bookmarks.
 */
@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["url"]),
        Index(value = ["profileId"]),
        Index(value = ["createdAt"]),
    ]
)
data class Favorite(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val favicon: String? = null,
    val folderId: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val profileId: String = "personal",
    val isPinned: Boolean = false
)

/**
 * Tab entity for session persistence.
 * Stores tab state for restoration across app restarts.
 */
@Entity(
    tableName = "tabs",
    indices = [
        Index(value = ["windowId"]),
        Index(value = ["profileId"]),
        Index(value = ["isPrivate"]),
        Index(value = ["groupId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = WindowEntity::class,
            parentColumns = ["id"],
            childColumns = ["windowId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TabEntity(
    @PrimaryKey val id: String,
    val windowId: String,
    val url: String,
    val title: String?,
    val favicon: String? = null,
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val groupId: String? = null,
    val position: Int = 0,
    val navigationHistory: String = "[]", // JSON array of URLs
    val historyIndex: Int = -1,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val lastActiveTime: Long = System.currentTimeMillis(),
    val profileId: String = "personal",
    val isActive: Boolean = false,
    val blockedCount: Int = 0
)

/**
 * Window entity for multi-window support.
 */
@Entity(
    tableName = "windows",
    indices = [Index(value = ["profileId"]), Index(value = ["workspaceId"])]
)
data class WindowEntity(
    @PrimaryKey val id: String,
    val label: String = "default",
    val profileId: String = "personal",
    val workspaceId: String? = null,
    val activeTabId: String? = null,
    val boundsX: Float = 0f,
    val boundsY: Float = 0f,
    val boundsWidth: Float = 0f,
    val boundsHeight: Float = 0f,
    val isMaximized: Boolean = false,
    val isHidden: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Tab group entity for organizing tabs.
 */
@Entity(
    tableName = "tab_groups",
    indices = [Index(value = ["windowId"]), Index(value = ["profileId"])],
    foreignKeys = [
        ForeignKey(
            entity = WindowEntity::class,
            parentColumns = ["id"],
            childColumns = ["windowId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TabGroupEntity(
    @PrimaryKey val id: String,
    val windowId: String,
    val name: String,
    val isCollapsed: Boolean = false,
    val position: Int = 0,
    val profileId: String = "personal",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Closed tab entry for "Reopen Closed Tab" feature.
 * Stores closed tab state for recovery.
 */
@Entity(
    tableName = "closed_tabs",
    indices = [
        Index(value = ["windowId"]),
        Index(value = ["closedAt"]),
        Index(value = ["profileId"]),
    ]
)
data class ClosedTabEntity(
    @PrimaryKey val id: String,
    val windowId: String,
    val tabId: String,
    val url: String,
    val title: String?,
    val favicon: String? = null,
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val groupId: String? = null,
    val position: Int = 0,
    val navigationHistory: String = "[]",
    val historyIndex: Int = -1,
    val closedAt: Long = System.currentTimeMillis(),
    val profileId: String = "personal",
    val tier: Int = 2, // 1 = held view, 2 = snapshot, 3 = URL only
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupTabIds: String = "[]" // JSON array of tab IDs for group entries
)

/**
 * Download entity for download management.
 */
@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["windowId"]),
        Index(value = ["tabId"]),
        Index(value = ["state"]),
        Index(value = ["startedAt"]),
        Index(value = ["profileId"]),
    ]
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val windowId: String,
    val tabId: String?,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long = -1,
    val receivedBytes: Long = 0,
    val targetPath: String? = null,
    val state: Int = 0, // 0=Pending, 1=InProgress, 2=Completed, 3=Cancelled, 4=Failed
    val error: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
    val profileId: String = "personal",
    val isPrivate: Boolean = false
)

/**
 * Profile entity for user profiles.
 */
@Entity(
    tableName = "profiles",
    indices = [Index(value = ["name"], unique = true)]
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatar: String? = null,
    val isPersonal: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Workspace entity for named workspaces (Patron feature).
 */
@Entity(
    tableName = "workspaces",
    indices = [Index(value = ["profileId"])]
)
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val windowIds: String = "[]", // JSON array of window IDs
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Permission decision entity for site permissions.
 */
@Entity(
    tableName = "permission_decisions",
    indices = [
        Index(value = ["origin"]),
        Index(value = ["resource"]),
        Index(value = ["profileId"]),
    ]
)
data class PermissionDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val origin: String,
    val resource: String, // "camera", "microphone", "geolocation", "notifications", etc.
    val decision: Int = 0, // 0=Default, 1=Allow, 2=Deny, 3=Ask
    val isRemembered: Boolean = false,
    val profileId: String = "personal",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Adblock exception entity for user allowlist.
 */
@Entity(
    tableName = "adblock_exceptions",
    indices = [Index(value = ["profileId"])]
)
data class AdblockExceptionEntity(
    @PrimaryKey val hostname: String,
    val profileId: String = "personal",
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Settings entity for Room-backed settings.
 * Used for settings that benefit from relational queries.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String, // JSON serialized value
    val profileId: String = "personal",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Search engine entity.
 */
@Entity(tableName = "search_engines")
data class SearchEngineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val searchUrl: String,
    val suggestUrl: String? = null,
    val isDefault: Boolean = false,
    val profileId: String = "personal"
)