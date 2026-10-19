package com.zaus.nullwave.feature.library.presentation

/** A recovery hint from the UI's platform check, not a claim of permanent denial. */
internal enum class AccessAction { Request, ExplainThenRequest, SettingsSuggested, CheckAgain }

/** A fresh OS observation; it is never persisted as authorization to read media. */
internal sealed interface LibraryAccess {
    data object Granted : LibraryAccess
    data class Required(val action: AccessAction) : LibraryAccess
}
