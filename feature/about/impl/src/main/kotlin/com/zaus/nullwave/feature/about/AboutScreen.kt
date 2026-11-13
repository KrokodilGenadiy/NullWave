package com.zaus.nullwave.feature.about

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.about.api.R as AboutR
import com.zaus.nullwave.feature.about.impl.R

@Composable
internal fun AboutScreen(menuButton: @Composable () -> Unit) {
    NullWaveScaffold(topBar = {
        NullWaveTopBar(stringResource(AboutR.string.about_title), navigationIcon = menuButton)
    }) { padding ->
        Text(stringResource(R.string.about_placeholder),
            Modifier.padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(NullWaveTheme.dimens.gutter),
            color = NullWaveTheme.colors.textSecondary)
    }
}
