package io.github.l1zerox.pandora

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ApkUpdateRecoveryTest {
    @Test fun enqueueQueryAndPatchFailuresUseOneFallbackThenFullFailuresStop() {
        for(stage in listOf("enqueue","query","patch")) {
            var delta=true
            val events=mutableListOf<String>()
            fun step(operation: ()->Unit) = runApkUpdateStep(operation,{delta},
                fallback={events+="full:${it.message}"; delta=false},failed={events+="error:${it.message}"})
            step { throw IOException(stage) }
            assertEquals(listOf("full:$stage"),events)
            step { throw IOException("offline") }
            assertEquals(listOf("full:$stage","error:offline"),events)
        }
    }
    @Test fun failedFallbackIsReportedWithoutRetryingRecursively() {
        var attempts=0
        val failures=mutableListOf<Exception>()
        val disk=IOException("storage unavailable")
        runApkUpdateStep({throw IOException("bad patch")},{true},
            fallback={attempts++; throw disk},failed={failures+=it})
        assertEquals(1,attempts)
        assertEquals(listOf(disk),failures)
    }
    @Test fun cancellationNeverStartsAnotherDownloadOrBecomesAnError() {
        val cancelled=CancellationException("screen left")
        for(cancelFallback in listOf(false,true)) {
            var fallbacks=0; var failures=0
            val result=runCatching {
                runApkUpdateStep({if(cancelFallback) throw IOException("patch") else throw cancelled},{true},
                    fallback={fallbacks++; throw cancelled},failed={failures++})
            }
            assertSame(cancelled,result.exceptionOrNull())
            assertEquals(if(cancelFallback) 1 else 0,fallbacks); assertEquals(0,failures)
        }
    }
    @Test fun successDoesNotInvokeRecoveryAndFullRequestSurvivesSerialization() {
        var completed=false
        runApkUpdateStep({completed=true},{error("must not inspect recovery on success")},
            fallback={fail("Unexpected fallback")},failed={fail("Unexpected failure")})
        assertTrue(completed)
        val target="0.4.25-beta.2"; val base="0.4.25-beta.1"
        val release=AppRelease(target,"$RELEASES_URL/tag/v$target",
            "$RELEASES_URL/download/v$target/YoutubePandora-Android-v$target.apk","",true,1000,"sha256:"+"aa".repeat(32),
            ApkDeltaRelease(base,"$RELEASES_URL/download/v$target/${deltaAssetName(target,base)}",100,"sha256:"+"bb".repeat(32)))
        val restored=parseApkRelease(release.copy(delta=null).updateJson())!!
        assertNull(restored.delta); assertEquals(release.downloadUrl,restored.downloadUrl)
        assertEquals(release.digest,restored.digest); assertEquals(release.size,restored.size)
    }
}
