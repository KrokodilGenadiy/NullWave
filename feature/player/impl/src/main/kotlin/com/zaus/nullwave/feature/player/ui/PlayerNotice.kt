package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveInlineMessage
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageAction
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.asString
import com.zaus.nullwave.core.playback.api.command.PlaybackCapability
import com.zaus.nullwave.core.playback.api.model.PlaybackConnection
import com.zaus.nullwave.core.playback.api.model.PlaybackConnectionError
import com.zaus.nullwave.core.playback.api.model.PlaybackProblemCode
import com.zaus.nullwave.feature.player.impl.R
import com.zaus.nullwave.feature.player.presentation.PlayerIntent
import com.zaus.nullwave.feature.player.presentation.PlayerState

/** One persistent notice: local command failure, unavailable connection, then engine problem. */
@Composable
internal fun PlayerNotice(
    state: PlayerState,
    canSubmit: Boolean,
    onIntent: (PlayerIntent) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val command = state.commandNotice
    val connection = state.connectionMessage
    val problem = state.visibleProblem
    val problemKey = state.problemKey
    val message = command?.message ?: connection ?: state.noticeMessage ?: return
    val target = state.liveSnapshot?.queue?.current
    val action = when {
        command != null -> null
        connection != null -> NullWaveMessageAction(
            stringResource(R.string.player_reconnect),
            { onIntent(PlayerIntent.Reconnect) },
            enabled = canSubmit,
        )
        problem?.code == PlaybackProblemCode.AccessDenied -> NullWaveMessageAction(
            stringResource(R.string.player_open_library), onOpenLibrary,
        )
        problem != null && target != null && state.supportsItemCommand(PlaybackCapability.Retry) &&
            (problem.target == null || problem.target == target) -> NullWaveMessageAction(
                stringResource(R.string.player_retry),
                { onIntent(PlayerIntent.RetryProblem(target, problem.id)) },
                enabled = canSubmit,
            )
        else -> null
    }
    val dismiss: (() -> Unit)? = when {
        command != null -> { { onIntent(PlayerIntent.DismissCommandNotice(command.id)) } }
        connection == null && problemKey != null -> { { onIntent(PlayerIntent.DismissProblem(problemKey)) } }
        else -> null
    }
    NullWaveInlineMessage(
        message = message.asString(),
        modifier = Modifier.padding(NullWaveTheme.spacing.sm).testTag("player_notice")
            .semantics { liveRegion = LiveRegionMode.Polite },
        variant = NullWaveMessageVariant.Error,
        action = action,
        onDismiss = dismiss,
        dismissDescription = stringResource(R.string.player_dismiss_notice),
    )
}

@Preview(name = "Player connection notice", showBackground = true)
@Preview(name = "Player connection notice RU", locale = "ru", fontScale = 1.5f, showBackground = true)
@Composable
private fun PlayerNoticePreview() {
    NullWaveTheme {
        PlayerNotice(
            PlayerState(connection = PlaybackConnection.Unavailable(PlaybackConnectionError.StartNotAllowed)),
            canSubmit = true,
            onIntent = {},
            onOpenLibrary = {},
        )
    }
}
