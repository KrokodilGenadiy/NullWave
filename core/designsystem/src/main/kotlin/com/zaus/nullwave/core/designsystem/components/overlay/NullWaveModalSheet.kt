package com.zaus.nullwave.core.designsystem.components.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.modifier.nullWaveBorder
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Material modal window with chamfered top corners. Compose only while the sheet is requested.
 * Remove it in [onDismissRequest]; for an explicit close, await sheetState.hide(), then remove it
 * only if !sheetState.isVisible (a supplied state's confirmValueChange may veto hiding).
 * [properties] controls Back/scrim; [sheetGesturesEnabled] controls dragging, not explicit actions.
 * Material handles IME and [contentWindowInsets]; do not add the host screen's insets a second time.
 * Content owns padding and scrolling: use a bounded verticalScroll Column or LazyColumn as needed.
 */
@ExperimentalMaterial3Api
@Composable
fun NullWaveModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    sheetGesturesEnabled: Boolean = true,
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NullWaveTheme.colors
    val shape = NullWaveTheme.shapes.large.copy(
        topStart = NullWaveTheme.shapes.large.topEnd,
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp),
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.nullWaveBorder(
            BorderStroke(NullWaveTheme.dimens.hairline, colors.outline), shape, borderStyle,
        ),
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        properties = properties,
        shape = shape,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(shape = RectangleShape, color = colors.textSecondary)
        },
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}
