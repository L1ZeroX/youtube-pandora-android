package io.github.l1zerox.pandora

import org.json.JSONArray
import org.json.JSONObject

internal data class UserPlaylist(val id: String, val name: String, val uris: List<String> = emptyList())
internal data class LibraryCollections(val favorites: Set<String> = emptySet(), val playlists: List<UserPlaylist> = emptyList()) {
    fun without(uri: String)=copy(favorites=favorites-uri,playlists=playlists.map { it.copy(uris=it.uris-uri) })
    fun addTo(id: String, uris: List<String>)=copy(playlists=playlists.map {
        if(it.id == id) it.copy(uris=(it.uris+uris).distinct()) else it
    })
    fun json(): String=JSONObject().put("favorites",JSONArray(favorites.toList())).put("playlists",JSONArray().also { array ->
        playlists.forEach { array.put(JSONObject().put("id",it.id).put("name",it.name).put("uris",JSONArray(it.uris))) }
    }).toString()
    companion object {
        fun parse(text: String): LibraryCollections=runCatching {
            val json=JSONObject(text)
            fun strings(array: JSONArray?)=if(array == null) emptyList() else (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.distinct()
            val lists=json.optJSONArray("playlists") ?: JSONArray()
            LibraryCollections(strings(json.optJSONArray("favorites")).toSet(),(0 until lists.length()).mapNotNull { index ->
                val item=lists.optJSONObject(index) ?: return@mapNotNull null
                val id=item.optString("id"); val name=item.optString("name").trim().take(60)
                if(id.isBlank() || name.isBlank()) null else UserPlaylist(id,name,strings(item.optJSONArray("uris")))
            }.distinctBy { it.id })
        }.getOrDefault(LibraryCollections())
    }
}

internal enum class LibrarySort(val label: String) { NEWEST("追加が新しい順"), OLDEST("追加が古い順"), TITLE("タイトル順"), LONGEST("長い順"), SHORTEST("短い順") }
internal fun organizedLibrary(entries: List<LibraryEntry>, data: LibraryCollections, collection: String, sort: LibrarySort,
    dubbedOnly: Boolean): List<LibraryEntry> {
    val playlist=data.playlists.firstOrNull { it.id == collection }
    val members=when { collection == "favorites" -> data.favorites; playlist != null -> playlist.uris.toSet(); else -> null }
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
