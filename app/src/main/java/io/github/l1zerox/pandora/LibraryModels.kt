package io.github.l1zerox.pandora

internal data class LibraryEntry(val uri: String, val title: String, val mime: String,
    val durationMs: Long = 0, val added: Long = 0, val imported: Boolean = false) {
    val video: Boolean get() = mime.startsWith("video/")
}

internal fun libraryMime(mime: String?, name: String): String? = when {
    mime?.startsWith("video/") == true || mime?.startsWith("audio/") == true -> mime
    name.endsWith(".mp4",true) -> "video/mp4"
    name.endsWith(".mp3",true) -> "audio/mpeg"
    else -> null
}

/** Download rows win over imported duplicates; deterministic ordering survives refreshes. */
internal fun libraryEntries(downloads: List<LibraryEntry>, imports: List<LibraryEntry>, filter: String = "すべて", search: String = ""): List<LibraryEntry> =
    (downloads+imports).distinctBy { it.uri }.filter {
        (filter == "すべて" || (filter == "動画" && it.video) || (filter == "音楽" && !it.video)) &&
            it.title.contains(search.trim(),ignoreCase=true)
    }.sortedWith(compareByDescending<LibraryEntry> { it.added }.thenBy { it.title }.thenBy { it.uri })

internal fun pipAspect(width: Int, height: Int, pixelRatio: Float = 1f): Float =
    if(width <= 0 || height <= 0 || !pixelRatio.isFinite() || pixelRatio <= 0) 16f/9f
    else (width.toDouble()*pixelRatio/height).coerceIn(1.0/2.39,2.39).toFloat()
