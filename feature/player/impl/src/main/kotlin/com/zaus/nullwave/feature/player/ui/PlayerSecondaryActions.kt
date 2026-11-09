package com.zaus.nullwave.feature.player.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.player.impl.R

@Composable
internal fun PlayerSecondaryActions(onOpenQueue: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlayerAction(NullWaveIcons.Favourite, stringResource(R.string.player_action_fav))
        PlayerAction(NullWaveIcons.Queue, stringResource(R.string.player_queue), onOpenQueue)
        PlayerAction(NullWaveIcons.Lyrics, stringResource(R.string.player_action_lyrics))
        PlayerAction(NullWaveIcons.Info, stringResource(R.string.player_action_info))
    }
}

@Composable
private fun PlayerAction(@DrawableRes icon: Int, label: String, onClick: (() -> Unit)? = null) {
    val tint = NullWaveTheme.colors.textSecondary
    Column(
        Modifier.then(
            if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
            else Modifier.semantics(mergeDescendants = true) { role = Role.Button; disabled() },
        ).defaultMinSize(minWidth = 56.dp, minHeight = NullWaveTheme.dimens.touchTarget),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        val foreground = if (onClick == null) tint.copy(alpha = 0.65f) else tint
        NullWaveIcon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = foreground)
        Text(label, style = NullWaveTheme.typography.micro, color = foreground)
    }
}
