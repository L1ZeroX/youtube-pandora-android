package io.github.l1zerox.pandora

import kotlin.math.roundToLong

internal fun playbackFraction(position: Long, duration: Long): Float =
    if(duration > 0) (position.coerceIn(0,duration).toDouble()/duration).toFloat() else 0f

/** Shared by the drag-time label and the player command, avoiding differing truncation/rounding. */
internal fun playbackPositionMs(duration: Long, fraction: Float): Long =
    if(duration > 0 && fraction.isFinite()) (duration.toDouble()*fraction.coerceIn(0f,1f)).roundToLong().coerceIn(0,duration) else 0
