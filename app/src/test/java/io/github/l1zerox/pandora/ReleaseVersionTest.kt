package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun libraryBetaCanBeSelectedFromItsBaseAndReturnOnExplicitRequest() {
        val releases=listOf(release("0.4.5",preview=false),release("0.4.6-beta.1"))
        assertEquals("0.4.6-beta.1",newestRelease(releases,"0.4.5",beta=true,offer=true)!!.version)
        assertEquals("0.4.5",newestRelease(releases,"0.4.6-beta.1",offer=true)!!.version)
        assertNull(newestRelease(releases,"0.4.6-beta.1"))
    }
    @Test fun promotedReleaseUpgradesBothInstalledChannels() {
        val stable=release("0.4.0",preview=false)
        for(installed in listOf("0.3.5","0.4.0-beta.8")) {
            assertEquals("0.4.0",newestRelease(listOf(stable),installed,unifiedOnly=true)!!.version)
        }
    }
    @Test fun promotedReleaseDoesNotOfferArchivedBetaDowngrade() {
        val old=release("0.4.0-beta.8")
        assertNull(newestRelease(listOf(old),"0.4.0",beta=true,offer=true,unifiedOnly=true))
        val next=release("0.4.1-beta.1")
        assertEquals("0.4.1-beta.1",newestRelease(listOf(old,next),"0.4.0",beta=true,offer=true,unifiedOnly=true)!!.version)
    }
    @Test fun unifiedAppsOnlyOfferInstallCompatibleChannels() {
        val releases=listOf(release("0.2.7"),release("0.3.0-beta.3"),release("0.3.0"),release("0.4.0-beta.1"))
        assertEquals("0.3.0",newestRelease(releases,"0.4.0-beta.1",offer=true,unifiedOnly=true)!!.version)
        assertEquals("0.4.0-beta.1",newestRelease(releases,"0.3.0",beta=true,offer=true,unifiedOnly=true)!!.version)
        assertNull(newestRelease(listOf(release("0.3.0-beta.3")),"0.3.0",beta=true,offer=true,unifiedOnly=true))
        assertNull(newestRelease(listOf(release("0.2.7")),"0.4.0-beta.1",offer=true,unifiedOnly=true))
    }
    @Test fun returnToReleaseOffersOlderVersionOnlyOnExplicitRequest() {
        val releases=listOf(release("0.2.5"),release("0.2.6"),release("0.3.0-beta.2"),release("0.4.0",draft=true))
        assertNull(newestRelease(releases,"0.3.0-beta.2"))
        assertEquals("0.2.6",newestRelease(releases,"0.3.0-beta.2",beta=false,offer=true)!!.version)
        assertNull(newestRelease(listOf(release("0.3.0-beta.2")),"0.3.0-beta.2",beta=false,offer=true))
    }
    @Test fun betaChannelRequiresOptInAndHasNumericOrdering() {
        val beta=release("0.3.0-beta.1")
        assertNull(newestRelease(listOf(beta),"0.2.5"))
        assertEquals("0.3.0-beta.1",newestRelease(listOf(beta),"0.2.5",beta=true)!!.version)
        assertNull(newestRelease(listOf(beta),"0.3.0-beta.1",beta=true))
        assertNotNull(newestRelease(listOf(beta),"0.3.0-beta.1",beta=true,offer=true))
        assertTrue(ReleaseVersion.parse("0.3.0-beta.10")!! > ReleaseVersion.parse("0.3.0-beta.2")!!)
        assertTrue(ReleaseVersion.parse("0.3.0")!! > ReleaseVersion.parse("0.3.0-beta.10")!!)
    }
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
