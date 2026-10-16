package com.zaus.nullwave.core.designsystem.components.sleeptimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.control.chip.NullWaveChip
import com.zaus.nullwave.core.designsystem.components.control.slider.NullWaveSlider
import com.zaus.nullwave.core.designsystem.components.control.stepper.NullWaveStepper
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.components.overlay.NullWaveModalSheet
import com.zaus.nullwave.core.designsystem.components.overlay.NullWaveSheetHeader
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.components.settings.NullWaveSwitchRow
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Preview(name = "Sleep timer - Yellow", group = "Sleep timer", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowSleepTimerPreview() = SleepTimerSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Sleep timer - Crimson", group = "Sleep timer", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonSleepTimerPreview() = SleepTimerSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Sleep timer - Dive", group = "Sleep timer", widthDp = 412, heightDp = 1100, locale = "en")
@Composable
private fun DiveSleepTimerPreview() = SleepTimerSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Sleep timer - Large text", group = "Sleep timer", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeSleepTimerPreview() = SleepTimerSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Sleep timer - RTL", group = "Sleep timer", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlSleepTimerPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        SleepTimerSheet(NullWaveColorVariant.Cyberpunk)
    }
}

private enum class TimerPreviewMode { Off, Running, Finished }
private enum class TimerPreviewAction { Start, Cancel, Restart }

/** Local fixtures only: no ticking clock, persistence or playback connection. */
private data class TimerPreviewState(
    val mode: TimerPreviewMode = TimerPreviewMode.Running,
    val minutes: Int = 30,
    val remainingLabel: String = "18:42",
    val remainingDescription: String = "Осталось 18 минут 42 секунды",
    val fadeOut: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var state by remember { mutableStateOf(TimerPreviewState()) }
        var enabled by remember { mutableStateOf(true) }
        var acceptUpdates by remember { mutableStateOf(true) }
        var glow by remember { mutableStateOf(true) }
        var sheetVisible by remember { mutableStateOf(false) }
        var durationRequests by remember { mutableIntStateOf(0) }
        var durationFinishes by remember { mutableIntStateOf(0) }
        var fadeRequests by remember { mutableIntStateOf(0) }
        var starts by remember { mutableIntStateOf(0) }
        var cancels by remember { mutableIntStateOf(0) }
        var restarts by remember { mutableIntStateOf(0) }
        var externalChanges by remember { mutableIntStateOf(0) }
        var dismissals by remember { mutableIntStateOf(0) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val spacing = NullWaveTheme.spacing
        val gutter = NullWaveTheme.dimens.gutter
        val changeMinutes: (Int) -> Unit = {
            durationRequests++
            if (acceptUpdates) state = state.copy(minutes = it)
        }
        val finishDuration: () -> Unit = { durationFinishes++ }
        val changeFade: (Boolean) -> Unit = {
            fadeRequests++
            if (acceptUpdates) state = state.copy(fadeOut = it)
        }
        val action: (TimerPreviewAction) -> Unit = { action ->
            when (action) {
                TimerPreviewAction.Cancel -> { cancels++; state = state.copy(mode = TimerPreviewMode.Off) }
                TimerPreviewAction.Start, TimerPreviewAction.Restart -> {
                    if (action == TimerPreviewAction.Start) starts++ else restarts++
                    state = state.copy(
                        mode = TimerPreviewMode.Running,
                        remainingLabel = "${state.minutes.toString().padStart(2, '0')}:00",
                        remainingDescription = "Осталось ${state.minutes} минут",
                    )
                }
            }
        }
        val dismiss: () -> Unit = { sheetVisible = false; dismissals++ }
        val closeSheet: () -> Unit = {
            scope.launch {
                sheetState.hide()
                if (!sheetState.isVisible && sheetVisible) dismiss()
            }
        }

        NullWaveScaffold(topBar = { NullWaveTopBar("Sleep timer preview") }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState())) {
                SleepTimerSample(state, enabled, glow, changeMinutes, finishDuration, changeFade, action)
                Column(Modifier.padding(gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    NullWaveSectionHeader("Условия проверки", supportingText = "Это локальная витрина: время не убывает и звук не меняется.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        TimerPreviewMode.entries.forEach { mode ->
                            NullWaveChip(mode.name, state.mode == mode, {
                                state = state.copy(mode = mode, remainingLabel = "18:42",
                                    remainingDescription = "Осталось 18 минут 42 секунды")
                                externalChanges++
                            })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        NullWaveButton("Открыть ModalSheet", { sheetVisible = true })
                        NullWaveButton("Enabled: $enabled", { enabled = !enabled })
                        NullWaveButton("Принимать выбор: $acceptUpdates", { acceptUpdates = !acceptUpdates })
                        NullWaveButton("Glow: $glow", { glow = !glow })
                        NullWaveButton("Сменить минуты извне", {
                            state = state.copy(minutes = if (state.minutes == 60) 15 else 60)
                            externalChanges++
                        })
                    }
                    Text("Duration: $durationRequests · Finished: $durationFinishes · Fade: $fadeRequests\n" +
                        "Start: $starts · Cancel: $cancels · Restart: $restarts\n" +
                        "External: $externalChanges · Sheet dismiss: $dismissals",
                        style = NullWaveTheme.typography.mono)
                    Text("Минуты и presets меняют только длительность следующего запуска. Остаток меняется по Start/Restart. " +
                        "При отказе выбор минут и fade не меняется; Enabled=false блокирует их callbacks и действия таймера. " +
                        "Открытие или закрытие sheet не сбрасывает состояние.", style = NullWaveTheme.typography.caption)
                    NullWaveSectionHeader("Длинное время и подпись")
                    NullWaveTimeReadout(
                        timeLabel = "123:45:06",
                        caption = "Длинное пояснение текущего значения для проверки переноса при крупном системном шрифте",
                        contentDescription = "123 часа 45 минут 6 секунд. Пример длинного времени.",
                        active = false,
                        glowEnabled = false,
                    )
                }
            }
        }
        if (sheetVisible) {
            NullWaveModalSheet(onDismissRequest = dismiss, sheetState = sheetState) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    SleepTimerSample(state, enabled, glow, changeMinutes, finishDuration, changeFade, action, onClose = closeSheet)
                    Text("Общее состояние с inline-примером. Duration: $durationRequests · Finished: $durationFinishes · Fade: $fadeRequests\n" +
                        "Start: $starts · Cancel: $cancels · Restart: $restarts",
                        Modifier.padding(gutter), style = NullWaveTheme.typography.mono)
                }
            }
        }
    }
}

