package com.zaus.nullwave.core.result

/**
 * An expected outcome with a caller-defined error type. It has no MVI or Android dependencies.
 *
 * The boundary owning an expected failure converts it to [Failure]. Cancellation and programming
 * errors continue to propagate as exceptions; this contract does not catch or classify them.
 */
sealed interface AppResult<out T, out E> {
    data class Success<out T>(val value: T) : AppResult<T, Nothing>

    /**
     * [error] describes the failure for application logic; [cause] retains the original diagnostic
     * object, including its stack and cause chain. [originalMessage] captures its message at creation
     * or an explicit diagnostic message when no exception exists. Either diagnostic may be null.
     *
     * Diagnostics are not localized UI text. Keep Throwable out of saved/UI state; persistent
     * diagnostics need a separate serializable snapshot appropriate to the owning feature.
     */
    data class Failure<out E>(
        val error: E,
        val cause: Throwable? = null,
        val originalMessage: String? = cause?.message,
    ) : AppResult<Nothing, E>
}

/** An expected outcome for a command that has no result value. */
typealias EmptyResult<E> = AppResult<Unit, E>
