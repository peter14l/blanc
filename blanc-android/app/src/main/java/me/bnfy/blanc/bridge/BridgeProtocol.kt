package me.bnfy.blanc.bridge

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

/**
 * Bridge protocol for communication between React UI and Kotlin native.
 * Defines message types, commands, events, and serialization.
 */
object BridgeProtocol {

    private val gson = Gson()

    // ===== Base Message Types =====

    /**
     * Base message envelope for all bridge communication.
     */
    @Suppress("UNUSED_PARAMETER")
    data class Message(
        @SerializedName("type") val type: String, // "command" | "event" | "response" | "error"
        @SerializedName("payload") val payload: Any? = null,
        @SerializedName("id") val id: String? = null, // For request-response correlation
        @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Command message (React -> Native).
     */
    data class Command(
        @SerializedName("command") val command: String,
        @SerializedName("args") val args: Map<String, Any>? = null,
        @SerializedName("id") val id: String? = null
    )

    /**
     * Event message (Native -> React).
     */
    data class Event(
        @SerializedName("event") val event: String,
        @SerializedName("data") val data: Any? = null
    )

    /**
     * Response message (Native -> React for commands).
     */
    data class Response(
        @SerializedName("id") val id: String,
        @SerializedName("result") val result: Any? = null,
        @SerializedName("error") val error: ErrorInfo? = null
    )

    /**
     * Error info for failed commands.
     */
    data class ErrorInfo(
        @SerializedName("code") val code: String,
        @SerializedName("message") val message: String,
        @SerializedName("details") val details: Any? = null
    )

    // ===== Command Names =====

    object Commands {
        // State
        const val GET_STATE_PROJECTION = "get_state_projection"
        const val GET_WINDOW_PROJECTION = "get_window_projection"

        // Tabs
        const val CREATE_TAB = "create_tab"
        const val CLOSE_TAB = "close_tab"
        const val CLOSE_ALL_TABS = "close_all_tabs"
        const val SWITCH_TAB = "switch_tab"
        const val NAVIGATE = "navigate"
        const val RELOAD_TAB = "reload_tab"
        const val GO_BACK = "go_back"
        const val GO_FORWARD = "go_forward"
        const val DUPLICATE_TAB = "duplicate_tab"
        const val SET_TAB_PINNED = "set_tab_pinned"
        const val SET_TAB_MUTED = "set_tab_muted"

        // Tab Groups
        const val CREATE_GROUP = "create_group"
        const val RENAME_GROUP = "rename_group"
        const val SET_GROUP_COLLAPSED = "set_group_collapsed"
        const val MOVE_TAB_TO_GROUP = "move_tab_to_group"
        const val CLOSE_GROUP = "close_group"
        const val FOCUS_GROUP = "focus_group"

        // Closed Tabs
        const val REOPEN_CLOSED_TAB = "reopen_closed_tab"
        const val FORGET_CLOSED_TAB = "forget_closed_tab"
        const val CLEAR_CLOSED_TABS = "clear_closed_tabs"

        // Windows
        const val CREATE_WINDOW = "create_window"
        const val CLOSE_WINDOW = "close_window"
        const val FOCUS_WINDOW = "focus_window"
        const val LIST_WINDOWS = "list_windows"
        const val SET_VIEWPORT = "set_viewport"
        const val MINIMIZE_WINDOW = "minimize_window"
        const val MAXIMIZE_WINDOW = "maximize_window"
        const val TOGGLE_MAXIMIZE_WINDOW = "toggle_maximize_window"
        const val CLOSE_WINDOW_UI = "close_window_ui"

        // History
        const val HISTORY_LIST = "history_list"
        const val HISTORY_RECORD = "history_record"
        const val HISTORY_REMOVE = "history_remove"
        const val HISTORY_CLEAR = "history_clear"

        // Favorites
        const val FAVORITES_LIST = "favorites_list"
        const val FAVORITES_ADD = "favorites_add"
        const val FAVORITES_UPDATE = "favorites_update"
        const val FAVORITES_REMOVE = "favorites_remove"

        // Settings
        const val GET_SETTINGS = "get_settings"
        const val SAVE_SETTINGS = "save_settings"
        const val GET_SYNC_ELIGIBILITY = "get_sync_eligibility"

        // Ad Blocking
        const val ADBLOCK_STATUS = "adblock_status"
        const val TOGGLE_ADBLOCK = "toggle_adblock"
        const val ADBLOCK_ADD_EXCEPTION = "adblock_add_exception"
        const val ADBLOCK_REMOVE_EXCEPTION = "adblock_remove_exception"
        const val ADBLOCK_EXCEPT_ACTIVE = "adblock_except_active"

        // Permissions
        const val PERMISSION_LIST_DECISIONS = "permission_list_decisions"
        const val PERMISSION_SET_DECISION = "permission_set_decision"
        const val PERMISSION_RESPOND = "permission_respond"

        // Downloads
        const val DOWNLOADS_LIST = "downloads_list"
        const val DOWNLOADS_OPEN = "downloads_open"
        const val DOWNLOADS_SHOW_IN_FOLDER = "downloads_show_in_folder"
        const val DOWNLOADS_CANCEL = "downloads_cancel"
        const val DOWNLOADS_CLEAR_COMPLETED = "downloads_clear_completed"

        // Surfaces
        const val OPEN_SURFACE = "open_surface"
        const val SURFACE_CLOSE = "surface_close"
    }

    // ===== Event Names =====

    object Events {
        const val STATE_UPDATED = "blanc:state-updated"
        const val TAB_EVENT = "blanc:tab-event"
        const val BLOCKING_STATUS = "blanc:blocking-status"
        const val PERMISSION_REQUEST = "blanc:permission-request"
        const val PERMISSION_RESOLVED = "blanc:permission-resolved"
        const val DOWNLOAD_UPDATED = "blanc:download-updated"
        const val TOAST = "blanc:toast"
        const val CREATE_WINDOW_REQUEST = "blanc:create-window-request"
    }

    // ===== Payload Types =====

    // State Projection
    data class StateProjection(
        val windows: List<WindowProjection>,
        val focusedWindowId: String?,
        val adblockEnabled: Boolean,
        val blockingReady: Boolean,
        val totalBlocked: Long,
        val blockingError: String?
    )

    data class WindowProjection(
        val id: String,
        val label: String,
        val profileId: String,
        val activeTabId: String?,
        val tabIds: List<String>,
        val groups: List<GroupProjection>,
        val overlay: OverlayProjection?,
        val closedTabs: List<ClosedTabProjection>,
        val workspaceId: String?,
        val permissionPromptOpen: Boolean
    )

    data class GroupProjection(
        val id: String,
        val name: String,
        val collapsed: Boolean,
        val tabIds: List<String>
    )

    data class OverlayProjection(
        val mode: String?, // "panel", "palette", "find", null
        val anchorRect: Rect?
    )

    data class Rect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    data class ClosedTabProjection(
        val id: String,
        val title: String?,
        val favicon: String?,
        val isGroup: Boolean,
        val groupName: String?,
        val tabCount: Int
    )

    // Tab Record (for commands that return tabs)
    data class TabRecord(
        val id: String,
        val windowId: String,
        val url: String,
        val title: String?,
        val favicon: String?,
        val isPrivate: Boolean,
        val isPinned: Boolean,
        val isMuted: Boolean,
        val groupId: String?,
        val position: Int,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
        val isLoading: Boolean,
        val progress: Int,
        val blockedCount: Int,
        val isActive: Boolean
    )

    // Window Record (for list_windows)
    data class WindowRecord(
        val id: String,
        val label: String,
        val profileId: String,
        val activeTabId: String?,
        val tabCount: Int,
        val workspaceId: String?
    )

    // History
    data class HistoryPage(
        val entries: List<HistoryEntry>,
        val total: Int
    )

    data class HistoryEntry(
        val id: Long,
        val url: String,
        val title: String?,
        val visitTime: Long,
        val favicon: String?,
        val isPrivate: Boolean
    )

    // Favorites
    data class Favorite(
        val id: String,
        val url: String,
        val title: String,
        val favicon: String?,
        val folderId: String?,
        val position: Int,
        val createdAt: Long,
        val updatedAt: Long,
        val isPinned: Boolean
    )

    // Settings
    data class UserSettings(
        val adblockEnabled: Boolean,
        val adblockExceptions: List<String>,
        val defaultSearchEngine: String,
        val theme: String,
        val homepage: String,
        val startupBehavior: String,
        val quietTabsDelay: String,
        val blockThirdPartyCookies: Boolean,
        val doNotTrack: Boolean,
        val clearOnExit: Boolean,
        val sendUsageStats: Boolean,
        val showHomeButton: Boolean,
        val showBookmarksBar: Boolean,
        val tabPreview: Boolean,
        val smoothScrolling: Boolean,
        val activeProfileId: String,
        val syncEnabled: Boolean,
        val syncPassphraseSet: Boolean,
        val appIcon: String,
        val patronActive: Boolean
    )

    data class SyncEligibility(
        val canSync: Boolean,
        val reason: String?
    )

    // Adblock
    data class BlockingStatus(
        val enabled: Boolean,
        val ready: Boolean,
        val totalBlocked: Long,
        val error: String?
    )

    // Permissions
    data class PermissionDecisionRecord(
        val origin: String,
        val resource: String,
        val decision: Int, // 0=Default, 1=Allow, 2=Deny, 3=Ask
        val remembered: Boolean
    )

    data class PermissionRequest(
        val id: String,
        val windowId: String,
        val tabId: String,
        val origin: String,
        val resource: String,
        val kind: String
    )

    // Downloads
    data class DownloadRecord(
        val id: String,
        val windowId: String,
        val tabId: String?,
        val url: String,
        val fileName: String,
        val mimeType: String?,
        val totalBytes: Long,
        val receivedBytes: Long,
        val targetPath: String?,
        val state: Int, // 0=Pending, 1=InProgress, 2=Completed, 3=Cancelled, 4=Failed
        val error: String?,
        val startedAt: Long,
        val completedAt: Long
    )

    // Surfaces
    data class SurfaceRequest(
        val surface: String,
        val private: Boolean
    )

    // Tab Events
    sealed class TabEvent {
        data class Loading(val tabId: String, val generation: Long) : TabEvent()
        data class Loaded(val tabId: String, val generation: Long) : TabEvent()
        data class Navigated(val tabId: String, val url: String, val title: String?, val generation: Long) : TabEvent()
        data class TitleChanged(val tabId: String, val title: String) : TabEvent()
        data class CaptureChanged(val tabId: String, val capturing: Boolean) : TabEvent()
        data class NavigationError(val tabId: String, val url: String, val message: String) : TabEvent()
    }

    // Toast
    data class Toast(
        val kind: String, // "error" | "info" | "success"
        val message: String
    )

    // Create Window Request (Android only)
    data class CreateWindowRequest(
        val url: String,
        val private: Boolean
    )

    // ===== Serialization Helpers =====

    /**
     * Creates a command message.
     */
    fun createCommand(command: String, args: Map<String, Any>? = null, id: String? = null): String {
        return gson.toJson(Command(command, args, id))
    }

    /**
     * Creates an event message.
     */
    fun createEvent(event: String, data: Any? = null): String {
        return gson.toJson(Event(event, data))
    }

    /**
     * Creates a success response.
     */
    fun createResponse(id: String, result: Any? = null): String {
        return gson.toJson(Response(id, result))
    }

    /**
     * Creates an error response.
     */
    fun createErrorResponse(id: String, code: String, message: String, details: Any? = null): String {
        return gson.toJson(Response(id, null, ErrorInfo(code, message, details)))
    }

    /**
     * Parses a command message from JSON.
     */
    fun parseCommand(json: String): Command? {
        return try {
            gson.fromJson(json, Command::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Serializes any object to JSON.
     */
    fun toJson(obj: Any): String {
        return gson.toJson(obj)
    }

    /**
     * Deserializes JSON to a type.
     */
    fun <T> fromJson(json: String, type: Type): T? {
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deserializes JSON to a class.
     */
    fun <T> fromJson(json: String, clazz: Class<T>): T? {
        return try {
            gson.fromJson(json, clazz)
        } catch (e: Exception) {
            null
        }
    }
}