package com.zaus.nullwave.feature.player.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackProblem
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import com.zaus.nullwave.feature.player.api.ui.PlayerHost
import com.zaus.nullwave.feature.player.presentation.PlayerViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Production host and generated Metro registrations; only the backend and destinations are fake. */
@DependencyGraph(AppScope::class)
internal interface PlayerHostTestGraph : ViewModelGraph {
    val playerHost: PlayerHost

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides playback: PlaybackController): PlayerHostTestGraph
    }
}

internal class PlayerHostActivityFixture {
    val playback = RecordingHostPlaybackController()
    val graph = createGraphFactory<PlayerHostTestGraph.Factory>().create(playback)
    val hostVisible = mutableStateOf(true)
    val entryOwners = mutableMapOf<HostDestination, ViewModelStoreOwner>()
    var libraryRequests = 0
}

class PlayerHostTestActivity : ComponentActivity() {
    private lateinit var activeFixture: PlayerHostActivityFixture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeFixture = checkNotNull(fixture)
        setContent {
            NullWaveTheme {
                CompositionLocalProvider(LocalMetroViewModelFactory provides activeFixture.graph.metroViewModelFactory) {
                    if (activeFixture.hostVisible.value) {
                        activeFixture.graph.playerHost.Content(
                            owner = this,
                            lifecycleOwner = this,
                            onOpenLibrary = { activeFixture.libraryRequests++ },
                        ) {
                            ProbeNavigation(activeFixture)
                        }
                    } else {
                        Text("Host removed")
                    }
                }
            }
        }
    }

    /** Do not create a VM in the test before proving the production host stored it on this owner. */
    internal fun playerViewModel(): PlayerViewModel {
        check(PlayerViewModelKey in viewModelStore.keys()) { "PlayerHost did not use the Activity ViewModelStore/key" }
        return ViewModelProvider.create(this, activeFixture.graph.metroViewModelFactory)[PlayerViewModelKey, PlayerViewModel::class]
    }

    companion object {
        internal var fixture: PlayerHostActivityFixture? = null
    }
}

internal enum class HostDestination : NavKey { Library, Search }

/** Real entry decorators create independent child owners, as in the application's navigation host. */
@Composable
private fun ProbeNavigation(fixture: PlayerHostActivityFixture) {
    val backStack = remember { mutableStateListOf<NavKey>(HostDestination.Library) }
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { if (backStack.size > 1) backStack.removeLast() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HostDestination> { destination ->
                val owner = checkNotNull(LocalViewModelStoreOwner.current)
                SideEffect { fixture.entryOwners[destination] = owner }
                Column(Modifier.testTag("host_${destination.name.lowercase()}")) {
                    Text(destination.name)
                    if (destination == HostDestination.Library) {
                        Button(
                            onClick = { backStack.add(HostDestination.Search) },
                            modifier = Modifier.testTag("host_open_search"),
                        ) { Text("Open Search") }
                    } else {
                        Button(
                            onClick = { backStack.removeLast() },
                            modifier = Modifier.testTag("host_back_library"),
                        ) { Text("Back to Library") }
                    }
                }
            }
        },
    )
}

/** Counts actual collection lifetimes; all commands are recorded without a service or audio engine. */
internal class RecordingHostPlaybackController : PlaybackController {
    val connection = MutableStateFlow<PlaybackConnection>(hostProblem())
    val progress = MutableStateFlow<PlaybackProgress?>(null)
    val stateStarts = AtomicInteger()
    val progressStarts = AtomicInteger()
    val activeStates = AtomicInteger()
    val activeProgress = AtomicInteger()
    val commands = mutableListOf<PlaybackCommand>()
    var commandReply: EmptyResult<PlaybackCommandError> = AppResult.Success(Unit)
    var reconnects = 0

    override fun observeState(): Flow<PlaybackConnection> = flow {
        stateStarts.incrementAndGet()
        activeStates.incrementAndGet()
        try { emitAll(connection) } finally { activeStates.decrementAndGet() }
    }

    override fun observeProgress(): Flow<PlaybackProgress?> = flow {
        progressStarts.incrementAndGet()
        activeProgress.incrementAndGet()
        try { emitAll(progress) } finally { activeProgress.decrementAndGet() }
    }

    override suspend fun execute(
        command: PlaybackCommand,
        beforeDispatch: (PlaybackSnapshot?) -> Unit,
    ): EmptyResult<PlaybackCommandError> {
        beforeDispatch((connection.value as? PlaybackConnection.Connected)?.snapshot)
        commands.add(command)
        return commandReply
    }

    override suspend fun reconnect(): EmptyResult<PlaybackConnectionError> {
        reconnects++
        return AppResult.Success(Unit)
    }
}

internal fun hostProblem(
    code: PlaybackProblemCode = PlaybackProblemCode.DecoderFailure,
    problemId: String = "host-problem",
): PlaybackConnection.Connected {
    val session = PlaybackSessionId("host-session")
    val entry = QueueEntry(
        QueueEntryId("host-entry"),
        MediaTrack(TrackId("host-track"), "Host track", "Artist", "Album", 120_000),
    )
    val target = PlaybackItemToken(session, entry.entryId, activation = 0)
    return PlaybackConnection.Connected(PlaybackSnapshot.create(
        sessionId = session,
        queue = PlaybackQueue.create(entries = listOf(entry), current = target),
        transport = PlaybackTransport(problem = PlaybackProblem(PlaybackProblemId(problemId), code, target)),
        capabilities = setOf(PlaybackCapability.Retry),
    ))
}
