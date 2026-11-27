package com.nonheadless.browser

import android.webkit.WebResourceResponse
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * HTML-native persistence for the browser's own local web.
 *
 * This first vertical slice persists rendered-DOM snapshots as derived representations.
 * It does NOT claim to capture original HTTP response bodies or immutable remote receipts.
 * Every directory entry used by the local web is an HTML document; no database is introduced.
 */
internal class LocalWebStore(private val root: File) {
    data class SavedRepresentation(
        val siteId: String,
        val documentId: String,
        val revisionId: String,
        val file: File,
        val sha256: String
    )

    @Synchronized
    fun saveRenderedSnapshot(sourceUrl: String, renderedHtml: String): SavedRepresentation {
        require(renderedHtml.isNotBlank()) { "Cannot save an empty rendered document" }
        val source = normalizeHttpUrl(sourceUrl)
        val host = URI(source).host.lowercase()
        val sitesDir = File(root, "sites").apply { mkdirs() }

        val siteDir = findSite(sitesDir, host) ?: File(sitesDir, "site-${UUID.randomUUID()}").apply { mkdirs() }
        val siteIndex = File(siteDir, "index.html")
        val siteId = readMeta(siteIndex, "local-site-id") ?: UUID.randomUUID().toString()

        val docsDir = File(siteDir, "documents").apply { mkdirs() }
        val documentDir = findDocument(docsDir, source)
            ?: File(docsDir, "document-${UUID.randomUUID()}").apply { mkdirs() }
        val documentIndex = File(documentDir, "index.html")
        val documentId = readMeta(documentIndex, "local-document-id") ?: UUID.randomUUID().toString()
        val revisionId = UUID.randomUUID().toString()
        val createdAt = Instant.now().toString()
        val snapshotBytes = renderedHtml.toByteArray(Charsets.UTF_8)
        val digest = sha256(snapshotBytes)

        val revision = Jsoup.parse(renderedHtml, source)
        revision.outputSettings().prettyPrint(true)
        putMeta(revision, "local-representation-kind", "rendered-dom-snapshot")
        putMeta(revision, "local-site-id", siteId)
        putMeta(revision, "local-document-id", documentId)
        putMeta(revision, "local-revision-id", revisionId)
        putMeta(revision, "local-source-url", source)
        putMeta(revision, "local-representation-created-at", createdAt)
        putMeta(revision, "local-representation-sha256", digest)
        putMeta(revision, "local-receipt-status", "not-an-original-http-receipt")
        val provenance = revision.body().appendElement("section")
            .attr("id", "local-representation-provenance")
            .attr("data-record-kind", "rendered-dom-snapshot")
        provenance.appendElement("h2").text("Local representation record")
        provenance.appendElement("dl").apply {
            appendElement("dt").text("Source URL")
            appendElement("dd").text(source)
            appendElement("dt").text("Representation")
            appendElement("dd").text("Rendered DOM snapshot extracted from WebView")
            appendElement("dt").text("Generated")
            appendElement("dd").text(createdAt)
            appendElement("dt").text("SHA-256 of captured rendered HTML before local metadata")
            appendElement("dd").text(digest)
            appendElement("dt").text("Original HTTP receipt")
            appendElement("dd").text("Not captured by this operation; response body and response headers are not represented as received.")
        }

        val revisionsDir = File(documentDir, "revisions").apply { mkdirs() }
        val revisionFile = File(revisionsDir, "$revisionId.html")
        atomicWrite(revisionFile, revision.outerHtml())

        writeDocumentIndex(documentIndex, source, documentId, revisionId, revisionsDir)
        writeSiteIndex(siteIndex, host, siteId, docsDir)
        writeRootIndex(sitesDir)
        return SavedRepresentation(siteId, documentId, revisionId, revisionFile, digest)
    }

    /** Serves only HTML files contained beneath this local-web root for the reserved local origin. */
    fun interceptLocalRequest(path: String): WebResourceResponse? {
        val decoded = try { java.net.URLDecoder.decode(path.ifBlank { "/index.html" }, "UTF-8") }
        catch (_: Exception) { return null }
        if (decoded.split('/').any { it == ".." } || decoded.contains('\\')) return null
        val relative = decoded.removePrefix("/").ifBlank { "index.html" }
        val base = root.canonicalFile
        val target = File(base, relative).canonicalFile
        if (!target.path.startsWith(base.path + File.separator) || !target.isFile) return null
        if (!target.name.endsWith(".html", ignoreCase = true)) return null
        return try { WebResourceResponse("text/html", "UTF-8", target.inputStream()) }
        catch (_: Exception) { null }
    }

