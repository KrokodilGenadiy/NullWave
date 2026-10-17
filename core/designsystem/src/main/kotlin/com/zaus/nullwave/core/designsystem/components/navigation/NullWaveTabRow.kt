package com.zaus.nullwave.core.designsystem.components.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.motion.systemAnimationsEnabled
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Immutable
data class NullWaveTabItem(val label: String, val enabled: Boolean = true) {
    init { require(label.isNotBlank()) { "Tab label must not be blank" } }
}

/**
 * Controlled scrollable tabs with a text-bound lock-on effect. Supply a nonempty list and valid
 * [selectedIndex]; replace the list rather than mutating it. A changed list or motion mode settles
 * immediately, as does first composition. Only an actual selection change starts the effect.
 *
 * [reduceMotion] replaces brackets/split/width changes with a short underline cross-fade. The caller
 * owns this preference; Compose's animator duration scale is also respected (zero settles instantly).
 * Material owns horizontal scrolling and selectableGroup; each cell owns Role.Tab/selected/disabled.
 * Press/focus feedback follows the label, never the rectangular hit area. Labels are not ellipsized.
 */
@Composable
fun NullWaveTabRow(
    items: List<NullWaveTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    require(selectedIndex in items.indices) { "Selected index must identify an existing tab" }
    val colors = NullWaveTheme.colors
    val animationsEnabled = systemAnimationsEnabled()
    val animation = remember(items, reduceMotion, animationsEnabled) {
        TabAnimation(items.size, selectedIndex, reduceMotion)
    }
    // Capture widths/opacity before drawing the new selection, avoiding a one-frame completed line
    // before LaunchedEffect resets the clock. Rapid selection changes cancel the previous effect.
    SideEffect {
        if (animation.transition.selectedIndex != selectedIndex) {
            animation.select(selectedIndex, animate = animationsEnabled)
        }
    }
    LaunchedEffect(animation, selectedIndex) {
        if (!animation.pending) return@LaunchedEffect
        animation.progress.snapTo(0f)
        animation.pending = false
        animation.progress.animateTo(
            1f,
            tween(if (reduceMotion) TabMotion.FadeMillis else TabMotion.LockMillis, easing = LinearEasing),
        )
    }
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = colors.surface,
        contentColor = colors.primary,
        edgePadding = NullWaveTheme.spacing.xs,
        minTabWidth = NullWaveTheme.dimens.touchTarget,
        divider = { NullWaveDivider() },
        // Draw locally around each Text layout: no window coordinates or duplicate indicator.
        indicator = {},
    ) {
        items.forEachIndexed { index, item ->
            val source = remember { MutableInteractionSource() }
            val focused = source.collectIsFocusedAsState()
            val pressed = source.collectIsPressedAsState()
            val layout = remember { TabLabelLayout() }
            Box(
                modifier = Modifier.defaultMinSize(minHeight = NullWaveTheme.dimens.touchTarget)
                    .selectable(
                        selected = index == selectedIndex, enabled = item.enabled, role = Role.Tab,
                        interactionSource = source, indication = null,
                        onClick = { if (item.enabled) onSelect(index) },
                    )
                    .padding(horizontal = NullWaveTheme.spacing.md, vertical = NullWaveTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    item.label,
                    style = NullWaveTheme.typography.tab,
                    color = {
                        when {
                            !item.enabled -> colors.textSecondary
                            index == selectedIndex -> colors.primary
                            pressed.value || focused.value -> colors.textPrimary
                            else -> colors.textSecondary
                        }
                    },
                    onTextLayout = { layout.value = it },
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        val progress = animation.frameProgress
                        drawTabDecoration(
                            underline = animation.transition.underline(index, progress),
                            lockProgress = if (item.enabled && index == selectedIndex && !reduceMotion && animationsEnabled) progress else 1f,
                            layout = layout.value,
                            colors = colors,
                            enabled = item.enabled,
                            pressed = item.enabled && pressed.value,
                            focused = item.enabled && focused.value,
                        )
                    },
                )
            }
        }
    }
}

/** Layout is consumed in the same node's draw; no extra snapshot/layout feedback loop. */
private class TabLabelLayout { var value: TextLayoutResult? = null }

private class TabAnimation(count: Int, selectedIndex: Int, reduced: Boolean) {
    val progress = Animatable(1f)
    var pending by mutableStateOf(false)
    var transition by mutableStateOf(TabTransition.settled(count, selectedIndex, reduced))
    val frameProgress: Float get() = if (pending) 0f else progress.value

    fun select(index: Int, animate: Boolean) {
        transition = if (animate) transition.retarget(index, frameProgress)
        else TabTransition.settled(transition.from.size, index, transition.reduced)
        pending = animate
    }
}
