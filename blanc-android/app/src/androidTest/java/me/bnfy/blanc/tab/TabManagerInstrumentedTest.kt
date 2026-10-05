package me.bnfy.blanc.tab

import android.app.Activity
import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for TabManager tab lifecycle.
 * Tests tab creation, switching, closing, and state management.
 */
@RunWith(AndroidJUnit4::class)
class TabManagerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var activity: Activity
    private lateinit var mockBridge: BlancBridge
    private var tabManager: TabManager? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Launch a test activity
        val scenario = ActivityScenario.launch(TestActivity::class.java)
        scenario.onActivity { activity = it }

        mockBridge = object : BlancBridge {
            override fun onTabCreated(tab: Tab) {}
            override fun onTabUpdated(tab: Tab) {}
            override fun onTabClosed(tabId: String) {}
            override fun onTabSwitched(tabId: String) {}
            override fun onNavigation(tabId: String, url: String, title: String?) {}
            override fun onProgress(tabId: String, progress: Int) {}
            override fun onCreateWindowRequested(url: String, isPrivate: Boolean): Tab? = null
        }

        tabManager = TabManager(context, mockBridge, WebViewFactory.createRegular(context))
        tabManager?.setActivity(activity)
    }

    @Test
    fun testCreateTab() {
        val tab = tabManager?.createTab("https://example.com")

        assertNotNull(tab)
        assertNotNull(tab?.webView)
        assertEquals("https://example.com", tab?.url)
        assertFalse(tab?.isPrivate ?: true)
        assertEquals(1, tabManager?.getTabCount() ?: 0)
        assertEquals(tab?.id, tabManager?.getActiveTabId())
    }

    @Test
    fun testCreatePrivateTab() {
        val tab = tabManager?.createPrivateTab("https://private.com")

        assertNotNull(tab)
        assertNotNull(tab?.webView)
        assertEquals("https://private.com", tab?.url)
        assertTrue(tab?.isPrivate ?: false)
        assertEquals(1, tabManager?.getTabCount() ?: 0)
    }

    @Test
    fun testMultipleTabs() {
        val tab1 = tabManager?.createTab("https://a.com")
        val tab2 = tabManager?.createTab("https://b.com")
        val tab3 = tabManager?.createTab("https://c.com")

        assertEquals(3, tabManager?.getTabCount() ?: 0)
        assertEquals(3, tabManager?.getAllTabs().size)
        assertEquals(listOf(tab1?.id, tab2?.id, tab3?.id), tabManager?.getAllTabIds())
    }

    @Test
    fun testSwitchTab() {
        val tab1 = tabManager?.createTab("https://a.com")
        val tab2 = tabManager?.createTab("https://b.com")

        // Initially tab1 is active
        assertEquals(tab1?.id, tabManager?.getActiveTabId())

        // Switch to tab2
        val result = tabManager?.switchTab(tab2?.id ?: "")
        assertTrue(result)
        assertEquals(tab2?.id, tabManager?.getActiveTabId())

        // Switch back to tab1
        tabManager?.switchTab(tab1?.id ?: "")
        assertEquals(tab1?.id, tabManager?.getActiveTabId())
    }

    @Test
    fun testCloseTab() {
        val tab1 = tabManager?.createTab("https://a.com")
        val tab2 = tabManager?.createTab("https://b.com")

        assertEquals(2, tabManager?.getTabCount() ?: 0)

        // Close tab1
        val result = tabManager?.closeTab(tab1?.id ?: "")
        assertTrue(result)
        assertEquals(1, tabManager?.getTabCount() ?: 0)
        assertNull(tabManager?.getTab(tab1?.id ?: ""))

        // tab2 should now be active
        assertEquals(tab2?.id, tabManager?.getActiveTabId())
    }

    @Test
    fun testCloseActiveTabSelectsAdjacent() {
        val tab1 = tabManager?.createTab("https://a.com")
        val tab2 = tabManager?.createTab("https://b.com")
        val tab3 = tabManager?.createTab("https://c.com")

        // tab3 is active
        assertEquals(tab3?.id, tabManager?.getActiveTabId())

        // Close active tab (tab3)
        tabManager?.closeActiveTab()

        // tab2 should now be active
        assertEquals(tab2?.id, tabManager?.getActiveTabId())
        assertEquals(2, tabManager?.getTabCount() ?: 0)
    }

    @Test
    fun testCloseLastTab() {
        val tab = tabManager?.createTab("https://only.com")

        tabManager?.closeTab(tab?.id ?: "")

        assertEquals(0, tabManager?.getTabCount() ?: 0)
        assertNull(tabManager?.getActiveTabId())
        assertNull(tabManager?.getActiveTab())
    }

    @Test
    fun testCloseAllTabs() {
        tabManager?.createTab("https://a.com")
        tabManager?.createTab("https://b.com")
        tabManager?.createTab("https://c.com")

        assertEquals(3, tabManager?.getTabCount() ?: 0)

        tabManager?.closeAllTabs()

        assertEquals(0, tabManager?.getTabCount() ?: 0)
        assertTrue(tabManager?.getAllTabs().isEmpty() ?: false)
        assertNull(tabManager?.getActiveTabId())
    }

    @Test
    fun testGetTab() {
        val tab = tabManager?.createTab("https://example.com")

        val retrieved = tabManager?.getTab(tab?.id ?: "")
        assertNotNull(retrieved)
        assertEquals(tab?.id, retrieved?.id)

        val notFound = tabManager?.getTab("non-existent")
        assertNull(notFound)
    }

    @Test
    fun testMoveTab() {
        val tab1 = tabManager?.createTab("https://a.com")
        val tab2 = tabManager?.createTab("https://b.com")
        val tab3 = tabManager?.createTab("https://c.com")

        // Initial order: tab1, tab2, tab3
        assertEquals(listOf(tab1?.id, tab2?.id, tab3?.id), tabManager?.getAllTabIds())

        // Move tab1 to end
        tabManager?.moveTab(tab1?.id ?: "", 2)
        assertEquals(listOf(tab2?.id, tab3?.id, tab1?.id), tabManager?.getAllTabIds())

        // Move tab3 to front
        tabManager?.moveTab(tab3?.id ?: "", 0)
        assertEquals(listOf(tab3?.id, tab2?.id, tab1?.id), tabManager?.getAllTabIds())
    }

    @Test
    fun testNavigateTo() {
        val tab = tabManager?.createTab("https://a.com")

        val result = tabManager?.navigateTo(tab?.id ?: "", "https://b.com")
        assertTrue(result)

        // Note: Actual navigation is async, so we can't verify URL immediately
        // without waiting for onPageFinished
    }

    @Test
    fun testReloadStopGoBackForward() {
        val tab = tabManager?.createTab("https://a.com")
        tabManager?.navigateTo(tab?.id ?: "", "https://b.com")

        // These methods exist and don't crash
        tabManager?.reloadActiveTab()
        tabManager?.stopActiveTab()
        tabManager?.goBack()
        tabManager?.goForward()

        assertFalse(tabManager?.canGoBack() ?: true)
        assertFalse(tabManager?.canGoForward() ?: true)
    }

    @Test
    fun testPrivateTabFiltering() {
        tabManager?.createTab("https://a.com")
        tabManager?.createPrivateTab("https://private1.com")
        tabManager?.createTab("https://b.com")
        tabManager?.createPrivateTab("https://private2.com")

        assertEquals(4, tabManager?.getTabCount() ?: 0)
        assertEquals(2, tabManager?.getPrivateTabs().size)
        assertEquals(2, tabManager?.getRegularTabs().size)
    }

    @Test
    fun testSaveRestoreSession() {
        val tab1 = tabManager?.createTab("https://a.com")
        val tab2 = tabManager?.createPrivateTab("https://private.com")

        // Wait for tabs to load
        Thread.sleep(500)

        val savedStates = tabManager?.saveSession()
        assertNotNull(savedStates)
        assertEquals(2, savedStates?.size)

        // Restore
        tabManager?.closeAllTabs()
        assertEquals(0, tabManager?.getTabCount() ?: 0)

        tabManager?.restoreSession(savedStates!!)

        // Wait for restoration
        Thread.sleep(500)

        assertEquals(2, tabManager?.getTabCount() ?: 0)
        val restoredTab1 = tabManager?.getTab(tab1?.id ?: "")
        val restoredTab2 = tabManager?.getTab(tab2?.id ?: "")

        assertNotNull(restoredTab1)
        assertNotNull(restoredTab2)
        assertEquals("https://a.com", restoredTab1?.url)
        assertEquals("https://private.com", restoredTab2?.url)
        assertFalse(restoredTab1?.isPrivate ?: true)
        assertTrue(restoredTab2?.isPrivate ?: false)
    }

    @Test
    fun testDestroy() {
        tabManager?.createTab("https://a.com")
        tabManager?.createTab("https://b.com")

        tabManager?.destroy()

        assertEquals(0, tabManager?.getTabCount() ?: 0)
        assertNull(tabManager?.getActiveTabId())
    }

    // Test Activity for instrumented tests
    class TestActivity : Activity() {
        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(WebView(this))
        }
    }
}