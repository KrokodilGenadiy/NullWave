// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.playback.api.model.PlaybackSnapshot

fun bypass(snapshot: PlaybackSnapshot) = snapshot.copy(capabilities = mutableSetOf())
