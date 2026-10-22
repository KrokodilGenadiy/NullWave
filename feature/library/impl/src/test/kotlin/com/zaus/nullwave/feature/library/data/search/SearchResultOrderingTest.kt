package com.zaus.nullwave.feature.library.data.search

import java.text.CollationKey
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchResultOrderingTest {
    @Test
    fun `cancelled sorting does not compare any labels`() {
        val job = Job().apply { cancel() }
        val collator = CancellingCollator(job)
        assertThrows(CancellationException::class.java) {
            hits(32).sortedHits(job, collator, { it }, { it })
        }
        assertEquals(0, collator.comparisons)
    }

    @Test
    fun `cancellation inside a comparison stops the remaining sort comparisons`() {
        val job = Job()
        val collator = CancellingCollator(job)
        assertThrows(CancellationException::class.java) {
            hits(32).sortedHits(job, collator, { it }, { it })
        }
        assertEquals(1, collator.comparisons)
    }

    @Test
    fun `cancellation in the last comparison still prevents returning the result`() {
        val job = Job()
        val collator = CancellingCollator(job)
        // Two items need just one comparison; cancellation must also be checked after sorting.
        assertThrows(CancellationException::class.java) {
            hits(2).sortedHits(job, collator, { it }, { it })
        }
        assertEquals(1, collator.comparisons)
    }

    private fun hits(count: Int) = (count downTo 1).map { RankedHit("Song $it", SearchMatchRank.PrimaryTokens) }

    /** A deterministic cancellation trigger inside the real ordering, without threads or sleeps. */
    private class CancellingCollator(private val job: Job) : Collator() {
        private val delegate = Collator.getInstance(Locale.ENGLISH)
        var comparisons = 0
            private set

        override fun compare(source: String, target: String): Int {
            comparisons++
            job.cancel()
            return delegate.compare(source, target)
        }

        override fun getCollationKey(source: String): CollationKey = delegate.getCollationKey(source)
        override fun hashCode(): Int = delegate.hashCode()
    }
}
