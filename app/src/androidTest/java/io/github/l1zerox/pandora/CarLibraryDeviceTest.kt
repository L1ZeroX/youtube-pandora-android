package io.github.l1zerox.pandora

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Process
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaConstants
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.UUID

@androidx.annotation.OptIn(UnstableApi::class)
class CarLibraryDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun <T> result(block: ()->ListenableFuture<T>): T {
        var future: ListenableFuture<T>?=null
        instrumentation.runOnMainSync { future=block() }
        return future!!.get(15,TimeUnit.SECONDS)
    }
    @Test fun carDiscoveryAndArtworkProviderAreDeclaredWithoutExportingThePlayerActivity() {
        val pm=context.packageManager
        for(action in listOf("androidx.media3.session.MediaLibraryService","android.media.browse.MediaBrowserService")) {
            val services=pm.queryIntentServices(Intent(action).setPackage(context.packageName),0)
            assertTrue(services.any { it.serviceInfo.name == PlaybackService::class.java.name && it.serviceInfo.exported })
        }
        val app=pm.getApplicationInfo(context.packageName,PackageManager.GET_META_DATA)
        assertTrue(app.metaData.getInt("com.google.android.gms.car.application") != 0)
        assertTrue(pm.queryIntentActivities(Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").setPackage(context.packageName),0)
            .any { it.activityInfo.name == VoiceSearchActivity::class.java.name })
        val provider=pm.getProviderInfo(ComponentName(context,CarArtworkProvider::class.java),0)
        assertFalse(provider.exported); assertTrue(provider.grantUriPermissions)
        assertFalse(pm.getActivityInfo(ComponentName(context,PlayerActivity::class.java),0).exported)
    }
    @Test fun browserIdsResolveOnlyKnownAudioAndKeepPhonePlaybackCompatible() {
        val a=LibraryEntry("content://car-fixture/a","A","audio/mpeg",durationMs=60_000,album="Album",track=1)
        val b=a.copy(uri="content://car-fixture/b",title="B",track=2)
        val video=a.copy(uri="content://car-fixture/video",mime="video/mp4",title="Video")
        val entries=listOf(a,b,video)
        val catalog=CarMusicCatalog(entries,LibraryCollections())
        lateinit var player: ExoPlayer
        lateinit var callback: CarLibraryCallback
        lateinit var session: MediaLibraryService.MediaLibrarySession
        val external=MediaSession.ControllerInfo.createTestOnlyControllerInfo("test.car",1,Process.myUid()+1,0,0,true,Bundle.EMPTY,true)
        val denied=MediaSession.ControllerInfo.createTestOnlyControllerInfo("test.untrusted",1,Process.myUid()+2,0,0,false,Bundle.EMPTY,true)
        val own=MediaSession.ControllerInfo.createTestOnlyControllerInfo(context.packageName,Process.myPid(),Process.myUid(),0,0,true,Bundle.EMPTY,true)
        instrumentation.runOnMainSync {
            player=ExoPlayer.Builder(context).build().apply { volume=0f }
            callback=CarLibraryCallback(context,{entries},{LibraryCollections()})
            session=MediaLibraryService.MediaLibrarySession.Builder(context,player,callback).setId("car-test-${System.nanoTime()}").build()
        }
        try {
            assertTrue(result { callback.onConnectAsync(session,external) }.isAccepted)
            assertFalse(result { callback.onConnectAsync(session,denied) }.isAccepted)
            val root=result { callback.onGetLibraryRoot(session,external,null) }
            assertEquals(CAR_ROOT,root.value!!.mediaId); assertEquals(true,root.value!!.mediaMetadata.isBrowsable)
            assertTrue(root.params!!.extras.getBoolean("android.media.browse.SEARCH_SUPPORTED"))
            val top=result { callback.onGetChildren(session,external,CAR_ROOT,0,80,null) }.value!!
            assertEquals(4,top.size); assertEquals(CAR_ALBUMS,top.first().mediaId)
            assertEquals(2,result { callback.onGetSearchResult(session,external,"Album",0,80,null) }.value!!.size)
            val album=catalog.children(CAR_ALBUMS,0,80)!!.single()
            val albumItem=result { callback.onGetItem(session,external,album.id) }.value!!
            assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,albumItem.mediaMetadata.extras!!.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM))
            val rows=result { callback.onGetChildren(session,external,album.id,0,80,null) }.value!!
            assertEquals(listOf("A","B"),rows.map { it.mediaMetadata.title.toString() })
            assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,rows.first().mediaMetadata.extras!!.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM))
            val selected=catalog.children(album.id,0,80)!![1]
            val request=MediaItem.Builder().setMediaId(selected.id).setUri("https://example.invalid/arbitrary.mp4").build()
            val queue=result { callback.onSetMediaItems(session,external,listOf(request),0,5000) }
            assertEquals(listOf(a.uri,b.uri),queue.mediaItems.map { it.mediaId })
            assertEquals(b.uri,queue.mediaItems[1].localConfiguration!!.uri.toString())
            assertEquals(1,queue.startIndex); assertEquals(5000L,queue.startPositionMs)
            val voice=MediaItem.Builder().setMediaId("car:search")
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("Album").build()).build()
            assertEquals(listOf(a.uri,b.uri),result { callback.onSetMediaItems(session,own,listOf(voice),0,0) }.mediaItems.map { it.mediaId })
            assertTrue(queue.mediaItems.all { it.mediaMetadata.artworkUri!!.scheme == "content" && it.mediaMetadata.extras!!.getBoolean("video") == false })
            try {
                result { callback.onSetMediaItems(session,external,listOf(MediaItem.Builder().setMediaId(video.uri).build()),0,0) }
                fail("Car requests must not resolve video or arbitrary URLs")
            } catch(expected: ExecutionException) { assertTrue(expected.cause is IllegalStateException) }
            val phone=MediaItem.Builder().setMediaId(video.uri).setUri(video.uri).build()
            assertSame(phone,result { callback.onSetMediaItems(session,own,listOf(phone),0,0) }.mediaItems.single())
        } finally { instrumentation.runOnMainSync { callback.close(); session.release(); player.release() } }
    }
    @Test fun customArtworkIsReadableAsABoundedImageAndCannotBeOpenedForWriting() {
        val uri="content://car-artwork-test/${UUID.randomUUID()}"
        val entry=LibraryEntry(uri,"Cover","audio/mpeg")
        val key=trackArtworkKey(uri)
        val source=Bitmap.createBitmap(800,800,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        try {
            LibraryArtwork.set(key,saveCroppedArtwork(context,source,ArtworkCrop()))
            val cover=carArtworkUri(context,entry,null,context.packageName)
            val decoded=context.contentResolver.openInputStream(cover)!!.use { BitmapFactory.decodeStream(it) }!!
            assertTrue(decoded.width <= 256 && decoded.height <= 256)
            assertTrue(android.graphics.Color.blue(decoded.getPixel(0,0)) > 200); decoded.recycle()
            try { context.contentResolver.openFileDescriptor(cover,"w")?.close(); fail("Artwork provider must be read-only") }
            catch(expected: java.io.FileNotFoundException) { /* expected */ }
            val changedQuery=cover.buildUpon().appendQueryParameter("source","content://not-the-requested-song").build()
            val same=context.contentResolver.openInputStream(changedQuery)!!.use { BitmapFactory.decodeStream(it) }!!
            assertTrue(android.graphics.Color.blue(same.getPixel(0,0)) > 200); same.recycle()
        } finally { LibraryArtwork.set(key,null); source.recycle() }
    }
}
