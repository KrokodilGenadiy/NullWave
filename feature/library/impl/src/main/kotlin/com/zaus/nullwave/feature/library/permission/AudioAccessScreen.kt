package com.zaus.nullwave.feature.library.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.CyberButtonType
import com.zaus.nullwave.core.designsystem.components.control.NullWaveCyberButton
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Artboard 41 - first run, before the audio permission has been asked for.
 *
 * A full-screen takeover, unlike [AccessDeniedScreen]: this is the only screen in the app where the
 * wordmark appears at full scale, and there is no library shell behind it yet to show.
 *
 * ## The copy is the design's, verbatim
 *
 * It is load-bearing and worth not paraphrasing. The spec's own note on this artboard reads *"copy states
 * what the app cannot do, not only what it wants"* - hence the sentence about having no network
 * permission and sending nothing anywhere. A permission prompt that only lists what it would like is the
 * thing this is deliberately not.
 *
 * @param onChooseFolders the design's second path - a folder picker instead of broad access. **Null until
 *   that path exists**, and the button is then omitted rather than rendered dead. The spec says "both
 *   paths are real", so shipping a button that does nothing would misrepresent it in the other direction.
 */
@Composable
fun AudioAccessScreen(
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
    onChooseFolders: (() -> Unit)? = null,
) {
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = spacing.lg, vertical = spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The design calls for a heavy condensed distressed display face here, which is not bundled -
        // see NOTES.md. Chakra Petch Bold at display scale is the stand-in, as it is for cover art.
        Text(
            text = Wordmark,
            style = NullWaveTheme.typography.display,
            color = colors.textPrimary,
        )
        NullWaveMicroLabel(
            text = "Offline local player · build 4.11",
            color = colors.textTertiary,
            modifier = Modifier.padding(top = spacing.xxs),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Audio access",
                style = NullWaveTheme.typography.h1,
                color = colors.textPrimary,
            )
            Text(
                text = "The player reads audio files already on this device. It has no network " +
                    "permission, sends nothing anywhere, and writes only its own settings.",
                style = NullWaveTheme.typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            // Padlock plus caption, as the artboard has it - the icon was missing from the first pass.
            // `textTertiary` matches the design's `#5A6975`; it is the quietest thing on the screen on
            // purpose, because it states a fact rather than asking for anything.
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = spacing.xs),
            ) {
                Icon(
                    painter = painterResource(NullWaveIcons.Permission),
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(CaptionIconSize),
                )
                NullWaveMicroLabel(
                    text = "READ_MEDIA_AUDIO · the only permission asked",
                    color = colors.textTertiary,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NullWaveCyberButton(
                text = "Grant access",
                onClick = onGrantAccess,
                type = CyberButtonType.Primary,
            )
            if (onChooseFolders != null) {
                NullWaveCyberButton(
                    text = "Choose folders instead",
                    onClick = onChooseFolders,
                    type = CyberButtonType.Tertiary,
                )
            }
        }
    }
}

/** Uppercased at the call site, like every display string in this system. */
private const val Wordmark = "NULLWAVE"

/** 18dp in the artboards - smaller than `dimens.icon`, because it annotates text rather than acting. */
private val CaptionIconSize = 18.dp

@Preview(name = "41 · First-run permission", showBackground = true, heightDp = 720)
@Composable
private fun AudioAccessPreview() {
    NullWaveTheme {
        AudioAccessScreen(onGrantAccess = {})
    }
}

@Preview(name = "41 · with the folder path wired", showBackground = true, heightDp = 720)
@Composable
private fun AudioAccessWithFoldersPreview() {
    NullWaveTheme {
        AudioAccessScreen(onGrantAccess = {}, onChooseFolders = {})
    }
}
