package com.zaus.nullwave.feature.library.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The library feature's destinations.
 *
 * These are the app's default landing screen plus the detail screens the player links back to, so
 * this is the api module other features and `:app` will depend on most.
 *
 * They implement [NavKey] directly rather than sharing a sealed parent: Kotlin requires every
 * subtype of a sealed type to live in the same module, which would force all keys back into one
 * shared file. Navigation 3 only needs [NavKey], and back-stack persistence still works because
 * `NavKeySerializer` resolves each key's serializer reflectively by class name.
 */
@Serializable
data object LibraryKey : NavKey

@Serializable
data class AlbumDetailKey(val albumId: Long) : NavKey

@Serializable
data class ArtistDetailKey(val artistId: Long) : NavKey

@Serializable
data class PlaylistDetailKey(val playlistId: Long) : NavKey

@Serializable
data class FolderDetailKey(val path: String) : NavKey
