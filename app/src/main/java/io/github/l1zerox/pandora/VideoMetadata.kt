package io.github.l1zerox.pandora

import java.time.Duration

internal fun positiveDuration(value: Double?): Double? = value?.takeIf { it.isFinite() && it > 0 }

internal fun parsedDuration(value: String?): Double? {
    val text=value?.trim().orEmpty()
    positiveDuration(text.toDoubleOrNull())?.let { return it }
    if(text.startsWith("P",true)) return runCatching {
        positiveDuration(Duration.parse(text.uppercase()).toMillis()/1000.0)
    }.getOrNull()
    val parts=text.split(':')
    if(parts.size !in 2..3) return null
    val numbers=parts.map { it.toDoubleOrNull()?.takeIf { n -> n.isFinite() && n >= 0 } ?: return null }
    if(numbers.drop(1).any { it >= 60 }) return null
    return positiveDuration(numbers.fold(0.0) { total,n -> total*60+n })
}

/** Refresh metadata without resetting queue identity, selection, saved files, or transfer state. */
internal fun Video.withMetadata(found: Video): Video = copy(
    title=found.title.takeIf { it.isNotBlank() && it != found.url && it != "タイトル不明" } ?: title,
    site=found.site.ifBlank { site },thumbnail=found.thumbnail.ifBlank { thumbnail },
    duration=positiveDuration(found.duration) ?: duration,resolved=resolved || found.resolved)

internal fun mergeDiscoveredVideos(existing: List<Video>, found: List<Video>): List<Video> {
    val updates=found.groupBy { mediaIdentity(it.url) }
    val result=existing.map { current -> updates[mediaIdentity(current.url)]?.fold(current) { item,new -> item.withMetadata(new) } ?: current }
    val identities=existing.map { mediaIdentity(it.url) }.toMutableSet()
    return result+found.filter { identities.add(mediaIdentity(it.url)) }
}

/** Read only the requested video's details, never duration fields from recommendations/ads. */
internal fun youtubeDurationFromHtml(html: String, videoId: String): Double? {
    if(!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return null
    for(match in Regex("\"videoDetails\"\\s*:\\s*\\{").findAll(html)) {
        val start=match.range.last
        var depth=0; var quoted=false; var escaped=false; var end=-1
        for(index in start until minOf(html.length,start+100000)) {
            val c=html[index]
            if(quoted) {
                if(escaped) escaped=false else if(c == '\\') escaped=true else if(c == '"') quoted=false
            } else when(c) {
                '"' -> quoted=true
                '{' -> depth++
                '}' -> { depth--; if(depth == 0) { end=index+1; break } }
            }
        }
        if(end < 0) continue
        val details=html.substring(start,end)
        val id=Regex("\"videoId\"\\s*:\\s*\"([^\"]+)\"").find(details)?.groupValues?.get(1)
        if(id != videoId) continue
        if(Regex("\"isLive\"\\s*:\\s*true").containsMatchIn(details)) return null
        val seconds=Regex("\"lengthSeconds\"\\s*:\\s*\"?([0-9.]+)(?:\"|(?=\\s*[,}]))").find(details)?.groupValues?.get(1)
        parsedDuration(seconds)?.let { return it }
    }
    val head=html.substringBefore("</head>")
    fun attributes(tag: String)=Regex("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2",RegexOption.DOT_MATCHES_ALL)
        .findAll(tag).associate { it.groupValues[1].lowercase() to it.groupValues[3].replace("&amp;","&") }
    val canonical=Regex("<link\\b[^>]*>",RegexOption.IGNORE_CASE).findAll(head).map { attributes(it.value) }
        .firstOrNull { it["rel"] == "canonical" }?.get("href")
    if(canonical == null || youtubeVideoId(canonical) != videoId) return null
    return Regex("<meta\\b[^>]*>",RegexOption.IGNORE_CASE).findAll(head).map { attributes(it.value) }
        .firstOrNull { it["itemprop"] == "duration" }?.get("content")?.let(::parsedDuration)
}
