// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest

fun bypass(request: PlaybackQueueRequest) = request.copy(trackIds = mutableListOf())
