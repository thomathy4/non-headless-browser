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
}
