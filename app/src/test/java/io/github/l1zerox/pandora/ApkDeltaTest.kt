package io.github.l1zerox.pandora

import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class ApkDeltaTest {
    private fun sha(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun fixture(block: (File,File,File)->Unit) {
        val dir=kotlin.io.path.createTempDirectory("pandora-delta").toFile()
        try { block(File(dir,"base.apk"),File(dir,"delta.gz"),File(dir,"out.apk")) }
        finally { listOf("base.apk","delta.gz","out.apk").forEach { File(dir,it).delete() }; dir.delete() }
    }
    private fun patch(file: File, base: ByteArray, target: ByteArray, count: Int=2, commands: DataOutputStream.()->Unit) {
        DataOutputStream(GZIPOutputStream(file.outputStream())).use {
            it.writeBytes("PNDRDLT1"); it.writeLong(base.size.toLong()); it.writeLong(target.size.toLong())
            it.write(sha(base)); it.write(sha(target)); it.writeInt(count); it.commands()
        }
    }
    private fun DataOutputStream.copy(offset: Long, length: Long) { writeByte(1); writeLong(offset); writeLong(length) }
    private fun DataOutputStream.data(bytes: ByteArray) { writeByte(2); writeLong(bytes.size.toLong()); write(bytes) }
    private fun apply(base: File, delta: File, output: File, target: ByteArray)=
        applyApkDelta(base,delta,output,target.size.toLong(),"sha256:"+sha(target).hexDigest())

    @Test fun reconstructsAcrossBufferBoundariesAndReportsExactProgress() = fixture { base,delta,out ->
        val old=ByteArray(400_000) { (it%251).toByte() }; base.writeBytes(old)
        val added="changed signed header".toByteArray(); val target=added+old.copyOfRange(50,350_000)
        patch(delta,old,target) { data(added); copy(50,349_950) }
        var last=0L
        applyApkDelta(base,delta,out,target.size.toLong(),"sha256:"+sha(target).hexDigest()) { assertTrue(it >= last); last=it }
        assertArrayEquals(target,out.readBytes()); assertEquals(target.size.toLong(),last); assertArrayEquals(old,base.readBytes())
    }
    @Test fun wrongBaseAndTargetMetadataNeverLeaveAnInstallableOutput() = fixture { base,delta,out ->
        val old="original".toByteArray(); val target="new".toByteArray()
        patch(delta,old,target,1) { data(target) }; base.writeText("modified")
        assertTrue(runCatching { apply(base,delta,out,target) }.isFailure); assertFalse(out.exists())
        base.writeBytes(old)
        assertTrue(runCatching { apply(base,delta,out,"bad".toByteArray()) }.isFailure); assertFalse(out.exists())
    }
    @Test fun invalidCopyRangesAndUnknownInstructionsAreRejected() = fixture { base,delta,out ->
        val old="old data".toByteArray(); base.writeBytes(old); val target="new".toByteArray()
        for(offset in listOf(-1L,8L,Long.MAX_VALUE)) {
            patch(delta,old,target,1) { copy(offset,3) }
            assertTrue(runCatching { apply(base,delta,out,target) }.isFailure); assertFalse(out.exists())
        }
        patch(delta,old,target,1) { writeByte(99) }
        assertTrue(runCatching { apply(base,delta,out,target) }.isFailure); assertFalse(out.exists())
    }
    @Test fun truncatedOversizedOrTrailingLiteralDataIsRejected() = fixture { base,delta,out ->
        val old="old".toByteArray(); base.writeBytes(old); val target="new".toByteArray()
        for(length in listOf(0L,4L,Long.MAX_VALUE)) {
            patch(delta,old,target,1) { writeByte(2); writeLong(length) }
            assertTrue(runCatching { apply(base,delta,out,target) }.isFailure); assertFalse(out.exists())
        }
        patch(delta,old,target,1) { writeByte(2); writeLong(3); writeByte(1) }
        assertTrue(runCatching { apply(base,delta,out,target) }.isFailure); assertFalse(out.exists())
        patch(delta,old,target,1) { data(target); writeByte(1) }
        assertTrue(runCatching { apply(base,delta,out,target) }.isFailure); assertFalse(out.exists())
    }
    @Test fun corruptedOutputAndGzipChecksumCannotPass() = fixture { base,delta,out ->
        val old="old".toByteArray(); base.writeBytes(old); val target="new".toByteArray()
        patch(delta,old,target,1) { data("bad".toByteArray()) }
        assertTrue(runCatching { apply(base,delta,out,target) }.isFailure); assertFalse(out.exists())
        patch(delta,old,target,1) { data(target) }
        val bytes=delta.readBytes(); bytes[bytes.size-8]=(bytes[bytes.size-8].toInt() xor 1).toByte(); delta.writeBytes(bytes)
        assertTrue(runCatching { apply(base,delta,out,target) }.isFailure); assertFalse(out.exists())
    }
    @Test fun inputCannotBeOverwrittenAndHugeCommandCountIsRejected() = fixture { base,delta,out ->
        val old="old".toByteArray(); base.writeBytes(old); val target="new".toByteArray()
        patch(delta,old,target,100_001) {}
        assertTrue(runCatching { apply(base,delta,out,target) }.isFailure); assertFalse(out.exists())
        assertTrue(runCatching { apply(base,delta,base,target) }.isFailure); assertArrayEquals(old,base.readBytes())
    }
    @Test fun releaseSelectionRequiresAnExactBaseCanonicalUrlAndBothDigests() {
        val target="0.4.25-beta.1"; val base="0.4.24"
        val url="$RELEASES_URL/download/v$target/"
        val apk=ReleaseAsset("YoutubePandora-Android-v$target.apk",url+"YoutubePandora-Android-v$target.apk",true,1000,"sha256:"+"aa".repeat(32))
        val name=deltaAssetName(target,base)
        val patch=ReleaseAsset(name,url+name,true,100,"sha256:"+"bb".repeat(32))
        fun selected(asset: ReleaseAsset, version: String=base, full: ReleaseAsset=apk)=newestRelease(
            listOf(ReleaseCandidate("v$target",false,true,"",listOf(full,asset))),version,beta=true)!!
        val result=selected(patch)
        assertNotNull(result.delta); assertEquals(result,parseApkRelease(result.updateJson()))
        assertNull(selected(patch,"0.4.23").delta)
        assertNull(selected(patch.copy(url="https://example.com/patch")).delta)
        assertNull(selected(patch.copy(digest="")).delta)
        assertNull(selected(patch.copy(size=1000)).delta)
        assertNull(selected(patch.copy(uploaded=false)).delta)
        assertNull(selected(patch,full=apk.copy(digest="")).delta)
        assertFalse(validApkRelease(result.copy(delta=result.delta!!.copy(url="https://example.com/patch"))))
    }
}
