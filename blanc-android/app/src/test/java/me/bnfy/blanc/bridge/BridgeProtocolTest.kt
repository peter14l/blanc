package me.bnfy.blanc.bridge

import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test

@ExperimentalCoroutinesApi
class BridgeProtocolTest {

    private val gson = Gson()

    @Test
    fun testCommandSerialization() {
        val command = BridgeProtocol.Command(
            command = "create_tab",
            args = mapOf("url" to "https://example.com", "private" to false),
            id = "cmd-123"
        )

        val json = gson.toJson(command)
        assertThat(json, containsString("create_tab"))
        assertThat(json, containsString("https://example.com"))
        assertThat(json, containsString("cmd-123"))

        val parsed = gson.fromJson<BridgeProtocol.Command>(json, BridgeProtocol.Command::class.java)
        assertThat(parsed.command, equalTo("create_tab"))
        assertThat(parsed.args!!["url"], equalTo("https://example.com"))
        assertThat(parsed.id, equalTo("cmd-123"))
    }

    @Test
    fun testEventSerialization() {
        val event = BridgeProtocol.Event(
            event = "blanc:state-updated",
            data = mapOf("windows" to listOf())
        )

        val json = gson.toJson(event)
        assertThat(json, containsString("blanc:state-updated"))

        val parsed = gson.fromJson<BridgeProtocol.Event>(json, BridgeProtocol.Event::class.java)
        assertThat(parsed.event, equalTo("blanc:state-updated"))
    }

    @Test
    fun testResponseSerialization() {
        val response = BridgeProtocol.Response(
            id = "cmd-123",
            result = mapOf("tabId" to "tab-456")
        )

        val json = gson.toJson(response)
        assertThat(json, containsString("cmd-123"))
        assertThat(json, containsString("tab-456"))

        val parsed = gson.fromJson<BridgeProtocol.Response>(json, BridgeProtocol.Response::class.java)
        assertThat(parsed.id, equalTo("cmd-123"))
        assertThat(parsed.result, notNullValue())
    }

    @Test
    fun testErrorResponseSerialization() {
        val response = BridgeProtocol.Response(
            id = "cmd-123",
            error = BridgeProtocol.ErrorInfo("TAB_NOT_FOUND", "Tab not found: tab-999")
        )

        val json = gson.toJson(response)
        assertThat(json, containsString("TAB_NOT_FOUND"))
        assertThat(json, containsString("Tab not found"))

        val parsed = gson.fromJson<BridgeProtocol.Response>(json, BridgeProtocol.Response::class.java)
        assertThat(parsed.error!!.code, equalTo("TAB_NOT_FOUND"))
        assertThat(parsed.error!!.message, containsString("Tab not found"))
    }

    @Test
    fun testStateProjectionSerialization() {
        val projection = BridgeProtocol.StateProjection(
            windows = listOf(
                BridgeProtocol.WindowProjection(
                    id = "win-1",
                    label = "Window 1",
                    profileId = "personal",
                    activeTabId = "tab-1",
                    tabIds = listOf("tab-1", "tab-2"),
                    groups = listOf(),
                    overlay = null,
                    closedTabs = listOf(),
                    workspaceId = null,
                    permissionPromptOpen = false
                )
            ),
            focusedWindowId = "win-1",
            adblockEnabled = true,
            blockingReady = true,
            totalBlocked = 1234,
            blockingError = null
        )

        val json = gson.toJson(projection)
        assertThat(json, containsString("win-1"))
        assertThat(json, containsString("1234"))

        val parsed = gson.fromJson<BridgeProtocol.StateProjection>(json, BridgeProtocol.StateProjection::class.java)
        assertThat(parsed.windows.size, equalTo(1))
        assertThat(parsed.totalBlocked, equalTo(1234L))
    }

    @Test
    fun testTabRecordSerialization() {
        val tab = BridgeProtocol.TabRecord(
            id = "tab-1",
            windowId = "win-1",
            url = "https://example.com",
            title = "Example",
            favicon = "https://example.com/favicon.ico",
            isPrivate = false,
            isPinned = true,
            isMuted = false,
            groupId = null,
            position = 0,
            canGoBack = true,
            canGoForward = false,
            isLoading = false,
            progress = 100,
            blockedCount = 5,
            isActive = true
        )

        val json = gson.toJson(tab)
        assertThat(json, containsString("tab-1"))
        assertThat(json, containsString("https://example.com"))
        assertThat(json, containsString("isPinned\":true"))

        val parsed = gson.fromJson<BridgeProtocol.TabRecord>(json, BridgeProtocol.TabRecord::class.java)
        assertThat(parsed.id, equalTo("tab-1"))
        assertThat(parsed.isPinned, equalTo(true))
        assertThat(parsed.blockedCount, equalTo(5))
    }

