package com.zaus.nullwave.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Uses generated Metro bindings and real ViewModelProvider/Store, without Android Bundle stubs. */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelOwnershipTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var graph: OwnershipGraph

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        graph = createGraph<OwnershipGraph>()
    }

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `factory is app scoped but ViewModels are retained only by their store and key`() = runTest(dispatcher) {
        val owner = owner()
        val first = provider(owner)["screen", OwnershipVm::class]
        val again = provider(owner)["screen", OwnershipVm::class]
        val differentKey = provider(owner)["other-screen", OwnershipVm::class]
        val differentOwner = provider(owner())["screen", OwnershipVm::class]

        assertSame(graph.metroViewModelFactory, graph.metroViewModelFactory)
        assertSame(first, again)
        assertNotSame(first, differentKey)
        assertNotSame(first, differentOwner)
        assertSame(first.dependency, differentKey.dependency)
        assertSame(first.dependency, differentOwner.dependency)
        assertEquals(3, graph.dependency.created)
    }

    @Test
    fun `new owner using retained store receives the same ViewModel and state`() = runTest(dispatcher) {
        val originalOwner = owner()
        val first = provider(originalOwner)[OwnershipVm::class]
        first.onIntent(Increment)
        runCurrent()

        val recreatedOwner = Owner(originalOwner.viewModelStore)
        val restored = provider(recreatedOwner)[OwnershipVm::class]

        assertSame(first, restored)
        assertEquals(1, restored.state.value.value)
        assertEquals(0, first.clears)
        assertEquals(1, graph.dependency.created)
    }

    @Test
    fun `clearing one store does not clear another store or app dependency`() = runTest(dispatcher) {
        val firstOwner = owner()
        val first = provider(firstOwner)[OwnershipVm::class]
        val other = provider(owner())[OwnershipVm::class]

        firstOwner.viewModelStore.clear()
        firstOwner.viewModelStore.clear()
        other.onIntent(Increment)
        runCurrent()

        assertEquals(1, first.clears)
        assertEquals(0, other.clears)
        assertEquals(1, other.state.value.value)
        assertSame(graph.dependency, other.dependency)
    }

    @Test
    fun `clear cancels MVI work and sources and ignores callbacks to the old instance`() = runTest(dispatcher) {
        val owner = owner()
        val vm = provider(owner)[OwnershipVm::class]
        backgroundScope.launch { vm.withUiBinding { awaitCancellation() } }
        vm.onIntent(Start)
        runCurrent()
        assertTrue(vm.state.value.loading)
        assertEquals(1, vm.sourceStarts)

        owner.viewModelStore.clear()
        runCurrent()
        val frozen = vm.state.value
        vm.onIntent(Increment)
        vm.collectEffects { error("Cleared VM must not deliver") }
        runCurrent()

        assertEquals(1, vm.workStops)
        assertEquals(1, vm.sourceStops)
        assertEquals(1, vm.clears)
        assertEquals(frozen, vm.state.value)
        val fresh = provider(owner)[OwnershipVm::class]
        assertNotSame(vm, fresh)
        assertFalse(fresh.state.value.loading)
        assertEquals(0, fresh.state.value.value)
    }

    @Test
    fun `assisted factory receives provider key and extras only on first creation`() = runTest(dispatcher) {
        val owner = owner()
        fun extras(seed: String) = MutableCreationExtras().apply { this[SeedKey] = seed }
        val first = provider(owner, extras("first"))["one", AssistedOwnershipVm::class]
        val same = provider(owner, extras("ignored"))["one", AssistedOwnershipVm::class]
        val second = provider(owner, extras("second"))["two", AssistedOwnershipVm::class]

        assertSame(first, same)
        assertEquals("first", first.seed)
        assertEquals("one", first.providerKey)
        assertEquals("second", second.seed)
        assertEquals("two", second.providerKey)
        assertNotSame(first, second)
        assertSame(first.dependency, second.dependency)
    }

    @Test
    fun `missing assisted input fails without poisoning the store`() = runTest(dispatcher) {
        val owner = owner()
        assertThrows(IllegalStateException::class.java) {
            provider(owner)["retry", AssistedOwnershipVm::class]
        }
        val extras = MutableCreationExtras().apply { this[SeedKey] = "valid" }
        val vm = provider(owner, extras)["retry", AssistedOwnershipVm::class]
        assertEquals("valid", vm.seed)
    }

    @Test
    fun `unregistered model is rejected rather than bypassing dependency injection`() = runTest(dispatcher) {
        assertThrows(IllegalArgumentException::class.java) {
            provider(owner())[UnregisteredVm::class]
        }
    }

    private fun owner() = Owner(ViewModelStore().also(stores::add))
    private fun provider(owner: ViewModelStoreOwner, extras: CreationExtras = CreationExtras.Empty) =
        ViewModelProvider.create(owner, graph.metroViewModelFactory, extras)

    private class Owner(override val viewModelStore: ViewModelStore) : ViewModelStoreOwner
}

@DependencyGraph(AppScope::class)
internal interface OwnershipGraph : ViewModelGraph {
    val dependency: OwnershipDependency
}

@Inject
@SingleIn(AppScope::class)
internal class OwnershipDependency {
    var created = 0
}

internal data class OwnershipState(val value: Int = 0, val loading: Boolean = false) : UiState
internal sealed interface OwnershipIntent : UiIntent
internal data object Increment : OwnershipIntent
internal data object Start : OwnershipIntent

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
internal class OwnershipVm(val dependency: OwnershipDependency) :
    BaseMviViewModel<OwnershipState, OwnershipIntent, UiEffect>(OwnershipState(), sourceStopTimeoutMillis = 0) {
    var clears = 0
    var sourceStarts = 0
    var sourceStops = 0
    var workStops = 0

    init {
        dependency.created++
        addCloseable { clears++ }
    }

    override fun MviDefinition<OwnershipState, OwnershipIntent, UiEffect>.define() {
        val work = idle<Unit, Unit>("work") {
            onStarted { copy(loading = true) }
            execute {
                try {
                    awaitCancellation()
                } finally {
                    workStops++
                }
            }
            onFinished { copy(loading = false) }
        }
        source("observed", flow = {
            flow<Unit> {
                sourceStarts++
                try {
                    awaitCancellation()
                } finally {
                    sourceStops++
                }
            }
        }, reducer = { this })
        on<Increment> { reduce { copy(value = value + 1) } }
        on<Start> { submit(work, Unit) }
    }
}

internal object SeedKey : CreationExtras.Key<String>

@AssistedInject
internal class AssistedOwnershipVm(
    @Assisted val seed: String,
    @Assisted val providerKey: String,
    val dependency: OwnershipDependency,
) : BaseMviViewModel<OwnershipState, OwnershipIntent, UiEffect>(OwnershipState()) {
    override fun MviDefinition<OwnershipState, OwnershipIntent, UiEffect>.define() = Unit

    @AssistedFactory
    @ViewModelAssistedFactoryKey(AssistedOwnershipVm::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): AssistedOwnershipVm = create(
            seed = checkNotNull(extras[SeedKey]) { "Seed is required" },
            providerKey = checkNotNull(extras[ViewModelProvider.VIEW_MODEL_KEY]),
        )

        fun create(@Assisted seed: String, @Assisted providerKey: String): AssistedOwnershipVm
    }
}

private class UnregisteredVm : BaseMviViewModel<OwnershipState, OwnershipIntent, UiEffect>(OwnershipState()) {
    override fun MviDefinition<OwnershipState, OwnershipIntent, UiEffect>.define() = Unit
}
