package com.zaus.nullwave.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.data.ScanOutcome
import com.zaus.nullwave.core.data.ScanProgress
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Scaffolding, not screens. The Songs / Artists / Albums / Playlists / Folders tabs go here, plus the
 * detail screens.
 *
 * Note what these signatures do *not* contain: no `NavKey`, no back stack, no navigator. A screen takes
 * lambdas and knows nothing about where they go, which is what lets it be previewed and tested with no
 * navigation wired up. The keys live in `LibraryModule`, at the navigation boundary.
 *
 * It also takes no repository and no scanner - [LibraryContent] owns those and passes down plain values,
 * which is what keeps this previewable.
 *
 * ## The scan readout is temporary
 *
 * [trackCount] and [lastScan] are rendered as text so the scan can be verified **in the app** rather than
 * through `adb` and a log line - that is `DATA.md`'s Phase 0 checkpoint. Two consecutive runs should show
 * the second reporting `+0 ~0 -0`; anything else means the incremental diff is not comparing equal and
 * every launch rewrites the whole table. It goes when the real Songs list lands.
 */
@Composable
fun LibraryScreen(
    onPlayAll: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackCount: Long? = null,
    lastScan: ScanOutcome? = null,
    scanProgress: ScanProgress? = null,
) {
    val colors = NullWaveTheme.colors

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs),
    ) {
        // Placeholder affordance: taps through to a detail screen so the navigation wiring is
        // verifiable before the real tabs exist.
        Text(
            text = "LIBRARY",
            style = NullWaveTheme.typography.h1,
            color = colors.textPrimary,
            modifier = Modifier.clickable { onAlbumClick(PlaceholderAlbumId) },
        )

        // Stands in for artboard 42 until that screen has a component. Only non-null once a scan has
        // outlasted the threshold, so a fast scan shows nothing here at all.
        scanProgress?.let { p ->
            NullWaveMicroLabel(
                text = when (p) {
                    is ScanProgress.Reading -> "reading media store…"
                    is ScanProgress.Indexing ->
                        "indexing ${p.processed} / ${p.total} · ${p.currentFile}"
                    is ScanProgress.Writing ->
                        "writing +${p.inserting} ~${p.updating} -${p.removing} · " +
                            "+${p.newAlbums} albums · +${p.newArtists} artists"
                    is ScanProgress.Finished -> "done"
                },
                color = colors.primary,
            )
        }

        NullWaveMicroLabel(
            text = when (trackCount) {
                null -> "reading library…"
                0L -> "no tracks"
                else -> "$trackCount tracks"
            },
            color = colors.textSecondary,
        )

        // The log line DATA.md's checkpoint asks for, on screen instead of in logcat.
        lastScan?.let { outcome ->
            NullWaveMicroLabel(
                text = when (outcome) {
                    is ScanOutcome.PermissionDenied -> "scan refused · no permission"
                    is ScanOutcome.Completed ->
                        "+${outcome.inserted} ~${outcome.updated} -${outcome.removed} · " +
                            "${outcome.total} total · ${outcome.elapsedMillis} ms"
                },
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "ALBUM $albumId",
            style = NullWaveTheme.typography.h1,
            color = NullWaveTheme.colors.textPrimary,
            modifier = Modifier.clickable(onClick = onBack),
        )
    }
}

/** Stand-in until the library lists real albums. */
private const val PlaceholderAlbumId = 42L

@Preview(name = "Library · scanned", showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    NullWaveTheme {
        LibraryScreen(
            onPlayAll = {},
            onAlbumClick = {},
            trackCount = 1_284,
            lastScan = ScanOutcome.Completed(
                inserted = 1_284,
                updated = 0,
                removed = 0,
                total = 1_284,
                elapsedMillis = 412,
            ),
        )
    }
}

@Preview(name = "Library · reading", showBackground = true)
@Composable
private fun LibraryScreenLoadingPreview() {
    NullWaveTheme {
        LibraryScreen(onPlayAll = {}, onAlbumClick = {})
    }
}
