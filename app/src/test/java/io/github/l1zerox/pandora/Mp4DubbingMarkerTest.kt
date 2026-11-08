package io.github.l1zerox.pandora

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class Mp4DubbingMarkerTest {
    private fun atom(type: String, data: ByteArray, large: Boolean = false): ByteArray =
        ByteBuffer.allocate(if(large) 16 else 8).putInt(if(large) 1 else data.size+8)
            .put(type.toByteArray(Charsets.ISO_8859_1)).apply { if(large) putLong(data.size+16L) }.array()+data
    private fun tagged(text: String = AUTO_DUBBED_MARKER, utf16: Boolean = false, large: Boolean = false): ByteArray {
        val data=ByteBuffer.allocate(8).putInt(if(utf16) 2 else 1).putInt(0).array()+text.toByteArray(if(utf16) Charsets.UTF_16BE else Charsets.UTF_8)
        return atom("moov",atom("udta",atom("meta",ByteArray(4)+atom("ilst",atom("©gen",atom("data",data))))),large)
    }
    private fun probe(data: ByteArray)=mp4DubbingMarker(data.size.toLong()) { position,count ->
        if(position+count > data.size || position < 0) null else data.copyOfRange(position.toInt(),position.toInt()+count)
    }
    @Test fun readsCustomGenreWithUtf8Utf16ExtendedSizeAndZeroPadding() {
        assertTrue(probe(tagged()))
        assertTrue(probe(tagged(utf16=true)))
        assertTrue(probe(tagged(large=true)))
        assertTrue(probe(tagged(" $AUTO_DUBBED_MARKER\u0000")))
    }
    @Test fun doesNotGuessFromJapaneseLanguageOrAnArbitraryMarkerInVideoData() {
        assertFalse(probe(tagged("jpn")))
        assertFalse(probe(tagged("Japanese")))
        assertFalse(probe(atom("mdat",AUTO_DUBBED_MARKER.toByteArray())))
        assertFalse(probe(atom("moov",atom("free",AUTO_DUBBED_MARKER.toByteArray()))))
    }
    @Test fun skipsMultiGigabyteMediaPayloadAndReadsOnlyMetadataAtTheEnd() {
        val payloadSize=5_000_000_000L
        val header=ByteBuffer.allocate(16).putInt(1).put("mdat".toByteArray()).putLong(payloadSize).array()
        val metadata=tagged()
        var readBytes=0
        assertTrue(mp4DubbingMarker(payloadSize+metadata.size) { position,count ->
            readBytes+=count
            when {
                position < 16 && position+count <= 16 -> header.copyOfRange(position.toInt(),position.toInt()+count)
                position >= payloadSize && position+count <= payloadSize+metadata.size ->
                    metadata.copyOfRange((position-payloadSize).toInt(),(position-payloadSize).toInt()+count)
                else -> error("Video payload must not be read")
            }
        })
        assertTrue(readBytes < 512)
    }
    @Test fun rejectsTruncatedOverflowingOrUnrelatedContainers() {
        val valid=tagged()
        for(length in 0 until valid.size) assertFalse("length=$length",probe(valid.copyOf(length)))
        assertFalse(probe(ByteBuffer.allocate(16).putInt(1).put("moov".toByteArray()).putLong(Long.MAX_VALUE).array()))
        assertFalse(probe(ByteBuffer.allocate(8).putInt(4).put("moov".toByteArray()).array()))
        assertFalse(probe(atom("free",valid)))
        assertFalse(mp4DubbingMarker(100) { _,_ -> null })
    }
    @Test fun boundsTheNumberOfBoxesInMalformedOrOversizedMetadata() {
        val empty=atom("free",ByteArray(0))
        val contents=ByteArray(empty.size*5000) { empty[it%empty.size] }+tagged()
        var calls=0
        assertFalse(mp4DubbingMarker(contents.size.toLong()) { position,count ->
            calls++; contents.copyOfRange(position.toInt(),position.toInt()+count)
        })
        assertTrue(calls <= 4096)
    }
}
