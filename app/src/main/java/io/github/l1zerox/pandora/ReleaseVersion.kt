package io.github.l1zerox.pandora

internal const val RELEASES_URL = "https://github.com/L1ZeroX/youtube-pandora-android/releases"
internal data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int): Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int = compareValuesBy(this,other,{it.major},{it.minor},{it.patch})
    companion object {
        fun parse(value: String): ReleaseVersion? {
            val match=Regex("v?(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})").matchEntire(value) ?: return null
            return ReleaseVersion(match.groupValues[1].toInt(),match.groupValues[2].toInt(),match.groupValues[3].toInt())
        }
    }
}
internal data class ReleaseAsset(val name: String, val url: String, val uploaded: Boolean)
internal data class ReleaseCandidate(val tag: String, val draft: Boolean, val preview: Boolean, val notes: String, val assets: List<ReleaseAsset>)
internal data class AppRelease(val version: String, val pageUrl: String, val downloadUrl: String, val notes: String, val preview: Boolean)

internal fun newestRelease(releases: List<ReleaseCandidate>, current: String): AppRelease? {
    val installed=requireNotNull(ReleaseVersion.parse(current))
    return releases.mapNotNull { release ->
        val number=ReleaseVersion.parse(release.tag) ?: return@mapNotNull null
        if(release.draft || number <= installed) return@mapNotNull null
        // Android is currently distributed through GitHub's prerelease channel as well.
        val version=release.tag.removePrefix("v")
        val name="YoutubePandora-Android-v$version.apk"
        val download="$RELEASES_URL/download/${release.tag}/$name"
        if(release.assets.none { it.name == name && it.uploaded && it.url == download }) return@mapNotNull null
        number to AppRelease(version,"$RELEASES_URL/tag/${release.tag}",download,
            release.notes.ifBlank { "更新内容はリリースページをご確認ください。" }.take(3000),release.preview)
    }.maxByOrNull { it.first }?.second
}
