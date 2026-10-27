package com.zaus.nullwave.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.presentation.LibraryBody
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryState
import com.zaus.nullwave.feature.library.presentation.RefreshState
import kotlinx.coroutines.delay

private val previewTracks = listOf(
    MediaTrack(TrackId("preview:1"), "Low Orbit", "Nullwave", "Transmission", 203_000, sizeBytes = 32_000_000),
    MediaTrack(TrackId("preview:2"), "Очень длинное название трека на дальней орбите", "Необычайно длинное имя исполнителя", "Ночной город", 4_021_000, sizeBytes = 68_000_000),
    MediaTrack(TrackId("preview:3"), "Untitled recording", null, null, null, sizeBytes = 1_250_000),
    MediaTrack(TrackId("preview:4"), "Unknown artist", null, "Off-Grid", 281_000, sizeBytes = 27_000_000),
)

internal class LibraryPreviewStates : PreviewParameterProvider<LibraryState> {
    override val values = sequenceOf(
        LibraryBody.Ready(previewTracks), LibraryBody.Loading,
        LibraryBody.Ready(previewTracks.map { it.copy(sizeBytes = null) }),
        LibraryBody.AccessRequired(AccessAction.Request),
        LibraryBody.AccessRequired(AccessAction.SettingsSuggested),
        LibraryBody.Ready(emptyList()),
        LibraryBody.Failed(UiText.Resource(R.string.library_load_failed)),
        LibraryBody.Ready(previewTracks, RefreshState.Running),
        LibraryBody.Ready(previewTracks, RefreshState.Failed(UiText.Resource(R.string.library_refresh_failed))),
    ).map { LibraryState(LibraryTrackQuery("ru"), it) }
}

@Preview(name = "Library states", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun LibraryPreview(@PreviewParameter(LibraryPreviewStates::class) state: LibraryState) {
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        LibraryScreen(state, onIntent = {}, onAboutClick = {})
    }
}

@Preview(name = "Library large text", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LibraryLargeTextPreview() {
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        LibraryScreen(LibraryState(LibraryTrackQuery("ru"), LibraryBody.Ready(previewTracks)), {}, {})
    }
}

@Preview(name = "Library filters - RU", widthDp = 360, heightDp = 640, locale = "ru")
@Preview(name = "Library filters - EN", widthDp = 360, heightDp = 640, locale = "en")
@Preview(name = "Library filters - Large text", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LibraryFiltersPreview() {
    var query by remember { mutableStateOf(LibraryTrackQuery("ru")) }
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        LibraryScreen(
            LibraryState(query, LibraryBody.Ready(previewTracks)),
            onIntent = { intent ->
                if (intent is LibraryIntent.SortChanged) {
                    query = query.copy(sort = intent.sort, direction = intent.direction)
                }
            },
            onAboutClick = {},
        )
    }
}

/** Interactive-only delay lets the reviewer inspect refresh without a real MediaStore query. */
@Preview(name = "Library refresh without jumps", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun LibraryRefreshPreview() {
    var refreshing by remember { mutableStateOf(false) }
    val tracks = remember {
        (1..40).map { previewTracks[it % previewTracks.size].copy(id = TrackId("preview:$it")) }
    }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(1_500)
            refreshing = false
        }
    }
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        LibraryScreen(
            LibraryState(LibraryTrackQuery("ru"), LibraryBody.Ready(tracks,
                if (refreshing) RefreshState.Running else RefreshState.Idle)),
            onIntent = { if (it == LibraryIntent.Refresh) refreshing = true }, onAboutClick = {},
        )
    }
}
