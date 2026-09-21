package com.zaus.nullwave.feature.about.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * This feature's public destination.
 *
 * Declared here rather than in :core:navigation so adding a feature never edits a shared file.
 * Anything that wants to navigate here depends on this api module; nothing has to know the impl
 * exists.
 */
@Serializable
data object AboutKey : NavKey