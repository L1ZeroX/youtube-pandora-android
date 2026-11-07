package io.github.l1zerox.pandora

import android.content.ClipboardManager
import android.content.Context

/** Reads only while the panel the user opened actually owns input focus. */
internal class BubbleClipboard(context: Context, private val canRead: ()->Boolean, private val message: (String)->Unit) {
    private val clipboard=context.getSystemService(ClipboardManager::class.java)
    private var listening=false
    private var lastImport: Pair<Long,List<String>>? = null
    private val listener=ClipboardManager.OnPrimaryClipChangedListener { read() }
    fun focusChanged(focused: Boolean) {
        val enabled=focused && canRead()
        if(enabled && !listening) {
            clipboard.addPrimaryClipChangedListener(listener); listening=true; read()
        } else if(!enabled) close()
    }
    private fun read() {
        if(!listening || !canRead()) return
        try {
            val clip=clipboard.primaryClip
            if(clip == null) { message("コピーしたYouTube URLを取得できません。共有からも追加できます。"); return }
            if(clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE",false) == true) {
                message("機密としてコピーされた内容は追加しません。"); return
            }
            // Do not coerce content URIs or retain/log unrelated clipboard text.
            val text=(0 until minOf(clip.itemCount,8)).joinToString("\n") { index ->
                val item=clip.getItemAt(index)
                (item.text?.take(65536)?.toString() ?: item.uri?.toString()).orEmpty()
            }
            val urls=clipboardYoutubeUrls(text)
            if(urls.isEmpty()) { message("コピーしたYouTube動画のURLはありません。"); return }
            val token=clip.description.timestamp to urls.map(::mediaIdentity)
            val existing=Store.state.value.videos.map { mediaIdentity(it.url) }.toSet()
            if(urls.all { mediaIdentity(it) in existing }) {
                lastImport=token; message("コピーした動画は追加済みです。"); return
            }
            if(lastImport == token) {
                message("このコピーは確認済みです。追加し直す場合は再コピーまたは共有してください。"); return
            }
            val added=Store.receiveShare(urls.joinToString("\n"))
            lastImport=token
            message("コピーしたYouTube動画を $added 件追加しました。")
        } catch(_: Exception) {
            message("コピー内容を読み取れません。YouTubeの共有から追加してください。")
        }
    }
    fun close() {
        if(listening) clipboard.removePrimaryClipChangedListener(listener)
        listening=false
    }
}
