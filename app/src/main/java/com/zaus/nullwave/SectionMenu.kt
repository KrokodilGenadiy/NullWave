package com.zaus.nullwave

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveDrawerItem
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveOctagonFrame
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.about.api.AboutKey
import com.zaus.nullwave.feature.equalizer.api.EqualizerKey
import com.zaus.nullwave.feature.equalizer.api.R as EqualizerR
import com.zaus.nullwave.feature.library.api.LibraryKey
import com.zaus.nullwave.feature.about.api.R as AboutR
import com.zaus.nullwave.feature.settings.api.R as SettingsR
import com.zaus.nullwave.feature.settings.api.SettingsKey
import com.zaus.nullwave.feature.sleeptimer.api.R as SleepTimerR
import com.zaus.nullwave.feature.sleeptimer.api.SleepTimerKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart

internal data class MenuDestination(val key: NavKey, @StringRes val title: Int, @DrawableRes val icon: Int)

internal val menuDestinations = listOf(
    MenuDestination(LibraryKey, R.string.menu_library, NullWaveIcons.NowPlaying),
    MenuDestination(EqualizerKey, EqualizerR.string.equalizer_title, NullWaveIcons.Equalizer),
    MenuDestination(SleepTimerKey, SleepTimerR.string.sleeptimer_title, NullWaveIcons.SleepTimer),
    MenuDestination(SettingsKey, SettingsR.string.settings_title, NullWaveIcons.Settings),
    MenuDestination(AboutKey, AboutR.string.about_title, NullWaveIcons.Info),
)

@Composable
internal fun SectionMenu(current: NavKey, detail: String?, onMenuDestination: (NavKey) -> Unit) {
    val drawer = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val openingBack = remember(drawer, scope) {
        object : OnBackPressedCallback(drawer.targetValue == WideNavigationRailValue.Expanded) {
            override fun handleOnBackPressed() {
                isEnabled = false
                scope.launch(start = CoroutineStart.UNDISPATCHED) { drawer.snapTo(WideNavigationRailValue.Collapsed) }
            }
        }
    }
    val dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
    val owner = LocalLifecycleOwner.current
    DisposableEffect(dispatcher, owner, openingBack) {
        dispatcher.addCallback(owner, openingBack)
        onDispose { openingBack.remove() }
    }
    val colors = NullWaveTheme.colors
    val uriHandler = LocalUriHandler.current
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    NullWaveIconButton(NullWaveIcons.Menu, stringResource(R.string.menu_open), {
        // Android can deliver Back before Material creates the dialog on an animation frame.
        openingBack.isEnabled = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) { drawer.expand() }
    })
    // The standalone modal owns its window, including the scrim over the Activity's MiniPlayer.
    ModalWideNavigationRail(
        state = drawer,
        hideOnCollapse = true,
        modifier = Modifier.width((windowWidth - 48.dp).coerceIn(0.dp, 316.dp)).testTag("section_drawer"),
        expandedShape = RectangleShape,
        colors = WideNavigationRailDefaults.colors(
            modalContainerColor = colors.surface,
            modalContentColor = colors.textPrimary,
            modalScrimColor = Color(0xC7030507),
        ),
    ) {
        val dialogHasFocus = LocalWindowInfo.current.isWindowFocused
        SideEffect { if (dialogHasFocus) openingBack.isEnabled = false }
        // The modal has its own window. NullWave's surface stays dark in the system light theme.
        val view = LocalView.current
        LaunchedEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NullWaveOctagonFrame(Modifier.size(56.dp), color = colors.surfaceRaised,
                    border = BorderStroke(1.dp, colors.primary)) {
                    NullWaveIcon(NullWaveIcons.Songs, null, tint = colors.primary)
                }
                Column {
                    Text("NULLWAVE", style = NullWaveTheme.typography.h1, color = colors.primary)
                    Text(stringResource(R.string.menu_local), style = NullWaveTheme.typography.caption)
                    if (detail != null) {
                        Text(detail,
                            style = NullWaveTheme.typography.micro, color = colors.textSecondary)
                    }
                }
            }
            NullWaveDivider()
            Column(Modifier.selectableGroup().padding(vertical = 12.dp)) {
                menuDestinations.forEach { destination ->
                    NullWaveDrawerItem(stringResource(destination.title), selected = destination.key == current, icon = destination.icon,
                        onClick = {
                            scope.launch {
                                if (destination.key == current) {
                                    drawer.collapse()
                                } else {
                                    drawer.snapTo(WideNavigationRailValue.Collapsed)
                                    onMenuDestination(destination.key)
                                }
                            }
                        })
                }
            }
            NullWaveDivider()
            ExternalLinks(onOpen = uriHandler::openUri)
        }
    }
}

private val externalLinks = listOf(
    "Spotify" to "https://open.spotify.com/",
    "YouTube Music" to "https://music.youtube.com/",
    "SoundCloud" to "https://soundcloud.com/",
    "Bandcamp" to "https://bandcamp.com/",
)

@Composable
private fun ExternalLinks(onOpen: (String) -> Unit) {
    var linkFailed by remember { mutableStateOf(false) }
    Text(stringResource(R.string.menu_links),
        Modifier.padding(12.dp).semantics { heading() },
        style = NullWaveTheme.typography.micro, color = NullWaveTheme.colors.textSecondary)
    externalLinks.forEach { (label, url) ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                linkFailed = try {
                    onOpen(url)
                    false
                } catch (_: IllegalArgumentException) {
                    true
                }
            }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$label ↗", style = NullWaveTheme.typography.body, color = NullWaveTheme.colors.textSecondary)
        }
    }
    if (linkFailed) Text(stringResource(R.string.menu_link_error),
        Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        style = NullWaveTheme.typography.caption, color = NullWaveTheme.colors.danger)
}
