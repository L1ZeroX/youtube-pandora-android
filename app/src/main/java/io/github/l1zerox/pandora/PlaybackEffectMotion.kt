package io.github.l1zerox.pandora

import kotlin.math.abs
import kotlin.math.exp

/** Display-only interpolation. Audio analysis and the slider's time/position never use this clock. */
internal class PlaybackEffectMotion(private val effect: PlaybackEffect, private val reactive: Boolean) {
    val spectrum=FloatArray(SpectrumPreset.bands)
    var bass=0f
        private set
    var phase=0.0
        private set
    var active=false
        private set

    fun clear(): Boolean {
        val changed=active || bass != 0f || spectrum.any { it != 0f }
        active=false; bass=0f; spectrum.fill(0f)
        return changed
    }

    fun advance(seconds: Double, targetBass: Float, targetSpectrum: List<Float>): Boolean {
        if(!seconds.isFinite() || seconds <= 0) return false
        // A dropped frame or resumed window must not make the shape jump through a large phase.
        val dt=seconds.coerceAtMost(.050)
        var changed=!active
        active=true
        if(effect == PlaybackEffect.BARS) {
            val mix=(1-exp(-dt/.035)).toFloat()
            for(i in spectrum.indices) {
                val next=approach(spectrum[i],targetSpectrum.getOrNull(i) ?: 0f,mix)
                if(next != spectrum[i]) changed=true
                spectrum[i]=next
            }
        } else {
            val previous=bass
            bass=if(reactive) approach(bass,targetBass,(1-exp(-dt/.050)).toFloat()) else 0f
            val cyclesPerSecond=if(reactive) .25+2.25*(previous+bass)/2
                else if(effect == PlaybackEffect.NOTIFICATION) 5.0/18 else 1.0/1.4
            val period=if(effect == PlaybackEffect.NOTIFICATION) 5.0 else 1.0
            phase=(phase+dt*cyclesPerSecond)%period
            changed=changed || bass != previous || !reactive || bass > 0f
        }
        return changed
    }

    private fun approach(old: Float, raw: Float, mix: Float): Float {
        val target=if(raw.isFinite()) raw.coerceIn(0f,1f) else 0f
        return if(abs(target-old) < .0001f) target else old+(target-old)*mix
    }
}
