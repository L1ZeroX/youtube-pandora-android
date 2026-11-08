package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class SavedAudioMetadataTest {
    private val saved="""{"revision":"1024:2000","autoDubbed":true}"""
    @Test fun confirmedSelectionSurvivesSerializationForTheSameFile() {
        assertTrue(savedAutoDubbedMatches(saved,"1024:2000"))
    }
    @Test fun overwrittenFilesAndUnverifiableRevisionsCannotInheritALabel() {
        for(revision in listOf("2048:2000","1024:2001","",null)) assertFalse(savedAutoDubbedMatches(saved,revision))
    }
    @Test fun missingCorruptAndUnconfirmedRecordsCannotCreateALabel() {
        for(value in listOf(null,"broken","{}",saved.replace("true","false"))) assertFalse(savedAutoDubbedMatches(value,"1024:2000"))
    }
}
