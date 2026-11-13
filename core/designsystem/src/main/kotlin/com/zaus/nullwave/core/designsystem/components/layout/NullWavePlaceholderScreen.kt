package com.zaus.nullwave.core.designsystem.components.layout

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Composable
fun NullWavePlaceholderScreen(title: String, @DrawableRes icon: Int, menuButton: @Composable () -> Unit) {
    NullWaveScaffold(topBar = {
        NullWaveTopBar(title, navigationIcon = menuButton)
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(NullWaveTheme.dimens.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md, Alignment.CenterVertically),
        ) {
            NullWaveIcon(icon, contentDescription = null, modifier = Modifier.size(48.dp),
                tint = NullWaveTheme.colors.primary)
            Text(stringResource(R.string.nw_feature_in_development),
                style = NullWaveTheme.typography.body, color = NullWaveTheme.colors.textSecondary,
                textAlign = TextAlign.Center)
        }
    }
}
