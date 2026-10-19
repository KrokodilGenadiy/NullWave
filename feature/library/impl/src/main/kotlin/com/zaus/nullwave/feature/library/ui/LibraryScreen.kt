package com.zaus.nullwave.feature.library.ui

import android.text.format.Formatter

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.zaus.nullwave.core.designsystem.components.content.NullWaveTrackRow
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveEmptyState
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveErrorState
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveInlineMessage
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveLoadingIndicator
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageAction
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageVariant
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveDivider
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveOctagonFrame
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.asString
import com.zaus.nullwave.core.mvi.ui.collectMviStateWithLifecycle
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.platform.AndroidAudioPermission
import com.zaus.nullwave.feature.library.platform.AudioPermission
import com.zaus.nullwave.feature.library.presentation.AccessAction
import com.zaus.nullwave.feature.library.presentation.LibraryBody
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryState
import com.zaus.nullwave.feature.library.presentation.LibraryViewModel
import com.zaus.nullwave.feature.library.presentation.RefreshState
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
internal fun LibraryRoot(
    onAboutClick: () -> Unit,
    artworkLoader: LibraryArtworkLoader?,
    viewModel: LibraryViewModel = metroViewModel(),
    permission: AudioPermission = rememberAudioPermission(),
) {
    val state by viewModel.collectMviStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(viewModel, localeTag) { viewModel.onIntent(LibraryIntent.LocaleChanged(localeTag)) }
    LibraryPermissionHost(permission, { viewModel.onIntent(LibraryIntent.AccessChecked(it)) }) { accessUi ->
        LibraryScreen(state, viewModel::onIntent, onAboutClick, accessUi, artworkLoader)
    }
}

@Composable
private fun rememberAudioPermission(): AudioPermission {
    val activity = checkNotNull(LocalActivity.current) { "LibraryRoot requires an Activity host" }
    return remember(activity) { AndroidAudioPermission(activity) }
}

