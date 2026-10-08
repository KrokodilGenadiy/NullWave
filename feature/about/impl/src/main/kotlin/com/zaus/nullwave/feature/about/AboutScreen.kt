package com.zaus.nullwave.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.feature.about.impl.R

/** Temporary destination for verifying the navigation foundation. */
@Composable
internal fun AboutScreen(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.about_placeholder))
        Button(onClick = onBackClick) {
            Text(stringResource(R.string.about_action))
        }
    }
}
