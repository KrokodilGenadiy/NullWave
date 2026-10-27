package com.zaus.nullwave.core.playback.api.model

enum class PlaybackPhase { Idle, Buffering, Ready, Ended }

enum class PlaybackSuppression { None, AudioFocus, Output, Other }

enum class PlaybackProblemCode {
    AccessDenied, NotFound, Io, UnsupportedMedia, DecoderFailure, OutputUnavailable, Unknown,
}

/** UI-safe failure identity and classification. Original Throwable/message stay with the owner. */
data class PlaybackProblem(
    val id: PlaybackProblemId,
    val code: PlaybackProblemCode,
    val target: PlaybackItemToken? = null,
)

/** Orthogonal engine facts; [isPlaying] is derived, never an independently writable flag. */
data class PlaybackTransport(
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val playRequested: Boolean = false,
    val suppression: PlaybackSuppression = PlaybackSuppression.None,
    val problem: PlaybackProblem? = null,
) {
    init {
        require(problem == null || phase == PlaybackPhase.Idle) { "A fatal playback problem belongs to Idle." }
    }

    val isPlaying: Boolean get() = phase == PlaybackPhase.Ready && playRequested &&
        suppression == PlaybackSuppression.None && problem == null
}