    private fun findSite(sitesDir: File, host: String): File? = sitesDir.listFiles()
        ?.filter { it.isDirectory && it.name.startsWith("site-") }
        ?.firstOrNull { readMeta(File(it, "index.html"), "remote-site-host") == host }

    private fun findDocument(docsDir: File, source: String): File? = docsDir.listFiles()
        ?.filter { it.isDirectory && it.name.startsWith("document-") }
        ?.firstOrNull { readMeta(File(it, "index.html"), "local-source-url") == source }

    private fun writeRootIndex(sitesDir: File) {
        val doc = htmlDocument("Local Web")
        doc.body().appendElement("h1").text("Local Web")
        doc.body().appendElement("p").text("Browser-owned HTML records. Only saved representations appear here.")
        val list = doc.body().appendElement("ul")
        sitesDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("site-") }?.sortedBy { it.name }?.forEach { site ->
            val host = readMeta(File(site, "index.html"), "remote-site-host") ?: return@forEach
            list.appendElement("li").appendElement("a")
                .attr("href", "sites/${site.name}/index.html").text(host)
        }
        atomicWrite(File(root, "index.html"), doc.outerHtml())
    }

    private fun writeSiteIndex(file: File, host: String, siteId: String, docsDir: File) {
        val doc = htmlDocument("Local site — $host")
        putMeta(doc, "local-site-id", siteId)
        putMeta(doc, "remote-site-host", host)
        doc.body().appendElement("nav").appendElement("a").attr("href", "../../index.html").text("All local sites")
        doc.body().appendElement("h1").text(host)
        doc.body().appendElement("p").text("Canonical local HTML entry point for this domain.")
        val list = doc.body().appendElement("ul")
        docsDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("document-") }?.sortedBy { it.name }?.forEach { dir ->
            val index = File(dir, "index.html")
            val source = readMeta(index, "local-source-url") ?: return@forEach
            val title = readMeta(index, "local-document-title") ?: source
            list.appendElement("li").appendElement("a")
                .attr("href", "documents/${dir.name}/index.html").text(title)
            list.appendElement("p").text(source)
        }
        atomicWrite(file, doc.outerHtml())
    }

    private fun writeDocumentIndex(file: File, source: String, documentId: String, currentRevision: String, revisionsDir: File) {
        val doc = htmlDocument("Local document")
        putMeta(doc, "local-document-id", documentId)
        putMeta(doc, "local-source-url", source)
        putMeta(doc, "local-document-title", source)
        doc.body().appendElement("nav").appendElement("a").attr("href", "../../index.html").text("Domain home")
        doc.body().appendElement("h1").text(source)
        doc.body().appendElement("p").text("Persistent local document identity: $documentId")
        doc.body().appendElement("p").text("Remote URL is source metadata, not the local identity.")
        doc.body().appendElement("h2").text("Representations and revisions")
        val list = doc.body().appendElement("ol")
        revisionsDir.listFiles()?.filter { it.isFile && it.extension == "html" }?.sortedBy { it.name }?.forEach { revision ->
            val id = revision.nameWithoutExtension
            list.appendElement("li").appendElement("a").attr("href", "revisions/${revision.name}")
                .text(if (id == currentRevision) "Current representation — $id" else "Earlier representation — $id")
        }
        atomicWrite(file, doc.outerHtml())
    }

    private fun htmlDocument(title: String): Document =
        Jsoup.parse("<!doctype html><html><head><meta charset=\"utf-8\"><title></title></head><body></body></html>").also {
            it.title(title)
            it.outputSettings().prettyPrint(true)
        }

    private fun putMeta(doc: Document, name: String, value: String) {
        doc.head().select("meta[name=\"$name\"]").remove()
        doc.head().appendElement("meta").attr("name", name).attr("content", value)
    }

    private fun readMeta(file: File, name: String): String? {
        if (!file.isFile) return null
        return try {
            Jsoup.parse(file, "UTF-8").selectFirst("meta[name=\"$name\"]")?.attr("content")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    private fun normalizeHttpUrl(value: String): String {
        val uri = URI(value.trim()).normalize()
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) { "Only HTTP(S) source URLs can be saved" }
        require(!uri.host.isNullOrBlank()) { "Source URL has no valid host" }
        return URI(uri.scheme.lowercase(), null, uri.host.lowercase(), uri.port, uri.path.ifBlank { "/" }, uri.query, null).toASCIIString()
    }

    private fun atomicWrite(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(content, Charsets.UTF_8)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
