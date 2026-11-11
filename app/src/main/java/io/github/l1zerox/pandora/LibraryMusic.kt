package io.github.l1zerox.pandora

import java.security.MessageDigest
import java.util.Locale

internal enum class LibrarySection(val label: String, val mime: String) { VIDEO("動画","video/*"), MUSIC("音楽","audio/*") }
internal fun sectionEntries(entries: List<LibraryEntry>, section: LibrarySection)=entries.filter { it.video == (section == LibrarySection.VIDEO) }
internal fun musicNumber(text: String?)=text?.substringBefore('/')?.trim()?.toIntOrNull()?.coerceIn(0,999) ?: 0
private val trackPrefix=Regex("^\\s*([0-9]{1,3})(\\s*[-._]\\s*|\\s+)(\\S.*)$")
internal fun musicTrackPrefix(title: String, track: Int = 0): Int {
    val match=trackPrefix.matchEntire(title) ?: return 0
    val digits=match.groupValues[1]; val number=digits.toInt()
    return if(number > 0 && (digits.length > 1 && digits.startsWith('0') || track == number || match.groupValues[2].any { it in "-._" })) number else 0
}
internal fun musicTitle(title: String, track: Int = 0): String =
    if(musicTrackPrefix(title,track) > 0) trackPrefix.matchEntire(title)!!.groupValues[3].trim() else title.trim()
internal fun LibraryEntry.musicMetadata(titleTag: String?, artistTag: String?, albumTag: String?, albumArtistTag: String?, trackTag: String?, discTag: String?): LibraryEntry {
    if(video) return this
    val number=musicNumber(trackTag).takeIf { it > 0 } ?: musicTrackPrefix(title)
    return copy(title=musicTitle(titleTag?.trim()?.takeIf { it.isNotEmpty() } ?: title,number),
        artist=artistTag.orEmpty().trim(),album=albumTag.orEmpty().trim(),albumArtist=albumArtistTag.orEmpty().trim(),track=number,disc=musicNumber(discTag))
}
internal data class MusicAlbum(val id: String, val name: String, val artist: String, val tracks: List<LibraryEntry>, val manual: Boolean = false)
internal const val MUSIC_ALBUM_ROOT="albums"
internal fun albumTracks(entries: List<LibraryEntry>)=entries.filterNot { it.video }.sortedWith(
    compareBy<LibraryEntry> { it.disc.takeIf { n -> n > 0 } ?: 1 }.thenBy { it.track.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.title }.thenBy { it.uri })
internal fun libraryAlbums(entries: List<LibraryEntry>, data: LibraryCollections): List<MusicAlbum> {
    val audio=entries.filterNot { it.video }.distinctBy { it.uri }
    val assigned=data.albums.flatMap { it.uris }.toSet()
    val manual=data.albums.map { list -> MusicAlbum(list.id,list.name,"",albumTracks(audio.filter { it.uri in list.uris }),true) }
    val tagged=audio.filter { it.uri !in assigned && it.album.isNotBlank() }.groupBy {
        it.album.trim().lowercase(Locale.ROOT) to it.albumArtist.ifBlank { it.artist }.trim().lowercase(Locale.ROOT)
    }.map { (key,tracks) ->
        val bytes=(key.first+"\u0000"+key.second).toByteArray(Charsets.UTF_8)
        val id="album-tag:"+MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        MusicAlbum(id,tracks.first().album,tracks.first().albumArtist.ifBlank { tracks.first().artist },albumTracks(tracks))
    }
    val untagged=audio.filter { it.uri !in assigned && it.album.isBlank() }
    return (manual+tagged).sortedBy { it.name.lowercase(Locale.ROOT) }+
        if(untagged.isEmpty()) emptyList() else listOf(MusicAlbum("album-unknown","アルバム情報なし","",albumTracks(untagged)))
}
internal fun isAlbumCollection(id: String)=id == MUSIC_ALBUM_ROOT || id.startsWith("album:") || id.startsWith("album-tag:") || id == "album-unknown"

/** Opening the current song from an album must replace an older queue without restarting the song. */
internal fun albumQueueResumePosition(currentUri: String?, requestedUri: String, collection: String, restoring: Boolean, position: Long): Long? =
    if(!restoring && currentUri == requestedUri && collection != MUSIC_ALBUM_ROOT && isAlbumCollection(collection)) position.coerceAtLeast(0) else null
