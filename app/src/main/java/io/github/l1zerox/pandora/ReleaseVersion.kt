package io.github.l1zerox.pandora

internal const val RELEASES_URL = "https://github.com/L1ZeroX/youtube-pandora-android/releases"
internal data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int, val beta: Int? = null): Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int = compareValuesBy(this,other,{it.major},{it.minor},{it.patch},{it.beta ?: Int.MAX_VALUE})
    companion object {
        fun parse(value: String): ReleaseVersion? {
            val match=Regex("v?(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})(?:-beta\\.(\\d{1,9}))?").matchEntire(value) ?: return null
            return ReleaseVersion(match.groupValues[1].toInt(),match.groupValues[2].toInt(),match.groupValues[3].toInt(),match.groupValues[4].toIntOrNull())
        }
    }
}
internal data class ReleaseAsset(val name: String, val url: String, val uploaded: Boolean, val size: Long = 0, val digest: String = "")
internal data class ReleaseCandidate(val tag: String, val draft: Boolean, val preview: Boolean, val notes: String, val assets: List<ReleaseAsset>)
internal data class AppRelease(val version: String, val pageUrl: String, val downloadUrl: String, val notes: String, val preview: Boolean,
    val size: Long = 0, val digest: String = "", val delta: ApkDeltaRelease? = null)
internal data class ApkDeltaRelease(val baseVersion: String, val url: String, val size: Long, val digest: String)
internal fun deltaAssetName(target: String, base: String)="YoutubePandora-Android-v$target-from-v$base.pdelta.gz"
internal fun validApkDelta(delta: ApkDeltaRelease, target: String, size: Long, digest: String): Boolean =
    ReleaseVersion.parse(delta.baseVersion) != null && ReleaseVersion.parse(target) != null && delta.baseVersion != target &&
        size in 1..MAX_DELTA_APK_BYTES && delta.size > 0 && delta.size < size &&
        Regex("sha256:[a-fA-F0-9]{64}").matches(digest) && Regex("sha256:[a-fA-F0-9]{64}").matches(delta.digest) &&
        delta.url == "$RELEASES_URL/download/v$target/${deltaAssetName(target,delta.baseVersion)}"

internal fun unifiedRelease(version: ReleaseVersion): Boolean = if(version.beta == null)
    version >= ReleaseVersion(0,3,0) else version >= ReleaseVersion(0,4,0,1)

internal fun newestRelease(releases: List<ReleaseCandidate>, current: String, beta: Boolean = false, offer: Boolean = false,
                           unifiedOnly: Boolean = false): AppRelease? {
    val installed=requireNotNull(ReleaseVersion.parse(current))
    return releases.mapNotNull { release ->
        val number=ReleaseVersion.parse(release.tag) ?: return@mapNotNull null
        if(unifiedOnly && !unifiedRelease(number)) return@mapNotNull null
        if(release.draft || (number.beta != null) != beta || (!offer && number <= installed)) return@mapNotNull null
        // Both channels use one package and increasing versionCodes. An explicit channel switch
        // must not offer an older APK that Android will refuse after downloading it.
        if(number < installed) return@mapNotNull null
        // Android is currently distributed through GitHub's prerelease channel as well.
        val version=release.tag.removePrefix("v")
        val name="YoutubePandora-Android-v$version.apk"
        val download="$RELEASES_URL/download/${release.tag}/$name"
        val asset=release.assets.firstOrNull { it.name == name && it.uploaded && it.url == download } ?: return@mapNotNull null
        val deltaName=deltaAssetName(version,current)
        val delta=release.assets.firstOrNull { it.name == deltaName && it.uploaded }?.let {
            ApkDeltaRelease(current,it.url,it.size,it.digest).takeIf { patch -> validApkDelta(patch,version,asset.size,asset.digest) }
        }
        number to AppRelease(version,"$RELEASES_URL/tag/${release.tag}",download,
            release.notes.ifBlank { "更新内容はリリースページをご確認ください。" }.take(3000),release.preview,asset.size,asset.digest,delta)
    }.maxByOrNull { it.first }?.second
}
