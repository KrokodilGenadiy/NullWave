package com.zaus.nullwave.core.playback.media3.engine

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.zaus.nullwave.core.playback.api.PlaybackClock
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.command.PlaybackCommand
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackPhase
import com.zaus.nullwave.core.playback.api.model.PlaybackProblem
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemId
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackSuppression
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.core.playback.api.model.RepeatMode
import com.zaus.nullwave.core.playback.media3.state.PlaybackServiceRegistry
import com.zaus.nullwave.core.playback.runtime.PlaybackIdentities
import com.zaus.nullwave.core.playback.runtime.QueueCommit
import com.zaus.nullwave.core.playback.runtime.QueueCommitTarget
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.EmptyResult
import java.util.UUID

/**
 * Application-looper adapter of the service's raw player. The session wrapper calls the owner,
 * while this target calls the raw player, avoiding recursive command admission.
 */
@UnstableApi
internal class Media3QueueCommitTarget(
    private val player: Player,
    private val identities: PlaybackIdentities,
    private val registry: PlaybackServiceRegistry,
    private val clock: PlaybackClock,
    private val acquireForeground: () -> AppResult<AutoCloseable, PlaybackCommandError>,
) : QueueCommitTarget, Player.Listener, AutoCloseable {
    private val metadata = mutableMapOf<String, QueueEntry>()
    private var current: PlaybackItemToken? = null
    private var requestedActivation: PlaybackItemToken? = null
    private var endedActivation: PlaybackItemToken? = null
    private var positionRevision = 0L
    private var published = PlaybackSnapshot.create(identities.sessionId)
    private var observedError: PlaybackException? = null
    private var problem: PlaybackProblem? = null
    private var batching = false
    private var closed = false

    /** Original diagnostics remain service-owned; only the problem identity/classification reaches UI. */
    var lastEngineFailure: EngineFailure? = null
        private set

    init {
        registry.attach(identities.sessionId, ::sampleProgress)
        player.addListener(this)
        publish()
    }

    override fun install(commit: QueueCommit): EmptyResult<PlaybackCommandError> {
        if (closed) return unavailable()
        if (!supports(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE, Player.COMMAND_PLAY_PAUSE)) {
            return unavailable()
        }
        check(commit.current.sessionId == identities.sessionId) { "Queue belongs to a different service session." }
        val items = commit.prepared.items.mapIndexed { index, resolved ->
            val entry = commit.entries[index]
            MediaItem.Builder()
                .setMediaId(entry.entryId.value)
                .setUri(resolved.resourceUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(entry.track.title)
                        .setArtist(entry.track.artist)
                        .setAlbumTitle(entry.track.album)
                        .build(),
                )
                .build()
        }
        // Retain old entries until the raw timeline confirms the replacement. A throwing engine
        // can have either old or partially changed state; no optimistic queue is published.
        commit.entries.forEach { metadata[it.entryId.value] = it }
        requestedActivation = commit.current
        return mutate {
            player.playWhenReady = false
            player.setMediaItems(
                items, commit.prepared.request.startIndex,
                commit.prepared.request.startPositionMs.coerceAtMost(MAX_MEDIA3_POSITION_MS),
            )
            player.prepare()
            player.playWhenReady = commit.prepared.request.playRequested
        }
    }

    override fun execute(command: PlaybackCommand): EmptyResult<PlaybackCommandError> {
        if (closed) return unavailable()
        publish()
        return when (command) {
            is PlaybackCommand.ReplaceQueue -> error("ReplaceQueue must enter through PlaybackQueueOwner.")
            is PlaybackCommand.SetPlayRequested -> setPlayRequested(command.value)
            is PlaybackCommand.SelectQueueEntry -> selectQueueEntry(command)
            is PlaybackCommand.RemoveQueueEntry -> removeQueueEntry(command)
            is PlaybackCommand.SeekTo -> {
                if (command.target != current) stale()
                else {
                    val duration = durationMs()
                    if (duration == null || !supports(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) unavailable()
                    else mutate { player.seekTo(command.positionMs.coerceAtMost(duration).coerceAtMost(MAX_MEDIA3_POSITION_MS)) }
                }
            }
            PlaybackCommand.Next -> {
                if (current == null || !supports(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) unavailable()
                else mutate {
                    if (player.nextMediaItemIndex == player.currentMediaItemIndex) reactivateCurrent()
                    player.seekToNextMediaItem()
                }
            }
            PlaybackCommand.Previous -> previous()
            is PlaybackCommand.SetRepeat -> {
                if (!supports(Player.COMMAND_SET_REPEAT_MODE)) unavailable()
                else mutate { player.repeatMode = command.mode.toMedia3() }
            }
            is PlaybackCommand.SetShuffle -> {
                if (!supports(Player.COMMAND_SET_SHUFFLE_MODE)) unavailable()
                else mutate { player.shuffleModeEnabled = command.enabled }
            }
            is PlaybackCommand.Retry -> {
                if (command.target != current || command.problemId != problem?.id) stale()
                else if (!supports(Player.COMMAND_PREPARE)) unavailable()
                else mutate {
                    reactivateCurrent()
                    player.prepare()
                }
            }
            PlaybackCommand.StopAndClear -> {
                if (!supports(Player.COMMAND_STOP, Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_PLAY_PAUSE)) unavailable()
                else mutate {
                    player.playWhenReady = false
                    player.stop()
                    player.clearMediaItems()
                }
            }
        }
    }

    /** Native prepare reopens an existing resource; it does not repeat catalog metadata resolution. */
    fun prepareFromNative() {
        // ExoPlayer.prepare() is a no-op outside Idle; do not invent an activation for that no-op.
        if (closed || !supports(Player.COMMAND_PREPARE) || player.playbackState != Player.STATE_IDLE) return
        mutate {
            reactivateCurrent()
            player.prepare()
        }
    }

    private fun setPlayRequested(value: Boolean): EmptyResult<PlaybackCommandError> {
        if (current == null || !supports(Player.COMMAND_PLAY_PAUSE)) return unavailable()
        if (value && problem != null) return unavailable()
        if (value && player.playbackState == Player.STATE_IDLE && !supports(Player.COMMAND_PREPARE)) return unavailable()
        if (value && player.playbackState == Player.STATE_ENDED && !supports(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            return unavailable()
        }
        return mutate {
            if (value && player.playbackState == Player.STATE_ENDED) {
                reactivateCurrent()
                player.seekTo(0)
            }
            if (value && player.playbackState == Player.STATE_IDLE) {
                reactivateCurrent()
                player.prepare()
            }
            player.playWhenReady = value
        }
    }

    private fun selectQueueEntry(command: PlaybackCommand.SelectQueueEntry): EmptyResult<PlaybackCommandError> {
        val index = when (val selection = command.resolveBaseIndex(published)) {
            is AppResult.Failure -> return selection
            is AppResult.Success -> selection.value
        }
        if (command.entryId == current?.entryId) return AppResult.Success(Unit)
        if (PlaybackCapability.SelectQueueEntry !in published.capabilities) return unavailable()

        // Acquire only for a live move that can resume audio. A stale tap must never start the service.
        val lease = if (player.playWhenReady) {
            when (val result = acquireForeground()) {
                is AppResult.Failure -> return result
                is AppResult.Success -> result.value
            }
        } else null
        return lease.use {
            mutate {
                player.seekTo(index, 0L)
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
            }
        }
    }

    private fun removeQueueEntry(command: PlaybackCommand.RemoveQueueEntry): EmptyResult<PlaybackCommandError> {
        val index = when (val result = command.resolveBaseIndex(published)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }
        if (PlaybackCapability.RemoveQueueEntry !in published.capabilities) return unavailable()
        return mutate { player.removeMediaItem(index) }
    }

    private fun previous(): EmptyResult<PlaybackCommandError> {
        if (current == null) return unavailable()
        val restart = player.isCurrentMediaItemSeekable && player.currentPosition > PREVIOUS_RESTART_MS
        return when {
            restart && supports(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) -> mutate { player.seekTo(0) }
            player.hasPreviousMediaItem() && supports(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ->
                mutate { player.seekToPreviousMediaItem() }
            supports(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) -> mutate { player.seekTo(0) }
            else -> unavailable()
        }
    }

    private inline fun mutate(action: () -> Unit): EmptyResult<PlaybackCommandError> {
        batching = true
        var primaryFailure: Throwable? = null
        try {
            action()
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            batching = false
            val primary = primaryFailure
            if (primary == null) publish()
            else try {
                publish()
            } catch (publicationFailure: Throwable) {
                if (publicationFailure !== primary) primary.addSuppressed(publicationFailure)
            }
        }
        return AppResult.Success(Unit)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (!batching) publish()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (closed) return
        positionRevision++
        val previous = current
        if (reason == Player.DISCONTINUITY_REASON_SEEK && previous != null && endedActivation == previous &&
            player.playbackState != Player.STATE_ENDED && requestedActivation == null &&
            player.currentMediaItem?.mediaId == previous.entryId.value) {
            reactivateCurrent()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (!closed && playbackState == Player.STATE_ENDED) {
            synchronizeCurrent()
            endedActivation = current
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!closed) synchronizeCurrent(
            // A native Next/Previous can repeat the same entry with SEEK, unlike an ordinary scrub.
            // An activation already requested by the owner or Ended replay still takes precedence.
            reactivate = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
        )
    }

    private fun reactivateCurrent() {
        current?.entryId?.let { requestedActivation = identities.activate(it) }
    }

    private fun synchronizeCurrent(reactivate: Boolean = false) {
        val id = player.currentMediaItem?.mediaId?.takeIf { metadata.containsKey(it) }?.let(::QueueEntryId)
        val selected = requestedActivation?.takeIf { it.entryId == id }
        val next = when {
            id == null -> null
            selected != null -> selected
            current?.entryId != id || reactivate -> identities.activate(id)
            else -> current
        }
        if (selected != null) requestedActivation = null
        if (next != current) {
            current = next
            endedActivation = null
            positionRevision++
        }
    }

    /** Publishes actual raw player facts after events/commands, never from a controller's optimistic cache. */
    fun publish() {
        if (closed || batching) return
        synchronizeCurrent()
        val entries = List(player.mediaItemCount) { index ->
            val id = player.getMediaItemAt(index).mediaId
            checkNotNull(metadata[id]) { "Raw engine contains an entry not installed by the playback owner." }
        }
        // The released/empty raw timeline has no active item, regardless of a prospective index.
        if (entries.isEmpty()) {
            current = null
            requestedActivation = null
        }
        metadata.keys.retainAll(entries.map { it.entryId.value }.toSet())
        val repeat = player.repeatMode.toRepeatMode()
        val shuffle = player.shuffleModeEnabled
        val order = playOrder(entries, shuffle)
        val oldQueue = published.queue
        val queueChanged = oldQueue.entries != entries || oldQueue.playOrder != order || oldQueue.current != current ||
            oldQueue.repeatMode != repeat || oldQueue.shuffleEnabled != shuffle
        val queue = if (!queueChanged) oldQueue else PlaybackQueue.create(
            entries, order, current, oldQueue.revision + 1, repeat, shuffle,
        )
        val error = player.playerError
        if (error == null) {
            observedError = null
            problem = null
        } else if (error !== observedError || problem?.target != current) {
            observedError = error
            problem = PlaybackProblem(PlaybackProblemId(UUID.randomUUID().toString()), error.toProblemCode(), current)
            lastEngineFailure = EngineFailure(problem!!, error, error.message)
        }
        val transport = if (entries.isEmpty()) PlaybackTransport() else PlaybackTransport(
            phase = if (error != null) PlaybackPhase.Idle else player.playbackState.toPhase(),
            playRequested = player.playWhenReady,
            suppression = player.playbackSuppressionReason.toSuppression(),
            problem = problem,
        )
        val capabilities = capabilities(queue, transport.problem != null)
        if (queue !== oldQueue || transport != published.transport || positionRevision != published.positionRevision ||
            capabilities != published.capabilities) {
            published = PlaybackSnapshot.create(
                identities.sessionId, published.revision + 1, queue, transport, positionRevision, capabilities,
            )
        }
        registry.publish(published, sampleProgress())
    }

    /** Position-only sampling deliberately leaves control/queue snapshots and revisions untouched. */
    fun sampleProgress(): PlaybackProgress? {
        if (closed) return null
        val target = published.queue.current ?: return null
        if (player.currentMediaItem?.mediaId != target.entryId.value) return null
        return PlaybackProgress(
            target, published.positionRevision, player.currentPosition.coerceAtLeast(0), durationMs(),
            player.bufferedPosition.takeIf { it >= 0 && it != C.TIME_UNSET }, clock.nowMs(), player.isPlaying,
            player.playbackParameters.speed,
        )
    }

    private fun playOrder(entries: List<QueueEntry>, shuffle: Boolean): List<QueueEntryId> {
        if (!shuffle || entries.isEmpty()) return entries.map { it.entryId }
        val timeline = player.currentTimeline
        var index = timeline.getFirstWindowIndex(true)
        val result = ArrayList<QueueEntryId>(entries.size)
        while (index != C.INDEX_UNSET && result.size < entries.size) {
            check(index in entries.indices) { "Engine traversal index must belong to its queue." }
            result += entries[index].entryId
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        check(result.size == entries.size && result.toSet().size == entries.size) {
            "Engine shuffle traversal must cover the complete queue."
        }
        return result
    }

    private fun capabilities(queue: PlaybackQueue, hasProblem: Boolean): Set<PlaybackCapability> = buildSet {
        if (supports(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE, Player.COMMAND_PLAY_PAUSE)) {
            add(PlaybackCapability.ReplaceQueue)
        }
        if (supports(Player.COMMAND_SET_REPEAT_MODE)) add(PlaybackCapability.SetRepeat)
        if (supports(Player.COMMAND_SET_SHUFFLE_MODE)) add(PlaybackCapability.SetShuffle)
        if (supports(Player.COMMAND_STOP, Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_PLAY_PAUSE)) {
            add(PlaybackCapability.StopAndClear)
        }
        if (queue.hasUpcomingEntries && supports(Player.COMMAND_CHANGE_MEDIA_ITEMS)) add(PlaybackCapability.RemoveQueueEntry)
        if (queue.current != null) {
            if (supports(Player.COMMAND_SEEK_TO_MEDIA_ITEM) &&
                (player.playbackState != Player.STATE_IDLE || supports(Player.COMMAND_PREPARE))) {
                add(PlaybackCapability.SelectQueueEntry)
            }
            if (supports(Player.COMMAND_PLAY_PAUSE)) add(PlaybackCapability.SetPlayRequested)
            if (durationMs() != null && supports(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) add(PlaybackCapability.SeekTo)
            if (supports(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) add(PlaybackCapability.Next)
            if (supports(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) || supports(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                add(PlaybackCapability.Previous)
            }
            if (hasProblem && supports(Player.COMMAND_PREPARE)) add(PlaybackCapability.Retry)
        }
    }

    private fun supports(vararg commands: Int): Boolean = commands.all(player::isCommandAvailable)
    private fun durationMs(): Long? = player.duration.takeIf { it >= 0 && it != C.TIME_UNSET }
    private fun unavailable() = AppResult.Failure(PlaybackCommandError.CommandUnavailable)
    private fun stale() = AppResult.Failure(PlaybackCommandError.StaleTarget)

    override fun close() {
        if (closed) return
        closed = true
        player.removeListener(this)
        metadata.clear()
        registry.detach(identities.sessionId)
    }

    data class EngineFailure(val problem: PlaybackProblem, val cause: PlaybackException, val originalMessage: String?)

    private companion object {
        const val PREVIOUS_RESTART_MS = 3_000L
        // Media3 converts milliseconds to microseconds using multiplication, not saturation.
        const val MAX_MEDIA3_POSITION_MS = Long.MAX_VALUE / 1_000

        fun RepeatMode.toMedia3(): Int = when (this) {
            RepeatMode.Off -> Player.REPEAT_MODE_OFF
            RepeatMode.One -> Player.REPEAT_MODE_ONE
            RepeatMode.All -> Player.REPEAT_MODE_ALL
        }

        fun Int.toRepeatMode(): RepeatMode = when (this) {
            Player.REPEAT_MODE_ONE -> RepeatMode.One
            Player.REPEAT_MODE_ALL -> RepeatMode.All
            else -> RepeatMode.Off
        }

        fun Int.toPhase(): PlaybackPhase = when (this) {
            Player.STATE_BUFFERING -> PlaybackPhase.Buffering
            Player.STATE_READY -> PlaybackPhase.Ready
            Player.STATE_ENDED -> PlaybackPhase.Ended
            else -> PlaybackPhase.Idle
        }

        fun Int.toSuppression(): PlaybackSuppression = when (this) {
            Player.PLAYBACK_SUPPRESSION_REASON_NONE -> PlaybackSuppression.None
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> PlaybackSuppression.AudioFocus
            Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT -> PlaybackSuppression.Output
            else -> PlaybackSuppression.Other
        }

        fun PlaybackException.toProblemCode(): PlaybackProblemCode = when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION, PlaybackException.ERROR_CODE_PERMISSION_DENIED ->
                PlaybackProblemCode.AccessDenied
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackProblemCode.NotFound
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> PlaybackProblemCode.UnsupportedMedia
            in 2_000..2_999, PlaybackException.ERROR_CODE_TIMEOUT -> PlaybackProblemCode.Io
            in 4_000..4_999 -> PlaybackProblemCode.DecoderFailure
            in 5_000..5_999 -> PlaybackProblemCode.OutputUnavailable
            else -> PlaybackProblemCode.Unknown
        }
    }
}