    @Test
    fun testHistoryPageSerialization() {
        val page = BridgeProtocol.HistoryPage(
            entries = listOf(
                BridgeProtocol.HistoryEntry(
                    id = 1,
                    url = "https://example.com",
                    title = "Example",
                    visitTime = System.currentTimeMillis(),
                    favicon = null,
                    isPrivate = false
                )
            ),
            total = 1
        )

        val json = gson.toJson(page)
        assertThat(json, containsString("total\":1"))

        val parsed = gson.fromJson<BridgeProtocol.HistoryPage>(json, BridgeProtocol.HistoryPage::class.java)
        assertThat(parsed.total, equalTo(1))
        assertThat(parsed.entries.size, equalTo(1))
    }

    @Test
    fun testFavoriteSerialization() {
        val favorite = BridgeProtocol.Favorite(
            id = "fav-1",
            url = "https://example.com",
            title = "Example",
            favicon = "https://example.com/favicon.ico",
            folderId = null,
            position = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isPinned = true
        )

        val json = gson.toJson(favorite)
        assertThat(json, containsString("fav-1"))
        assertThat(json, containsString("isPinned\":true"))

        val parsed = gson.fromJson<BridgeProtocol.Favorite>(json, BridgeProtocol.Favorite::class.java)
        assertThat(parsed.isPinned, equalTo(true))
    }

    @Test
    fun testUserSettingsSerialization() {
        val settings = BridgeProtocol.UserSettings(
            adblockEnabled = true,
            adblockExceptions = listOf("example.com"),
            defaultSearchEngine = "duckduckgo",
            theme = "dark",
            homepage = "blanc://newtab",
            startupBehavior = "newtab",
            quietTabsDelay = "1h",
            blockThirdPartyCookies = true,
            doNotTrack = true,
            clearOnExit = false,
            sendUsageStats = true,
            showHomeButton = true,
            showBookmarksBar = false,
            tabPreview = true,
            smoothScrolling = true,
            activeProfileId = "personal",
            syncEnabled = false,
            syncPassphraseSet = false,
            appIcon = "sunrise",
            patronActive = false
        )

        val json = gson.toJson(settings)
        assertThat(json, containsString("duckduckgo"))
        assertThat(json, containsString("dark"))
        assertThat(json, containsString("sunrise"))

        val parsed = gson.fromJson<BridgeProtocol.UserSettings>(json, BridgeProtocol.UserSettings::class.java)
        assertThat(parsed.defaultSearchEngine, equalTo("duckduckgo"))
        assertThat(parsed.theme, equalTo("dark"))
        assertThat(parsed.adblockExceptions, contains("example.com"))
    }

    @Test
    fun testBlockingStatusSerialization() {
        val status = BridgeProtocol.BlockingStatus(
            enabled = true,
            ready = true,
            totalBlocked = 5000,
            error = null
        )

        val json = gson.toJson(status)
        assertThat(json, containsString("enabled\":true"))
        assertThat(json, containsString("5000"))

        val parsed = gson.fromJson<BridgeProtocol.BlockingStatus>(json, BridgeProtocol.BlockingStatus::class.java)
        assertThat(parsed.enabled, equalTo(true))
        assertThat(parsed.totalBlocked, equalTo(5000L))
    }

    @Test
    fun testPermissionDecisionRecordSerialization() {
        val record = BridgeProtocol.PermissionDecisionRecord(
            origin = "https://example.com",
            resource = "camera",
            decision = 1,
            remembered = true
        )

        val json = gson.toJson(record)
        assertThat(json, containsString("camera"))
        assertThat(json, containsString("decision\":1"))

        val parsed = gson.fromJson<BridgeProtocol.PermissionDecisionRecord>(json, BridgeProtocol.PermissionDecisionRecord::class.java)
        assertThat(parsed.resource, equalTo("camera"))
        assertThat(parsed.decision, equalTo(1))
    }

    @Test
    fun testDownloadRecordSerialization() {
        val download = BridgeProtocol.DownloadRecord(
            id = "dl-1",
            windowId = "win-1",
            tabId = "tab-1",
            url = "https://example.com/file.zip",
            fileName = "file.zip",
            mimeType = "application/zip",
            totalBytes = 1000,
            receivedBytes = 500,
            targetPath = "/sdcard/Download/file.zip",
            state = 1, // InProgress
            error = null,
            startedAt = System.currentTimeMillis(),
            completedAt = 0
        )

        val json = gson.toJson(download)
        assertThat(json, containsString("InProgress"))
        assertThat(json, containsString("500"))

        val parsed = gson.fromJson<BridgeProtocol.DownloadRecord>(json, BridgeProtocol.DownloadRecord::class.java)
        assertThat(parsed.state, equalTo(1))
        assertThat(parsed.receivedBytes, equalTo(500L))
    }

