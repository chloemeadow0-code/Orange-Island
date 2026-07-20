package com.orangeisland.app.api

import org.junit.Assert.*
import org.junit.Test

class DuckDuckGoScraperTest {

    private val scraper = DuckDuckGoScraper()

    // -- WebResult parsing tests --------------------------------------------------

    @Test
    fun parseResults_withDdgRedirectUrls_extractsDecodedUrl() {
        val html = buildHtml(
            result(
                num = 1,
                href = "//duckduckgo.com/l/?uddg=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FHello%2C_world&amp;rut=abc123",
                title = "Hello, world - Wikipedia",
                snippet = "A \"Hello, world\" program is usually a simple computer program."
            )
        )

        val results = scraper.parseResults(html, maxResults = 5)
        assertEquals(1, results.size)
        assertEquals("Hello, world - Wikipedia", results[0].title)
        assertEquals("https://en.wikipedia.org/wiki/Hello,_world", results[0].url)
        assertEquals("A \"Hello, world\" program is usually a simple computer program.", results[0].snippet)
    }

    @Test
    fun parseResults_withDirectUrl_usesUrlAsIs() {
        val html = buildHtml(
            result(
                num = 1,
                href = "https://kotlinlang.org/docs/coroutines-guide.html",
                title = "Coroutines guide | Kotlin Documentation",
                snippet = "Kotlin's concept of suspending function provides a safer abstraction."
            )
        )

        val results = scraper.parseResults(html, maxResults = 5)
        assertEquals(1, results.size)
        assertEquals("Coroutines guide | Kotlin Documentation", results[0].title)
        assertEquals("https://kotlinlang.org/docs/coroutines-guide.html", results[0].url)
    }

    @Test
    fun parseResults_withProtocolRelativeUrl_addsHttps() {
        val html = buildHtml(
            result(
                num = 1,
                href = "//en.wikipedia.org/wiki/Hello,_world",
                title = "Hello, world - Wikipedia",
                snippet = "A simple program."
            )
        )

        val results = scraper.parseResults(html, maxResults = 5)
        assertEquals(1, results.size)
        assertEquals("https://en.wikipedia.org/wiki/Hello,_world", results[0].url)
    }

    @Test
    fun parseResults_multipleResults_returnsAllUpToMax() {
        val html = buildHtml(
            result(1, "https://a.com/page1", "Result One", "Snippet one."),
            result(2, "https://b.com/page2", "Result Two", "Snippet two."),
            result(3, "https://c.com/page3", "Result Three", "Snippet three."),
        )

        val results = scraper.parseResults(html, maxResults = 2)
        assertEquals(2, results.size)
        assertEquals("Result One", results[0].title)
        assertEquals("Result Two", results[1].title)
    }

