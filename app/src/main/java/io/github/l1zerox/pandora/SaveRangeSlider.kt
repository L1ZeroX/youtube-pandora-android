package io.github.l1zerox.pandora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToLong

// Keep one-second precision in the Float-based slider, including very long sources.
internal fun saveRangeBarMaximum(duration: Double?): Long? = duration
    ?.takeIf { it.isFinite() && it > 0 && it <= 16_777_216 }
    ?.let { ceil(it).toLong() }

internal fun saveRangeBarCandidates(videos: List<Video>)=videos.filter { it.selected }.ifEmpty { videos }
internal fun saveRangeBarReferences(videos: List<Video>) = saveRangeBarCandidates(videos).filter { saveRangeBarMaximum(it.duration) != null }

internal data class SaveRangeBarSelection(val start: Long, val end: Long) {
    fun values()=start.toFloat()..end.toFloat()
    fun endText(previous: SaveRangeBarSelection, previousText: String)=
        if(previousText.isBlank() && end == previous.end) "" else saveTimeText(end)
}

/** Project an unfinished text edit onto the bar without changing the user's input. */
internal fun saveRangeBarSelection(start: String, end: String, maximum: Long): SaveRangeBarSelection {
    require(maximum in 1..16_777_216)
    val first=(parseSaveTime(start) ?: 0).coerceIn(0,maximum-1)
    val last=(parseSaveTime(end) ?: maximum).coerceIn(first+1,maximum)
    return SaveRangeBarSelection(first,last)
}

internal fun moveSaveRangeBar(values: ClosedFloatingPointRange<Float>, previous: SaveRangeBarSelection, maximum: Long): SaveRangeBarSelection {
    require(maximum in 1..16_777_216)
    if(!values.start.isFinite() || !values.endInclusive.isFinite()) return previous
    var first=values.start.roundToLong().coerceIn(0,maximum-1)
    var last=values.endInclusive.roundToLong().coerceIn(1,maximum)
    if(first >= last) {
        if(values.start != previous.start.toFloat()) first=last-1 else last=first+1
    }
    return SaveRangeBarSelection(first,last)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveRangeSlider(selection: SaveRangeBarSelection, maximum: Long, enabled: Boolean,
    onChange: (SaveRangeBarSelection)->Unit) {
    val tint=MaterialTheme.colorScheme.primary.copy(alpha=if(enabled) 1f else .38f)
    val colors=SliderDefaults.colors()
    @Composable fun marker(tag: String) {
        Box(Modifier.width(4.dp).height(48.dp),contentAlignment=Alignment.Center) {
            Box(Modifier.width(4.dp).height(28.dp).background(tint,RoundedCornerShape(2.dp)).testTag(tag))
        }
    }
    RangeSlider(value=selection.values(),onValueChange={onChange(moveSaveRangeBar(it,selection,maximum))},
        valueRange=0f..maximum.toFloat(),enabled=enabled,colors=colors,
        modifier=Modifier.fillMaxWidth().height(48.dp).testTag("save-range-slider"),
        startThumb={marker("save-range-start")},endThumb={marker("save-range-end")},
        track={ state -> SliderDefaults.Track(rangeSliderState=state,modifier=Modifier.height(4.dp).testTag("save-range-track"),
            enabled=enabled,colors=colors,drawStopIndicator=null,thumbTrackGapSize=0.dp,trackInsideCornerSize=0.dp) })
}
