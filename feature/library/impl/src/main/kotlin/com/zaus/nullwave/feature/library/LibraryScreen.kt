package com.zaus.nullwave.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.library.impl.R

/** Temporary destination for verifying the navigation foundation. */
@Composable
internal fun LibraryScreen(onAboutClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NullWaveTheme.dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
    ) {
        Text(stringResource(R.string.library_title), style = NullWaveTheme.typography.h1)
        Text(stringResource(R.string.library_placeholder), color = NullWaveTheme.colors.textSecondary)
        Button(
            onClick = onAboutClick,
            modifier = Modifier.sizeIn(
                minWidth = NullWaveTheme.dimens.touchTarget,
                minHeight = NullWaveTheme.dimens.touchTarget,
            ),
            shape = NullWaveTheme.shapes.small,
        ) {
            Text(stringResource(R.string.library_action))
        }
    }
}
