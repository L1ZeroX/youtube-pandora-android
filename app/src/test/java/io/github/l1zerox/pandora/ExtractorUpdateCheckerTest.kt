package io.github.l1zerox.pandora

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExtractorUpdateCheckerTest {
    @Test fun onlyStableDateTagsAreOffered() {
        assertEquals("2026.11.01",stableExtractorRelease(JSONObject("""{"tag_name":"2026.11.01"}""")))
        for(extra in listOf("\"draft\":true","\"prerelease\":true"))
            assertNull(stableExtractorRelease(JSONObject("{\"tag_name\":\"2026.11.01\",$extra}")))
        assertNull(stableExtractorRelease(JSONObject("""{"tag_name":"nightly"}""")))
    }
    @Test fun newerVersionPromptsAndOlderOrEqualDoesNot() = runBlocking {
        for(latest in listOf("2026.10.25","2026.10.26","2026.11.01")) {
            val c=ExtractorUpdateChecker(this,{0L},{"2026.10.26" to latest})
            c.check(); yield()
            assertEquals(latest.takeIf { it == "2026.11.01" },c.state.value.latest)
            assertEquals("",c.state.value.message)
        }
    }
    @Test fun pendingPromptAndCooldownAvoidRepeatedChecksButManualBypasses() = runBlocking {
        var clock=0L; var calls=0
        val c=ExtractorUpdateChecker(this,{clock},{calls++; "2026.10.26" to "2026.11.01"})
        c.check(); yield(); clock=30_000_000; c.check(); yield(); assertEquals(1,calls)
        c.dismiss(); c.check(); yield(); assertEquals(2,calls)
        c.dismiss(); c.check(); yield(); assertEquals(2,calls)
        c.check(true); yield(); assertEquals(3,calls)
    }
    @Test fun offlineAutomaticChecksAreSilentAndCanRecover() = runBlocking {
        var clock=0L; var offline=true
        val c=ExtractorUpdateChecker(this,{clock},{if(offline) error("offline"); "2026.10.26" to "2026.11.01"})
        c.check(); yield(); assertEquals("",c.state.value.message)
        c.check(true); yield(); assertTrue(c.state.value.message.isNotEmpty())
        c.dismiss(); offline=false; clock=60_000; c.check(); yield()
        assertEquals("2026.11.01",c.state.value.latest)
    }
    @Test fun updatingPreventsConcurrentChecksAndDismissalAndVerifiesInstalledVersion() = runBlocking {
        var calls=0
        val c=ExtractorUpdateChecker(this,{0L},{calls++; "2026.10.26" to "2026.11.01"})
        c.check(); yield(); c.started(); c.dismiss(); c.check(true); yield()
        assertEquals(1,calls); assertTrue(c.state.value.updating)
        c.completed("2026.10.26",true,"yt-dlpを更新しました。")
        assertTrue(c.state.value.message.contains("完了できません"))
        assertFalse(c.state.value.message.contains("更新しました"))
        c.check(true); yield(); c.started(); c.completed("2026.11.01",true,"")
        assertFalse(c.state.value.updating); assertNull(c.state.value.latest)
        assertTrue(c.state.value.message.contains("更新しました"))
    }
    @Test fun concurrentChecksShareOneRequestAndFailuresAllowRetry() = runBlocking {
        val result=CompletableDeferred<Pair<String,String?>>()
        var calls=0
        val c=ExtractorUpdateChecker(this,{0L},{calls++; result.await()})
        c.check(); c.check(true); yield(); assertEquals(1,calls)
        result.complete("2026.10.26" to "2026.11.01"); yield()
        c.started(); c.completed(null,false,"失敗")
        assertFalse(c.state.value.updating); assertTrue(c.state.value.message.contains("失敗"))
    }
}
