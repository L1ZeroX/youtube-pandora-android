package io.github.l1zerox.pandora

import java.security.MessageDigest
import java.util.Locale
import java.text.Normalizer

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
internal data class MusicAlbum(val id: String, val name: String, val artist: String, val tracks: List<LibraryEntry>, val manual: Boolean = false,
    val legacyIds: Set<String> = emptySet()) {
    fun matches(key: String)=id == key || key in legacyIds
}
/** Android's ICU regex engine rejects Java's (?U) flag; normalize whitespace directly. */
internal fun albumNameKey(name: String): String = buildString {
    var space=false
    for(char in Normalizer.normalize(name,Normalizer.Form.NFKC)) {
        if(Character.getType(char) == Character.FORMAT.toInt()) continue
        if(char.isWhitespace() || char == '\u0085') {
            space=isNotEmpty()
        } else {
            if(space) append(' ')
            append(if(char == '’') '\'' else char)
            space=false
        }
    }
}.lowercase(Locale.ROOT)
private fun albumId(key: String)="album-tag:"+MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
internal const val MUSIC_ALBUM_ROOT="albums"
internal fun albumTracks(entries: List<LibraryEntry>)=entries.filterNot { it.video }.sortedWith(
    compareBy<LibraryEntry> { it.disc.takeIf { n -> n > 0 } ?: 1 }.thenBy { it.track.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.title }.thenBy { it.uri })
internal fun libraryAlbums(entries: List<LibraryEntry>, data: LibraryCollections): List<MusicAlbum> {
    val audio=entries.filterNot { it.video }.distinctBy { it.uri }
    val assigned=data.albums.flatMap { it.uris }.toSet()
    val manual=data.albums.map { list -> MusicAlbum(list.id,list.name,"",albumTracks(audio.filter { it.uri in list.uris }),true) }
    val tagged=audio.filter { it.uri !in assigned && albumNameKey(it.album).isNotBlank() }.groupBy {
        albumNameKey(it.album)
    }.map { (key,tracks) ->
        val artists=tracks.map { it.albumArtist.ifBlank { it.artist } }.filter { it.isNotBlank() }.distinctBy(::albumNameKey)
        val legacy=tracks.map { albumId(it.album.trim().lowercase(Locale.ROOT)+"\u0000"+it.albumArtist.ifBlank { it.artist }.trim().lowercase(Locale.ROOT)) }.toSet()
        MusicAlbum(albumId(key),tracks.first().album.trim(),if(artists.size == 1) artists.single() else if(artists.isEmpty()) "" else "複数のアーティスト",albumTracks(tracks),legacyIds=legacy)
    }
    val untagged=audio.filter { it.uri !in assigned && albumNameKey(it.album).isBlank() }
    return (manual+tagged).sortedBy { it.name.lowercase(Locale.ROOT) }+
        if(untagged.isEmpty()) emptyList() else listOf(MusicAlbum("album-unknown","アルバム情報なし","",albumTracks(untagged)))
}
internal fun isAlbumCollection(id: String)=id == MUSIC_ALBUM_ROOT || id.startsWith("album:") || id.startsWith("album-tag:") || id == "album-unknown"

/** Opening the current song from an album must replace an older queue without restarting the song. */
internal fun albumQueueResumePosition(currentUri: String?, requestedUri: String, collection: String, restoring: Boolean, position: Long): Long? =
    if(!restoring && currentUri == requestedUri && collection != MUSIC_ALBUM_ROOT && isAlbumCollection(collection)) position.coerceAtLeast(0) else null
