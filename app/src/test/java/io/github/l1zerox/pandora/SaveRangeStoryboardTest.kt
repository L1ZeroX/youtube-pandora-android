package io.github.l1zerox.pandora

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SaveRangeStoryboardTest {
    private fun format(width: Int = 160)=JSONObject().put("protocol","mhtml").put("width",width).put("height",90)
        .put("columns",2).put("rows",2).put("fps",.5).put("url","https://images.example.com/boards/")
        .put("fragments",JSONArray().apply {
            for((index,duration) in listOf(8,8,2).withIndex()) put(JSONObject().put("url","$index.jpg").put("duration",duration))
        })
    private fun root(vararg formats: JSONObject)=JSONObject().put("duration",18).put("formats",JSONArray(formats.toList()))
    @Test fun selectedPositionMapsAcrossRowsAndSpriteSheets() {
        val board=parseSaveRangeStoryboard(root(format()))!!
        assertEquals(StoryboardTile("https://images.example.com/boards/0.jpg",0,0,160,90),board.tile(0))
        assertEquals(StoryboardTile("https://images.example.com/boards/0.jpg",160,0,160,90),board.tile(2))
        assertEquals(StoryboardTile("https://images.example.com/boards/0.jpg",0,90,160,90),board.tile(4))
        assertEquals(StoryboardTile("https://images.example.com/boards/1.jpg",0,0,160,90),board.tile(8))
    }
    @Test fun exclusiveEndShowsTheLastIncludedCellAndSkipsBlankTailCells() {
        val board=parseSaveRangeStoryboard(root(format()))!!
        assertEquals(160,board.tile(4,true).left); assertEquals(0,board.tile(4,true).top)
        assertEquals("https://images.example.com/boards/0.jpg",board.tile(8,true).url)
        for(seconds in listOf(17L,18L,Long.MAX_VALUE)) {
            val tile=board.tile(seconds,true)
            assertEquals("https://images.example.com/boards/2.jpg",tile.url)
            assertEquals(0,tile.left); assertEquals(0,tile.top)
        }
        assertEquals(board.tile(0),board.tile(-30))
    }
    @Test fun fractionalVideoLengthNeverPointsOutsideTheLastSheet() {
        val f=format().put("fps",4.0/1.3).put("fragments",JSONArray().put(JSONObject().put("url","end.jpg").put("duration",1.3)))
        val board=parseSaveRangeStoryboard(root(f).put("duration",1.3))!!
        val last=board.tile(2,true)
        assertEquals(160,last.left); assertEquals(90,last.top)
    }
    @Test fun choosesReadableResolutionWithoutTakingOversizedSheets() {
        val huge=format(640).put("height",640).put("rows",20).put("columns",20)
        assertEquals(320,parseSaveRangeStoryboard(root(format(80),huge,format(320),format(160)))!!.width)
        assertEquals(160,parseSaveRangeStoryboard(root(format(160),format(80)))!!.width)
    }
    @Test fun rejectsAudioVideoFormatsUnknownLengthAndLiveSources() {
        assertNull(parseSaveRangeStoryboard(root(format().put("protocol","https"))))
        for(duration in listOf(0,-1,"NaN",JSONObject.NULL)) assertNull(parseSaveRangeStoryboard(root(format()).put("duration",duration)))
        assertNull(parseSaveRangeStoryboard(root(format()).put("is_live",true)))
        assertNull(parseSaveRangeStoryboard(root(format()).put("live_status","is_upcoming")))
    }
    @Test fun rejectsUnusableGeometryFpsMissingFragmentsAndUncoveredTail() {
        for((key,value) in listOf("rows" to 0,"columns" to 21,"width" to -1,"height" to 10000,"fps" to 0))
            assertNull(key,parseSaveRangeStoryboard(root(format().put(key,value))))
        assertNull(parseSaveRangeStoryboard(root(format().put("fps","NaN"))))
        assertNull(parseSaveRangeStoryboard(root(format().put("fragments",JSONArray()))))
        val short=format().apply { getJSONArray("fragments").remove(2) }
        assertNull(parseSaveRangeStoryboard(root(short)))
        assertNull(parseSaveRangeStoryboard(root(format().put("fps",100))))
    }
    @Test fun onlyImageHttpUrlsAreResolvedAndSignedParametersArePreserved() {
        for(url in listOf("file:///private/image.jpg","content://private/image","https://name:secret@example.com/image","https://images.example.com/\$M.jpg")) {
            val f=format().apply { getJSONArray("fragments").getJSONObject(0).put("url",url) }
            assertNull(url,parseSaveRangeStoryboard(root(f)))
        }
        val f=format().apply { getJSONArray("fragments").getJSONObject(0).put("url","0.jpg?sigh=test\$signature%2Fvalue&x=1") }
        assertEquals("https://images.example.com/boards/0.jpg?sigh=test\$signature%2Fvalue&x=1",parseSaveRangeStoryboard(root(f))!!.tile(0).url)
    }
    @Test fun missingFragmentDurationUsesFrameCadenceAndLastSheetIsClamped() {
        val f=format().apply { getJSONArray("fragments").getJSONObject(2).remove("duration") }
        val board=parseSaveRangeStoryboard(root(f))!!
        assertEquals(1,board.sheets.last().frames)
        assertEquals(2.0,board.sheets.last().duration,0.0)
    }
}
