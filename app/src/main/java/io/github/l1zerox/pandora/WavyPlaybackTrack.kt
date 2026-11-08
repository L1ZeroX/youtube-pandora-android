package io.github.l1zerox.pandora

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

/** Only Y moves. Both ends stay on the baseline, so the marker always identifies the real position. */
internal fun playbackWaveOffset(x: Float, playedWidth: Float, wavelength: Float, amplitude: Float, phase: Float): Float {
    if(!x.isFinite() || !playedWidth.isFinite() || !wavelength.isFinite() || !amplitude.isFinite() || !phase.isFinite() ||
        x <= 0 || x >= playedWidth || wavelength <= 0 || amplitude <= 0) return 0f
    val fade=min(1f,min(x,playedWidth-x)/(wavelength*.5f)).coerceAtLeast(0f)
    return (sin(2*PI*(x.toDouble()/wavelength-phase))*amplitude*fade).toFloat()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WavyPlaybackTrack(state: SliderState, enabled: Boolean, playing: Boolean, color: Color,
    effect: PlaybackEffect = PlaybackEffect.WAVE) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var visible by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,_ -> visible=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val moving=enabled && playing && visible && state.value > state.valueRange.start && state.value < state.valueRange.endInclusive
    val phase=remember { Animatable(0f) }
    val strength=animateFloatAsState(if(moving) 1f else 0f,tween(220),label="playback-wave-strength")
    LaunchedEffect(moving,effect) {
        if(moving) {
            phase.snapTo(0f)
            // Compose's animation clock respects the system animator duration scale.
            phase.animateTo(if(effect == PlaybackEffect.NOTIFICATION) 5f else 1f,
                infiniteRepeatable(tween(if(effect == PlaybackEffect.NOTIFICATION) 18_000 else 1400,easing=LinearEasing),RepeatMode.Restart))
        }
    }
    val rtl=LocalLayoutDirection.current == LayoutDirection.Rtl
    val path=remember { Path() }
    val active=if(enabled) color else color.copy(alpha=.38f)
    Canvas(Modifier.fillMaxWidth().height(if(effect == PlaybackEffect.NOTIFICATION) 26.dp else 12.dp).testTag("playback-position-track")) {
        val range=state.valueRange.endInclusive-state.valueRange.start
        val fraction=if(range > 0) ((state.value-state.valueRange.start)/range).coerceIn(0f,1f) else 0f
        val played=size.width*fraction
        val baseline=size.height/2
        val stroke=(if(effect == PlaybackEffect.NOTIFICATION) 5 else 3).dp.toPx()
        scale(scaleX=if(rtl) -1f else 1f,scaleY=1f) {
            if(played < size.width) drawLine(active.copy(alpha=active.alpha*.22f),Offset(played,baseline),Offset(size.width,baseline),stroke,StrokeCap.Round)
            if(played > 0) {
                if(effect == PlaybackEffect.NOTIFICATION) {
                    drawLine(active,Offset(0f,baseline),Offset(played,baseline),stroke,StrokeCap.Round)
                    for(layer in 0..1) {
                        path.reset(); path.moveTo(0f,baseline)
                        val samples=ceil(played/2.dp.toPx()).toInt().coerceAtLeast(1)
                        for(index in 1..samples) {
                            val x=played*index/samples
                            val height=notificationWaveHeight(x,played,(if(layer == 0) 170 else 140).dp.toPx(),
                                (if(layer == 0) 9 else 6).dp.toPx()*strength.value,
                                phase.value*(if(layer == 0) 1f else .8f)+layer*.35f)
                            path.lineTo(x,baseline-stroke/2-height)
                        }
                        path.lineTo(played,baseline); path.close()
                        drawPath(path,active.copy(alpha=active.alpha*(if(layer == 0) .5f else .9f)))
                    }
                    return@scale
                }
                val amplitude=4.dp.toPx()*strength.value
                if(amplitude < .01f) drawLine(active,Offset(0f,baseline),Offset(played,baseline),stroke,StrokeCap.Round)
                else {
                    path.reset(); path.moveTo(0f,baseline)
                    val samples=ceil(played/2.dp.toPx()).toInt().coerceAtLeast(1)
                    for(index in 1..samples) {
                        val x=played*index/samples
                        path.lineTo(x,baseline+playbackWaveOffset(x,played,28.dp.toPx(),amplitude,phase.value))
                    }
                    drawPath(path,active,style=Stroke(stroke,cap=StrokeCap.Round))
                }
            }
        }
    }
}