/** Typed and stateless composition shared by inline and modal previews. */
@Composable
private fun SleepTimerSample(
    state: TimerPreviewState,
    enabled: Boolean,
    glowEnabled: Boolean,
    onMinutesChange: (Int) -> Unit,
    onDurationChangeFinished: () -> Unit,
    onFadeOutChange: (Boolean) -> Unit,
    onAction: (TimerPreviewAction) -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val spacing = NullWaveTheme.spacing
    val gutter = NullWaveTheme.dimens.gutter
    val active = state.mode == TimerPreviewMode.Running
    NullWaveSheetHeader("Таймер сна", supportingText = when (state.mode) {
        TimerPreviewMode.Off -> "Выключен · выбери длительность"
        TimerPreviewMode.Running -> "Запущен · показан фиксированный остаток"
        TimerPreviewMode.Finished -> "Завершён · можно запустить снова"
    }, onClose = onClose)
    Column(Modifier.padding(gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        NullWaveTimeReadout(
            timeLabel = when (state.mode) {
                TimerPreviewMode.Off -> "--:--"
                TimerPreviewMode.Running -> state.remainingLabel
                TimerPreviewMode.Finished -> "00:00"
            },
            caption = when (state.mode) {
                TimerPreviewMode.Off -> "Таймер выключен"
                TimerPreviewMode.Running -> "Осталось"
                TimerPreviewMode.Finished -> "Таймер завершён"
            },
            contentDescription = when (state.mode) {
                TimerPreviewMode.Off -> "Таймер сна выключен"
                TimerPreviewMode.Running -> state.remainingDescription
                TimerPreviewMode.Finished -> "Таймер сна завершён. Осталось ноль секунд"
            },
            active = active,
            glowEnabled = glowEnabled,
        )
        NullWaveSlider(state.minutes.toFloat(), { onMinutesChange(it.roundToInt()) }, "Длительность следующего запуска",
            Modifier.fillMaxWidth(), enabled = enabled, valueRange = 1f..120f, steps = 118,
            valueDescription = "${state.minutes} минут", onValueChangeFinished = onDurationChangeFinished)
        NullWaveStepper(state.minutes.toFloat(), { onMinutesChange(it.roundToInt()) }, "Следующий запуск",
            "${state.minutes} мин", step = 1f, valueRange = 1f..120f, enabled = enabled,
            onValueChangeFinished = onDurationChangeFinished)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            listOf(15, 30, 45, 60).forEach { minutes ->
                NullWaveChip("$minutes мин", state.minutes == minutes, {
                    onMinutesChange(minutes)
                    onDurationChangeFinished()
                }, enabled = enabled)
            }
        }
    }
    NullWaveSwitchRow("Плавное затухание за последние 20 секунд", state.fadeOut, onFadeOutChange,
        supportingText = "Только UI-значение: изменение звука не подключено", enabled = enabled)
    FlowRow(Modifier.padding(gutter), horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (active) NullWaveButton("Отменить таймер", { onAction(TimerPreviewAction.Cancel) },
            variant = NullWaveButtonVariant.Danger, enabled = enabled)
        if (state.mode == TimerPreviewMode.Off) {
            NullWaveButton("Запустить", { onAction(TimerPreviewAction.Start) }, enabled = enabled)
        } else {
            NullWaveButton("Перезапустить", { onAction(TimerPreviewAction.Restart) }, enabled = enabled)
        }
    }
}
