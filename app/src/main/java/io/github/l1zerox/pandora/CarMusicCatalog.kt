package io.github.l1zerox.pandora

import java.util.Base64

internal const val CAR_ROOT="car:root"
internal const val CAR_SONGS="car:songs"
internal const val CAR_ALBUMS="car:albums"
internal const val CAR_FAVORITES="car:favorites"
internal const val CAR_PLAYLISTS="car:playlists"
internal const val CAR_PAGE_SIZE=80
internal data class CarMusicNode(val id: String, val title: String, val subtitle: String = "",
    val entry: LibraryEntry? = null, val group: String = "", val album: String? = null)
internal data class CarMusicQueue(val entries: List<LibraryEntry>, val index: Int)
private fun carKey(text: String)=Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))

/** Only known library audio is resolvable. A browser-supplied URL is never opened for playback. */
internal class CarMusicCatalog(entries: List<LibraryEntry>, data: LibraryCollections) {
    val songs=entries.filter { it.mime.startsWith("audio/") && it.uri.startsWith("content://") }.distinctBy { it.uri }
        .sortedWith(compareBy<LibraryEntry> { libraryTitleKey(it.title) }.thenBy { it.uri })
    private val byUri=songs.associateBy { it.uri }
    private val nodes=mutableMapOf<String,CarMusicNode>()
    private val children=mutableMapOf<String,List<CarMusicNode>>()
    private val groups=mutableMapOf<String,List<LibraryEntry>>()
    val root=CarMusicNode(CAR_ROOT,"Youtube Pandora!")
    init {
        nodes[root.id]=root
        val albums=libraryAlbums(songs,data).filter { it.tracks.isNotEmpty() }
        val favorites=songs.filter { it.uri in data.favorites }
        val playlists=data.playlists.map { list -> list to list.uris.mapNotNull(byUri::get) }.filter { it.second.isNotEmpty() }
        val top=listOf(CarMusicNode(CAR_ALBUMS,"アルバム","${albums.size}枚"),CarMusicNode(CAR_SONGS,"曲","${songs.size}曲"),
            CarMusicNode(CAR_FAVORITES,"お気に入り","${favorites.size}曲"),CarMusicNode(CAR_PLAYLISTS,"プレイリスト","${playlists.size}件"))
        putChildren(CAR_ROOT,top)
        putTracks(CAR_SONGS,songs)
        putTracks(CAR_FAVORITES,favorites)
        putChildren(CAR_ALBUMS,albums.map { album ->
            val id="car:album:${carKey(album.id)}"
            val node=CarMusicNode(id,album.name,"${album.tracks.size}曲",group=id,album=album.id)
            putTracks(id,album.tracks,album.id)
            node
        })
        putChildren(CAR_PLAYLISTS,playlists.map { (list,tracks) ->
            val id="car:playlist:${carKey(list.id)}"
            putTracks(id,tracks)
            CarMusicNode(id,list.name,"${tracks.size}曲",group=id)
        })
    }
    private fun putTracks(id: String, tracks: List<LibraryEntry>, album: String? = null) {
        val unique=tracks.distinctBy { it.uri }
        groups[id]=unique
        putChildren(id,unique.map { CarMusicNode("car:track:${carKey(id)}:${carKey(it.uri)}",it.title,it.artist,it,id,album) })
    }
    private fun putChildren(parent: String, items: List<CarMusicNode>) {
        items.forEach { nodes[it.id]=it }
        if(items.size <= CAR_PAGE_SIZE) { children[parent]=items; return }
        var span=CAR_PAGE_SIZE
        while((items.size.toLong()+span-1)/span > CAR_PAGE_SIZE) span*=CAR_PAGE_SIZE
        children[parent]=items.chunked(span).mapIndexed { index, part ->
            val id="$parent/page:$index"
            val first=index*span+1
            val node=CarMusicNode(id,"$first–${first+part.size-1}","${part.first().title} … ${part.last().title}")
            nodes[id]=node; putChildren(id,part); node
        }
    }
    fun item(id: String): CarMusicNode? = nodes[id] ?: byUri[id]?.let { CarMusicNode(id,it.title,it.artist,it,CAR_SONGS) }
    fun children(id: String, page: Int, size: Int): List<CarMusicNode>? = children[id]?.let { carPage(it,page,size) }
    fun coverEntry(node: CarMusicNode)=node.entry ?: groups[node.id]?.firstOrNull()
    fun search(query: String): List<CarMusicNode> {
        val key=albumNameKey(query.trim()).take(200)
        if(key.isBlank()) return emptyList()
        return songs.asSequence().filter { entry -> listOf(entry.title,entry.artist,entry.album,entry.albumArtist).any { albumNameKey(it).contains(key) } }
            .take(CAR_PAGE_SIZE).map { CarMusicNode(it.uri,it.title,it.artist,it,CAR_SONGS) }.toList()
    }
    fun queue(id: String): CarMusicQueue? {
        val node=item(id) ?: return null
        val entry=node.entry ?: return groups[id]?.takeIf { it.isNotEmpty() }?.let { CarMusicQueue(it,0) }
        val queue=groups[node.group] ?: return null
        val index=queue.indexOfFirst { it.uri == entry.uri }
        return if(index < 0) null else CarMusicQueue(queue,index)
    }
}

internal fun <T> carPage(items: List<T>, page: Int, size: Int): List<T> {
    require(page >= 0 && size > 0)
    val start=page.toLong()*size
    if(start >= items.size) return emptyList()
    return items.subList(start.toInt(),minOf(start+size,items.size.toLong()).toInt())
}

internal fun carStartPosition(position: Long, duration: Long)=
    if(position < 0) 0L else if(duration > 0) position.coerceAtMost(duration) else position
