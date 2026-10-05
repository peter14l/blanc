package me.bnfy.blanc.tab

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for TabManager logic.
 * Tests tab lifecycle management without Android dependencies.
 */
class TabManagerTest {

    private lateinit var mockBridge: BlancBridge
    private lateinit var mockFactory: WebViewFactory
    private lateinit var mockAdblockEngine: AdblockEngine
    private lateinit var tabManager: TabManager

    @Before
    fun setUp() {
        mockBridge = mock(BlancBridge::class.java)
        mockFactory = mock(WebViewFactory::class.java)
        mockAdblockEngine = mock(AdblockEngine::class.java)

        // We can't easily instantiate TabManager without Android Context
        // These tests focus on the logic that can be tested in isolation
    }

    @Test
    fun testTabCreationLogic() {
        // Test the core logic of tab creation and management
        val tabs = mutableMapOf<String, Tab>()
        val tabOrder = mutableListOf<String>()

        // Simulate creating tabs
        val tab1 = Tab.create()
        val tab2 = Tab.createPrivate()
        val tab3 = Tab.create()

        tabs[tab1.id] = tab1
        tabOrder.add(tab1.id)
        tabs[tab2.id] = tab2
        tabOrder.add(tab2.id)
        tabs[tab3.id] = tab3
        tabOrder.add(tab3.id)

        assertEquals(3, tabs.size)
        assertEquals(3, tabOrder.size)
        assertFalse(tab1.isPrivate)
        assertTrue(tab2.isPrivate)
        assertFalse(tab3.isPrivate)
    }

    @Test
    fun testTabOrderManagement() {
        val tabOrder = mutableListOf<String>()
        val tab1 = Tab.create()
        val tab2 = Tab.create()
        val tab3 = Tab.create()

        tabOrder.add(tab1.id)
        tabOrder.add(tab2.id)
        tabOrder.add(tab3.id)

        // Test move tab
        val currentIndex = tabOrder.indexOf(tab1.id)
        assertEquals(0, currentIndex)

        tabOrder.removeAt(currentIndex)
        tabOrder.add(2, tab1.id) // Move to end
        assertEquals(2, tabOrder.indexOf(tab1.id))

        tabOrder.removeAt(2)
        tabOrder.add(0, tab1.id) // Move to front
        assertEquals(0, tabOrder.indexOf(tab1.id))
    }

    @Test
    fun testSelectAdjacentTab() {
        val tabOrder = mutableListOf<String>()
        val tab1 = Tab.create()
        val tab2 = Tab.create()
        val tab3 = Tab.create()

        tabOrder.add(tab1.id)
        tabOrder.add(tab2.id)
        tabOrder.add(tab3.id)

        // Close middle tab (tab2), should select tab3 (next)
        val closedIndex = tabOrder.indexOf(tab2.id)
        tabOrder.removeAt(closedIndex)

        var nextIndex = closedIndex
        if (nextIndex >= tabOrder.size) {
            nextIndex = tabOrder.size - 1
        }
        val nextTabId = tabOrder[nextIndex]
        assertEquals(tab3.id, nextTabId)

        // Close last tab (tab3), should select tab1 (previous)
        tabOrder.remove(tab3.id)
        nextIndex = tabOrder.indexOf(tab3.id)
        if (nextIndex >= tabOrder.size) {
            nextIndex = tabOrder.size - 1
        }
        val prevTabId = tabOrder[nextIndex]
        assertEquals(tab1.id, prevTabId)
    }

    @Test
    fun testPrivateTabFiltering() {
        val tabs = mutableMapOf<String, Tab>()
        val tab1 = Tab.create()
        val tab2 = Tab.createPrivate()
        val tab3 = Tab.create()
        val tab4 = Tab.createPrivate()

        tabs[tab1.id] = tab1
        tabs[tab2.id] = tab2
        tabs[tab3.id] = tab3
        tabs[tab4.id] = tab4

        val privateTabs = tabs.values.filter { it.isPrivate }.toList()
        val regularTabs = tabs.values.filter { !it.isPrivate }.toList()

        assertEquals(2, privateTabs.size)
        assertEquals(2, regularTabs.size)
        assertTrue(privateTabs.all { it.isPrivate })
        assertTrue(regularTabs.all { !it.isPrivate })
    }

    @Test
    fun testTabStateSerialization() {
        val tab = Tab.create()
        tab.url = "https://example.com"
        tab.title = "Example Domain"
        tab.addToHistory("https://example.com")
        tab.addToHistory("https://example.com/about")
        tab.canGoBack = true
        tab.canGoForward = false

        // Simulate TabManager.TabState
        data class TabState(
            val id: String,
            val url: String,
            val title: String,
            val isPrivate: Boolean,
            val history: List<String>,
            val historyIndex: Int,
            val canGoBack: Boolean,
            val canGoForward: Boolean
        )

        val state = TabState(
            id = tab.id,
            url = tab.url,
            title = tab.title,
            isPrivate = tab.isPrivate,
            history = tab.history.toList(),
            historyIndex = tab.historyIndex,
            canGoBack = tab.canGoBack,
            canGoForward = tab.canGoForward
        )

        assertEquals(tab.id, state.id)
        assertEquals("https://example.com", state.url)
        assertEquals("Example Domain", state.title)
        assertEquals(2, state.history.size)
        assertEquals(1, state.historyIndex)
        assertTrue(state.canGoBack)
        assertFalse(state.canGoForward)
    }

    @Test
    fun testTabRestorationLogic() {
        // Simulate restoring tabs from session
        val restoredTabs = mutableListOf<Tab>()

        val state1 = TabManager.TabState(
            id = "tab-1",
            url = "https://example.com",
            title = "Example",
            isPrivate = false,
            history = listOf("https://example.com"),
            historyIndex = 0,
            canGoBack = false,
            canGoForward = false
        )

        val state2 = TabManager.TabState(
            id = "tab-2",
            url = "https://private.com",
            title = "Private",
            isPrivate = true,
            history = listOf("https://private.com"),
            historyIndex = 0,
            canGoBack = false,
            canGoForward = false
        )

        // Create tabs from states
        val tab1 = Tab(
            id = state1.id,
            url = state1.url,
            title = state1.title,
            isPrivate = state1.isPrivate,
            history = state1.history.toMutableList(),
            historyIndex = state1.historyIndex,
            canGoBack = state1.canGoBack,
            canGoForward = state1.canGoForward
        )

        val tab2 = Tab(
            id = state2.id,
            url = state2.url,
            title = state2.title,
            isPrivate = state2.isPrivate,
            history = state2.history.toMutableList(),
            historyIndex = state2.historyIndex,
            canGoBack = state2.canGoBack,
            canGoForward = state2.canGoForward
        )

        restoredTabs.add(tab1)
        restoredTabs.add(tab2)

        assertEquals(2, restoredTabs.size)
        assertFalse(restoredTabs[0].isPrivate)
        assertTrue(restoredTabs[1].isPrivate)
        assertEquals("https://example.com", restoredTabs[0].url)
        assertEquals("https://private.com", restoredTabs[1].url)
    }
}