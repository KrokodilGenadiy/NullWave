package com.zaus.nullwave.core.mvi.ui

import com.zaus.nullwave.core.mvi.R
import com.zaus.nullwave.core.result.DataError

/** Shared local-data failures. Features own mappings for their more specific error types. */
fun DataError.toUiText(): UiText = UiText.Resource(
    when (this) {
        DataError.AccessDenied -> R.string.mvi_error_access_denied
        DataError.NotFound -> R.string.mvi_error_not_found
        DataError.StorageFull -> R.string.mvi_error_storage_full
        DataError.Io -> R.string.mvi_error_io
    },
)
