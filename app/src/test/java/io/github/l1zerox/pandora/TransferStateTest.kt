package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

class TransferStateTest {
    @Test fun publishedDownloadsLeaveQueueWithoutRemovingUnfinishedItemsOrCounters() {
        val saved=Video("saved","https://example.com/saved","Saved",status="完了",savedUri="content://media/1")
        val pending=listOf("待機中","失敗","一時停止","キャンセル","変換中").mapIndexed { i,status ->
            Video("item$i","https://example.com/$i",status,status=status)
        }
        val state=ScreenState(videos=listOf(saved)+pending,busy=true,current=saved.key,done=1,total=6,operation="DOWNLOAD")
        val result=state.withoutSavedDownloads()
        assertEquals(pending,result.videos); assertEquals("",result.current)
        assertTrue(result.busy); assertEquals(1,result.done); assertEquals(6,result.total)
        assertEquals(state.preferences,result.preferences)
    }
    @Test fun unconfirmedCompletionAndCurrentUnfinishedWorkArePreserved() {
        val unconfirmed=Video("unknown","https://example.com/unknown","Unknown",status="完了")
        val active=Video("active","https://example.com/active","Active",status="ダウンロード中")
        val saved=Video("saved","https://example.com/saved","Saved",status="完了",savedUri="content://media/1")
        val state=ScreenState(videos=listOf(unconfirmed,active,saved),current=active.key,busy=true)
        val result=state.withoutSavedDownloads()
        assertEquals(listOf(unconfirmed,active),result.videos); assertEquals(active.key,result.current)
        assertSame(result,result.withoutSavedDownloads())
    }
    @Test fun batchContinuesAfterSuccessfulRowsDisappearAndDoesNotRepeatTheirKeys() {
        val a=Video("a","https://example.com/a","A")
        val b=Video("b","https://example.com/b","B")
        val batch=DownloadBatch(Preferences(),listOf(a,b),false)
        batch.attempted.add(a.key)
        val queue=ScreenState(videos=listOf(a.copy(status="完了",savedUri="content://media/1"),b)).withoutSavedDownloads()
        assertEquals(listOf(b),batch.remaining(queue.videos))
        assertEquals(listOf(b),batch.remaining(queue.videos+a))
        batch.attempted.add(b.key)
        assertTrue(batch.remaining(emptyList()).isEmpty()); assertEquals(2,batch.attempted.size)
    }
    @Test fun cancelWinsOverPauseAndCheckStopsTheWorker() {
        val control=TransferControl()
        control.check(); assertFalse(control.stopped)
        control.pause(); assertTrue(control.paused)
        assertThrows(CancellationException::class.java) { control.check() }
        control.cancel(); control.pause()
        assertFalse(control.paused); assertTrue(control.stopped)
        assertThrows(CancellationException::class.java) { control.check() }
    }
    @Test fun resumedWorkUsesSameDirectoryButChangedOutputSettingsDoNot() {
        val root=File("cache")
        val video=Video("key","https://example.com/video","Title")
        val path=transferWorkspace(root,video,Preferences())
        assertEquals(path,transferWorkspace(root,video.copy(title="New title",status="一時停止"),Preferences(dark=false)))
        assertEquals(root,path.parentFile)
        assertTrue(path.name.matches(Regex("transfer-[0-9a-f]{64}")))
        for(p in listOf(Preferences(format="MP3"),Preferences(fps="60fps"),Preferences(quality="720p以下")))
            assertNotEquals(path,transferWorkspace(root,video,p))
    }
    @Test fun activeBatchPicksNewSharesAndDoesNotRepeatCompletedOrFailedAttempts() {
        val a=Video("a","https://example.com/a","A")
        val b=Video("b","https://example.com/b","B",selected=false)
        val batch=DownloadBatch(Preferences(),listOf(a,b),false)
        assertEquals(listOf(a),batch.remaining(listOf(a,b)))
        batch.attempted.add(a.key)
        val videos=appendSharedVideos(listOf(a.copy(status="完了"),b),"https://example.com/new")
        assertEquals(listOf(videos.last()),batch.remaining(videos))
        batch.attempted.add(videos.last().key)
        assertTrue(batch.remaining(videos.map { it.copy(status="失敗") }).isEmpty())
    }
    @Test fun retryRemainsResumableAfterStatusChangesAndAcceptsNewShares() {
        val failed=Video("a","https://example.com/a","A",status="失敗")
        val waiting=Video("b","https://example.com/b","B")
        val batch=DownloadBatch(Preferences(),listOf(failed,waiting),true)
        val paused=failed.copy(status="一時停止")
        assertEquals(listOf(paused),batch.remaining(listOf(paused,waiting)))
        val shared=appendSharedVideos(listOf(paused,waiting),"https://example.com/c")
        assertEquals(listOf(paused,shared.last()),batch.remaining(shared))
    }
    @Test fun parsesKnownAndEstimatedByteProgressWithoutGuessingUnknownValues() {
        val value=transferProgress("PANDORA_PROGRESS|downloading|1048576|4194304|NA|524288|6")!!
        assertEquals(.25f,value.fraction!!,.0001f)
        assertEquals("512.0 KiB/s",value.speed); assertEquals(6.0,value.eta!!,0.0)
        assertEquals(.5f,transferProgress("PANDORA_PROGRESS|downloading|2|NA|4|NA|NA")!!.fraction!!,.0001f)
        val unknown=transferProgress("PANDORA_PROGRESS|downloading|3|NA|NA|NaN|Infinity")!!
        assertNull(unknown.fraction); assertNull(unknown.eta); assertEquals("",unknown.speed)
        assertEquals(1f,transferProgress("PANDORA_PROGRESS|finished|3|NA|NA|NA|0")!!.fraction!!,0f)
    }
    @Test fun conversionProgressUsesProcessingSpeedAndRemainingDuration() {
        val value=transferProgress("PANDORA_CONVERT|30|120|10")!!
        assertEquals(.25f,value.fraction!!,.0001f); assertEquals("3.00x",value.speed)
        assertEquals(30.0,value.eta!!,.0001)
        assertNull(transferProgress("PANDORA_CONVERT|0|0|0")!!.eta)
        assertNull(transferProgress("PANDORA_PROGRESS|bad"))
        assertNull(transferProgress("random log"))
    }
    @Test fun shareTextAcceptsHttpUrlsAndDeduplicatesYoutubeForms() {
        assertEquals(listOf("https://youtu.be/abc?si=hello"),sharedUrls(
            "動画の共有 https://youtu.be/abc?si=hello\nhttps://www.youtube.com/watch?v=abc&t=12"))
        assertEquals("youtube:abc",mediaIdentity("https://www.youtube.com/shorts/abc"))
        assertEquals("youtube:abc",mediaIdentity("https://m.youtube.com/live/abc"))
        assertEquals(emptyList<String>(),sharedUrls("file:///a https://user:password@example.com/a"))
        assertEquals(listOf("https://example.com/video"),sharedUrls("動画（https://example.com/video）。"))
        assertNotEquals(mediaIdentity("https://evil-youtube.com/watch?v=abc"),"youtube:abc")
    }
    @Test fun sharesPreserveExistingSelectionStateFilesAndResolvedMetadata() {
        val old=Video("youtube:abc","https://www.youtube.com/watch?v=abc","Saved",status="完了",savedUri="content://media/1")
        val result=appendSharedVideos(listOf(old),"https://youtu.be/abc https://example.com/new")
        assertSame(old,result.first()); assertEquals(2,result.size)
        assertFalse(result.last().resolved); assertTrue(result.last().selected)
        assertEquals("content://media/1",result.first().savedUri)
    }
}
