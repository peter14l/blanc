package me.bnfy.blanc.storage

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
class HistoryDaoTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testInsertAndGet() = runBlocking {
        val dao = database.historyDao()
        val entry = HistoryEntry(
            url = "https://example.com",
            title = "Example",
            visitTime = System.currentTimeMillis(),
            profileId = "personal"
        )

        val id = dao.insert(entry).await()
        assertThat(id, greaterThan(0L))

        val retrieved = dao.getById(id).await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.url, equalTo("https://example.com"))
    }

    @Test
    fun testRecordVisitUpdatesExisting() = runBlocking {
        val dao = database.historyDao()
        val url = "https://example.com"
        val profileId = "personal"

        // First visit
        dao.recordVisit(url, "First Visit", 1000L, null, false, profileId).await()
        var count = dao.getTotalCount(profileId, null).await()
        assertThat(count, equalTo(1))

        // Second visit (should update visitTime)
        dao.recordVisit(url, "Second Visit", 2000L, null, false, profileId).await()
        count = dao.getTotalCount(profileId, null).await()
        assertThat(count, equalTo(1))

        val entry = dao.getByUrl(url, profileId).await()
        assertThat(entry!!.title, equalTo("Second Visit"))
        assertThat(entry.visitTime, equalTo(2000L))
    }

    @Test
    fun testClearHistory() = runBlocking {
        val dao = database.historyDao()
        val profileId = "personal"

        for (i in 1..5) {
            dao.insert(HistoryEntry(
                url = "https://example.com/page$i",
                title = "Page $i",
                visitTime = System.currentTimeMillis(),
                profileId = profileId
            )).await()
        }

        var count = dao.getTotalCount(profileId, null).await()
        assertThat(count, equalTo(5))

        dao.clearHistory(profileId).await()

        count = dao.getTotalCount(profileId, null).await()
        assertThat(count, equalTo(0))
    }
}

@ExperimentalCoroutinesApi
class BookmarkDaoTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testInsertAndGet() = runBlocking {
        val dao = database.bookmarkDao()
        val bookmark = Bookmark(
            id = "bm-1",
            url = "https://example.com",
            title = "Example",
            profileId = "personal"
        )

        dao.insert(bookmark).await()

        val retrieved = dao.getById("bm-1").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.url, equalTo("https://example.com"))
    }

    @Test
    fun testGetByUrl() = runBlocking {
        val dao = database.bookmarkDao()
        val bookmark = Bookmark(
            id = "bm-1",
            url = "https://example.com",
            title = "Example",
            profileId = "personal"
        )

        dao.insert(bookmark).await()

        val retrieved = dao.getByUrl("https://example.com", "personal").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.id, equalTo("bm-1"))
    }

    @Test
    fun testIsBookmarked() = runBlocking {
        val dao = database.bookmarkDao()
        val bookmark = Bookmark(
            id = "bm-1",
            url = "https://example.com",
            title = "Example",
            profileId = "personal"
        )

        dao.insert(bookmark).await()

        val count = dao.isBookmarked("https://example.com", "personal").await()
        assertThat(count, equalTo(1))

        val countNot = dao.isBookmarked("https://notbookmarked.com", "personal").await()
        assertThat(countNot, equalTo(0))
    }
}

@ExperimentalCoroutinesApi
class FavoriteDaoTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testPinnedOrdering() = runBlocking {
        val dao = database.favoriteDao()
        val profileId = "personal"

        val unpinned = Favorite(id = "1", url = "https://a.com", title = "A", isPinned = false, position = 0, profileId = profileId)
        val pinned = Favorite(id = "2", url = "https://b.com", title = "B", isPinned = true, position = 0, profileId = profileId)

        dao.insert(unpinned).await()
        dao.insert(pinned).await()

        val all = dao.getAll(profileId).await()
        assertThat(all[0].id, equalTo("2")) // Pinned first
        assertThat(all[1].id, equalTo("1"))
    }

    @Test
    fun testSetPinned() = runBlocking {
        val dao = database.favoriteDao()
        val fav = Favorite(id = "1", url = "https://a.com", title = "A", isPinned = false, position = 0, profileId = "personal")
        dao.insert(fav).await()

        dao.setPinned("1", true, System.currentTimeMillis()).await()

        val retrieved = dao.getById("1").await()
        assertThat(retrieved!!.isPinned, equalTo(true))
    }
}

