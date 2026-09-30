package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Text that resolves out of noise, character by character, whenever [text] changes.
 *
 * The cyberdeck's one text-level effect: a readout locking on rather than a label being replaced.
 * Reserved for chrome that changes on navigation - the app bar title, the scan path readout - not
 * for list content, where it would be unreadable and irritating.
 *
 * Honours `motion.reduceMotion` (renders [text] immediately), and skips the animation in
 * `@Preview` so previews show the real string instead of an empty frame.
 *
 * Layout does not jitter even though the display face is not monospaced: give this a slot with a
 * fixed width - `Modifier.weight(1f)` in the app bar - and only the glyphs shimmer, not the box.
 */
@Composable
fun NullWaveDecodingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NullWaveTheme.typography.h2,
    color: Color = NullWaveTheme.colors.textPrimary,
    durationMillis: Int = DecodeDurationMillis,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    // LaunchedEffect never runs in a static preview, so without this the preview renders whatever
    // the initial value happens to be - i.e. nothing.
    val isStatic = NullWaveTheme.motion.reduceMotion || LocalInspectionMode.current

    var rendered by remember { mutableStateOf(if (isStatic) text else "") }

    LaunchedEffect(text, isStatic, durationMillis) {
        if (isStatic || text.isEmpty()) {
            rendered = text
            return@LaunchedEffect
        }

        // One character locks in per tick, left to right. The tick is derived from the total
        // duration so a two-word title and a long album name take about the same time, then
        // clamped so neither extreme becomes a strobe or a crawl.
        val tick = (durationMillis / text.length)
            .coerceIn(MinTickMillis, MaxTickMillis)
            .toLong()

        for (resolved in 0..text.length) {
            rendered = buildString(text.length) {
                append(text, 0, resolved)
                for (i in resolved until text.length) {
                    val target = text[i]
                    // Keep spaces: the word shape stays recognisable while the letters churn.
                    append(if (target.isWhitespace()) target else Glyphs[Random.nextInt(Glyphs.length)])
                }
            }
            if (resolved < text.length) delay(tick.milliseconds)
        }
        rendered = text
    }

    Text(
        text = rendered,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

/**
 * Uppercase letters, digits and a few technical marks - the character set the game's readouts use.
 * No lowercase: the chrome this is used on is uppercase, so lowercase noise would read as a bug.
 */
private const val Glyphs = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&/\\<>*+=-_"

/**
 * Whole-string reveal time. Not a motion token yet - if this effect spreads beyond the app bar,
 * promote it into `NullWaveMotion` so it sits in the token sheet with the others.
 */
private const val DecodeDurationMillis = 360

private const val MinTickMillis = 16
private const val MaxTickMillis = 60

@Preview(name = "Decoding text", widthDp = 320, heightDp = 60, showBackground = true)
@Composable
private fun DecodingTextPreview() {
    NullWaveTheme {
        NullWaveDecodingText(text = "EQUALIZER")
    }
}
