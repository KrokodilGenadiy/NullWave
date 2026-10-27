// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.RepeatMode

fun bypass() = PlaybackQueue(
    entries = emptyList(),
    playOrder = emptyList(),
    current = null,
    revision = 0,
    repeatMode = RepeatMode.Off,
    shuffleEnabled = false,
)
