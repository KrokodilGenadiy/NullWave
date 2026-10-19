package com.zaus.nullwave.feature.library.ui

/** Formatting only: unknown is omitted, known zero is 0:00, hours never wrap at 24. */
internal fun libraryDuration(durationMs: Long?): String? = durationMs?.let {
    require(it >= 0)
    val seconds = it / 1_000
    val minutes = seconds / 60
    val tail = (seconds % 60).toString().padStart(2, '0')
    if (minutes < 60) "$minutes:$tail"
    else "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$tail"
}
