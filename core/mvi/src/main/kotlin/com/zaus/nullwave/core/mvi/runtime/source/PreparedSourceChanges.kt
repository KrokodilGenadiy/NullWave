package com.zaus.nullwave.core.mvi.runtime.source

/** Validation/preparation never mutates revisions; commit is internal and notification runs last. */
internal class PreparedSourceChanges(
    val commit: () -> Unit,
    val notifyCollectors: () -> Unit,
) {
    companion object {
        val None = PreparedSourceChanges(commit = {}, notifyCollectors = {})
    }
}
