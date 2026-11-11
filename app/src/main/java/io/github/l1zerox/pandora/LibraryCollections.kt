package io.github.l1zerox.pandora

import org.json.JSONArray
import org.json.JSONObject

internal data class UserPlaylist(val id: String, val name: String, val uris: List<String> = emptyList())
internal data class LibraryCollections(val favorites: Set<String> = emptySet(), val playlists: List<UserPlaylist> = emptyList(), val folders: List<UserPlaylist> = emptyList(), val albums: List<UserPlaylist> = emptyList()) {
    fun without(uri: String)=copy(favorites=favorites-uri,playlists=playlists.map { it.copy(uris=it.uris-uri) },folders=folders.map { it.copy(uris=it.uris-uri) },albums=albums.map { it.copy(uris=it.uris-uri) })
    fun withAlbum(album: UserPlaylist): LibraryCollections {
        require(album.name.isNotBlank())
        val members=album.uris.toSet()
        return copy(albums=albums.filterNot { it.id == album.id }.map { it.copy(uris=it.uris.filterNot { uri -> uri in members }) }+album.copy(uris=album.uris.distinct()))
    }
    fun addTo(id: String, uris: List<String>)=copy(playlists=playlists.map {
        if(it.id == id) it.copy(uris=(it.uris+uris).distinct()) else it
    })
    fun moveToFolder(id: String, uris: List<String>): LibraryCollections {
        require(id.isEmpty() || folders.any { it.id == id }) { "フォルダーが見つかりません。" }
        val moving=uris.toSet()
        return copy(folders=folders.map { folder -> folder.copy(uris=
            (folder.uris.filterNot { it in moving } + if(folder.id == id) uris else emptyList()).distinct()) })
    }
    fun json(): String=JSONObject().put("favorites",JSONArray(favorites.toList())).put("playlists",JSONArray().also { array ->
        playlists.forEach { array.put(JSONObject().put("id",it.id).put("name",it.name).put("uris",JSONArray(it.uris))) }
    }).put("folders",JSONArray().also { array ->
        folders.forEach { array.put(JSONObject().put("id",it.id).put("name",it.name).put("uris",JSONArray(it.uris))) }
    }).put("albums",JSONArray().also { array ->
        albums.forEach { array.put(JSONObject().put("id",it.id).put("name",it.name).put("uris",JSONArray(it.uris))) }
    }).toString()
    companion object {
        fun parse(text: String): LibraryCollections=runCatching {
            val json=JSONObject(text)
            fun strings(array: JSONArray?)=if(array == null) emptyList() else (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.distinct()
            fun lists(key: String): List<UserPlaylist> {
                val lists=json.optJSONArray(key) ?: JSONArray()
                return (0 until lists.length()).mapNotNull { index ->
                val item=lists.optJSONObject(index) ?: return@mapNotNull null
                val id=item.optString("id"); val name=item.optString("name").trim().take(60)
                if(id.isBlank() || name.isBlank()) null else UserPlaylist(id,name,strings(item.optJSONArray("uris")))
                }.distinctBy { it.id }
            }
            val assigned=mutableSetOf<String>()
            val folders=lists("folders").map { it.copy(uris=it.uris.filter { uri -> assigned.add(uri) }) }
            val albumAssigned=mutableSetOf<String>()
            LibraryCollections(strings(json.optJSONArray("favorites")).toSet(),lists("playlists"),folders,
                lists("albums").map { it.copy(uris=it.uris.filter { uri -> albumAssigned.add(uri) }) })
        }.getOrDefault(LibraryCollections())
    }
}

internal enum class LibrarySort(val label: String) { NEWEST("追加が新しい順"), OLDEST("追加が古い順"), TITLE("タイトル順"), LONGEST("長い順"), SHORTEST("短い順") }
internal const val LIBRARY_FOLDER_ROOT="folder-root"
internal fun isFolderCollection(collection: String)=collection == LIBRARY_FOLDER_ROOT || collection == "unfiled" || collection.startsWith("folder:")
internal fun folderParent(collection: String)=if(collection == LIBRARY_FOLDER_ROOT) "" else LIBRARY_FOLDER_ROOT
internal data class LibraryFolderRow(val id: String, val name: String, val count: Int)
internal fun libraryFolderRows(entries: List<LibraryEntry>, data: LibraryCollections, dubbedOnly: Boolean): List<LibraryFolderRow> {
    val available=entries.filter { !dubbedOnly || it.autoDubbed && it.video }.map { it.uri }.toSet()
    return data.folders.map { LibraryFolderRow(it.id,it.name,it.uris.toSet().count { uri -> uri in available }) }
}
internal fun organizedLibrary(entries: List<LibraryEntry>, data: LibraryCollections, collection: String, sort: LibrarySort,
    dubbedOnly: Boolean): List<LibraryEntry> {
    if(isAlbumCollection(collection)) return if(collection == MUSIC_ALBUM_ROOT) emptyList()
        else libraryAlbums(entries,data).firstOrNull { it.id == collection }?.tracks.orEmpty()
    val playlist=data.playlists.firstOrNull { it.id == collection }
    val folder=data.folders.firstOrNull { it.id == collection }
    val members=when { collection == "unfiled" || collection == LIBRARY_FOLDER_ROOT -> entries.map { it.uri }.toSet()-data.folders.flatMap { it.uris }.toSet()
        folder != null -> folder.uris.toSet()
        collection == "favorites" -> data.favorites; playlist != null -> playlist.uris.toSet(); else -> null }
    val filtered=entries.filter { (members == null || it.uri in members) && (!dubbedOnly || it.autoDubbed && it.video) }
    if(playlist != null && sort == LibrarySort.NEWEST) return filtered.sortedBy { playlist.uris.indexOf(it.uri) }
    val order=when(sort) {
        LibrarySort.NEWEST -> compareByDescending<LibraryEntry> { it.added }
        LibrarySort.OLDEST -> compareBy<LibraryEntry> { it.added }
        LibrarySort.TITLE -> compareBy<LibraryEntry> { it.title.lowercase() }
        LibrarySort.LONGEST -> compareByDescending<LibraryEntry> { it.durationMs }
        LibrarySort.SHORTEST -> compareBy<LibraryEntry> { if(it.durationMs > 0) it.durationMs else Long.MAX_VALUE }
    }
    return filtered.sortedWith(order.thenBy { it.title }.thenBy { it.uri })
}

internal fun resumablePosition(position: Long, duration: Long): Long =
    if(duration > 10_000 && position >= 5_000 && position < duration-3_000) position else 0
