package me.bnfy.blanc.adblock

import java.util.regex.Pattern

/**
 * Parses EasyList/EasyPrivacy format filter lists into structured Filter objects.
 *
 * Supports the following filter formats:
 * - Comments: `! comment`
 * - Exception rules: `@@||example.com^`
 * - Domain-restricted cosmetic: `example.com##.ad`
 * - Network filters: `||example.com^$third-party`
 * - Cosmetic filters: `##.ad-banner`, `example.com##.ad`
 * - HTML filters: `example.com##^script:has-text(ad)`
 */
class FilterListParser {

    companion object {
        /** Pattern to match the domain part of a filter (before ## or ^) */
        private const val DOMAIN_PATTERN = "^([a-zA-Z0-9.-]+)(?:##|\\^)"
        /** Pattern to match exception rules */
        private const val EXCEPTION_PATTERN = "^@@"
        /** Pattern to match cosmetic filters */
        private const val COSMETIC_PATTERN = "##"
        /** Pattern to match HTML filters */
        private const val HTML_FILTER_PATTERN = "#@#"
        /** Pattern to match network filter options */
        private const val OPTIONS_PATTERN = "\\$(.+)$"
    }

    /**
     * Parses a filter list from a string (one filter per line).
     * @param content The filter list content
     * @return List of parsed Filter objects
     */
    fun parseFilterList(content: String): List<Filter> {
        val filters = mutableListOf<Filter>()
        val lines = content.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) {
                // Skip comments, empty lines, and section headers
                continue
            }

