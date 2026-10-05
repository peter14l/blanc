package me.bnfy.blanc.tab

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for Tab data class logic.
 * Tests history management, navigation state, and tab operations.
 */
class TabTest {

    private lateinit var tab: Tab

    @Before
    fun setUp() {
        tab = Tab.create()
    }

    @Test
    fun testInitialState() {
        assertEquals("about:blank", tab.url)
        assertEquals("", tab.title)
        assertFalse(tab.canGoBack)
        assertFalse(tab.canGoForward)
        assertFalse(tab.isPrivate)
        assertFalse(tab.isLoading)
        assertEquals(0, tab.blockedCount)
        assertTrue(tab.history.isEmpty())
        assertEquals(-1, tab.historyIndex)
        assertEquals(0, tab.progress)
    }

    @Test
    fun testAddToHistory() {
        tab.addToHistory("https://example.com")
        assertEquals(1, tab.history.size)
        assertEquals(0, tab.historyIndex)
        assertEquals("https://example.com", tab.history[0])
        assertTrue(tab.canGoBack)
        assertFalse(tab.canGoForward)

        tab.addToHistory("https://example.com/page2")
        assertEquals(2, tab.history.size)
        assertEquals(1, tab.historyIndex)
        assertTrue(tab.canGoBack)
        assertFalse(tab.canGoForward)
    }

    @Test
    fun testGoBack() {
        tab.addToHistory("https://example.com")
        tab.addToHistory("https://example.com/page2")

        val url = tab.goBack()
        assertEquals("https://example.com", url)
        assertEquals(0, tab.historyIndex)
        assertFalse(tab.canGoBack)
        assertTrue(tab.canGoForward)

        // Can't go back further
        assertNull(tab.goBack())
    }

    @Test
    fun testGoForward() {
        tab.addToHistory("https://example.com")
        tab.addToHistory("https://example.com/page2")
        tab.goBack() // Back to index 0

        val url = tab.goForward()
        assertEquals("https://example.com/page2", url)
        assertEquals(1, tab.historyIndex)
        assertTrue(tab.canGoBack)
        assertFalse(tab.canGoForward)

        // Can't go forward further
        assertNull(tab.goForward())
    }

    @Test
    fun testHistoryTruncationOnNewNavigation() {
        tab.addToHistory("https://example.com")
        tab.addToHistory("https://example.com/page2")
        tab.addToHistory("https://example.com/page3")
        tab.goBack() // Back to page2 (index 1)
        tab.goBack() // Back to example.com (index 0)

        // Navigate to new URL - should truncate forward history
        tab.addToHistory("https://newsite.com")

        assertEquals(2, tab.history.size)
        assertEquals("https://example.com", tab.history[0])
        assertEquals("https://newsite.com", tab.history[1])
        assertEquals(1, tab.historyIndex)
        assertTrue(tab.canGoBack)
        assertFalse(tab.canGoForward)
    }

    @Test
    fun testResetForNewNavigation() {
        tab.isLoading = false
        tab.progress = 100
        tab.blockedCount = 5
        tab.title = "Test Page"

        tab.resetForNewNavigation()

        assertTrue(tab.isLoading)
        assertEquals(0, tab.progress)
        assertEquals(0, tab.blockedCount)
        assertEquals("", tab.title)
    }

    @Test
    fun testPrivateTabCreation() {
        val privateTab = Tab.createPrivate()
        assertTrue(privateTab.isPrivate)
    }

    @Test
    fun testToBridgeTab() {
        tab.url = "https://example.com"
        tab.title = "Example"
        tab.addToHistory("https://example.com")

        val bridgeTab = tab.toBridgeTab()

        assertEquals(tab.id, bridgeTab.id)
        assertEquals(tab.url, bridgeTab.url)
        assertEquals(tab.title, bridgeTab.title)
        assertEquals(tab.history, bridgeTab.history)
        assertNull(bridgeTab.webView) // WebView should be stripped
    }

    @Test
    fun testUpdateNavigationState() {
        tab.history.add("https://a.com")
        tab.history.add("https://b.com")
        tab.historyIndex = 0
        tab.updateNavigationState()
        assertFalse(tab.canGoBack)
        assertTrue(tab.canGoForward)

        tab.historyIndex = 1
        tab.updateNavigationState()
        assertTrue(tab.canGoBack)
        assertFalse(tab.canGoForward)
    }
}