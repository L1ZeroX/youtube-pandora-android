package io.github.l1zerox.pandora

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.graphics.Bitmap
import androidx.core.graphics.scale
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Base64
import org.json.JSONObject

internal fun carArtworkUri(context: Context, entry: LibraryEntry, album: String?, caller: String): Uri {
    val revision=(album?.let { LibraryArtwork.file(albumArtworkKey(it)) } ?: LibraryArtwork.file(entry))?.name.orEmpty()
    val request=JSONObject().put("source",entry.uri).put("album",entry.album).put("revision",revision)
        .apply { if(album != null) put("override",album) }.toString()
    // Bind the entire request to the granted path; changing query parameters must not change the source.
    val path=Base64.getUrlEncoder().withoutPadding().encodeToString(request.toByteArray(Charsets.UTF_8))
    val uri=Uri.Builder().scheme("content").authority(context.packageName+".car-artwork").appendPath("cover").appendPath(path).build()
    val targets=mutableSetOf(caller)
    if(context.packageManager.checkSignatures("com.google.android.projection.gearhead","com.google.android.gms") == PackageManager.SIGNATURE_MATCH)
        targets.add("com.google.android.projection.gearhead")
    targets.filterNot { it == context.packageName }.forEach { name -> runCatching { context.grantUriPermission(name,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    return uri
}

/** Unexported provider with explicit, read-only grants to the connected media controller. */
class CarArtworkProvider: ContentProvider() {
    override fun onCreate()=true
    override fun getType(uri: Uri)="image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): MatrixCursor {
        val columns=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(columns.map { if(it == OpenableColumns.DISPLAY_NAME) "artwork.jpg" else null }) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?)=throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?)=throw UnsupportedOperationException()
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val parts=uri.pathSegments
        if(mode != "r" || parts.size != 2 || parts[0] != "cover" || parts[1].length > 8192) throw FileNotFoundException()
        val request=runCatching { JSONObject(String(Base64.getUrlDecoder().decode(parts[1]),Charsets.UTF_8)) }.getOrNull() ?: throw FileNotFoundException()
        val source=request.optString("source")
        if(!source.startsWith("content://") || source.length > 4096) throw FileNotFoundException()
        val context=context ?: throw FileNotFoundException()
        val key=MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val directory=File(context.cacheDir,"car-artwork").apply { mkdirs() }
        val file=File(directory,"$key.jpg")
        synchronized(lock) {
            if(!file.isFile) {
                val entry=LibraryEntry(source,"","audio/mpeg",album=request.optString("album"))
                val image=runBlocking(Dispatchers.IO) { LibraryRepository.artwork(context,entry,request.optString("override").takeIf { it.isNotBlank() }) } ?: throw FileNotFoundException()
                val scale=(maxOf(image.width,image.height)/256f).coerceAtLeast(1f)
                val thumbnail=image.scale((image.width/scale).toInt().coerceAtLeast(1),(image.height/scale).toInt().coerceAtLeast(1),true)
                try {
                    file.outputStream().use { if(!thumbnail.compress(Bitmap.CompressFormat.JPEG,80,it)) throw FileNotFoundException() }
                } catch(e: Exception) { file.delete(); throw e }
                finally { if(thumbnail !== image) thumbnail.recycle(); image.recycle() }
                directory.listFiles()?.filter { it != file && it.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() }?.drop(127)?.forEach { it.delete() }
            }
            file.setLastModified(System.currentTimeMillis())
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }
    companion object { private val lock=Any() }
}