/** State-only renderer; all platform launchers and repository work belong outside this function. */
@Composable
internal fun LibraryScreen(
    state: LibraryState,
    onIntent: (LibraryIntent) -> Unit,
    onAboutClick: () -> Unit,
    accessUi: LibraryAccessUi = LibraryAccessUi(),
    artworkLoader: LibraryArtworkLoader? = null,
) {
    val spacing = NullWaveTheme.spacing
    val ready = state.body as? LibraryBody.Ready
    var filtersVisible by rememberSaveable { mutableStateOf(false) }
    NullWaveScaffold(
        modifier = Modifier.fillMaxSize().testTag("library_screen"),
        topBar = {
            Column {
                NullWaveTopBar(stringResource(R.string.library_title), actions = {
                    // Same slot throughout refresh: no inserted list item or changing toolbar height.
                    if (ready?.refresh == RefreshState.Running) Box(
                        Modifier.size(NullWaveTheme.dimens.touchTarget), contentAlignment = Alignment.Center,
                    ) {
                        NullWaveLoadingIndicator(Modifier.testTag("library_refreshing"),
                            label = stringResource(R.string.library_refreshing), showLabel = false)
                    } else NullWaveIconButton(NullWaveIcons.Rescan, stringResource(R.string.library_refresh),
                        onClick = { onIntent(LibraryIntent.Refresh) },
                        enabled = ready != null)
                    NullWaveIconButton(NullWaveIcons.Info, stringResource(R.string.library_action), onAboutClick)
                })
                NullWaveDivider()
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("library_list"),
            contentPadding = padding,
        ) {
            when (val body = state.body) {
                LibraryBody.Loading -> item("loading") {
                    Box(Modifier.fillParentMaxSize().padding(spacing.lg), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                            LibraryStateIcon(NullWaveIcons.Rescan)
                            NullWaveLoadingIndicator(Modifier.testTag("library_loading"),
                                label = stringResource(R.string.library_loading))
                        }
                    }
                }
                is LibraryBody.AccessRequired -> item("access") {
                    LibraryAccessContent(body.action, accessUi)
                }
                is LibraryBody.Failed -> item("error") {
                    NullWaveErrorState(
                        stringResource(R.string.library_error_title), body.message.asString(),
                        modifier = Modifier.testTag("library_error").semantics { liveRegion = LiveRegionMode.Polite },
                        primaryAction = {
                            NullWaveButton(stringResource(R.string.library_retry), { onIntent(LibraryIntent.Retry) })
                        },
                    )
                }
                is LibraryBody.Ready -> {
                    item("summary") {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(NullWaveTheme.dimens.gutter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            val bytes = remember(body.tracks) { libraryTotalBytes(body.tracks) }
                            val size = bytes?.let { Formatter.formatShortFileSize(LocalContext.current, it) }
                                ?: stringResource(R.string.library_size_unknown)
                            val count = pluralStringResource(R.plurals.library_track_count, body.tracks.size, body.tracks.size)
                            Text(stringResource(R.string.library_summary, count, size),
                                Modifier.align(Alignment.CenterVertically).testTag("library_summary"),
                                style = NullWaveTheme.typography.micro, color = NullWaveTheme.colors.textSecondary)
                            LibraryFiltersButton(state.query, onClick = { filtersVisible = true })
                        }
                    }
                    when (val refresh = body.refresh) {
                        RefreshState.Idle, RefreshState.Running -> Unit
                        is RefreshState.Failed -> item("refresh_error") {
                            NullWaveInlineMessage(refresh.message.asString(),
                                Modifier.padding(spacing.md).testTag("library_refresh_error")
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                variant = NullWaveMessageVariant.Error,
                                action = NullWaveMessageAction(stringResource(R.string.library_retry), { onIntent(LibraryIntent.Retry) }))
                        }
                    }
                    if (body.tracks.isEmpty()) item("empty") {
                        NullWaveEmptyState(stringResource(R.string.library_empty_title), stringResource(R.string.library_empty_body),
                            modifier = Modifier.testTag("library_empty"),
                            illustration = { LibraryStateIcon(NullWaveIcons.Songs) })
                    }
                    items(body.tracks, key = { "track:${it.id.value}" }, contentType = { "track" }) { track ->
                        NullWaveTrackRow(
                            title = track.title,
                            subtitle = librarySubtitle(track.artist, track.album, stringResource(R.string.library_unknown_artist)),
                            durationLabel = libraryDuration(track.durationMs),
                            modifier = Modifier.testTag("library_track:${track.id.value}"),
                            artwork = { LibraryArtwork(track, artworkLoader) },
                        )
                        NullWaveDivider(Modifier.padding(horizontal = NullWaveTheme.dimens.gutter))
                    }
                }
            }
        }
    }
    if (filtersVisible) LibraryFiltersDialog(state.query, onIntent, onDismiss = { filtersVisible = false })
}

@Composable
private fun LibraryAccessContent(action: AccessAction, accessUi: LibraryAccessUi) {
    Column(Modifier.testTag("library_access")) {
        if (accessUi.launchFailed) NullWaveInlineMessage(stringResource(R.string.library_access_launch_failed),
            Modifier.padding(NullWaveTheme.spacing.md).semantics { liveRegion = LiveRegionMode.Polite },
            variant = NullWaveMessageVariant.Error)
        NullWaveEmptyState(
            stringResource(R.string.library_access_title),
            stringResource(if (action == AccessAction.SettingsSuggested) R.string.library_access_settings_body else R.string.library_access_body),
            illustration = { LibraryStateIcon(NullWaveIcons.Permission) },
            primaryAction = {
                NullWaveButton(
                    label = stringResource(when (action) {
                        AccessAction.SettingsSuggested -> R.string.library_open_settings
                        AccessAction.CheckAgain -> R.string.library_check_access
                        else -> R.string.library_grant_access
                    }),
                    onClick = accessUi.onAccess,
                    enabled = !accessUi.busy,
                    modifier = Modifier.testTag("library_access_action"),
                )
            },
            secondaryAction = if (action == AccessAction.SettingsSuggested) {{
                NullWaveButton(stringResource(R.string.library_ask_again), accessUi.onAskAgain,
                    variant = NullWaveButtonVariant.Neutral, enabled = !accessUi.busy)
            }} else null,
        )
    }
}

@Composable
private fun LibraryStateIcon(icon: Int) {
    NullWaveOctagonFrame(Modifier.size(72.dp), borderStyle = NullWaveBorderStyle.ClippedCorners) {
        NullWaveIcon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = NullWaveTheme.colors.primary)
    }
}
