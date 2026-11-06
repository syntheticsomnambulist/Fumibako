package dev.aiharness.core.media

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfInfoTest {
    private fun pdf(vararg body: String) = ("%PDF-1.7\n" + body.joinToString("\n") + "\n%%EOF").toByteArray(Charsets.ISO_8859_1)

    @Test
    fun theDocumentInformationGivesTitleAndAuthor() {
        val bytes = pdf(
            "1 0 obj << /Type /Catalog /Outlines 2 0 R >> endobj",
            "2 0 obj << /Title (Chapter One) >> endobj",
            "7 0 obj << /Title (Night Market \\(Stalls\\)) /Author (Ana Ruiz) /Producer (Writer) >> endobj",
            "trailer << /Root 1 0 R /Info 7 0 R >>",
        )
        assertEquals(PdfInfo.Info("Night Market (Stalls)", "Ana Ruiz"), PdfInfo.of(bytes), "the trailer's, not a bookmark's")
    }

    @Test
    fun unicodeTitlesAreRead() {
        // "文箱" in UTF-16 with its byte-order mark, as a hex string.
        val bytes = pdf("3 0 obj << /Title <FEFF6587 7BB1> /Author (Sam) >> endobj", "trailer << /Info 3 0 R >>")
        assertEquals("文箱", PdfInfo.of(bytes).title)
    }

    @Test
    fun xmpIsTheFallback() {
        val xmp = "<x:xmpmeta><rdf:RDF><rdf:Description><dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">Tea &amp; Time</rdf:li></rdf:Alt></dc:title>" +
            "<dc:creator><rdf:Seq><rdf:li>Mira</rdf:li></rdf:Seq></dc:creator></rdf:Description></rdf:RDF></x:xmpmeta>"
        assertEquals(PdfInfo.Info("Tea & Time", "Mira"), PdfInfo.of(pdf("5 0 obj << /Type /Metadata >> stream", xmp, "endstream endobj")))
    }

    @Test
    fun titlesThatOnlyNameTheFileAreLeftOut() {
        assertNull(PdfInfo.of(pdf("3 0 obj << /Title (Microsoft Word - report.docx) >> endobj", "trailer << /Info 3 0 R >>")).title)
        assertNull(PdfInfo.of(pdf("3 0 obj << /Title (untitled) >> endobj", "trailer << /Info 3 0 R >>")).title)
        assertNull(PdfInfo.of(pdf("3 0 obj << /Title (market-map) >> endobj", "trailer << /Info 3 0 R >>"), "market-map.pdf").title)
        assertEquals(PdfInfo.Info(null, null), PdfInfo.of("not a pdf".toByteArray()))
    }
}
