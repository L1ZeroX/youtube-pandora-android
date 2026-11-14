package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class CarClientTrustTest {
    private val auto="fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf"
    private val assistant="f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"
    @Test fun actualReleaseCertificateAllowsVerifiedLegacyAutoWithoutMediaControlPermission() {
        val bytes=javaClass.getResourceAsStream("/car/android-auto-release.der")!!.use { it.readBytes() }
        assertEquals(auto,carCertificateHash(bytes))
        assertTrue(carClientAllowed(false,false,true,CAR_AUTO_PACKAGE,setOf(carCertificateHash(bytes))))
    }
    @Test fun androidAutoIsNotRequiredToHaveTheGoogleAppCertificate() {
        assertNotEquals(auto,assistant)
        assertTrue(carReleaseMatches(CAR_AUTO_PACKAGE,setOf(auto)))
        assertFalse(carReleaseMatches(CAR_AUTO_PACKAGE,setOf(assistant)))
    }
    @Test fun unrelatedPackagesCannotBorrowAnAllowedSigningCertificate() {
        for(name in listOf(CAR_AUTO_PACKAGE+".fake","com.google.android.gms","unknown",""))
            assertFalse(carClientAllowed(false,false,true,name,setOf(auto,assistant)))
    }
    @Test fun unverifiedPackageIdentityIsRejectedEvenWhenTheCertificateMatches() {
        assertFalse(carClientAllowed(false,false,false,CAR_AUTO_PACKAGE,setOf(auto)))
    }
    @Test fun debugAndUnknownCertificatesNeverEnableCarBrowsing() {
        for(hash in listOf("1975b2f17177bc89a5dff31f9e64a6cae281a53dc1d1d59b1d147fe1c82afa00",
            "70811a3eacfd2e83e18da9bfede52df16ce91f2e69a44d21f18ab66991130771","00".repeat(32)))
            assertFalse(carClientAllowed(false,false,true,CAR_AUTO_PACKAGE,setOf(hash)))
        assertFalse(carReleaseMatches(CAR_AUTO_PACKAGE,emptySet()))
    }
    @Test fun validatedCertificateRotationHistoryStillRecognizesTheOriginalReleaseSigner() {
        assertTrue(carReleaseMatches(CAR_AUTO_PACKAGE,setOf("11".repeat(32),auto.uppercase())))
        assertFalse(carReleaseMatches("another.package",setOf(auto)))
    }
    @Test fun appAndFrameworkTrustedControllersKeepTheirExistingAccess() {
        assertTrue(carClientAllowed(true,false,false,"own",emptySet()))
        assertTrue(carClientAllowed(false,true,false,"android",emptySet()))
    }
    @Test fun assistantAndArbitraryCertificateBytesAreCheckedAgainstTheirOwnPackage() {
        assertTrue(carReleaseMatches("com.google.android.googlequicksearchbox",setOf(assistant)))
        assertFalse(carReleaseMatches("com.google.android.googlequicksearchbox",setOf(auto)))
        for(bytes in listOf(byteArrayOf(),byteArrayOf(1,2,3)))
            assertFalse(carReleaseMatches(CAR_AUTO_PACKAGE,setOf(carCertificateHash(bytes))))
    }
}
