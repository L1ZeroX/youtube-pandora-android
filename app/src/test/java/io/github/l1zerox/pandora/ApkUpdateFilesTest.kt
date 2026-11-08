package io.github.l1zerox.pandora

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ApkUpdateFilesTest {
    @Test fun staleRequestsCannotDownloadAnOlderReleaseButPromotionAndSameVersionAreAllowed() {
        assertFalse(apkVersionCanUpdate("0.4.24","0.4.25-beta.2"))
        assertFalse(apkVersionCanUpdate("0.4.25-beta.1","0.4.25-beta.2"))
        assertTrue(apkVersionCanUpdate("0.4.25","0.4.25-beta.2"))
        assertTrue(apkVersionCanUpdate("0.4.25-beta.2","0.4.25-beta.2"))
        assertFalse(apkVersionCanUpdate("invalid","0.4.25-beta.2"))
        assertFalse(apkVersionCanUpdate("0.4.25","invalid"))
    }
    private val release=AppRelease("0.4.17","$RELEASES_URL/tag/v0.4.17",
        "$RELEASES_URL/download/v0.4.17/YoutubePandora-Android-v0.4.17.apk","更新",false)

    @Test fun releaseRecordRoundTripsWithIntegrityInformation() {
        val item=release.copy(size=12345,digest="sha256:"+"ab".repeat(32))
        assertEquals(item,parseApkRelease(item.updateJson()))
        assertNull(parseApkRelease("broken")); assertNull(parseApkRelease(null))
    }
    @Test fun onlyCanonicalProjectApksCanBeRequested() {
        assertTrue(validApkRelease(release))
        for(url in listOf(release.downloadUrl.replace("https:","http:"),"https://evil.example/app.apk",release.downloadUrl+"?redirect=1")) {
            assertFalse(validApkRelease(release.copy(downloadUrl=url)))
        }
        assertFalse(validApkRelease(release.copy(version="../file")))
        assertFalse(validApkRelease(release.copy(size=-1)))
        assertFalse(validApkRelease(release.copy(digest="sha256:invalid")))
    }
    @Test fun completeBytesPassButTruncationAndCorruptionFail() {
        val file=File.createTempFile("apk-update-", ".apk")
        try {
            val bytes="fixture APK bytes".toByteArray()
            file.writeBytes(bytes)
            val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val item=release.copy(size=bytes.size.toLong(),digest="sha256:$hash")
            verifyApkBytes(file,item)
            file.writeBytes(bytes.dropLast(1).toByteArray())
            assertTrue(runCatching { verifyApkBytes(file,item) }.isFailure)
            file.writeBytes(ByteArray(bytes.size))
            assertTrue(runCatching { verifyApkBytes(file,item) }.isFailure)
        } finally { file.delete() }
    }
    @Test fun oldReleaseWithoutDigestStillNeedsNonEmptyCompleteFile() {
        val file=File.createTempFile("apk-update-", ".apk")
        try {
            assertTrue(runCatching { verifyApkBytes(file,release) }.isFailure)
            file.writeText("APK")
            verifyApkBytes(file,release.copy(size=3,digest="null"))
            assertTrue(runCatching { verifyApkBytes(file,release.copy(size=4)) }.isFailure)
        } finally { file.delete() }
    }
    @Test fun installationRejectsDifferentPackageSignatureVersionAndDowngrade() {
        fun compatible(pkg: String="app", version: String="0.4.17", code: Long=38, signers: Set<String> = setOf("key")) =
            compatibleUpdate(pkg,"app",version,"0.4.17",code,37,signers,setOf("key"))
        assertTrue(compatible()); assertTrue(compatible(code=37))
        assertFalse(compatible(pkg="other")); assertFalse(compatible(version="0.4.16"))
        assertFalse(compatible(code=36)); assertFalse(compatible(signers=setOf("other")))
        assertFalse(compatible(signers=emptySet())); assertFalse(compatible(signers=setOf("key","extra")))
    }
    @Test fun cleanupRequiresSuccessfulVersionChangeNotMerelyOpeningInstaller() {
        assertTrue(installedApkUpdate("0.4.17",37,"0.4.17",38))
        assertTrue(installedApkUpdate("0.4.17",37,"0.4.18",39))
        assertFalse(installedApkUpdate("0.4.17",37,"0.4.16",37))
        assertFalse(installedApkUpdate("0.4.17",38,"0.4.17",38))
    }
    @Test fun releaseSelectionCarriesOnlyTheMatchingUploadedApkIntegrity() {
        val name="YoutubePandora-Android-v0.4.17.apk"
        val candidate=ReleaseCandidate("v0.4.17",false,false,"",listOf(
            ReleaseAsset("source.zip","https://example.com",true,5,"sha256:"+"aa".repeat(32)),
            ReleaseAsset(name,release.downloadUrl,true,99,"sha256:"+"bb".repeat(32))))
        val selected=newestRelease(listOf(candidate),"0.4.16")!!
        assertEquals(99L,selected.size); assertEquals("sha256:"+"bb".repeat(32),selected.digest)
    }
}
