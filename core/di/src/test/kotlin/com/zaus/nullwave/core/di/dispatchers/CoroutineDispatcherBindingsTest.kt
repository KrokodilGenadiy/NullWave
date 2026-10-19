package com.zaus.nullwave.core.di.dispatchers

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertSame
import org.junit.Test

class CoroutineDispatcherBindingsTest {
    @Test
    fun `app scope aggregates shared dispatcher bindings with distinct qualifiers`() {
        val graph = createGraph<DispatcherTestGraph>()

        assertSame(Dispatchers.IO, graph.io)
        assertSame(Dispatchers.Default, graph.default)
        assertSame(Dispatchers.Main, graph.main)
    }

    @Test
    fun `test graph replaces production dispatchers without changing consumers`() {
        val scheduler = TestCoroutineScheduler()
        val io = StandardTestDispatcher(scheduler, "io")
        val default = StandardTestDispatcher(scheduler, "default")
        val main = StandardTestDispatcher(scheduler, "main")
        val graph = createGraphFactory<OverriddenDispatcherTestGraph.Factory>().create(io, default, main)

        assertSame(io, graph.io)
        assertSame(default, graph.default)
        assertSame(main, graph.main)
    }
}

internal interface DispatcherAccess {
    @IoDispatcher val io: CoroutineDispatcher
    @DefaultDispatcher val default: CoroutineDispatcher
    @MainDispatcher val main: CoroutineDispatcher
}

@DependencyGraph(AppScope::class)
internal interface DispatcherTestGraph : DispatcherAccess, ViewModelGraph

@DependencyGraph(AppScope::class, excludes = [CoroutineDispatcherBindings::class])
internal interface OverriddenDispatcherTestGraph : DispatcherAccess, ViewModelGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides @IoDispatcher io: CoroutineDispatcher,
            @Provides @DefaultDispatcher default: CoroutineDispatcher,
            @Provides @MainDispatcher main: CoroutineDispatcher,
        ): OverriddenDispatcherTestGraph
    }
}
