package me.bnfy.blanc.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testDatabaseCreation() = runBlocking {
        // Verify all DAOs are accessible
        assertThat(database.historyDao(), notNullValue())
        assertThat(database.bookmarkDao(), notNullValue())
        assertThat(database.favoriteDao(), notNullValue())
        assertThat(database.settingsDao(), notNullValue())
        assertThat(database.tabDao(), notNullValue())
        assertThat(database.closedTabDao(), notNullValue())
        assertThat(database.downloadDao(), notNullValue())
        assertThat(database.profileDao(), notNullValue())
    }

    @Test
    fun testHistoryDaoOperations() = runBlocking {
        val dao = database.historyDao()

        // Insert
        val entry = HistoryEntry(
            url = "https://example.com",
            title = "Example Domain",
            visitTime = System.currentTimeMillis(),
            favicon = "https://example.com/favicon.ico",
            isPrivate = false,
            profileId = "personal"
        )
        val id = dao.insert(entry).await()
        assertThat(id, greaterThan(0L))

        // Get by ID
        val retrieved = dao.getById(id).await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.url, equalTo("https://example.com"))
        assertThat(retrieved.title, equalTo("Example Domain"))

        // Get by URL
        val byUrl = dao.getByUrl("https://example.com", "personal").await()
        assertThat(byUrl, notNullValue())
        assertThat(byUrl!!.id, equalTo(id))

        // Pagination
        for (i in 1..15) {
            dao.insert(HistoryEntry(
                url = "https://example.com/page$i",
                title = "Page $i",
                visitTime = System.currentTimeMillis() - i * 1000L,
                profileId = "personal"
            )).await()
        }

        val page1 = dao.getHistoryPage("personal", 10, 0, null).await()
        assertThat(page1.size, equalTo(10))

        val page2 = dao.getHistoryPage("personal", 10, 10, null).await()
        assertThat(page2.size, equalTo(6)) // 1 original + 15 new = 16 total

        // Search
        val searchResults = dao.getHistoryPage("personal", 10, 0, "page5").await()
        assertThat(searchResults.size, equalTo(1))
        assertThat(searchResults[0].url, containsString("page5"))

        // Clear
        dao.clearHistory("personal").await()
        val count = dao.getTotalCount("personal", null).await()
        assertThat(count, equalTo(0))
    }

    @Test
    fun testBookmarkDaoOperations() = runBlocking {
        val dao = database.bookmarkDao()

        val bookmark = Bookmark(
            id = "bm-1",
            url = "https://example.com",
            title = "Example",
            favicon = "https://example.com/favicon.ico",
            folderId = null,
            position = 0,
            profileId = "personal"
        )
        dao.insert(bookmark).await()

        val retrieved = dao.getById("bm-1").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.url, equalTo("https://example.com"))

        // Folder operations
        val folder = Bookmark(
            id = "bm-folder",
            url = "folder://bookmarks",
            title = "My Folder",
            profileId = "personal"
        )
        dao.insert(folder).await()

        val child = Bookmark(
            id = "bm-child",
            url = "https://child.com",
            title = "Child Bookmark",
            folderId = "bm-folder",
            profileId = "personal"
        )
        dao.insert(child).await()

        val folderContents = dao.getByFolder("personal", "bm-folder").await()
        assertThat(folderContents.size, equalTo(1))
        assertThat(folderContents[0].id, equalTo("bm-child"))

        val rootBookmarks = dao.getRootBookmarks("personal").await()
        assertThat(rootBookmarks.size, equalTo(2)) // bm-1 and bm-folder
    }

    @Test
    fun testFavoriteDaoOperations() = runBlocking {
        val dao = database.favoriteDao()

        val pinned = Favorite(
            id = "fav-pinned",
            url = "https://pinned.com",
            title = "Pinned",
            isPinned = true,
            position = 0,
            profileId = "personal"
        )
        val unpinned = Favorite(
            id = "fav-unpinned",
            url = "https://unpinned.com",
            title = "Unpinned",
            isPinned = false,
            position = 0,
            profileId = "personal"
        )

        dao.insert(pinned).await()
        dao.insert(unpinned).await()

        // Pinned should come first
        val all = dao.getAll("personal").await()
        assertThat(all[0].id, equalTo("fav-pinned"))
        assertThat(all[1].id, equalTo("fav-unpinned"))

        val pinnedOnly = dao.getPinned("personal").await()
        assertThat(pinnedOnly.size, equalTo(1))
        assertThat(pinnedOnly[0].id, equalTo("fav-pinned"))

        val unpinnedOnly = dao.getUnpinned("personal").await()
        assertThat(unpinnedOnly.size, equalTo(1))
        assertThat(unpinnedOnly[0].id, equalTo("fav-unpinned"))
    }

    @Test
    fun testTabDaoOperations() = runBlocking {
        val dao = database.tabDao()

        // Create window
        val window = WindowEntity(
            id = "win-1",
            label = "Main Window",
            profileId = "personal"
        )
        dao.insertWindow(window).await()

        // Create tabs
        val tab1 = TabEntity(
            id = "tab-1",
            windowId = "win-1",
            url = "https://a.com",
            title = "Tab A",
            isActive = true,
            profileId = "personal"
        )
        val tab2 = TabEntity(
            id = "tab-2",
            windowId = "win-1",
            url = "https://b.com",
            title = "Tab B",
            isActive = false,
            profileId = "personal"
        )

        dao.insert(tab1).await()
        dao.insert(tab2).await()

        val tabs = dao.getByWindow("win-1").await()
        assertThat(tabs.size, equalTo(2))

        val activeTab = dao.getActiveTab("win-1").await()
        assertThat(activeTab!!.id, equalTo("tab-1"))

        // Switch active tab
        dao.setActiveTab("win-1", "tab-2").await()
        val newActive = dao.getActiveTab("win-1").await()
        assertThat(newActive!!.id, equalTo("tab-2"))
    }

    @Test
    fun testClosedTabDaoOperations() = runBlocking {
        val dao = database.closedTabDao()

        val entry1 = ClosedTabEntity(
            id = "closed-1",
            windowId = "win-1",
            tabId = "tab-1",
            url = "https://a.com",
            title = "Tab A",
            tier = 1, // Held view
            profileId = "personal"
        )
        val entry2 = ClosedTabEntity(
            id = "closed-2",
            windowId = "win-1",
            tabId = "tab-2",
            url = "https://b.com",
            title = "Tab B",
            tier = 2, // Snapshot
            profileId = "personal"
        )

        dao.insert(entry1).await()
        dao.insert(entry2).await()

        val recent = dao.getRecentByWindow("win-1", 10).await()
        assertThat(recent.size, equalTo(2))

        // Degrade held views
        dao.degradeHeldViews("win-1").await()

        val degraded = dao.getById("closed-1").await()
        assertThat(degraded!!.tier, equalTo(2))

        val stillSnapshot = dao.getById("closed-2").await()
        assertThat(stillSnapshot!!.tier, equalTo(2))
    }

    @Test
    fun testDownloadDaoOperations() = runBlocking {
        val dao = database.downloadDao()

        val download = DownloadEntity(
            id = "dl-1",
            windowId = "win-1",
            tabId = "tab-1",
            url = "https://example.com/file.zip",
            fileName = "file.zip",
            mimeType = "application/zip",
            totalBytes = 1000,
            state = 0, // Pending
            profileId = "personal"
        )
        dao.insert(download).await()

        // Update progress
        dao.updateProgress("dl-1", 500, 1000).await()
        dao.updateState("dl-1", 1, null, 0).await()

        var retrieved = dao.getById("dl-1").await()
        assertThat(retrieved!!.state, equalTo(1))
        assertThat(retrieved.receivedBytes, equalTo(500L))

        // Complete
        dao.updateProgress("dl-1", 1000, 1000).await()
        dao.updateState("dl-1", 2, null, System.currentTimeMillis()).await()

        retrieved = dao.getById("dl-1").await()
        assertThat(retrieved!!.state, equalTo(2))
        assertThat(retrieved.completedAt, greaterThan(0L))

        // Clear completed
        dao.clearCompleted("win-1").await()
        val remaining = dao.getByWindow("win-1").await()
        assertThat(remaining.size, equalTo(0))
    }

    @Test
    fun testProfileDaoOperations() = runBlocking {
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

        // Workspaces
        val ws = WorkspaceEntity(
            id = "ws-1",
            profileId = "personal",
            name = "Work"
        )
        dao.insertWorkspace(ws).await()

        val workspaces = dao.getWorkspacesByProfile("personal").await()
        assertThat(workspaces.size, equalTo(1))
        assertThat(workspaces[0].name, equalTo("Work"))
    }

    @Test
    fun testSettingsDaoOperations() = runBlocking {
        val dao = database.settingsDao()

        val setting = SettingEntity(
            key = "test.key",
            value = "\"test value\"",
            profileId = "personal"
        )
        dao.insert(setting).await()

        val retrieved = dao.getByKey("test.key", "personal").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.value, equalTo("\"test value\""))

        // Search engines
        val engine = SearchEngineEntity(
            id = "custom",
            name = "Custom",
            searchUrl = "https://custom.com/search?q=%s",
            isDefault = false,
            profileId = "personal"
        )
        dao.insertSearchEngine(engine).await()

        val engines = dao.getSearchEngines("personal").await()
        assertThat(engines.size, greaterThanOrEqualTo(1))

        val customEngine = dao.getSearchEngineById("custom", "personal").await()
        assertThat(customEngine, notNullValue())
        assertThat(customEngine!!.name, equalTo("Custom"))
    }

    @Test
    fun testAdblockExceptions() = runBlocking {
        val dao = database.profileDao()

        val exception = AdblockExceptionEntity(
            hostname = "example.com",
            profileId = "personal"
        )
        dao.insertAdblockException(exception).await()

        val isException = dao.isException("example.com", "personal").await()
        assertThat(isException, equalTo(1))

        val exceptions = dao.getAdblockExceptions("personal").await()
        assertThat(exceptions.size, equalTo(1))
        assertThat(exceptions[0].hostname, equalTo("example.com"))

        dao.deleteAdblockException("example.com", "personal").await()
        val afterDelete = dao.isException("example.com", "personal").await()
        assertThat(afterDelete, equalTo(0))
    }

    @Test
    fun testTransaction() = runBlocking {
        val dao = database.tabDao()

        // Test transactional session state
        val window = WindowEntity(
            id = "win-tx",
            label = "Transactional Window",
            profileId = "personal"
        )
        val tab1 = TabEntity(
            id = "tab-tx-1",
            windowId = "win-tx",
            url = "https://a.com",
            profileId = "personal"
        )
        val tab2 = TabEntity(
            id = "tab-tx-2",
            windowId = "win-tx",
            url = "https://b.com",
            profileId = "personal"
        )

        database.runInTransaction {
            dao.insertWindow(window)
            dao.insert(tab1)
            dao.insert(tab2)
        }

        val tabs = dao.getByWindow("win-tx").await()
        assertThat(tabs.size, equalTo(2))
    }

    @Test
    fun testConverters() = runBlocking {
        // Test that type converters work correctly
        val dao = database.tabDao()

        val tab = TabEntity(
            id = "tab-conv",
            windowId = "win-1",
            url = "https://example.com",
            navigationHistory = "[\"https://a.com\",\"https://b.com\",\"https://c.com\"]",
            historyIndex = 1,
            profileId = "personal"
        )
        dao.insert(tab).await()

        val retrieved = dao.getById("tab-conv").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.navigationHistory, equalTo("[\"https://a.com\",\"https://b.com\",\"https://c.com\"]"))
        assertThat(retrieved.historyIndex, equalTo(1))
    }
}