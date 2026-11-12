package io.github.l1zerox.pandora

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class FolderDocument(val id: String, val name: String, val mime: String?, val directory: Boolean)
internal data class AudioFolderResult(val found: Int = 0, val added: Int = 0, val duplicate: Int = 0, val failures: Int = 0)

/** Provider-independent traversal: descend once, skip non-audio, retain readable siblings on failures. */
internal suspend fun scanAudioFolder(root: String, children: suspend (String)->List<FolderDocument>,
    add: suspend (FolderDocument,String)->Boolean, progress: suspend (Int)->Unit = {}): AudioFolderResult {
    val pending=ArrayDeque<String>(); pending.add(root)
    val visited=mutableSetOf<String>(); val files=mutableSetOf<String>()
    var result=AudioFolderResult()
    while(pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val directory=pending.removeFirst()
        if(!visited.add(directory)) continue
        val documents=try { children(directory) } catch(e: CancellationException) { throw e }
        catch(e: Exception) { result=result.copy(failures=result.failures+1); continue }
        for(document in documents) {
            currentCoroutineContext().ensureActive()
            if(document.directory) { if(document.id !in visited) pending.add(document.id); continue }
            if(!files.add(document.id)) continue
            val mime=libraryMime(document.mime,document.name)?.takeIf { it.startsWith("audio/") } ?: continue
            result=result.copy(found=result.found+1)
            try {
                result=if(add(document,mime)) result.copy(added=result.added+1) else result.copy(duplicate=result.duplicate+1)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { result=result.copy(failures=result.failures+1) }
            if(result.found%25 == 0) progress(result.found)
        }
    }
    progress(result.found)
    return result
}
