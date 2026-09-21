package com.zaus.nullwave.core.designsystem.icon

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.zaus.nullwave.core.designsystem.R

/**
 * The icon sheet. Every icon in the app comes from here - no per-screen improvisation.
 *
 * Base set is Material Symbols Outlined, weight 300, grade 0, restyled: mitred caps and joins,
 * curves faceted at 45 degrees where the silhouette survives it. Grid is a 24dp box with a 20dp
 * live area, 2dp stroke (1dp for secondary/dim icons - same glyph, dimmed, never a different
 * drawing), no curve radius below 4dp, and the play triangle is the only filled shape.
 *
 * The drawables are drawn in white and tinted at the call site, so
 * `Icon(painter = NullWaveIcons.painter(NullWaveIcons.Play), contentDescription = null)` picks up
 * `LocalContentColor`.
 *
 * Every icon has to pass the squint test: identifiable at 24dp with its label hidden.
 */
object NullWaveIcons {

    // --- Transport -------------------------------------------------------------------------

    @DrawableRes val Play: Int = R.drawable.ic_play
    @DrawableRes val Pause: Int = R.drawable.ic_pause
    @DrawableRes val Previous: Int = R.drawable.ic_previous
    @DrawableRes val Next: Int = R.drawable.ic_next
    @DrawableRes val Shuffle: Int = R.drawable.ic_shuffle
    @DrawableRes val Repeat: Int = R.drawable.ic_repeat

    // --- Player actions --------------------------------------------------------------------

    /** Angular heart, hollow. Ten vertices, straight segments only - unset state. */
    @DrawableRes val Favourite: Int = R.drawable.ic_favourite

    /** The identical silhouette, filled. Only the fill changes between states. */
    @DrawableRes val FavouriteFilled: Int = R.drawable.ic_favourite_filled

    /** A list with a play arrow at the leading edge. Deliberately not three plain lines. */
    @DrawableRes val Queue: Int = R.drawable.ic_queue
    @DrawableRes val AddToQueue: Int = R.drawable.ic_add_to_queue
    @DrawableRes val Lyrics: Int = R.drawable.ic_lyrics
    @DrawableRes val Info: Int = R.drawable.ic_info
    @DrawableRes val Output: Int = R.drawable.ic_output
    @DrawableRes val Waveform: Int = R.drawable.ic_waveform

    // --- Navigation and chrome -------------------------------------------------------------

    @DrawableRes val Menu: Int = R.drawable.ic_menu
    @DrawableRes val Back: Int = R.drawable.ic_back
    @DrawableRes val Forward: Int = R.drawable.ic_forward
    @DrawableRes val Collapse: Int = R.drawable.ic_collapse
    @DrawableRes val Expand: Int = R.drawable.ic_expand
    @DrawableRes val Close: Int = R.drawable.ic_close
    @DrawableRes val Overflow: Int = R.drawable.ic_overflow
    @DrawableRes val Confirm: Int = R.drawable.ic_confirm

    // --- Library -----------------------------------------------------------------------------

    @DrawableRes val Songs: Int = R.drawable.ic_songs
    @DrawableRes val Albums: Int = R.drawable.ic_albums
    @DrawableRes val Artists: Int = R.drawable.ic_artists
    @DrawableRes val Playlists: Int = R.drawable.ic_playlists
    @DrawableRes val Folders: Int = R.drawable.ic_folders
    @DrawableRes val GridView: Int = R.drawable.ic_grid_view
    @DrawableRes val ListView: Int = R.drawable.ic_list_view
    @DrawableRes val Search: Int = R.drawable.ic_search

    // --- Editing -----------------------------------------------------------------------------

    @DrawableRes val Create: Int = R.drawable.ic_create
    @DrawableRes val Rename: Int = R.drawable.ic_rename
    @DrawableRes val Delete: Int = R.drawable.ic_delete
    @DrawableRes val Share: Int = R.drawable.ic_share
    @DrawableRes val Reorder: Int = R.drawable.ic_reorder
    @DrawableRes val Sort: Int = R.drawable.ic_sort
    @DrawableRes val Filter: Int = R.drawable.ic_filter
    @DrawableRes val Rescan: Int = R.drawable.ic_rescan

    // --- Destinations and status -------------------------------------------------------------

    @DrawableRes val Settings: Int = R.drawable.ic_settings
    @DrawableRes val Equalizer: Int = R.drawable.ic_equalizer
    @DrawableRes val NowPlaying: Int = R.drawable.ic_now_playing
    @DrawableRes val SleepTimer: Int = R.drawable.ic_sleep_timer
    @DrawableRes val Duration: Int = R.drawable.ic_duration
    @DrawableRes val Recent: Int = R.drawable.ic_recent
    @DrawableRes val OutputDevice: Int = R.drawable.ic_output_device
    @DrawableRes val Storage: Int = R.drawable.ic_storage
    @DrawableRes val Permission: Int = R.drawable.ic_permission
    @DrawableRes val Warning: Int = R.drawable.ic_warning

    /** Every icon on the sheet, in sheet order. Handy for a debug/icon-audit screen. */
    val All: List<Pair<String, Int>> = listOf(
        "Play" to Play,
        "Pause" to Pause,
        "Previous" to Previous,
        "Next" to Next,
        "Shuffle" to Shuffle,
        "Repeat" to Repeat,
        "Favourite" to Favourite,
        "Favourite (set)" to FavouriteFilled,
        "Queue" to Queue,
        "Add to queue" to AddToQueue,
        "Lyrics" to Lyrics,
        "Info" to Info,
        "Output" to Output,
        "Waveform" to Waveform,
        "Menu" to Menu,
        "Back" to Back,
        "Forward" to Forward,
        "Collapse" to Collapse,
        "Expand" to Expand,
        "Close" to Close,
        "Overflow" to Overflow,
        "Confirm" to Confirm,
        "Songs" to Songs,
        "Albums" to Albums,
        "Artists" to Artists,
        "Playlists" to Playlists,
        "Folders" to Folders,
        "Grid view" to GridView,
        "List view" to ListView,
        "Search" to Search,
        "Create" to Create,
        "Rename" to Rename,
        "Delete" to Delete,
        "Share" to Share,
        "Reorder" to Reorder,
        "Sort" to Sort,
        "Filter" to Filter,
        "Rescan" to Rescan,
        "Settings" to Settings,
        "Equalizer" to Equalizer,
        "Now playing" to NowPlaying,
        "Sleep timer" to SleepTimer,
        "Duration" to Duration,
        "Recent" to Recent,
        "Output device" to OutputDevice,
        "Storage" to Storage,
        "Permission" to Permission,
        "Warning" to Warning,
    )

    @Composable
    fun painter(@DrawableRes id: Int): Painter = painterResource(id)
}
