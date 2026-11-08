package io.github.l1zerox.pandora

import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AudioLanguageTest {
    private val japanese=AudioTrack("opus","ja","Japanese auto-dubbed")
    private val original=AudioTrack("aac","en","English (original)")

    @Test fun japaneseLanguageTagsExcludeOtherLanguagesAndDescriptiveTracks() {
        for(tag in listOf("ja","JA","jpn","ja-JP","ja_JP","ja-orig")) assertTrue(tag,isJapaneseAudio(tag))
        for(tag in listOf("","und","en","jv","jam","japanese","ja-desc","ja-JP-desc")) assertFalse(tag,isJapaneseAudio(tag))
    }
    @Test fun detectsJapaneseAudioSeparatelyFromVideoAndUnknownLanguage() {
        val decision=audioDecision(listOf(japanese),listOf(original,japanese))
        assertEquals(true,decision.japaneseAvailable); assertTrue(decision.usesJapanese); assertTrue(decision.autoDubbed)
        assertEquals("日本語の自動吹き替えを使用",decision.label)
        val fallback=audioDecision(listOf(original),listOf(original,AudioTrack("none","ja")))
        assertEquals(false,fallback.japaneseAvailable); assertFalse(fallback.usesJapanese); assertTrue(fallback.original)
        assertEquals(null,audioDecision(listOf(AudioTrack("aac","")),emptyList()).japaneseAvailable)
        assertFalse(audioDecision(listOf(japanese.copy(note="Japanese")),emptyList()).autoDubbed)
    }
    @Test fun unavailableSelectionIsDistinguishedFromAbsentJapaneseAudio() {
        val result=audioDecision(listOf(original),listOf(original,japanese))
        assertEquals(true,result.japaneseAvailable); assertFalse(result.usesJapanese)
        assertTrue(result.label.contains("選択できず"))
    }
    @Test fun recognizesLocalizedAndUnicodeAutoDubbingNotesWithoutLabelingOrdinaryJapanese() {
        for(note in listOf("日本語（自動吹き替え）","日本語・自動吹替","日本語 自動ダビング","オートダビング版",
            "Japanese auto\u2011dubbed, medium","Japanese AUTO_DUBBED","Japanese automatically dubbed","Japanese (auto dubbing)")) {
            assertTrue(note,audioDecision(listOf(japanese.copy(note=note)),emptyList()).autoDubbed)
        }
        for(note in listOf("Japanese","日本語","日本語（オリジナル）","Japanese original","Japanese dubbed")) {
            assertFalse(note,audioDecision(listOf(japanese.copy(note=note)),emptyList()).autoDubbed)
        }
        assertTrue(audioDecision(listOf(japanese.copy(note="日本語（オリジナル）")),emptyList()).original)
    }
    @Test fun fillsMissingSelectedNotesOnlyFromTheSameFormatId() {
        val report="""PANDORA_AUDIO|{"selected":[{"format_id":"137","acodec":"none"},{"format_id":"140-2","acodec":"aac","language":"ja"}],"available":[{"format_id":"140-0","acodec":"aac","language":"en","format_note":"English original"},{"format_id":"140-2","acodec":"aac","language":"ja","format_note":"日本語（自動吹き替え）"}]}"""
        val selected=parseAudioSelection(report)!!
        assertTrue(selected.autoDubbed); assertTrue(selected.usesJapanese); assertFalse(selected.original)
        val originalReport=report.replace("\"140-2\",\"acodec\":\"aac\",\"language\":\"ja\"}],","\"140-0\",\"acodec\":\"aac\",\"language\":\"en\"}],")
        val selectedOriginal=parseAudioSelection(originalReport)!!
        assertFalse(selectedOriginal.autoDubbed); assertTrue(selectedOriginal.original)
    }
    @Test fun mergedFormatIdsRecoverAudioSelectionWhenRequestedFormatDetailsAreAbsent() {
        val report="""PANDORA_AUDIO|{"selected":null,"single":{"format_id":"137+140-2","language":"en","acodec":"aac"},"available":[{"format_id":"137","acodec":"none"},{"format_id":"140-2","acodec":"aac","language":"ja","format_note":"Japanese auto-dubbed"}]}"""
        assertTrue(parseAudioSelection(report)!!.autoDubbed)
        assertTrue(parseAudioSelection(report)!!.usesJapanese)
    }
    @Test fun explicitlyDubbedAudioCanBeIdentifiedEvenWhenItIsNotJapanese() {
        val decision=audioDecision(listOf(AudioTrack("aac","fr","French auto-dubbed")),emptyList())
        assertTrue(decision.autoDubbed); assertFalse(decision.usesJapanese)
        assertEquals("自動吹き替え音声を使用",decision.label)
        assertFalse(autoDubbedMetadataArgs(decision,true).isEmpty())
    }
    @Test fun parsesMergedAndCombinedSelectionsWithoutUsingTheRootLanguageAsTheChosenTrack() {
        val merged="""PANDORA_AUDIO|{"selected":[{"acodec":"none","language":"en"},{"acodec":"opus","language":"ja","format_note":"Japanese auto-dubbed"}],"single":{"acodec":"aac","language":"en"},"available":[{"acodec":"opus","language":"ja"}]}"""
        assertTrue(parseAudioSelection(merged)!!.usesJapanese)
        val combined="""PANDORA_AUDIO|{"selected":[],"single":{"acodec":"aac","language":"ja-JP"},"available":[]}"""
        assertTrue(parseAudioSelection(combined)!!.usesJapanese)
        assertNull(parseAudioSelection("PANDORA_AUDIO|broken")); assertNull(parseAudioSelection("ordinary log"))
        assertNull(parseAudioSelection(AUDIO_SELECTION_PREFIX+"x".repeat(256_001)))
    }
    @Test fun toggleKeepsOldDefaultsAndSeparatesResumeFilesForMp4AndMp3() {
        assertFalse(Preferences().preferJapaneseAudio)
        val video=Video("a","https://example.com/a","a")
        for(format in listOf("MP4","MP3")) {
            val p=Preferences(format=format)
            val old=if(format == "MP4") "bestvideo[height<=1080]+bestaudio/best[height<=1080]" else "bestaudio/best"
            assertEquals(old,formatSelector(p))
            assertNotEquals(transferWorkspace(File("root"),video,p),transferWorkspace(File("root"),video,p.copy(preferJapaneseAudio=true)))
        }
    }
    @Test fun aVideosJapaneseTitleLanguageDoesNotProveTheMuxedAudioIsJapanese() {
        val inherited="""PANDORA_AUDIO|{"selected":[],"single":{"format_id":"mux","acodec":"aac","language":"ja"},"available":[{"format_id":"mux","acodec":"aac"}]}"""
        val result=parseAudioSelection(inherited)!!
        assertFalse(result.usesJapanese); assertNull(result.japaneseAvailable)
    }
    @Test fun reportedSourceMustStayInsideTheWorkspaceAndCannotBeAPartialFile() {
        val work=File("build/audio-path-test").absoluteFile
        val selected=File(work,"source.ja.137+251-3.mkv")
        assertEquals(selected.canonicalFile,selectedAudioSource(work,AUDIO_FILE_PREFIX+selected.absolutePath))
        assertNull(selectedAudioSource(work,AUDIO_FILE_PREFIX+File(work,"../source.ja.mp4").absolutePath))
        assertNull(selectedAudioSource(work,AUDIO_FILE_PREFIX+File(work,"source.ja.mp4.part").absolutePath))
        assertNull(selectedAudioSource(work,AUDIO_FILE_PREFIX+File(work,"output.mp4").absolutePath))
    }
    @Test fun exportsActualSelectorsAndRepeatedPrintOptionsForBundledExtractorIntegration() {
        val request=YoutubeDLRequest("https://example.com/fixture").apply { addAudioSelectionOptions() }
        val options=request.buildCommand().dropLast(1)
        assertEquals(2,options.count { it == "--print" })
        assertTrue(options.contains("--no-simulate")); assertTrue(options.contains("--progress"))
        val selectors=JSONObject()
        for(format in listOf("MP4","MP3")) for(quality in listOf("720p以下","1080p以下","最高")) for(enabled in listOf(false,true)) {
            selectors.put("$format|$quality|$enabled",formatSelector(Preferences(format=format,quality=quality,preferJapaneseAudio=enabled)))
        }
        val output=File("build/reports/audio-selection-fixture.json").apply { parentFile?.mkdirs() }
        output.writeText(JSONObject().put("selectors",selectors).put("options",JSONArray(options))
            .put("output",AUDIO_OUTPUT_NAME).put("selectionTemplate",AUDIO_SELECTION_TEMPLATE.substringAfter(':'))
            .put("filePrefix",AUDIO_FILE_PREFIX).toString())
    }
}
