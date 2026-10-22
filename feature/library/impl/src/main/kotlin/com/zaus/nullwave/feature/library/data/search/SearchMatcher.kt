package com.zaus.nullwave.feature.library.data.search

import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ensureActive

/** Fields are normalized separately: tokens cannot span field boundaries. */
internal class SearchMatcher(query: LibrarySearchQuery, private val context: CoroutineContext) {
    private val text = query.text
    private val tokens = text.split(' ').distinct()

    /** The caller reuses missingTokens for supporting fields instead of scanning the primary twice. */
    fun matchPrimary(primary: String?): PrimaryMatch {
        context.ensureActive()
        if (primary == text) return PrimaryMatch(SearchMatchRank.Exact, emptyList())
        if (primary?.startsWith(text) == true) return PrimaryMatch(SearchMatchRank.Prefix, emptyList())
        val missing = tokens.filter { token ->
            context.ensureActive()
            primary?.contains(token) != true
        }
        val rank = if (missing.isEmpty()) SearchMatchRank.PrimaryTokens else null
        return PrimaryMatch(rank, missing)
    }

    fun covers(tokens: List<String>, fields: List<String>): Boolean = tokens.all { token ->
        context.ensureActive()
        fields.any { field ->
            context.ensureActive()
            field.contains(token)
        }
    }
}

internal data class PrimaryMatch(val rank: SearchMatchRank?, val missingTokens: List<String>)

/** Declaration order is the search contract, independent of Library tab sorting. */
internal enum class SearchMatchRank { Exact, Prefix, PrimaryTokens, Supporting }
