package io.github.l1zerox.pandora

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

internal fun AppRelease.updateJson(): String = JSONObject().put("version",version).put("page",pageUrl)
    .put("url",downloadUrl).put("notes",notes).put("preview",preview).put("size",size).put("digest",digest).toString()

internal fun parseApkRelease(text: String?): AppRelease? = runCatching {
    val json=JSONObject(text ?: return null)
    AppRelease(json.getString("version"),json.getString("page"),json.getString("url"),json.optString("notes"),
        json.optBoolean("preview"),json.optLong("size"),json.optString("digest")).also { require(validApkRelease(it)) }
}.getOrNull()

internal fun validApkRelease(release: AppRelease): Boolean = ReleaseVersion.parse(release.version) != null &&
    release.downloadUrl == "$RELEASES_URL/download/v${release.version}/YoutubePandora-Android-v${release.version}.apk" &&
    release.pageUrl == "$RELEASES_URL/tag/v${release.version}" && release.size >= 0 &&
    (release.digest in listOf("","null") || Regex("sha256:[a-fA-F0-9]{64}").matches(release.digest))

internal fun verifyApkBytes(file: File, release: AppRelease) {
    require(file.isFile && file.length() > 0) { "APKがありません。もう一度ダウンロードしてください。" }
    require(release.size <= 0 || file.length() == release.size) { "APKのサイズが一致しません。再ダウンロードしてください。" }
    if(release.digest !in listOf("","null")) {
        require(Regex("sha256:[a-fA-F0-9]{64}").matches(release.digest)) { "APKの検証情報が不正です。" }
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer=ByteArray(128*1024)
            while(true) { val n=input.read(buffer); if(n < 0) break; digest.update(buffer,0,n) }
        }
        val actual=digest.digest().joinToString("") { "%02x".format(it) }
        require(actual.equals(release.digest.substringAfter(':'),true)) { "APKの検証に失敗しました。再ダウンロードしてください。" }
    }
}

internal fun compatibleUpdate(packageName: String, expectedPackage: String, version: String?, expectedVersion: String,
    code: Long, installedCode: Long, signers: Set<String>, installedSigners: Set<String>): Boolean =
    packageName == expectedPackage && version == expectedVersion && code >= installedCode &&
        signers.isNotEmpty() && signers == installedSigners

internal fun installedApkUpdate(target: String, fromCode: Int, currentVersion: String, currentCode: Int): Boolean {
    val wanted=ReleaseVersion.parse(target) ?: return false
    val current=ReleaseVersion.parse(currentVersion) ?: return false
    return fromCode < currentCode && wanted <= current
}
