package io.github.l1zerox.pandora

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import com.yausername.youtubedl_android.YoutubeDLRequest

internal const val JAPANESE_AUDIO_PATTERN="(?i)^(ja|jpn)([-_](?!desc(?:$|[-_]))[a-z0-9]+)*$"
internal const val AUDIO_SELECTION_PREFIX="PANDORA_AUDIO|"
internal const val AUDIO_FILE_PREFIX="PANDORA_AUDIO_FILE|"
internal const val AUDIO_FILE_TEMPLATE="after_move:PANDORA_AUDIO_FILE|%(filepath)s"
internal const val AUDIO_OUTPUT_NAME="source.%(requested_formats.1.language,language|und)S.%(format_id)S.%(ext)s"
// Only language/codec/note fields are printed; never stream URLs, cookies or the full info JSON.
internal const val AUDIO_SELECTION_TEMPLATE="before_dl:PANDORA_AUDIO|{\"selected\":%(requested_formats.:.{format_id,acodec,language,format_note})j,\"single\":%(.{format_id,acodec,language,format_note})j,\"available\":%(formats.:.{format_id,acodec,language,format_note})j}"

internal fun YoutubeDLRequest.addAudioSelectionOptions() {
    addOption("--print",AUDIO_SELECTION_TEMPLATE)
    addOption("--print",AUDIO_FILE_TEMPLATE)
    addOption("--no-simulate"); addOption("--no-quiet"); addOption("--progress")
}

internal fun isJapaneseAudio(language: String): Boolean = Regex(JAPANESE_AUDIO_PATTERN).matches(language.trim())

internal fun preferredAudioFormatSelector(p: Preferences, height: String): String {
    val japanese="[language~='$JAPANESE_AUDIO_PATTERN']"
    val original="[format_note~='(?i)original']"
    fun selection(filter: String) = if(p.format == "MP3") "bestaudio$filter/best$filter"
        else "bestvideo$height+bestaudio$filter/best$height$filter"
    return listOf(selection(japanese),selection(original),selection("")).joinToString("/")
}

internal data class AudioTrack(val codec: String, val language: String, val note: String = "") {
    val hasAudio get() = codec.isNotBlank() && codec !in listOf("none","null","NA")
}
private fun normalizedAudioNote(note: String)=Normalizer.normalize(note,Normalizer.Form.NFKC)
    .replace(Regex("[\u2010-\u2015\u2212_]"),"-")
internal fun isOriginalAudioNote(note: String)=Regex("(?i)(?<!non-)\\boriginal\\b|オリジナル|元の音声|元音声").containsMatchIn(normalizedAudioNote(note))
internal fun isAutoDubbedAudioNote(note: String)=Regex("(?i)\\b(?:auto(?:matically)?[\\s-]*dubb(?:ed|ing)|dubb(?:ed|ing)[\\s-]*automatically)\\b|自動(?:音声)?(?:吹き替え|吹替え?|ダビング)|オートダビング")
    .containsMatchIn(normalizedAudioNote(note))
internal data class AudioDecision(val japaneseAvailable: Boolean?, val usesJapanese: Boolean, val original: Boolean, val autoDubbed: Boolean) {
    val label: String get() = when {
        usesJapanese && autoDubbed -> "日本語の自動吹き替えを使用"
        usesJapanese -> "日本語音声を使用"
        autoDubbed && !original -> "自動吹き替え音声を使用"
        japaneseAvailable == true -> "日本語音声を選択できず、通常音声を使用"
        japaneseAvailable == false && original -> "日本語音声なし・元音声を使用"
        japaneseAvailable == false -> "日本語音声なし・通常音声を使用"
        else -> "音声の言語を判別できず、通常音声を使用"
    }
}
internal fun audioDecision(selected: List<AudioTrack>, available: List<AudioTrack>): AudioDecision {
    val audio=selected.filter { it.hasAudio }
    val offered=(available+audio).filter { it.hasAudio }
    val japanese=offered.any { isJapaneseAudio(it.language) }
    val known=offered.any { it.language.isNotBlank() && it.language !in listOf("und","null","NA") }
    return AudioDecision(if(japanese) true else if(known) false else null,
        audio.any { isJapaneseAudio(it.language) },audio.any { isOriginalAudioNote(it.note) },
        audio.any { isAutoDubbedAudioNote(it.note) && !isOriginalAudioNote(it.note) })
}

internal fun parseAudioSelection(line: String): AudioDecision? {
    val text=line.trim()
    if(!text.startsWith(AUDIO_SELECTION_PREFIX) || text.length > 256_000) return null
    return runCatching {
        val root=JSONObject(text.removePrefix(AUDIO_SELECTION_PREFIX))
        fun track(item: JSONObject)=AudioTrack(item.optString("acodec",""),item.optString("language",""),item.optString("format_note",""))
        fun tracks(array: JSONArray?) = if(array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::track) }
        val offered=root.optJSONArray("available")
        val offeredItems=if(offered == null) emptyList() else (0 until offered.length()).mapNotNull { offered.optJSONObject(it) }
        fun complete(item: JSONObject): AudioTrack {
            val id=item.optString("format_id")
            val exact=offeredItems.firstOrNull { id.isNotBlank() && it.optString("format_id") == id }
            fun field(key: String)=item.optString(key).takeUnless { it.isBlank() || it in listOf("NA","null") }
                ?: exact?.optString(key).orEmpty()
            // Some clients omit the note on requested_formats; only borrow from the same format ID.
            val note=listOf(field("format_note"),exact?.optString("format_note").orEmpty()).filter { it.isNotBlank() }.distinct().joinToString(" / ")
            return AudioTrack(field("acodec"),field("language"),note)
        }
        val requested=root.optJSONArray("selected")?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::complete) } }.orEmpty()
        val single=root.optJSONObject("single")
        val formatId=single?.optString("format_id").orEmpty()
        // A video's root language can be its original language, not the selected muxed audio.
        val exact=offeredItems.firstOrNull { formatId.isNotBlank() && it.optString("format_id") == formatId }
        val mergedIds=formatId.split('+').filter { it.isNotBlank() }
        val merged=mergedIds.mapNotNull { id -> offeredItems.firstOrNull { it.optString("format_id") == id } }
        val selected=requested.ifEmpty {
            if(mergedIds.size > 1 && merged.size == mergedIds.size) merged.map(::track)
            else (exact ?: single)?.let { listOf(track(it)) }.orEmpty()
        }
        audioDecision(selected,tracks(offered))
    }.getOrNull()
}

internal fun selectedAudioSource(work: File, line: String): File? = runCatching {
    if(!line.startsWith(AUDIO_FILE_PREFIX)) return null
    File(line.removePrefix(AUDIO_FILE_PREFIX)).canonicalFile.takeIf {
        it.parentFile == work.canonicalFile && it.name.startsWith("source.") &&
            it.extension.lowercase() !in listOf("part","ytdl","json","")
    }
}.getOrNull()
