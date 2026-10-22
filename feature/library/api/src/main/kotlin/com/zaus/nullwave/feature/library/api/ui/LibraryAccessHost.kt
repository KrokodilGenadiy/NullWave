package com.zaus.nullwave.feature.library.api.ui

import androidx.compose.runtime.Composable
import com.zaus.nullwave.feature.library.api.access.LibraryAccess

/**
 * Entry-owned permission UI. Implementations register launchers unconditionally, check fresh access
 * on resume and deduplicate the corresponding result. Requests only follow explicit user actions.
 * No Activity or grant is retained by the injected service; state belongs to its composition.
 */
interface LibraryAccessHost {
    @Composable
    fun Content(onAccessChecked: (LibraryAccess) -> Unit, content: @Composable (LibraryAccessUi) -> Unit)
}

data class LibraryAccessUi(
    val busy: Boolean = false,
    val launchFailed: Boolean = false,
    val onAccess: () -> Unit = {},
    val onAskAgain: () -> Unit = {},
    val access: LibraryAccess? = null,
)
