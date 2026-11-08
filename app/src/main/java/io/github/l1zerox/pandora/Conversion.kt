package io.github.l1zerox.pandora

import kotlin.math.abs
import java.util.Locale

internal const val INTERPOLATED_FPS = "60fps（フレーム補間）"
internal fun interpolationFilter(sourceFps: Double?, duration: Double): String? {
    require(sourceFps != null && sourceFps.isFinite() && sourceFps > 0) { "元動画のフレームレートを確認できません。通常の60fpsを選択してください。" }
    if(sourceFps >= 59.99) return null
    val motion="minterpolate=fps=60:mi_mode=mci:mc_mode=obmc:me_mode=bilat:me=epzs:search_param=16:scd=fdiff"
    return if(duration.isFinite() && duration > 0)
        "tpad=stop_mode=clone:stop_duration=%.6f,%s,trim=duration=%.6f".format(Locale.US,maxOf(.2,3/sourceFps),motion,duration)
    else motion
}

internal fun frameRate(value: String): Double? {
    val parts=value.split('/')
    val numerator=parts.firstOrNull()?.toDoubleOrNull() ?: return null
    val rate=when(parts.size) {
        1 -> numerator
        2 -> numerator / (parts[1].toDoubleOrNull() ?: return null)
        else -> return null
    }
    return rate.takeIf { it.isFinite() && it > 0 }
}

internal fun canCopyVideo(codec: String, pixelFormat: String, selectedFps: String, averageFps: String, nominalFps: String): Boolean {
    if(codec != "h264" || pixelFormat != "yuv420p") return false
    if(selectedFps == "元のまま") return true
    val target=if(selectedFps == INTERPOLATED_FPS) 60.0 else frameRate(selectedFps.removeSuffix("fps")) ?: return false
    val average=frameRate(averageFps) ?: return false
    val nominal=frameRate(nominalFps) ?: return false
    return abs(average-target) < 0.000001 && abs(nominal-target) < 0.000001
}

internal fun conversionInputArgs(source: String, range: ResolvedSaveRange? = null) = listOf("-y","-nostdin","-hide_banner",
    "-filter_threads","1","-filter_complex_threads","1","-threads","1") +
    (range?.inputArgs() ?: emptyList()) + listOf("-i",source)

internal fun videoEncodingArgs(copy: Boolean, selectedFps: String, motionFilter: String? = null): List<String> {
    if(copy) return listOf("-c:v","copy")
    return listOf("-c:v","libx264","-preset","ultrafast","-tune","zerolatency","-crf","22",
        "-pix_fmt","yuv420p","-threads:v","2") +
        if(motionFilter != null) listOf("-vf",motionFilter)
        else if(selectedFps == "元のまま") emptyList() else listOf("-r",if(selectedFps == INTERPOLATED_FPS) "60" else selectedFps.removeSuffix("fps"))
}

internal fun interruptedStatus(status: String) = status in setOf("ダウンロード中","情報を確認中","変換中","端末へ保存中")
