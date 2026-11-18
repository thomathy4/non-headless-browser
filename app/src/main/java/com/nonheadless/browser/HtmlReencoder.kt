package com.nonheadless.browser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI

/**
 * Converts script elements and inline event handlers to inspectable HTML metadata.
 * This is not a general-purpose HTML sanitizer or a guarantee that all web behavior is inert.
 */
internal object HtmlReencoder {
    private data class Record(
        val id: String, val kind: String, val origin: String,
        val resource: String?, val summary: String
    )

    fun reencode(rawHtml: String, pageUrl: String?): String {
        val document = Jsoup.parse(rawHtml, pageUrl.orEmpty())
        document.outputSettings().prettyPrint(false)
        val records = mutableListOf<Record>()
        var counter = 0

        // Preserve fallback markup whether Jsoup initially represented it as raw text
        // (scripting-enabled noscript parsing) or as ordinary child elements.
        // Replace the noscript element before attaching parsed nodes so the final DOM
        // is an ordinary container and can be traversed consistently.
        document.select("noscript").toList().forEach { fallback ->
            val fallbackMarkup = if (fallback.childNodes().all { it is org.jsoup.nodes.TextNode }) {
                fallback.childNodes().joinToString("") { (it as org.jsoup.nodes.TextNode).wholeText }
            } else {
                fallback.html()
            }
            val fallbackNodes = Parser.parseFragment(fallbackMarkup, document.body(), pageUrl.orEmpty())
            counter += 1
            val id = "js-$counter"
            val replacement = Element("div")
                .attr("id", id)
                .addClass("js-reencoded-noscript")
                .attr("data-js-reencoded", "noscript")
            replacement.appendElement("small").text("[JS Abstraction #$counter — NoScript Fallback]")
            fallback.replaceWith(replacement)
            replacement.appendChildren(fallbackNodes)
            records += Record(
                id, "NoScript Fallback", "noscript", null,
                "${replacement.text().length} chars static fallback"
            )
        }

        document.select("script").toList().forEach { script ->
            counter += 1
            val id = "js-$counter"
            val rawSource = script.attr("src").trim()
            val scriptType = script.attr("type").ifBlank { "text/javascript" }
            val replacement = Element("aside")
                .attr("id", id)
                .addClass("js-reencoded-node")
                .attr("data-js-reencoded", if (rawSource.isBlank()) "inline" else "external")
                .attr("data-js-type", scriptType)

            if (rawSource.isNotBlank()) {
                val resolved = resolveScriptUrl(rawSource, pageUrl)
                replacement.attr("data-js-src", resolved)
                val paragraph = replacement.appendElement("p")
                paragraph.appendElement("strong").text("[JS Abstraction #$counter — External Script] ")
                paragraph.appendElement("a").attr("href", resolved)
                    .attr("rel", "nofollow noopener").text(resolved)
                paragraph.appendElement("code").text(" (raw: $rawSource, type: $scriptType)")
                records += Record(id, "External Script", "script[src]", resolved, "type=$scriptType")
            } else {
                val source = script.data()
                replacement.attr("data-js-length", source.length.toString())
                val details = replacement.appendElement("details")
                details.appendElement("summary")
                    .text("[JS Abstraction #$counter — Inline Script] (${source.length} chars, type: $scriptType)")
                details.appendElement("pre").appendElement("code").text(source)
                records += Record(id, "Inline Script", "script[inline]", null, "$scriptType | ${source.length} chars")
            }
            script.replaceWith(replacement)
        }

        document.getAllElements().forEach { element ->
            val eventAttributes = element.attributes().asList()
                .filter { it.key.lowercase().startsWith("on") }
            if (eventAttributes.isNotEmpty()) {
                eventAttributes.forEach { attribute ->
                    counter += 1
                    val eventName = attribute.key.lowercase().removePrefix("on")
                    val id = "js-$counter"
                    element.attr("data-js-event-$eventName", attribute.value)
                    element.removeAttr(attribute.key)
                    records += Record(
                        id, "Event Attribute", "<${element.normalName()} ${attribute.key}>",
                        null, attribute.value.take(120)
                    )
                }
                element.attr("data-js-reencoded", "attribute")
            }
        }

        document.select("noscript").toList().forEach { fallback ->
            counter += 1
            val id = "js-$counter"
            fallback.tagName("div").attr("id", id).addClass("js-reencoded-noscript")
                .attr("data-js-reencoded", "noscript")
            fallback.prependElement("small").text("[JS Abstraction #$counter — NoScript Fallback]")
            records += Record(id, "NoScript Fallback", "noscript", null, "${fallback.text().length} chars static fallback")
        }

        document.select("script").remove()
        document.body().appendChild(buildManifest(records, pageUrl))
        return document.outerHtml()
    }

    private fun resolveScriptUrl(rawSource: String, pageUrl: String?): String =
        try {
            if (pageUrl.isNullOrBlank() || rawSource.startsWith("data:", true)) rawSource
            else URI(pageUrl).resolve(rawSource).toString()
        } catch (_: Exception) { rawSource }

    private fun buildManifest(records: List<Record>, pageUrl: String?): Element {
        val section = Element("section").attr("id", "js-provenance-manifest")
            .attr("data-js-reencoded", "manifest").attr("data-source-url", pageUrl.orEmpty())
        section.appendElement("hr")
        section.appendElement("h2").text("Re-encoded JavaScript Provenance Manifest (${records.size} items)")
        section.appendElement("p").text(
            "Scripts and inline event handlers were converted to HTML metadata. This is not a complete HTML security sanitizer."
        )
        val table = section.appendElement("table").attr("border", "1")
            .attr("cellpadding", "4").attr("cellspacing", "0")
        val header = table.appendElement("thead").appendElement("tr")
        listOf("Node ID", "Abstraction Type", "Origin", "Linked JS Resource / Summary")
            .forEach { header.appendElement("th").text(it) }
        val body = table.appendElement("tbody")
        if (records.isEmpty()) {
            body.appendElement("tr").appendElement("td").attr("colspan", "4")
                .text("No embedded or external JavaScript detected on this page.")
        } else {
            records.forEach { record ->
                val row = body.appendElement("tr")
                row.appendElement("td").appendElement("a").attr("href", "#${record.id}").text(record.id)
                row.appendElement("td").text(record.kind)
                row.appendElement("td").appendElement("code").text(record.origin)
                val cell = row.appendElement("td")
                if (record.resource != null) {
                    cell.appendElement("a").attr("href", record.resource)
                        .attr("rel", "nofollow noopener").text(record.resource)
                } else cell.text(record.summary)
            }
        }
        return section
    }
}
