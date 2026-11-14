package io.github.l1zerox.pandora

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal enum class PlaybackEffect(val id: String, val label: String) {
    WAVE("wave","従来の波"), NOTIFICATION("notification","通知センター風"), BASS("bass_80hz","80Hz低音連動");
    fun reactsToBass(notificationBass: Boolean)=this == BASS || this == NOTIFICATION && notificationBass
    companion object { fun fromId(id: String)=if(id == "bass_100hz") BASS else entries.firstOrNull { it.id == id } ?: WAVE }
}

internal fun playbackBassEnabled(p: Preferences)=p.libraryPlayerWave && PlaybackEffect.fromId(p.libraryPlayerEffect).reactsToBass(p.libraryNotificationBass)

/** Soft, filled crests above the track, observed on the device's notification media player. */
internal fun notificationWaveHeight(x: Float, played: Float, wavelength: Float, amplitude: Float, phase: Float): Float {
    if(!x.isFinite() || !played.isFinite() || !wavelength.isFinite() || !amplitude.isFinite() || !phase.isFinite() ||
        x <= 0 || x >= played || wavelength <= 0 || amplitude <= 0) return 0f
    val taper=min(1f,min(x,played-x)/(wavelength*.22f)).coerceAtLeast(0f)
    val crest=(.5+.5*sin(2*PI*(x.toDouble()/wavelength+phase))).pow(1.7)
    return (crest*amplitude*taper).toFloat()
}
