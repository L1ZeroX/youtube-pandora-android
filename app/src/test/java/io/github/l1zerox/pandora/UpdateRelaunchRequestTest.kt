package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class UpdateRelaunchRequestTest {
    private val now=1_800_000_000_000L
    private val request=UpdateRelaunchRequest("0.4.23",43,now)

    @Test fun requestSurvivesProcessReplacement() {
        assertEquals(request,parseUpdateRelaunch(request.json()))
        assertTrue(parseUpdateRelaunch(request.json())!!.eligible("0.4.23",44,now+60_000))
    }
    @Test fun startingOrCancellingInstallerDoesNotCountAsInstalled() {
        assertFalse(request.eligible("0.4.22",43,now+1))
        assertFalse(request.eligible("0.4.23",43,now+1))
        assertFalse(request.eligible("0.4.23",42,now+1))
    }
    @Test fun unrelatedLaterUpdateCannotTriggerOldRequest() {
        assertFalse(request.eligible("0.4.24",45,now+1))
        assertFalse(request.eligible("0.4.23-beta.1",44,now+1))
    }
    @Test fun expiredAndFutureDatedRequestsNeverOpenApp() {
        assertTrue(request.eligible("0.4.23",44,now+UPDATE_RELAUNCH_WINDOW_MS))
        assertFalse(request.eligible("0.4.23",44,now+UPDATE_RELAUNCH_WINDOW_MS+1))
        assertFalse(request.eligible("0.4.23",44,now-1))
    }
    @Test fun invalidRecordsAreIgnored() {
        for(raw in listOf(null,"broken","{}",request.copy(version="not-a-version").json(),
            request.copy(fromCode=0).json(),request.copy(requestedAt=0).json())) assertNull(parseUpdateRelaunch(raw))
    }
}
