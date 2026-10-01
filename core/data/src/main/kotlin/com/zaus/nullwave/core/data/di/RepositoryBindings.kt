package com.zaus.nullwave.core.data.di

import android.content.Context
import com.zaus.nullwave.core.data.LibraryScanner
import com.zaus.nullwave.core.data.TrackRepository
import com.zaus.nullwave.core.data.internal.MediaStoreLibraryScanner
import com.zaus.nullwave.core.data.internal.TrackRepositoryImpl
import com.zaus.nullwave.data.database.NullWaveDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Binds the repository interfaces to their implementations, for the application graph.
 *
 * ## Why a container rather than `@ContributesBinding` on the implementation
 *
 * `@ContributesBinding` makes the *consuming* module's generated graph name the implementation class, so
 * the implementation has to be public. `TrackRepositoryImpl` is `internal` - the whole point of the
 * domain model is that nothing outside this module touches the generated schema types, and a public
 * implementation leaks the door to them. Contributing it from the first module fails to build with
 * `[Metro/MissingBinding] No binding found for TrackRepository`.
 *
 * A binding container avoids that because of where the names appear. This object and its function are
 * public, and their *signature* mentions only public types - [NullWaveDatabase] in, [TrackRepository]
 * out. The internal class is named only in the body, which is compiled here, where it is visible. So
 * `:app` merges the container without ever being able to see what it constructs.
 *
 * Same shape as the feature modules' `@ContributesTo` containers: nothing outside this module names it.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object RepositoryBindings {

    /**
     * One repository per process. `@SingleIn` matters here rather than being habit: the implementation
     * holds no mutable state, but a second instance would mean a second set of SQLDelight query
     * listeners, and every `Flow` collector would get its own.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideTrackRepository(database: NullWaveDatabase): TrackRepository =
        TrackRepositoryImpl(database)

    /**
     * Also `@SingleIn`, for a different reason than the repository: [LibraryScanner.mediaStoreChanges]
     * registers a `ContentObserver`, and two scanner instances would mean two observers and two
     * concurrent scans racing the same tables.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideLibraryScanner(context: Context, database: NullWaveDatabase): LibraryScanner =
        MediaStoreLibraryScanner(context, database)
}
