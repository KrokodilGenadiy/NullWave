package com.zaus.nullwave.core.playback.media3

/** Admission of service intents, separate from session command admission. No queue is restored here. */
internal object PlaybackServiceStartPolicy {
    enum class Decision { DelegateMediaButton, Ignore, FinishIdleStart }

    fun decide(
        instanceId: String,
        isMediaButton: Boolean,
        receivedInstanceId: String?,
        ongoing: Boolean,
    ): Decision {
        // An old notification must neither control nor stop a newer, active service incarnation.
        val stale = receivedInstanceId != null && receivedInstanceId != instanceId
        if (!stale && isMediaButton) return Decision.DelegateMediaButton
        // A matching self-start can arrive after its request has already failed or been stopped.
        return if (ongoing) Decision.Ignore else Decision.FinishIdleStart
    }
}
