package com.zaus.nullwave.feature.library.api.access

/** A recovery hint, not a claim that a denial is permanent. */
enum class AccessAction { Request, ExplainThenRequest, SettingsSuggested, CheckAgain }

/** Fresh platform observation; never persist this as authorization to read media. */
sealed interface LibraryAccess {
    data object Granted : LibraryAccess
    data class Required(val action: AccessAction) : LibraryAccess
}
