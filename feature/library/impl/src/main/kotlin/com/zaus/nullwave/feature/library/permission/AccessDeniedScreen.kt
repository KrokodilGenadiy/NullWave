package com.zaus.nullwave.feature.library.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
 * Artboard 45 - the permission was asked for and refused, and the system will no longer offer the dialog.
 *
 * **Not a takeover.** The artboard keeps the library's app bar and the five tabs visible above this, inert
 * - the spec's note is *"tabs stay visible and inert — the shell is real, the content is not"*. So this is
 * the body of the library screen, not a replacement for it, which is the structural difference from
 * [AudioAccessScreen].
 *
 * ## Danger is on the icon, not the text
 *
 * The caption is `textSecondary` (`#93A3AD` in the artboard) and only the warning triangle is `danger`.
 * Worth stating because the first version of this screen coloured the caption `danger`, which both
 * contradicted the artboard and broke the design system's own rule: `danger` "never sets body text (it
 * fails contrast at small sizes)" - see `NullWaveColors`.
 *
 * ## The settings path is text in the design
 *
 * The artboard renders `READ_MEDIA_AUDIO · DENIED` and `SET IT IN SYSTEM SETTINGS › APPS › NULLWAVE` as
 * two lines of one mono caption, with no control. [onOpenSettings] is therefore **optional**, and passing
 * it adds a button the artboard does not have.
 *
 * That addition is deliberate rather than an oversight: this is the one state the app cannot leave on its
 * own, and telling a user to go and find Settings themselves when a deep link exists is poor. It is opt-in
 * so the deviation is visible at the call site and can be dropped in one edit.
 */
@Composable
fun AccessDeniedScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Access denied",
            style = NullWaveTheme.typography.h1,
            color = colors.textPrimary,
        )
        Text(
            text = "Audio permission was declined, so there is nothing to list. Nothing else in the " +
                "app needs it, and it can be revoked again at any time.",
            style = NullWaveTheme.typography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.sm),
        )

        // The artboard's two-line caption, beside a triangle. `Alignment.Top` because the icon aligns to
        // the first line rather than centring against both.
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(top = spacing.md),
        ) {
            Icon(
                painter = painterResource(NullWaveIcons.Warning),
                contentDescription = null,
                // The only thing on this screen that carries danger.
                tint = colors.danger,
                modifier = Modifier.size(CaptionIconSize),
            )
            NullWaveMicroLabel(
                text = "READ_MEDIA_AUDIO · denied\nSet it in system settings › apps › NullWave",
                color = colors.textSecondary,
            )
        }

        if (onOpenSettings != null) {
            NullWaveCyberButton(
                text = "Open settings",
                onClick = onOpenSettings,
                type = CyberButtonType.Secondary,
                modifier = Modifier.padding(top = spacing.lg),
            )
        }
    }
}

/** 18dp in the artboards - it annotates text rather than acting, so smaller than `dimens.icon`. */
private val CaptionIconSize = 18.dp

@Preview(name = "45 · Permission denied (as drawn)", showBackground = true, heightDp = 560)
@Composable
private fun AccessDeniedPreview() {
    NullWaveTheme {
        AccessDeniedScreen()
    }
}

@Preview(name = "45 · with the settings deep link", showBackground = true, heightDp = 560)
@Composable
private fun AccessDeniedWithSettingsPreview() {
    NullWaveTheme {
        AccessDeniedScreen(onOpenSettings = {})
    }
}
