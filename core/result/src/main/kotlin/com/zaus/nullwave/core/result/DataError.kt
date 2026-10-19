package com.zaus.nullwave.core.result

/** Shared local-storage failures. Feature-specific validation can use its own error type. */
enum class DataError {
    AccessDenied,
    NotFound,
    StorageFull,
    Io,
}
