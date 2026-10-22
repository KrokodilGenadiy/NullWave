package com.zaus.nullwave.feature.library.data.search

import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {
    @Test
    fun `primary analysis returns rank and only the tokens still needing support`() {
        val matcher = matcher(Job())
        assertEquals(PrimaryMatch(SearchMatchRank.Exact, emptyList<String>()), matcher.matchPrimary("moon light"))
        assertEquals(PrimaryMatch(SearchMatchRank.Prefix, emptyList<String>()), matcher.matchPrimary("moon light live"))
        assertEquals(PrimaryMatch(SearchMatchRank.PrimaryTokens, emptyList<String>()), matcher.matchPrimary("light over moon"))
        val partial = matcher.matchPrimary("moon")
        assertNull(partial.rank)
        assertEquals(listOf("light"), partial.missingTokens)
        assertTrue(matcher.covers(partial.missingTokens, listOf("light orchestra")))
        assertFalse(matcher.covers(partial.missingTokens, listOf("li", "ght")))
        assertEquals(PrimaryMatch(null, listOf("moon", "light")), matcher.matchPrimary(null))
    }

    @Test
    fun `missing tokens are distinct and keep query order`() {
        val matcher = SearchMatcher(checkNotNull(LibrarySearchQuery.fromInput("moon light light live live", "en")), Job())
        assertEquals(listOf("light", "live"), matcher.matchPrimary("moon").missingTokens)
    }

    @Test
    fun `cancelled matching cannot complete even through an exact match fast path`() {
        val job = Job().apply { cancel() }
        assertThrows(CancellationException::class.java) { matcher(job).matchPrimary("moon light") }
    }

    @Test
    fun `cancellation between tokens stops before the remaining tokens are visited`() {
        val job = Job()
        var visited = 0
        val tokens = object : AbstractList<String>() {
            private val values = listOf("moon", "light", "live")
            override val size = values.size
            override fun get(index: Int): String {
                visited++
                if (index == 1) job.cancel()
                return values[index]
            }
        }
        assertThrows(CancellationException::class.java) {
            matcher(job).covers(tokens, listOf("moon light live"))
        }
        assertEquals(2, visited)
    }

    @Test
    fun `cancellation while trying supporting fields stops before later fields`() {
        val job = Job()
        var visited = 0
        val fields = object : AbstractList<String>() {
            private val values = listOf("unrelated", "also unrelated", "light")
            override val size = values.size
            override fun get(index: Int): String {
                visited++
                if (index == 1) job.cancel()
                return values[index]
            }
        }
        assertThrows(CancellationException::class.java) {
            matcher(job).covers(listOf("light"), fields)
        }
        assertEquals(2, visited)
    }

    private fun matcher(job: Job) = SearchMatcher(checkNotNull(LibrarySearchQuery.fromInput("moon light", "en")), job)
}
