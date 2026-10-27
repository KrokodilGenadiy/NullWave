// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.PlaybackSessionId
import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot
import com.zaus.nullwave.core.playback.api.model.PlaybackTransport

fun bypass() = PlaybackSnapshot(
    sessionId = PlaybackSessionId("session"),
    revision = 0,
    queue = PlaybackQueue.create(),
    transport = PlaybackTransport(),
    positionRevision = 0,
    capabilities = emptySet(),
)
