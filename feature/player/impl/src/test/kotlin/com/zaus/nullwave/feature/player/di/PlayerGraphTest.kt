package com.zaus.nullwave.feature.player.di

import androidx.lifecycle.viewmodel.CreationExtras
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.feature.player.api.ui.PlayerHost
import com.zaus.nullwave.feature.player.presentation.PlayerFixture
import com.zaus.nullwave.feature.player.presentation.PlayerViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerGraphTest {
    private val fixture = PlayerFixture()

    @Before fun setUp() = fixture.setUp()
    @After fun tearDown() = fixture.tearDown()

    @Test
    fun `production contribution reaches Metro factory and creates unscoped Player instances`() = with(fixture) {
        runTest(dispatcher) {
            val graph = createGraphFactory<PlayerTestGraph.Factory>().create(playback)
            val first = graph.metroViewModelFactory.create(PlayerViewModel::class, CreationExtras.Empty)
                .also { store.put("first", it) }
            val second = graph.metroViewModelFactory.create(PlayerViewModel::class, CreationExtras.Empty)
                .also { store.put("second", it) }
            assertNotSame(first, second)
            assertNotNull(graph.playerHost)
            // Creating the host never replaces the shared factory or caches a VM in the graph.
            assertSame(graph.metroViewModelFactory, graph.metroViewModelFactory)
            assertEquals(0, playback.stateObservations)
            assertEquals(0, playback.progressObservations)
            val snapshot = connected()
            control(snapshot)
            bind(first)
            assertSame(snapshot, first.snapshot.value.state.connection)
        }
    }
}

@DependencyGraph(AppScope::class)
internal interface PlayerTestGraph : ViewModelGraph {
    val playerHost: PlayerHost

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides controller: PlaybackController): PlayerTestGraph
    }
}
