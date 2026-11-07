package io.github.l1zerox.pandora

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.net.URI
import java.net.URLDecoder

/** Only individual YouTube video links are eligible for clipboard auto-add. */
internal fun youtubeVideoId(url: String): String? {
    val uri=runCatching { URI(url) }.getOrNull() ?: return null
    if(uri.scheme !in listOf("http","https") || uri.userInfo != null || uri.port !in listOf(-1,80,443)) return null
    val host=uri.host?.lowercase() ?: return null
    if(host != "youtu.be" && host != "youtube.com" && !host.endsWith(".youtube.com")) return null
    val path=uri.rawPath.orEmpty().trimEnd('/')
    val id=when {
        host == "youtu.be" -> path.removePrefix("/")
        path == "/watch" -> uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.substringAfter('=')
            ?.let { runCatching { URLDecoder.decode(it,"UTF-8") }.getOrNull() }
        path.startsWith("/shorts/") -> path.removePrefix("/shorts/")
        path.startsWith("/live/") -> path.removePrefix("/live/")
        else -> null
    }
    return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
}

internal fun clipboardYoutubeUrls(text: String): List<String> = sharedUrls(text.take(65536))
    .filter { youtubeVideoId(it) != null }

internal fun youtubeThumbnail(url: String): String = youtubeVideoId(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()

internal fun bubbleVideo(state: ScreenState): Video? =
    state.videos.firstOrNull { state.busy && it.key == state.current }
        ?: state.videos.lastOrNull { it.selected && it.status != "完了" }
        ?: state.videos.lastOrNull { it.selected }

internal data class BubblePalette(val accent: Color, val surface: Color, val ring: Color, val track: Color)
internal fun bubblePalette(source: Color = Color(0xFF4388FF)): BubblePalette {
    val accent=readableBadgeColor(source)
    return BubblePalette(accent,lerp(accent,Color.Black,.76f),lerp(accent,Color.White,.48f),lerp(accent,Color.Black,.42f))
}
