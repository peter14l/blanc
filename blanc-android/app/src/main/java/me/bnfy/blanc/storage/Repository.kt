package me.bnfy.blanc.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import kotlinx.coroutines.CoroutineExceptionHandler

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "blanc_preferences")

/**
 * Repository layer - Single source of truth for data access.
 * Combines Room (structured data) + DataStore (simple preferences).
 * Exposes Flow/LiveData for reactive UI.
 */
class Repository private constructor(
    private val context: Context,
    private val database: AppDatabase,
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) {

    fun close() {
        database.close()
    }

    // ===== DataStore Keys =====

    // General settings
    private val KEY_ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
    private val KEY_ADBLOCK_EXCEPTIONS = stringSetPreferencesKey("adblock_exceptions")
    private val KEY_DEFAULT_SEARCH_ENGINE = stringPreferencesKey("default_search_engine")
    private val KEY_THEME = stringPreferencesKey("theme") // system, light, dark
    private val KEY_HOMEPAGE = stringPreferencesKey("homepage")
    private val KEY_STARTUP_BEHAVIOR = stringPreferencesKey("startup_behavior") // newtab, restore, homepage
    private val KEY_QUIET_TABS_DELAY = stringPreferencesKey("quiet_tabs_delay") // off, 30m, 1h, 6h

    // Privacy settings
    private val KEY_BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("block_third_party_cookies")
    private val KEY_DO_NOT_TRACK = booleanPreferencesKey("do_not_track")
    private val KEY_CLEAR_ON_EXIT = booleanPreferencesKey("clear_on_exit")
    private val KEY_SEND_USAGE_STATS = booleanPreferencesKey("send_usage_stats")

    // UI settings
    private val KEY_SHOW_HOME_BUTTON = booleanPreferencesKey("show_home_button")
    private val KEY_SHOW_BOOKMARKS_BAR = booleanPreferencesKey("show_bookmarks_bar")
    private val KEY_TAB_PREVIEW = booleanPreferencesKey("tab_preview")
    private val KEY_SMOOTH_SCROLLING = booleanPreferencesKey("smooth_scrolling")

    // Profile/Sync
    private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    private val KEY_SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
    private val KEY_SYNC_PASSPHRASE_SET = booleanPreferencesKey("sync_passphrase_set")

    // App icon (Sunrise, Sunrise Dark, Paper, Ink)
    private val KEY_APP_ICON = stringPreferencesKey("app_icon")

    // Patreon/Patron
    private val KEY_PATRON_ACTIVE = booleanPreferencesKey("patron_active")
    private val KEY_PATRON_ENTITLEMENTS = stringPreferencesKey("patron_entitlements")

    // Install ID (for telemetry)
    private val KEY_INSTALL_ID = stringPreferencesKey("install_id")
    private val KEY_SESSION_ID = stringPreferencesKey("session_id")
    private val KEY_HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")

    // Companion for singleton
    companion object {
        @Volatile private var INSTANCE: Repository? = null
        private val EXCEPTION_HANDLER = CoroutineExceptionHandler { _, e ->
            e.printStackTrace()
        }

        fun getInstance(context: Context): Repository {
            return INSTANCE ?: synchronized(this) {
                val database = AppDatabase.getInstance(context)
                val instance = Repository(context.applicationContext, database, context.applicationContext.dataStore, Gson())
                INSTANCE = instance
                instance
            }
        }

        fun createForTesting(context: Context): Repository {
            val database = AppDatabase.createInMemory(context)
            return Repository(context.applicationContext, database, context.applicationContext.dataStore, Gson())
        }

        fun clearInstance() {
            INSTANCE?.close()
            AppDatabase.clearInstance()
            INSTANCE = null
        }
    }

    // ===== DAO Accessors =====

    val historyDao = database.historyDao()
    val bookmarkDao = database.bookmarkDao()
    val favoriteDao = database.favoriteDao()
    val settingsDao = database.settingsDao()
    val tabDao = database.tabDao()
    val closedTabDao = database.closedTabDao()
    val downloadDao = database.downloadDao()
    val profileDao = database.profileDao()

    // ===== DataStore Settings Flow =====

    private val preferencesFlow: Flow<Preferences> = dataStore.data
        .catch { e ->
            if (e is java.io.IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }

    // ===== Public Settings API =====

    /** AdBlock enabled setting. */
    val adblockEnabled: Flow<Boolean> = preferencesFlow
        .map { it[KEY_ADBLOCK_ENABLED] ?: true }
        .distinctUntilChanged()

    /** AdBlock exceptions (hostnames). */
    val adblockExceptions: Flow<Set<String>> = preferencesFlow
        .map { it[KEY_ADBLOCK_EXCEPTIONS] ?: emptySet() }
        .distinctUntilChanged()

    /** Default search engine ID. */
    val defaultSearchEngine: Flow<String> = preferencesFlow
        .map { it[KEY_DEFAULT_SEARCH_ENGINE] ?: "duckduckgo" }
        .distinctUntilChanged()

    /** Theme setting. */
    val theme: Flow<String> = preferencesFlow
        .map { it[KEY_THEME] ?: "system" }
        .distinctUntilChanged()

    /** Homepage URL. */
    val homepage: Flow<String> = preferencesFlow
        .map { it[KEY_HOMEPAGE] ?: "blanc://newtab" }
        .distinctUntilChanged()

    /** Startup behavior. */
    val startupBehavior: Flow<String> = preferencesFlow
        .map { it[KEY_STARTUP_BEHAVIOR] ?: "newtab" }
        .distinctUntilChanged()

    /** Quiet tabs delay setting. */
    val quietTabsDelay: Flow<String> = preferencesFlow
        .map { it[KEY_QUIET_TABS_DELAY] ?: "1h" }
        .distinctUntilChanged()

    /** Block third-party cookies. */
    val blockThirdPartyCookies: Flow<Boolean> = preferencesFlow
        .map { it[KEY_BLOCK_THIRD_PARTY_COOKIES] ?: true }
        .distinctUntilChanged()

    /** Do Not Track. */
    val doNotTrack: Flow<Boolean> = preferencesFlow
        .map { it[KEY_DO_NOT_TRACK] ?: true }
        .distinctUntilChanged()

    /** Clear on exit. */
    val clearOnExit: Flow<Boolean> = preferencesFlow
        .map { it[KEY_CLEAR_ON_EXIT] ?: false }
        .distinctUntilChanged()

    /** Send usage stats. */
    val sendUsageStats: Flow<Boolean> = preferencesFlow
        .map { it[KEY_SEND_USAGE_STATS] ?: true }
        .distinctUntilChanged()

    /** Show home button. */
    val showHomeButton: Flow<Boolean> = preferencesFlow
        .map { it[KEY_SHOW_HOME_BUTTON] ?: true }
        .distinctUntilChanged()

    /** Show bookmarks bar. */
    val showBookmarksBar: Flow<Boolean> = preferencesFlow
        .map { it[KEY_SHOW_BOOKMARKS_BAR] ?: false }
        .distinctUntilChanged()

    /** Tab preview. */
    val tabPreview: Flow<Boolean> = preferencesFlow
        .map { it[KEY_TAB_PREVIEW] ?: true }
        .distinctUntilChanged()

    /** Smooth scrolling. */
    val smoothScrolling: Flow<Boolean> = preferencesFlow
        .map { it[KEY_SMOOTH_SCROLLING] ?: true }
        .distinctUntilChanged()

    /** Active profile ID. */
    val activeProfileId: Flow<String> = preferencesFlow
        .map { it[KEY_ACTIVE_PROFILE_ID] ?: "personal" }
        .distinctUntilChanged()

    /** Sync enabled. */
    val syncEnabled: Flow<Boolean> = preferencesFlow
        .map { it[KEY_SYNC_ENABLED] ?: false }
        .distinctUntilChanged()

    /** Sync passphrase set. */
    val syncPassphraseSet: Flow<Boolean> = preferencesFlow
        .map { it[KEY_SYNC_PASSPHRASE_SET] ?: false }
        .distinctUntilChanged()

    /** App icon. */
    val appIcon: Flow<String> = preferencesFlow
        .map { it[KEY_APP_ICON] ?: "sunrise" }
        .distinctUntilChanged()

    /** Patron active. */
    val patronActive: Flow<Boolean> = preferencesFlow
        .map { it[KEY_PATRON_ACTIVE] ?: false }
        .distinctUntilChanged()

    /** Install ID. */
    val installId: Flow<String> = preferencesFlow
        .map { it[KEY_INSTALL_ID] ?: generateInstallId() }
        .distinctUntilChanged()

    /** Has completed onboarding walkthrough. */
    val hasCompletedOnboarding: Flow<Boolean> = preferencesFlow
        .map { it[KEY_HAS_COMPLETED_ONBOARDING] ?: false }
        .distinctUntilChanged()

    suspend fun setHasCompletedOnboarding(completed: Boolean) {
        dataStore.edit { it[KEY_HAS_COMPLETED_ONBOARDING] = completed }
    }

    /** Session ID. */
    val sessionId: Flow<String> = preferencesFlow
        .map { it[KEY_SESSION_ID] ?: generateSessionId() }
        .distinctUntilChanged()

    // ===== Settings Mutation Methods =====

    suspend fun setAdblockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ADBLOCK_ENABLED] = enabled }
    }

    suspend fun addAdblockException(hostname: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_ADBLOCK_EXCEPTIONS] ?: mutableSetOf()
            val updated = current.toMutableSet().apply { add(hostname.lowercase()) }
            prefs[KEY_ADBLOCK_EXCEPTIONS] = updated
        }
        // Also persist to Room for querying
        profileDao.insertAdblockException(AdblockExceptionEntity(hostname.lowercase(), getActiveProfileIdSync()))
    }

    suspend fun removeAdblockException(hostname: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_ADBLOCK_EXCEPTIONS] ?: mutableSetOf()
            val updated = current.toMutableSet().apply { remove(hostname.lowercase()) }
            prefs[KEY_ADBLOCK_EXCEPTIONS] = updated
        }
        profileDao.deleteAdblockException(hostname.lowercase(), getActiveProfileIdSync())
    }

    suspend fun setDefaultSearchEngine(engineId: String) {
        dataStore.edit { it[KEY_DEFAULT_SEARCH_ENGINE] = engineId }
    }

    suspend fun setTheme(themeValue: String) {
        dataStore.edit { it[KEY_THEME] = themeValue }
    }

    suspend fun setHomepage(url: String) {
        dataStore.edit { it[KEY_HOMEPAGE] = url }
    }

    suspend fun setStartupBehavior(behavior: String) {
        dataStore.edit { it[KEY_STARTUP_BEHAVIOR] = behavior }
    }

    suspend fun setQuietTabsDelay(delay: String) {
        dataStore.edit { it[KEY_QUIET_TABS_DELAY] = delay }
    }

    suspend fun setBlockThirdPartyCookies(block: Boolean) {
        dataStore.edit { it[KEY_BLOCK_THIRD_PARTY_COOKIES] = block }
    }

    suspend fun setDoNotTrack(enabled: Boolean) {
        dataStore.edit { it[KEY_DO_NOT_TRACK] = enabled }
    }

    suspend fun setClearOnExit(enabled: Boolean) {
        dataStore.edit { it[KEY_CLEAR_ON_EXIT] = enabled }
    }

    suspend fun setSendUsageStats(enabled: Boolean) {
        dataStore.edit { it[KEY_SEND_USAGE_STATS] = enabled }
    }

    suspend fun setShowHomeButton(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_HOME_BUTTON] = show }
    }

    suspend fun setShowBookmarksBar(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_BOOKMARKS_BAR] = show }
    }

    suspend fun setTabPreview(enabled: Boolean) {
        dataStore.edit { it[KEY_TAB_PREVIEW] = enabled }
    }

    suspend fun setSmoothScrolling(enabled: Boolean) {
        dataStore.edit { it[KEY_SMOOTH_SCROLLING] = enabled }
    }

    suspend fun setActiveProfileId(profileId: String) {
        dataStore.edit { it[KEY_ACTIVE_PROFILE_ID] = profileId }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SYNC_ENABLED] = enabled }
    }

    suspend fun setSyncPassphraseSet(set: Boolean) {
        dataStore.edit { it[KEY_SYNC_PASSPHRASE_SET] = set }
    }

    suspend fun setAppIcon(icon: String) {
        dataStore.edit { it[KEY_APP_ICON] = icon }
    }

    suspend fun setPatronActive(active: Boolean) {
        dataStore.edit { it[KEY_PATRON_ACTIVE] = active }
    }

    suspend fun setPatronEntitlements(entitlementsJson: String) {
        dataStore.edit { it[KEY_PATRON_ENTITLEMENTS] = entitlementsJson }
    }

    suspend fun setInstallId(id: String) {
        dataStore.edit { it[KEY_INSTALL_ID] = id }
    }

    suspend fun setSessionId(id: String) {
        dataStore.edit { it[KEY_SESSION_ID] = id }
    }

    // ===== Helper Methods =====

    private suspend fun getActiveProfileIdSync(): String {
        return dataStore.data.first() [KEY_ACTIVE_PROFILE_ID] ?: "personal"
    }

    private fun generateInstallId(): String {
        return java.util.UUID.randomUUID().toString()
    }

    private fun generateSessionId(): String {
        return java.util.UUID.randomUUID().toString()
    }

    // ===== Settings Data Class for Bulk Operations =====

    data class UserSettings(
        val adblockEnabled: Boolean = true,
        val adblockExceptions: Set<String> = emptySet(),
        val defaultSearchEngine: String = "duckduckgo",
        val theme: String = "system",
        val homepage: String = "blanc://newtab",
        val startupBehavior: String = "newtab",
        val quietTabsDelay: String = "1h",
        val blockThirdPartyCookies: Boolean = true,
        val doNotTrack: Boolean = true,
        val clearOnExit: Boolean = false,
        val sendUsageStats: Boolean = true,
        val showHomeButton: Boolean = true,
        val showBookmarksBar: Boolean = false,
        val tabPreview: Boolean = true,
        val smoothScrolling: Boolean = true,
        val activeProfileId: String = "personal",
        val syncEnabled: Boolean = false,
        val syncPassphraseSet: Boolean = false,
        val appIcon: String = "sunrise",
        val patronActive: Boolean = false,
        val installId: String = "",
        val sessionId: String = ""
    )

    /** Gets all settings as a single UserSettings object. */
    suspend fun getSettings(): UserSettings {
        val prefs = dataStore.data.first()
        return UserSettings(
            adblockEnabled = prefs[KEY_ADBLOCK_ENABLED] ?: true,
            adblockExceptions = prefs[KEY_ADBLOCK_EXCEPTIONS] ?: emptySet(),
            defaultSearchEngine = prefs[KEY_DEFAULT_SEARCH_ENGINE] ?: "duckduckgo",
            theme = prefs[KEY_THEME] ?: "system",
            homepage = prefs[KEY_HOMEPAGE] ?: "blanc://newtab",
            startupBehavior = prefs[KEY_STARTUP_BEHAVIOR] ?: "newtab",
            quietTabsDelay = prefs[KEY_QUIET_TABS_DELAY] ?: "1h",
            blockThirdPartyCookies = prefs[KEY_BLOCK_THIRD_PARTY_COOKIES] ?: true,
            doNotTrack = prefs[KEY_DO_NOT_TRACK] ?: true,
            clearOnExit = prefs[KEY_CLEAR_ON_EXIT] ?: false,
            sendUsageStats = prefs[KEY_SEND_USAGE_STATS] ?: true,
            showHomeButton = prefs[KEY_SHOW_HOME_BUTTON] ?: true,
            showBookmarksBar = prefs[KEY_SHOW_BOOKMARKS_BAR] ?: false,
            tabPreview = prefs[KEY_TAB_PREVIEW] ?: true,
            smoothScrolling = prefs[KEY_SMOOTH_SCROLLING] ?: true,
            activeProfileId = prefs[KEY_ACTIVE_PROFILE_ID] ?: "personal",
            syncEnabled = prefs[KEY_SYNC_ENABLED] ?: false,
            syncPassphraseSet = prefs[KEY_SYNC_PASSPHRASE_SET] ?: false,
            appIcon = prefs[KEY_APP_ICON] ?: "sunrise",
            patronActive = prefs[KEY_PATRON_ACTIVE] ?: false,
            installId = prefs[KEY_INSTALL_ID] ?: generateInstallId(),
            sessionId = prefs[KEY_SESSION_ID] ?: generateSessionId()
        )
    }

    /** Gets all settings as Flow. */
    fun getSettingsFlow(): Flow<UserSettings> = preferencesFlow.map { prefs ->
        UserSettings(
            adblockEnabled = prefs[KEY_ADBLOCK_ENABLED] ?: true,
            adblockExceptions = prefs[KEY_ADBLOCK_EXCEPTIONS] ?: emptySet(),
            defaultSearchEngine = prefs[KEY_DEFAULT_SEARCH_ENGINE] ?: "duckduckgo",
            theme = prefs[KEY_THEME] ?: "system",
            homepage = prefs[KEY_HOMEPAGE] ?: "blanc://newtab",
            startupBehavior = prefs[KEY_STARTUP_BEHAVIOR] ?: "newtab",
            quietTabsDelay = prefs[KEY_QUIET_TABS_DELAY] ?: "1h",
            blockThirdPartyCookies = prefs[KEY_BLOCK_THIRD_PARTY_COOKIES] ?: true,
            doNotTrack = prefs[KEY_DO_NOT_TRACK] ?: true,
            clearOnExit = prefs[KEY_CLEAR_ON_EXIT] ?: false,
            sendUsageStats = prefs[KEY_SEND_USAGE_STATS] ?: true,
            showHomeButton = prefs[KEY_SHOW_HOME_BUTTON] ?: true,
            showBookmarksBar = prefs[KEY_SHOW_BOOKMARKS_BAR] ?: false,
            tabPreview = prefs[KEY_TAB_PREVIEW] ?: true,
            smoothScrolling = prefs[KEY_SMOOTH_SCROLLING] ?: true,
            activeProfileId = prefs[KEY_ACTIVE_PROFILE_ID] ?: "personal",
            syncEnabled = prefs[KEY_SYNC_ENABLED] ?: false,
            syncPassphraseSet = prefs[KEY_SYNC_PASSPHRASE_SET] ?: false,
            appIcon = prefs[KEY_APP_ICON] ?: "sunrise",
            patronActive = prefs[KEY_PATRON_ACTIVE] ?: false,
            installId = prefs[KEY_INSTALL_ID] ?: generateInstallId(),
            sessionId = prefs[KEY_SESSION_ID] ?: generateSessionId()
        )
    }

    /** Saves all settings from a UserSettings object. */
    suspend fun saveSettings(settings: UserSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_ADBLOCK_ENABLED] = settings.adblockEnabled
            prefs[KEY_ADBLOCK_EXCEPTIONS] = settings.adblockExceptions
            prefs[KEY_DEFAULT_SEARCH_ENGINE] = settings.defaultSearchEngine
            prefs[KEY_THEME] = settings.theme
            prefs[KEY_HOMEPAGE] = settings.homepage
            prefs[KEY_STARTUP_BEHAVIOR] = settings.startupBehavior
            prefs[KEY_QUIET_TABS_DELAY] = settings.quietTabsDelay
            prefs[KEY_BLOCK_THIRD_PARTY_COOKIES] = settings.blockThirdPartyCookies
            prefs[KEY_DO_NOT_TRACK] = settings.doNotTrack
            prefs[KEY_CLEAR_ON_EXIT] = settings.clearOnExit
            prefs[KEY_SEND_USAGE_STATS] = settings.sendUsageStats
            prefs[KEY_SHOW_HOME_BUTTON] = settings.showHomeButton
            prefs[KEY_SHOW_BOOKMARKS_BAR] = settings.showBookmarksBar
            prefs[KEY_TAB_PREVIEW] = settings.tabPreview
            prefs[KEY_SMOOTH_SCROLLING] = settings.smoothScrolling
            prefs[KEY_ACTIVE_PROFILE_ID] = settings.activeProfileId
            prefs[KEY_SYNC_ENABLED] = settings.syncEnabled
            prefs[KEY_SYNC_PASSPHRASE_SET] = settings.syncPassphraseSet
            prefs[KEY_APP_ICON] = settings.appIcon
            prefs[KEY_PATRON_ACTIVE] = settings.patronActive
            prefs[KEY_INSTALL_ID] = settings.installId
            prefs[KEY_SESSION_ID] = settings.sessionId
        }
    }

    // ===== High-level Operations =====

    /** Records a history visit (only for non-private tabs). */
    suspend fun recordHistoryVisit(url: String, title: String?, favicon: String?, profileId: String) {
        historyDao.recordVisit(url, title, System.currentTimeMillis(), favicon, false, profileId)
    }

    /** Gets history page for UI. */
    fun getHistoryPage(profileId: String, limit: Int, offset: Int, query: String?): Flow<HistoryPage> {
        return combine(
            historyDao.getHistoryPageFlow(profileId, limit, offset, query),
            historyDao.getTotalCountFlow(profileId, query)
        ) { entries, total ->
            HistoryPage(entries, total)
        }
    }

    /** Gets favorites for new tab page. */
    fun getFavoritesForNewTab(profileId: String): Flow<List<Favorite>> {
        return favoriteDao.getAllFlow(profileId)
    }

    /** Gets pinned favorites for new tab page. */
    fun getPinnedFavorites(profileId: String): Flow<List<Favorite>> {
        return favoriteDao.getPinnedFlow(profileId)
    }

    /** Gets session state for restore. */
    suspend fun getSessionState(profileId: String): SessionState {
        return tabDao.getSessionState(profileId)
    }

    /** Saves session state. */
    suspend fun saveSessionState(state: SessionState) {
        tabDao.saveSession(state)
    }

    /** Clears session for a profile. */
    suspend fun clearSession(profileId: String) {
        tabDao.clearSession(profileId)
    }

    /** Initializes default profile and search engines. */
    suspend fun initializeDefaults() {
        // Ensure personal profile exists
        val personalProfile = profileDao.getPersonalProfile() ?: ProfileEntity(
            id = "personal",
            name = "Personal",
            isPersonal = true,
            isDefault = true
        ).also { profileDao.insert(it) }

        // Set as active if no active profile
        val active = activeProfileId.first()
        if (active == "personal" && !personalProfile.isDefault) {
            profileDao.setDefault("personal", System.currentTimeMillis())
        }

        // Initialize default search engines
        val engines = listOf(
            SearchEngineEntity("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?q=%s", true, "personal"),
            SearchEngineEntity("google", "Google", "https://www.google.com/search?q=%s", "https://www.google.com/complete/search?q=%s", false, "personal"),
            SearchEngineEntity("bing", "Bing", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s", false, "personal"),
            SearchEngineEntity("brave", "Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com/api/suggest?q=%s", false, "personal")
        )
        engines.forEach { settingsDao.insertSearchEngine(it) }
    }

    /** Exports all user data for backup. */
    suspend fun exportData(): String {
        val data = mapOf(
            "history" to historyDao.getByProfile("personal"),
            "bookmarks" to bookmarkDao.getAll("personal"),
            "favorites" to favoriteDao.getAll("personal"),
            "downloads" to downloadDao.getByProfile("personal"),
            "closedTabs" to closedTabDao.getByProfile("personal"),
            "profiles" to profileDao.getAll(),
            "workspaces" to profileDao.getWorkspacesByProfile("personal"),
            "permissions" to profileDao.getPermissionsByProfile("personal"),
            "adblockExceptions" to profileDao.getAdblockExceptions("personal"),
            "settings" to getSettings()
        )
        return gson.toJson(data)
    }

    /** Imports user data from backup. */
    suspend fun importData(json: String) {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val data = gson.fromJson<Map<String, Any>>(json, type)

        database.runInTransaction {
            // Import logic would go here
            // This is a simplified version - real implementation would be more complex
        }
    }
}

/**
 * History page result with pagination info.
 */
data class HistoryPage(
    val entries: List<HistoryEntry>,
    val total: Int
)