@ExperimentalCoroutinesApi
class TabDaoTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testTabWindowRelationship() = runBlocking {
        val dao = database.tabDao()

        val window = WindowEntity(
            id = "win-1",
            label = "Main Window",
            profileId = "personal"
        )
        dao.insertWindow(window).await()

        val tab = TabEntity(
            id = "tab-1",
            windowId = "win-1",
            url = "https://example.com",
            title = "Example",
            profileId = "personal"
        )
        dao.insert(tab).await()

        val tabs = dao.getByWindow("win-1").await()
        assertThat(tabs.size, equalTo(1))
        assertThat(tabs[0].id, equalTo("tab-1"))
    }

    @Test
    fun testActiveTabManagement() = runBlocking {
        val dao = database.tabDao()

        val tab1 = TabEntity(id = "tab-1", windowId = "win-1", url = "https://a.com", isActive = false, profileId = "personal")
        val tab2 = TabEntity(id = "tab-2", windowId = "win-1", url = "https://b.com", isActive = false, profileId = "personal")
        
        dao.insert(tab1).await()
        dao.insert(tab2).await()

        dao.setActiveTab("win-1", "tab-2").await()

        val activeTab = dao.getActiveTab("win-1").await()
        assertThat(activeTab!!.id, equalTo("tab-2"))
    }
}

@ExperimentalCoroutinesApi
class ClosedTabDaoTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testTierDegradation() = runBlocking {
        val dao = database.closedTabDao()

        val held = ClosedTabEntity(
            id = "held-1",
            windowId = "win-1",
            tabId = "tab-1",
            url = "https://example.com",
            tier = 1, // Held view
            profileId = "personal"
        )
        dao.insert(held).await()

        val snapshot = ClosedTabEntity(
            id = "snap-1",
            windowId = "win-1",
            tabId = "tab-2",
            url = "https://example2.com",
            tier = 2, // Snapshot
            profileId = "personal"
        )
        dao.insert(snapshot).await()

        // Degrade held views
        dao.degradeHeldViews("win-1").await()

        val degraded = dao.getById("held-1").await()
        assertThat(degraded!!.tier, equalTo(2))

        // Snapshot should remain snapshot
        val stillSnapshot = dao.getById("snap-1").await()
        assertThat(stillSnapshot!!.tier, equalTo(2))
    }

    @Test
    fun testMaxEntriesEnforcement() = runBlocking {
        val dao = database.closedTabDao()
        val windowId = "win-1"

        // Insert 30 entries
        for (i in 1..30) {
            dao.insert(ClosedTabEntity(
                id = "closed-$i",
                windowId = windowId,
                tabId = "tab-$i",
                url = "https://example.com/$i",
                profileId = "personal"
            )).await()
        }

        // Enforce max 25
        dao.enforceMaxEntries(windowId, 25).await()

        val count = dao.countByWindow(windowId).await()
        assertThat(count, equalTo(25))
    }
}

@ExperimentalCoroutinesApi
class DownloadDaoTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testDownloadStateFlow() = runBlocking {
        val dao = database.downloadDao()

        val download = DownloadEntity(
            id = "dl-1",
            windowId = "win-1",
            url = "https://example.com/file.zip",
            fileName = "file.zip",
            totalBytes = 1000,
            state = 0, // Pending
            profileId = "personal"
        )
        dao.insert(download).await()

        // Start download
        dao.updateProgress("dl-1", 100, 1000).await()
        dao.updateState("dl-1", 1, null, 0).await() // InProgress

        var retrieved = dao.getById("dl-1").await()
        assertThat(retrieved!!.state, equalTo(1))
        assertThat(retrieved.receivedBytes, equalTo(100L))

        // Complete
        dao.updateProgress("dl-1", 1000, 1000).await()
        dao.updateState("dl-1", 2, null, System.currentTimeMillis()).await() // Completed

