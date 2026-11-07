package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Mp3ArtworkTest {
    private val video=Video("test","https://youtu.be/AbCd_12-345","Title",thumbnail="https://example.com/thumbnail.webp")
    @Test fun thumbnailAndGalleryChooseDifferentSourcesAndOtherFormatsIgnoreArtwork() {
        val p=Preferences(format="MP3")
        assertEquals(video.thumbnail,artworkSource(video,p))
        assertEquals("/private/cover.jpg",artworkSource(video,p.copy(artworkMode="ギャラリー",artworkImage="/private/cover.jpg")))
        assertNull(artworkSource(video,p.copy(artworkMode="なし")))
        assertNull(artworkSource(video,p.copy(format="MP4")))
    }
    @Test fun absentThumbnailUsesYoutubeFallbackOrNoArtwork() {
        val p=Preferences(format="MP3")
        assertEquals(youtubeThumbnail(video.url),artworkSource(video.copy(thumbnail=""),p))
        assertNull(artworkSource(video.copy(thumbnail="",url="https://example.com/video.mp4"),p))
    }
    @Test(expected=IllegalStateException::class) fun galleryMustHaveASelectedImage() {
        artworkSource(video,Preferences(format="MP3",artworkMode="ギャラリー"))
    }
    @Test fun coverMappingPreservesAudioAndWritesFrontCoverWithCompatibleId3() {
        val args=mp3EncodingArgs("320kbps","cover.jpg")
        assertFalse(args.contains("-vn"))
        assertEquals(listOf("0:a:0","1:v:0"),args.windowed(2).filter { it[0] == "-map" }.map { it[1] })
        assertTrue(args.windowed(2).contains(listOf("-b:a","320k")))
        assertTrue(args.windowed(2).contains(listOf("-disposition:v:0","attached_pic")))
        assertTrue(args.windowed(2).contains(listOf("-id3v2_version","3")))
        assertTrue(args.contains("comment=Cover (front)"))
    }
    @Test fun noArtworkNeverCopiesEmbeddedSourceVideoOrCover() {
        val args=mp3EncodingArgs("192kbps",null)
        assertTrue(args.contains("-vn"))
        assertEquals(listOf("0:a:0"),args.windowed(2).filter { it[0] == "-map" }.map { it[1] })
        assertFalse(args.contains("-i"))
    }
    @Test fun artworkChangesSeparateMp3ResumeWorkspaceButNotMp4() {
        val root=File("cache")
        val p=Preferences(format="MP3")
        val path=transferWorkspace(root,video,p)
        assertNotEquals(path,transferWorkspace(root,video,p.copy(artworkMode="なし")))
        assertNotEquals(path,transferWorkspace(root,video,p.copy(artworkMode="ギャラリー",artworkImage="one.jpg")))
        assertEquals(transferWorkspace(root,video,Preferences()),
            transferWorkspace(root,video,Preferences(artworkMode="ギャラリー",artworkImage="one.jpg")))
    }
}
