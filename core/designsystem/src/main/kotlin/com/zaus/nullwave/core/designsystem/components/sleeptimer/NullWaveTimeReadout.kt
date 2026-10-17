package com.zaus.nullwave.core.designsystem.components.sleeptimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Large, caller-formatted time and caption. No clock, duration formatting or timer state.
 * [contentDescription] describes both in the caller's locale, for example "18 minutes 42 seconds
 * remaining". The group is announced once; it is deliberately not a live region that interrupts
 * the user every second. Announcements of timer completion belong to the feature.
 *
 * [active] selects the accent treatment, not an enabled/disabled interaction state. The optional
 * glow is static and follows the accent palette. Caption text wraps with the system font scale.
 * [glowEnabled] can disable glow locally but cannot re-enable it when [NullWaveTheme.effects] forbids it.
 * The numeric line scales from 56 to 24 sp to fit, preserving system font scaling. Supply a compact
 * single-line [timeLabel] (such as mm:ss or h:mm:ss) and enough width for it at the minimum size;
 * allow the component to grow vertically. Numerals retain their order in RTL.
 */
@Composable
fun NullWaveTimeReadout(
    timeLabel: String,
    caption: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    glowEnabled: Boolean = true,
) {
    require(timeLabel.isNotBlank() && '\n' !in timeLabel && '\r' !in timeLabel) {
        "Time readout needs a non-blank, single-line time label"
    }
    require(caption.isNotBlank()) { "Time readout needs a caption" }
    require(contentDescription.isNotBlank()) { "Time readout needs an accessible description" }
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing
    val effects = NullWaveTheme.effects.withRestrictions(glowEnabled = glowEnabled)
    val glowRadius = with(LocalDensity.current) { ReadoutGlowRadius.toPx() }
    val timeStyle = NullWaveTheme.typography.mono.copy(
        fontSize = ReadoutFontSize,
        lineHeight = 1.15.em,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.04.em,
        textDirection = TextDirection.Ltr,
        shadow = if (active && effects.glowEnabled) Shadow(
            color = colors.primary.copy(alpha = ReadoutGlowAlpha),
            blurRadius = glowRadius,
        ) else null,
    )
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            this.contentDescription = contentDescription
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = timeLabel,
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
            color = if (active) colors.primary else colors.textSecondary,
            style = timeStyle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = ReadoutMinFontSize, maxFontSize = ReadoutFontSize),
        )
        Text(
            text = caption,
            modifier = Modifier.fillMaxWidth(),
            style = NullWaveTheme.typography.mono,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private val ReadoutFontSize = 56.sp
private val ReadoutMinFontSize = 24.sp
private val ReadoutGlowRadius = 12.dp
private const val ReadoutGlowAlpha = 0.24f
