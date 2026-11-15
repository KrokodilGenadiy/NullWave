package com.zaus.nullwave.feature.library.api

import androidx.navigation3.runtime.NavKey
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import kotlinx.serialization.Serializable

/** Both Albums and Search open this entry with the identity from their catalog result. */
@Serializable
data class AlbumDetailsKey(val albumId: AlbumId) : NavKey
