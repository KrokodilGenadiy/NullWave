package com.zaus.nullwave.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.player.api.PlayerController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Scaffolding, not a player.
 *
 * `@ContributesBinding(AppScope::class)` is what binds [PlayerController] to this class. Callers in
 * other modules see only the interface from `:feature:player:api` - they never reference this
 * type, and `:app` writes no binding for it.
 *
 * Replace the bodies with a real MediaSession/ExoPlayer implementation.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class RealPlayerController : PlayerController {

    private val _nowPlayingId = MutableStateFlow<Long?>(null)
    override val nowPlayingId: StateFlow<Long?> = _nowPlayingId

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    override fun play(trackIds: List<Long>, startIndex: Int) {
        _nowPlayingId.value = trackIds.getOrNull(startIndex)
        _isPlaying.value = _nowPlayingId.value != null
    }

    override fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    override fun next() = Unit

    override fun previous() = Unit
}

/**
 * The player is a bottom sheet rather than a navigation destination, so this module contributes no
 * [com.zaus.nullwave.core.navigation.EntryProviderInstaller] - the host owns the sheet and the
 * collapsed mini-player, and this module supplies the content. The three sheet states (hidden,
 * collapsed 64dp bar, expanded full-screen) go in from here.
 */
@Composable
fun PlayerSheetContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "PLAYER",
            style = NullWaveTheme.typography.h1,
            color = NullWaveTheme.colors.textPrimary,
        )
    }
}
