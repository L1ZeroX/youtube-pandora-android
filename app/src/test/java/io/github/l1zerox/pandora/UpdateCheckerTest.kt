package io.github.l1zerox.pandora

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {
    private val release=AppRelease("0.4.15","$RELEASES_URL/tag/v0.4.15",
        "$RELEASES_URL/download/v0.4.15/YoutubePandora-Android-v0.4.15.apk","更新内容",false)

    @Test fun longLivedProcessChecksAgainAndFindsNewRelease() = runBlocking {
        var clock=0L; var requests=0
        val checker=UpdateChecker(this,"0.4.14",{clock},{_,_ -> if(++requests == 1) null else release})
        checker.check(); yield()
        assertNull(checker.state.value.release)
        clock=299_999; checker.check(); yield(); assertEquals(1,requests)
        clock=300_000; checker.check(); yield()
        assertEquals(2,requests); assertEquals(release,checker.state.value.release)
    }

    @Test fun startupNetworkFailureDoesNotPermanentlyDisableAutomaticChecks() = runBlocking {
        var clock=0L; var requests=0
        val checker=UpdateChecker(this,"0.4.14",{clock},{_,_ ->
            if(++requests == 1) throw java.io.IOException("offline") else release })
        checker.check(); yield()
        assertFalse(checker.state.value.checking); assertTrue(checker.state.value.failed)
        assertEquals("",checker.state.value.message)
        clock=29_999; checker.check(); yield(); assertEquals(1,requests)
        clock=30_000; checker.check(); yield()
        assertEquals(2,requests); assertEquals(release,checker.state.value.release)
        assertFalse(checker.state.value.failed)
    }

    @Test fun overlappingActivityResumesDoNotStartConcurrentRequests() = runBlocking {
        var requests=0
        val response=CompletableDeferred<AppRelease?>()
        val checker=UpdateChecker(this,"0.4.14",{0L},{_,_ -> requests++; response.await()})
        checker.check(); yield()
        checker.check(); checker.check(manual=true); yield()
        assertTrue(checker.state.value.checking); assertEquals(1,requests)
        response.complete(release); yield()
        assertFalse(checker.state.value.checking); assertEquals(release,checker.state.value.release)
    }

    @Test fun pendingPromptSurvivesScreenChangesAndDownloadDeferral() = runBlocking {
        var clock=0L; var requests=0
        val checker=UpdateChecker(this,"0.4.14",{clock},{_,_ -> requests++; release})
        checker.check(); yield()
        clock=600_000; checker.check(); yield()
        assertEquals(1,requests); assertEquals(release,checker.state.value.release)
        checker.dismiss(); assertNull(checker.state.value.release)
        checker.check(); yield()
        assertEquals(2,requests); assertEquals(release,checker.state.value.release)
    }

    @Test fun dismissDoesNotImmediatelyReopenOnRotationOrShortScreenRoundTrip() = runBlocking {
        var clock=0L; var requests=0
        val checker=UpdateChecker(this,"0.4.14",{clock},{_,_ -> requests++; release})
        checker.check(); yield(); checker.dismiss()
        clock=1000; checker.check(); yield()
        assertEquals(1,requests); assertNull(checker.state.value.release)
        checker.check(manual=true); yield()
        assertEquals(2,requests); assertEquals(release,checker.state.value.release)
    }

    @Test fun manualChecksBypassCooldownAndKeepChannelSwitchAndErrorFeedback() = runBlocking {
        var fails=false
        val calls=mutableListOf<Pair<Boolean,Boolean>>()
        val checker=UpdateChecker(this,"0.4.14",{0L},{beta,offer ->
            calls+=beta to offer
            if(fails) throw java.io.IOException("offline") else null
        })
        checker.check(); yield(); assertEquals("",checker.state.value.message)
        checker.check(manual=true); yield(); assertTrue(checker.state.value.message.contains("新しいバージョンはありません"))
        checker.check(manual=true,beta=true,offer=true); yield()
        assertEquals(true to true,calls.last()); assertTrue(checker.state.value.message.contains("ベータ版"))
        fails=true; checker.check(manual=true); yield()
        assertTrue(checker.state.value.message.contains("更新を確認できませんでした"))
    }
}
