package com.zaus.nullwave.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * How a feature module registers its screens with the host `NavDisplay` without the host ever
 * importing the feature's composables.
 *
 * A feature contributes one of these into a set, and the shape is identical whether or not it navigates:
 * ```
 * @IntoSet
 * @Provides
 * fun libraryEntries(): EntryProviderInstaller = {
 *     entry<LibraryKey> {
 *         LibraryScreen(onAlbumClick = { id -> navigateTo(AlbumDetailKey(id)) })
 *     }
 *     entry<AboutKey> { AboutScreen() }
 * }
 * ```
 *
 * `MainActivity` injects `Set<EntryProviderInstaller>` and invokes each one inside
 * `entryProvider { ... }`. Adding a feature then means adding a module dependency and nothing else - no
 * `when` block in the host to edit, and no change here.
 *
 * ## The context parameter
 *
 * [NavigationScope] arrives as a **context parameter**, which is why nothing above names or passes it. The
 * host supplies it once with `with(nav) { ... }` and it is implicitly in context from there.
 *
 * This is what makes [navigateTo] and friends resolvable inside an installer: they declare the same context
 * requirement, so a screen composable calling one fails to compile with "no context argument for parameter of
 * type NavigationScope". A screen can still opt in by declaring a scope parameter of its own - and then the
 * coupling is in its signature, which is the property that matters. See [navigateTo] for the precise claim.
 *
 * Two earlier versions each gave up one half of that: an explicit installer argument (enforced, but five of
 * six modules carried an unused parameter) and a `CompositionLocal` (one clean shape, but reachable from any
 * composable in the tree with nothing in the signature to show for it). `NAVIGATION.md` records both.
 *
 * ## Why not a richer receiver
 *
 * Folding `backStack` onto the receiver alongside `entry` would remove even the `.current` read.
 * Unavailable: `EntryProviderScope` is a `public final class` in nav3, so it cannot be subclassed or
 * delegated to with `by`. A wrapper could forward `entry` to a held instance, but `entry` is `inline`
 * with a `reified` type parameter across several overloads with default arguments, so the wrapper would
 * re-declare part of nav3's public API and need revisiting on every nav3 release.
 */
typealias EntryProviderInstaller = context(NavigationScope) EntryProviderScope<NavKey>.() -> Unit
