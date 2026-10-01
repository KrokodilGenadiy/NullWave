package com.zaus.nullwave.core.data.model

/**
 * Audio quality band, borrowed from the game's inventory rarity coding.
 *
 * The *colours* live in `:core:designsystem` as `QualityTierColors` - they are tokens, and they are
 * exempt from the theme swap. The *classification* lives here, because it is a function of bitrate and
 * container, which is data's business. The two enumerations are paired by hand and must stay in step;
 * there are five of each, and `QualityTierTest` asserts the count so a sixth cannot appear silently.
 *
 * Drives the 3dp leading stripe on a track row, and only while "show quality tier" is on in Settings.
 */
enum class QualityTier {
    /** Lossy, at or below 128 kbps. */
    Low,

    /** Lossy, roughly 192-256 kbps. */
    Standard,

    /** Lossy, 320 kbps and up. */
    High,

    /** A lossless container at CD quality, or at unknown resolution. */
    Lossless,

    /** Lossless above CD quality - more than 48 kHz, or deeper than 16 bit. */
    HiRes,
    ;

    companion object {

        /**
         * Classify a track.
         *
         * **Container first.** No bitrate threshold can tell FLAC from a well-encoded MP3, and a
         * lossless file's bitrate varies with the material - so the mime type decides losslessness, and
         * bitrate only bands the lossy files.
         *
         * **Resolution second, and only for lossless.** This is where the nullability bites:
         * [sampleRate] and [bitDepth] are filled by a lazy per-file `MediaExtractor` probe, so at scan
         * time they are null and a hi-res file reports as [Lossless] until something looks at it. That
         * is the right way round. Under-promising corrects itself the moment the player screen probes
         * the file; guessing hi-res from a container would be wrong forever.
         */
        fun of(mimeType: String, bitrate: Long, sampleRate: Int?, bitDepth: Int?): QualityTier {
            if (isLossless(mimeType)) {
                val aboveCdQuality = (sampleRate != null && sampleRate > CdSampleRate) ||
                    (bitDepth != null && bitDepth > CdBitDepth)
                return if (aboveCdQuality) HiRes else Lossless
            }
            val kbps = bitrate / BitsPerKilobit
            return when {
                kbps >= HighKbps -> High
                kbps > LowKbps -> Standard
                else -> Low
            }
        }

        private fun isLossless(mimeType: String): Boolean =
            LosslessMimeFragments.any { mimeType.contains(it, ignoreCase = true) }

        /**
         * Matched as fragments, not compared for equality: real files spell the subtype `flac`, `x-flac`
         * or `vnd.wave` depending on who wrote the tag.
         *
         * `"ape"` is deliberately absent even though Monkey's Audio is lossless. As a three-letter
         * fragment it is the loosest possible match, and it does not even catch the real type, which is
         * `audio/x-monkeys-audio` - so it over-matched in principle while under-matching in practice.
         * The actual spelling is listed instead.
         */
        private val LosslessMimeFragments = listOf(
            "flac",
            "alac",
            "wav",
            "aiff",
            "monkeys-audio",
            "x-ms-wma-lossless",
        )

        private const val BitsPerKilobit = 1000
        private const val CdSampleRate = 48_000
        private const val CdBitDepth = 16
        private const val LowKbps = 128
        private const val HighKbps = 320
    }
}
