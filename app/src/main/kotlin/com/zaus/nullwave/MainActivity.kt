package com.zaus.nullwave

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.zaus.nullwave.core.designsystem.theme.NullWaveColors
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
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
 * [entryProviderInstallers] is where modularity pays off: each feature contributes its own screens
 * into this set, so adding a feature never edits this file.
 */
@ContributesIntoMap(ActivityScope::class, binding<Activity>())
@ActivityKey
@Inject
class MainActivity(
    private val backStack: NavBackStack<NavKey>,
    private val entryProviderInstallers: Set<@JvmSuppressWildcards EntryProviderInstaller>,
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark only: pin the system bars to the theme's canvas instead of letting the platform
        // pick a light scrim. The app bar and the mini-player handle their own insets.
        val barColor = NullWaveColors.Cyberpunk.bg.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(barColor),
            navigationBarStyle = SystemBarStyle.dark(barColor),
        )
        super.onCreate(savedInstanceState)
        setContent {
            NullWaveTheme {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entryProviderInstallers.forEach { install -> install() }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NullWaveTheme.colors.bg),
                )
            }
        }
    }
}
