package com.zaus.nullwave.feature.library.api

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDetailsKeyTest {
    @Test
    fun `restoring navigation keys preserves order and the same provider ID on different volumes`() {
        val stack = NavBackStack<NavKey>(
            LibraryKey,
            ArtistDetailsKey(ArtistId("external_primary:42")),
            ArtistDetailsKey(ArtistId("card:42")),
            ArtistDetailsKey(ArtistId("usb:42")),
        )
        val serializer = NavBackStackSerializer(NavKeySerializer<NavKey>())

        val restored = Json.decodeFromString(serializer, Json.encodeToString(serializer, stack)).toList()

        assertEquals(stack.toList(), restored)
        assertEquals(LibraryKey, restored.first())
        assertEquals(
            listOf("external_primary:42", "card:42", "usb:42"),
            restored.drop(1).map { (it as ArtistDetailsKey).artistId.value },
        )
    }
}
