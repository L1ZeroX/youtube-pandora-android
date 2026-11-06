package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LogJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun removesUrlCredentialsQueriesAndAuthorization() {
        val result=cleanLog("失敗 https://user:secret@example.com/private/video?token=abc\nAuthorization: Bearer secret\nCookie: SID=abc\npassword=abc")
        assertTrue(result.contains("example.com"))
        for(secret in listOf("user:","secret","private/video","abc","Bearer","SID=")) assertFalse(secret,result.contains(secret))
    }
    @Test fun unicodeAndMultilineErrorsSurviveRestart() {
        val folder=temporary.newFolder()
        val entry=LogEntry(1000,"ERROR","動画解析","解析に失敗しました\nlibcrypto.so.3\t原因")
        LogJournal(folder).append(entry)
        assertEquals(listOf(entry),LogJournal(folder).read())
    }
    @Test fun rotatesAndPreservesRecentErrorsWithinBound() {
        val folder=temporary.newFolder()
        val journal=LogJournal(folder,maxBytes=200)
        repeat(30) { journal.append(LogEntry(it.toLong(),"ERROR","保存","entry-$it "+"x".repeat(20))) }
        val restored=LogJournal(folder,maxBytes=200).read()
        assertEquals(29L,restored.last().time)
        assertTrue(restored.size < 30)
        assertTrue(folder.listFiles()!!.sumOf { it.length() } <= 400)
    }
    @Test fun incompleteRecordDoesNotHideEarlierLogs() {
        val folder=temporary.newFolder()
        val journal=LogJournal(folder)
        journal.append(LogEntry(1,"INFO","開始","正常な記録"))
        File(folder,"current.log").appendText("incomplete\n2\tERROR\t保存\t!!invalid-base64")
        assertEquals("正常な記録",LogJournal(folder).read().single().message)
    }
    @Test fun clearingRemovesBothFilesAcrossRestart() {
        val folder=temporary.newFolder()
        val journal=LogJournal(folder,maxBytes=100)
        repeat(10) { journal.append(LogEntry(it.toLong(),"ERROR","失敗","x".repeat(20))) }
        journal.clear()
        assertTrue(LogJournal(folder).read().isEmpty())
    }
}
