package io.github.l1zerox.pandora

internal data class LibraryEntry(val uri: String, val title: String, val mime: String,
    val durationMs: Long = 0, val added: Long = 0, val imported: Boolean = false,
    val width: Int = 0, val height: Int = 0, val autoDubbed: Boolean = false,
    val artist: String = "", val album: String = "", val albumArtist: String = "", val track: Int = 0, val disc: Int = 0) {
    val video: Boolean get() = mime.startsWith("video/")
    val short: Boolean get() = video && width > 0 && height > width
    val category: String get() = if(!video) "音楽" else if(short) "ショート" else "動画"
    val dubbingLabel: String? get() = if(video && autoDubbed) "オートダビング版" else null
}

/** Metadata dimensions describe the encoded frame; classify its displayed orientation. */
internal fun libraryDisplaySize(width: Int, height: Int, rotation: Int): Pair<Int,Int> {
    if(width <= 0 || height <= 0) return 0 to 0
    return if(((rotation%360)+360)%360 in listOf(90,270)) height to width else width to height
}

internal fun libraryMime(mime: String?, name: String): String? = when {
    mime?.startsWith("video/") == true || mime?.startsWith("audio/") == true -> mime
    name.endsWith(".mp4",true) -> "video/mp4"
    name.endsWith(".mp3",true) -> "audio/mpeg"
    name.endsWith(".m4a",true) -> "audio/mp4"
    name.endsWith(".flac",true) -> "audio/flac"
    name.endsWith(".wav",true) -> "audio/wav"
    name.endsWith(".ogg",true) -> "audio/ogg"
    name.endsWith(".opus",true) -> "audio/opus"
    name.endsWith(".aac",true) -> "audio/aac"
    else -> null
}

/** Download rows win over imported duplicates; deterministic ordering survives refreshes. */
internal fun libraryEntries(downloads: List<LibraryEntry>, imports: List<LibraryEntry>, filter: String = "すべて", search: String = ""): List<LibraryEntry> =
    (downloads+imports).distinctBy { it.uri }.filter {
        (filter == "すべて" || filter == it.category) &&
            (it.title.contains(search.trim(),ignoreCase=true) || !it.video &&
                (it.artist.contains(search.trim(),ignoreCase=true) || it.albumArtist.contains(search.trim(),ignoreCase=true) || it.album.contains(search.trim(),ignoreCase=true)))
    }.sortedWith(compareByDescending<LibraryEntry> { it.added }.thenBy { it.title }.thenBy { it.uri })

internal fun pipAspect(width: Int, height: Int, pixelRatio: Float = 1f): Float =
    if(width <= 0 || height <= 0 || !pixelRatio.isFinite() || pixelRatio <= 0) 16f/9f
    else (width.toDouble()*pixelRatio/height).coerceIn(1.0/2.39,2.39).toFloat()
