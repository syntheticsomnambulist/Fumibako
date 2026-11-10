package dev.aiharness.core.media

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileFactsTest {
    @Test
    fun recordingDatesAreReadAsPhonesWriteThem() {
        assertEquals(java.time.Instant.parse("2026-11-03T14:22:33Z").toEpochMilli(), FileFacts.recordedAt("20261103T142233.000Z"))
        assertNull(FileFacts.recordedAt("19040101T000000.000Z"), "a placeholder")
        assertNull(FileFacts.recordedAt(null))
        assertNull(FileFacts.recordedAt("sometime"))
    }

    @Test
    fun namesAndShapes() {
        assertEquals("beach walk", FileFacts.titleFromName("beach walk.mp4"))
        assertNull(FileFacts.titleFromName("1000004321.mp4"), "a picker's number says nothing")
        assertEquals(1080 to 1920, FileFacts.shownSize(1920, 1080, 90), "turned on its side")
        assertEquals(1920 to 1080, FileFacts.shownSize(1920, 1080, 0))
        assertNull(FileFacts.shownSize(0, 1080, 0))
    }

    @Test
    fun yearsAndTags() {
        assertEquals(1987, FileFacts.year("1987"))
        assertEquals(2024, FileFacts.year("2024-06-01"))
        assertNull(FileFacts.year("0"))
        assertNull(FileFacts.text("<unknown>"))
        assertEquals("Pavement", FileFacts.text(" Pavement "))
    }
}
