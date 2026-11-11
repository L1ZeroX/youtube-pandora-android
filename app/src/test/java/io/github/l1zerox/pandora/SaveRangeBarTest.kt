package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class SaveRangeBarTest {
    @Test fun boundsIncludeFractionalLastSecondAndRejectUnknownOrUnsafeDurations() {
        assertEquals(264L,saveRangeBarMaximum(263.7))
        assertEquals(263L,saveRangeBarMaximum(263.0))
        assertEquals(1L,saveRangeBarMaximum(.2))
        for(value in listOf(null,0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY,16_777_217.0)) assertNull(saveRangeBarMaximum(value))
    }
    @Test fun referenceChoicesOnlyIncludeSelectedVideosWithKnownLength() {
        val v=Video("one","https://example.com/1","One",duration=60.0)
        assertEquals(listOf(v),saveRangeBarReferences(listOf(v,v.copy(key="two",selected=false),v.copy(key="three",duration=null))))
    }
    @Test fun deselectedQueueStillProvidesAReferenceWithoutChangingSelection() {
        val known=Video("one","https://example.com/1","One",duration=90.0,selected=false)
        val unknown=known.copy(key="two",duration=null)
        assertEquals(listOf(known),saveRangeBarReferences(listOf(unknown,known)))
        assertFalse(known.selected)
        assertEquals(listOf(unknown.copy(selected=true)),saveRangeBarCandidates(listOf(known,unknown.copy(selected=true))))
        assertTrue(saveRangeBarReferences(listOf(known,unknown.copy(selected=true))).isEmpty())
    }
    @Test fun timeFieldsMapToTheSameWholeSecondPositions() {
        assertEquals(SaveRangeBarSelection(30,90),saveRangeBarSelection("0:30","1:30",180))
        assertEquals(SaveRangeBarSelection(0,180),saveRangeBarSelection("","",180))
        assertEquals(SaveRangeBarSelection(30,180),saveRangeBarSelection("30","",180))
        assertEquals(SaveRangeBarSelection(30,60),saveRangeBarSelection("30","90",60))
    }
    @Test fun unfinishedOrOutOfBoundsTextAlwaysHasASafeBarProjection() {
        for((a,b) in listOf("1:" to "bad","-1" to "20","100" to "20","60" to "60","50" to "0")) {
            val value=saveRangeBarSelection(a,b,60)
            assertTrue(value.start >= 0 && value.end <= 60 && value.start < value.end)
        }
        assertEquals(SaveRangeBarSelection(0,1),saveRangeBarSelection("30","60",1))
    }
    @Test fun movementRoundsToSecondsAndStopsBeforeHandlesCross() {
        val previous=SaveRangeBarSelection(10,30)
        assertEquals(SaveRangeBarSelection(15,30),moveSaveRangeBar(14.6f..30f,previous,60))
        assertEquals(SaveRangeBarSelection(29,30),moveSaveRangeBar(30f..30f,previous,60))
        assertEquals(SaveRangeBarSelection(10,11),moveSaveRangeBar(10f..10f,previous,60))
        assertEquals(SaveRangeBarSelection(0,60),moveSaveRangeBar(-10f..100f,previous,60))
        assertEquals(previous,moveSaveRangeBar(Float.NaN..30f,previous,60))
        assertEquals(previous,moveSaveRangeBar(10f..Float.POSITIVE_INFINITY,previous,60))
    }
    @Test fun movingStartKeepsBlankEndButMovingEndSetsAnExplicitTime() {
        val previous=SaveRangeBarSelection(10,60)
        assertEquals("",SaveRangeBarSelection(20,60).endText(previous,""))
        assertEquals("0:40",SaveRangeBarSelection(10,40).endText(previous,""))
        assertEquals("1:00",SaveRangeBarSelection(20,60).endText(previous,"60"))
    }
    @Test fun barTimesRoundTripThroughTheExistingSaveRangeValidation() {
        val previous=SaveRangeBarSelection(0,3600)
        val selected=moveSaveRangeBar(89.7f..180.2f,previous,3600)
        val range=saveRangeFromInput(true,saveTimeText(selected.start),selected.endText(previous,"1:00:00"))
        assertEquals(SaveRange(true,90,180),range)
        assertEquals(ResolvedSaveRange(90.0,90.0),resolveSaveRange(range,3600.0))
    }
}
