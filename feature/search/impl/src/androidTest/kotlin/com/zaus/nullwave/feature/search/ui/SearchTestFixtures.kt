package com.zaus.nullwave.feature.search.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.search.LibraryAlbumSearchHit
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.library.api.model.search.LibraryTrackSearchHit
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.api.repository.LibrarySearchRepository
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessHost
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

internal fun emptyResults() = LibrarySearchResults(emptyList(), emptyList(), emptyList())
internal fun searchResults(trackCount: Int = 1) = LibrarySearchResults(
    List(trackCount) { LibraryTrackSearchHit(MediaTrack(TrackId("track:$it"), "Orbit $it", "Static Pilgrim", "Transmission", 201_000), "orbit_$it.flac") },
    listOf(LibraryAlbumSearchHit(LibraryAlbum(AlbumId("album:1"), "Transmission", AlbumArtistCredit.Various, 12), listOf("Guest Orbit"))),
    listOf(LibraryArtist(ArtistId("artist:1"), "Static Pilgrim", 41, 3)),
)

/** Root tests fake only the API boundary. Real launcher/registry tests remain with Library's host. */
internal class FakeAccessHost : LibraryAccessHost {
    var access: LibraryAccess = LibraryAccess.Required(AccessAction.Request)
    var requests = 0
    var checks = 0
    var ui by mutableStateOf(LibraryAccessUi(access = access))
    private var publish: ((LibraryAccess) -> Unit)? = null

    @Composable
    override fun Content(onAccessChecked: (LibraryAccess) -> Unit, content: @Composable (LibraryAccessUi) -> Unit) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val callback by rememberUpdatedState(onAccessChecked)
        DisposableEffect(lifecycle) {
            publish = { value ->
                access = value
                ui = ui.copy(access = value, busy = false)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) callback(value)
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) { checks++; publish?.invoke(access) }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer); publish = null }
        }
        content(ui.copy(onAccess = { requests++; ui = ui.copy(busy = true) }, onAskAgain = { requests++ }))
    }

    fun reply(value: LibraryAccess) { checkNotNull(publish)(value) }
}

internal class RootOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

internal class RecordingSearchRepository : LibrarySearchRepository {
    val calls = mutableListOf<LibrarySearchQuery>()
    var active = 0
    var gate: CompletableDeferred<Unit>? = null
    val result = MutableStateFlow<AppResult<LibrarySearchResults, DataError>>(AppResult.Success(searchResults()))
    override fun observeSearch(query: LibrarySearchQuery) = flow {
        calls += query
        active++
        try {
            gate?.await()
            emitAll(result.transformWhile { value ->
                emit(value)
                // Match the API: AccessDenied is terminal, Io remains observable.
                value !is AppResult.Failure || value.error != DataError.AccessDenied
            })
        } finally { active-- }
    }
}

@Composable
internal fun SearchTestTheme(softwareKeyboardOnly: Boolean = false, content: @Composable () -> Unit) {
    val configuration = Configuration(LocalConfiguration.current).apply {
        setLocale(Locale.ENGLISH)
        if (softwareKeyboardOnly) {
            keyboard = Configuration.KEYBOARD_NOKEYS
            hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES
        }
    }
    val context = LocalContext.current
    val localized = remember(context, configuration) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration) {
        NullWaveTheme(effects = NullWaveEffects(reduceMotion = true), content = content)
    }
}
