package com.nonheadless.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.jsoup.Jsoup

class LocalWebStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun repeatedSnapshotsKeepSiteAndDocumentIdentityButCreateNewRevisions() {
        val store = LocalWebStore(temporaryFolder.newFolder("local-web"))
        val first = store.saveRenderedSnapshot("https://Example.com/guide", "<html><head><title>Guide</title></head><body>one</body></html>")
        val second = store.saveRenderedSnapshot("https://example.com/guide", "<html><head><title>Guide</title></head><body>two</body></html>")

        assertEquals(first.siteId, second.siteId)
        assertEquals(first.documentId, second.documentId)
        assertNotEquals(first.revisionId, second.revisionId)
        assertTrue(first.file.isFile)
        assertTrue(second.file.isFile)
        assertNotEquals(first.sha256, second.sha256)
    }

    @Test
    fun eachDomainGetsItsOwnCanonicalSiteIndex() {
        val root = temporaryFolder.newFolder("local-web")
        val store = LocalWebStore(root)
        val one = store.saveRenderedSnapshot("https://one.example/", "<html><body>one</body></html>")
        val two = store.saveRenderedSnapshot("https://two.example/", "<html><body>two</body></html>")

        assertNotEquals(one.siteId, two.siteId)
        val rootIndex = Jsoup.parse(java.io.File(root, "index.html"), "UTF-8")
        assertTrue(rootIndex.select("a[href]").any { it.text() == "one.example" })
        assertTrue(rootIndex.select("a[href]").any { it.text() == "two.example" })
    }

    @Test
    fun savedRepresentationExplicitlyDoesNotClaimAnOriginalReceipt() {
        val store = LocalWebStore(temporaryFolder.newFolder("local-web"))
        val saved = store.saveRenderedSnapshot("https://example.com/", "<html><body>captured</body></html>")
        val record = Jsoup.parse(saved.file, "UTF-8")
        assertEquals("rendered-dom-snapshot", record.selectFirst("meta[name=local-representation-kind]")?.attr("content"))
        assertEquals("not-an-original-http-receipt", record.selectFirst("meta[name=local-receipt-status]")?.attr("content"))
        assertTrue(record.text().contains("response body and response headers are not represented as received"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHttpSourceUrls() {
        LocalWebStore(temporaryFolder.newFolder("local-web"))
            .saveRenderedSnapshot("file:///etc/passwd", "<html><body>x</body></html>")
    }

    @Test
    fun localPathResolverRejectsTraversal() {
        val store = LocalWebStore(temporaryFolder.newFolder("local-web"))
        assertFalse(java.io.File(temporaryFolder.root, "escape.html").exists())
        assertEquals(null, store.interceptLocalRequest("/../../escape.html"))
    }
}
