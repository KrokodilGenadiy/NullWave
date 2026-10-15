package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Cards - Yellow", group = "Cards and metadata", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowCardsPreview() = MediaCardSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Cards - Crimson", group = "Cards and metadata", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonCardsPreview() = MediaCardSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Cards - Dive", group = "Cards and metadata", widthDp = 412, heightDp = 1100, locale = "en")
@Composable
private fun DiveCardsPreview() = MediaCardSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Cards - Large text", group = "Cards and metadata", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeCardsPreview() = MediaCardSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Cards - RTL", group = "Cards and metadata", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlCardsPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { MediaCardSheet(NullWaveColorVariant.Cyberpunk) }
}

@Composable
private fun MediaCardSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var opens by remember { mutableIntStateOf(0) }
        var more by remember { mutableIntStateOf(0) }
        var closedBorder by remember { mutableStateOf(false) }
        var enabled by remember { mutableStateOf(true) }
        var reduceMotion by remember { mutableStateOf(false) }
        var focusResult by remember { mutableStateOf("Фокус не запрошен") }
        val focus = remember { FocusRequester() }
        val inputMode = LocalInputModeManager.current
        val border = if (closedBorder) NullWaveBorderStyle.Closed else NullWaveBorderStyle.ClippedCorners
        Surface(color = NullWaveTheme.colors.bg, contentColor = NullWaveTheme.colors.textPrimary) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
            ) {
                Text("Open: $opens · More: $more", style = NullWaveTheme.typography.mono)
                NullWaveButton("Контур: ${if (closedBorder) "замкнут" else "срезан"}", { closedBorder = !closedBorder })
                NullWaveButton("Карточки: ${if (enabled) "enabled" else "disabled"}", { enabled = !enabled })
                NullWaveButton("Reduce motion: ${if (reduceMotion) "ON" else "OFF"}", { reduceMotion = !reduceMotion })
                NullWaveButton("Фокус на первый альбом", {
                    focusResult = if (inputMode.requestInputMode(InputMode.Keyboard) && focus.requestFocus()) "Запрос принят" else "Запрос отклонён"
                })
                Text(focusResult, style = NullWaveTheme.typography.mono)
                NullWaveSectionHeader("Grid / альбомы и плейлисты")
                FlowRow(
                    Modifier.fillMaxWidth(), maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
                    horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
                ) {
                    NullWaveMediaGridCard(
                        "Nullwave EP", { opens++ }, Modifier.weight(1f).focusRequester(focus), supportingText = "Static Pilgrim",
                        artwork = { NullWaveCoverArt(monogram = "NW") }, enabled = enabled, borderStyle = border,
                    )
                    NullWaveMediaGridCard(
                        "Ночной маршрут — очень длинное название плейлиста", { opens++ }, Modifier.weight(1f),
                        supportingText = "128 треков · 8 ч 24 мин", artwork = { NullWaveCoverArt(monogram = "НМ") },
                        badge = { NullWaveBadge("Умный", variant = NullWaveBadgeVariant.Secondary) }, enabled = enabled, borderStyle = border,
                    )
                    NullWaveMediaGridCard(
                        "Загрузка обложки", { opens++ }, Modifier.weight(1f),
                        artwork = { NullWaveCoverArt(placeholder = NullWaveArtworkPlaceholder.Loading, reduceMotion = reduceMotion) }, enabled = enabled, borderStyle = border,
                    )
                    NullWaveMediaGridCard(
                        "Недоступный альбом", { opens++ }, Modifier.weight(1f), supportingText = "Папка отключена",
                        artwork = { NullWaveCoverArt(placeholder = NullWaveArtworkPlaceholder.Error) }, enabled = false, borderStyle = border,
                    )
                }
                NullWaveSectionHeader("List / исполнитель, папка, плейлист")
                NullWaveMediaListItem(
                    "Ночной Вектор", { opens++ }, supportingText = "3 альбома · 41 трек",
                    artwork = { NullWaveCoverArt(monogram = "НВ") }, enabled = enabled, borderStyle = border,
                    onMoreClick = { more++ }, moreDescription = "Другие действия: Ночной Вектор",
                )
                NullWaveMediaListItem(
                    "Музыка для очень долгой дороги за город", { opens++ }, supportingText = "/Music/Collections/Night drive/",
                    badge = { NullWaveBadge("На устройстве") }, enabled = enabled, borderStyle = border,
                    onMoreClick = { more++ }, moreDescription = "Другие действия: папка музыки",
                )
                NullWaveMediaListItem("Пустой плейлист", { opens++ }, supportingText = "0 треков", enabled = enabled, borderStyle = border)
                NullWaveSectionHeader("Badge / без действий")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
                    NullWaveBadge("FLAC", borderStyle = border)
                    NullWaveBadge("Избранное", variant = NullWaveBadgeVariant.Primary, borderStyle = border)
                    NullWaveBadge("Автоматически обновляемый плейлист", variant = NullWaveBadgeVariant.Secondary, borderStyle = border)
                }
                NullWaveSectionHeader("StatBar / готовые значения")
                NullWaveStatBar(listOf(NullWaveStat("Треки", "128"), NullWaveStat("Длительность", "8 ч 24 мин"), NullWaveStat("Размер", "3,8 ГБ")))
                NullWaveStatBar(listOf(NullWaveStat("Треки", "0")))
                NullWaveSectionHeader("Metadata / полные значения")
                NullWaveMetadataRow("Название", "Очень длинное название композиции, которое в подробностях читается полностью")
                NullWaveMetadataRow("Исполнитель", "Ночной Вектор · приглашённый исполнитель с длинным именем")
                NullWaveMetadataRow("Формат", "FLAC · 24 bit", monospace = true)
                NullWaveMetadataRow("Частота дискретизации", "96 000 Гц", monospace = true)
                NullWaveMetadataRow("Путь", "/storage/emulated/0/Music/НочнойВектор/ОченьДлинноеИмяАльбома/01_LongUnbrokenTrackFilenameWithoutSpaces.flac", monospace = true)
                NullWaveMetadataRow("Год", "Не указан")
            }
        }
    }
}
