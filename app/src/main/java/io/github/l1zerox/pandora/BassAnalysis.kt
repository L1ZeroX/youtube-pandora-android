package io.github.l1zerox.pandora

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class BassPcm(val bytes: Int, val bigEndian: Boolean = false) {
    U8(1), S16(2), S24(3), S32(4), F32(4), F64(8),
    S16_BE(2,true), S24_BE(3,true), S32_BE(4,true), F32_BE(4,true), F64_BE(8,true);
    fun read(buffer: ByteBuffer): Double = when(this) {
        U8 -> ((buffer.get().toInt() and 255)-128)/128.0
        S16,S16_BE -> buffer.short/32768.0
        S24,S24_BE -> {
            val a=buffer.get().toInt() and 255; val b=buffer.get().toInt() and 255; val c=buffer.get().toInt() and 255
            val value=if(bigEndian) (a shl 16) or (b shl 8) or c else a or (b shl 8) or (c shl 16)
            ((value shl 8) shr 8)/8388608.0
        }
        S32,S32_BE -> buffer.int/2147483648.0
        F32,F32_BE -> buffer.float.toDouble()
        F64,F64_BE -> buffer.double
    }.let { if(it.isFinite()) it.coerceIn(-1.0,1.0) else 0.0 }
}

internal data class BassPoint(val timeUs: Long, val level: Float)

/** Two cascaded 100 Hz band-pass filters. Average energy per channel avoids stereo cancellation. */
internal class BassDetector(private val sampleRate: Int, private val channels: Int, private val encoding: BassPcm) {
    init { require(sampleRate in 1_000..384_000 && channels in 1..32) }
    private val alpha=sin(2*PI*100/sampleRate)/(2*2.0)
    private val b0=alpha/(1+alpha)
    private val a1=-2*cos(2*PI*100/sampleRate)/(1+alpha)
    private val a2=(1-alpha)/(1+alpha)
    private val z1=DoubleArray(channels); private val z2=DoubleArray(channels)
    private val z3=DoubleArray(channels); private val z4=DoubleArray(channels)
    private val window=(sampleRate/25).coerceAtLeast(1)
    val frameBytes=channels*encoding.bytes
    private var count=0; private var energy=0.0
    fun reset() { z1.fill(0.0); z2.fill(0.0); z3.fill(0.0); z4.fill(0.0); count=0; energy=0.0 }
    fun read(source: ByteBuffer, timeUs: Long, emit: (BassPoint)->Unit) {
        val buffer=source.asReadOnlyBuffer().order(if(encoding.bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        var frames=0L
        while(buffer.remaining() >= frameBytes) {
            for(channel in 0 until channels) {
                val x=encoding.read(buffer)
                val first=b0*x+z1[channel]
                z1[channel]=-a1*first+z2[channel]; z2[channel]=-b0*x-a2*first
                val out=b0*first+z3[channel]
                z3[channel]=-a1*out+z4[channel]; z4[channel]=-b0*first-a2*out
                energy+=out*out
            }
            frames++; count++
            if(count >= window) {
                val rms=sqrt(energy/(count*channels))
                val level=if(rms <= .0001) 0f else ((20*log10(rms)+55)/43).toFloat().coerceIn(0f,1f)
                emit(BassPoint(timeUs+frames*1_000_000/sampleRate,level))
                count=0; energy=0.0
            }
        }
    }
}

/** Samples can arrive ahead of the sound. Expose only the window reached by the audio clock. */
internal class BassTimeline(private val limit: Int = 256) {
    private val points=ArrayDeque<BassPoint>()
    private var current=BassPoint(Long.MIN_VALUE,0f)
    private var position=Long.MIN_VALUE
    fun clear() { points.clear(); current=BassPoint(Long.MIN_VALUE,0f); position=Long.MIN_VALUE }
    fun add(point: BassPoint) {
        if(points.lastOrNull()?.let { it.timeUs > point.timeUs } == true) clear()
        points.addLast(point)
        while(points.size > limit) points.removeFirst()
    }
    fun levelAt(timeUs: Long): Float {
        if(position != Long.MIN_VALUE && timeUs < position-100_000) { clear(); return 0f }
        position=timeUs
        while(points.isNotEmpty() && points.first().timeUs <= timeUs) current=points.removeFirst()
        return if(current.timeUs == Long.MIN_VALUE || timeUs-current.timeUs > 200_000) 0f else current.level
    }
}

internal fun bassCycleMillis(level: Float): Int =
    (4_000/(1+9*(if(level.isFinite()) level.coerceIn(0f,1f) else 0f))).toInt()
