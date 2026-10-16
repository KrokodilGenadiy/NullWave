package com.zaus.nullwave.core.designsystem.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Screen chrome with a single owner for system bars, cutouts and IME space. [windowInsets] defaults
 * to safeDrawing (which includes IME); all slots sit inside its unconsumed area. The background
 * extends behind those insets. Do not add safeDrawingPadding/imePadding again inside the slots.
 *
 * Material measures [topBar] and [bottomBar], and places [snackbarHost] above the bottom slot.
 * The caller owns bar visibility and state: an empty slot reserves no height. Slots must not clip
 * decorative overflow such as MiniPlayer's glow. No fixed player height or playback policy lives here.
 *
 * [content] receives padding for the measured bars. Apply it once and consume it:
 * - ordinary content: Modifier.padding(padding).consumeWindowInsets(padding);
 * - LazyColumn: contentPadding = padding, Modifier.consumeWindowInsets(padding).
 *
 * Keep scroll content full-size so it can scroll beneath the bars; padding makes its last item
 * reachable above the bottom slot. For an already inset parent, consumed insets are excluded
 * automatically. Pass WindowInsets(0, 0, 0, 0) only if the parent intentionally owns all insets.
 * A product host must choose one owner when integrating this with the current navigation host.
 */
@Composable
fun NullWaveScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = NullWaveTheme.colors
    Scaffold(
        modifier = modifier.background(colors.bg).windowInsetsPadding(windowInsets),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        containerColor = colors.bg,
        contentColor = colors.textPrimary,
        // The outer modifier handles and consumes the same insets for every slot, including IME.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}
