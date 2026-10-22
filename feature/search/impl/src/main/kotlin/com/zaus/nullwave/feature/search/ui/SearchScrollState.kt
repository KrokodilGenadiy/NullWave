package com.zaus.nullwave.feature.search.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** Keep the saver above Loading/AccessRequired so placeholders cannot clamp the saved position. */
@Composable
internal fun rememberSearchScrollState(query: String?, localeTag: String): LazyListState =
    rememberSaveable(query, localeTag, saver = listSaver(
        save = { listOf(query.orEmpty(), localeTag, it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
        restore = {
            // rememberSaveable inputs reset live state, but do not validate restored inputs.
            if (it[0] == query.orEmpty() && it[1] == localeTag) {
                LazyListState(it[2] as Int, it[3] as Int)
            } else null
        },
    )) { LazyListState() }