    @Test
    fun parseResults_emptyHtml_returnsEmptyList() {
        val results = scraper.parseResults("<html><body></body></html>", maxResults = 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun parseResults_noResultRows_returnsEmptyList() {
        val html = """
            <table border="0">
            <tr><td>No results found</td></tr>
            </table>
        """.trimIndent()

        val results = scraper.parseResults(html, maxResults = 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun parseResults_htmlEntitiesInTitle_areDecoded() {
        val html = buildHtml(
            result(
                num = 1,
                href = "https://example.com/page",
                title = "How &quot;Hello World&quot; Became the Universal First Step",
                snippet = "Learn about the &lt;code&gt;hello world&lt;/code&gt; pattern &amp; its history."
            )
        )

        val results = scraper.parseResults(html, maxResults = 5)
        assertEquals(1, results.size)
        assertEquals("How \"Hello World\" Became the Universal First Step", results[0].title)
        assertEquals("Learn about the <code>hello world</code> pattern & its history.", results[0].snippet)
    }

    @Test
    fun parseResults_boldTagsInSnippet_areStripped() {
        val html = buildHtml(
            result(
                num = 1,
                href = "https://example.com",
                title = "Search Result",
                snippet = "This is a <b>highlighted</b> term in the snippet."
            )
        )

        val results = scraper.parseResults(html, maxResults = 5)
        assertEquals(1, results.size)
        assertEquals("This is a highlighted term in the snippet.", results[0].snippet)
    }

    @Test
    fun parseResults_respectsMaxResults() {
        val html = buildHtml(
            result(1, "https://a.com", "A", "snippet a"),
            result(2, "https://b.com", "B", "snippet b"),
            result(3, "https://c.com", "C", "snippet c"),
            result(4, "https://d.com", "D", "snippet d"),
            result(5, "https://e.com", "E", "snippet e"),
            result(6, "https://f.com", "F", "snippet f"),
        )

        val results = scraper.parseResults(html, maxResults = 4)
        assertEquals(4, results.size)
    }

    @Test
    fun parseResults_skipsResultWithBlankTitle() {
        val html = buildHtml(
            result(
                num = 1,
                href = "https://example.com/good",
                title = "Good Result",
                snippet = "Has a title."
            ),
            "<tr><td valign=\"top\">2.&nbsp;</td><td><a rel=\"nofollow\" href=\"https://example.com/bad\" class='result-link'></a></td></tr>" +
            "<tr><td>&nbsp;&nbsp;&nbsp;</td><td class='result-snippet'>No title here.</td></tr>" +
            "<tr><td>&nbsp;</td><td>&nbsp;</td></tr>",
            result(
                num = 3,
                href = "https://example.com/another",
                title = "Another Good",
                snippet = "Also has a title."
            ),
        )

        val results = scraper.parseResults(html, maxResults = 5)
        assertEquals(2, results.size)
        assertEquals("Good Result", results[0].title)
        assertEquals("Another Good", results[1].title)
    }

    @Test
    fun parseResults_skipsResultWithBlankUrl() {
        val html = buildHtml(
            result(
                num = 1,
                href = "https://example.com/good",
                title = "Good Result",
                snippet = "Has a URL."
            ),
            "<tr><td valign=\"top\">2.&nbsp;</td><td><a rel=\"nofollow\" href=\"\" class='result-link'>No URL</a></td></tr>" +
            "<tr><td>&nbsp;&nbsp;&nbsp;</td><td class='result-snippet'>Missing href.</td></tr>" +
            "<tr><td>&nbsp;</td><td>&nbsp;</td></tr>",
        )

        val results = scraper.parseResults(html, maxResults = 5)
        assertEquals(1, results.size)
        assertEquals("Good Result", results[0].title)
    }

    @Test
    fun parseResults_trimsWhitespaceInTitleAndSnippet() {
        val html = buildHtml(
            result(
                num = 1,
                href = "https://example.com",
                title = "\n  Spaced Title  \n",
                snippet = "\n  Spaced Snippet  \n"
            )
        )

        val results = scraper.parseResults(html, maxResults = 5)
        assertEquals(1, results.size)
        assertEquals("Spaced Title", results[0].title)
        assertEquals("Spaced Snippet", results[0].snippet)
    }

    // -- URL extraction tests -----------------------------------------------------

    @Test
    fun extractUrl_ddgRedirect_decodesUtf8() {
        val html = buildHtml(
            result(
                num = 1,
                href = "//duckduckgo.com/l/?uddg=https%3A%2F%2Fzh.wikipedia.org%2Fwiki%2F%E4%BD%A0%E5%A5%BD&amp;rut=xyz",
                title = "你好 - Wikipedia",
                snippet = "UTF-8 test."
            )
        )
        val results = scraper.parseResults(html, maxResults = 5)
        assertEquals(1, results.size)
        assertEquals("https://zh.wikipedia.org/wiki/你好", results[0].url)
    }

    @Test
    fun extractUrl_plusInRedirect_decodesToSpace() {
        val html = buildHtml(
            result(
                num = 1,
                href = "//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fsearch%3Fq%3Dhello+world&amp;rut=abc",
                title = "Example Search",
                snippet = "Plus-to-space test."
            )
        )
        val results = scraper.parseResults(html, maxResults = 5)
        assertEquals(1, results.size)
        assertEquals("https://example.com/search?q=hello world", results[0].url)
    }

    // -- CAPTCHA detection tests --------------------------------------------------

    @Test
    fun captchaRegex_detectsAnomalyModal() {
        val html = """
            <html><body>
            <div class="anomaly-modal__mask">
                <div class="anomaly-modal__title">Unfortunately, bots use DuckDuckGo too.</div>
            </div>
            </body></html>
        """.trimIndent()
        val regex = Regex("""anomaly-modal|challenge-form|Unfortunately.*bots""")
        assertTrue(regex.containsMatchIn(html))
    }

    @Test
    fun captchaRegex_detectsChallengeForm() {
        val html = """<form id="challenge-form" action="//duckduckgo.com/anomaly.js" method="POST">"""
        val regex = Regex("""anomaly-modal|challenge-form|Unfortunately.*bots""")
        assertTrue(regex.containsMatchIn(html))
    }

    // -- Pagination / vqd tests ---------------------------------------------------

    @Test
    fun vqdRegex_extractsTokenFromNextPageForm() {
        val html = """
            <form class="next_form" action="/lite/" method="post">
                <input type="hidden" name="q" value="hello world">
                <input type="hidden" name="s" value="10">
                <input type="hidden" name="vqd" value="4-303532065876549868505813218373180541363">
            </form>
        """.trimIndent()

        val vqdRegex = Regex("""name="vqd"\s+value="([^"]*)"""")
        val match = vqdRegex.find(html)
        assertNotNull("vqd token should be found", match)
        assertEquals("4-303532065876549868505813218373180541363", match!!.groupValues[1])
    }

    @Test
    fun vqdRegex_noNextPage_returnsNull() {
        val html = "<html><body>No pagination here.</body></html>"
        val vqdRegex = Regex("""name="vqd"\s+value="([^"]*)"""")
        assertNull(vqdRegex.find(html))
    }

    @Test
    fun offsetRegex_extractsFromNextPageForm() {
        val html = """
            <form class="next_form" action="/lite/" method="post">
                <input type="hidden" name="s" value="20">
            </form>
        """.trimIndent()

        val offsetRegex = Regex("""name="s"\s+value="(\d+)"""")
        val match = offsetRegex.find(html)
        assertNotNull("offset should be found", match)
        assertEquals("20", match!!.groupValues[1])
    }

    // -- SearchResponse error type tests ------------------------------------------

    @Test
    fun searchResponse_errorTypes_areDistinct() {
        // Verify the three error types are distinct enum values.
        val types = DuckDuckGoScraper.SearchErrorType.values()
        assertEquals(3, types.size)
        assertTrue(types.toSet().size == 3)
    }

    @Test
    fun searchResponse_success_holdsResults() {
        val results = listOf(
            DuckDuckGoScraper.WebResult("Title", "https://example.com", "Snippet")
        )
        val response = DuckDuckGoScraper.SearchResponse.Success(results)
        assertEquals(1, response.results.size)
        assertEquals("Title", response.results[0].title)
    }

    @Test
    fun searchResponse_error_holdsTypeAndMessage() {
        val response = DuckDuckGoScraper.SearchResponse.Error(
            DuckDuckGoScraper.SearchErrorType.CAPTCHA,
            "Bot detection triggered."
        )
        assertEquals(DuckDuckGoScraper.SearchErrorType.CAPTCHA, response.type)
        assertEquals("Bot detection triggered.", response.message)
    }

    // -- helpers ------------------------------------------------------------------

    private fun buildHtml(vararg rows: String): String = """
        <html>
        <body>
        <table border="0"></table>
        <table border="0">
        ${rows.joinToString("\n")}
        </table>
        </body>
        </html>
    """.trimIndent()

    private fun result(num: Int, href: String, title: String, snippet: String): String = """
        <tr>
            <td valign="top">$num.&nbsp;</td>
            <td><a rel="nofollow" href="$href" class='result-link'>$title</a></td>
        </tr>
        <tr>
            <td>&nbsp;&nbsp;&nbsp;</td>
            <td class='result-snippet'>$snippet</td>
        </tr>
        <tr>
            <td>&nbsp;&nbsp;&nbsp;</td>
            <td><span class='link-text'>example.com</span></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td>&nbsp;</td>
        </tr>
    """.trimIndent()
}
