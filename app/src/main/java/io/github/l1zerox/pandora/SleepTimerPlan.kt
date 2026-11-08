package io.github.l1zerox.pandora

/** Monotonic elapsed time is unaffected by wall-clock/time-zone changes. */
internal data class SleepTimerPlan(val deadline: Long = 0, val endingUri: String = "") {
    fun expired(now: Long)=deadline > 0 && now >= deadline
    fun status(now: Long)=SleepTimerStatus(if(deadline > 0) ((deadline-now).coerceAtLeast(0)+999)/1000 else 0,endingUri.isNotBlank())
    fun leavesItem(uri: String)=endingUri.isNotBlank() && endingUri != uri
    companion object {
        fun start(minutes: Int, now: Long, uri: String)=when {
            minutes in listOf(15,30,60) -> SleepTimerPlan(now+minutes*60_000L)
            minutes == -1 -> SleepTimerPlan(endingUri=uri)
            else -> SleepTimerPlan()
        }
    }
}
