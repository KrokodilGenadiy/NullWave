package com.zaus.nullwave.feature.library.data.source

import com.zaus.nullwave.core.media.model.track.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaStoreTrackAddressTest {
    @Test
    fun `concrete primary and removable volume identities retain their volume and Long id`() {
        assertEquals(MediaStoreTrackAddress("external_primary", 0), MediaStoreTrackAddress.from(TrackId("external_primary:0")))
        assertEquals(MediaStoreTrackAddress("1234-abcd", 42), MediaStoreTrackAddress.from(TrackId("1234-abcd:42")))
        assertEquals(
            MediaStoreTrackAddress("external_primary", Long.MAX_VALUE),
            MediaStoreTrackAddress.from(TrackId("external_primary:${Long.MAX_VALUE}")),
        )
    }

    @Test
    fun `unsupported structure and noncanonical numeric aliases are rejected`() {
        for (value in listOf(
            "no-separator", ":1", "external_primary:", "external_primary:1:2", "external_primary:not-a-number",
            "external_primary:-1", "external_primary:+1", "external_primary:01",
            "external_primary:1.0", "external_primary:1e2", "external_primary: 1",
            "external_primary:1 ", "external_primary:9223372036854775808",
        )) {
            assertNull(value, MediaStoreTrackAddress.from(TrackId(value)))
        }
    }

    @Test
    fun `synthetic volumes and path or query fragments cannot become a resource address`() {
        for (value in listOf(
            "external:1", "internal:1", "external_primary/other:1", "../external_primary:1",
            "external_primary?query:1", "external_primary#fragment:1", "external primary:1",
            "content://media/external_primary/audio/media/1", "external_primary\n:1",
        )) {
            assertNull(value, MediaStoreTrackAddress.from(TrackId(value)))
        }
    }
}
