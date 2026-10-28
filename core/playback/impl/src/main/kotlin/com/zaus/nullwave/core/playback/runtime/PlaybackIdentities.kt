package com.zaus.nullwave.core.playback.runtime

import com.zaus.nullwave.core.playback.api.model.PlaybackItemToken
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import java.util.UUID

/** One service incarnation. The owner and eventual engine share this source on their application looper. */
internal class PlaybackIdentities(
    val sessionId: PlaybackSessionId = PlaybackSessionId(UUID.randomUUID().toString()),
    private val entryId: () -> QueueEntryId = { QueueEntryId(UUID.randomUUID().toString()) },
) {
    private var activation = 0L

    fun newEntryId(): QueueEntryId = entryId()

    fun activate(entryId: QueueEntryId): PlaybackItemToken {
        check(activation < Long.MAX_VALUE) { "Playback activation identities are exhausted." }
        return PlaybackItemToken(sessionId, entryId, activation++)
    }
}
