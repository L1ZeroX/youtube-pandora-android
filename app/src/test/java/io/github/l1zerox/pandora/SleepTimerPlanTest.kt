package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class SleepTimerPlanTest {
    @Test fun timedPlansExpireOnceElapsedTimeReachesTheirDeadline() {
        for(minutes in listOf(15,30,60)) {
            val plan=SleepTimerPlan.start(minutes,12345,"a")
            assertEquals(minutes*60L,plan.status(12345).remainingSeconds)
            assertFalse(plan.expired(plan.deadline-1))
            assertEquals(1L,plan.status(plan.deadline-1).remainingSeconds)
            assertTrue(plan.expired(plan.deadline))
            assertTrue(plan.expired(plan.deadline+10000))
            assertFalse(plan.status(plan.deadline+10000).active)
            assertFalse(plan.leavesItem("b"))
        }
    }
    @Test fun endOfItemIsBoundToTheCurrentUriAndIsNotATimedCountdown() {
        val plan=SleepTimerPlan.start(-1,100,"a")
        assertTrue(plan.status(100).endOfItem)
        assertFalse(plan.expired(Long.MAX_VALUE))
        assertFalse(plan.leavesItem("a"))
        assertTrue(plan.leavesItem("b"))
        assertTrue(plan.leavesItem(""))
    }
    @Test fun offInvalidValuesAndEndWithoutAnItemDoNotSchedulePlaybackChanges() {
        for(minutes in listOf(0,1,-2)) {
            val plan=SleepTimerPlan.start(minutes,100,"a")
            assertFalse(plan.status(100).active)
            assertFalse(plan.expired(Long.MAX_VALUE))
        }
        assertFalse(SleepTimerPlan.start(-1,100,"").status(100).active)
    }
}
