package io.github.l1zerox.pandora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Keep the thin marker centered inside the full slider height, including constrained library rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackPositionSlider(mediaKey: String, value: Float, onValueChange: (Float)->Unit,
    onValueChangeFinished: ()->Unit, enabled: Boolean, color: Color, description: String,
    modifier: Modifier = Modifier, compact: Boolean = false) {
    val tint=if(enabled) color else color.copy(alpha=.38f)
    val colors=SliderDefaults.colors(thumbColor=color,activeTrackColor=color,inactiveTrackColor=color.copy(alpha=.22f),
        disabledThumbColor=tint,disabledActiveTrackColor=tint,disabledInactiveTrackColor=tint.copy(alpha=.22f))
    // A gesture on the old item must be disposed when playback moves to another item.
    key(mediaKey) {
        Slider(value=value.coerceIn(0f,1f),onValueChange=onValueChange,onValueChangeFinished=onValueChangeFinished,
            enabled=enabled,colors=colors,modifier=modifier.height(if(compact) 32.dp else 48.dp)
                .semantics { contentDescription=description },
            thumb={
                Box(Modifier.width(4.dp).fillMaxHeight(),contentAlignment=Alignment.Center) {
                    Box(Modifier.width(4.dp).height(if(compact) 20.dp else 28.dp)
                        .background(tint,RoundedCornerShape(2.dp)).testTag("playback-position-marker"))
                }
            },
            track={ state ->
                // Material uses this same SliderState for thumb placement, taps, drags and accessibility.
                SliderDefaults.Track(sliderState=state,modifier=Modifier.height(3.dp).testTag("playback-position-track"),
                    enabled=enabled,colors=colors,drawStopIndicator=null,thumbTrackGapSize=0.dp,trackInsideCornerSize=0.dp)
            })
    }
}
