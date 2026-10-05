package me.bnfy.blanc.adblock

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FilterListParser.
 * Tests parsing of EasyList/EasyPrivacy format filters.
 */
class FilterListParserTest {

    private lateinit var parser: FilterListParser

    @Before
    fun setUp() {
        parser = FilterListParser()
    }

    @Test
    fun testParseEmptyLinesAndComments() {
        val content = """
            ! This is a comment
            ! Another comment

            ||example.com^
        """.trimIndent()

        val filters = parser.parseFilterList(content)
        assertEquals(1, filters.size)
        assertEquals("||example.com^", filters[0].raw)
    }

    @Test
    fun testParseNetworkFilter() {
        val filters = parser.parseFilterList("||example.com^")
        assertEquals(1, filters.size)
        val filter = filters[0]
        assertEquals(FilterType.NETWORK, filter.type)
        assertFalse(filter.isException)
        assertTrue(filter.pattern.contains("example\\.com"))
    }

    @Test
    fun testParseExceptionFilter() {
        val filters = parser.parseFilterList("@@||example.com^")
        assertEquals(1, filters.size)
        val filter = filters[0]
        assertEquals(FilterType.NETWORK, filter.type)
        assertTrue(filter.isException)
    }

    @Test
    fun testParseCosmeticFilter() {
        val filters = parser.parseFilterList("##.ad-banner")
        assertEquals(1, filters.size)
        val filter = filters[0]
        assertEquals(FilterType.COSMETIC, filter.type)
        assertEquals(".ad-banner", filter.pattern)
    }

    @Test
    fun testParseDomainSpecificCosmeticFilter() {
        val filters = parser.parseFilterList("example.com##.ad-banner")
        assertEquals(1, filters.size)
        val filter = filters[0]
        assertEquals(FilterType.COSMETIC, filter.type)
        assertEquals(".ad-banner", filter.pattern)
        assertEquals(1, filter.domains.size)
        assertEquals("example.com", filter.domains[0])
    }

    @Test
    fun testParseMultipleDomainCosmeticFilter() {
        val filters = parser.parseFilterList("example.com,test.com##.ad")
        assertEquals(1, filters.size)
        val filter = filters[0]
        assertEquals(2, filter.domains.size)
        assertTrue(filter.domains.contains("example.com"))
        assertTrue(filter.domains.contains("test.com"))
    }

    @Test
    fun testParseHtmlFilterException() {
        val filters = parser.parseFilterList("example.com#@##.legitimate")
        assertEquals(1, filters.size)
        val filter = filters[0]
        assertEquals(FilterType.HTML, filter.type)
        assertTrue(filter.isException)
        assertEquals(".legitimate", filter.pattern)
    }

    @Test
    fun testParseNetworkFilterWithOptions() {
        val filters = parser.parseFilterList("||example.com^$third-party,script")
        assertEquals(1, filters.size)
        val filter = filters[0]
        assertEquals(FilterType.NETWORK, filter.type)
        assertTrue(filter.options.thirdParty)
        assertTrue(filter.options.script)
    }

    @Test
    fun testParseNetworkFilterWithDomainOption() {
        val filters = parser.parseFilterList("||ads.com^$domain=example.com,test.com")
        assertEquals(1, filters.size)
        val filter = filters[0]
        assertEquals(2, filter.domains.size)
        assertTrue(filter.domains.contains("example.com"))
        assertTrue(filter.domains.contains("test.com"))
    }

    @Test
    fun testParseNetworkFilterWithExcludeDomain() {
        val filters = parser.parseFilterList("||ads.com^$domain=~example.com")
        assertEquals(1, filters.size)
        val filter = filters[0]
        assertEquals(1, filter.domains.size)
        assertEquals("~example.com", filter.domains[0])
    }

    @Test
    fun testParseComplexFilterList() {
        val content = """
            ! Comment
            @@||example.com^
            ||ads.com^$third-party
            ##.ad-banner
            example.com##.site-ad
            example.com#@##.legitimate
            ||tracking.com^$script,image,domain=example.com
        """.trimIndent()

        val filters = parser.parseFilterList(content)
        assertEquals(6, filters.size)

        // Exception
        assertTrue(filters[0].isException)
        assertEquals(FilterType.NETWORK, filters[0].type)

        // Network with options
        assertTrue(filters[1].options.thirdParty)
        assertEquals(FilterType.NETWORK, filters[1].type)

        // Global cosmetic
        assertEquals(FilterType.COSMETIC, filters[2].type)
        assertTrue(filters[2].domains.isEmpty())

        // Domain-specific cosmetic
        assertEquals(FilterType.COSMETIC, filters[3].type)
        assertEquals(1, filters[3].domains.size)

        // HTML exception
        assertEquals(FilterType.HTML, filters[4].type)
        assertTrue(filters[4].isException)

        // Network with multiple options
        assertEquals(FilterType.NETWORK, filters[5].type)
        assertTrue(filters[5].options.script)
        assertTrue(filters[5].options.image)
        assertEquals(1, filters[5].domains.size)
    }

