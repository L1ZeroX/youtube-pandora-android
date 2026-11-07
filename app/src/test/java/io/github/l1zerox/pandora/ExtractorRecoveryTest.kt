package io.github.l1zerox.pandora

import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ExtractorRecoveryTest {
    private val url="https://www.youtube.com/watch?v=abcdefghijk"
    private fun forbidden()=IllegalStateException("ERROR: unable to download video data: HTTP Error 403: Forbidden")

    @Test fun bundleUpgradesOldInstallsButPreservesEqualAndNewerManualUpdates() {
        assertTrue(shouldInstallExtractor("2025.11.12"))
        assertTrue(shouldInstallExtractor(null))
        assertFalse(shouldInstallExtractor("2026.10.26"))
        assertFalse(shouldInstallExtractor("2026.10.26.232701"))
        assertFalse(shouldInstallExtractor("2026.11.01"))
    }
    @Test fun actualVersionIsReadWithoutUpdaterPreferences() {
        val file=File.createTempFile("extractor-test",".zip")
        try {
            ZipOutputStream(file.outputStream()).use {
                it.putNextEntry(ZipEntry("yt_dlp/version.py")); it.write("__version__ = '2026.10.26'".toByteArray()); it.closeEntry()
            }
            assertEquals("2026.10.26",extractorVersion(file))
            file.writeText("broken archive")
            assertNull(extractorVersion(file))
        } finally { file.delete() }
    }
    @Test fun forbiddenRecoversOnceAndKeepsSuccessfulResult() {
        var attempts=0; var recoveries=0
        val result=withYoutubeRecovery(url,{}, {recoveries++}) { if(attempts++ == 0) throw forbidden(); "saved" }
        assertEquals("saved",result); assertEquals(2,attempts); assertEquals(1,recoveries)
    }
    @Test fun repeatedForbiddenDoesNotLoop() {
        var attempts=0; var recoveries=0
        try { withYoutubeRecovery(url,{}, {recoveries++}) { attempts++; throw forbidden() }; fail() }
        catch(error: IllegalStateException) { assertTrue(error.message!!.contains("403")) }
        assertEquals(2,attempts); assertEquals(1,recoveries)
    }
    @Test fun otherSitesAndNon403FailuresAreNotRetried() {
        for((target,error) in listOf("https://example.com/watch" to forbidden(),url to IllegalStateException("HTTP Error 404"),url to CancellationException())) {
            var attempts=0
            try { withYoutubeRecovery(target,{}, {fail("Unexpected recovery")}) { attempts++; throw error }; fail() }
            catch(actual: Exception) { assertSame(error,actual) }
            assertEquals(1,attempts)
        }
    }
    @Test fun stopDuringRecoveryPreventsNewDownload() {
        var stopped=false; var attempts=0
        try {
            withYoutubeRecovery(url,{if(stopped) throw CancellationException()}, {stopped=true}) {attempts++; throw forbidden()}
            fail()
        } catch(expected: CancellationException) { assertEquals(1,attempts) }
    }
    @Test fun stopAfterFailurePreventsUpdating() {
        var stopped=false
        try {
            withYoutubeRecovery(url,{if(stopped) throw CancellationException()}, {fail("Updated after stop")}) {stopped=true; throw forbidden()}
            fail()
        } catch(expected: CancellationException) { }
    }
    @Test fun friendly403RetainsDiagnosisButDoesNotExposeSignedUrls() {
        val message=downloadFailureMessage(url,IllegalStateException("HTTP Error 403: https://example.com/video?token=secret"))
        assertTrue(message.contains("yt-dlpを更新")); assertTrue(message.contains("403")); assertFalse(message.contains("secret"))
        assertEquals("no space",downloadFailureMessage(url,IllegalStateException("no space")))
    }
}