            val filter = parseFilter(trimmed)
            if (filter != null) {
                filters.add(filter)
            }
        }

        return filters
    }

    /**
     * Parses a single filter line into a Filter object.
     * @param line A single filter line
     * @return Parsed Filter or null if unparseable
     */
    fun parseFilter(line: String): Filter? {
        val trimmed = line.trim()

        // Check for exception rule
        val isException = trimmed.startsWith("@@")
        val workingLine = if (isException) trimmed.substring(2) else trimmed

        // Check for cosmetic/HTML filter
        val cosmeticIndex = workingLine.indexOf("##")
        val htmlFilterIndex = workingLine.indexOf("#@#")

        if (cosmeticIndex >= 0 || htmlFilterIndex >= 0) {
            return parseCosmeticFilter(workingLine, cosmeticIndex, htmlFilterIndex, isException)
        }

        // Network filter
        return parseNetworkFilter(workingLine, isException)
    }

    /**
     * Parses a network filter (e.g., `||example.com^$third-party`).
     */
    private fun parseNetworkFilter(line: String, isException: Boolean): Filter? {
        // Extract options if present (e.g., $third-party,script,domain=example.com)
        var filterLine = line
        val options = mutableMapOf<String, String>()
        val optionsMatch = OPTIONS_PATTERN.toRegex().find(line)
        if (optionsMatch != null) {
            filterLine = line.substring(0, optionsMatch.range.first)
            val optionsStr = optionsMatch.groupValues[1]
            parseOptions(optionsStr, options)
        }

        // Parse domain/pattern
        val pattern = parseNetworkPattern(filterLine)
        if (pattern == null) return null

        val domains = options["domain"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val thirdParty = options.containsKey("third-party")
        val firstParty = options.containsKey("first-party")
        val script = options.containsKey("script")
        val image = options.containsKey("image")
        val stylesheet = options.containsKey("stylesheet")
        val xmlhttprequest = options.containsKey("xmlhttprequest")
        val objectType = options.containsKey("object")
        val subdocument = options.containsKey("subdocument")
        val document = options.containsKey("document")
        val elemhide = options.containsKey("elemhide")
        val popup = options.containsKey("popup")
        val dontblock = options.containsKey("dontblock")
        val matchCase = options.containsKey("match-case")
        val collapse = options.containsKey("collapse")
        val redirect = options["redirect"]
        val redirectRule = options["redirect-rule"]
        val important = options.containsKey("important")

        return Filter(
            id = Filter.generateId(),
            raw = line,
            isException = isException,
            type = FilterType.NETWORK,
            pattern = pattern,
            domains = domains,
            options = FilterOptions(
                thirdParty = thirdParty,
                firstParty = firstParty,
                script = script,
                image = image,
                stylesheet = stylesheet,
                xmlhttprequest = xmlhttprequest,
                `object` = objectType,
                subdocument = subdocument,
                document = document,
                elemhide = elemhide,
                popup = popup,
                dontblock = dontblock,
                matchCase = matchCase,
                collapse = collapse,
                redirect = redirect,
                redirectRule = redirectRule,
                important = important
            )
        )
    }

    /**
     * Parses the network pattern from a filter line (e.g., `||example.com^`).
     */
    private fun parseNetworkPattern(line: String): String? {
        var pattern = line.trim()

        // Remove leading/trailing anchors
        pattern = pattern.trimStart('|').trimEnd('|')

        // Remove trailing ^ (separator)
        if (pattern.endsWith("^")) {
            pattern = pattern.substring(0, pattern.length - 1)
        }

        // Convert * to .* for regex
        pattern = pattern.replace("*", ".*")

        // Escape special regex chars except * which we already handled
        pattern = pattern
            .replace(".", "\\.")
            .replace("?", "\\?")
            .replace("+", "\\+")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("^", "[^a-zA-Z0-9_.-]") // ^ matches separator in EasyList

        return if (pattern.isNotEmpty()) pattern else null
    }

    /**
     * Parses filter options (e.g., $third-party,script,domain=example.com).
     */
    private fun parseOptions(optionsStr: String, options: MutableMap<String, String>) {
        val parts = optionsStr.split(",")
        for (part in parts) {
            val kv = part.split("=", limit = 2)
            if (kv.size == 2) {
                options[kv[0].trim()] = kv[1].trim()
            } else {
                options[kv[0].trim()] = "true"
            }
        }
    }

    /**
     * Parses a cosmetic or HTML filter (e.g., `example.com##.ad`, `##.ad-banner`).
     */
    private fun parseCosmeticFilter(
        line: String,
        cosmeticIndex: Int,
        htmlFilterIndex: Int,
        isException: Boolean
    ): Filter? {
        val isHtmlFilter = htmlFilterIndex >= 0
        val separatorIndex = if (isHtmlFilter) htmlFilterIndex else cosmeticIndex
        val isExceptionCosmetic = isException || isHtmlFilter // #@# is exception for cosmetic

        // Split into domain part and selector part
        val domainPart = if (separatorIndex > 0) line.substring(0, separatorIndex) else ""
        val selectorPart = line.substring(separatorIndex + (if (isHtmlFilter) 3 else 2))

        val domains = if (domainPart.isNotEmpty()) {
            domainPart.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList<String>()
        }

        val selector = selectorPart.trim()
        if (selector.isEmpty()) return null

        return Filter(
            id = Filter.generateId(),
            raw = line,
            isException = isExceptionCosmetic,
            type = if (isHtmlFilter) FilterType.HTML else FilterType.COSMETIC,
            pattern = selector,
            domains = domains,
            options = FilterOptions()
        )
    }
}

/**
 * Represents a parsed filter rule.
 */
