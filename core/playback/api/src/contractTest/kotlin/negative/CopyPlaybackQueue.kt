// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.playback.api.model.PlaybackQueue

fun bypass(queue: PlaybackQueue) = queue.copy(entries = mutableListOf(), playOrder = mutableListOf())
