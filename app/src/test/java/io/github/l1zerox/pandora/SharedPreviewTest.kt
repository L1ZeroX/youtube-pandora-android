package io.github.l1zerox.pandora

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SharedPreviewTest {
    private val video=appendSharedVideos(emptyList(),"https://youtu.be/AbCd_12-345").single()
    private val preview=SharedPreview("Shared video title","https://i.ytimg.com/vi/AbCd_12-345/hqdefault.jpg")

    @Test fun queueArrivalStartsPreviewAndRepeatedUpdatesDoNotDuplicateRequests() = runBlocking {
        val release=CompletableDeferred<Unit>()
        var calls=0
        var result: SharedPreview? = null
        val loader=SharedPreviewLoader(this,{ calls++; release.await(); preview },{ _,p -> result=p })
        loader.update(listOf(video)); yield()
        loader.update(listOf(video)); yield()
        assertEquals(1,calls)
        release.complete(Unit); yield()
        assertEquals(preview,result)
        loader.update(listOf(video)); yield()
        assertEquals(1,calls)
    }

    @Test fun transientFailureRetriesWithoutUserInteraction() = runBlocking {
        var calls=0
        var result: SharedPreview? = null
        val loader=SharedPreviewLoader(this,{
            if(++calls < 3) error("offline")
            preview
        },{ _,p -> result=p },retryDelay=0)
        loader.update(listOf(video)); yield()
        assertEquals(3,calls)
        assertEquals(preview,result)
    }

    @Test fun persistentFailureStopsAfterThreeAttemptsAndDoesNotChangeQueue() = runBlocking {
        var calls=0
        var failures=0
        val loader=SharedPreviewLoader(this,{ calls++; error("offline") },{ _,_ -> fail("Must not apply") },
            { failures++ },retryDelay=0)
        loader.update(listOf(video)); yield()
        repeat(5) { loader.update(listOf(video)); yield() }
        assertEquals(3,calls)
        assertEquals(1,failures)
    }

    @Test fun deletingQueueEntryCancelsPendingPreview() = runBlocking {
        val release=CompletableDeferred<Unit>()
        var applied=false
        val loader=SharedPreviewLoader(this,{ release.await(); preview },{ _,_ -> applied=true })
        loader.update(listOf(video)); yield()
        loader.update(emptyList()); release.complete(Unit); yield()
        assertFalse(applied)
    }

    @Test fun previewPreservesActiveDownloadAndFullResolutionStillRunsAtDownloadTime() {
        val active=video.copy(selected=false,status="一時停止",savedUri="content://saved",error="existing")
        val state=ScreenState(videos=listOf(active),busy=true,current=active.key,operation="DOWNLOAD",
            paused=true,progress=.4f,speed="1 MiB/s",eta=10.0)
        val updated=state.withSharedPreview(video,preview)
        assertEquals(state.copy(videos=updated.videos),updated)
        assertEquals(active.copy(title=preview.title,thumbnail=preview.thumbnail,site="YouTube"),updated.videos.single())
        assertFalse(updated.videos.single().resolved)
    }

    @Test fun lateResultCannotResurrectDeletedVideoOrOverwriteResolvedMetadata() {
        assertTrue(ScreenState().withSharedPreview(video,preview).videos.isEmpty())
        val resolved=video.copy(title="Full title",duration=123.0,resolved=true)
        val state=ScreenState(videos=listOf(resolved))
        assertEquals(state,state.withSharedPreview(video,preview))
        val replaced=ScreenState(videos=listOf(video.copy(url="https://youtu.be/ZyXw_98-765")))
        assertEquals(replaced,replaced.withSharedPreview(video,preview))
    }
}
