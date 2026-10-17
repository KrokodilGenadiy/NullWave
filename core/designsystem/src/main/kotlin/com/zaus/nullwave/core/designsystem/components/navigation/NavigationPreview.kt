package com.zaus.nullwave.core.designsystem.components.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.preview.rememberPreviewFocus
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Navigation - Yellow", group = "Navigation", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowNavigationPreview() = NavigationSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Navigation - Crimson", group = "Navigation", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonNavigationPreview() = NavigationSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Navigation - Dive", group = "Navigation", widthDp = 360, heightDp = 1100, locale = "en")
@Composable
private fun DiveNavigationPreview() = NavigationSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Navigation - Large text", group = "Navigation", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeNavigationPreview() = NavigationSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Navigation - RTL", group = "Navigation", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlNavigationPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        NavigationSheet(NullWaveColorVariant.Cyberpunk)
    }
}

/** A component gallery, not a library/navigation implementation. Callbacks only change demo state. */
@Composable
private fun NavigationSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var actions by remember { mutableIntStateOf(0) }
        var selectionCount by remember { mutableIntStateOf(3) }
        var selectionVisible by remember { mutableStateOf(true) }
        var tab by remember { mutableIntStateOf(0) }
        var drawer by remember { mutableIntStateOf(0) }
        val drawerFocus = rememberPreviewFocus()
        val tabs = remember {
            listOf("Песни", "Альбомы", "Исполнители", "Плейлисты", "Папки").map(::NullWaveTabItem) +
                NullWaveTabItem("Недоступный раздел", enabled = false)
        }
        NullWavePreviewSurface {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
            ) {
                NullWaveSectionHeader("Navigation / ${variant.name}", Modifier.padding(NullWaveTheme.dimens.gutter))
                Text("Actions: $actions · Tab: $tab · Drawer: $drawer", Modifier.padding(horizontal = NullWaveTheme.dimens.gutter), style = NullWaveTheme.typography.mono)
                NullWaveTopBar(
                    "Медиатека / Library",
                    navigationIcon = { NullWaveIconButton(NullWaveIcons.Menu, "Открыть меню", { actions++ }) },
                    actions = {
                        NullWaveIconButton(NullWaveIcons.Search, "Поиск", { actions++ })
                        NullWaveIconButton(NullWaveIcons.More, "Другие действия", { actions++ })
                    },
                )
                NullWaveTopBar("Очень длинный заголовок экрана с названием коллекции", navigationIcon = {
                    NullWaveIconButton(NullWaveIcons.Back, "Назад", { actions++ })
                })
                if (selectionVisible) {
                    NullWaveSelectionTopBar(selectionCount, onClose = { selectionVisible = false }) {
                        NullWaveIconButton(
                            NullWaveIcons.Check, "Выбрать всё", { selectionCount = 125 },
                            tint = LocalContentColor.current, pressedTint = NullWaveTheme.colors.secondary,
                        )
                        NullWaveIconButton(
                            NullWaveIcons.More, "Недоступное действие", { actions++ }, enabled = false,
                            tint = LocalContentColor.current, pressedTint = NullWaveTheme.colors.secondary,
                            disabledTint = NullWaveTheme.colors.bg,
                        )
                    }
                }
                NullWaveButton("Выбор с нуля", { selectionCount = 0; selectionVisible = true }, Modifier.padding(horizontal = NullWaveTheme.dimens.gutter))
                NullWaveTabRow(tabs, tab, { tab = it })
                NullWaveButton("Открыть последнюю доступную вкладку", { tab = 4 }, Modifier.padding(horizontal = NullWaveTheme.dimens.gutter))
                // A selected destination may become unavailable while its content is still visible.
                NullWaveTabRow(listOf(NullWaveTabItem("Выбрана, но недоступна", false)), 0, { actions++ })
                Column(
                    Modifier.padding(horizontal = NullWaveTheme.dimens.gutter).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs),
                ) {
                    NullWaveDrawerItem("Медиатека", drawer == 0, { drawer = 0 }, icon = NullWaveIcons.Songs, badge = {
                        Text("128", style = NullWaveTheme.typography.mono)
                    })
                    NullWaveDrawerItem("Мои длинные названия плейлистов / Playlists", drawer == 1, { drawer = 1 },
                        modifier = drawerFocus.modifier,
                        icon = NullWaveIcons.Playlists,
                        borderStyle = NullWaveBorderStyle.ClippedCorners)
                    NullWaveDrawerItem("Настройки", drawer == 2, { drawer = 2 }, icon = NullWaveIcons.Settings)
                    NullWaveDrawerItem("Недоступный раздел", false, { actions++ }, icon = NullWaveIcons.Folders, enabled = false)
                }
                NullWaveDrawerItem("Выбран, но недоступен", true, { actions++ },
                    Modifier.padding(horizontal = NullWaveTheme.dimens.gutter), icon = NullWaveIcons.Albums, enabled = false)
                NullWaveButton(
                    "Фокус на плейлисты",
                    drawerFocus::requestFocus,
                    Modifier.padding(horizontal = NullWaveTheme.dimens.gutter),
                )
                Text(
                    "Плейлисты · ${drawerFocus.status}",
                    Modifier.padding(NullWaveTheme.dimens.gutter),
                    style = NullWaveTheme.typography.mono,
                )
            }
        }
    }
}
