package com.zaus.nullwave.core.mvi.runtime

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.currentCoroutineContext

/** Runtime counterpart of the DSL boundary, including explicitly captured receivers. */
internal object MviAccess {
    private enum class Phase { Definition, Intent, Reducer, Work }
    private class Access(
        val owner: Any,
        val phase: Phase,
        val intent: Any? = null,
        val previous: Access? = null,
    )
    private val access = ThreadLocal<Access?>()

    // Unlike a ThreadLocal value left by an inline resume, this belongs to the actual caller.
    private class WorkContext : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<WorkContext>
    }

    fun checkMutationAllowed(owner: Any) {
        val phase = access.get().find(owner)?.phase
        check(phase == null || phase == Phase.Intent) {
            "MVI mutation is forbidden inside a definition, reducer or execute/source block."
        }
    }

    /** Structured work cannot acquire a UI capability or lifetime, even through another VM. */
    suspend fun checkUiEntryAllowed() {
        check(currentCoroutineContext()[WorkContext] == null) {
            "Acquire UI API, bind UI and collect effects from the UI/platform boundary, not from MVI work."
        }
    }

    fun checkDefinitionScope(owner: Any) {
        val current = access.get()
        check(current != null && current.phase == Phase.Definition && current.owner === owner) {
            "MVI declarations are only available while defining the screen."
        }
    }

    fun checkIntentScope(owner: Any, scope: Any) {
        val current = access.get()
        check(current != null && current.owner === owner && current.phase == Phase.Intent && current.intent === scope) {
            "An intent scope may only be used during its own handler."
        }
    }

    fun <T> defining(owner: Any, block: () -> T): T = within(owner, Phase.Definition, block = block)
    fun <T> handling(owner: Any, scope: Any, block: () -> T): T = within(owner, Phase.Intent, scope, block)
    fun <T> reducing(owner: Any, block: () -> T): T = within(owner, Phase.Reducer, block = block)

    /** Publication is an external observer boundary for this owner, not for other active owners. */
    fun <T> publishing(owner: Any, block: () -> T): T = withAccess(access.get().without(owner), block)

    /** An operation/source belongs to its VM scope, independently of another VM's caller stack. */
    fun workContext(owner: Any): CoroutineContext =
        access.asContextElement(Access(owner, Phase.Work)) + WorkContext()

    /**
     * Runtime scheduling and UI collection must not inherit an inline caller's ThreadLocal phase.
     * This only sets the synchronous access phase; it does not remove a coroutine's WorkContext.
     * Public UI entries must still check work ownership before entering this context.
     */
    fun neutralContext(): CoroutineContext = access.asContextElement(null)

    private fun <T> within(owner: Any, phase: Phase, intent: Any? = null, block: () -> T): T =
        withAccess(Access(owner, phase, intent, access.get().without(owner)), block)

    private fun <T> withAccess(value: Access?, block: () -> T): T {
        val previous = access.get()
        access.set(value)
        return try {
            block()
        } finally {
            access.set(previous)
        }
    }

    private fun Access?.find(owner: Any): Access? = when {
        this == null -> null
        this.owner === owner -> this
        else -> previous.find(owner)
    }

    private fun Access?.without(owner: Any): Access? = when {
        this == null -> null
        this.owner === owner -> previous
        else -> Access(this.owner, phase, intent, previous.without(owner))
    }
}
