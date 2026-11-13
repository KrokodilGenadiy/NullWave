package com.zaus.nullwave.core.mvi.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.zaus.nullwave.core.mvi.MviViewModel
import com.zaus.nullwave.core.mvi.UiIntent
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain

/**
 * Main dispatcher and ViewModel ownership for one JVM test, created fresh per test class instance.
 * Call [setUp] from @Before and [tearDown] from @After, after releasing the feature's own fakes.
 * Pass [dispatcher] to runTest so the test and the ViewModels share one virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MviTestMain(val dispatcher: TestDispatcher = StandardTestDispatcher()) {
    /** Owns the test's ViewModels like a navigation entry; clearing it runs their onCleared. */
    val store = ViewModelStore()
    private var nextKey = 0

    fun setUp() = Dispatchers.setMain(dispatcher)

    /**
     * Registers [viewModel] so [tearDown] clears it, and returns it. Goes through the public provider,
     * since ViewModelStore.put is restricted to the lifecycle library.
     */
    fun <VM : ViewModel> own(viewModel: VM): VM {
        val handOver = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
        }
        return ViewModelProvider(store, handOver)["mvi-test-${nextKey++}", viewModel.javaClass]
    }

    /** Clears every owned ViewModel, runs the cleanup that scheduled, then restores Main. */
    fun tearDown() {
        try {
            store.clear()
            dispatcher.scheduler.runCurrent()
        } finally {
            Dispatchers.resetMain()
        }
    }
}

/**
 * Holds one UI binding, as a visible screen does, so declared sources collect. Cancel the returned
 * Job to unbind; the ViewModel's source stop timeout then applies. The binding ends with the test.
 */
fun TestScope.bind(viewModel: MviViewModel<*, *, *>): Job =
    backgroundScope.launch { viewModel.withUiBinding { awaitCancellation() } }.also { runCurrent() }

/** Sends [intent] through the UI capability and runs what it scheduled, without advancing time. */
suspend fun <I : UiIntent> TestScope.send(viewModel: MviViewModel<*, I, *>, intent: I) {
    viewModel.uiApi().onIntent(intent)
    runCurrent()
}

/**
 * Sends each of [examples] to a fresh ViewModel from [createViewModel], through the public UI API
 * only, so a new intent cannot compile cleanly and then crash on first use.
 *
 * First it fails, naming every concrete subtype of the sealed intent [I] that has no example, so
 * adding an intent also requires adding it here. A missing on<…> handler (or a handler that throws
 * in the initial State) fails inside the ViewModel's own coroutine; the enclosing runTest reports it
 * with the intent's name. No UI binding is held, so sources stay stopped while handlers run.
 * [createViewModel] should register each instance with [MviTestMain.own] so tearDown clears it.
 */
suspend inline fun <reified I : UiIntent> TestScope.assertHandlesEveryIntent(
    examples: List<I>,
    noinline createViewModel: () -> MviViewModel<*, I, *>,
) = assertHandlesEveryIntent(I::class, examples, createViewModel)

suspend fun <I : UiIntent> TestScope.assertHandlesEveryIntent(
    root: KClass<I>,
    examples: List<I>,
    createViewModel: () -> MviViewModel<*, I, *>,
) {
    require(root.isSealed) { "${root.qualifiedName} must be sealed so every intent can be listed." }
    val covered = examples.map { it::class }.toSet()
    val withoutExample = concreteSubtypes(root).filter { it !in covered }
    if (withoutExample.isNotEmpty()) {
        val names = withoutExample.map { it.qualifiedName ?: it.java.name }.sorted()
        throw AssertionError("No example for ${names.size} intent(s): ${names.joinToString()}")
    }
    for (example in examples) send(createViewModel(), example)
}

/** Leaves of a sealed hierarchy. An abstract open leaf is listed too: handlers match exact types. */
private fun concreteSubtypes(type: KClass<*>): List<KClass<*>> =
    if (type.isSealed) type.sealedSubclasses.flatMap(::concreteSubtypes) else listOf(type)