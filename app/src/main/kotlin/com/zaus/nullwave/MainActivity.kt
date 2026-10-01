package com.zaus.nullwave

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveNavigationRail
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveNavigationRailHeader
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveNavigationRailItem
import com.zaus.nullwave.core.designsystem.components.overlay.NullWaveScrim
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.NavigationScope
import com.zaus.nullwave.core.navigation.TopLevelDestination
import com.zaus.nullwave.core.navigation.inDisplayOrder
import com.zaus.nullwave.core.navigation.navigateToTopLevel
import com.zaus.nullwave.feature.library.api.LibraryKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey

/**
 * The single Activity host.
 *
 * Constructor-injected by MetroX's `MetroAppComponentFactory` - note there is no `by viewModels`,
 * no manual graph lookup and no `inject(this)`. The `@ContributesIntoMap` + `@ActivityKey` pair
 * puts it in the map that factory reads.
 *
 * Both injected sets are where modularity pays off: features contribute their own screens
 * ([entryProviderInstallers]) and their own rail entries ([topLevelDestinations]), so adding a
 * feature never edits this file.
 */
@ContributesIntoMap(ActivityScope::class, binding<Activity>())
@ActivityKey
@Inject
class MainActivity(
    private val entryProviderInstallers: Set<@JvmSuppressWildcards EntryProviderInstaller>,
    private val topLevelDestinations: Set<@JvmSuppressWildcards TopLevelDestination>,
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark only: pin the system bars to the theme's canvas instead of letting the platform
        // pick a light scrim. The rail handles its own insets.
        val barColor = NullWaveColors.Cyberpunk.bg.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(barColor),
            navigationBarStyle = SystemBarStyle.dark(barColor),
        )
        super.onCreate(savedInstanceState)
        setContent {
            NullWaveTheme {
                NullWaveApp(
                    destinations = topLevelDestinations,
                    entryProviderInstallers = entryProviderInstallers,
                )
            }
        }
    }
}

/** The host's [NavigationScope]. Named rather than anonymous so the shadowing is unambiguous. */
private class HostNavigationScope(
    override val backStack: NavBackStack<NavKey>,
) : NavigationScope

@Composable
private fun NullWaveApp(
    destinations: Set<TopLevelDestination>,
    entryProviderInstallers: Set<EntryProviderInstaller>,
) {
    // Owned by composition, not by the DI graph. `rememberNavBackStack` wraps it in `rememberSaveable`
    // with `NavKeySerializer`, so it survives configuration change AND process death - every NavKey in
    // the app is already @Serializable, so that costs nothing here.
    //
    // It used to be bound as @SingleIn(ActivityScope::class), which despite the name made it live as
    // long as the process: ActivityScope is a marker folded into the one AppGraph, and AppGraph is
    // `by lazy` on the Application. That meant a relaunch could resume on a stale destination while a
    // process-death relaunch resumed on Library, and an emptied stack stayed emptied for the life of
    // the process - NavDisplay throws on an empty back stack, so the crash repeated on every reopen
    // and backing out did not clear it. See NAVIGATION.md.
    val backStack = rememberNavBackStack(LibraryKey)

    // Everything the host offers a screen travels through here. Adding a capability means a member on
    // NavigationScope and a line in this class - never a change to EntryProviderInstaller, and so never
    // a change in any feature module.
    val nav = remember(backStack) { HostNavigationScope(backStack) }

    // Built once, not on every recomposition. `entryProvider { }` is a plain function (no Composer), so
    // remembering it is legal, and without this each recomposition of NullWaveApp re-runs all six
    // installers and rebuilds the key -> entry map.
    //
    // `with(nav)` supplies the installers' context parameter. That one call is the only place the scope
    // is named: from there it is implicitly in context for every installer and for the
    // navigateTo/navigateBack vocabulary they call.
    //
    // The captured @Composable lambdas are frozen along with it. Safe while installers close over only
    // stable values - `nav` is remembered, the DI singletons they inject are application-scoped. An
    // installer that ever captures changing state has to appear in these keys, or it will go stale.
    val resolveEntry = remember(entryProviderInstallers, nav) {
        entryProvider {
            with(nav) {
                entryProviderInstallers.forEach { install -> install() }
            }
        }
    }

    // A Set has no order; sections give it one. See RailSection.
    val railItems = remember(destinations) { destinations.inDisplayOrder() }

    // Held as MutableState rather than destructured with `by`, so that nothing in THIS function reads
    // it. Every read lives inside DrawerOverlay, which means opening or closing the drawer invalidates
    // only that composable's scope.
    //
    // This matters more than it looks: NavDisplay has no skipping path in its bytecode (verified - it
    // declares `backStack: List<T>`, which Compose treats as unstable), so it is NOT skippable and
    // recomposes whenever its caller does. Reading the flag here would therefore recompose the entire
    // content screen every time the drawer slid in or out.
    val railExpanded = rememberSaveable { mutableStateOf(false) }

    // NavBackStack is itself a StateObject backed by a SnapshotStateList, so reading it here is
    // already an observable snapshot read - no cast needed, and none would succeed.
    // Root rather than top: drilling from the library into an album should keep "Player" lit
    // rather than clearing the selection.
    val selectedKey by remember(backStack) { derivedStateOf { backStack.firstOrNull() } }
    val topBarTitle = railItems.firstOrNull { it.key == selectedKey }?.label.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NullWaveTheme.colors.bg)
    ) {
        // Outside the Box on purpose: a true modal drawer covers the app bar, but keeping the bar
        // above it means the same hamburger that opened the drawer can close it.
        // Reading `.value` inside the click lambda happens at event time, not composition time, so it
        // subscribes nothing. The bar can toggle the drawer without this function observing it.
        NullWaveTopBar(
            title = topBarTitle,
            onNavigationClick = { railExpanded.value = !railExpanded.value },
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Leave a strip of content visible so it reads as "tap outside to dismiss", and cap it
            // so the drawer does not sprawl on a tablet. Self-adjusting: 304dp on a 360dp phone,
            // 320dp on a 412dp one.
            val drawerWidth = (maxWidth - DrawerContentPeek).coerceAtMost(DrawerMaxWidth)

            // Bottom of the stack, and it never resizes when the drawer opens - which is the whole
            // reason this is a Box and not a Row.
            //
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = resolveEntry,
                modifier = Modifier.fillMaxSize(),
            )

            DrawerOverlay(
                isExpanded = railExpanded,
                items = railItems,
                selectedKey = selectedKey,
                width = drawerWidth,
                nav = nav,
            )
        }
    }
}

