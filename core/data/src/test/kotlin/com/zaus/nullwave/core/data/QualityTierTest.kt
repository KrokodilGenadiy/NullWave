package com.zaus.nullwave.core.data

import com.zaus.nullwave.core.data.model.QualityTier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one piece of real classification logic in the data layer, so the one worth pinning.
 *
 * `QualityTier` drives the 3dp tier stripe on a track row, and its five cases are paired by hand with
 * `QualityTierColors` in `:core:designsystem`.
 */
class QualityTierTest {

    private fun tier(
        mimeType: String = "audio/mpeg",
        kbps: Int = 320,
        sampleRate: Int? = null,
        bitDepth: Int? = null,
    ) = QualityTier.of(mimeType, bitrate = kbps * 1000L, sampleRate = sampleRate, bitDepth = bitDepth)

    @Test
    fun `lossy bands follow bitrate`() {
        assertEquals(QualityTier.Low, tier(kbps = 96))
        assertEquals(QualityTier.Low, tier(kbps = 128))
        assertEquals(QualityTier.Standard, tier(kbps = 192))
        assertEquals(QualityTier.Standard, tier(kbps = 256))
        assertEquals(QualityTier.High, tier(kbps = 320))
        assertEquals(QualityTier.High, tier(kbps = 500))
    }

    @Test
    fun `128 is Low and 129 is Standard`() {
        // Pinning the boundary, because off-by-one here silently mislabels a whole library.
        assertEquals(QualityTier.Low, tier(kbps = 128))
        assertEquals(QualityTier.Standard, tier(kbps = 129))
    }

    @Test
    fun `container beats bitrate`() {
        // A lossless file's bitrate varies with the material, so no threshold can identify it. A FLAC
        // that happens to encode at 300 kbps is still lossless.
        assertEquals(QualityTier.Lossless, tier(mimeType = "audio/flac", kbps = 300))
        assertEquals(QualityTier.Lossless, tier(mimeType = "audio/x-flac", kbps = 1100))
        assertEquals(QualityTier.Lossless, tier(mimeType = "audio/wav", kbps = 1411))
    }

    @Test
    fun `mime subtype is matched as a fragment`() {
        // Real files spell it several ways, which is why this is `contains` and not equality.
        assertEquals(QualityTier.Lossless, tier(mimeType = "audio/x-flac"))
        assertEquals(QualityTier.Lossless, tier(mimeType = "AUDIO/FLAC"))
        assertEquals(QualityTier.Lossless, tier(mimeType = "audio/x-aiff"))
    }

    @Test
    fun `hi-res needs lossless plus resolution`() {
        assertEquals(QualityTier.HiRes, tier(mimeType = "audio/flac", sampleRate = 96_000))
        assertEquals(QualityTier.HiRes, tier(mimeType = "audio/flac", bitDepth = 24))
        // 44.1/16 is CD quality, not hi-res.
        assertEquals(
            QualityTier.Lossless,
            tier(mimeType = "audio/flac", sampleRate = 44_100, bitDepth = 16),
        )
    }

    @Test
    fun `a high sample rate on a lossy file does not make it hi-res`() {
        assertEquals(QualityTier.High, tier(mimeType = "audio/mpeg", kbps = 320, sampleRate = 96_000))
    }

    @Test
    fun `unprobed lossless reports Lossless rather than guessing HiRes`() {
        // sampleRate and bitDepth are null until the lazy MediaExtractor probe runs, so at scan time
        // every lossless file looks like CD quality. Under-promising is deliberate: it corrects itself
        // when the player screen probes the file, whereas guessing hi-res from a container would be
        // wrong forever.
        assertEquals(
            QualityTier.Lossless,
            tier(mimeType = "audio/flac", sampleRate = null, bitDepth = null),
        )
    }

    @Test
    fun `every tier has a colour counterpart`() {
        // The two enumerations are paired by hand - QualityTierColors in :core:designsystem has one
        // entry per case here. This fails if someone adds a sixth tier without the colour.
        assertEquals(5, QualityTier.entries.size)
    }
}
