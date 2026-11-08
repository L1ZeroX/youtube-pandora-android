package io.github.l1zerox.pandora

import org.json.JSONObject
import java.util.Locale

data class SaveRange(val enabled: Boolean = false, val startSeconds: Long = 0, val endSeconds: Long? = null)

internal const val OPEN_SAVE_RANGE="io.github.l1zerox.pandora.OPEN_SAVE_RANGE"
private const val MAX_RANGE_SECONDS=Long.MAX_VALUE/1000

internal fun parseSaveTime(text: String): Long? {
    val parts=text.trim().replace('：',':').split(':')
    if(parts.size !in 1..3 || parts.any { !Regex("[0-9]{1,15}").matches(it) }) return null
    val values=parts.map { it.toLongOrNull() ?: return null }
    if(values.drop(1).any { it !in 0..59 }) return null
    return runCatching { values.fold(0L) { total,value -> Math.addExact(Math.multiplyExact(total,60),value) } }
        .getOrNull()?.takeIf { it <= MAX_RANGE_SECONDS }
}

internal fun saveTimeText(seconds: Long): String = if(seconds >= 3600)
    String.format(Locale.US,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
else String.format(Locale.US,"%d:%02d",seconds/60,seconds%60)

internal fun validateSaveRange(range: SaveRange): SaveRange {
    require(range.startSeconds in 0..MAX_RANGE_SECONDS) { "開始時刻が不正です。" }
    require(range.endSeconds == null || range.endSeconds in 1..MAX_RANGE_SECONDS && range.endSeconds > range.startSeconds) {
        "終了時刻は開始時刻より後にしてください。"
    }
    require(!range.enabled || range.startSeconds > 0 || range.endSeconds != null) { "開始か終了を指定してください。全体を保存する場合はオフにします。" }
    return range
}

internal fun saveRangeFromInput(enabled: Boolean, start: String, end: String): SaveRange {
    val first=if(start.isBlank()) 0 else parseSaveTime(start) ?: error("開始時刻は秒数、分:秒、時:分:秒で入力してください。")
    val last=if(end.isBlank()) null else parseSaveTime(end) ?: error("終了時刻は秒数、分:秒、時:分:秒で入力してください。")
    return validateSaveRange(SaveRange(enabled,first,last))
}

internal fun SaveRange.summary()=if(!enabled) "全体" else "${saveTimeText(startSeconds)} → ${endSeconds?.let(::saveTimeText) ?: "最後まで"}"
internal fun SaveRange.identity()=if(!enabled) "" else "clip:$startSeconds:${endSeconds ?: "end"}"
internal fun SaveRange.json()=JSONObject().put("enabled",enabled).put("start",startSeconds).put("end",endSeconds ?: JSONObject.NULL)
internal fun readSaveRange(json: JSONObject?): SaveRange = if(json == null) SaveRange() else runCatching {
    validateSaveRange(SaveRange(json.optBoolean("enabled"),json.optLong("start",0),
        if(json.isNull("end")) null else json.getLong("end")))
}.getOrDefault(SaveRange())

internal data class ResolvedSaveRange(val start: Double, val duration: Double?) {
    fun inputArgs()=if(start > 0) listOf("-ss",String.format(Locale.US,"%.6f",start)) else emptyList()
    fun outputArgs()=(duration?.let { listOf("-t",String.format(Locale.US,"%.6f",it)) } ?: emptyList()) + listOf("-map_chapters","-1")
    fun fileSuffix()=" [clip_${durationText(start).replace(':','-')}-${duration?.let { durationText(start+it).replace(':','-') } ?: "end"}]"
}

internal fun resolveSaveRange(range: SaveRange, sourceDuration: Double?): ResolvedSaveRange? {
    if(!range.enabled) return null
    validateSaveRange(range)
    val known=sourceDuration?.takeIf { it.isFinite() && it > 0 }
    val start=range.startSeconds.toDouble()
    require(known == null || start < known) { "開始時刻が動画の長さ（${durationText(known)}）以上です。保存範囲を変更してください。" }
    val end=range.endSeconds?.toDouble()?.let { if(known != null) minOf(it,known) else it } ?: known
    return ResolvedSaveRange(start,end?.minus(start))
}
