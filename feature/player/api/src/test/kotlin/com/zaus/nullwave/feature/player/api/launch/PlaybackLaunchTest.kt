package com.zaus.nullwave.feature.player.api.launch

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.testing.MviTestMain
import com.zaus.nullwave.core.mvi.testing.send
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Command replies through the shared block; engine observation is covered by the screens' tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackLaunchTest {
    private val mvi = MviTestMain()

    @Before
    fun setUp() = mvi.setUp()

    @After
    fun tearDown() = mvi.tearDown()

    @Test
    fun `confirmed access denial closes access even when the screen shows no text for it`() = runTest(mvi.dispatcher) {
        val vm = screen(reply = denied, message = { null })

        send(vm, Start)
        runCurrent()

        assertTrue(vm.snapshot.value.state.closed)
        assertNull(vm.snapshot.value.state.launch.notice)
    }

    @Test
    fun `confirmed access denial closes access and keeps its text`() = runTest(mvi.dispatcher) {
        val vm = screen(reply = denied, message = { UiText.Literal("denied") })

        send(vm, Start)
        runCurrent()

        assertTrue(vm.snapshot.value.state.closed)
        assertEquals(UiText.Literal("denied"), vm.snapshot.value.state.launch.notice)
    }

    @Test
    fun `other failure without text leaves the screen unchanged`() = runTest(mvi.dispatcher) {
        val vm = screen(reply = AppResult.Failure(PlaybackCommandError.Superseded), message = { null })

        send(vm, Start)
        runCurrent()

        assertFalse(vm.snapshot.value.state.closed)
        assertNull(vm.snapshot.value.state.launch.notice)
        assertTrue(vm.snapshot.value.state.launch.monitoring)
    }

    private fun screen(
        reply: EmptyResult<PlaybackCommandError>,
        message: (PlaybackCommandError) -> UiText?,
    ) = ScreenViewModel(ReplyingController(reply), message).also { mvi.own(it) }

    private val denied: EmptyResult<PlaybackCommandError> =
        AppResult.Failure(PlaybackCommandError.SourceFailure(TrackId("primary:1"), PlaybackSourceError.AccessDenied))
}

private data class ScreenState(
    val closed: Boolean = false,
    val launch: PlaybackLaunchState = PlaybackLaunchState(),
) : UiState

private data object Start : UiIntent

private class ScreenViewModel(
    private val controller: PlaybackController,
    private val message: (PlaybackCommandError) -> UiText?,
) : BaseMviViewModel<ScreenState, Start, Nothing>(ScreenState()) {
    override fun MviDefinition<ScreenState, Start, Nothing>.define() {
        val play = playbackLaunch(
            name = "screen.play",
            controller = controller,
            launchState = { launch },
            withLaunchState = { copy(launch = it) },
            closeAccess = { copy(closed = true) },
            message = message,
        )
        on<Start> { submit(play.operation, PlaybackQueueRequest.create(listOf(TrackId("primary:1")), 0)) }
    }
}

private class ReplyingController(private val reply: EmptyResult<PlaybackCommandError>) : PlaybackController {
    override val publishedSnapshot: PlaybackSnapshot? = null
    override fun observeState(): Flow<PlaybackConnection> = flowOf(PlaybackConnection.Disconnected)
    override fun observeProgress(): Flow<PlaybackProgress?> = flowOf(null)
    override suspend fun reconnect(): EmptyResult<PlaybackConnectionError> = AppResult.Success(Unit)

    override suspend fun execute(
        command: PlaybackCommand,
        beforeDispatch: (PlaybackSnapshot?) -> Unit,
    ): EmptyResult<PlaybackCommandError> {
        beforeDispatch(null)
        return reply
    }
}
