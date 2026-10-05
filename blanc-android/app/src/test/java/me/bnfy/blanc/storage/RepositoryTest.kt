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
class RepositoryTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: Repository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        repository = Repository.createForTesting(context)
    }

    @After
    fun teardown() {
        database.close()
        Repository.clearInstance()
    }

    @Test
    fun testInsertAndGetHistory() = runBlocking {
        val profileId = "personal"
        val entry = HistoryEntry(
            url = "https://example.com",
            title = "Example Domain",
            visitTime = System.currentTimeMillis(),
            favicon = "https://example.com/favicon.ico",
            isPrivate = false,
            profileId = profileId
        )

        val id = repository.historyDao.insert(entry).await()
        assertThat(id, greaterThan(0L))

        val retrieved = repository.historyDao.getById(id).await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.url, equalTo("https://example.com"))
        assertThat(retrieved.title, equalTo("Example Domain"))
        assertThat(retrieved.profileId, equalTo(profileId))
    }

    @Test
    fun testHistoryPagination() = runBlocking {
        val profileId = "personal"
        val now = System.currentTimeMillis()

        // Insert multiple entries
        for (i in 1..15) {
            repository.historyDao.insert(HistoryEntry(
                url = "https://example.com/page$i",
                title = "Page $i",
                visitTime = now - i * 1000L,
                profileId = profileId
            )).await()
        }

        // Get first page
        val page1 = repository.historyDao.getHistoryPage(profileId, 10, 0, null).await()
        assertThat(page1.size, equalTo(10))
        assertThat(page1[0].url, equalTo("https://example.com/page1"))

        // Get second page
        val page2 = repository.historyDao.getHistoryPage(profileId, 10, 10, null).await()
        assertThat(page2.size, equalTo(5))
    }

    @Test
    fun testHistorySearch() = runBlocking {
        val profileId = "personal"

        repository.historyDao.insert(HistoryEntry(
            url = "https://github.com/user/repo",
            title = "GitHub Repository",
            visitTime = System.currentTimeMillis(),
            profileId = profileId
        )).await()

        repository.historyDao.insert(HistoryEntry(
            url = "https://gitlab.com/user/project",
            title = "GitLab Project",
            visitTime = System.currentTimeMillis(),
            profileId = profileId
        )).await()

        val results = repository.historyDao.getHistoryPage(profileId, 10, 0, "github").await()
        assertThat(results.size, equalTo(1))
        assertThat(results[0].url, containsString("github"))
    }

    @Test
    fun testInsertAndGetBookmark() = runBlocking {
        val profileId = "personal"
        val bookmark = Bookmark(
            id = "bm-1",
            url = "https://example.com",
            title = "Example",
            favicon = "https://example.com/favicon.ico",
            profileId = profileId
        )

        repository.bookmarkDao.insert(bookmark).await()

        val retrieved = repository.bookmarkDao.getById("bm-1").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.url, equalTo("https://example.com"))
        assertThat(retrieved.title, equalTo("Example"))
    }

    @Test
    fun testBookmarkFolderOperations() = runBlocking {
        val profileId = "personal"

        // Insert root bookmark
        val root = Bookmark(id = "bm-root", url = "https://example.com", title = "Root", profileId = profileId)
        repository.bookmarkDao.insert(root).await()

        // Insert folder bookmark
        val folder = Bookmark(id = "bm-folder", url = "folder://bookmarks", title = "My Folder", profileId = profileId)
        repository.bookmarkDao.insert(folder).await()

        // Insert bookmark in folder
        val child = Bookmark(
            id = "bm-child",
            url = "https://child.com",
            title = "Child",
            folderId = "bm-folder",
            profileId = profileId
        )
        repository.bookmarkDao.insert(child).await()

        val folderContents = repository.bookmarkDao.getByFolder(profileId, "bm-folder").await()
        assertThat(folderContents.size, equalTo(1))
        assertThat(folderContents[0].id, equalTo("bm-child"))
    }

    @Test
    fun testInsertAndGetFavorite() = runBlocking {
        val profileId = "personal"
        val favorite = Favorite(
            id = "fav-1",
            url = "https://example.com",
            title = "Example",
            favicon = "https://example.com/favicon.ico",
            isPinned = true,
            profileId = profileId
        )

        repository.favoriteDao.insert(favorite).await()

        val retrieved = repository.favoriteDao.getById("fav-1").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.isPinned, equalTo(true))
    }

    @Test
    fun testPinnedFavoritesOrder() = runBlocking {
        val profileId = "personal"

        val unpinned = Favorite(id = "fav-1", url = "https://a.com", title = "A", isPinned = false, position = 0, profileId = profileId)
        val pinned = Favorite(id = "fav-2", url = "https://b.com", title = "B", isPinned = true, position = 0, profileId = profileId)
        val anotherPinned = Favorite(id = "fav-3", url = "https://c.com", title = "C", isPinned = true, position = 1, profileId = profileId)

        repository.favoriteDao.insert(unpinned).await()
        repository.favoriteDao.insert(pinned).await()
        repository.favoriteDao.insert(anotherPinned).await()

        val all = repository.favoriteDao.getAll(profileId).await()
        // Pinned first, then by position
        assertThat(all[0].id, equalTo("fav-2"))
        assertThat(all[1].id, equalTo("fav-3"))
        assertThat(all[2].id, equalTo("fav-1"))
    }

    @Test
    fun testTabEntityOperations() = runBlocking {
        val tab = TabEntity(
            id = "tab-1",
            windowId = "window-1",
            url = "https://example.com",
            title = "Example",
            isPrivate = false,
            isPinned = true,
            profileId = "personal"
        )

        repository.tabDao.insert(tab).await()

        val retrieved = repository.tabDao.getById("tab-1").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.isPinned, equalTo(true))
        assertThat(retrieved.windowId, equalTo("window-1"))
    }

    @Test
    fun testClosedTabTierDegradation() = runBlocking {
        val entry = ClosedTabEntity(
            id = "closed-1",
            windowId = "window-1",
            tabId = "tab-1",
            url = "https://example.com",
            title = "Example",
            tier = 1, // Held view
            profileId = "personal"
        )

        repository.closedTabDao.insert(entry).await()

        // Degrade to snapshot
        repository.closedTabDao.degradeHeldViews("window-1").await()

        val degraded = repository.closedTabDao.getById("closed-1").await()
        assertThat(degraded!!.tier, equalTo(2))
    }

    @Test
    fun testDownloadStateTransitions() = runBlocking {
        val download = DownloadEntity(
            id = "dl-1",
            windowId = "window-1",
            tabId = "tab-1",
            url = "https://example.com/file.zip",
            fileName = "file.zip",
            mimeType = "application/zip",
            totalBytes = 1000,
            state = 0, // Pending
            profileId = "personal"
        )

        repository.downloadDao.insert(download).await()

        // Update to in progress
        repository.downloadDao.updateProgress("dl-1", 500, 1000).await()
        repository.downloadDao.updateState("dl-1", 1, null, 0).await() // InProgress

        var retrieved = repository.downloadDao.getById("dl-1").await()
        assertThat(retrieved!!.state, equalTo(1))
        assertThat(retrieved.receivedBytes, equalTo(500L))

        // Complete
        repository.downloadDao.updateProgress("dl-1", 1000, 1000).await()
        repository.downloadDao.updateState("dl-1", 2, null, System.currentTimeMillis()).await() // Completed

        retrieved = repository.downloadDao.getById("dl-1").await()
        assertThat(retrieved!!.state, equalTo(2))
        assertThat(retrieved.completedAt, greaterThan(0L))
    }

    @Test
    fun testProfileAndWorkspace() = runBlocking {
        val profile = ProfileEntity(
            id = "profile-1",
            name = "Work",
            isPersonal = false
        )
        repository.profileDao.insert(profile).await()

        val workspace = WorkspaceEntity(
            id = "ws-1",
            profileId = "profile-1",
            name = "Development",
            windowIds = "[\"window-1\", \"window-2\"]"
        )
        repository.profileDao.insertWorkspace(workspace).await()

        val retrievedProfile = repository.profileDao.getById("profile-1").await()
        assertThat(retrievedProfile!!.name, equalTo("Work"))

        val workspaces = repository.profileDao.getWorkspacesByProfile("profile-1").await()
        assertThat(workspaces.size, equalTo(1))
        assertThat(workspaces[0].name, equalTo("Development"))
    }

    @Test
    fun testPermissionDecisions() = runBlocking {
        val decision = PermissionDecisionEntity(
            origin = "https://example.com",
            resource = "camera",
            decision = 1, // Allow
            isRemembered = true,
            profileId = "personal"
        )

        repository.profileDao.insertPermission(decision).await()

        val retrieved = repository.profileDao.getPermission("https://example.com", "camera", "personal").await()
        assertThat(retrieved, notNullValue())
        assertThat(retrieved!!.decision, equalTo(1))
        assertThat(retrieved.isRemembered, equalTo(true))
    }

    @Test
    fun testAdblockExceptions() = runBlocking {
        val exception = AdblockExceptionEntity(
            hostname = "example.com",
            profileId = "personal"
        )

        repository.profileDao.insertAdblockException(exception).await()

        val isException = repository.profileDao.isException("example.com", "personal").await()
        assertThat(isException, equalTo(1))

        val exceptions = repository.profileDao.getAdblockExceptions("personal").await()
        assertThat(exceptions.size, equalTo(1))
        assertThat(exceptions[0].hostname, equalTo("example.com"))

        repository.profileDao.deleteAdblockException("example.com", "personal").await()
        val isExceptionAfterDelete = repository.profileDao.isException("example.com", "personal").await()
        assertThat(isExceptionAfterDelete, equalTo(0))
    }

    @Test
    fun testSettingsDataStore() = runBlocking {
        // Test settings save/get
        val settings = Repository.UserSettings(
            adblockEnabled = false,
            theme = "dark",
            defaultSearchEngine = "brave"
        )

        repository.saveSettings(settings).await()

        val retrieved = repository.getSettings().await()
        assertThat(retrieved.adblockEnabled, equalTo(false))
        assertThat(retrieved.theme, equalTo("dark"))
        assertThat(retrieved.defaultSearchEngine, equalTo("brave"))
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