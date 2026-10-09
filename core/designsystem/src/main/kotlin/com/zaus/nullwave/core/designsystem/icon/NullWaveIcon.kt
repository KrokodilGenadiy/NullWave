package com.zaus.nullwave.core.designsystem.icon

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Non-interactive glyph, 24 dp by default. The caller supplies a localized description,
 * or explicitly passes null when a surrounding label/control already describes it.
 * A clickable parent owns the touch target and action; this primitive has no click handling.
 */
@Composable
fun NullWaveIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(NullWaveTheme.dimens.icon),
        tint = tint,
    )
}
