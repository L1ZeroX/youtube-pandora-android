package io.github.l1zerox.pandora

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer

internal object BassRuntime {
    @Volatile var enabled=false
    val level=MutableStateFlow(0f)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class BassRenderersFactory(context: Context): DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink? =
        super.buildAudioSink(context,enableFloatOutput,enableAudioOutputPlaybackParams)?.let { BassAudioSink(it) }
}

/** Observes a read-only view. All original bytes, timestamps, return values and audio options pass through. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class BassAudioSink(delegate: AudioSink, private val enabled: ()->Boolean = { BassRuntime.enabled },
    private val publish: (Float)->Unit = { BassRuntime.level.value=it }): ForwardingAudioSink(delegate) {
    private var detector: BassDetector?=null
    private val timeline=BassTimeline()
    private var active=false
    private var playing=false
    private var failed=false
    private var buffer: ByteBuffer?=null
    private var bufferTime=Long.MIN_VALUE
    override fun configure(config: AudioSink.AudioSinkConfig) {
        super.configure(config)
        val format=config.format
        val encoding=when(format.pcmEncoding) {
            C.ENCODING_PCM_8BIT -> BassPcm.U8; C.ENCODING_PCM_16BIT -> BassPcm.S16
            C.ENCODING_PCM_24BIT -> BassPcm.S24; C.ENCODING_PCM_32BIT -> BassPcm.S32
            C.ENCODING_PCM_FLOAT -> BassPcm.F32; C.ENCODING_PCM_DOUBLE -> BassPcm.F64
            C.ENCODING_PCM_16BIT_BIG_ENDIAN -> BassPcm.S16_BE; C.ENCODING_PCM_24BIT_BIG_ENDIAN -> BassPcm.S24_BE
            C.ENCODING_PCM_32BIT_BIG_ENDIAN -> BassPcm.S32_BE; C.ENCODING_PCM_FLOAT_BIG_ENDIAN -> BassPcm.F32_BE
            C.ENCODING_PCM_DOUBLE_BIG_ENDIAN -> BassPcm.F64_BE; else -> null
        }
        detector=if(format.sampleMimeType == MimeTypes.AUDIO_RAW && encoding != null && format.sampleRate in 1_000..384_000 && format.channelCount in 1..32)
            BassDetector(format.sampleRate,format.channelCount,encoding) else null
        clear()
    }
    private fun clear() { detector?.reset(); timeline.clear(); buffer=null; bufferTime=Long.MIN_VALUE; failed=false; publish(0f) }
    private fun sync() {
        val next=enabled()
        if(next != active) { active=next; clear() }
    }
    override fun handleBuffer(input: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        sync()
        val reader=detector
        if(active && !failed && reader != null && presentationTimeUs != C.TIME_UNSET &&
            (buffer !== input || bufferTime != presentationTimeUs)) {
            buffer=input; bufferTime=presentationTimeUs
            runCatching { reader.read(input,presentationTimeUs,timeline::add) }.onFailure { failed=true; timeline.clear(); publish(0f) }
        }
        return super.handleBuffer(input,presentationTimeUs,encodedAccessUnitCount)
    }
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val time=super.getCurrentPositionUs(sourceEnded)
        sync()
        publish(if(active && playing && time != AudioSink.CURRENT_POSITION_NOT_SET) timeline.levelAt(time) else 0f)
        return time
    }
    override fun play() { playing=true; super.play() }
    override fun pause() { playing=false; publish(0f); super.pause() }
    override fun flush() { clear(); super.flush() }
    override fun handleDiscontinuity() { clear(); super.handleDiscontinuity() }
    override fun reset() { playing=false; clear(); super.reset() }
    override fun release() { playing=false; clear(); super.release() }
}
