package io.github.l1zerox.pandora

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Receive the selected share target without bringing the download screen over the source app. */
class ShareActivity : Activity() {
    private var routed=false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(intent?.action != Intent.ACTION_SEND) { finish(); return }
        if(savedInstanceState == null) {
            val text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            val added=Store.receiveShare(text)
            Toast.makeText(this,if(added > 0) "$added 件をDLキューに追加しました。" else "追加できる新しいURLはありません。",Toast.LENGTH_SHORT).show()
        }
    }
    override fun onResume() {
        super.onResume()
        if(routed || isFinishing) return
        routed=true
        // Start while this user-launched activity is resumed, including a cold service start.
        if(!BubbleRuntime.sync(this)) {
            startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }
}
