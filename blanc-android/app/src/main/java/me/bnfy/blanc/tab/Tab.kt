package me.bnfy.blanc.tab

import android.webkit.WebView
import androidx.annotation.Keep
import java.util.UUID

/**
 * Data class representing a browser tab's state.
 *
 * This class holds all the mutable state associated with a tab, including
 * its WebView, navigation history, loading state, and private browsing flag.
 * Instances are managed exclusively by [TabManager].
 */
@Keep
data class Tab(
    /** Unique identifier for this tab. */
    val id: String = UUID.randomUUID().toString(),

    /** The WebView instance rendering this tab's content. */
    var webView: WebView? = null,

    /** The currently displayed URL. */
    var url: String = "about:blank",

    /** The current page title. */
    var title: String = "",

    /** The window ID this tab belongs to. */
    var windowId: String = "default",

    /** The favicon URL. */
    var favicon: String? = null,

    /** Whether the tab can navigate backward in history. */
    var canGoBack: Boolean = false,

    /** Whether the tab can navigate forward in history. */
    var canGoForward: Boolean = false,

    /** Whether this tab is in private/incognito mode. */
    val isPrivate: Boolean = false,

    /** Whether the tab is currently loading a page. */
    var isLoading: Boolean = false,

    /** Number of blocked requests (ads/trackers) for the current page. */
    var blockedCount: Int = 0,

    /** Navigation history as a list of URLs. */
    var history: MutableList<String> = mutableListOf(),

    /** Current index within [history]. */
    var historyIndex: Int = -1,

    /** Current page loading progress (0-100). */
    var progress: Int = 0,

    /** Whether this tab is pinned. */
    var isPinned: Boolean = false,

    /** Whether this tab is muted. */
    var isMuted: Boolean = false,

    /** The group ID this tab belongs to, if any. */
    var groupId: String? = null,

    /** The position of this tab in the tab order. */
    var position: Int = 0,

    /** The workspace ID this tab belongs to, if any. */
    var workspaceId: String? = null,

    /** Whether this tab is currently active. */
    var isActive: Boolean = false,

    /** The profile ID this tab belongs to. */
    var profileId: String = "personal",

    /** Thumbnail bitmap for tab switcher (cached). */
    var thumbnail: android.graphics.Bitmap? = null
) {

    /**
     * Adds a URL to the navigation history.
     * Truncates any forward history beyond the current index.
     */
    fun addToHistory(newUrl: String) {
        // Truncate forward history if we're not at the end
        if (historyIndex < history.size - 1) {
            history = history.subList(0, historyIndex + 1).toMutableList()
        }
        history.add(newUrl)
        historyIndex = history.lastIndex
        updateNavigationState()
    }

    /**
     * Updates the canGoBack/canGoForward flags based on current history position.
     */
    fun updateNavigationState() {
        canGoBack = historyIndex > 0
        canGoForward = historyIndex < history.size - 1
    }

    /**
     * Navigates backward in history if possible.
     * @return The URL to navigate to, or null if cannot go back.
     */
    fun goBack(): String? {
        if (canGoBack) {
            historyIndex--
            updateNavigationState()
            return history[historyIndex]
        }
        return null
    }

    /**
     * Navigates forward in history if possible.
     * @return The URL to navigate to, or null if cannot go forward.
     */
    fun goForward(): String? {
        if (canGoForward) {
            historyIndex++
            updateNavigationState()
            return history[historyIndex]
        }
        return null
    }

    /**
     * Resets the tab to initial state (used when loading a new URL programmatically).
     */
    fun resetForNewNavigation() {
        isLoading = true
        progress = 0
        blockedCount = 0
        title = ""
    }

    /**
     * Creates a copy of this tab with updated fields for bridge events.
     * The WebView reference is not copied (set to null) since it's not serializable.
     */
    fun toBridgeTab(): Tab {
        return this.copy(webView = null)
    }

    companion object {
        /** Creates a new regular (non-private) tab. */
        fun create(): Tab = Tab()

        /** Creates a new private/incognito tab. */
        fun createPrivate(): Tab = Tab(isPrivate = true)
    }
}