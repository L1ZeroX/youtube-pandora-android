package io.github.l1zerox.pandora

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

@androidx.annotation.OptIn(UnstableApi::class)
class BassAudioSinkTest {
    private class Output {
        var clock=0L; var take=Int.MAX_VALUE; var calls=0; var timestamp=0L; var units=0
        val sink=Proxy.newProxyInstance(AudioSink::class.java.classLoader,arrayOf(AudioSink::class.java)) { _,method,args ->
            when(method.name) {
                "getCurrentPositionUs" -> clock
                "handleBuffer" -> {
                    val buffer=args[0] as ByteBuffer; timestamp=args[1] as Long; units=args[2] as Int; calls++
                    buffer.position(buffer.position()+minOf(take,buffer.remaining())); !buffer.hasRemaining()
                }
                else -> when(method.returnType) { Boolean::class.javaPrimitiveType -> false; Int::class.javaPrimitiveType -> 0; Long::class.javaPrimitiveType -> 0L; else -> null }
            }
        } as AudioSink
    }
    private fun config(mime: String = MimeTypes.AUDIO_RAW)=AudioSink.AudioSinkConfig.Builder(
        Format.Builder().setSampleMimeType(mime).setSampleRate(48_000).setChannelCount(1).setPcmEncoding(C.ENCODING_PCM_16BIT).build()).build()

    @Test fun passThroughPreservesBytesPositionsTimestampsAndRetriesWithoutDoubleCounting() {
        val output=Output(); var level=0f
        val sink=BassAudioSink(output.sink,{true},{level=it}); sink.configure(config()); sink.play()
        val data=bassTone(); val original=data.array().clone(); val pts=1_000_000_000_000L
        output.take=data.remaining()/2
        assertFalse(sink.handleBuffer(data,pts,7)); assertEquals(original.size/2,data.position())
        output.clock=pts+20_000; assertEquals(output.clock,sink.getCurrentPositionUs(false)); assertEquals(0f,level,0f)
        output.clock=pts+200_000; sink.getCurrentPositionUs(false); assertTrue(level > .8f)
        assertTrue(sink.handleBuffer(data,pts,7)); assertEquals(2,output.calls)
        output.clock=pts+800_000; sink.getCurrentPositionUs(false); assertTrue(level > .8f)
        assertEquals(pts,output.timestamp); assertEquals(7,output.units); assertArrayEquals(original,data.array())
    }
    @Test fun pauseDisableFlushAndReenableClearOldLevelsAndKeepAudioRunning() {
        val output=Output(); var level=0f; var enabled=true
        val sink=BassAudioSink(output.sink,{enabled},{level=it}); sink.configure(config()); sink.play()
        sink.handleBuffer(bassTone(),0,1); output.clock=200_000; sink.getCurrentPositionUs(false); assertTrue(level > .8f)
        sink.pause(); sink.getCurrentPositionUs(false); assertEquals(0f,level,0f)
        sink.play(); sink.getCurrentPositionUs(false); assertTrue(level > .8f)
        enabled=false; sink.getCurrentPositionUs(false); assertEquals(0f,level,0f)
        assertTrue(sink.handleBuffer(bassTone(),1_000_000,1)); assertEquals(2,output.calls)
        enabled=true; sink.handleBuffer(bassTone(),2_000_000,1); output.clock=2_200_000; sink.getCurrentPositionUs(false); assertTrue(level > .8f)
        sink.flush(); sink.getCurrentPositionUs(false); assertEquals(0f,level,0f)
    }
    @Test fun encodedOrUnsupportedInputIsForwardedWithoutPretendingToMeasureBass() {
        val output=Output(); var level=1f
        val sink=BassAudioSink(output.sink,{true},{level=it}); sink.configure(config(MimeTypes.AUDIO_AAC)); sink.play()
        val data=ByteBuffer.wrap(byteArrayOf(1,2,3,4)); assertTrue(sink.handleBuffer(data,0,1))
        output.clock=100_000; sink.getCurrentPositionUs(false); assertEquals(0f,level,0f)
        assertEquals(1,output.calls); assertArrayEquals(byteArrayOf(1,2,3,4),data.array())
    }
}