        retrieved = dao.getById("dl-1").await()
        assertThat(retrieved!!.state, equalTo(2))
        assertThat(retrieved.completedAt, greaterThan(0L))
    }

    @Test
    fun testClearCompleted() = runBlocking {
        val dao = database.downloadDao()
        val profileId = "personal"

        val completed = DownloadEntity(
            id = "dl-1",
            windowId = "win-1",
            url = "https://example.com/done.zip",
            fileName = "done.zip",
            state = 2, // Completed
            profileId = profileId
        )
        val inProgress = DownloadEntity(
            id = "dl-2",
            windowId = "win-1",
            url = "https://example.com/progress.zip",
            fileName = "progress.zip",
            state = 1, // InProgress
            profileId = profileId
        )
        dao.insert(completed).await()
        dao.insert(inProgress).await()

        dao.clearCompletedByProfile(profileId).await()

        val remaining = dao.getByProfile(profileId).await()
        assertThat(remaining.size, equalTo(1))
        assertThat(remaining[0].id, equalTo("dl-2"))
    }
}

@ExperimentalCoroutinesApi
class ProfileDaoTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testPersonalProfile() = runBlocking {
        val dao = database.profileDao()

        val personal = ProfileEntity(
            id = "personal",
            name = "Personal",
            isPersonal = true,
            isDefault = true
        )
        dao.insert(personal).await()

        val retrieved = dao.getPersonalProfile().await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.id, equalTo("personal"))
        assertThat(retrieved.isPersonal, equalTo(true))
    }

    @Test
    fun testWorkspaceOperations() = runBlocking {
        val dao = database.profileDao()

        val profile = ProfileEntity(id = "profile-1", name = "Work", isPersonal = false)
        dao.insert(profile).await()

        val ws1 = WorkspaceEntity(id = "ws-1", profileId = "profile-1", name = "Dev")
        val ws2 = WorkspaceEntity(id = "ws-2", profileId = "profile-1", name = "Research")
        dao.insertWorkspace(ws1).await()
        dao.insertWorkspace(ws2).await()

        val workspaces = dao.getWorkspacesByProfile("profile-1").await()
        assertThat(workspaces.size, equalTo(2))
    }

    @Test
    fun testPermissionDecisions() = runBlocking {
        val dao = database.profileDao()

        val decision = PermissionDecisionEntity(
            origin = "https://example.com",
            resource = "camera",
            decision = 1, // Allow
            profileId = "personal"
        )
        dao.insertPermission(decision).await()

        val retrieved = dao.getPermission("https://example.com", "camera", "personal").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.decision, equalTo(1))
    }
}

@ExperimentalCoroutinesApi
class SettingsDaoTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testSettingsCRUD() = runBlocking {
        val dao = database.settingsDao()

        val setting = SettingEntity(
            key = "test.setting",
            value = "\"test value\"",
            profileId = "personal"
        )
        dao.insert(setting).await()

        val retrieved = dao.getByKey("test.setting", "personal").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.value, equalTo("\"test value\""))

        val updated = SettingEntity(
            key = "test.setting",
            value = "\"updated\"",
            profileId = "personal"
        )
        dao.update(updated).await()

        val retrieved2 = dao.getByKey("test.setting", "personal").await()
        assertThat(retrieved2!!.value, equalTo("\"updated\""))
    }

    @Test
    fun testSearchEngines() = runBlocking {
        val dao = database.settingsDao()

        val engine1 = SearchEngineEntity("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", null, true, "personal")
        val engine2 = SearchEngineEntity("google", "Google", "https://google.com/search?q=%s", null, false, "personal")
        dao.insertSearchEngine(engine1).await()
        dao.insertSearchEngine(engine2).await()

        val defaultEngine = dao.getDefaultSearchEngine("personal").await()
        assertThat(defaultEngine!!.id, equalTo("duckduckgo"))

        dao.clearDefaultSearchEngine("personal").await()
        dao.setDefaultSearchEngine("google", "personal").await()

        val newDefault = dao.getDefaultSearchEngine("personal").await()
        assertThat(newDefault!!.id, equalTo("google"))
    }
}

/**
 * Rule to replace Main dispatcher with TestDispatcher for tests.
 */
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = TestDispatcher()
) : TestRule {
    @ExperimentalCoroutinesApi
    override fun apply(statement: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement {
        return object : org.junit.runners.model.Statement() {
            @ExperimentalCoroutinesApi
            override fun evaluate() {
                Dispatchers.setMain(testDispatcher)
                try {
                    statement.evaluate()
                } finally {
                    Dispatchers.resetMain()
                    testDispatcher.cleanupTestCoroutines()
                }
            }
        }
    }
}