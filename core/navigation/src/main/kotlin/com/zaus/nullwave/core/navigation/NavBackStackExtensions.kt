package com.zaus.nullwave.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The app's entire navigation vocabulary: three functions, each requiring a [NavigationScope] in context.
 *
 * Navigation 3 has no `Navigator` to inject - the back stack *is* the navigation state, and you mutate it.
 * That is a feature, not a gap: nothing holds a second copy of the truth, there is nothing to keep in sync,
 * and the stack is directly inspectable in a test.
 *
 * The mutations themselves are `internal` extensions at the foot of this file. They are not part of the
 * public surface because there is no second way to navigate: the host has a [NavigationScope] like everyone
 * else and uses the same three functions. One vocabulary, one spelling per operation.
 */

/**
 * Push a detail screen.
 *
 * Guarded against pushing the key already on top, which is what a double tap on a list row produces: two
 * identical entries, and two backs needed to escape one mistake. Guarding here means no caller has to
 * remember to debounce.
 *
 * ## The context parameter is the boundary
 *
 * This resolves inside an `EntryProviderInstaller`, where the host has supplied a [NavigationScope], and not
 * in a screen composable, where nothing has:
 *
 * ```
 * e: No context argument for 'nav: NavigationScope' found.
 * ```
 *
 * What that buys is precise, and worth stating precisely: **navigation coupling cannot be implicit.** A
 * screen *can* still navigate - by declaring a `NavigationScope` or a `NavBackStack` parameter of its own,
 * since `:core:navigation` exposes nav3 transitively - but it cannot do so without that parameter appearing
 * in its signature, permanently and visibly. Which is exactly what a `CompositionLocal` gave away: there,
 * any composable in the tree could navigate with nothing to show for it.
 *
 * So this is not a wall, it is a declaration requirement. The wall would be a module split - screens in a
 * module that does not depend on `:core:navigation` at all - and that costs a module per feature for the
 * difference between "visible in the signature" and "unnameable". See `NAVIGATION.md`.
 */
context(nav: NavigationScope)
fun navigateTo(key: NavKey) = nav.backStack.push(key)

/**
 * Pop one entry, refusing to empty the stack.
 *
 * `NavDisplay`'s own `onBack` uses `removeLastOrNull`, which *can* empty it - correct there, because an empty
 * stack is how the Activity finishes. This is for in-app "up" affordances, where leaving no destination
 * rendered would be a blank screen rather than an exit. Same context requirement as [navigateTo].
 */
context(nav: NavigationScope)
fun navigateBack() = nav.backStack.pop()

/**
 * Switch to a top-level destination - a drawer or rail selection.
 *
 * Replaces the stack rather than pushing onto it: tapping Settings, then Equalizer, then Settings again
 * should leave one entry, not three. Detail screens push with [navigateTo].
 *
 * The early return matters - without it, tapping the destination you are already on clears and re-adds it,
 * which restarts the screen and replays the enter transition for no reason. Same context requirement as
 * [navigateTo].
 */
context(nav: NavigationScope)
fun navigateToTopLevel(key: NavKey) = nav.backStack.switchTopLevel(key)

// The mutations. Internal: callers use the three functions above, which is what keeps one operation to one
// name. Kept as extensions rather than inlined so each guard has somewhere to be documented and tested.

internal fun NavBackStack<NavKey>.push(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

internal fun NavBackStack<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}

internal fun NavBackStack<NavKey>.switchTopLevel(key: NavKey) {
    if (lastOrNull() == key && size == 1) return
    clear()
    add(key)
}
