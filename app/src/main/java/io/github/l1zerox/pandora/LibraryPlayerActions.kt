package io.github.l1zerox.pandora

import androidx.media3.common.Player

/** Ignore taps from a panel for the previous item, and preserve pause/play state. */
internal fun seekLibraryAdjacent(player: Player?, expectedUri: String, next: Boolean) {
    val current=player ?: return
    val command=if(next) Player.COMMAND_SEEK_TO_NEXT else Player.COMMAND_SEEK_TO_PREVIOUS
    if(current.currentMediaItem?.mediaId != expectedUri || !current.isCommandAvailable(command)) return
    if(next) current.seekToNext() else current.seekToPrevious()
}
