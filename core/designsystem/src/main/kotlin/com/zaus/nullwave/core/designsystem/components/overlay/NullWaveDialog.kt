package com.zaus.nullwave.core.designsystem.components.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSurface
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Native modal window. Compose conditionally and remove it in [onDismissRequest].
 * [properties] controls Back/outside dismissal; explicit action buttons remain caller-owned.
 * The wrapper consumes safe-drawing insets (including IME) in its edge-to-edge dialog window.
 * Title, content and actions share one scroll region so all remain reachable in a short window.
 * Supply finite, non-scrollable content here; use a sheet for large/lazy collections.
 */
@Composable
fun NullWaveDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    ),
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Box(Modifier.safeDrawingPadding().padding(NullWaveTheme.dimens.gutter)) {
            DialogContent(title, modifier, borderStyle, actions, content)
        }
    }
}

/** Shared with static previews; this surface by itself does not create a modal window. */
@Composable
internal fun DialogContent(
    title: String,
    modifier: Modifier = Modifier,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.Closed,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(title.isNotBlank()) { "Dialog title must not be blank" }
    val spacing = NullWaveTheme.spacing
    NullWaveSurface(
        modifier = modifier.widthIn(max = DialogMaxWidth).fillMaxWidth()
            .semantics { paneTitle = title },
        borderStyle = borderStyle,
        contentColor = NullWaveTheme.colors.textPrimary,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(title, Modifier.semantics { heading() }, style = NullWaveTheme.typography.h2)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm), content = content)
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                content = actions,
            )
        }
    }
}

private val DialogMaxWidth = 560.dp
