package com.zaus.nullwave.feature.library.api

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumDetailsKeyTest {
    @Test
    fun `restoring navigation keys preserves provider volume and grouped Download identity`() {
        val stack = NavBackStack<NavKey>(
            LibraryKey,
            AlbumDetailsKey(AlbumId("external_primary:12")),
            AlbumDetailsKey(AlbumId("card:12")),
            AlbumDetailsKey(AlbumId("external_primary:folder/Download")),
        )
        val serializer = NavBackStackSerializer(NavKeySerializer<NavKey>())
        val saved = Json.encodeToString(serializer, stack)
        assertEquals(stack.toList(), Json.decodeFromString(serializer, saved).toList())
    }
}
