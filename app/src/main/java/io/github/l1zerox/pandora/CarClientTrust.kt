package io.github.l1zerox.pandora

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal const val CAR_AUTO_PACKAGE="com.google.android.projection.gearhead"

// Release signing-certificate SHA-256 values from Google's UAMP caller allowlist; no debug keys.
// https://github.com/android/uamp/blob/media3/common/src/main/res/xml/allowed_media_browser_callers.xml
internal val carReleaseCertificates=mapOf(
    CAR_AUTO_PACKAGE to "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
    "com.google.android.googlequicksearchbox" to "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83")

internal fun carReleaseMatches(packageName: String, certificateHashes: Set<String>): Boolean =
    carReleaseCertificates[packageName]?.let { expected -> certificateHashes.any { it.equals(expected,ignoreCase=true) } } == true

internal fun carClientAllowed(ownUid: Boolean, mediaTrusted: Boolean, packageVerified: Boolean,
    packageName: String, certificateHashes: Set<String>) = ownUid || mediaTrusted || packageVerified && carReleaseMatches(packageName,certificateHashes)

internal fun carCertificateHash(certificate: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }

private val carCertificateCache=ConcurrentHashMap<List<Signature>,Set<String>>()
internal fun carInstalledCertificateHashes(context: Context, packageName: String): Set<String> {
    if(packageName !in carReleaseCertificates) return emptySet()
    return runCatching {
        val signing=context.packageManager.getPackageInfo(packageName,PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
        val history=if(signing?.hasMultipleSigners() == true) null else signing?.signingCertificateHistory
        val signatures=history.orEmpty().toList()
        carCertificateCache.computeIfAbsent(signatures) { values -> values.map { carCertificateHash(it.toByteArray()) }.toSet() }
    }.getOrDefault(emptySet())
}

internal fun carReleaseInstalled(context: Context, packageName: String)=carReleaseMatches(packageName,carInstalledCertificateHashes(context,packageName))
