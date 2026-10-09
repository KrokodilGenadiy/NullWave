package com.zaus.nullwave.core.designsystem.icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Icons - Yellow", group = "Icons", widthDp = 360, heightDp = 1180)
@Composable
private fun YellowIconsPreview() = IconSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Icons - Crimson", group = "Icons", widthDp = 360, heightDp = 1180)
@Composable
private fun CrimsonIconsPreview() = IconSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Icons - Dive", group = "Icons", widthDp = 360, heightDp = 1180)
@Composable
private fun DiveIconsPreview() = IconSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Icons - RTL", group = "Icons", widthDp = 360, heightDp = 1180)
@Composable
private fun RtlIconsPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        IconSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Preview(name = "Icons - Large labels", group = "Icons", widthDp = 360, heightDp = 1180, fontScale = 2f)
@Composable
private fun LargeLabelsPreview() = IconSheet(NullWaveColorVariant.Cyberpunk)

/** Preview-only catalogue. Labels are not production accessibility descriptions. */
@Composable
private fun IconSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        val colors = NullWaveTheme.colors
        val spacing = NullWaveTheme.spacing
        Surface(color = colors.bg) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Text("ICONS / ${variant.name}", style = NullWaveTheme.typography.h1)
                Text(
                    "48 icons · 24 dp · default / accent",
                    style = NullWaveTheme.typography.mono,
                    color = colors.textSecondary,
                )
                IconGroups.forEach { (title, icons) ->
                    Text(title, style = NullWaveTheme.typography.h2)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        icons.forEach { (label, icon) ->
                            Column(
                                modifier = Modifier.width(96.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                    NullWaveIcon(icon, contentDescription = null)
                                    NullWaveIcon(icon, contentDescription = null, tint = colors.primary)
                                }
                                Text(
                                    label,
                                    style = NullWaveTheme.typography.caption,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                Text("24 / 32 dp · Favourite", style = NullWaveTheme.typography.caption)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                    NullWaveIcon(NullWaveIcons.Favourite, contentDescription = null, tint = colors.textSecondary)
                    NullWaveIcon(NullWaveIcons.Favourite, contentDescription = null, modifier = Modifier.size(32.dp))
                    NullWaveIcon(NullWaveIcons.FavouriteFilled, contentDescription = null)
                    NullWaveIcon(NullWaveIcons.FavouriteFilled, contentDescription = null, modifier = Modifier.size(32.dp))
                }
                Text("Без подписей / Unlabelled", style = NullWaveTheme.typography.caption)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    IconGroups.forEach { (_, icons) ->
                        icons.forEach { (_, icon) -> NullWaveIcon(icon, contentDescription = null) }
                    }
                }
            }
        }
    }
}

// Preview metadata only; action descriptions belong to the consuming feature.
private val IconGroups = listOf(
    "Transport" to listOf(
        "Play" to NullWaveIcons.Play,
        "Pause" to NullWaveIcons.Pause,
        "Previous" to NullWaveIcons.Previous,
        "Next" to NullWaveIcons.Next,
        "Shuffle" to NullWaveIcons.Shuffle,
        "Repeat" to NullWaveIcons.Repeat,
    ),
    "Player actions" to listOf(
        "Favourite" to NullWaveIcons.Favourite,
        "Favourite Filled" to NullWaveIcons.FavouriteFilled,
        "Queue" to NullWaveIcons.Queue,
        "Add To Queue" to NullWaveIcons.AddToQueue,
        "Lyrics" to NullWaveIcons.Lyrics,
        "Info" to NullWaveIcons.Info,
        "Output" to NullWaveIcons.Output,
        "Waveform" to NullWaveIcons.Waveform,
    ),
    "Navigation and chrome" to listOf(
        "Menu" to NullWaveIcons.Menu,
        "Back" to NullWaveIcons.Back,
        "Forward" to NullWaveIcons.Forward,
        "Collapse" to NullWaveIcons.Collapse,
        "Expand" to NullWaveIcons.Expand,
        "Close" to NullWaveIcons.Close,
        "More" to NullWaveIcons.More,
        "Check" to NullWaveIcons.Check,
    ),
    "Library" to listOf(
        "Songs" to NullWaveIcons.Songs,
        "Albums" to NullWaveIcons.Albums,
        "Artists" to NullWaveIcons.Artists,
        "Playlists" to NullWaveIcons.Playlists,
        "Folders" to NullWaveIcons.Folders,
        "Grid View" to NullWaveIcons.GridView,
        "List View" to NullWaveIcons.ListView,
        "Search" to NullWaveIcons.Search,
    ),
    "Editing" to listOf(
        "Create" to NullWaveIcons.Create,
        "Rename" to NullWaveIcons.Rename,
        "Delete" to NullWaveIcons.Delete,
        "Share" to NullWaveIcons.Share,
        "Reorder" to NullWaveIcons.Reorder,
        "Sort" to NullWaveIcons.Sort,
        "Filter" to NullWaveIcons.Filter,
        "Rescan" to NullWaveIcons.Rescan,
    ),
    "Destinations and status" to listOf(
        "Settings" to NullWaveIcons.Settings,
        "Equalizer" to NullWaveIcons.Equalizer,
        "Now Playing" to NullWaveIcons.NowPlaying,
        "Sleep Timer" to NullWaveIcons.SleepTimer,
        "Duration" to NullWaveIcons.Duration,
        "Recent" to NullWaveIcons.Recent,
        "Output Device" to NullWaveIcons.OutputDevice,
        "Storage" to NullWaveIcons.Storage,
        "Permission" to NullWaveIcons.Permission,
        "Warning" to NullWaveIcons.Warning,
    ),
)
