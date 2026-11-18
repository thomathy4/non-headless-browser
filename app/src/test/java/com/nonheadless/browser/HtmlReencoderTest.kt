package com.nonheadless.browser

import org.jsoup.Jsoup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlReencoderTest {
    @Test
    fun neutralizesInlineAndExternalScripts() {
        val input = """<html><head><script src="/assets/app.js"></script></head><body><script>window.example = 1;</script></body></html>"""
        val output = HtmlReencoder.reencode(input, "https://example.test/page")
        val doc = Jsoup.parse(output)
        assertTrue(doc.select("script").isEmpty())
        assertTrue(output.contains("https://example.test/assets/app.js"))
        assertTrue(output.contains("window.example"))
        assertTrue(doc.select("[data-js-reencoded=external]").size == 1)
        assertTrue(doc.select("[data-js-reencoded=inline]").size == 1)
        assertTrue(doc.select("#js-provenance-manifest").size == 1)
    }

    @Test
    fun convertsInlineEventHandlersToDataAttributes() {
        val output = HtmlReencoder.reencode("""<button onclick="alert('hello')">Press</button>""", "https://example.test")
        val button = Jsoup.parse(output).selectFirst("button")!!
        assertFalse(button.hasAttr("onclick"))
        assertTrue(button.attr("data-js-event-click").contains("alert"))
        assertTrue(button.attr("data-js-reencoded") == "attribute")
    }

    @Test
    fun convertsNoscriptFallbackToOrdinaryDiv() {
        val output = HtmlReencoder.reencode("<noscript><p>Fallback text</p></noscript>", "https://example.test")
        val doc = Jsoup.parse(output)
        assertTrue(doc.select("noscript").isEmpty())
        assertTrue(doc.select(".js-reencoded-noscript p").text().contains("Fallback text"))
    }

    @Test
    fun reencodesScriptsAndEventsInsideNoscriptFallback() {
        val input = """<noscript><p>Fallback</p><script>window.fallback = true;</script><button onclick="runFallback()">Run</button></noscript>"""
        val doc = Jsoup.parse(HtmlReencoder.reencode(input, "https://example.test"))
        val fallback = doc.selectFirst(".js-reencoded-noscript")!!
        assertTrue(fallback.select("p").text().contains("Fallback"))
        assertTrue(fallback.select("script").isEmpty())
        assertTrue(fallback.select("[data-js-reencoded=inline]").size == 1)
        val button = fallback.selectFirst("button")!!
        assertFalse(button.hasAttr("onclick"))
        assertTrue(button.hasAttr("data-js-event-click"))
    }


    @Test
    fun enrichesLinksWithExpandableHistoricalRecords() {
        val input = """<html><head><title>Example</title></head><body><a href="/about">About us</a></body></html>"""
        val output = HtmlReencoder.reencode(input, "https://example.test/index.html")
        val doc = Jsoup.parse(output)
        val record = doc.selectFirst("details.local-link-history")!!
        assertTrue(record.selectFirst("summary")!!.text().contains("About us"))
        assertTrue(record.text().contains("https://example.test/about"))
        val historical = record.selectFirst("details.local-historical-captures")!!
        assertTrue(historical.selectFirst("summary")!!.text() == "Historical captures")
        assertTrue(historical.text().contains("No local historical captures indexed"))
        assertTrue(record.selectFirst("a[data-original-destination]")!!.attr("href") == "https://example.test/about")
    }

    @Test
    fun addsStableLocalPageIdentityAndCaptureMetadata() {
        val input = """<html><body><p>Page</p></body></html>"""
        val first = Jsoup.parse(HtmlReencoder.reencode(input, "https://example.test/page"))
        val second = Jsoup.parse(HtmlReencoder.reencode(input, "https://example.test/page"))
        assertTrue(first.selectFirst("meta[name=local-site-id]")!!.attr("content").isNotBlank())
        assertTrue(first.selectFirst("meta[name=local-document-id]")!!.attr("content").isNotBlank())
        assertTrue(first.selectFirst("meta[name=local-capture-generated-at]")!!.attr("content").isNotBlank())
        assertTrue(first.selectFirst("meta[name=local-site-id]")!!.attr("content") ==
            second.selectFirst("meta[name=local-site-id]")!!.attr("content"))
        assertTrue(first.selectFirst("meta[name=local-document-id]")!!.attr("content") ==
            second.selectFirst("meta[name=local-document-id]")!!.attr("content"))
    }

    @Test
    fun preservesOriginalAnchorAttributesInsideExpandedLinkRecord() {
        val input = """<a href="../next?q=1" target="_blank" rel="nofollow">Next</a>"""
        val doc = Jsoup.parse(HtmlReencoder.reencode(input, "https://example.test/section/page"))
        val record = doc.selectFirst("details.local-link-history")!!
        val originalLink = record.selectFirst("a[data-original-destination]")!!
        assertTrue(originalLink.attr("href") == "https://example.test/next?q=1")
        assertTrue(originalLink.attr("target") == "_blank")
        assertTrue(originalLink.attr("rel").contains("nofollow"))
        assertTrue(record.selectFirst("[data-original-href]")!!.attr("data-original-href") == "../next?q=1")
    }

}
