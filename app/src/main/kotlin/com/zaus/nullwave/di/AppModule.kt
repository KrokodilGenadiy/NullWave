package com.zaus.nullwave.di

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.feature.library.api.LibraryKey
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Host-level bindings.
 *
 * NOTE: this replaces the `androidx.navigation.Navigator` you had here - that is Navigation 2's
 * `Navigator<D : NavDestination>`, which is why it wanted a type argument. Navigation 3 has no
 * equivalent injectable navigator; the back stack itself is the navigation state, and you mutate
 * it directly (`backStack.add(AlbumDetail(id))`, `backStack.removeLastOrNull()`).
 *
 * Scoped to [ActivityScope] so it survives recomposition but not process death. Nav 3 can restore
 * it across process death via `rememberNavBackStack`, which is a different (and probably better)
 * arrangement - worth deciding which you want before building on this.
 */
@ContributesTo(ActivityScope::class)
@BindingContainer
object AppModule {

    @Provides
    @SingleIn(ActivityScope::class)
    fun provideBackStack(): NavBackStack<NavKey> = NavBackStack(LibraryKey)
}
