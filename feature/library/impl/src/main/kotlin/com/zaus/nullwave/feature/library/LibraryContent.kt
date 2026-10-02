package com.zaus.nullwave.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaus.nullwave.core.data.LibraryScanner
import com.zaus.nullwave.core.data.ScanOutcome
import com.zaus.nullwave.core.data.ScanProgress
import com.zaus.nullwave.core.data.TrackRepository
import kotlinx.coroutines.delay

/**
 * The library once the audio permission is held: scans, then renders.
 *
 * Sits between `AudioAccessGate` and [LibraryScreen] because neither should own this. The gate decides
 * *whether* the library may read anything; the screen renders what is there. Deciding *when* to scan is a
 * third job, and it is the one that was missing - granting the permission previously showed an empty
 * library forever, because nothing collected [LibraryScanner.scan].
 *
 * ## Two triggers, one scan at a time
 *
 * A scan on entry, and a rescan whenever MediaStore reports a change. They can overlap - a file copy
 * finishing while the first scan runs - and that is safe because `scan()` holds a `Mutex`: the second
 * collection waits rather than racing the first and deleting the rows it just wrote.
 *
 * `mediaStoreChanges()` does not emit on subscribe, so the two triggers never duplicate on launch.
 *
 * ## The scan screen is threshold-gated
 *
 * [ScanProgress] is collected throughout, but [showScanScreen] only becomes true once a scan has been
 * running for [ScanScreenThreshold]. The design's artboard 42 assumes a tag-reading scanner taking tens of
 * seconds; this one reads MediaStore and finishes in hundreds of milliseconds, so showing it
 * unconditionally would flash a full-screen progress view for two frames on every launch - worse than
 * showing nothing. On a large library or a slow device it still appears, which is when it earns its place.
 *
 * ## No ViewModel
 *
 * Same reasoning as the gate - see `LIBRARY.md` §1b. One caveat that will matter: `LaunchedEffect(Unit)`
 * is scoped to this composition, so a configuration change re-runs the entry scan. Cheap and harmless
 * today because the diff makes an unchanged rescan write nothing, but it is the first thing a `ViewModel`
 * would fix, and the reason to revisit the decision when the Songs tab arrives.
 */
@Composable
fun LibraryContent(
    scanner: LibraryScanner,
    repository: TrackRepository,
    onPlayAll: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null until the first emission, which distinguishes "not read yet" from "read, and there are none".
    // The screen needs that difference: zero tracks after a scan is the empty library, artboard 44.
    val trackCount by repository.trackCount().collectAsStateWithLifecycle(initialValue = null)

    var progress by remember { mutableStateOf<ScanProgress?>(null) }
    var showScanScreen by remember { mutableStateOf(false) }

    // Held separately from `progress` rather than derived from it. Deriving it meant the readout vanished
    // the moment a rescan started - `progress` becomes Indexing again, so the last completed scan's
    // result was simply gone until the new one finished.
    var lastOutcome by remember { mutableStateOf<ScanOutcome?>(null) }

    // Shared by both triggers below, so the two collections cannot drift apart in what they record.
    val record: (ScanProgress) -> Unit = { p ->
        progress = p
        if (p is ScanProgress.Finished) lastOutcome = p.outcome
    }

    val isScanning = progress != null && progress !is ScanProgress.Finished

    // The threshold. A separate effect keyed on `isScanning` rather than a timestamp comparison, so the
    // flag flips exactly once per scan and resets the moment one finishes.
    LaunchedEffect(isScanning) {
        if (!isScanning) {
            showScanScreen = false
            return@LaunchedEffect
        }
        delay(ScanScreenThreshold)
        showScanScreen = true
    }

    LaunchedEffect(Unit) {
        scanner.scan().collect(record)
    }

    LaunchedEffect(Unit) {
        // Conflated upstream, so a burst of filesystem events collapses into one rescan. A change
        // arriving mid-scan blocks on the scanner's Mutex rather than racing it.
        scanner.mediaStoreChanges().collect {
            scanner.scan().collect(record)
        }
    }

    // TODO: artboard 42 proper, once it has a component. Rendering the readout below in the meantime
    //  rather than a half-built progress screen - see NOTES.md.
    LibraryScreen(
        trackCount = trackCount,
        lastScan = lastOutcome,
        scanProgress = progress.takeIf { showScanScreen },
        onPlayAll = onPlayAll,
        onAlbumClick = onAlbumClick,
        modifier = modifier,
    )
}

/**
 * How long a scan must run before the design's scan screen is worth showing.
 *
 * 500ms is the usual anti-flicker figure: long enough that a fast scan never flashes a progress view,
 * short enough that a user who waits is told why. Worth re-tuning against a real library on a slow device
 * rather than taken as settled.
 */
private const val ScanScreenThreshold = 500L
