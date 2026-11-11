package com.zaus.nullwave.core.playback.media3.engine

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.playback.api.model.PlaybackProgress
import com.zaus.nullwave.core.playback.media3.testing.createSilentWav
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@UnstableApi
internal fun withSilentExoPlayer(check: suspend (ExoPlayer, String) -> Unit): Unit = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val file = createSilentWav(context.cacheDir, durationSeconds = 60)
    try {
        withContext(Dispatchers.Main) {
            val player = ExoPlayer.Builder(context).build()
            try {
                withTimeout(15_000) { check(player, Uri.fromFile(file).toString()) }
            } finally {
                player.release()
            }
        }
    } finally {
        file.delete()
    }
}

@UnstableApi
internal suspend fun assertPositionAfterQueueEdit(
    player: ExoPlayer,
    target: Media3QueueCommitTarget,
    before: PlaybackProgress,
) {
    // Observe the edit after the playback thread has processed it as well as the app looper.
    val applied = CompletableDeferred<Unit>()
    player.createMessage { _, _ -> applied.complete(Unit) }.send()
    applied.await()
    val positionAfterEdit = player.currentPosition
    assertTrue("Queue edit rewound playback from ${before.positionMs} to $positionAfterEdit",
        positionAfterEdit >= before.positionMs)
    if (before.advancing) while (player.currentPosition <= positionAfterEdit) delay(10)

    val after = requireNotNull(target.sampleProgress())
    assertEquals(before.target, after.target)
    assertEquals(before.positionRevision, after.positionRevision)
    assertEquals(before.advancing, after.advancing)
    if (before.advancing) {
        val elapsed = after.sampledAtMs - before.sampledAtMs
        assertTrue("Position must advance continuously: $before -> $after",
            after.positionMs - before.positionMs in 1..(elapsed + 100))
    } else {
        assertEquals(before.positionMs, after.positionMs)
    }
}
