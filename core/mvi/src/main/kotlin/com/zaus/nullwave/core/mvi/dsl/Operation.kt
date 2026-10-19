package com.zaus.nullwave.core.mvi.dsl

/**
 * A typed operation declared once in an MVI definition. It exposes no execution or state access:
 * submit its immutable input or cancel it through an intent handler's scope.
 *
 * The owning ViewModel is checked at dispatch; a handle cannot be used by another ViewModel.
 */
class Operation<A> internal constructor(
    internal val owner: Any,
    private val submitRequest: (A) -> Boolean,
    private val cancelRequests: () -> Boolean,
) {
    internal fun submit(input: A): Boolean = submitRequest(input)

    internal fun cancel(): Boolean = cancelRequests()
}
