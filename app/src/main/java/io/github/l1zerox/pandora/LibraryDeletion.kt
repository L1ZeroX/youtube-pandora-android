package io.github.l1zerox.pandora

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal sealed interface LibraryDeleteResult {
    data object Deleted: LibraryDeleteResult
    data class Consent(val sender: IntentSender, val retry: Boolean): LibraryDeleteResult
}

internal object LibraryDeletion {
    // Once confirmed, complete file deletion and library/session cleanup together, even across rotation.
    suspend fun delete(context: Context, entry: LibraryEntry, allowConsent: Boolean = true): LibraryDeleteResult =
        withContext(Dispatchers.IO+NonCancellable) {
            val uri=Uri.parse(entry.uri)
            require(uri.scheme == "content")
            val resolver=context.contentResolver
            try {
                if(DocumentsContract.isDocumentUri(context,uri)) {
                    val flags=resolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_FLAGS),null,null,null)?.use {
                        if(it.moveToFirst()) it.getInt(0) else 0
                    } ?: 0
                    check(flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) { "この保存先は削除に対応していません。" }
                    check(DocumentsContract.deleteDocument(resolver,uri)) { "ファイルを削除できませんでした。" }
                } else {
                    require(uri.authority == MediaStore.AUTHORITY)
                    check(resolver.delete(uri,null,null) == 1) { "ファイルが見つからないか、削除できませんでした。" }
                }
            } catch(error: SecurityException) {
                if(!allowConsent) throw error
                if(Build.VERSION.SDK_INT >= 30 && uri.authority == MediaStore.AUTHORITY)
                    return@withContext LibraryDeleteResult.Consent(MediaStore.createDeleteRequest(resolver,listOf(uri)).intentSender,false)
                if(error is RecoverableSecurityException)
                    return@withContext LibraryDeleteResult.Consent(error.userAction.actionIntent.intentSender,true)
                throw error
            }
            finish(context,entry)
            LibraryDeleteResult.Deleted
        }

    /** Also used after Android has completed an approved delete request. */
    suspend fun finish(context: Context, entry: LibraryEntry) = withContext(Dispatchers.IO+NonCancellable) {
        LibraryRepository.forgetImport(context,entry.uri)
        withContext(Dispatchers.Main) { PlaybackRuntime.removeLibraryItem?.invoke(entry.uri) }
    }

    suspend fun removeImport(context: Context, entry: LibraryEntry) {
        require(entry.imported)
        finish(context,entry)
    }
}
