package io.github.l1zerox.pandora

import kotlin.math.abs

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
    val target=frameRate(selectedFps.removeSuffix("fps")) ?: return false
    val average=frameRate(averageFps) ?: return false
    val nominal=frameRate(nominalFps) ?: return false
    return abs(average-target) < 0.000001 && abs(nominal-target) < 0.000001
}

internal fun conversionInputArgs(source: String) = listOf("-y","-nostdin","-hide_banner",
    "-filter_threads","1","-filter_complex_threads","1","-threads","1","-i",source)

internal fun videoEncodingArgs(copy: Boolean, selectedFps: String): List<String> {
    if(copy) return listOf("-c:v","copy")
    return listOf("-c:v","libx264","-preset","ultrafast","-tune","zerolatency","-crf","22",
        "-pix_fmt","yuv420p","-threads:v","2") +
        if(selectedFps == "元のまま") emptyList() else listOf("-r",selectedFps.removeSuffix("fps"))
}

internal fun interruptedStatus(status: String) = status in setOf("ダウンロード中","情報を確認中","変換中","端末へ保存中")
