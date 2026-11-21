package io.github.l1zerox.pandora

import android.os.Bundle
import android.os.Process
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

@androidx.annotation.OptIn(UnstableApi::class)
class MusicShuffleSessionTest {
    @Test fun ownedSessionCommandSwitchesScopeWithoutSeekingAndRejectsForeignOrStaleRequests() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val a=LibraryEntry("content://shuffle-test/a","A","audio/mpeg",album="One",track=1)
        val b=a.copy(uri="content://shuffle-test/b",title="B",track=2)
        val c=a.copy(uri="content://shuffle-test/c",title="C",album="Two")
        val entries=listOf(a,b,c,a.copy(uri="content://shuffle-test/video",mime="video/mp4"))
        var loads=0
        var loadWait: CompletableDeferred<Unit>?=null
        var entered: CountDownLatch?=null
        val callback=CarLibraryCallback(context,{loads++; entered?.countDown(); loadWait?.await(); entries},{LibraryCollections()})
        val own=MediaSession.ControllerInfo.createTestOnlyControllerInfo(context.packageName,Process.myPid(),Process.myUid(),0,0,true,Bundle.EMPTY,true)
        val foreign=MediaSession.ControllerInfo.createTestOnlyControllerInfo("test.foreign",1,Process.myUid()+1,0,0,true,Bundle.EMPTY,true)
        val command=SessionCommand(MUSIC_SHUFFLE_COMMAND,Bundle.EMPTY)
        lateinit var player: ExoPlayer
        lateinit var session: MediaLibrarySession
        fun <T> result(block: ()->ListenableFuture<T>): T {
            lateinit var future: ListenableFuture<T>
            instrumentation.runOnMainSync { future=block() }
            return future.get(15,TimeUnit.SECONDS)
        }
        instrumentation.runOnMainSync {
            player=ExoPlayer.Builder(context).build().apply { volume=0f }
            session=MediaLibrarySession.Builder(context,player,callback).setId("shuffle-${System.nanoTime()}").build()
            player.setMediaItems(listOf(a.mediaItem(),b.mediaItem()),1,12_345)
            player.repeatMode=Player.REPEAT_MODE_ONE
        }
        try {
            assertTrue(result { callback.onConnectAsync(session,own) }.availableSessionCommands.contains(command))
            assertFalse(result { callback.onConnectAsync(session,foreign) }.availableSessionCommands.contains(command))
            fun send(scope: MusicShuffleScope, uri: String = b.uri, caller: MediaSession.ControllerInfo = own)=result {
                callback.onCustomCommand(session,caller,command,Bundle().apply { putString("scope",scope.name); putString("uri",uri) })
            }.resultCode
            assertEquals(SessionError.ERROR_PERMISSION_DENIED,send(MusicShuffleScope.ALL,caller=foreign))
            assertEquals(0,loads)
            assertEquals(SessionResult.RESULT_SUCCESS,send(MusicShuffleScope.ALL))
            instrumentation.runOnMainSync {
                assertEquals(3,player.mediaItemCount); assertEquals(b.uri,player.currentMediaItem!!.mediaId)
                assertEquals(12_345L,player.currentPosition); assertFalse(player.playWhenReady)
                assertEquals(Player.REPEAT_MODE_ONE,player.repeatMode); assertTrue(player.shuffleModeEnabled)
                assertEquals(MusicShuffleScope.ALL.name,player.currentMediaItem!!.mediaMetadata.extras!!.getString(MUSIC_SHUFFLE_SCOPE))
            }
            assertEquals(SessionResult.RESULT_SUCCESS,send(MusicShuffleScope.ALBUM))
            instrumentation.runOnMainSync {
                assertEquals(listOf(a.uri,b.uri),(0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
                assertEquals(12_345L,player.currentPosition); assertEquals(1,player.currentMediaItemIndex)
                assertEquals(MusicShuffleScope.ALBUM.name,player.currentMediaItem!!.mediaMetadata.extras!!.getString(MUSIC_SHUFFLE_SCOPE))
            }
            assertEquals(SessionError.ERROR_BAD_VALUE,send(MusicShuffleScope.ALL,uri=a.uri))
            val before=loads
            assertEquals(SessionResult.RESULT_SUCCESS,send(MusicShuffleScope.OFF)); assertEquals(before,loads)
            instrumentation.runOnMainSync { assertFalse(player.shuffleModeEnabled); assertEquals(2,player.mediaItemCount) }
            val gate=CompletableDeferred<Unit>(); val loading=CountDownLatch(1)
            loadWait=gate; entered=loading
            lateinit var pending: ListenableFuture<SessionResult>
            instrumentation.runOnMainSync {
                pending=callback.onCustomCommand(session,own,command,Bundle().apply {
                    putString("uri",b.uri); putString("scope",MusicShuffleScope.ALL.name)
                })
            }
            assertTrue(loading.await(5,TimeUnit.SECONDS))
            assertEquals(SessionResult.RESULT_SUCCESS,send(MusicShuffleScope.OFF))
            gate.complete(Unit)
            assertEquals(SessionError.INFO_CANCELLED,pending.get(15,TimeUnit.SECONDS).resultCode)
            instrumentation.runOnMainSync { assertFalse(player.shuffleModeEnabled); assertEquals(2,player.mediaItemCount) }
        } finally { instrumentation.runOnMainSync { callback.close(); session.release(); player.release() } }
    }
}
