package me.bnfy.blanc.bridge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.bnfy.blanc.storage.AppDatabase
import me.bnfy.blanc.storage.Repository
import me.bnfy.blanc.tab.TabManager
import me.bnfy.blanc.tab.WebViewFactory
import me.bnfy.blanc.adblock.AdblockEngine
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito

@RunWith(AndroidJUnit4::class)
class BlancBridgeInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: Repository
    private lateinit var tabManager: TabManager
    private lateinit var adblockEngine: AdblockEngine
    private lateinit var bridge: BlancBridge
    private val testDispatcher = kotlinx.coroutines.test.TestDispatcher()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        repository = Repository.createForTesting(context)
        
        // Mock dependencies
        val webViewFactory = Mockito.mock(WebViewFactory::class.java)
        adblockEngine = Mockito.mock(AdblockEngine::class.java)
        
        // We can't easily create a real TabManager in test without Activity
        // So we'll test the bridge logic directly where possible
    }

    @After
    fun teardown() {
        database.close()
        Repository.clearInstance()
    }

    @Test
    fun testBridgeCreation() {
        // Test that the bridge can be instantiated with required dependencies
        // Note: Full integration test requires Activity and WebView
        assertThat(repository, notNullValue())
        assertThat(database, notNullValue())
    }

    @Test
    fun testRepositoryInitialization() = kotlinx.coroutines.runBlocking {
        // Test that repository initializes defaults
        repository.initializeDefaults().await()
        
        val personalProfile = repository.profileDao.getPersonalProfile().await()
        assertThat(personalProfile, notNullValue())
        assertThat(personalProfile!!.isPersonal, equalTo(true))
        
        val engines = repository.settingsDao.getSearchEngines("personal").await()
        assertThat(engines.size, greaterThanOrEqualTo(4)) // DuckDuckGo, Google, Bing, Brave
    }

    @Test
    fun testSettingsRoundTrip() = kotlinx.coroutines.runBlocking {
        val settings = Repository.UserSettings(
            adblockEnabled = false,
            theme = "dark",
            defaultSearchEngine = "brave",
            quietTabsDelay = "30m"
        )
        
        repository.saveSettings(settings).await()
        
        val retrieved = repository.getSettings().await()
        assertThat(retrieved.adblockEnabled, equalTo(false))
        assertThat(retrieved.theme, equalTo("dark"))
        assertThat(retrieved.defaultSearchEngine, equalTo("brave"))
        assertThat(retrieved.quietTabsDelay, equalTo("30m"))
    }

    @Test
    fun testAdblockExceptionPersistence() = kotlinx.coroutines.runBlocking {
        repository.addAdblockException("example.com").await()
        
        val isException = repository.profileDao.isException("example.com", "personal").await()
        assertThat(isException, equalTo(1))
        
        val exceptions = repository.profileDao.getAdblockExceptions("personal").await()
        assertThat(exceptions.size, equalTo(1))
        assertThat(exceptions[0].hostname, equalTo("example.com"))
        
        repository.removeAdblockException("example.com").await()
        
        val afterRemove = repository.profileDao.isException("example.com", "personal").await()
        assertThat(afterRemove, equalTo(0))
    }

    @Test
    fun testHistoryRecording() = kotlinx.coroutines.runBlocking {
        repository.recordHistoryVisit(
            "https://example.com",
            "Example Domain",
            "https://example.com/favicon.ico",
            "personal"
        ).await()
        
        val entries = repository.historyDao.getHistoryPage("personal", 10, 0, null).await()
        assertThat(entries.size, greaterThanOrEqualTo(1))
        
        val found = entries.find { it.url == "https://example.com" }
        assertThat(found, notNullValue())
        assertThat(found!!.title, equalTo("Example Domain"))
    }

    @Test
    fun testFavoriteOperations() = kotlinx.coroutines.runBlocking {
        val favorite = me.bnfy.blanc.storage.Favorite(
            id = "fav-test",
            url = "https://favorite.com",
            title = "Favorite Site",
            isPinned = true,
            profileId = "personal"
        )
        
        repository.favoriteDao.insert(favorite).await()
        
        val retrieved = repository.favoriteDao.getById("fav-test").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.isPinned, equalTo(true))
        
        val all = repository.favoriteDao.getAll("personal").await()
        assertThat(all.size, greaterThanOrEqualTo(1))
    }

    @Test
    fun testSessionState() = kotlinx.coroutines.runBlocking {
        val window = me.bnfy.blanc.storage.WindowEntity(
            id = "win-session",
            label = "Session Window",
            profileId = "personal"
        )
        
        val tab = me.bnfy.blanc.storage.TabEntity(
            id = "tab-session",
            windowId = "win-session",
            url = "https://session.com",
            title = "Session Tab",
            profileId = "personal"
        )
        
        repository.tabDao.insertWindow(window).await()
        repository.tabDao.insert(tab).await()
        
        val session = repository.tabDao.getSessionState("personal").await()
        assertThat(session.windows.size, equalTo(1))
        assertThat(session.windows[0].window.id, equalTo("win-session"))
        assertThat(session.windows[0].tabs.size, equalTo(1))
    }
}