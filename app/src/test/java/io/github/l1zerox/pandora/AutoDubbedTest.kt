package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class AutoDubbedTest {
    @Test fun writesProvenanceOnlyForConfirmedDubbedAudio() {
        val dubbed=audioDecision(listOf(AudioTrack("aac","ja","Japanese auto-dubbed")),emptyList())
        assertEquals(listOf("-metadata","genre=$AUTO_DUBBED_MARKER"),autoDubbedMetadataArgs(dubbed,true))
        assertTrue(autoDubbedMetadataArgs(dubbed,false).isEmpty())
        assertTrue(autoDubbedMetadataArgs(null,true).isEmpty())
        for(note in listOf("Japanese (original)","Japanese","")) {
            assertTrue(autoDubbedMetadataArgs(audioDecision(listOf(AudioTrack("aac","ja",note)),emptyList()),true).isEmpty())
        }
        assertFalse(autoDubbedMetadataArgs(dubbed.copy(usesJapanese=false),true).isEmpty())
        assertTrue(autoDubbedMetadataArgs(dubbed.copy(original=true),true).isEmpty())
    }

    @Test fun oldJapaneseFilesAndArbitraryGenresAreNotAssumedDubbed() {
        assertTrue(isAutoDubbedMetadata(AUTO_DUBBED_MARKER))
        for(value in listOf(null,"","jpn","Japanese","オートダビング版","Music")) assertFalse(isAutoDubbedMetadata(value))
    }

    @Test fun videoAndShortCardsKeepBadgeThroughFilteringButMusicDoesNotShowIt() {
        val video=LibraryEntry("video","dubbed","video/mp4",width=1920,height=1080,autoDubbed=true)
        val short=video.copy(uri="short",width=1080,height=1920)
        assertEquals("オートダビング版",libraryEntries(listOf(video,short),emptyList(),"動画").single().dubbingLabel)
        assertEquals("オートダビング版",libraryEntries(listOf(video,short),emptyList(),"ショート").single().dubbingLabel)
        assertNull(video.copy(autoDubbed=false).dubbingLabel)
        assertNull(video.copy(mime="audio/mpeg").dubbingLabel)
    }
}
