package com.zaus.nullwave.feature.library.data.source

import com.zaus.nullwave.feature.library.api.model.album.AlbumId

/** Fake of the platform seam; repository flow/error/mapping code stays real in these tests. */
internal class FakeMediaStoreAudioSource : MediaStoreAudioSource {
    var granted = true
    var registrationFailure: Exception? = null
    var closeFailure: Exception? = null
    var beforeRegister: (() -> Unit)? = null
    var onRegister: (() -> Unit)? = null
    var read: suspend () -> List<MediaStoreAudioRow> = { emptyList() }
    var reads = 0
    var registrations = 0
    var closes = 0
    var activeObservers = 0
    private val observers = mutableSetOf<() -> Unit>()

    override fun hasReadAccess(): Boolean = granted

    override fun observeChanges(onChanged: () -> Unit): AutoCloseable {
        beforeRegister?.invoke()
        registrationFailure?.let { throw it }
        observers += onChanged
        registrations++
        activeObservers++
        onRegister?.invoke()
        return AutoCloseable {
            check(observers.remove(onChanged)) { "Observer closed twice" }
            activeObservers--
            closes++
            closeFailure?.let { throw it }
        }
    }

    override suspend fun readRows(albumId: AlbumId?): List<MediaStoreAudioRow> {
        check(activeObservers > 0) { "Reading before observing would miss changes" }
        reads++
        return read()
    }

    fun invalidate() = observers.toList().forEach { it() }
}

internal fun audioRow(
    id: Long = 1,
    volume: String? = "external_primary",
    title: String? = "Low Orbit",
    artist: String? = "Nullwave",
    added: Long? = 123,
) = MediaStoreAudioRow(id, volume, title, "track.mp3", artist, "Signals", 180_000, added, albumId = 8, artistId = 4)
