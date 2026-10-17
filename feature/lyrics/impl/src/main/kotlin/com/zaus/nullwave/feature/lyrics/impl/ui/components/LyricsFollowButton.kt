package com.zaus.nullwave.feature.lyrics.impl.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.feature.lyrics.impl.R

/**
 * Requests a return to the current synced line. The owner decides visibility and performs scrolling;
 * this is an action, not a switch that claims automatic following is already enabled.
 */
@Composable
internal fun LyricsFollowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    NullWaveButton(
        label = stringResource(R.string.lyrics_return_to_current),
        onClick = onClick,
        modifier = modifier,
        variant = NullWaveButtonVariant.Neutral,
        enabled = enabled,
        leadingIcon = NullWaveIcons.Lyrics,
    )
}
