package me.bnfy.blanc.adblock

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.bnfy.blanc.tab.Tab
import me.bnfy.blanc.tab.BlancBridge
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*

/**
 * Instrumented tests for AdblockWebViewClient.
 * Requires Android runtime for WebView.
 */
@RunWith(AndroidJUnit4::class)
class AdblockWebViewClientInstrumentedTest {

    private lateinit var context: Context
    private lateinit var mockBridge: BlancBridge
    private lateinit var mockTab: Tab
    private lateinit var adblockEngine: AdblockEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        mockBridge = mock(BlancBridge::class.java)
        mockTab = Tab.create()
        adblockEngine = AdblockEngine.createForTesting(context)
    }

    @Test
    fun testAdblockWebViewClientCreation() {
        val client = AdblockWebViewClient(mockTab, mockBridge, adblockEngine)
        assertNotNull(client)
    }

    @Test
    fun testAdblockWebViewClientDecoratorCreation() {
        val delegate = mock(android.webkit.WebViewClient::class.java)
        val client = AdblockWebViewClientDecorator(delegate, mockTab, mockBridge, adblockEngine)
        assertNotNull(client)
    }

    @Test
    fun testCosmeticFilterEngineInjection() {
        val webView = WebView(context)
        val cosmeticEngine = adblockEngine.getCosmeticEngine()

        // Add a test cosmetic filter
        val filter = Filter(
            id = "test-cosmetic",
            raw = "##.test-ad",
            isException = false,
            type = FilterType.COSMETIC,
            pattern = ".test-ad",
            domains = emptyList(),
            options = FilterOptions()
        )
        cosmeticEngine.addCosmeticFilter(filter)

        // Generate CSS
        val css = cosmeticEngine.generateCssForUrl("https://example.com/page.html")
        assertTrue(css.contains(".test-ad"))
        assertTrue(css.contains("display: none"))

        // Cleanup
        webView.destroy()
    }

    @Test
    fun testAdblockEngineStats() {
        val stats = adblockEngine.getStats()
        assertNotNull(stats)
        assertEquals(0, stats.totalBlocked)
        assertEquals(0, stats.todayBlocked)
        assertTrue(stats.isEnabled)
    }

    @Test
    fun testExceptionHandling() {
        adblockEngine.addException("example.com")
        assertTrue(adblockEngine.isException("example.com"))
        assertTrue(adblockEngine.isException("EXAMPLE.COM"))

        adblockEngine.removeException("example.com")
        assertFalse(adblockEngine.isException("example.com"))
    }

    @Test
    fun testEnableDisable() {
        assertTrue(adblockEngine.isEnabled())

        adblockEngine.setEnabled(false)
        assertFalse(adblockEngine.isEnabled())

        adblockEngine.setEnabled(true)
        assertTrue(adblockEngine.isEnabled())
    }

    @Test
    fun testCosmeticFilterDomainMatching() {
        val cosmeticEngine = adblockEngine.getCosmeticEngine()

        val filter1 = Filter(
            id = "filter-1",
            raw = "example.com##.ad",
            isException = false,
            type = FilterType.COSMETIC,
            pattern = ".ad",
            domains = listOf("example.com"),
            options = FilterOptions()
        )

        val filter2 = Filter(
            id = "filter-2",
            raw = "test.com##.banner",
            isException = false,
            type = FilterType.COSMETIC,
            pattern = ".banner",
            domains = listOf("test.com"),
            options = FilterOptions()
        )

        cosmeticEngine.addCosmeticFilter(filter1)
        cosmeticEngine.addCosmeticFilter(filter2)

        // Test example.com gets only .ad filter
        val css1 = cosmeticEngine.generateCssForUrl("https://example.com/page.html")
        assertTrue(css1.contains(".ad"))
        assertFalse(css1.contains(".banner"))

        // Test test.com gets only .banner filter
        val css2 = cosmeticEngine.generateCssForUrl("https://test.com/page.html")
        assertTrue(css2.contains(".banner"))
        assertFalse(css2.contains(".ad"))

        // Test other.com gets neither
        val css3 = cosmeticEngine.generateCssForUrl("https://other.com/page.html")
        assertFalse(css3.contains(".ad"))
        assertFalse(css3.contains(".banner"))
    }

    @Test
    fun testCosmeticFilterException() {
        val cosmeticEngine = adblockEngine.getCosmeticEngine()

        val filter = Filter(
            id = "filter-1",
            raw = "##.ad",
            isException = false,
            type = FilterType.COSMETIC,
            pattern = ".ad",
            domains = emptyList(),
            options = FilterOptions()
        )

        val exception = Filter(
            id = "exception-1",
            raw = "example.com#@##.ad",
            isException = true,
            type = FilterType.HTML,
            pattern = ".ad",
            domains = listOf("example.com"),
            options = FilterOptions()
        )

        cosmeticEngine.addCosmeticFilter(filter)
        cosmeticEngine.addCosmeticFilter(exception)

        // Exception should apply on example.com
        val css1 = cosmeticEngine.generateCssForUrl("https://example.com/page.html")
        assertFalse(css1.contains(".ad"))

        // Filter should apply on other.com
        val css2 = cosmeticEngine.generateCssForUrl("https://other.com/page.html")
        assertTrue(css2.contains(".ad"))
    }
}