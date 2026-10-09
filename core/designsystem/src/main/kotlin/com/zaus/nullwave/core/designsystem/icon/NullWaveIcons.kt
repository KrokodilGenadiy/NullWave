package com.zaus.nullwave.core.designsystem.icon

import androidx.annotation.DrawableRes
import com.zaus.nullwave.core.designsystem.R

/**
 * Full 48-icon legacy sheet, grouped by purpose. NullWaveIcon supplies size and tint.
 * Only Back/Forward auto-mirror; transport controls retain their playback direction.
 * Source attribution and license are bundled in assets/licenses/material_symbols_*.txt.
 */
object NullWaveIcons {

    // --- Transport -------------------------------------------------------------------------

    @DrawableRes val Play: Int = R.drawable.nw_icon_play
    @DrawableRes val Pause: Int = R.drawable.nw_icon_pause
    @DrawableRes val Previous: Int = R.drawable.nw_icon_previous
    @DrawableRes val Next: Int = R.drawable.nw_icon_next
    @DrawableRes val Shuffle: Int = R.drawable.nw_icon_shuffle
    @DrawableRes val Repeat: Int = R.drawable.nw_icon_repeat

    // --- Player actions --------------------------------------------------------------------

    /** Angular heart, hollow. Ten vertices, straight segments only - unset state. */
    @DrawableRes val Favourite: Int = R.drawable.nw_icon_favourite

    /** The identical silhouette, filled. Only the fill changes between states. */
    @DrawableRes val FavouriteFilled: Int = R.drawable.nw_icon_favourite_filled

    /** A list with a play arrow at the leading edge. Deliberately not three plain lines. */
    @DrawableRes val Queue: Int = R.drawable.nw_icon_queue
    @DrawableRes val AddToQueue: Int = R.drawable.nw_icon_add_to_queue
    @DrawableRes val Lyrics: Int = R.drawable.nw_icon_lyrics
    @DrawableRes val Info: Int = R.drawable.nw_icon_info
    @DrawableRes val Output: Int = R.drawable.nw_icon_output
    @DrawableRes val Waveform: Int = R.drawable.nw_icon_waveform

    // --- Navigation and chrome -------------------------------------------------------------

    @DrawableRes val Menu: Int = R.drawable.nw_icon_menu
    /** Directional: mirrors in right-to-left layouts. */
    @DrawableRes val Back: Int = R.drawable.nw_icon_back
    /** Directional: mirrors in right-to-left layouts. */
    @DrawableRes val Forward: Int = R.drawable.nw_icon_forward
    @DrawableRes val Collapse: Int = R.drawable.nw_icon_collapse
    @DrawableRes val Expand: Int = R.drawable.nw_icon_expand
    @DrawableRes val Close: Int = R.drawable.nw_icon_close
    @DrawableRes val More: Int = R.drawable.nw_icon_more
    @DrawableRes val Check: Int = R.drawable.nw_icon_check

    // --- Library -----------------------------------------------------------------------------

    @DrawableRes val Songs: Int = R.drawable.nw_icon_songs
    @DrawableRes val Albums: Int = R.drawable.nw_icon_albums
    @DrawableRes val Artists: Int = R.drawable.nw_icon_artists
    @DrawableRes val Playlists: Int = R.drawable.nw_icon_playlists
    @DrawableRes val Folders: Int = R.drawable.nw_icon_folders
    @DrawableRes val GridView: Int = R.drawable.nw_icon_grid_view
    @DrawableRes val ListView: Int = R.drawable.nw_icon_list_view
    @DrawableRes val Search: Int = R.drawable.nw_icon_search

    // --- Editing -----------------------------------------------------------------------------

    @DrawableRes val Create: Int = R.drawable.nw_icon_create
    @DrawableRes val Rename: Int = R.drawable.nw_icon_rename
    @DrawableRes val Delete: Int = R.drawable.nw_icon_delete
    @DrawableRes val Share: Int = R.drawable.nw_icon_share
    @DrawableRes val Reorder: Int = R.drawable.nw_icon_reorder
    @DrawableRes val Sort: Int = R.drawable.nw_icon_sort
    @DrawableRes val Filter: Int = R.drawable.nw_icon_filter
    @DrawableRes val Rescan: Int = R.drawable.nw_icon_rescan

    // --- Destinations and status -------------------------------------------------------------

    @DrawableRes val Settings: Int = R.drawable.nw_icon_settings
    @DrawableRes val Equalizer: Int = R.drawable.nw_icon_equalizer
    @DrawableRes val NowPlaying: Int = R.drawable.nw_icon_now_playing
    @DrawableRes val SleepTimer: Int = R.drawable.nw_icon_sleep_timer
    @DrawableRes val Duration: Int = R.drawable.nw_icon_duration
    @DrawableRes val Recent: Int = R.drawable.nw_icon_recent
    @DrawableRes val OutputDevice: Int = R.drawable.nw_icon_output_device
    @DrawableRes val Storage: Int = R.drawable.nw_icon_storage
    @DrawableRes val Permission: Int = R.drawable.nw_icon_permission
    @DrawableRes val Warning: Int = R.drawable.nw_icon_warning
}