/**
 * The drawer, its scrim and its back handling - everything that reads [isExpanded].
 *
 * Separated from [NullWaveApp] purely to contain that read. A `MutableState` is passed rather than a
 * `Boolean` plus callbacks so the flag is observed *here*, which keeps a drawer toggle from invalidating
 * the scope that holds `NavDisplay`. Passing `Boolean` would defeat the whole point: the caller would have
 * to read it to pass it.
 */
@Composable
private fun BoxScope.DrawerOverlay(
    isExpanded: MutableState<Boolean>,
    items: List<TopLevelDestination>,
    selectedKey: NavKey?,
    width: Dp,
    nav: NavigationScope,
) {
    val expanded = isExpanded.value

    // An open drawer swallows back, before NavDisplay gets a chance to pop. The design's rule:
    // expanded player sheet -> collapsed; drawer open -> closed.
    BackHandler(enabled = expanded) { isExpanded.value = false }

    NullWaveScrim(
        isVisible = expanded,
        onDismiss = { isExpanded.value = false },
    )

    NullWaveNavigationRail(
        isExpanded = expanded,
        modifier = Modifier.align(Alignment.TopStart),
        collapsedWidth = 0.dp,
        expandedWidth = width,
        header = { NullWaveNavigationRailHeader(initials = "NW") },
    ) { expandedState ->
        items.forEach { destination ->
            NullWaveNavigationRailItem(
                icon = destination.icon,
                label = destination.label,
                isSelected = destination.key == selectedKey,
                isExpanded = expandedState,
                onClick = {
                    isExpanded.value = false
                    // The host is not special: it holds a NavigationScope like any installer and uses
                    // the same vocabulary. The back-stack mutations are internal to :core:navigation,
                    // so there is exactly one way to do this.
                    with(nav) { navigateToTopLevel(destination.key) }
                },
            )
        }
    }
}

/** Strip of content left visible beside an open drawer, so it reads as dismissable. */
private val DrawerContentPeek = 56.dp

/** Ceiling, so the drawer does not sprawl across a tablet. */
private val DrawerMaxWidth = 320.dp

/*
 * ---------------------------------------------------------------------------------------------
 * ADAPTIVE LAYOUT - parked until the phone app is further along.
 *
 * The design wants the rail only on expanded windows; phones get a modal drawer behind the top
 * bar's hamburger. Both read the same injected Set<TopLevelDestination>, so nothing above changes
 * except which container renders railItems.
 *
 * To enable:
 *   1. uncomment `implementation(libs.androidx.compose.material3.windowSizeClass)` in
 *      app/build.gradle.kts
 *   2. replace the Row in NullWaveApp with the branch below
 *   3. build NullWaveNavigationDrawer in :core:designsystem - the drawer carries what an 80dp rail
 *      cannot: the octagonal avatar header with display name and the mono sub-line
 *      (LOCAL LIBRARY - 1,284 TRACKS), and the dimmer EXTERNAL LINKS group.
 *
 * @Composable
 * private fun NullWaveApp(...) {
 *     val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
 *     val useRail = widthClass != WindowWidthSizeClass.COMPACT
 *
 *     if (useRail) {
 *         Row(Modifier.fillMaxSize()) {
 *             NullWaveNavigationRail(header = { ... }) { railItems.forEach { ... } }
 *             NavDisplay(...)
 *         }
 *     } else {
 *         val drawerState = rememberDrawerState(DrawerValue.Closed)
 *         NullWaveNavigationDrawer(
 *             drawerState = drawerState,
 *             items = railItems,
 *             selectedKey = selectedKey,
 *             onSelect = { key ->
 *                 with(nav) { navigateToTopLevel(key) }
 *                 scope.launch { drawerState.close() }
 *             },
 *         ) {
 *             NavDisplay(...)
 *         }
 *     }
 * }
 *
 * Back behaviour the design specifies, which the drawer branch has to honour:
 *   expanded player sheet -> collapsed;  drawer open -> closed.
 * ---------------------------------------------------------------------------------------------
 */
