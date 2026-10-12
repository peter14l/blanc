package me.bnfy.blanc.util

/**
 * Parses bookmarks exports produced by other browsers.
 *
 * Primary format: the Netscape bookmarks HTML file that Chrome, Edge, Brave,
 * Vivaldi, Opera and Firefox produce via "Export bookmarks": a DOCTYPE +
 * `<H1>` + nested `<DL>`/`<DT>` tree where `<H3>` marks a folder and
 * `<A HREF="...">` marks a bookmark.
 *
 * Fallback: any generic HTML document containing `<a href="...">` anchors.
 *
 * Pure Kotlin (no Android imports) so it can be unit-tested without a device.
 * Android cannot read another installed browser's private data directly (each
 * app's profile is sandboxed), so the browser-export file — the same universal
 * route the desktop app offers — is the supported import path on this platform.
 */
object BookmarksHtmlImporter {

    /** A single bookmark parsed out of an export file. */
    data class Entry(
        val title: String,
        val url: String,
        val folderPath: String
    )

    private val FOLDER_REGEX =
        Regex("<H3[^>]*>(.*?)</H3>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ANCHOR_REGEX =
        Regex("<A[^>]*>(.*?)</A>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val HREF_REGEX =
        Regex("HREF\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val GENERIC_ANCHOR_REGEX = Regex(
        "<a\\b[^>]*?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " "
    )

    private val ENTITY_REGEX =
        Regex("&(?:#(\\d{1,6})|#x([0-9a-fA-F]{1,6})|([a-zA-Z][a-zA-Z0-9]{1,8}));")

    /** Parses the export file's contents into flat entries (structured Netscape, then generic-anchor fallback). */
    fun parse(content: String): List<Entry> {
        return structuredParse(content).ifEmpty { anchorFallback(content) }
    }

    /** Decodes the HTML entities browsers emit in titles. */
    fun unescape(input: String): String {
        return ENTITY_REGEX.replace(input) { match ->
            when {
                match.groupValues[1].isNotEmpty() ->
                    match.groupValues[1].toIntOrNull()?.let(::codePointOf) ?: match.value
                match.groupValues[2].isNotEmpty() ->
                    match.groupValues[2].toIntOrNull(16)?.let(::codePointOf) ?: match.value
                else -> NAMED_ENTITIES[match.groupValues[3].lowercase()] ?: match.value
            }
        }
    }

    private fun codePointOf(codePoint: Int): String? =
        runCatching { String(Character.toChars(codePoint)) }.getOrNull()

    private fun structuredParse(content: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val folderStack = ArrayDeque<String>()
        val lines = content.lines()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("</DL>", ignoreCase = true) -> {
                    folderStack.removeLastOrNull()
                }
                line.contains("<H3", ignoreCase = true) -> {
                    var buffer = line
                    while (!buffer.contains("</H3>", ignoreCase = true) && i + 1 < lines.size) {
                        i++
                        buffer += lines[i].trim()
                    }
                    val name = FOLDER_REGEX.find(buffer)?.groupValues?.get(1)?.trim().orEmpty()
                    if (name.isNotEmpty()) folderStack.addLast(name)
                }
                line.contains("<A", ignoreCase = true) -> {
                    var buffer = line
                    while (!buffer.contains("</A>", ignoreCase = true) && i + 1 < lines.size) {
                        i++
                        buffer += lines[i].trim()
                    }
                    val href = HREF_REGEX.find(buffer)?.groupValues?.get(1)
                    if (href != null && href.startsWith("http", ignoreCase = true)) {
                        val title = ANCHOR_REGEX.find(buffer)?.groupValues?.get(1)?.trim().orEmpty()
                        entries.add(
                            Entry(
                                title = unescape(title).ifBlank { href },
                                url = href,
                                folderPath = folderStack.joinToString("/")
                            )
                        )
                    }
                }
            }
            i++
        }
        return entries
    }

    private fun anchorFallback(content: String): List<Entry> {
        return GENERIC_ANCHOR_REGEX.findAll(content).mapNotNull { match ->
            val url = unescape(match.groupValues[1])
            if (!url.startsWith("http", ignoreCase = true)) {
                null
            } else {
                val title = unescape(match.groupValues[2].trim()).ifBlank { url }
                Entry(title, url, "")
            }
        }.toList()
    }
}