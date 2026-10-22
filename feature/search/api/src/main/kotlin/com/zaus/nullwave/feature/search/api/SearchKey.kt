package com.zaus.nullwave.feature.search.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Query belongs to the entry's SavedStateHandle, not its navigation identity. */
@Serializable
data object SearchKey : NavKey
