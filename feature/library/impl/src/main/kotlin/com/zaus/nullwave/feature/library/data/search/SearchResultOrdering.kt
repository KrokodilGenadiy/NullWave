package com.zaus.nullwave.feature.library.data.search

import com.zaus.nullwave.feature.library.data.mapping.compareMissingLast
import java.text.Collator
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ensureActive

internal data class RankedHit<T>(val hit: T, val rank: SearchMatchRank)

/** Shared ordering for all three groups; checks cancellation in comparisons and result copying. */
internal fun <T> List<RankedHit<T>>.sortedHits(
    context: CoroutineContext,
    collator: Collator,
    label: (T) -> String?,
    identity: (T) -> String,
): List<T> = sortedWith { first, second ->
    context.ensureActive()
    val rank = first.rank.compareTo(second.rank)
    if (rank != 0) rank else {
        val order = compareMissingLast(label(first.hit), label(second.hit), true, collator::compare)
        if (order != 0) order else identity(first.hit).compareTo(identity(second.hit))
    }
}.map {
    context.ensureActive()
    it.hit
}
