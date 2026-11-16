package io.github.l1zerox.pandora

import org.json.JSONObject
import java.net.URI
import kotlin.math.ceil
import kotlin.math.floor

internal data class StoryboardTile(val url: String, val left: Int, val top: Int, val width: Int, val height: Int)
internal data class StoryboardSheet(val url: String, val start: Double, val duration: Double, val frames: Int)
internal data class SaveRangeStoryboard(val width: Int, val height: Int, val columns: Int, val rows: Int,
    val fps: Double, val duration: Double, val sheets: List<StoryboardSheet>) {
    /** End is exclusive: preview the last included image, never a blank cell after the video. */
    fun tile(seconds: Long, endExclusive: Boolean = false): StoryboardTile {
        val time=(seconds.toDouble()-if(endExclusive) .001 else 0.0).coerceIn(0.0,(duration-.001).coerceAtLeast(0.0))
        val sheet=sheets.lastOrNull { it.start <= time } ?: sheets.first()
        val cell=floor((time-sheet.start).coerceAtLeast(0.0)*fps+1e-8).toInt().coerceIn(0,sheet.frames-1)
        return StoryboardTile(sheet.url,cell%columns*width,cell/columns*height,width,height)
    }
}

private fun storyboardUrl(raw: String, base: String): String? = runCatching {
    val uri=URI(base).resolve(raw)
    uri.takeIf { it.scheme in listOf("http","https") && !it.host.isNullOrBlank() && it.userInfo == null &&
        it.toString().length <= 8192 && !it.path.orEmpty().contains('$') }?.toString()
}.getOrNull()

/** yt-dlp exposes sprite sheets as image-only mhtml formats; no media stream is downloaded. */
internal fun parseSaveRangeStoryboard(json: JSONObject): SaveRangeStoryboard? {
    val duration=json.optDouble("duration").takeIf { it.isFinite() && it > 0 && it <= 16_777_216 } ?: return null
    if(json.optBoolean("is_live") || json.optString("live_status") in listOf("is_live","is_upcoming")) return null
    val formats=json.optJSONArray("formats") ?: return null
    val candidates=(0 until minOf(formats.length(),1000)).mapNotNull { index ->
        val format=formats.optJSONObject(index) ?: return@mapNotNull null
        if(format.optString("protocol") != "mhtml") return@mapNotNull null
        val width=format.optInt("width"); val height=format.optInt("height")
        val columns=format.optInt("columns"); val rows=format.optInt("rows")
        val fps=format.optDouble("fps")
        if(width !in 1..640 || height !in 1..640 || columns !in 1..20 || rows !in 1..20 ||
            width.toLong()*height*columns*rows > 4_000_000 || !fps.isFinite() || fps <= 0 || fps > 120) return@mapNotNull null
        val fragments=format.optJSONArray("fragments")
        if(fragments == null || fragments.length() !in 1..10_000) return@mapNotNull null
        val base=format.optString("fragment_base_url").ifBlank { format.optString("url") }
        var start=0.0
        val sheets=ArrayList<StoryboardSheet>()
        for(i in 0 until fragments.length()) {
            val fragment=fragments.optJSONObject(i) ?: return@mapNotNull null
            val url=storyboardUrl(fragment.optString("url"),base) ?: return@mapNotNull null
            val length=fragment.optDouble("duration",columns*rows/fps)
            if(!length.isFinite() || length <= 0) return@mapNotNull null
            val included=minOf(length,duration-start)
            if(included <= 0) break
            if(included*fps > columns*rows+.001) return@mapNotNull null
            val frames=ceil(included*fps-1e-7).toInt().coerceIn(1,columns*rows)
            sheets.add(StoryboardSheet(url,start,included,frames)); start+=length
        }
        if(sheets.isEmpty() || start+0.05 < duration) return@mapNotNull null
        SaveRangeStoryboard(width,height,columns,rows,fps,duration,sheets)
    }
    return candidates.filter { it.width <= 320 }.maxByOrNull { it.width }
        ?: candidates.minByOrNull { it.width }
}
