package io.github.l1zerox.pandora

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

internal object SpectrumPreset {
    const val bands=32
    const val bassLow=40.0
    const val bassHigh=160.0
    const val gain=1.7
    const val attack=.020
    const val release=.200
}

internal fun spectrumEnvelope(previous: Float, target: Float, seconds: Double): Float {
    val old=if(previous.isFinite()) previous.coerceIn(0f,1f) else 0f
    val next=if(target.isFinite()) target.coerceIn(0f,1f) else 0f
    if(!seconds.isFinite() || seconds <= 0) return old
    val coefficient=exp(-seconds/(if(next > old) SpectrumPreset.attack else SpectrumPreset.release))
    return (next+(old-next)*coefficient).toFloat().coerceIn(0f,1f)
}

/** A small real spectrum, with energy averaged across channels rather than cancelling L/R. */
internal class SpectrumDetector(private val sampleRate: Int, private val channels: Int, private val encoding: BassPcm): PcmLevelDetector {
    init { require(sampleRate in 1_000..384_000 && channels in 1..32) }
    override val frameBytes=channels*encoding.bytes
    private val rate=min(sampleRate,8000)
    private val usedChannels=min(channels,8)
    private val size=512
    private val hop=(rate/50).coerceAtLeast(1)
    private val history=Array(usedChannels) { DoubleArray(size) }
    private val hann=DoubleArray(size) { .5-.5*cos(2*PI*it/(size-1)) }
    private val normalizer=2.0/(size*hann.sumOf { it*it }*usedChannels)
    private val reverse=IntArray(size) { Integer.reverse(it) ushr 23 }
    private val real=DoubleArray(size); private val imaginary=DoubleArray(size)
    private val powers=DoubleArray(size/2+1)
    private val twiddleCos=DoubleArray(size/2) { cos(-2*PI*it/size) }
    private val twiddleSin=DoubleArray(size/2) { sin(-2*PI*it/size) }
    private val top=min(3000.0,rate*.45)
    private val ratio=(top/SpectrumPreset.bassLow).pow(1.0/(SpectrumPreset.bands-1))
    private val centers=DoubleArray(SpectrumPreset.bands) { SpectrumPreset.bassLow*ratio.pow(it) }
    private val bins=centers.map { center ->
        val low=ceil(max(SpectrumPreset.bassLow,center/sqrt(ratio))*size/rate).toInt().coerceIn(1,size/2)
        val high=floor(min(top,center*sqrt(ratio))*size/rate).toInt().coerceIn(1,size/2)
        if(high >= low) low..high else (center*size/rate).roundToInt().coerceIn(1,size/2).let { it..it }
    }
    private val bassBins=ceil(SpectrumPreset.bassLow*size/rate).toInt().coerceIn(1,size/2)..floor(SpectrumPreset.bassHigh*size/rate).toInt().coerceIn(1,size/2)
    private val levels=FloatArray(SpectrumPreset.bands)
    private var bass=0f
    // Anti-alias before reducing to at most 8 kHz. Two low-pass stages, shared coefficients.
    private val w=2*PI*min(3000.0,sampleRate*.42)/sampleRate
    private val alpha=sin(w)/(2*sqrt(.5))
    private val b0=(1-cos(w))/(2*(1+alpha)); private val b1=2*b0
    private val a1=-2*cos(w)/(1+alpha); private val a2=(1-alpha)/(1+alpha)
    private val z1=DoubleArray(usedChannels); private val z2=DoubleArray(usedChannels)
    private val z3=DoubleArray(usedChannels); private val z4=DoubleArray(usedChannels)
    private val sums=DoubleArray(usedChannels)
    private var phase=0; private var group=0; private var cursor=0; private var count=0; private var since=0; private var ready=false
    override fun reset() {
        history.forEach { it.fill(0.0) }; z1.fill(0.0); z2.fill(0.0); z3.fill(0.0); z4.fill(0.0); sums.fill(0.0)
        levels.fill(0f); bass=0f; phase=0; group=0; cursor=0; count=0; since=0; ready=false
    }
    override fun read(source: ByteBuffer, timeUs: Long, emit: (BassPoint)->Unit) {
        val input=source.asReadOnlyBuffer().order(if(encoding.bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        var frames=0L
        while(input.remaining() >= frameBytes) {
            for(channel in 0 until channels) {
                val sample=encoding.read(input)
                if(channel < usedChannels) {
                    val first=b0*sample+z1[channel]
                    z1[channel]=b1*sample-a1*first+z2[channel]; z2[channel]=b0*sample-a2*first
                    val second=b0*first+z3[channel]
                    z3[channel]=b1*first-a1*second+z4[channel]; z4[channel]=b0*first-a2*second
                    sums[channel]+=second
                }
            }
            frames++; group++; phase+=rate
            if(phase < sampleRate) continue
            phase-=sampleRate
            for(channel in 0 until usedChannels) { history[channel][cursor]=sums[channel]/group; sums[channel]=0.0 }
            group=0; cursor=(cursor+1)%size; count=min(size,count+1); since++
            if(count == size && (!ready || since >= hop)) {
                ready=true; since=0
                emit(analyze(timeUs+frames*1_000_000/sampleRate))
            }
        }
    }
    private fun analyze(timeUs: Long): BassPoint {
        powers.fill(0.0)
        for(channel in 0 until usedChannels) {
            imaginary.fill(0.0)
            for(i in 0 until size) real[reverse[i]]=history[channel][(cursor+i)%size]*hann[i]
            var length=2
            while(length <= size) {
                val half=length/2; val step=size/length
                for(start in 0 until size step length) for(j in 0 until half) {
                    val a=start+j; val b=a+half; val twiddle=j*step
                    val re=real[b]*twiddleCos[twiddle]-imaginary[b]*twiddleSin[twiddle]
                    val im=real[b]*twiddleSin[twiddle]+imaginary[b]*twiddleCos[twiddle]
                    real[b]=real[a]-re; imaginary[b]=imaginary[a]-im; real[a]+=re; imaginary[a]+=im
                }
                length*=2
            }
            for(i in 1..size/2) powers[i]+=real[i]*real[i]+imaginary[i]*imaginary[i]
        }
        fun level(range: IntRange, gain: Double): Float {
            val rms=sqrt(range.sumOf { powers[it] }*normalizer)*gain
            return if(rms <= .00001) 0f else ((20*log10(rms)+65)/56).toFloat().coerceIn(0f,1f)
        }
        val seconds=hop.toDouble()/rate
        for(i in levels.indices) levels[i]=spectrumEnvelope(levels[i],level(bins[i],if(centers[i] <= SpectrumPreset.bassHigh) SpectrumPreset.gain else 1.0),seconds)
        bass=spectrumEnvelope(bass,level(bassBins,SpectrumPreset.gain),seconds)
        return BassPoint(timeUs,bass,levels.toList())
    }
}
