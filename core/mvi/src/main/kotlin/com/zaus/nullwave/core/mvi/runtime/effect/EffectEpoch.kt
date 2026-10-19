package com.zaus.nullwave.core.mvi.runtime.effect

/** Main-confined lifetime shared by effects from one operation generation. */
internal class EffectEpoch {
    var isCurrent: Boolean = true
        private set

    fun revoke() {
        isCurrent = false
    }
}
