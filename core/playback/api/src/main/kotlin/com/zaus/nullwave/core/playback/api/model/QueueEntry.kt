package com.zaus.nullwave.core.playback.api.model

import com.zaus.nullwave.core.media.model.track.MediaTrack

/** One occurrence and its catalog metadata; a resource URI is deliberately not part of UI state. */
data class QueueEntry(val entryId: QueueEntryId, val track: MediaTrack)

enum class RepeatMode { Off, One, All }