data class Filter(
    val id: String,
    val raw: String,
    val isException: Boolean,
    val type: FilterType,
    val pattern: String, // Regex pattern for network, CSS selector for cosmetic
    val domains: List<String> = emptyList(), // Empty = all domains
    val options: FilterOptions = FilterOptions()
) {
    companion object {
        private var idCounter = 0L
        fun generateId(): String = "filter_${System.currentTimeMillis()}_${idCounter++}"
    }

    /**
     * Checks if this filter applies to the given URL and request context.
     */
    fun matches(url: String, sourceUrl: String?, requestType: String): Boolean {
        return when (type) {
            FilterType.NETWORK -> matchesNetwork(url, sourceUrl, requestType)
            FilterType.COSMETIC, FilterType.HTML -> matchesCosmetic(url)
        }
    }

    private fun matchesNetwork(url: String, sourceUrl: String?, requestType: String): Boolean {
        // Check domain restriction
        if (domains.isNotEmpty()) {
            val urlHost = extractHost(url)
            val sourceHost = sourceUrl?.let { extractHost(it) }
            val matchesDomain = domains.any { domain ->
                matchesDomain(urlHost, domain) || (sourceHost != null && matchesDomain(sourceHost, domain))
            }
            if (!matchesDomain) return false
        }

        // Check request type options
        if (options.script && requestType != "script") return false
        if (options.image && requestType != "image") return false
        if (options.stylesheet && requestType != "stylesheet") return false
        if (options.xmlhttprequest && requestType != "xmlhttprequest") return false
        if (options.`object` && requestType != "object") return false
        if (options.subdocument && requestType != "subdocument") return false
        if (options.document && requestType != "document") return false
        if (options.elemhide && requestType != "elemhide") return false
        if (options.popup && requestType != "popup") return false

        // Check third-party/first-party
        if (options.thirdParty || options.firstParty) {
            val urlHost = extractHost(url)
            val sourceHost = sourceUrl?.let { extractHost(it) } ?: ""
            val isThirdParty = sourceHost.isNotEmpty() && !sameDomain(urlHost, sourceHost)

            if (options.thirdParty && !isThirdParty) return false
            if (options.firstParty && isThirdParty) return false
        }

        // Match pattern against URL
        return matchPattern(url, pattern, options.matchCase)
    }

    private fun matchesCosmetic(url: String): Boolean {
        if (domains.isEmpty()) return true // Applies to all domains
        val urlHost = extractHost(url)
        return domains.any { matchesDomain(urlHost, it) }
    }

    private fun extractHost(url: String): String {
        return try {
            java.net.URL(url).host.toLowerCase()
        } catch (e: Exception) {
            ""
        }
    }

    private fun matchesDomain(host: String, domain: String): Boolean {
        val cleanDomain = domain.removePrefix("~").lowercase() // ~domain means exclude
        val isExclude = domain.startsWith("~")
        val matches = host == cleanDomain || host.endsWith(".${cleanDomain}")
        return if (isExclude) !matches else matches
    }

    private fun sameDomain(host1: String, host2: String): Boolean {
        if (host1 == host2) return true
        // Check if one is subdomain of the other (simplified eTLD+1 check)
        val parts1 = host1.split(".")
        val parts2 = host2.split(".")
        if (parts1.size >= 2 && parts2.size >= 2) {
            return parts1.takeLast(2) == parts2.takeLast(2)
        }
        return false
    }

    private fun matchPattern(url: String, pattern: String, matchCase: Boolean): Boolean {
        val flags = if (matchCase) 0 else Pattern.CASE_INSENSITIVE
        return Pattern.compile(pattern, flags).matcher(url).find()
    }
}

/**
 * Type of filter rule.
 */
enum class FilterType {
    NETWORK,    // Blocks network requests (||example.com^)
    COSMETIC,   // Hides elements via CSS (##.ad)
    HTML        // Exception for cosmetic filters (#@#)
}

/**
 * Options for network filters.
 */
data class FilterOptions(
    val thirdParty: Boolean = false,
    val firstParty: Boolean = false,
    val script: Boolean = false,
    val image: Boolean = false,
    val stylesheet: Boolean = false,
    val xmlhttprequest: Boolean = false,
    val `object`: Boolean = false,
    val subdocument: Boolean = false,
    val document: Boolean = false,
    val elemhide: Boolean = false,
    val popup: Boolean = false,
    val dontblock: Boolean = false,
    val matchCase: Boolean = false,
    val collapse: Boolean = false,
    val redirect: String? = null,
    val redirectRule: String? = null,
    val important: Boolean = false
)