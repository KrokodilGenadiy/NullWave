package com.zaus.nullwave.feature.library.ui.artistdetails

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistDetails

private val previewDetails = LibraryArtistDetails.create(
    LibraryArtist(ArtistId("preview:1"), "Static Pilgrim", trackCount = 4, albumCount = 2),
    listOf(
        LibraryAlbum(AlbumId("preview:10"), "Carbon Sleep", AlbumArtistCredit.Named("Static Pilgrim"), trackCount = 2),
        LibraryAlbum(AlbumId("preview:11"), "Ночной город", AlbumArtistCredit.Various, trackCount = 14),
    ),
    listOf(
        MediaTrack(TrackId("preview:100"), "Carbon Sleep", "Static Pilgrim", "Carbon Sleep", 182_000),
        MediaTrack(TrackId("preview:101"), "Deadzone Chorus", "Static Pilgrim", "Carbon Sleep", 221_000),
        MediaTrack(TrackId("preview:102"), "Очень длинное название трека на дальней орбите", "Static Pilgrim", "Ночной город", 4_021_000),
        MediaTrack(TrackId("preview:103"), "voice_memo_01", "Static Pilgrim", null, null),
    ),
)

@Preview(name = "Artist details", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun ArtistDetailsPreview() {
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        ArtistDetailsContent(previewDetails, artworkLoader = null, onBack = {}, onAlbumClick = {},
            onPlay = {}, onShuffle = {}, onTrackClick = {}, playbackEnabled = true)
    }
}
