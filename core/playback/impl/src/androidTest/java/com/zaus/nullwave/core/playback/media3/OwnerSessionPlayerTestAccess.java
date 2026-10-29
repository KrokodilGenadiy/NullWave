package com.zaus.nullwave.core.playback.media3;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.Player;
import com.google.common.util.concurrent.ListenableFuture;

/** Java package access inspects a protected handler future without changing the production API. */
@UnstableApi
public final class OwnerSessionPlayerTestAccess {
    private OwnerSessionPlayerTestAccess() {}

    public static ListenableFuture<?> setPlayWhenReady(OwnerSessionPlayer player, boolean value) {
        return player.handleSetPlayWhenReady(value);
    }

    public static ListenableFuture<?> seek(OwnerSessionPlayer player, long positionMs) {
        return player.handleSeek(0, positionMs, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    }
}
