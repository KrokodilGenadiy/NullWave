package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.zaus.nullwave.core.designsystem.motion.decorativeMotionEnabled
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlin.random.Random

/**
 * Text that decodes into place: cipher glyphs in [scrambleColor] resolve into [text] from start to
 * end, then the component is plain text. It plays on first composition and again when [text] or
 * [replayKey] changes; nothing repeats on its own. A new composition, for example after Activity
 * recreation, plays it again: the decode is decoration, not state worth saving.
 *
 * Layout is always measured from the final copy, so the size never jumps while glyphs cycle.
 * Accessibility services read only [text]; the glyphs are not announced. Reduced motion, the theme
 * policy, or a zero system animation scale show the final copy at once.
 */
@Composable
fun NullWaveDecodeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    scrambleColor: Color = NullWaveTheme.colors.secondary,
    replayKey: Any? = null,
    reduceMotion: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val animate = decorativeMotionEnabled(reduceMotion)
    val frames = remember(text) { DecodeTextFrames(text) }
    val progress = remember(text, replayKey) { Animatable(if (animate) 0f else 1f) }
    val seed = remember(text, replayKey) { Random.nextInt() }
    LaunchedEffect(progress, animate) {
        // Turning motion off mid-decode jumps to the final copy; turning it on never replays.
        if (!animate) progress.snapTo(1f)
        else if (progress.value < 1f) progress.animateTo(1f, tween(frames.durationMs, easing = LinearEasing))
    }
    val decoding = animate && progress.value < 1f
    Box(modifier) {
        // The final copy owns size and semantics; it is only hidden while the glyphs are drawn.
        Text(
            text = text,
            modifier = Modifier.drawWithContent { if (!decoding) drawContent() },
            color = color,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
        )
        if (decoding) {
            val frame = frames.frame((progress.value * frames.durationMs).toLong(), seed)
            Text(
                text = buildAnnotatedString {
                    append(frame.text.substring(0, frame.resolvedEnd))
                    withStyle(SpanStyle(color = scrambleColor)) { append(frame.text.substring(frame.resolvedEnd)) }
                },
                modifier = Modifier.matchParentSize().clearAndSetSemantics {},
                color = color,
                style = style,
                maxLines = maxLines,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
