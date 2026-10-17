package com.zaus.nullwave.core.designsystem.components.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.content.NullWaveTrackRowBody
import com.zaus.nullwave.core.designsystem.components.content.TrackRowMinHeight
import com.zaus.nullwave.core.designsystem.components.content.TrackRowSpineWidth
import com.zaus.nullwave.core.designsystem.components.content.trackMetadataBelow
import com.zaus.nullwave.core.designsystem.components.overlay.NullWaveDialog
import com.zaus.nullwave.core.designsystem.components.overlay.NullWaveMenuItem
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * A queue entry sharing TrackRow's one-line title/subtitle and adaptive metadata. [available]
 * controls playback only: missing media can still have movement/removal actions. [enabled] disables
 * all input. Nullable callbacks define available actions; current/manual/automatic queue policy,
 * entry IDs and ordering belong to the host. A track appearing twice needs two distinct entry keys.
 *
 * The trailing handle is a separate target. Tap/Enter opens named actions; accessibility exposes
 * those same actions directly. Supplying [dragCallbacks] additionally enables handle-only long-
 * press dragging. Supply movement alternatives whenever the host permits a move. [dragging] is
 * controlled by the host; playback/menu actions pause during the drag without disabling its handle.
 * Enabled dragging adds a static primary halo when theme and local [glowEnabled] permit it,
 * including with reduced motion. A clipping ancestor
 * can trim it; the host should leave space around the lifted row and draw it above its neighbours.
 * [artwork] is decorative and noninteractive; [interactionSource] belongs to the playback target.
 */
@Composable
fun NullWaveQueueRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    durationLabel: String? = null,
    playing: Boolean = false,
    available: Boolean = true,
    enabled: Boolean = true,
    artwork: @Composable () -> Unit = { NullWaveCoverArt() },
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    dragging: Boolean = false,
    dragCallbacks: NullWaveDragCallbacks? = null,
    interactionSource: MutableInteractionSource? = null,
    reduceMotion: Boolean = false,
    glowEnabled: Boolean = true,
) {
    require(title.isNotBlank()) { "Queue entry title must not be blank" }
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing
    val dimens = NullWaveTheme.dimens
    val effects = NullWaveTheme.effects.withRestrictions(glowEnabled = glowEnabled)
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val playbackEnabled = enabled && available && !dragging
    val managementEnabled = enabled && !dragging
    val hasHandle = onMoveUp != null || onMoveDown != null || onRemove != null || dragCallbacks != null
    var actionsVisible by remember { mutableStateOf(false) }
    val latestManagementEnabled by rememberUpdatedState(managementEnabled)
    val latestMoveUp by rememberUpdatedState(onMoveUp)
    val latestMoveDown by rememberUpdatedState(onMoveDown)
    val latestRemove by rememberUpdatedState(onRemove)
    val latestPlaybackEnabled by rememberUpdatedState(playbackEnabled)
    val latestClick by rememberUpdatedState(onClick)
    val actionDescription = stringResource(R.string.nw_queue_actions, title)
    val moveUpLabel = stringResource(R.string.nw_queue_move_up)
    val moveDownLabel = stringResource(R.string.nw_queue_move_down)
    val removeLabel = stringResource(R.string.nw_queue_remove)
    val playingDescription = if (playing) stringResource(R.string.nw_track_playing) else null

    fun performAction(action: (() -> Unit)?): Boolean {
        if (!latestManagementEnabled || action == null) return false
        actionsVisible = false
        action()
        return true
    }

    // Re-enabling a row must not revive an old popup after a drag or an external queue update.
    LaunchedEffect(managementEnabled, hasHandle) {
        if (!managementEnabled || !hasHandle) actionsVisible = false
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bodyWidth = maxWidth - (if (hasHandle) dimens.gutter + dimens.touchTarget else 0.dp)
        val metadataBelow = trackMetadataBelow(bodyWidth, LocalDensity.current.fontScale)
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = TrackRowMinHeight)
                .then(
                    if (enabled && dragging && effects.glowEnabled) Modifier.dropShadow(
                        shape = RectangleShape,
                        shadow = Shadow(radius = DragGlowRadius, color = colors.primary, alpha = DragGlowAlpha),
                    ) else Modifier,
                )
                .background(if (playing || dragging || playbackEnabled && pressed) colors.surfaceRaised else colors.bg)
                .drawBehind {
                    if (playing) {
                        val width = TrackRowSpineWidth.toPx().coerceAtMost(size.width)
                        drawRect(
                            if (enabled && available) colors.primary else colors.textSecondary,
                            Offset(if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width, 0f),
                            Size(width, size.height),
                        )
                    }
                }
                .then(if (enabled && dragging) Modifier.border(dimens.focusBorder, colors.primary, RectangleShape) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).defaultMinSize(minHeight = TrackRowMinHeight)
                    .then(if (playbackEnabled && focused) Modifier.border(dimens.focusBorder, colors.textPrimary, RectangleShape) else Modifier)
                    .semantics { if (playingDescription != null) stateDescription = playingDescription }
                    .clickable(
                        interactionSource = source,
                        indication = ripple(),
                        enabled = playbackEnabled,
                        role = Role.Button,
                        onClick = { if (latestPlaybackEnabled) latestClick() },
                    )
                    .padding(
                        start = dimens.gutter,
                        end = if (hasHandle) spacing.sm else dimens.gutter,
                        top = spacing.sm,
                        bottom = spacing.sm,
                    ),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NullWaveTrackRowBody(
                    title = title,
                    subtitle = subtitle,
                    durationLabel = durationLabel,
                    playing = playing,
                    enabled = enabled && available,
                    metadataBelow = metadataBelow,
                    artwork = artwork,
                    reduceMotion = reduceMotion,
                    unavailable = !available,
                )
            }
            if (hasHandle) NullWaveDragHandle(
                contentDescription = actionDescription,
                onClick = { if (latestManagementEnabled) actionsVisible = true },
                modifier = Modifier.padding(end = dimens.gutter).semantics {
                    customActions = if (managementEnabled) buildList {
                        if (onMoveUp != null) add(CustomAccessibilityAction(moveUpLabel) { performAction(latestMoveUp) })
                        if (onMoveDown != null) add(CustomAccessibilityAction(moveDownLabel) { performAction(latestMoveDown) })
                        if (onRemove != null) add(CustomAccessibilityAction(removeLabel) { performAction(latestRemove) })
                    } else emptyList()
                },
                enabled = enabled,
                dragging = dragging,
                dragCallbacks = dragCallbacks,
            )
        }
    }
    if (actionsVisible && managementEnabled && hasHandle) {
        NullWaveDialog(
            title = actionDescription,
            onDismissRequest = { actionsVisible = false },
            actions = {
                NullWaveMenuItem(stringResource(R.string.nw_sheet_close), { actionsVisible = false })
            },
        ) {
            if (dragCallbacks != null) Text(stringResource(R.string.nw_queue_drag_hint), style = NullWaveTheme.typography.body)
            if (onMoveUp != null) NullWaveMenuItem(moveUpLabel, { performAction(latestMoveUp) })
            if (onMoveDown != null) NullWaveMenuItem(moveDownLabel, { performAction(latestMoveDown) })
            if (onRemove != null) NullWaveMenuItem(removeLabel, { performAction(latestRemove) })
        }
    }
}

private val DragGlowRadius = 10.dp
private const val DragGlowAlpha = 0.28f
