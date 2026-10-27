package com.zaus.nullwave.core.media.model

import com.zaus.nullwave.core.media.model.track.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaIdentityTest {
    @Test
    fun `track identity has no consumer-visible source format requirement`() {
        for (opaque in listOf("primary:1", "usb-volume:1", "future-source/item/1", "Трек 1", " id ")) {
            assertEquals(opaque, TrackId(opaque).value)
        }
    }

    @Test
    fun `blank identities are rejected without treating different sources as equal`() {
        for (blank in listOf("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException::class.java) { TrackId(blank) }
        }
        assertNotEquals(TrackId("primary:1"), TrackId("usb-volume:1"))
    }

    @Test
    fun `artwork identity and revision are opaque and jointly determine the reference`() {
        val artwork = ArtworkRef("source/cover", "revision:1")
        assertEquals(artwork, ArtworkRef("source/cover", "revision:1"))
        assertNotEquals(artwork, artwork.copy(key = "other/cover"))
        assertNotEquals(artwork, artwork.copy(revision = "revision:2"))
        assertEquals("revision:1", artwork.revision)
    }

    @Test
    fun `blank artwork key or revision is rejected through constructor and copy`() {
        val artwork = ArtworkRef("source/cover", "revision")
        for (blank in listOf("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException::class.java) { ArtworkRef(blank, "revision") }
            assertThrows(IllegalArgumentException::class.java) { ArtworkRef("source/cover", blank) }
            assertThrows(IllegalArgumentException::class.java) { artwork.copy(key = blank) }
            assertThrows(IllegalArgumentException::class.java) { artwork.copy(revision = blank) }
        }
    }
}
