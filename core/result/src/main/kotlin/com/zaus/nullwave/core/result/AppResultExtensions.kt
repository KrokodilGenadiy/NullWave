package com.zaus.nullwave.core.result

/** Transforms a success; a failure is returned unchanged, including its diagnostics. */
inline fun <T, E, R> AppResult<T, E>.map(transform: (T) -> R): AppResult<R, E> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

/** Reclassifies a failure while preserving its original cause and captured diagnostic message. */
inline fun <T, E, F> AppResult<T, E>.mapError(transform: (E) -> F): AppResult<T, F> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> AppResult.Failure(transform(error), cause, originalMessage)
    }

/** Observes success and returns this same result. Callback exceptions propagate unchanged. */
inline fun <T, E> AppResult<T, E>.onSuccess(action: (T) -> Unit): AppResult<T, E> {
    if (this is AppResult.Success) action(value)
    return this
}

/** Observes the whole failure so diagnostics remain available to the owning boundary. */
inline fun <T, E> AppResult<T, E>.onFailure(
    action: (AppResult.Failure<E>) -> Unit,
): AppResult<T, E> {
    if (this is AppResult.Failure) action(this)
    return this
}

/** Produces a value from either branch, without catching exceptions from either callback. */
inline fun <T, E, R> AppResult<T, E>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppResult.Failure<E>) -> R,
): R = when (this) {
    is AppResult.Success -> onSuccess(value)
    is AppResult.Failure -> onFailure(this)
}

/** Discards a successful value; a failure and its diagnostics remain unchanged. */
fun <T, E> AppResult<T, E>.asEmptyResult(): EmptyResult<E> = map { }
