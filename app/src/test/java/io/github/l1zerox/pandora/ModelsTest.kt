package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class ModelsTest {
    @Test fun removingCheckedVideosPreservesUncheckedFilesAndSettings() {
        val selected=Video("a","https://example.com/a","A",status="完了",savedUri="content://media/1")
        val retained=Video("b","https://example.com/b","B",selected=false,savedUri="content://media/2")
        val state=ScreenState(videos=listOf(selected,retained),preferences=Preferences(format="MP3"))
        val result=state.withoutSelectedVideos()
        assertEquals(listOf(retained),result.videos)
        assertEquals(state.preferences,result.preferences)
        assertEquals("content://media/2",result.videos.single().savedUri)
        assertEquals("1件を一覧から削除しました。",result.message)
        assertEquals(result,result.withoutSelectedVideos())
    }
    @Test fun removingCheckedVideosCannotModifyActiveQueue() {
        val state=ScreenState(videos=listOf(Video("a","https://example.com/a","A")),busy=true)
        assertSame(state,state.withoutSelectedVideos())
    }
    @Test fun urlsAreSeparatedAndDeduplicated() {
        assertEquals(listOf("https://example.com/a","https://example.com/b"), validUrls(" https://example.com/a\nhttps://example.com/b\nhttps://example.com/a "))
    }
    @Test fun rejectsInvalidAndCredentialUrls() {
        listOf("file:///tmp/a","https://name:password@example.com/a","not a url").forEach {
            assertThrows(IllegalArgumentException::class.java) { validUrls(it) }
        }
    }
    @Test fun durationHandlesUnknownAndHours() {
        assertEquals("時間不明", durationText(null))
        assertEquals("時間不明", durationText(Double.NaN))
        assertEquals("4:24",durationText(264.0))
        assertEquals("1:01:01",durationText(3661.5))
    }
    @Test fun selectorsRespectQualityAndAudio() {
        assertEquals("bestvideo[height<=720]+bestaudio/best[height<=720]",formatSelector(Preferences(quality="720p以下")))
        assertEquals("bestaudio/best",formatSelector(Preferences(format="MP3")))
        assertEquals("bestvideo+bestaudio/best",formatSelector(Preferences(quality="最高")))
    }
    @Test fun fileNamesAreSafeAndBounded() {
        assertFalse(safeName("a/b:c\nvideo").contains('/'))
        assertEquals(150,safeName("あ".repeat(200)).length)
        assertEquals("video",safeName("  "))
    }
}
