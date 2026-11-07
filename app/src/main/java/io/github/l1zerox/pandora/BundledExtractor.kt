package io.github.l1zerox.pandora

import android.content.Context
import android.system.Os
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.FileOutputStream

internal object BundledExtractor {
    private fun binary(context: Context) = File(File(File(context.noBackupFilesDir,YoutubeDL.baseName),YoutubeDL.ytdlpDirName),YoutubeDL.ytdlpBin)
    fun version(context: Context): String? = extractorVersion(binary(context))

    /** Replace only older copies, atomically; manually updated newer engines stay installed. */
    @Synchronized fun install(context: Context, check: ()->Unit) {
        check()
        val destination=binary(context)
        val version=extractorVersion(destination)
        if(!shouldInstallExtractor(version)) return
        if(version == null && destination.isFile && isBundledExtractor(destination,check)) return
        val pending=File.createTempFile("pandora-extractor-",".tmp",destination.parentFile)
        try {
            context.resources.openRawResource(R.raw.ytdlp).use { input ->
                FileOutputStream(pending).use { output ->
                    val buffer=ByteArray(65536)
                    while(true) { check(); val count=input.read(buffer); if(count < 0) break; output.write(buffer,0,count) }
                    output.fd.sync()
                }
            }
            require(isBundledExtractor(pending,check)) { "同梱yt-dlpの整合性確認に失敗しました。" }
            check()
            Os.rename(pending.absolutePath,destination.absolutePath)
            AppLog.info("yt-dlp更新","同梱版 $BUNDLED_EXTRACTOR_VERSION を適用しました。")
        } finally { pending.delete() }
    }
}
