package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    private fun release(version: String, draft: Boolean = false, preview: Boolean = true): ReleaseCandidate {
        val tag="v$version"
        val name="YoutubePandora-Android-v$version.apk"
        return ReleaseCandidate(tag,draft,preview,"更新内容",listOf(ReleaseAsset(name,"$RELEASES_URL/download/$tag/$name",true)))
    }
    @Test fun comparesNumbersAndRejectsInvalidTags() {
        assertTrue(ReleaseVersion.parse("v0.10.0")!! > ReleaseVersion.parse("0.9.9")!!)
        listOf("0.1","v0.2.x","0.2.0-rc1","../0.2.0","0.2.0\n","").forEach { assertNull(ReleaseVersion.parse(it)) }
    }
    @Test fun androidPreviewReleasesAreIncludedAndHighestVersionWins() {
        val result=newestRelease(listOf(release("0.9.0",preview=false),release("0.11.0"),release("0.10.0")),"0.9.0")!!
        assertEquals("0.11.0",result.version)
        assertTrue(result.preview)
        assertTrue(result.downloadUrl.endsWith("YoutubePandora-Android-v0.11.0.apk"))
    }
    @Test fun equalOlderDraftOrMissingApkDoesNotNotify() {
        assertNull(newestRelease(listOf(release("0.1.0"),release("0.2.0"),release("0.3.0",draft=true),release("0.4.0").copy(assets=emptyList())),"0.2.0"))
        assertNull(newestRelease(emptyList(),"0.2.0"))
    }
    @Test fun onlyUploadedApkFromOurRepositoryIsAccepted() {
        val candidate=release("0.3.0")
        val asset=candidate.assets.single()
        listOf(asset.copy(url="https://example.com/app.apk"),asset.copy(uploaded=false),asset.copy(name="source.zip")).forEach {
            assertNull(newestRelease(listOf(candidate.copy(assets=listOf(it))),"0.2.0"))
        }
    }
    @Test fun releaseNotesAreBounded() {
        assertEquals(3000,newestRelease(listOf(release("0.3.0").copy(notes="あ".repeat(8000))),"0.2.0")!!.notes.length)
    }
}
