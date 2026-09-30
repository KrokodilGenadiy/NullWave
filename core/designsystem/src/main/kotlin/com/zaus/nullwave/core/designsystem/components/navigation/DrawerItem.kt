package com.zaus.nullwave.core.designsystem.components.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * A row in the navigation drawer.
 *
 * Selected is the same language as [NullWaveTrackRow]: a 3dp `primary` spine plus a `surfaceRaised`
 * fill. One selection idiom across the app, so it never has to be relearned.
 *
 * [isExternal] is the dimmer treatment the design requires for the `EXTERNAL LINKS` group - Spotify,
 * YouTube Music, SoundCloud, Bandcamp. Those are outbound and must **never read as in-app
 * destinations**: dimmer label, no spine even when touched, and an arrow suffix marking them as
 * leaving the app.
 */
@Composable
fun NullWaveDrawerItem(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isExternal: Boolean = false,
) {
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens

    val contentColor = when {
        isExternal -> colors.textTertiary
        isSelected -> colors.primary
        else -> colors.textSecondary
    }
    // External rows never take the selected treatment, however they are interacted with.
    val showSpine = isSelected && !isExternal

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.rowHeight)
            .background(if (showSpine) colors.surfaceRaised else Color.Transparent)
            .clickable(onClick = onClick)
            .drawBehind {
                if (showSpine) {
                    drawRect(color = colors.primary, size = Size(dimens.accentSpine.toPx(), size.height))
                }
            }
            .padding(horizontal = dimens.gutter),
        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(dimens.icon),
        )
        Text(
            text = label,
            style = if (isSelected && !isExternal) {
                NullWaveTheme.typography.bodyEmphasis
            } else {
                NullWaveTheme.typography.body
            },
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isExternal) {
            Icon(
                painter = painterResource(NullWaveIcons.Forward),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(ExternalArrowSize),
            )
        }
    }
}

private val ExternalArrowSize = 16.dp

@Preview(name = "Drawer item", showBackground = true)
@Composable
private fun DrawerItemPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(modifier = Modifier.background(colors.surface)) {
            NullWaveDrawerItem("Player", NullWaveIcons.NowPlaying, {}, isSelected = true)
            NullWaveDrawerItem("Equalizer", NullWaveIcons.Equalizer, {})
            NullWaveDrawerItem("Sleep timer", NullWaveIcons.SleepTimer, {})
            NullWaveDrawerItem("Settings", NullWaveIcons.Settings, {})
            NullWaveDrawerItem("About", NullWaveIcons.Info, {})

            NullWaveMicroLabel(
                text = "external links",
                modifier = Modifier.padding(
                    start = NullWaveTheme.dimens.gutter,
                    top = NullWaveTheme.spacing.md,
                    bottom = NullWaveTheme.spacing.xxs,
                ),
            )
            NullWaveDrawerItem("Spotify", NullWaveIcons.Share, {}, isExternal = true)
            NullWaveDrawerItem("Bandcamp", NullWaveIcons.Share, {}, isExternal = true)
        }
    }
}
