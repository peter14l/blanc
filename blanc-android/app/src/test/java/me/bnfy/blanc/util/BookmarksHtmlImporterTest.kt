package me.bnfy.blanc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksHtmlImporterTest {

    @Test
    fun parsesNestedNetscapeExportWithFolders() {
        val content = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <!-- This is an automatically generated file. -->
            <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
            <TITLE>Bookmarks</TITLE>
            <H1>Bookmarks</H1>
            <DL><p>
                <DT><H3 ADD_DATE="1347558117" PERSONAL_TOOLBAR_FOLDER="true">Bookmarks bar</H3>
                <DL><p>
                    <DT><A HREF="https://github.com/" ADD_DATE="1347558117">GitHub</A>
                    <DT><A HREF="https://www.google.com/" ADD_DATE="1347558117">Google</A>
                </DL><p>
                <DT><H3 ADD_DATE="1347558117">Other bookmarks</H3>
                <DL><p>
                    <DT><A HREF="https://example.com/a">Example &amp; Co</A>
                </DL><p>
                <DT><A HREF="https://root.example/">Root link</A>
            </DL><p>
        """.trimIndent()

        val entries = BookmarksHtmlImporter.parse(content)

        assertEquals(4, entries.size)
        assertEquals("GitHub", entries[0].title)
        assertEquals("https://github.com/", entries[0].url)
        assertEquals("Bookmarks bar", entries[0].folderPath)
        assertEquals("Google", entries[1].title)
        assertEquals("Bookmarks bar", entries[1].folderPath)
        assertEquals("Example & Co", entries[2].title)
        assertEquals("Other bookmarks", entries[2].folderPath)
        assertEquals("Root link", entries[3].title)
        assertEquals("", entries[3].folderPath)
    }

    @Test
    fun unescapesHtmlEntitiesInTitles() {
        assertEquals("A & B < C > \"D\" 'E'", BookmarksHtmlImporter.unescape("A &amp; B &lt; C &gt; &quot;D&quot; &apos;E&apos;"))
        assertEquals("x'y", BookmarksHtmlImporter.unescape("x&#39;y"))
        assertEquals("x'y", BookmarksHtmlImporter.unescape("x&#x27;y"))
        assertEquals("a b", BookmarksHtmlImporter.unescape("a&nbsp;b"))
        // Unknown entities pass through untouched.
        assertEquals("&bogus;", BookmarksHtmlImporter.unescape("&bogus;"))
    }

    @Test
    fun filtersOutNonHttpLinks() {
        val content = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
                <DT><A HREF="https://ok.example/">OK</A>
                <DT><A HREF="javascript:void(0)">Script</A>
                <DT><A HREF="chrome://settings/">Chrome</A>
                <DT><A HREF="mailto:a@b.c">Mail</A>
                <DT><A HREF="data:text/html,hi">Data</A>
            </DL><p>
        """.trimIndent()

        val entries = BookmarksHtmlImporter.parse(content)

        assertEquals(1, entries.size)
        assertEquals("https://ok.example/", entries[0].url)
    }

    @Test
    fun fallsBackToGenericAnchorsForPlainHtml() {
        val content = """
            <html><body>
                <p>Some page</p>
                <a href="https://one.example/">One</a>
                <a rel="nofollow" href="https://two.example/?x=1&amp;y=2">Two &amp; More</a>
                <a href="https://three.example/">
                    Multi-line
                    title
                </a>
            </body></html>
        """.trimIndent()

        val entries = BookmarksHtmlImporter.parse(content)

        assertEquals(3, entries.size)
        assertEquals("One", entries[0].title)
        assertEquals("https://two.example/?x=1&y=2", entries[1].url)
        assertEquals("Two & More", entries[1].title)
        assertEquals("Multi-line title", entries[2].title.replace("\n", "").trim())
    }

    @Test
    fun returnsEmptyForGarbageContent() {
        assertTrue(BookmarksHtmlImporter.parse("").isEmpty())
        assertTrue(BookmarksHtmlImporter.parse("not a bookmark file at all").isEmpty())
        assertTrue(BookmarksHtmlImporter.parse("<html><body>nothing here</body></html>").isEmpty())
    }

    @Test
    fun multiLineAnchorTagInNetscapeFile() {
        val content = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
                <DT><A HREF="https://multi.example/"
                    ADD_DATE="1347558117">Wide tag</A>
            </DL><p>
        """.trimIndent()

        val entries = BookmarksHtmlImporter.parse(content)

        assertEquals(1, entries.size)
        assertEquals("Wide tag", entries[0].title)
        assertEquals("https://multi.example/", entries[0].url)
    }
}