package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class ConversionTest {
    @Test fun interpolationCreatesMotionOnlyForLowerRatesAndKeepsDuration() {
        val filter=interpolationFilter(30.0,2.0)!!
        assertTrue(filter.contains("mi_mode=mci"))
        assertTrue(filter.contains("trim=duration=2.000000"))
        assertTrue(videoEncodingArgs(false,INTERPOLATED_FPS,filter).contains(filter))
        assertNull(interpolationFilter(60.0,2.0))
        assertNull(interpolationFilter(120.0,2.0))
        assertThrows(IllegalArgumentException::class.java) { interpolationFilter(null,2.0) }
        assertFalse(canCopyVideo("h264","yuv420p",INTERPOLATED_FPS,"30/1","30/1"))
        assertTrue(canCopyVideo("h264","yuv420p",INTERPOLATED_FPS,"60/1","60/1"))
    }
    @Test fun parsesValidRatesAndRejectsUnknownRates() {
        assertEquals(60.0,frameRate("60/1")!!,0.000001)
        assertEquals(59.94005994,frameRate("60000/1001")!!,0.000001)
        listOf("0/0","1/0","0","-30","NaN","","1/2/3").forEach { assertNull(frameRate(it)) }
    }
    @Test fun matchingFpsAvoidsEncodingButDifferentOrUnknownRatesDoNot() {
        assertTrue(canCopyVideo("h264","yuv420p","60fps","60/1","60/1"))
        assertTrue(canCopyVideo("h264","yuv420p","元のまま","0/0","0/0"))
        listOf("30/1","60000/1001","0/0").forEach {
            assertFalse(canCopyVideo("h264","yuv420p","60fps",it,it))
        }
        assertFalse(canCopyVideo("h264","yuv420p","60fps","60/1","30/1"))
    }
    @Test fun incompatibleCodecOrPixelFormatStillRequiresEncoding() {
        assertFalse(canCopyVideo("vp9","yuv420p","元のまま","60/1","60/1"))
        assertFalse(canCopyVideo("h264","yuv420p10le","60fps","60/1","60/1"))
    }
    @Test fun copyPreservesTimestampsAndEncodingRequestsFps() {
        assertEquals(listOf("-c:v","copy"),videoEncodingArgs(true,"60fps"))
        val encoded=videoEncodingArgs(false,"60fps")
        assertEquals("60",encoded[encoded.indexOf("-r")+1])
        assertEquals("2",encoded[encoded.indexOf("-threads:v")+1])
        assertEquals("zerolatency",encoded[encoded.indexOf("-tune")+1])
        assertFalse(videoEncodingArgs(false,"元のまま").contains("-r"))
        val input=conversionInputArgs("/cache/日本語 video.mkv")
        assertTrue(input.indexOf("-threads") < input.indexOf("-i"))
        assertEquals("1",input[input.indexOf("-threads")+1])
        assertEquals("/cache/日本語 video.mkv",input.last())
    }
    @Test fun onlyActiveWorkIsReportedAsInterrupted() {
        listOf("ダウンロード中","情報を確認中","変換中","端末へ保存中").forEach { assertTrue(interruptedStatus(it)) }
        listOf("完了","失敗","待機中","停止","").forEach { assertFalse(interruptedStatus(it)) }
    }
}
