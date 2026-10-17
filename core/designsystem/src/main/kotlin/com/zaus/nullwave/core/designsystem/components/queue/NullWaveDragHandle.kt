package com.zaus.nullwave.core.designsystem.components.queue

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * A handle gesture, not an ordering engine. [onDrag] reports vertical movement in physical pixels.
 * The host owns stable queue-entry keys, item geometry, tentative order and committing/rolling back.
 * One callback bundle is captured at gesture start; it must remain valid until its matching stop.
 * An active session receives exactly one [onStop], including cancellation, disable and disposal.
 */
class NullWaveDragCallbacks(
    val onStart: () -> Unit,
    val onDrag: (deltaY: Float) -> Unit,
    val onStop: (cancelled: Boolean) -> Unit,
)

/**
 * A 48 dp reorder target. Tap/Enter calls [onClick] for named movement alternatives; holding the
 * handle starts an optional drag. Movement before the long press remains available to scrolling.
 * The detector consumes the long-press gesture, including its release, so it cannot also click.
 * [dragging] is host-owned visual state; keep [enabled] true while a permitted drag is in progress.
 * During drag the handle stays transparent, with no press/ripple layer over its parent's surface.
 * Horizontal displacement is ignored. This component does not choose a destination or scroll a list.
 */
@Composable
fun NullWaveDragHandle(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dragging: Boolean = false,
    dragCallbacks: NullWaveDragCallbacks? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = NullWaveTheme.colors
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val latestCallbacks by rememberUpdatedState(dragCallbacks)
    val latestEnabled by rememberUpdatedState(enabled)
    var handlingDrag by remember { mutableStateOf(false) }
    val dragDescription = if (dragging || handlingDrag) stringResource(R.string.nw_queue_dragging) else null
    val dragModifier = if (enabled && dragCallbacks != null) {
        // Callback objects may be recreated during reordering. Keying on one would cancel every
        // update; stable entry keys belong to the containing list, not to callback identity.
        Modifier.pointerInput(Unit) {
            var session: NullWaveDragCallbacks? = null
            fun stop(cancelled: Boolean) {
                val active = session ?: return
                session = null
                handlingDrag = false
                active.onStop(cancelled)
            }
            try {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        val callbacks = latestCallbacks
                        if (latestEnabled && callbacks != null) {
                            session = callbacks
                            handlingDrag = true
                            callbacks.onStart()
                        }
                    },
                    onDrag = { _, amount ->
                        if (latestEnabled && latestCallbacks != null) session?.onDrag(amount.y)
                        else stop(cancelled = true)
                    },
                    onDragEnd = { stop(cancelled = !latestEnabled || latestCallbacks == null) },
                    onDragCancel = { stop(cancelled = true) },
                )
            } finally {
                // Covers removal/rekeying even if no pointer event follows the composition change.
                stop(cancelled = true)
            }
        }
    } else Modifier
    val dragActive = enabled && (dragging || handlingDrag)
    // Material IconButton's ripple reads its enclosing configuration, independently of glyph tint.
    // Keep this accent override local, preserving an ancestor's disabled ripple or alpha policy.
    val rippleConfiguration = if (dragActive) null else LocalRippleConfiguration.current?.let {
        RippleConfiguration(color = colors.primary, rippleAlpha = it.rippleAlpha)
    }
    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
        NullWaveIconButton(
            icon = NullWaveIcons.Reorder,
            contentDescription = contentDescription,
            onClick = { if (enabled && !dragging && !handlingDrag) onClick() },
            modifier = modifier.then(dragModifier).semantics {
                if (dragDescription != null) stateDescription = dragDescription
            },
            enabled = enabled,
            tint = if (enabled && pressed || dragActive) colors.primary else colors.textSecondary,
            // Long press can still be pressed before movement cancels the original click. Remove
            // both its fill and ripple at takeover so the lifted row remains one continuous surface.
            pressedTint = if (dragActive) colors.primary else colors.onPrimary,
            pressedContainerColor = if (dragActive) Color.Transparent else colors.primary,
            interactionSource = interactions,
        )
    }
}
