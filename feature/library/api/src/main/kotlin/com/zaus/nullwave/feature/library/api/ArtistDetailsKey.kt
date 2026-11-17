package com.zaus.nullwave.feature.library.api

import androidx.navigation3.runtime.NavKey
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import kotlinx.serialization.Serializable

/** Both Artists and Search open this entry with the identity from their catalog result. */
@Serializable
data class ArtistDetailsKey(val artistId: ArtistId) : NavKey
