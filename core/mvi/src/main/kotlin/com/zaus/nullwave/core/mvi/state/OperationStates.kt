package com.zaus.nullwave.core.mvi.state

import com.zaus.nullwave.core.mvi.dsl.OperationKey

/** Before definition, keys are unavailable. After definition, an undeclared key is a caller error. */
class OperationStates internal constructor(
    private val initialized: Boolean,
    entries: Map<OperationKey<*>, OperationStatus>,
) {
    private val entries = entries.toMap()

    operator fun get(key: OperationKey<*>): OperationStatus =
        if (!initialized) OperationStatus.Unavailable else checkNotNull(entries[key]) {
            "Operation '${key.name}' is not declared by this ViewModel. Use the declaration's stable key."
        }

    override fun equals(other: Any?): Boolean =
        other is OperationStates && initialized == other.initialized && entries == other.entries
    override fun hashCode(): Int = 31 * initialized.hashCode() + entries.hashCode()
    override fun toString(): String = "OperationStates(initialized=$initialized, entries=$entries)"

    internal companion object {
        val Uninitialized = OperationStates(false, emptyMap())
    }
}
