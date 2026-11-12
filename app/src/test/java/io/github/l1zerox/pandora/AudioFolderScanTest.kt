package io.github.l1zerox.pandora

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AudioFolderScanTest {
    private fun folder(id: String)=FolderDocument(id,id,null,true)
    private fun file(id: String, name: String, mime: String? = null)=FolderDocument(id,name,mime,false)
    @Test fun readsNestedAudioAndIgnoresImagesVideosAndDuplicateProviderRows()=runBlocking {
        val graph=mapOf("root" to listOf(folder("album"),file("song","01.mp3"),file("image","cover.jpg"),file("video","video.mp4")),
            "album" to listOf(file("second","02.FLAC"),file("song","01.mp3"),folder("root")))
        val seen=mutableListOf<String>(); var progress=0
        val result=scanAudioFolder("root",{graph[it].orEmpty()},{ doc,mime -> assertTrue(mime.startsWith("audio/")); seen+=doc.id; true },{progress=it})
        assertEquals(listOf("song","second"),seen)
        assertEquals(AudioFolderResult(2,2,0,0),result); assertEquals(2,progress)
    }
    @Test fun repeatsSkipExistingSongsAndUnreadableFoldersDoNotDiscardSiblings()=runBlocking {
        val result=scanAudioFolder("root",{ id ->
            if(id == "bad") error("No access")
            listOf(folder("bad"),file("old","old.m4a"),file("new","new.opus"),file("broken","broken.wav"))
        },{doc,_ -> if(doc.id == "broken") error("No access") else doc.id != "old"})
        assertEquals(AudioFolderResult(3,1,1,2),result)
    }
    @Test fun cancellationIsNeverReportedAsAnImportFailure() {
        assertThrows(CancellationException::class.java) { runBlocking {
            scanAudioFolder("root",{throw CancellationException("cancelled")},{_,_->true})
        } }
        assertThrows(CancellationException::class.java) { runBlocking {
            scanAudioFolder("root",{listOf(file("a","a.mp3"))},{_,_->throw CancellationException("cancelled")})
        } }
    }
}
