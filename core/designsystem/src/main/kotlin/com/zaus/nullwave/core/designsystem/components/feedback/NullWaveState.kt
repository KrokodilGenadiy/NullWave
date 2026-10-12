package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveOctagonFrame
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Shared empty-content layout, with no built-in library/search/permission policy.
 * Illustration is decorative; convey its meaning in [title] and [body]. Actions retain their own
 * semantics. The caller owns scrolling, insets and any desired full-screen size via [modifier].
 */
@Composable
fun NullWaveEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
    primaryAction: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    StateContent(title, body, modifier, illustration, primaryAction, secondaryAction)
}

/**
 * Recoverable or terminal error, depending on caller-provided copy and actions.
 * No automatic retry, focus stealing or live-region announcement; the owning screen decides
 * when an asynchronous failure needs announcing. Error text remains neutral and readable.
 */
@Composable
fun NullWaveErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = {
        NullWaveOctagonFrame(
            modifier = Modifier.size(StateIllustrationSize),
            borderStyle = NullWaveBorderStyle.ClippedCorners,
        ) {
            NullWaveIcon(
                icon = NullWaveIcons.Warning,
                contentDescription = null,
                modifier = Modifier.size(StateIconSize),
                tint = NullWaveTheme.colors.danger,
            )
        }
    },
    primaryAction: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    StateContent(title, body, modifier, illustration, primaryAction, secondaryAction)
}

@Composable
private fun StateContent(
    title: String,
    body: String,
    modifier: Modifier,
    illustration: (@Composable () -> Unit)?,
    primaryAction: (@Composable () -> Unit)?,
    secondaryAction: (@Composable () -> Unit)?,
) {
    require(title.isNotBlank()) { "State title must not be blank" }
    require(body.isNotBlank()) { "State body must not be blank" }
    val spacing = NullWaveTheme.spacing
    val colors = NullWaveTheme.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = StateMaxWidth).fillMaxWidth().padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (illustration != null) {
                Box(Modifier.clearAndSetSemantics {}) { illustration() }
            }
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                color = colors.textPrimary,
                style = NullWaveTheme.typography.h1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                color = colors.textSecondary,
                style = NullWaveTheme.typography.body,
                textAlign = TextAlign.Center,
            )
            if (primaryAction != null || secondaryAction != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    primaryAction?.invoke()
                    secondaryAction?.invoke()
                }
            }
        }
    }
}

private val StateMaxWidth = 480.dp
private val StateIllustrationSize = 72.dp
private val StateIconSize = 32.dp
