package me.bnfy.blanc.tab

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Instrumented tests for TabWebViewClient behavior.
 */
@RunWith(AndroidJUnit4::class)
class TabWebViewClientInstrumentedTest {

    private lateinit var context: Context
    private lateinit var tab: Tab
    private lateinit var mockBridge: BlancBridge
    private lateinit var mockAdblockEngine: AdblockEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tab = Tab.create()
        mockBridge = mock(BlancBridge::class.java)
        mockAdblockEngine = mock(AdblockEngine::class.java)
    }

    @Test
    fun testShouldOverrideUrlLoading_NormalUrl() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        val request = mock(WebResourceRequest::class.java)
        `when`(request.url).thenReturn(Uri.parse("https://example.com"))
        `when`(request.isForMainFrame).thenReturn(true)

        val result = client.shouldOverrideUrlLoading(webView, request)

        assertFalse(result)
        assertEquals("https://example.com", tab.url)
        assertTrue(tab.isLoading)
        verify(mockBridge).onNavigation(eq(tab.id), eq("https://example.com"), isNull())
    }

    @Test
    fun testShouldOverrideUrlLoading_BlancUrl() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        val request = mock(WebResourceRequest::class.java)
        `when`(request.url).thenReturn(Uri.parse("blanc://settings"))
        `when`(request.isForMainFrame).thenReturn(true)

        val result = client.shouldOverrideUrlLoading(webView, request)

        assertTrue(result)
    }

    @Test
    fun testShouldOverrideUrlLoading_MailtoUrl() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        val request = mock(WebResourceRequest::class.java)
        `when`(request.url).thenReturn(Uri.parse("mailto:test@example.com"))
        `when`(request.isForMainFrame).thenReturn(true)

        val result = client.shouldOverrideUrlLoading(webView, request)

        // Should return false to let OS handle it
        assertFalse(result)
    }

    @Test
    fun testOnPageStarted() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        client.onPageStarted(webView, "https://example.com", null)

        assertEquals("https://example.com", tab.url)
        assertTrue(tab.isLoading)
        assertEquals(0, tab.progress)
        assertEquals(0, tab.blockedCount)
        verify(mockBridge).onNavigation(eq(tab.id), eq("https://example.com"), isNull())
        verify(mockBridge).onTabUpdated(any())
    }

    @Test
    fun testOnPageFinished() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        client.onPageFinished(webView, "https://example.com")

        assertEquals("https://example.com", tab.url)
        assertFalse(tab.isLoading)
        assertEquals(100, tab.progress)
        assertEquals(1, tab.history.size)
        assertEquals(0, tab.historyIndex)
        verify(mockBridge).onTabUpdated(any())
    }

    @Test
    fun testOnReceivedTitle() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        client.onReceivedTitle(webView, "Example Domain")

        assertEquals("Example Domain", tab.title)
        verify(mockBridge).onTabUpdated(any())
    }

    @Test
    fun testShouldInterceptRequest_Blocked() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        val blockedResponse = WebResourceResponse("text/plain", "utf-8", null)
        `when`(mockAdblockEngine.shouldBlock(anyString(), any())).thenReturn(true)
        `when`(mockAdblockEngine.createBlockedResponse()).thenReturn(blockedResponse)

        val request = mock(WebResourceRequest::class.java)
        `when`(request.url).thenReturn(Uri.parse("https://ads.example.com/banner.js"))

        val response = client.shouldInterceptRequest(webView, request)

        assertEquals(blockedResponse, response)
        assertEquals(1, tab.blockedCount)
        verify(mockBridge).onTabUpdated(any())
    }

    @Test
    fun testShouldInterceptRequest_NotBlocked() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        `when`(mockAdblockEngine.shouldBlock(anyString(), any())).thenReturn(false)

        val request = mock(WebResourceRequest::class.java)
        `when`(request.url).thenReturn(Uri.parse("https://example.com/script.js"))

        val response = client.shouldInterceptRequest(webView, request)

        assertNull(response)
        assertEquals(0, tab.blockedCount)
    }

    @Test
    fun testOnReceivedError_MainFrame() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        val request = mock(WebResourceRequest::class.java)
        `when`(request.isForMainFrame).thenReturn(true)

        val error = mock(android.webkit.WebResourceError::class.java)

        client.onReceivedError(webView, request, error)

        assertFalse(tab.isLoading)
        assertEquals(100, tab.progress)
        verify(mockBridge).onTabUpdated(any())
    }

    @Test
    fun testHistoryTracking() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        // Simulate navigation sequence
        client.onPageStarted(webView, "https://a.com", null)
        client.onPageFinished(webView, "https://a.com")

        assertEquals(1, tab.history.size)
        assertEquals(0, tab.historyIndex)

        client.onPageStarted(webView, "https://b.com", null)
        client.onPageFinished(webView, "https://b.com")

        assertEquals(2, tab.history.size)
        assertEquals(1, tab.historyIndex)
        assertTrue(tab.canGoBack)
        assertFalse(tab.canGoForward)

        client.onPageStarted(webView, "https://c.com", null)
        client.onPageFinished(webView, "https://c.com")

        assertEquals(3, tab.history.size)
        assertEquals(2, tab.historyIndex)
    }

    @Test
    fun testGoBackForwardNavigation() {
        val client = TabWebViewClient(tab, mockBridge, mockAdblockEngine)
        val webView = WebView(context)

        // Build history
        client.onPageStarted(webView, "https://a.com", null)
        client.onPageFinished(webView, "https://a.com")
        client.onPageStarted(webView, "https://b.com", null)
        client.onPageFinished(webView, "https://b.com")

        // Go back
        val backUrl = tab.goBack()
        assertEquals("https://a.com", backUrl)
        assertEquals(0, tab.historyIndex)
        assertFalse(tab.canGoBack)
        assertTrue(tab.canGoForward)

        // Go forward
        val forwardUrl = tab.goForward()
        assertEquals("https://b.com", forwardUrl)
        assertEquals(1, tab.historyIndex)
        assertTrue(tab.canGoBack)
        assertFalse(tab.canGoForward)
    }
}