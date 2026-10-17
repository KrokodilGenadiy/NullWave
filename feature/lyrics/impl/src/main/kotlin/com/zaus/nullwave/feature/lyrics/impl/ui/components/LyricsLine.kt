package com.zaus.nullwave.feature.lyrics.impl.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.lyrics.impl.R

/** Visual hierarchy only. The caller determines proximity to the current line. */
internal enum class LyricsLineEmphasis { Current, Neighbor, Distant }

/**
 * Render-only lyric text; no timestamp, seek action or synchronization policy.
 * Unsynced text uses [LyricsLineEmphasis.Neighbor] for every line. Stanza spacing belongs to
 * the list; [text] must contain actual text. Lines wrap fully and preserve casing/newlines.
 *
 * Current text has a localized state description alongside the text's native semantics.
 * It is not a live region: changing the current line must not interrupt TalkBack repeatedly.
 * Allow height to grow with text/font scale. Emphasis changes can change the measured height;
 * the future scrolling owner must account for that when positioning the current line.
 * [glowEnabled] adds a static accent halo only to Current, without changing text measurement
 * or semantics. Leave space around the text; a clipping ancestor can trim the halo.
 */
@Composable
internal fun LyricsLine(
    text: String,
    modifier: Modifier = Modifier,
    emphasis: LyricsLineEmphasis = LyricsLineEmphasis.Neighbor,
    glowEnabled: Boolean = true,
) {
    require(text.isNotBlank()) { "Lyrics line must contain text" }
    val colors = NullWaveTheme.colors
    val current = emphasis == LyricsLineEmphasis.Current
    val glowRadius = with(LocalDensity.current) { CurrentLineGlowRadius.toPx() }
    val currentDescription = if (current) stringResource(R.string.lyrics_current_line) else null
    val fontSize = when (emphasis) {
        LyricsLineEmphasis.Current -> CurrentLineSize
        LyricsLineEmphasis.Neighbor -> NeighborLineSize
        LyricsLineEmphasis.Distant -> DistantLineSize
    }
    Text(
        text = text,
        modifier = modifier.then(
            if (currentDescription != null) Modifier.semantics { stateDescription = currentDescription }
            else Modifier,
        ),
        color = if (current) colors.primary else colors.textSecondary,
        style = NullWaveTheme.typography.h1.copy(
            fontSize = fontSize,
            lineHeight = 1.25.em,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.em,
            shadow = if (current && glowEnabled) Shadow(
                color = colors.primary.copy(alpha = CurrentLineGlowAlpha),
                blurRadius = glowRadius,
            ) else null,
        ),
        textAlign = TextAlign.Start,
    )
}

private val CurrentLineSize = 26.sp
private val NeighborLineSize = 21.sp
private val DistantLineSize = 20.sp
private val CurrentLineGlowRadius = 10.dp
private const val CurrentLineGlowAlpha = 0.35f
