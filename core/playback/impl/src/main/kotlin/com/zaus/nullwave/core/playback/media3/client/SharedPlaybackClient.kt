package com.zaus.nullwave.core.playback.media3.client

import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.media3.PlaybackServiceRegistry
import com.zaus.nullwave.core.playback.media3.PlaybackSessionProtocol
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A narrow connection seam: tests exercise leases/polling without mocking Android or Media3 Player. */
internal fun interface PlaybackConnector {
    suspend fun connect(onDisconnected: () -> Unit): PlaybackClientConnection
}

internal interface PlaybackClientConnection {
    /** Every connection, including a fake, identifies the service confirmed by its handshake. */
    val sessionId: PlaybackSessionId
    suspend fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError>
    fun close()
}

internal class PlaybackConnectException(
    val reason: PlaybackConnectionError,
    cause: Throwable? = null,
) : Exception("Playback connection failed: $reason", cause)

/** Main-confined shared client. Its leases own only the UI connection, never the service engine. */
internal class SharedPlaybackClient(
    private val main: CoroutineDispatcher,
    private val registry: PlaybackServiceRegistry,
    private val connector: PlaybackConnector,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + main),
    private val connectionTimeoutMs: Long = PlaybackSessionProtocol.CONNECTION_TIMEOUT_MS,
    private val progressIntervalMs: Long = PlaybackSessionProtocol.PROGRESS_INTERVAL_MS,
) : PlaybackController {
    private val state = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Disconnected)
    private val progress = MutableStateFlow<PlaybackProgress?>(null)
    private var leases = 0
    private var progressLeases = 0
    private var connecting: Attempt? = null
    private var active: Active? = null
    private var latestAttempt: Attempt? = null
    private var stateObserver: Job? = null
    private var progressObserver: Job? = null

    override fun observeState(): Flow<PlaybackConnection> = flow {
        // flowOn may enter an already-cancelled producer before its first emission.
        currentCoroutineContext().ensureActive()
        acquire(progress = false)
        try { emitAll(state) }
        finally { release(progress = false) }
    }.flowOn(main).conflate()

    override fun observeProgress(): Flow<PlaybackProgress?> = flow {
        currentCoroutineContext().ensureActive()
        acquire(progress = true)
        try { emitAll(progress) }
        finally { release(progress = true) }
    }.flowOn(main).conflate()

    override suspend fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> = withContext(main) {
        val reply = acquire(progress = false)
        try {
            val connection = reply.await()
            val snapshot = registry.snapshot.value
            if (active?.connection !== connection || snapshot != null && connection.sessionId != snapshot.sessionId) {
                active?.takeIf { it.connection === connection }?.let { disconnected(it.attempt) }
                return@withContext AppResult.Failure(PlaybackCommandError.ConnectionUnavailable)
            }
            // Capabilities describe UI availability, not admission. Even a rejected transport
            // action must reach the owner to revoke an older pending replacement.
            registry.clearConnectionError()
            connection.execute(command)
        } catch (failure: PlaybackConnectException) {
            AppResult.Failure(
                if (failure.reason == PlaybackConnectionError.StartNotAllowed) PlaybackCommandError.StartNotAllowed
                else PlaybackCommandError.ConnectionUnavailable,
                failure.cause ?: failure,
            )
        } finally {
            release(progress = false)
        }
    }

    private fun acquire(progress: Boolean): CompletableDeferred<PlaybackClientConnection> {
        leases++
        if (progress) progressLeases++
        val reply = ensureConnection()
        updateProgressDemand()
        if (progress) refreshProgress()
        return reply
    }

    private fun release(progress: Boolean) {
        leases--
        if (progress) progressLeases--
        if (leases == 0) {
            val oldAttempt = connecting
            val oldActive = active
            connecting = null
            active = null
            latestAttempt = null
            stateObserver?.cancel()
            stateObserver = null
            progressObserver?.cancel()
            progressObserver = null
            oldAttempt?.work?.cancel()
            oldAttempt?.reply?.cancel()
            oldActive?.connection?.close()
            if (leases == 0 && latestAttempt == null) this.progress.value = null
            // A null progress publication or platform release can synchronously create a new lease.
            if (leases == 0 && latestAttempt == null) state.value = PlaybackConnection.Disconnected
        } else updateProgressDemand()
    }

    private fun ensureConnection(): CompletableDeferred<PlaybackClientConnection> {
        active?.let { return CompletableDeferred(it.connection) }
        connecting?.let { return it.reply }
        val attempt = Attempt(CompletableDeferred(scope.coroutineContext.job))
        val work = scope.launch(main, start = CoroutineStart.LAZY) {
            try {
                val connection = withTimeout(connectionTimeoutMs) {
                    connector.connect { scope.launch(main) { disconnected(attempt) } }
                }
                if (connecting !== attempt || leases == 0 || attempt.disconnected) {
                    connection.close()
                    if (connecting === attempt) {
                        connecting = null
                        state.value = PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected)
                    }
                    attempt.reply.completeExceptionally(PlaybackConnectException(PlaybackConnectionError.ServiceDisconnected))
                    return@launch
                }
                connecting = null
                active = Active(attempt, connection)
                startStateObserver(active!!)
                updateProgressDemand()
                attempt.reply.complete(connection)
            } catch (failure: Throwable) {
                if (connecting === attempt) {
                    connecting = null
                    val reason = when (failure) {
                        is PlaybackConnectException -> failure.reason
                        else -> PlaybackConnectionError.ConnectionFailed
                    }
                    state.value = PlaybackConnection.Unavailable(reason)
                }
                val reported = if (failure is TimeoutCancellationException) {
                    PlaybackConnectException(PlaybackConnectionError.ConnectionFailed, failure)
                } else failure
                attempt.reply.completeExceptionally(reported)
            }
        }
        attempt.work = work
        connecting = attempt
        latestAttempt = attempt
        work.invokeOnCompletion { failure -> if (failure != null) attempt.reply.completeExceptionally(failure) }
        state.value = PlaybackConnection.Connecting
        work.start()
        return attempt.reply
    }

    private fun disconnected(attempt: Attempt) {
        attempt.disconnected = true
        val current = active ?: return
        if (current.attempt !== attempt) return
        active = null
        stateObserver?.cancel()
        stateObserver = null
        progressObserver?.cancel()
        progressObserver = null
        current.connection.close()
        if (latestAttempt === attempt) progress.value = null
        if (latestAttempt === attempt) state.value = PlaybackConnection.Unavailable(PlaybackConnectionError.ServiceDisconnected)
    }

    private fun startStateObserver(current: Active) {
        val observer = scope.launch(main, start = CoroutineStart.LAZY) {
            combine(registry.snapshot, registry.failure) { snapshot, failure -> snapshot to failure }.collect { (snapshot, failure) ->
                if (active !== current) return@collect
                if (snapshot != null && current.connection.sessionId != snapshot.sessionId) {
                    disconnected(current.attempt)
                    return@collect
                }
                state.value = when {
                    failure != null -> PlaybackConnection.Unavailable(failure)
                    snapshot != null -> PlaybackConnection.Connected(snapshot)
                    else -> PlaybackConnection.Connecting
                }
            }
        }
        stateObserver = observer
        observer.start()
    }

    private fun updateProgressDemand() {
        if (progressLeases == 0 || active == null) {
            progressObserver?.cancel()
            progressObserver = null
            progress.value = null
            return
        }
        if (progressObserver != null) return
        val current = active!!
        val observer = scope.launch(main, start = CoroutineStart.LAZY) {
            combine(registry.anchor, registry.snapshot, registry.failure) { _, _, _ -> Unit }.collectLatest {
                if (active !== current) return@collectLatest
                var sample = refreshProgress()
                while (isActive && active === current && sample?.advancing == true) {
                    delay(progressIntervalMs)
                    sample = refreshProgress()
                }
            }
        }
        progressObserver = observer
        observer.start()
    }

    private fun refreshProgress(): PlaybackProgress? {
        if (active == null || progressLeases == 0 || registry.failure.value != null) {
            progress.value = null
            return null
        }
        val current = active ?: return null
        val snapshot = registry.snapshot.value
        val sample = if (snapshot != null && current.connection.sessionId == snapshot.sessionId) {
            registry.sampleProgress()?.takeIf { it.matches(snapshot) }
        } else null
        progress.value = sample
        return sample
    }

    private class Attempt(val reply: CompletableDeferred<PlaybackClientConnection>) {
        lateinit var work: Job
        var disconnected = false
    }
    private class Active(val attempt: Attempt, val connection: PlaybackClientConnection)

}
