package com.zaus.nullwave.core.designsystem.components.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.content.NullWaveTrackRow
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.control.searchField.NullWaveSearchField
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveSnackbarHost
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.components.player.NullWaveMiniPlayer
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlinx.coroutines.launch

@Preview(name = "Scaffold - Yellow", group = "Scaffold", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun YellowScaffoldPreview() = ScaffoldSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Scaffold - Crimson", group = "Scaffold", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun CrimsonScaffoldPreview() = ScaffoldSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Scaffold - Dive", group = "Scaffold", widthDp = 412, heightDp = 800, locale = "en")
@Composable
private fun DiveScaffoldPreview() = ScaffoldSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Scaffold - Large text", group = "Scaffold", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeScaffoldPreview() = ScaffoldSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Scaffold - RTL", group = "Scaffold", widthDp = 360, heightDp = 800, locale = "ar")
@Composable
private fun RtlScaffoldPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ScaffoldSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Preview(name = "Scaffold - No bars", group = "Scaffold", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun BareScaffoldPreview() = ScaffoldSheet(NullWaveColorVariant.Cyberpunk, bars = false)

@Preview(name = "Scaffold - Insets simulation", group = "Scaffold", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun InsetScaffoldPreview() = ScaffoldSheet(
    NullWaveColorVariant.Cyberpunk,
    windowInsets = WindowInsets(top = 24.dp, bottom = 220.dp),
    simulatedInsets = true,
)

@Composable
private fun ScaffoldSheet(
    variant: NullWaveColorVariant,
    bars: Boolean = true,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    simulatedInsets: Boolean = false,
) {
    NullWaveTheme(variant = variant) {
        var miniVisible by remember { mutableStateOf(bars) }
        var playing by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        var searches by remember { mutableIntStateOf(0) }
        var rowClicks by remember { mutableIntStateOf(0) }
        var lastClicks by remember { mutableIntStateOf(0) }
        var plays by remember { mutableIntStateOf(0) }
        var nextClicks by remember { mutableIntStateOf(0) }
        var expands by remember { mutableIntStateOf(0) }
        var snackbarPending by remember { mutableStateOf(false) }
        var snackbarResult by remember { mutableStateOf("—") }
        val host = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        val spacing = NullWaveTheme.spacing
        val gutter = NullWaveTheme.dimens.gutter

        NullWaveScaffold(
            modifier = Modifier.fillMaxSize(),
            windowInsets = windowInsets,
            topBar = {
                if (bars) NullWaveTopBar("Scaffold", actions = {
                    NullWaveIconButton(
                        NullWaveIcons.NowPlaying,
                        if (miniVisible) "Скрыть MiniPlayer" else "Показать MiniPlayer",
                        onClick = { miniVisible = !miniVisible },
                    )
                })
            },
            bottomBar = {
                if (miniVisible) NullWaveMiniPlayer(
                    title = "Deadzone Chorus / Ночной город",
                    isPlaying = playing,
                    onExpand = { expands++ },
                    onPlayPause = { playing = !playing; plays++ },
                    onNext = { nextClicks++ },
                    subtitle = "Static Pilgrim — Nullwave EP",
                    artwork = { NullWaveCoverArt(monogram = "NW") },
                    progress = 0.38f,
                    reduceMotion = true,
                )
            },
            snackbarHost = { NullWaveSnackbarHost(host) },
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
                contentPadding = innerPadding,
            ) {
                item(key = "controls") {
                    Column(Modifier.padding(gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        if (simulatedInsets) Text("Симуляция: top 24 dp / bottom 220 dp. Это не настоящая IME.",
                            style = NullWaveTheme.typography.caption)
                        Text("MiniPlayer: $miniVisible · строка: $rowClicks · последняя: $lastClicks\n" +
                            "Play: $plays · Next: $nextClicks · Expand: $expands · Search: $searches",
                            style = NullWaveTheme.typography.mono)
                        NullWaveSearchField(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = { searches++; keyboard?.hide(); focus.clearFocus() },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Поле для проверки IME",
                            placeholder = "Нажми, чтобы открыть клавиатуру",
                            supportingText = "Проверка ввода: список не фильтруется. Search закрывает IME.",
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            NullWaveButton(if (miniVisible) "Скрыть MiniPlayer" else "Показать MiniPlayer", { miniVisible = !miniVisible })
                            NullWaveButton("Snackbar", {
                                snackbarPending = true
                                scope.launch {
                                    try {
                                        snackbarResult = host.showSnackbar(
                                            "Сообщение над нижней панелью",
                                            actionLabel = "Проверить",
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Indefinite,
                                        ).name
                                    } finally {
                                        snackbarPending = false
                                    }
                                }
                            }, enabled = !snackbarPending)
                            NullWaveButton("К последней строке", {
                                scope.launch { listState.animateScrollToItem(ScaffoldTrackCount + 1) }
                            })
                        }
                        Text("Snackbar: $snackbarResult. Прокрути вниз: последняя кнопка должна целиком находиться над MiniPlayer.",
                            style = NullWaveTheme.typography.caption)
                    }
                }
                items(ScaffoldTrackCount, key = { "track-$it" }) { index ->
                    NullWaveTrackRow(
                        title = "Тестовый трек ${index + 1}",
                        onClick = { rowClicks++ },
                        subtitle = "NullWave preview",
                        durationLabel = "03:42",
                        artwork = { NullWaveCoverArt(monogram = (index + 1).toString()) },
                        reduceMotion = true,
                    )
                }
                item(key = "last") {
                    Column(Modifier.padding(gutter), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        NullWaveButton("Последняя строка — нажатий: $lastClicks", { lastClicks++ }, Modifier.fillMaxWidth())
                        Text("Конец списка. Кнопка доступна при видимой и скрытой панели, включая крупный шрифт и IME.",
                            style = NullWaveTheme.typography.caption)
                    }
                }
            }
        }
    }
}

private const val ScaffoldTrackCount = 20
