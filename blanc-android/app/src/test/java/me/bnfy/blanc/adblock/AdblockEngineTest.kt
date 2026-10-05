package me.bnfy.blanc.adblock

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for AdblockEngine.
 * Tests blocking logic, exceptions, statistics, and filter management.
 */
class AdblockEngineTest {

    private lateinit var mockContext: Context
    private lateinit var engine: AdblockEngine

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        // Use testing instance to avoid singleton
        engine = AdblockEngine.createForTesting(mockContext)
    }

    @Test
    fun testInitialState() {
        assertTrue(engine.isEnabled())
        val stats = engine.getStats()
        assertEquals(0, stats.totalBlocked)
        assertEquals(0, stats.todayBlocked)
        assertEquals(0, stats.exceptionCount)
        assertTrue(stats.isEnabled)
    }

    @Test
    fun testSetEnabled() {
        engine.setEnabled(false)
        assertFalse(engine.isEnabled())

        engine.setEnabled(true)
        assertTrue(engine.isEnabled())
    }

    @Test
    fun testAddAndRemoveException() {
        engine.addException("example.com")
        assertTrue(engine.isException("example.com"))
        assertTrue(engine.isException("EXAMPLE.COM")) // Case insensitive
        assertEquals(1, engine.getExceptions().size)

        engine.removeException("example.com")
        assertFalse(engine.isException("example.com"))
        assertEquals(0, engine.getExceptions().size)
    }

    @Test
    fun testExceptionBlocksFiltering() {
        // Add a filter that would block example.com
        val filter = Filter(
            id = "test-1",
            raw = "||example.com^",
            isException = false,
            type = FilterType.NETWORK,
            pattern = "example\\.com",
            domains = emptyList(),
            options = FilterOptions()
        )
        // We can't easily inject filters into the private map, so we test
        // the exception logic by checking the exception map directly
        engine.addException("example.com")
        assertTrue(engine.isException("example.com"))
    }

    @Test
    fun testGetStats() {
        engine.addException("example.com")
        engine.addException("test.com")

        val stats = engine.getStats()
        assertEquals(2, stats.exceptionCount)
        assertTrue(stats.isEnabled)
    }

    @Test
    fun testResetDailyStats() {
        // We can't directly increment the counter, but we can verify
        // the method exists and doesn't crash
        engine.resetDailyStats()
        val stats = engine.getStats()
        assertEquals(0, stats.todayBlocked)
    }

    @Test
    fun testCosmeticEngineAccess() {
        val cosmeticEngine = engine.getCosmeticEngine()
        assertNotNull(cosmeticEngine)
        assertEquals(0, cosmeticEngine.getTotalCosmeticFilterCount())
        assertEquals(0, cosmeticEngine.getTotalExceptionFilterCount())
    }

    @Test
    fun testShutdown() {
        // Should not throw
        engine.shutdown()
    }
}

/**
 * Tests for AdblockEngine with actual filter parsing (integration-style).
 * These tests use a mock context with real assets.
 */
class AdblockEngineIntegrationTest {

    private lateinit var engine: AdblockEngine
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        engine = AdblockEngine.createForTesting(mockContext)
    }

    @Test
    fun testBlockingLogicWithException() {
        // Test that exceptions work correctly
        engine.addException("allowed.com")

        // We can't easily test shouldBlock without proper filter loading
        // but we can verify the exception map
        assertTrue(engine.isException("allowed.com"))
    }

    @Test
    fun testCosmeticFilterInjection() {
        val cosmeticEngine = engine.getCosmeticEngine()

        // Add a cosmetic filter manually
        val filter = Filter(
            id = "cosmetic-1",
            raw = "##.test-ad",
            isException = false,
            type = FilterType.COSMETIC,
            pattern = ".test-ad",
            domains = emptyList(),
            options = FilterOptions()
        )

        cosmeticEngine.addCosmeticFilter(filter)
        assertEquals(1, cosmeticEngine.getTotalCosmeticFilterCount())

        // Generate CSS for any URL
        val css = cosmeticEngine.generateCssForUrl("https://example.com/page.html")
        assertTrue(css.contains(".test-ad"))
        assertTrue(css.contains("display: none"))

        // Remove filter
        cosmeticEngine.removeCosmeticFilter("cosmetic-1")
        assertEquals(0, cosmeticEngine.getTotalCosmeticFilterCount())
    }

    @Test
    fun testDomainSpecificCosmeticFilters() {
        val cosmeticEngine = engine.getCosmeticEngine()

        val filter1 = Filter(
            id = "cosmetic-1",
            raw = "example.com##.site-ad",
            isException = false,
            type = FilterType.COSMETIC,
            pattern = ".site-ad",
            domains = listOf("example.com"),
            options = FilterOptions()
        )

        val filter2 = Filter(
            id = "cosmetic-2",
            raw = "test.com##.other-ad",
            isException = false,
            type = FilterType.COSMETIC,
            pattern = ".other-ad",
            domains = listOf("test.com"),
            options = FilterOptions()
        )

        cosmeticEngine.addCosmeticFilter(filter1)
        cosmeticEngine.addCosmeticFilter(filter2)

        // Should only get example.com filter for example.com
        val css1 = cosmeticEngine.generateCssForUrl("https://example.com/page.html")
        assertTrue(css1.contains(".site-ad"))
        assertFalse(css1.contains(".other-ad"))

        // Should only get test.com filter for test.com
        val css2 = cosmeticEngine.generateCssForUrl("https://test.com/page.html")
        assertTrue(css2.contains(".other-ad"))
        assertFalse(css2.contains(".site-ad"))

        // Should get neither for other.com
        val css3 = cosmeticEngine.generateCssForUrl("https://other.com/page.html")
        assertFalse(css3.contains(".site-ad"))
        assertFalse(css3.contains(".other-ad"))
    }

    @Test
    fun testCosmeticFilterException() {
        val cosmeticEngine = engine.getCosmeticEngine()

        val filter = Filter(
            id = "cosmetic-1",
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

        // On example.com, the exception should apply
        val css1 = cosmeticEngine.generateCssForUrl("https://example.com/page.html")
        assertFalse(css1.contains(".ad"))

        // On other.com, the filter should apply
        val css2 = cosmeticEngine.generateCssForUrl("https://other.com/page.html")
        assertTrue(css2.contains(".ad"))
    }
}