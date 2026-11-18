package com.zaus.nullwave.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.control.searchField.NullWaveSearchField
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveEmptyState
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveErrorState
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveInlineMessage
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveLoadingIndicator
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageAction
import com.zaus.nullwave.core.designsystem.components.feedback.NullWaveMessageVariant
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveOctagonFrame
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.mvi.ui.asString
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.search.impl.R
import com.zaus.nullwave.feature.search.presentation.SearchBody
import com.zaus.nullwave.feature.search.presentation.SearchIntent
import com.zaus.nullwave.feature.search.presentation.SearchRefresh
import com.zaus.nullwave.feature.search.presentation.SearchState

/** State renderer. Only focus, scroll and IME presentation are owned by Compose. */
@Composable
internal fun SearchScreen(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onBack: () -> Unit,
    accessUi: LibraryAccessUi = LibraryAccessUi(),
    artwork: LibraryArtworkRenderer? = null,
    onAlbumClick: (AlbumId) -> Unit = {},
    onArtistClick: (ArtistId) -> Unit = {},
) {
    val resultsScroll = rememberSearchScrollState(state.query?.text, state.localeTag)
    val inputFocus = rememberSearchInputFocus()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val spacing = NullWaveTheme.spacing
    NullWaveScaffold(
        modifier = Modifier.fillMaxSize().testTag("search_screen"),
        snackbarHost = {
            state.playbackError?.let { message ->
                NullWaveInlineMessage(
                    message = message.asString(),
                    modifier = Modifier.padding(spacing.md).testTag("search_playback_error")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    variant = NullWaveMessageVariant.Error,
                    action = NullWaveMessageAction(
                        label = stringResource(R.string.search_playback_dismiss),
                        onClick = { onIntent(SearchIntent.DismissPlaybackError) },
                    ),
                )
            }
        },
        topBar = {
            Column {
                NullWaveTopBar(stringResource(R.string.search_title), navigationIcon = {
                    NullWaveIconButton(NullWaveIcons.Back, stringResource(R.string.search_back), {
                        inputFocus.restoreEditing = false
                        keyboard?.hide()
                        onBack()
                    }, modifier = Modifier.testTag("search_back"))
                }, actions = {
                    if ((state.body as? SearchBody.Ready)?.refresh == SearchRefresh.Running) {
                        Box(Modifier.size(NullWaveTheme.dimens.touchTarget), contentAlignment = Alignment.Center) {
                            NullWaveLoadingIndicator(Modifier.testTag("search_refreshing"),
                                label = stringResource(R.string.search_loading), showLabel = false)
                        }
                    }
                })
                NullWaveSearchField(
                    query = state.rawQuery,
                    onQueryChange = { onIntent(SearchIntent.QueryChanged(it)) },
                    onClear = { onIntent(SearchIntent.ClearQuery) },
                    // Typing already schedules the latest query. IME must not restart debounce.
                    onSearch = {
                        inputFocus.restoreEditing = false
                        keyboard?.hide()
                    },
                    label = stringResource(R.string.search_input),
                    placeholder = stringResource(R.string.search_input),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = NullWaveTheme.dimens.gutter, vertical = spacing.sm)
                        .focusRequester(inputFocus.requester)
                        .onFocusChanged { inputFocus.focused = it.isFocused }.testTag("search_input"),
                )
            }
        },
        bottomBar = {
            val refresh = (state.body as? SearchBody.Ready)?.refresh
            if (refresh is SearchRefresh.Failed) {
                // Persistent feedback stays reachable at every scroll position, above the IME.
                NullWaveInlineMessage(refresh.message.asString(),
                    Modifier.padding(NullWaveTheme.dimens.gutter).testTag("search_refresh_error")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    variant = NullWaveMessageVariant.Error,
                    action = NullWaveMessageAction(stringResource(R.string.search_retry), { onIntent(SearchIntent.Retry) }))
            }
        },
    ) { padding ->
        val body = state.body
        if (body is SearchBody.Ready) {
            fun stopEditing() {
                focusManager.clearFocus()
                inputFocus.restoreEditing = false
                keyboard?.hide()
            }
            SearchResults(body, state.rawQuery, state.playingTrackId, onIntent, artwork, padding, resultsScroll,
                onAlbumClick = { stopEditing(); onAlbumClick(it) },
                onArtistClick = { stopEditing(); onArtistClick(it) })
        } else LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding), contentPadding = padding) {
            item("state") {
                when (body) {
                    SearchBody.Idle -> NullWaveEmptyState(
                        stringResource(R.string.search_idle_title), stringResource(R.string.search_idle_body),
                        modifier = Modifier.testTag("search_idle"), illustration = { SearchStateIcon(NullWaveIcons.Search) },
                    )
                    SearchBody.Loading, SearchBody.CheckingAccess -> Box(
                        Modifier.fillParentMaxSize().padding(spacing.lg), contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                            SearchStateIcon(NullWaveIcons.Search)
                            NullWaveLoadingIndicator(Modifier.testTag("search_loading"), label = stringResource(R.string.search_loading))
                        }
                    }
                    SearchBody.AccessRequired -> SearchAccessContent(accessUi)
                    is SearchBody.Failed -> NullWaveErrorState(
                        stringResource(R.string.search_error_title), body.message.asString(),
                        modifier = Modifier.testTag("search_error").semantics { liveRegion = LiveRegionMode.Polite },
                        primaryAction = { NullWaveButton(stringResource(R.string.search_retry), { onIntent(SearchIntent.Retry) }) },
                    )
                    is SearchBody.Ready -> Unit
                }
            }
        }
    }
}

@Composable
private fun SearchAccessContent(ui: LibraryAccessUi) {
    // A repository can deny access after the last OS grant. Offer a fresh check in that case.
    val action = (ui.access as? LibraryAccess.Required)?.action ?: AccessAction.CheckAgain
    Column(Modifier.testTag("search_access")) {
        if (ui.launchFailed) NullWaveInlineMessage(stringResource(R.string.search_access_launch_failed),
            Modifier.padding(NullWaveTheme.spacing.md).testTag("search_access_launch_failed"), variant = NullWaveMessageVariant.Error)
        NullWaveEmptyState(
            stringResource(R.string.search_access_title),
            stringResource(if (action == AccessAction.SettingsSuggested) R.string.search_settings_body else R.string.search_access_body),
            illustration = { SearchStateIcon(NullWaveIcons.Permission) },
            primaryAction = {
                NullWaveButton(stringResource(when (action) {
                    AccessAction.SettingsSuggested -> R.string.search_open_settings
                    AccessAction.CheckAgain -> R.string.search_check_access
                    else -> R.string.search_grant_access
                }), ui.onAccess, enabled = !ui.busy, modifier = Modifier.testTag("search_access_action"))
            },
            secondaryAction = if (action == AccessAction.SettingsSuggested) {{
                NullWaveButton(stringResource(R.string.search_ask_again), ui.onAskAgain,
                    variant = NullWaveButtonVariant.Neutral, enabled = !ui.busy)
            }} else null,
        )
    }
}

@Composable
internal fun SearchStateIcon(icon: Int) {
    NullWaveOctagonFrame(Modifier.size(72.dp), borderStyle = NullWaveBorderStyle.ClippedCorners) {
        NullWaveIcon(icon, null, Modifier.size(32.dp), tint = NullWaveTheme.colors.primary)
    }
}
