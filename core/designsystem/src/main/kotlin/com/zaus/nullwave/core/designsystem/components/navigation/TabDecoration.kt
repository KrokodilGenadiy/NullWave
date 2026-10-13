package com.zaus.nullwave.core.designsystem.components.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors

/** Label-local draw space. Cell padding reserves room for the decoration, including the overshoot. */
internal fun DrawScope.drawTabDecoration(
    underline: TabUnderline,
    lockProgress: Float,
    layout: TextLayoutResult?,
    colors: NullWaveColors,
    enabled: Boolean = true,
    pressed: Boolean = false,
    focused: Boolean = false,
) {
    val accent = if (enabled) colors.primary else colors.textSecondary
    if (pressed) drawUnderline(1f, colors.primary, PressAlpha)
    drawUnderline(underline.width, accent, underline.alpha)
    // Persistent focus is independent of selection and survives press, without a filled hit area.
    if (focused) drawBrackets(BracketRest.toPx(), colors.textPrimary, 1f)
    if (lockProgress >= 1f) return
    val gap = when {
        lockProgress < TabMotion.ContractEnd -> mix(
            BracketStart.toPx(), BracketOvershoot.toPx(),
            FastOutSlowInEasing.transform(lockProgress / TabMotion.ContractEnd),
        )
        lockProgress < TabMotion.SettleEnd -> mix(
            BracketOvershoot.toPx(), BracketRest.toPx(),
            (lockProgress - TabMotion.ContractEnd) / (TabMotion.SettleEnd - TabMotion.ContractEnd),
        )
        else -> BracketRest.toPx()
    }
    val alpha = if (lockProgress < TabMotion.FadeStart) 1f else (1f - lockProgress) / (1f - TabMotion.FadeStart)
    drawBrackets(gap, accent, alpha)
    val impact = (lockProgress - TabMotion.ContractEnd) / (TabMotion.SplitMillis.toFloat() / TabMotion.LockMillis)
    if (impact !in 0f..<1f) return
    val split = when {
        impact < 0.25f -> 1f
        impact < 0.5f -> 0.55f
        impact < 0.75f -> 0.25f
        else -> 0f
    } * MaxSplit.toPx()
    if (split == 0f) return
    val jitter = when {
        impact < 0.25f -> 1f
        impact < 0.5f -> -1f
        else -> 0f
    } * Jitter.toPx()
    drawGhost(layout, gap, Offset(-split, jitter), colors.danger, alpha)
    drawGhost(layout, gap, Offset(split, -jitter), colors.secondary, alpha)
}

private fun DrawScope.drawUnderline(fraction: Float, color: Color, alpha: Float) {
    if (fraction <= 0f || alpha <= 0f) return
    val width = size.width * fraction
    drawRect(color, Offset((size.width - width) / 2f, size.height + IndicatorGap.toPx()), Size(width, IndicatorHeight.toPx()), alpha)
}

private fun DrawScope.drawGhost(layout: TextLayoutResult?, gap: Float, offset: Offset, color: Color, bracketAlpha: Float) {
    drawBrackets(gap, color, GhostAlpha * bracketAlpha, offset, BlendMode.Plus)
    if (layout != null) drawText(layout, color = color, topLeft = offset, alpha = GhostAlpha, shadow = Shadow.None, blendMode = BlendMode.Plus)
}

private fun DrawScope.drawBrackets(
    gap: Float,
    color: Color,
    alpha: Float,
    offset: Offset = Offset.Zero,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    if (alpha <= 0f) return
    val left = -gap + offset.x
    val right = size.width + gap + offset.x
    val top = -gap + offset.y
    val bottom = size.height + gap + offset.y
    val arm = BracketArm.toPx()
    val stroke = BracketStroke.toPx()
    val horizontal = Size(arm, stroke)
    val vertical = Size(stroke, arm)
    drawRect(color, Offset(left, top), horizontal, alpha, blendMode = blendMode)
    drawRect(color, Offset(left, top), vertical, alpha, blendMode = blendMode)
    drawRect(color, Offset(right - arm, top), horizontal, alpha, blendMode = blendMode)
    drawRect(color, Offset(right - stroke, top), vertical, alpha, blendMode = blendMode)
    drawRect(color, Offset(left, bottom - stroke), horizontal, alpha, blendMode = blendMode)
    drawRect(color, Offset(left, bottom - arm), vertical, alpha, blendMode = blendMode)
    drawRect(color, Offset(right - arm, bottom - stroke), horizontal, alpha, blendMode = blendMode)
    drawRect(color, Offset(right - stroke, bottom - arm), vertical, alpha, blendMode = blendMode)
}

private fun mix(from: Float, to: Float, progress: Float) = from + (to - from) * progress
private val IndicatorGap = 8.dp
private val IndicatorHeight = 2.dp
private val BracketStart = 10.dp
private val BracketOvershoot = 1.5.dp
private val BracketRest = 3.dp
private val BracketArm = 6.dp
private val BracketStroke = 2.dp
private val MaxSplit = 3.dp
private val Jitter = 1.dp
private const val GhostAlpha = 0.85f
private const val PressAlpha = 0.35f
