package com.zaus.nullwave.core.di.dispatchers

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Shared dispatcher bindings. Tests can exclude this container and supply their own dispatchers. */
@BindingContainer
@ContributesTo(AppScope::class)
object CoroutineDispatcherBindings {
    @Provides
    @IoDispatcher
    fun io(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun default(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun main(): CoroutineDispatcher = Dispatchers.Main
}