    @Test
    fun testTabEventSerialization() {
        val loadingEvent = BridgeProtocol.TabEvent.Loading("tab-1", 1)
        val navigatedEvent = BridgeProtocol.TabEvent.Navigated("tab-1", "https://example.com", "Example", 2)
        val captureEvent = BridgeProtocol.TabEvent.CaptureChanged("tab-1", true)

        val loadingJson = gson.toJson(loadingEvent)
        assertThat(loadingJson, containsString("Loading"))
        assertThat(loadingJson, containsString("tab-1"))

        val navigatedJson = gson.toJson(navigatedEvent)
        assertThat(navigatedJson, containsString("Navigated"))
        assertThat(navigatedJson, containsString("https://example.com"))

        val captureJson = gson.toJson(captureEvent)
        assertThat(captureJson, containsString("CaptureChanged"))
        assertThat(captureJson, containsString("capturing\":true"))
    }

    @Test
    fun testToastSerialization() {
        val toast = BridgeProtocol.Toast(kind = "success", message = "Tab closed")
        val json = gson.toJson(toast)
        assertThat(json, containsString("success"))
        assertThat(json, containsString("Tab closed"))

        val parsed = gson.fromJson<BridgeProtocol.Toast>(json, BridgeProtocol.Toast::class.java)
        assertThat(parsed.kind, equalTo("success"))
        assertThat(parsed.message, equalTo("Tab closed"))
    }

    @Test
    fun testCreateWindowRequestSerialization() {
        val request = BridgeProtocol.CreateWindowRequest(
            url = "https://example.com",
            private = true
        )

        val json = gson.toJson(request)
        assertThat(json, containsString("https://example.com"))
        assertThat(json, containsString("private\":true"))

        val parsed = gson.fromJson<BridgeProtocol.CreateWindowRequest>(json, BridgeProtocol.CreateWindowRequest::class.java)
        assertThat(parsed.url, equalTo("https://example.com"))
        assertThat(parsed.private, equalTo(true))
    }

    @Test
    fun testHelperMethods() {
        // Test createCommand
        val cmdJson = BridgeProtocol.createCommand("navigate", mapOf("tabId" to "tab-1", "url" to "https://example.com"), "cmd-1")
        assertThat(cmdJson, containsString("navigate"))
        assertThat(cmdJson, containsString("cmd-1"))

        // Test createEvent
        val eventJson = BridgeProtocol.createEvent("blanc:tab-event", mapOf("tabId" to "tab-1"))
        assertThat(eventJson, containsString("blanc:tab-event"))

        // Test createResponse
        val respJson = BridgeProtocol.createResponse("cmd-1", mapOf("success" to true))
        assertThat(respJson, containsString("cmd-1"))
        assertThat(respJson, containsString("success"))

        // Test createErrorResponse
        val errJson = BridgeProtocol.createErrorResponse("cmd-1", "ERROR", "Something went wrong")
        assertThat(errJson, containsString("ERROR"))
        assertThat(errJson, containsString("Something went wrong"))
    }

    @Test
    fun testRoundTripComplexObject() {
        // Test a complex nested object round-trip
        val projection = BridgeProtocol.StateProjection(
            windows = listOf(
                BridgeProtocol.WindowProjection(
                    id = "win-1",
                    label = "Main",
                    profileId = "personal",
                    activeTabId = "tab-1",
                    tabIds = listOf("tab-1", "tab-2"),
                    groups = listOf(
                        BridgeProtocol.GroupProjection(
                            id = "group-1",
                            name = "Work",
                            collapsed = false,
                            tabIds = listOf("tab-1")
                        )
                    ),
                    overlay = BridgeProtocol.OverlayProjection(
                        mode = "panel",
                        anchorRect = BridgeProtocol.Rect(100f, 200f, 400f, 300f)
                    ),
                    closedTabs = listOf(
                        BridgeProtocol.ClosedTabProjection(
                            id = "closed-1",
                            title = "Closed Tab",
                            favicon = null,
                            isGroup = false,
                            groupName = null,
                            tabCount = 1
                        )
                    ),
                    workspaceId = "ws-1",
                    permissionPromptOpen = false
                )
            ),
            focusedWindowId = "win-1",
            adblockEnabled = true,
            blockingReady = true,
            totalBlocked = 10000,
            blockingError = null
        )

        val json = gson.toJson(projection)
        val parsed = gson.fromJson<BridgeProtocol.StateProjection>(json, BridgeProtocol.StateProjection::class.java)

        assertThat(parsed.windows.size, equalTo(1))
        assertThat(parsed.windows[0].groups.size, equalTo(1))
        assertThat(parsed.windows[0].groups[0].name, equalTo("Work"))
        assertThat(parsed.windows[0].overlay?.mode, equalTo("panel"))
        assertThat(parsed.windows[0].closedTabs.size, equalTo(1))
    }
}