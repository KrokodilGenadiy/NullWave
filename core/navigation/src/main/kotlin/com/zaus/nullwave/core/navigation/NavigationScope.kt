package com.zaus.nullwave.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * What the host can do for a screen, handed to every [EntryProviderInstaller].
 *
 * ## Why this exists rather than passing the back stack directly
 *
 * [EntryProviderInstaller] is implemented by every feature module, which makes its signature the most
 * expensive thing in this subsystem to change - a change there is a change in all of them. Passing
 * `NavBackStack<NavKey>` straight to the installer would work today, and then the first time the host
 * needs to offer a screen anything *else* (show a snackbar, expand the player sheet, report the window
 * size class) the typealias changes and every feature changes with it.
 *
 * One object avoids that, because the arithmetic is lopsided: this interface has **one implementor**,
 * in `:app`, and **many consumers**. Adding a capability means adding a member here and implementing it
 * in that single place. Features are consumers, so they are untouched unless they want the new thing.
 *
 * That is the same property destinations already have - adding one touches only the feature that owns
 * it - extended to host capabilities.
 *
 * ## What it is deliberately not
 *
 * Not a `Navigator` wrapper. [backStack] is the real `NavBackStack`, because `NavDisplay` needs the real
 * thing and a wrapper would have to re-expose it anyway, leaving two ways to navigate and no answer to
 * which one is authoritative. The navigation vocabulary lives in `NavBackStackExtensions.kt` as
 * extensions: `push`, `pop`, `switchTopLevel`.
 *
 * Not a `Navigator` with a method per destination. That is the common shape in multi-module write-ups,
 * and it means a bridge interface every feature depends on growing a member per screen - the exact
 * per-screen core churn this design exists to avoid. Here a feature names its own keys and pushes them.
 */
interface NavigationScope {

    /**
     * The live back stack. Mutate it with [push], [pop] and [switchTopLevel].
     *
     * Owned by the host's composition (`rememberNavBackStack`), so it survives configuration change and
     * process death and dies with the UI. It is **not** in the DI graph: bound there it outlived the
     * Activity, which meant a relaunch could resume on a stale destination and an emptied stack stayed
     * emptied for the life of the process. `NAVIGATION.md` has the full account.
     */
    val backStack: NavBackStack<NavKey>
}

/**
 * ## How it reaches an installer
 *
 * As a **context parameter** on [EntryProviderInstaller], not as an argument and not through a
 * `CompositionLocal`. The host supplies it once with `with(nav) { … }`, and from there it is implicitly in
 * context for every installer:
 *
 * ```
 * fun libraryEntries(): EntryProviderInstaller = {
 *     entry<LibraryKey> {
 *         LibraryScreen(onAlbumClick = { navigateTo(AlbumDetailKey(it)) })
 *     }
 * }
 * ```
 *
 * Nothing is named, nothing is passed, and installers that navigate nowhere are the identical shape. The
 * vocabulary - `navigateTo`, `navigateBack`, `navigateToTopLevel` - declares `context(nav: NavigationScope)`
 * and so exists *only* where this scope is in context.
 *
 * The guarantee that buys, stated exactly: **navigation coupling cannot be implicit.** A screen that wants to
 * navigate has to declare a scope parameter of its own, which is then visible in its signature for good. It
 * is not forbidden - `:core:navigation` exposes nav3 transitively, so a screen *can* name these types - but
 * it cannot happen quietly. A `CompositionLocal` is what gave that away: there, any composable in the tree
 * could navigate with nothing in its signature to show for it.
 *
 * Earlier attempts each traded away one property: an installer argument five of six modules ignored, or a
 * local reachable from anywhere. Context parameters keep one installer shape *and* the declaration
 * requirement.
 *
 * Stable since Kotlin 2.4, which this project is on; no compiler flag is required.
 */
