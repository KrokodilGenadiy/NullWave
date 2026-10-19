package com.zaus.nullwave.core.di.dispatchers

import dev.zacsweers.metro.Qualifier

/** Blocking I/O, including ContentResolver queries. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** CPU-bound work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Scheduled Main dispatch; does not imply Main.immediate. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
