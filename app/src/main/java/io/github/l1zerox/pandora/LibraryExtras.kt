package io.github.l1zerox.pandora

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

internal object LibraryExtras {
    val state=MutableStateFlow(LibraryCollections())
    private lateinit var prefs: SharedPreferences
    private lateinit var bookmarks: SharedPreferences
    fun init(context: Context) {
        prefs=context.getSharedPreferences("library_collections",0)
        bookmarks=context.getSharedPreferences("playback_bookmarks",0)
        state.value=LibraryCollections.parse(prefs.getString("data","{}") ?: "{}")
    }
    @Synchronized private fun change(block: (LibraryCollections)->LibraryCollections) {
        val next=block(state.value)
        check(prefs.edit().putString("data",next.json()).commit()) { "ライブラリー設定を保存できませんでした。" }
        state.value=next
    }
    fun favorite(uri: String)=change { it.copy(favorites=if(uri in it.favorites) it.favorites-uri else it.favorites+uri) }
    fun create(name: String, uris: List<String> = emptyList()): String {
        val cleaned=name.trim().take(60); require(cleaned.isNotEmpty()) { "名前を入力してください。" }
        val id=UUID.randomUUID().toString()
        change { it.copy(playlists=it.playlists+UserPlaylist(id,cleaned,uris.distinct())) }; return id
    }
    fun rename(id: String, name: String) {
        val cleaned=name.trim().take(60); require(cleaned.isNotEmpty())
        change { it.copy(playlists=it.playlists.map { list -> if(list.id == id) list.copy(name=cleaned) else list }) }
    }
    fun deletePlaylist(id: String)=change { it.copy(playlists=it.playlists.filterNot { list -> list.id == id }) }
    fun add(id: String, uris: List<String>)=change { it.addTo(id,uris) }
    fun removeFrom(id: String, uris: Set<String>)=change { it.copy(playlists=it.playlists.map { list ->
        if(list.id == id) list.copy(uris=list.uris.filterNot { uri -> uri in uris }) else list
    }) }
    fun forget(uri: String) {
        change { it.without(uri) }; bookmarks.edit().remove(uri).apply()
    }
    fun position(uri: String, duration: Long)=resumablePosition(bookmarks.getLong(uri,0),duration)
    fun rememberPosition(uri: String, position: Long, duration: Long) {
        if(uri.isBlank() || duration <= 0) return
        val value=resumablePosition(position,duration)
        if(bookmarks.getLong(uri,0) != value) bookmarks.edit().putLong(uri,value).apply()
    }
}
