package com.zaus.nullwave

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.zaus.nullwave.core.designsystem.components.NullWaveNavigationRail
import com.zaus.nullwave.core.designsystem.components.NullWaveNavigationRailHeader
import com.zaus.nullwave.core.designsystem.components.NullWaveNavigationRailItem
import com.zaus.nullwave.core.designsystem.components.NullWaveScrim
import com.zaus.nullwave.core.designsystem.components.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.navigation.TopLevelDestination
import com.zaus.nullwave.core.navigation.inDisplayOrder
import com.zaus.nullwave.core.navigation.switchTopLevel
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
    private val backStack: NavBackStack<NavKey>,
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
                    backStack = backStack,
                    destinations = topLevelDestinations,
                    entryProviderInstallers = entryProviderInstallers,
                )
            }
        }
    }
}

@Composable
private fun NullWaveApp(
    backStack: NavBackStack<NavKey>,
    destinations: Set<TopLevelDestination>,
    entryProviderInstallers: Set<EntryProviderInstaller>,
) {
    // A Set has no order; sections give it one. See RailSection.
    val railItems = remember(destinations) { destinations.inDisplayOrder() }
    var railExpanded by rememberSaveable { mutableStateOf(false) }

    // NavBackStack is itself a StateObject backed by a SnapshotStateList, so reading it here is
    // already an observable snapshot read - no cast needed, and none would succeed.
    // Root rather than top: drilling from the library into an album should keep "Player" lit
    // rather than clearing the selection.
    val selectedKey by remember(backStack) { derivedStateOf { backStack.firstOrNull() } }
    val topBarTitle = railItems.firstOrNull { it.key == selectedKey }?.label.orEmpty()

    // An open drawer swallows back, before NavDisplay gets a chance to pop. The design's rule:
    // expanded player sheet -> collapsed; drawer open -> closed.
    BackHandler(enabled = railExpanded) { railExpanded = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NullWaveTheme.colors.bg)
    ) {
        // Outside the Box on purpose: a true modal drawer covers the app bar, but keeping the bar
        // above it means the same hamburger that opened the drawer can close it.
        NullWaveTopBar(
            title = topBarTitle,
            onNavigationClick = { railExpanded = !railExpanded },
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Leave a strip of content visible so it reads as "tap outside to dismiss", and cap it
            // so the drawer does not sprawl on a tablet. Self-adjusting: 304dp on a 360dp phone,
            // 320dp on a 412dp one.
            val drawerWidth = (maxWidth - DrawerContentPeek).coerceAtMost(DrawerMaxWidth)

            // Bottom of the stack, and it never resizes when the drawer opens - which is the whole
            // reason this is a Box and not a Row.
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entryProviderInstallers.forEach { install -> install() }
                },
                modifier = Modifier.fillMaxSize(),
            )

            NullWaveScrim(
                isVisible = railExpanded,
                onDismiss = { railExpanded = false },
            )

            NullWaveNavigationRail(
                isExpanded = railExpanded,
                modifier = Modifier.align(Alignment.TopStart),
                collapsedWidth = 0.dp,
                expandedWidth = drawerWidth,
                header = { NullWaveNavigationRailHeader(initials = "NW") },
            ) { expandedState ->
                railItems.forEach { destination ->
                    NullWaveNavigationRailItem(
                        icon = destination.icon,
                        label = destination.label,
                        isSelected = destination.key == selectedKey,
                        isExpanded = expandedState,
                        onClick = {
                            railExpanded = false
                            backStack.switchTopLevel(destination.key)
                        },
                    )
                }
            }
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
 *                 backStack.switchTopLevel(key)
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
