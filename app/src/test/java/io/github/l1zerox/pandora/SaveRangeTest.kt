package io.github.l1zerox.pandora

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

class SaveRangeTest {
    @Test fun acceptsSecondsMinutesAndHours() {
        mapOf("30" to 30L," 90 " to 90L,"1:30" to 90L,"1：02：15" to 3735L,"90:00" to 5400L,"0:00" to 0L).forEach { (text,value) ->
            assertEquals(text,value,parseSaveTime(text))
        }
        assertEquals(SaveRange(true,0,90),saveRangeFromInput(true,"","1:30"))
        assertEquals(SaveRange(true,30,null),saveRangeFromInput(true,"30",""))
    }
    @Test fun rejectsMalformedNegativeFractionalAndOverflowTimes() {
        listOf("","-1","1.5","NaN","Infinity","1:60","1:00:60","1:2:3:4",":30","1:","1 :30","999999999999999:00:00").forEach {
            assertNull(it,parseSaveTime(it))
        }
        for((start,end) in listOf("bad" to "30","0" to "bad","30" to "30","60" to "30","0" to "0","" to ""))
            assertThrows(IllegalStateException::class.java.takeIf { start == "bad" || end == "bad" } ?: IllegalArgumentException::class.java) {
                saveRangeFromInput(true,start,end)
            }
    }
    @Test fun oldOrInvalidSettingsDefaultToWholeDownload() {
        assertEquals(SaveRange(),readSaveRange(null))
        assertFalse(Preferences().saveRange.enabled)
        listOf("{}","{\"enabled\":true}","{\"enabled\":true,\"start\":-1}","{\"enabled\":true,\"start\":60,\"end\":30}").forEach {
            assertEquals(SaveRange(),readSaveRange(JSONObject(it)))
        }
        for(range in listOf(SaveRange(true,30,90),SaveRange(true,30),SaveRange(false,30,90)))
            assertEquals(range,readSaveRange(JSONObject(range.json().toString())))
    }
    @Test fun resolvesAndClampsUsingSourceDuration() {
        assertEquals(ResolvedSaveRange(30.0,60.0),resolveSaveRange(SaveRange(true,30,90),100.0))
        assertEquals(ResolvedSaveRange(30.0,12.5),resolveSaveRange(SaveRange(true,30,90),42.5))
        assertEquals(ResolvedSaveRange(30.0,12.5),resolveSaveRange(SaveRange(true,30),42.5))
        assertEquals(ResolvedSaveRange(0.0,10.0),resolveSaveRange(SaveRange(true,0,10),100.0))
        for(duration in listOf(30.0,12.0)) assertThrows(IllegalArgumentException::class.java) {
            resolveSaveRange(SaveRange(true,30),duration)
        }
    }
    @Test fun unknownDurationSupportsExplicitEndOrEndOfInput() {
        for(duration in listOf(null,0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY)) {
            assertEquals(ResolvedSaveRange(30.0,60.0),resolveSaveRange(SaveRange(true,30,90),duration))
            assertEquals(ResolvedSaveRange(30.0,null),resolveSaveRange(SaveRange(true,30),duration))
        }
        assertNull(resolveSaveRange(SaveRange(false,30,90),10.0))
    }
    @Test fun seeksTheSourceAndLimitsOutputWithoutSeekingTheAlbumArt() {
        val range=resolveSaveRange(SaveRange(true,30,90),100.0)!!
        val input=conversionInputArgs("source.mp4",range)
        val args=input+mp3EncodingArgs("192kbps","cover.jpg")+range.outputArgs()+"output.mp3"
        assertTrue(args.indexOf("-ss") < args.indexOf("-i"))
        assertEquals("30.000000",args[args.indexOf("-ss")+1])
        assertTrue(args.indexOf("-t") > args.indexOf("cover.jpg"))
        assertEquals("60.000000",args[args.indexOf("-t")+1])
        assertEquals(1,args.count { it == "-ss" })
        assertEquals(listOf("-map_chapters","-1"),ResolvedSaveRange(30.0,null).outputArgs())
        assertFalse(conversionInputArgs("source.mp4").contains("-ss"))
        assertEquals(emptyList<String>(),ResolvedSaveRange(0.0,30.0).inputArgs())
    }
    @Test fun argumentsUseDecimalPointInEveryLocale() {
        val old=Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("12.500000",ResolvedSaveRange(12.5,12.5).inputArgs()[1])
            assertEquals("12.500000",ResolvedSaveRange(12.5,12.5).outputArgs()[1])
            assertEquals("1:02:15",saveTimeText(3735))
        } finally { Locale.setDefault(old) }
    }
    @Test fun clipsHaveDistinctNamesAndAreNotRecognizedAsLegacyWholeFiles() {
        val range=ResolvedSaveRange(30.0,60.0)
        assertEquals(" [clip_0-30-1-30]",range.fileSuffix())
        assertNull(legacySavedSource("Title [abcdefghijk]${range.fileSuffix()}.mp4"))
        assertNull(legacySavedSource("Title [abcdefghijk]${range.fileSuffix()} (2).mp3"))
        assertEquals("0:30 → 最後まで",SaveRange(true,30).summary())
        assertEquals("全体",SaveRange(false,30,90).summary())
    }
    @Test fun savedClipsDoNotHideFullDownloadsOrOtherRanges() {
        val video=Video("youtube:abcdefghijk","https://youtu.be/abcdefghijk","Test")
        val range=SaveRange(true,30,90).identity()
        val saved=listOf(SavedDownload(savedSource(video),"MP4","content://1",range))
        assertTrue(saved.containsDownload(video,"MP4",range))
        assertFalse(saved.containsDownload(video,"MP4"))
        assertFalse(saved.containsDownload(video,"MP3",range))
        assertFalse(saved.containsDownload(video,"MP4",SaveRange(true,30,60).identity()))
        assertFalse(listOf(SavedDownload(savedSource(video),"MP4","content://2")).containsDownload(video,"MP4",range))
    }
    @Test fun pausedDownloadsResumeOnlyWithinTheSameRangeAndDisabledRangeKeepsOldWorkspace() {
        val video=Video("key","https://example.com/video","Title")
        val root=File("cache")
        val full=transferWorkspace(root,video,Preferences())
        assertEquals(full,transferWorkspace(root,video,Preferences(saveRange=SaveRange(false,30,60))))
        val p=Preferences(saveRange=SaveRange(true,30,60))
        val clip=transferWorkspace(root,video,p)
        assertNotEquals(full,clip)
        assertEquals(clip,transferWorkspace(root,video.copy(status="一時停止"),p.copy(dark=false)))
        assertNotEquals(clip,transferWorkspace(root,video,p.copy(saveRange=SaveRange(true,30,90))))
        assertNotEquals(clip,transferWorkspace(root,video,p.copy(saveRange=SaveRange(true,30))))
    }
}
