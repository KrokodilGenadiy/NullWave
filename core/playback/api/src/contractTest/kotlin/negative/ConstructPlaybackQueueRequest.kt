// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest

fun bypass() = PlaybackQueueRequest(
    trackIds = emptyList(),
    startIndex = 0,
    startPositionMs = 0,
    playRequested = true,
)
