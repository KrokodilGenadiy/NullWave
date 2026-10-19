package com.zaus.nullwave.di

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real Android registry/Bundle round trips, not an OS process-kill test. */
class ViewModelRestorationInstrumentedTest {
    @Test
    fun restoresOnlySelectedFieldsIntoNewVmAndGraph() = runBlocking(Dispatchers.Main.immediate) {
        val graph = graph()
        val owner = RegistryOwner(defaultArgs = Bundle().apply { putString("query", "initial") })
        val first = owner.provider(graph)["screen", RestorationVm::class]
        assertEquals("initial", first.state.value.query)
        first.onIntent(RestorationIntent.Edit("restored query", "track-42"))
        first.onIntent(RestorationIntent.Start)
        first.onIntent(RestorationIntent.Notify)
        yield()
        assertTrue(first.state.value.loading)
        assertEquals(1, first.workStarts)

        val saved = owner.save().parcelCopy()
        owner.destroy()
        yield()
        assertEquals(1, first.clears)
        assertEquals(1, first.workStops)

        val newGraph = graph()
        val restoredOwner = RegistryOwner(
            restored = saved,
            defaultArgs = Bundle().apply { putString("query", "must not replace restored input") },
        )
        try {
            val restored = restoredOwner.provider(newGraph)["screen", RestorationVm::class]
            assertNotSame(first, restored)
            assertNotSame(first.dependency, restored.dependency)
            assertEquals(RestorationState("restored query", "track-42"), restored.state.value)
            assertEquals(0, restored.workStarts)

            val effects = mutableListOf<RestorationEffect>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { restored.collectEffects(effects::add) }
            yield()
            assertTrue(effects.isEmpty())
            // Prove the collector was active, rather than mistaking an unstarted collector for no replay.
            restored.onIntent(RestorationIntent.Notify)
            yield()
            assertEquals(listOf(RestorationEffect), effects)
            collector.cancelAndJoin()
        } finally {
            restoredOwner.destroy()
        }
    }

    @Test
    fun differentProviderKeysRestoreIndependentlyInReverseCreationOrder() = runBlocking(Dispatchers.Main.immediate) {
        val graph = graph()
        val owner = RegistryOwner()
        owner.provider(graph)["first", RestorationVm::class].onIntent(RestorationIntent.Edit("alpha"))
        owner.provider(graph)["second", RestorationVm::class].onIntent(RestorationIntent.Edit("beta"))
        val saved = owner.save().parcelCopy()
        owner.destroy()

        val restoredOwner = RegistryOwner(restored = saved)
        try {
            val restoredGraph = graph()
            val second = restoredOwner.provider(restoredGraph)["second", RestorationVm::class]
            val first = restoredOwner.provider(restoredGraph)["first", RestorationVm::class]
            assertEquals("beta", second.state.value.query)
            assertEquals("alpha", first.state.value.query)
            assertNotSame(first, second)
            assertSame(first.dependency, second.dependency)
        } finally {
            restoredOwner.destroy()
        }
    }

    @Test
    fun replacementOwnerWithRetainedStoreKeepsVmAndContinuesSavingHandle() = runBlocking(Dispatchers.Main.immediate) {
        val graph = graph()
        val owner = RegistryOwner()
        val first = owner.provider(graph)["screen", RestorationVm::class]
        first.onIntent(RestorationIntent.Edit("before recreation"))
        val saved = owner.save().parcelCopy()
        owner.destroy(clearStore = false)

        val replacement = RegistryOwner(restored = saved, viewModelStore = owner.viewModelStore)
        val retained = replacement.provider(graph)["screen", RestorationVm::class]
        assertSame(first, retained)
        assertEquals(0, retained.clears)
        retained.onIntent(RestorationIntent.Edit("after recreation", "track-7"))
        val savedAgain = replacement.save().parcelCopy()
        replacement.destroy()

        val restoredOwner = RegistryOwner(restored = savedAgain)
        try {
            val restored = restoredOwner.provider(graph())["screen", RestorationVm::class]
            assertEquals(RestorationState("after recreation", "track-7"), restored.state.value)
            assertEquals(1, first.clears)
        } finally {
            restoredOwner.destroy()
        }
    }

    @Test
    fun missingOwnerExtrasFailInsteadOfSilentlyCreatingUnrestorableState() = runBlocking(Dispatchers.Main.immediate) {
        val graph = graph()
        val owner = RegistryOwner()
        try {
            val failure = assertThrows(IllegalArgumentException::class.java) {
                ViewModelProvider.create(owner, graph.metroViewModelFactory, CreationExtras.Empty)[
                    "screen", RestorationVm::class,
                ]
            }
            assertTrue(failure.message.orEmpty().contains("SAVED_STATE_REGISTRY_OWNER_KEY"))
            val valid = owner.provider(graph)["screen", RestorationVm::class]
            assertFalse(valid.state.value.loading)
            assertEquals(1, graph.dependency.created)
        } finally {
            owner.destroy()
        }
    }

    private fun graph(): RestorationGraph = createGraph()

    private class RegistryOwner(
        restored: Bundle? = null,
        defaultArgs: Bundle = Bundle(),
        override val viewModelStore: ViewModelStore = ViewModelStore(),
    ) : SavedStateRegistryOwner, ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val lifecycle = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val savedStateRegistry get() = controller.savedStateRegistry
        override val defaultViewModelProviderFactory = ViewModelProvider.NewInstanceFactory()
        override val defaultViewModelCreationExtras = MutableCreationExtras().apply {
            this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@RegistryOwner
            this[VIEW_MODEL_STORE_OWNER_KEY] = this@RegistryOwner
            this[DEFAULT_ARGS_KEY] = defaultArgs
        }

        init {
            controller.performAttach()
            enableSavedStateHandles()
            controller.performRestore(restored)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun provider(graph: RestorationGraph) = ViewModelProvider.create(this, graph.metroViewModelFactory)
        fun save() = Bundle().also(controller::performSave)

        fun destroy(clearStore: Boolean = true) {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            if (clearStore) viewModelStore.clear()
        }
    }

    private fun Bundle.parcelCopy(): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(this)
            parcel.setDataPosition(0)
            checkNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            parcel.recycle()
        }
    }
}
