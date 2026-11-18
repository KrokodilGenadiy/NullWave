package com.zaus.nullwave.feature.library.presentation.artistdetails

import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.feature.library.api.access.LibraryAccess

internal sealed interface ArtistDetailsIntent : UiIntent {
    data class AccessChecked(val access: LibraryAccess) : ArtistDetailsIntent
    data class LocaleChanged(val localeTag: String) : ArtistDetailsIntent
    data object Retry : ArtistDetailsIntent
}
