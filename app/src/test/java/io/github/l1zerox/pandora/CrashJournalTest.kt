package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashJournalTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun uncaughtErrorIsWrittenBeforeDelegatingAndSurvivesRestart() {
        val folder=temporary.newFolder()
        val error=NoSuchMethodError("test missing method")
        var delegated=false
        val thread=Thread({throw error},"test-conversion")
        thread.uncaughtExceptionHandler=RecordingCrashHandler(
            { t,e -> CrashJournal(folder).write(t,e,"test-version","FFmpeg") },
            Thread.UncaughtExceptionHandler { t,e ->
                assertSame(thread,t);assertSame(error,e)
                assertTrue(CrashJournal(folder).read()!!.message.contains("test missing method"))
                delegated=true
            })
        thread.start();thread.join(5000)
        assertFalse(thread.isAlive);assertTrue(delegated)
        val entry=CrashJournal(folder).read()!!
        assertTrue(entry.message.contains("test-conversion"))
        assertTrue(entry.message.contains("java.lang.NoSuchMethodError"))
        val normal=LogJournal(folder)
        normal.append(entry);CrashJournal(folder).clear()
        assertNull(CrashJournal(folder).read())
        assertEquals(entry,LogJournal(folder).read().single())
    }
    @Test fun loggingFailureStillPassesOriginalExceptionToSystemHandler() {
        val original=IllegalStateException("original")
        var received: Throwable?=null
        RecordingCrashHandler({_,_ -> throw OutOfMemoryError("write failed")},
            Thread.UncaughtExceptionHandler { _,e -> received=e }).uncaughtException(Thread.currentThread(),original)
        assertSame(original,received)
    }
    @Test fun nestedCauseAndFailingFramesAreRetainedAndSecretsRemoved() {
        val folder=temporary.newFolder()
        val cause=IllegalArgumentException("原因 https://user:secret@example.com/video?token=abc\nCookie: SID=secret")
        cause.stackTrace=arrayOf(StackTraceElement("TestEncoder","start","TestEncoder.kt",42))
        CrashJournal(folder).write(Thread.currentThread(),RuntimeException("wrapper",cause),"v-test","変換中")
        val text=CrashJournal(folder).read()!!.message
        assertTrue(text.contains("原因[1] java.lang.IllegalArgumentException"))
        assertTrue(text.contains("TestEncoder.kt:42"))
        assertFalse(text.contains("secret"));assertFalse(text.contains("token=abc"))
    }
    @Test fun hugeStacksStayBoundedWithoutDroppingExceptionHeader() {
        val folder=temporary.newFolder()
        val error=IllegalStateException("important cause")
        error.stackTrace=Array(10000) { StackTraceElement("Test","frame$it","File.kt",it) }
        CrashJournal(folder).write(Thread.currentThread(),error,"v-test","変換中")
        val entry=CrashJournal(folder).read()!!
        assertTrue(entry.message.contains("important cause"))
        assertTrue(entry.message.length <= 16000)
        assertTrue(File(folder,"pending-crash.txt").length() <= 64000)
    }
    @Test fun handlerRecordsOnlyOnceButAlwaysDelegates() {
        var writes=0;var exits=0
        val handler=RecordingCrashHandler({_,_ -> writes++},Thread.UncaughtExceptionHandler { _,_ -> exits++ })
        repeat(2) { handler.uncaughtException(Thread.currentThread(),IllegalStateException("test")) }
        assertEquals(1,writes);assertEquals(2,exits)
    }
}
