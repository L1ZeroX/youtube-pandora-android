package io.github.l1zerox.pandora

import org.json.JSONObject

internal const val UPDATE_RELAUNCH_WINDOW_MS=2*60*60*1000L

/** A short-lived request made immediately before opening Android's update confirmation. */
internal data class UpdateRelaunchRequest(val version: String, val fromCode: Int, val requestedAt: Long) {
    fun installed(currentVersion: String, currentCode: Int)=version == currentVersion && fromCode in 1 until currentCode
    fun eligible(currentVersion: String, currentCode: Int, now: Long)=
        installed(currentVersion,currentCode) && requestedAt > 0 && now >= requestedAt && now-requestedAt <= UPDATE_RELAUNCH_WINDOW_MS
    fun json(): String=JSONObject().put("version",version).put("fromCode",fromCode).put("requestedAt",requestedAt).toString()
}

internal fun parseUpdateRelaunch(text: String?): UpdateRelaunchRequest? = runCatching {
    val json=JSONObject(text ?: return null)
    UpdateRelaunchRequest(json.getString("version"),json.getInt("fromCode"),json.getLong("requestedAt")).also {
        require(ReleaseVersion.parse(it.version) != null && it.fromCode > 0 && it.requestedAt > 0)
    }
}.getOrNull()
