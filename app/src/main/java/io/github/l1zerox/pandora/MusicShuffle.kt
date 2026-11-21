package io.github.l1zerox.pandora

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

internal const val MUSIC_SHUFFLE_COMMAND="io.github.l1zerox.pandora.SHUFFLE_SCOPE"
internal const val MUSIC_SHUFFLE_SCOPE="pandora.shuffleScope"
internal enum class MusicShuffleScope(val label: String) {
    ALBUM("アルバム内"), ALL("アルバム外も含める"), OFF("シャッフルをオフ")
}

internal fun musicShuffleEntries(entries: List<LibraryEntry>, data: LibraryCollections, uri: String,
    scope: MusicShuffleScope): List<LibraryEntry> {
    val audio=entries.filterNot { it.video }.distinctBy { it.uri }
    require(audio.any { it.uri == uri }) { "再生中の曲がライブラリーにありません。" }
    return when(scope) {
        MusicShuffleScope.ALBUM -> libraryAlbums(audio,data).firstOrNull { album -> album.tracks.any { it.uri == uri } }?.tracks
            ?: error("再生中のアルバムが見つかりません。")
        MusicShuffleScope.ALL -> audio.sortedBy { libraryTitleKey(it.title) }
        MusicShuffleScope.OFF -> error("オフでは再生リストを変更しません。")
    }
}

internal fun MediaItem.withShuffleScope(scope: MusicShuffleScope): MediaItem = buildUpon().setMediaMetadata(
    mediaMetadata.buildUpon().setExtras(Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply {
        putString(MUSIC_SHUFFLE_SCOPE,scope.name)
    }).build()).build()

/** Called on the session's application thread. Never remove, seek or restart the current item. */
internal fun applyMusicShuffle(player: Player, expectedUri: String, items: List<MediaItem>, scope: MusicShuffleScope) {
    val current=player.currentMediaItem ?: error("再生中の曲がありません。")
    check(current.mediaId == expectedUri) { "曲が切り替わりました。もう一度選択してください。" }
    check(current.mediaMetadata.extras?.getBoolean("video") != true && current.localConfiguration?.mimeType?.startsWith("video/") != true)
    check(player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))
    if(scope == MusicShuffleScope.OFF) { player.shuffleModeEnabled=false; return }
    check(player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
    val wanted=items.indexOfFirst { it.mediaId == expectedUri }
    require(wanted >= 0 && items.map { it.mediaId }.distinct().size == items.size)
    val before=items.take(wanted).map { it.withShuffleScope(scope) }
    val after=items.drop(wanted+1).map { it.withShuffleScope(scope) }
    val retained=current.withShuffleScope(scope)
    val index=player.currentMediaItemIndex
    if(index+1 < player.mediaItemCount) player.removeMediaItems(index+1,player.mediaItemCount)
    if(index > 0) player.removeMediaItems(0,index)
    player.replaceMediaItem(0,retained)
    if(before.isNotEmpty()) player.addMediaItems(0,before)
    if(after.isNotEmpty()) player.addMediaItems(after)
    player.shuffleModeEnabled=true
}

@Composable
internal fun MusicShuffleDialog(onChoose: (MusicShuffleScope)->Unit,onDismiss: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("シャッフル範囲")},text={Column {
        Text("再生中の曲と再生位置を保って切り替えます。")
        MusicShuffleScope.entries.forEach { scope ->
            TextButton(onClick={onChoose(scope)},modifier=Modifier.fillMaxWidth()) { Text(when(scope) {
                MusicShuffleScope.ALBUM -> "アルバム内でシャッフル"
                MusicShuffleScope.ALL -> "アルバム外も含めてシャッフル"
                MusicShuffleScope.OFF -> scope.label
            }) }
        }
        Text("アルバム外を含めると、ライブラリーの音楽全体が対象です。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick=onDismiss) { Text("キャンセル") }})
}
