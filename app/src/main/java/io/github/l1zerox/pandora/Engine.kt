package io.github.l1zerox.pandora

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.system.Os
import android.system.OsConstants
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class Engine(private val context: Context, private val progress: (String, Float?, String) -> Unit) {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var native: Process? = null
    private val id = UUID.randomUUID().toString()
    fun init() {
        AppLog.info("準備","yt-dlp / FFmpegを初期化")
        YoutubeDL.init(context); FFmpeg.init(context); check()
        AppLog.info("準備","初期化完了 / yt-dlp ${YoutubeDL.version(context) ?: "不明"} / 空き容量 ${context.filesDir.usableSpace / (1024*1024)} MiB")
    }
    fun check() { if(cancelled.get()) throw CancellationException("停止しました") }
    fun cancel() {
        cancelled.set(true)
        native?.destroyForcibly()
        // Stop only descendants of this app's PID, never processes owned by another app.
        killDescendants()
        runCatching { YoutubeDL.destroyProcessById(id) }
    }
    private fun killDescendants() {
        val parentMap = mutableMapOf<Int, Int>()
        File("/proc").listFiles()?.forEach { file ->
            val pid = file.name.toIntOrNull() ?: return@forEach
            runCatching {
                val status = File(file,"status").readLines()
                fun number(prefix: String) = status.first { it.startsWith(prefix) }.substringAfter(':').trim().split(Regex("\\s+"))[0].toInt()
                if(number("Uid:") == android.os.Process.myUid()) parentMap[pid] = number("PPid:")
            }
        }
        val descendants = mutableSetOf(android.os.Process.myPid())
        repeat(12) { parentMap.forEach { (pid, parent) -> if(parent in descendants) descendants.add(pid) } }
        descendants.filter { it != android.os.Process.myPid() }.reversed().forEach { pid -> runCatching { Os.kill(pid, OsConstants.SIGKILL) } }
    }
    private fun request(url: String) = YoutubeDLRequest(url).apply {
        addOption("--no-warnings"); addOption("--socket-timeout", "20"); addOption("--retries", "3")
        addOption("--no-colors"); addOption("--newline"); addOption("--no-mtime")
        addOption("--match-filter", "!is_live & !is_upcoming")
    }
    private fun execute(request: YoutubeDLRequest): String {
        check()
        return YoutubeDL.execute(request, id) { percent, eta, line ->
            if(cancelled.get()) { cancel(); return@execute }
            if(line.trimStart().startsWith("{")) return@execute
            val phase = if(line.contains("[download]")) "ダウンロード中" else if(line.contains("[Merger]") || line.contains("[ExtractAudio]")) "変換中" else "情報を確認中"
            progress(phase, percent.takeIf { it >= 0 }?.coerceIn(0f,100f)?.div(100f),
                line.take(300) + if(eta >= 0) " · 残り ${durationText(eta.toDouble())}" else "")
        }.out.also { check() }
    }
    fun discover(url: String, playlist: Boolean): List<Video> {
        AppLog.info("情報取得","開始 / プレイリスト=$playlist / $url")
        val req = request(url).apply {
            addOption("--dump-single-json"); addOption("--skip-download"); addOption("--flat-playlist")
            addOption("--playlist-end", "500")
            if(!playlist) addOption("--no-playlist")
        }
        val root = JSONObject(execute(req))
        val entries = root.optJSONArray("entries")
        return if(entries == null) listOf(video(root,url)) else (0 until entries.length()).mapNotNull { i ->
            entries.optJSONObject(i)?.let { runCatching { video(it,url) }.getOrNull() }
        }
    }
    private fun video(json: JSONObject, fallback: String): Video {
        require(!json.optBoolean("is_live") && json.optString("live_status") !in listOf("is_live", "is_upcoming")) { "配信中・配信予定のライブは対象外です。" }
        val site = json.optString("extractor_key", json.optString("ie_key", json.optString("extractor", "video")))
        val videoId = json.optString("id")
        val url = if(site.startsWith("youtube",true) && videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId"
            else json.optString("webpage_url",json.optString("url",fallback))
        validUrls(url)
        val thumbnails = json.optJSONArray("thumbnails")
        val thumb = json.optString("thumbnail").ifBlank { thumbnails?.optJSONObject((thumbnails.length()-1).coerceAtLeast(0))?.optString("url") ?: "" }
        return Video("${site.lowercase()}:${videoId.ifBlank { url }}",url,json.optString("title","タイトル不明"),site,
            json.optDouble("duration").takeIf { it.isFinite() && it >= 0 },thumb)
    }
    fun download(video: Video, preferences: Preferences): String {
        val work = File(context.cacheDir, "download-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val req = request(video.url).apply {
                addOption("--no-playlist"); addOption("-f", formatSelector(preferences))
                addOption("-S", "res,fps,vcodec:h264,acodec:aac")
                addOption("-o", File(work,"source.%(ext)s").absolutePath)
                addOption("--merge-output-format", "mkv")
            }
            execute(req)
            AppLog.info("ダウンロード","取得完了、動画の解析へ")
            val source = work.listFiles()!!.firstOrNull { it.name.startsWith("source.") && it.extension !in listOf("part","ytdl","json") }
                ?: error("保存する動画ファイルがありません。")
            check()
            val metadata = JSONObject(runNative("libffprobe.so",listOf("-v","error","-show_streams","-show_format","-of","json",source.absolutePath)))
            val streams = metadata.getJSONArray("streams")
            val list = (0 until streams.length()).map { streams.getJSONObject(it) }
            val visual = list.firstOrNull { it.optString("codec_type") == "video" && it.optJSONObject("disposition")?.optInt("attached_pic") != 1 }
            val audio = list.firstOrNull { it.optString("codec_type") == "audio" }
            val duration = metadata.optJSONObject("format")?.optString("duration")?.toDoubleOrNull() ?: video.duration ?: 0.0
            val output = File(work, "output.${preferences.format.lowercase()}")
            AppLog.info("動画解析","映像=${visual?.optString("codec_name")} / ${visual?.optInt("width")}x${visual?.optInt("height")} / fps=${visual?.optString("avg_frame_rate")} (nominal=${visual?.optString("r_frame_rate")}) / 音声=${audio?.optString("codec_name")} / 時間=$duration 秒")
            val args = conversionInputArgs(source.absolutePath).toMutableList()
            var interpolating = false
            if(preferences.format == "MP3") {
                require(audio != null) { "音声がない動画です。" }
                args += listOf("-vn","-c:a","libmp3lame","-b:a",preferences.audio.removeSuffix("bps"))
            } else {
                require(visual != null) { "映像がありません。MP3を選択してください。" }
                val motionFilter = if(preferences.fps == INTERPOLATED_FPS) {
                    require(BuildConfig.BETA) { "フレーム補間はベータ版で利用できます。" }
                    interpolationFilter(frameRate(visual.optString("avg_frame_rate")) ?: frameRate(visual.optString("r_frame_rate")),duration)
                } else null
                interpolating = motionFilter != null
                if(interpolating) {
                    require(runNative("libffmpeg.so",listOf("-hide_banner","-filters")).contains("minterpolate")) { "この端末のFFmpegはフレーム補間に対応していません。" }
                    AppLog.info("フレーム補間","中間フレームを生成して60fpsにします（CPU）。")
                }
                val copy = canCopyVideo(visual.optString("codec_name"),visual.optString("pix_fmt"),preferences.fps,
                    visual.optString("avg_frame_rate"),visual.optString("r_frame_rate"))
                AppLog.info("変換方式",if(copy) "映像を再圧縮せずMP4へ保存" else "CPU変換 / 入力1スレッド・フィルター1・映像出力2 / ${preferences.fps}")
                args += listOf("-map","0:v:0","-map","0:a:0?") + videoEncodingArgs(copy,preferences.fps,motionFilter)
                args += if(audio?.optString("codec_name") == "aac") listOf("-c:a","copy") else listOf("-c:a","aac","-b:a","192k")
                args += listOf("-movflags","+faststart")
            }
            val conversionDetail = if(interpolating) "フレーム補間中（CPU・60fps）" else "${preferences.format} 変換中"
            progress("変換中", null, conversionDetail)
            args += listOf("-progress","pipe:1","-nostats",output.absolutePath)
            runNative("libffmpeg.so",args) { line ->
                if(line.startsWith("out_time_us=")) line.substringAfter('=').toDoubleOrNull()?.let {
                    progress("変換中", if(duration > 0) (it / 1e6 / duration).toFloat().coerceIn(0f,.999f) else null, conversionDetail)
                }
            }
            require(output.exists() && output.length() > 0) { "変換後のファイルがありません。" }
            if(interpolating) {
                val verified=JSONObject(runNative("libffprobe.so",listOf("-v","quiet","-print_format","json","-show_streams",output.absolutePath))).getJSONArray("streams")
                val rate=(0 until verified.length()).map { verified.getJSONObject(it) }.first { it.optString("codec_type") == "video" }.optString("avg_frame_rate")
                require(kotlin.math.abs((frameRate(rate) ?: 0.0)-60) < .01) { "補間後の60fps検証に失敗しました。" }
            }
            check()
            progress("端末へ保存中",null,saveRelativePath(preferences.format))
            return publish(output,"${safeName(video.title)} [${safeName(video.key.substringAfter(':'))}].${preferences.format.lowercase()}",preferences.format)
        } finally { work.deleteRecursively() }
    }
    private fun runNative(binary: String, args: List<String>, line: ((String)->Unit)? = null): String {
        check()
        val builder = NativeRuntime.command(context,binary,args)
        AppLog.info("ネイティブ処理","$binary 開始 / ライブラリー参照先=python,ffmpeg")
        ProcessDiagnostics.memory(context,"$binary 開始前")
        val process = builder.start(); native = process
        AppLog.info("ネイティブ処理","$binary プロセス起動完了")
        if(cancelled.get()) process.destroyForcibly()
        val output = StringBuilder()
        var lastMemory=System.nanoTime()
        var firstLine=true
        try {
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach {
                check()
                if(firstLine) {
                    firstLine=false
                    AppLog.info("ネイティブ出力","$binary 最初の出力: ${it.take(500)}")
                }
                line?.invoke(it)
                if(binary == "libffmpeg.so" && System.nanoTime()-lastMemory > 5_000_000_000L) {
                    lastMemory=System.nanoTime(); ProcessDiagnostics.memory(context,"変換中")
                }
                if(binary == "libffmpeg.so" && (it.contains("Error",ignoreCase=true) || it.contains("failed",ignoreCase=true) || it.startsWith("Stream mapping:"))) AppLog.info("FFmpeg出力",it)
                if(output.length > 100_000) output.delete(0,50_000)
                output.appendLine(it)
            } }
            val exit = process.waitFor(); check()
            AppLog.info("ネイティブ処理","$binary 終了コード=$exit")
            check(exit == 0) { "${if(binary == "libffprobe.so") "動画の解析" else "変換"}に失敗しました: ${output.takeLast(1600)}" }
            return output.toString()
        } finally { if(process.isAlive) process.destroyForcibly(); native = null }
    }
    private fun publish(file: File, name: String, format: String): String {
        val resolver = context.contentResolver
        val isVideo = format == "MP4"
        // Video/Audio insert endpoints reject Download paths; publish typed media via Downloads.
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        AppLog.info("保存","開始 / ${saveRelativePath(format)} / ${file.length()} bytes")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name)
            put(MediaStore.MediaColumns.MIME_TYPE,if(isVideo) "video/mp4" else "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH,saveRelativePath(format))
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val uri = resolver.insert(collection,values) ?: error("保存先を作成できません。")
        try {
            resolver.openOutputStream(uri,"w")!!.use { out -> file.inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                while(true) { check(); val count = input.read(buffer); if(count < 0) break; out.write(buffer,0,count) }
            } }
            check()
            check(resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null) == 1) {
                "保存したメディアを公開できませんでした。"
            }
            AppLog.info("保存","書き込み・公開完了 / $uri")
            return uri.toString()
        } catch(error: Exception) { resolver.delete(uri,null,null); throw error }
    }
    fun update() { YoutubeDL.updateYoutubeDL(context,YoutubeDL.UpdateChannel.STABLE); check() }
}