    @Test
    fun testFilterMatchesNetwork() {
        val filter = parser.parseFilter("||ads.example.com^")!!
        assertTrue(filter.matches("https://ads.example.com/banner.jpg", null, "image"))
        assertTrue(filter.matches("http://ads.example.com/script.js", null, "script"))
        assertFalse(filter.matches("https://example.com/page.html", null, "document"))
    }

    @Test
    fun testFilterMatchesWithSourceUrl() {
        val filter = parser.parseFilter("||ads.com^$third-party")!!
        // Third-party request
        assertTrue(filter.matches("https://ads.com/banner.jpg", "https://example.com/page.html", "image"))
        // First-party request (same domain)
        assertFalse(filter.matches("https://example.com/ads.jpg", "https://example.com/page.html", "image"))
    }

    @Test
    fun testFilterMatchesFirstPartyOption() {
        val filter = parser.parseFilter("||example.com^$first-party")!!
        assertTrue(filter.matches("https://example.com/tracking.js", "https://example.com/page.html", "script"))
        assertFalse(filter.matches("https://example.com/tracking.js", "https://other.com/page.html", "script"))
    }

    @Test
    fun testFilterMatchesDomainRestriction() {
        val filter = parser.parseFilter("||ads.com^$domain=example.com")!!
        assertTrue(filter.matches("https://ads.com/banner.jpg", "https://example.com/page.html", "image"))
        assertFalse(filter.matches("https://ads.com/banner.jpg", "https://other.com/page.html", "image"))
    }

    @Test
    fun testFilterMatchesExcludeDomain() {
        val filter = parser.parseFilter("||ads.com^$domain=~example.com")!!
        assertFalse(filter.matches("https://ads.com/banner.jpg", "https://example.com/page.html", "image"))
        assertTrue(filter.matches("https://ads.com/banner.jpg", "https://other.com/page.html", "image"))
    }

    @Test
    fun testFilterMatchesRequestType() {
        val filter = parser.parseFilter("||ads.com^$script")!!
        assertTrue(filter.matches("https://ads.com/script.js", null, "script"))
        assertFalse(filter.matches("https://ads.com/image.png", null, "image"))
        assertFalse(filter.matches("https://ads.com/style.css", null, "stylesheet"))
    }

    @Test
    fun testCosmeticFilterMatchesAllDomains() {
        val filter = parser.parseFilter("##.ad-banner")!!
        assertTrue(filter.matchesCosmetic("https://example.com/page.html"))
        assertTrue(filter.matchesCosmetic("https://test.com/page.html"))
    }

    @Test
    fun testCosmeticFilterMatchesSpecificDomain() {
        val filter = parser.parseFilter("example.com##.ad-banner")!!
        assertTrue(filter.matchesCosmetic("https://example.com/page.html"))
        assertTrue(filter.matchesCosmetic("https://sub.example.com/page.html"))
        assertFalse(filter.matchesCosmetic("https://other.com/page.html"))
    }

    @Test
    fun testCosmeticFilterMultipleDomains() {
        val filter = parser.parseFilter("example.com,test.com##.ad")!!
        assertTrue(filter.matchesCosmetic("https://example.com/page.html"))
        assertTrue(filter.matchesCosmetic("https://test.com/page.html"))
        assertFalse(filter.matchesCosmetic("https://other.com/page.html"))
    }

    @Test
    fun testWildcardPatternMatching() {
        val filter = parser.parseFilter("||*ads*.com^")!!
        // The parser converts * to .* and escapes dots
        assertTrue(filter.matches("https://ads.example.com/banner.jpg", null, "image"))
        assertTrue(filter.matches("https://myads.com/banner.jpg", null, "image"))
    }

    @Test
    fun testSeparatorMatching() {
        val filter = parser.parseFilter("||example.com^")!!
        // ^ matches separators like /, ?, &, =
        assertTrue(filter.matches("https://example.com/", null, "document"))
        assertTrue(filter.matches("https://example.com/path", null, "document"))
        assertTrue(filter.matches("https://example.com?query=1", null, "document"))
        assertFalse(filter.matches("https://example.com.evil.com/", null, "document"))
    }
}