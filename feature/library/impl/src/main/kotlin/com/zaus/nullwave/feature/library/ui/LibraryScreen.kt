package com.zaus.nullwave.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.navigation.SectionMenuButton
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
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTabItem
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTabRow
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveOctagonFrame
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.mvi.ui.asString
import com.zaus.nullwave.feature.library.impl.R
import com.zaus.nullwave.feature.library.artwork.LibraryArtworkLoader
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi
import com.zaus.nullwave.feature.library.presentation.LibraryBody
import com.zaus.nullwave.feature.library.presentation.LibraryIntent
import com.zaus.nullwave.feature.library.presentation.LibraryState
import com.zaus.nullwave.feature.library.presentation.LibraryTab
import com.zaus.nullwave.feature.library.presentation.RefreshState

/** State-only renderer; all platform launchers and repository work belong outside this function. */
@Composable
internal fun LibraryScreen(
    state: LibraryState,
    onIntent: (LibraryIntent) -> Unit,
    menuButton: SectionMenuButton,
    accessUi: LibraryAccessUi = LibraryAccessUi(),
    artworkLoader: LibraryArtworkLoader? = null,
    onSearchClick: () -> Unit = {},
    onAlbumClick: (AlbumId) -> Unit = {},
) {
    // Keep each position alive while another tab or a Loading/Failed branch is composed.
    // Feeding a temporary one-item loading grid into these states would clamp their positions.
    val songsScroll = rememberLazyGridState()
    val artistsScroll = rememberLazyGridState()
    val albumsScroll = rememberLazyGridState()
    val listState = when (state.selectedTab) {
        LibraryTab.Songs -> songsScroll
        LibraryTab.Artists -> artistsScroll
        LibraryTab.Albums -> albumsScroll
    }
    val spacing = NullWaveTheme.spacing
    val ready = state.selectedBody as? LibraryBody.Ready<*>
    var filtersVisible by rememberSaveable(state.selectedTab) { mutableStateOf(false) }
    val accessRequired = state.selectedBody is LibraryBody.AccessRequired
    // Sorting may temporarily show Loading; only lost access invalidates the open dialog.
    LaunchedEffect(accessRequired) {
        if (accessRequired) filtersVisible = false
    }
    NullWaveScaffold(
        modifier = Modifier.fillMaxSize().testTag("library_screen"),
        snackbarHost = {
            state.playbackError?.let { message ->
                LibraryPlaybackMessage(message) { onIntent(LibraryIntent.DismissPlaybackError) }
            }
        },
        topBar = {
            Column {
                NullWaveTopBar(stringResource(R.string.library_title), navigationIcon = {
                    val count = (state.body as? LibraryBody.Ready<*>)?.items?.size
                    menuButton(count?.let { pluralStringResource(R.plurals.library_track_count, it, it) })
                }, actions = {
                    NullWaveIconButton(NullWaveIcons.Search, stringResource(R.string.library_search), onSearchClick,
                        modifier = Modifier.testTag("library_search"))
                    // Same slot throughout refresh: no inserted list item or changing toolbar height.
                    if (ready?.refresh == RefreshState.Running) Box(
                        Modifier.size(NullWaveTheme.dimens.touchTarget), contentAlignment = Alignment.Center,
                    ) {
                        NullWaveLoadingIndicator(Modifier.testTag("library_refreshing"),
                            label = stringResource(R.string.library_refreshing), showLabel = false)
                    } else NullWaveIconButton(NullWaveIcons.Rescan, stringResource(R.string.library_refresh),
                        onClick = { onIntent(LibraryIntent.Refresh) },
                        enabled = ready != null)
                })
                NullWaveTabRow(
                    items = LibraryVisibleTabs.map { NullWaveTabItem(stringResource(it.tabLabel())) },
                    selectedIndex = LibraryVisibleTabs.indexOf(state.selectedTab),
                    onSelect = { onIntent(LibraryIntent.TabSelected(LibraryVisibleTabs[it])) },
                    modifier = Modifier.testTag("library_tabs"),
                )
            }
        },
    ) { padding ->
        if (ready != null) {
            LibraryReadyContent(state, onIntent, { filtersVisible = true }, artworkLoader, padding, listState, onAlbumClick)
            return@NullWaveScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("library_list"),
            contentPadding = padding,
        ) {
            when (val body = state.selectedBody) {
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
                is LibraryBody.Ready -> Unit
            }
        }
    }
    if (filtersVisible && !accessRequired) {
        LibraryFiltersDialog(state, onIntent, onDismiss = { filtersVisible = false })
    }
}

@Composable
internal fun LibraryPlaybackMessage(message: UiText, onDismiss: () -> Unit) {
    NullWaveInlineMessage(
        message = message.asString(),
        modifier = Modifier.padding(NullWaveTheme.spacing.md).testTag("library_playback_error")
            .semantics { liveRegion = LiveRegionMode.Polite },
        variant = NullWaveMessageVariant.Error,
        action = NullWaveMessageAction(stringResource(R.string.library_playback_dismiss), onDismiss),
    )
}

@Composable
internal fun LibraryAccessContent(action: AccessAction, accessUi: LibraryAccessUi) {
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
internal fun LibraryStateIcon(icon: Int) {
    NullWaveOctagonFrame(Modifier.size(72.dp), borderStyle = NullWaveBorderStyle.ClippedCorners) {
        NullWaveIcon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = NullWaveTheme.colors.primary)
    }
}
