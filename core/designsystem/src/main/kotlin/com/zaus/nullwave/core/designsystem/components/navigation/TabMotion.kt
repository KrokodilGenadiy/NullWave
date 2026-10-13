package com.zaus.nullwave.core.designsystem.components.navigation

import androidx.compose.animation.core.FastOutSlowInEasing

/** Pure normalized geometry, shared by runtime and frozen previews; independent of pixels/RTL. */
internal data class TabUnderline(val width: Float = 0f, val alpha: Float = 0f)

internal data class TabTransition(
    val from: List<TabUnderline>,
    val selectedIndex: Int,
    val reduced: Boolean,
) {
    fun underline(index: Int, progress: Float): TabUnderline {
        val p = progress.coerceIn(0f, 1f)
        val start = from[index]
        val target = if (index == selectedIndex) 1f else 0f
        if (p >= 1f) return TabUnderline(target, target)
        if (reduced) {
            val alpha = start.alpha * (1f - p) + target * p
            return TabUnderline(if (target > 0f && start.width == 0f) 1f else start.width, alpha)
        }
        return when {
            p < TabMotion.CollapseEnd -> TabUnderline(
                start.width * (1f - FastOutSlowInEasing.transform(p / TabMotion.CollapseEnd)), start.alpha,
            )
            p < TabMotion.GrowEnd -> TabUnderline(
                target * FastOutSlowInEasing.transform((p - TabMotion.CollapseEnd) / (TabMotion.GrowEnd - TabMotion.CollapseEnd)), target,
            )
            else -> TabUnderline(target, target)
        }
    }

    fun retarget(index: Int, progress: Float) = copy(
        from = from.indices.map { underline(it, progress) }, selectedIndex = index,
    )

    companion object {
        fun settled(count: Int, selectedIndex: Int, reduced: Boolean) = TabTransition(
            List(count) { val value = if (it == selectedIndex) 1f else 0f; TabUnderline(value, value) },
            selectedIndex, reduced,
        )
    }
}

internal object TabMotion {
    const val LockMillis = 320
    const val FadeMillis = 120
    const val SplitMillis = 90
    const val CollapseEnd = 0.25f
    const val ContractEnd = 0.45f
    const val SettleEnd = 0.55f
    const val GrowEnd = 0.6f
    const val FadeStart = 0.6f
}
