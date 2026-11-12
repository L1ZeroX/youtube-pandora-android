package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class LibraryMusicTest {
    private val one=LibraryEntry("one","01 Song","audio/mpeg",1000,album="Album",artist="Singer",track=1)
    private val two=one.copy(uri="two",title="Second",track=2)
    private val video=one.copy(uri="video",mime="video/mp4",width=1080,height=1920)

    @Test fun sectionsExcludeOtherMediaBeforeSearchingAndCollections() {
        val all=listOf(one,two,video)
        assertEquals(listOf(one,two),sectionEntries(all,LibrarySection.MUSIC))
        assertEquals(listOf(video),sectionEntries(all,LibrarySection.VIDEO))
        val collection=LibraryCollections(favorites=all.map { it.uri }.toSet())
        assertEquals(setOf("one","two"),organizedLibrary(sectionEntries(all,LibrarySection.MUSIC),collection,"favorites",LibrarySort.TITLE,false).map { it.uri }.toSet())
        assertEquals(listOf(video),libraryEntries(sectionEntries(all,LibrarySection.VIDEO),emptyList(),"ショート"))
    }
    @Test fun trackNumberCleanupKeepsNumbersThatBelongToTheTitle() {
        for(title in listOf("01 Song","01. Song","01_Song","1 - Song")) assertEquals("Song",musicTitle(title))
        assertEquals("Song",musicTitle("12 Song",12))
        for(title in listOf("1984","7 Rings","24K Magic","100 Years","Song 01","01")) assertEquals(title,musicTitle(title))
        assertEquals("Song 01",musicTitle("01 Song 01"))
    }
    @Test fun audioTagsOverrideFileNamesAndVideoTitlesRemainUnchanged() {
        val tagged=one.musicMetadata("02 Real title","Artist","Disc","Album Artist","2/12","1/2")
        assertEquals("Real title",tagged.title); assertEquals(2,tagged.track); assertEquals(1,tagged.disc)
        assertEquals("Disc",tagged.album); assertEquals("Album Artist",tagged.albumArtist)
        assertEquals("Song",one.musicMetadata(null,null,null,null,null,null).title)
        assertEquals(video,video.musicMetadata("New",null,null,null,null,null))
        assertEquals(listOf(tagged),libraryEntries(listOf(tagged),emptyList(),search="Disc"))
        assertEquals(listOf(tagged),libraryEntries(listOf(tagged),emptyList(),search="Artist"))
    }
    @Test fun albumsKeepDiscTrackOrderAndSeparateDifferentAlbumNames() {
        val common=one.copy(albumArtist="Compilation")
        val duet=two.copy(artist="Guest",albumArtist="Compilation")
        val disc2=common.copy(uri="third",disc=2)
        val other=one.copy(uri="other",album="Other Album",albumArtist="Different")
        val albums=libraryAlbums(listOf(duet,disc2,other,common,video),LibraryCollections())
        assertEquals(2,albums.size)
        val joined=albums.first { it.artist == "Compilation" }
        assertEquals(listOf("one","two","third"),joined.tracks.map { it.uri })
        assertEquals(joined.id,libraryAlbums(listOf(common.copy(album="ALBUM")),LibraryCollections()).single().id)
    }
    @Test fun compilationAlbumDoesNotSplitByTrackOrAlbumArtistAndAcceptsLegacyIds() {
        val group=one.copy(album="μ's Best Album Best Live! Collection II",artist="μ's")
        val solo=two.copy(album=" μ’s  Best Album Best Live! Collection II ",artist="高坂穂乃果",albumArtist="高坂穂乃果")
        val albums=libraryAlbums(listOf(group,solo),LibraryCollections())
        assertEquals(1,albums.size); assertEquals(2,albums.single().tracks.size)
        assertEquals("複数のアーティスト",albums.single().artist)
        for(oldId in albums.single().legacyIds) assertEquals(listOf(group,solo),organizedLibrary(listOf(group,solo),LibraryCollections(),oldId,LibrarySort.NEWEST,false))
        assertEquals(albums.single().id,libraryAlbums(listOf(group.copy(artist="Other",albumArtist="Other")),LibraryCollections()).single().id)
        val manual=LibraryCollections().withAlbum(UserPlaylist("album:custom","Separate",listOf(solo.uri)))
        assertEquals(2,libraryAlbums(listOf(group,solo),manual).size)
    }
    @Test fun manualAlbumsPersistOverrideAutomaticGroupingAndLeaveFilesWhenRemoved() {
        val initial=LibraryCollections(favorites=setOf("one"),folders=listOf(UserPlaylist("folder:a","F",listOf("one"))))
        val data=initial.withAlbum(UserPlaylist("album:a","Custom",listOf("one","one")))
        assertEquals(data,LibraryCollections.parse(data.json()))
        assertEquals(initial.favorites,data.favorites); assertEquals(initial.folders,data.folders)
        val albums=libraryAlbums(listOf(one,two,video),data)
        assertEquals(listOf(one),albums.first { it.manual }.tracks)
        assertEquals(listOf(two),albums.first { !it.manual }.tracks)
        val moved=data.withAlbum(UserPlaylist("album:b","Other",listOf("one")))
        assertTrue(moved.albums.first { it.id == "album:a" }.uris.isEmpty())
        assertTrue(moved.without("one").albums.all { it.uris.isEmpty() })
        assertEquals(2,libraryAlbums(listOf(one,two),data.copy(albums=emptyList())).single().tracks.size)
        assertTrue(LibraryCollections.parse("{}").albums.isEmpty())
    }
    @Test fun albumPlaybackIsLimitedToTheAlbumAndUntaggedSongsRemainAccessible() {
        val untagged=one.copy(uri="unknown",album="")
        val entries=listOf(one,two,untagged,video)
        val albums=libraryAlbums(entries,LibraryCollections())
        val normal=albums.first { it.id != "album-unknown" }
        assertEquals(listOf(one,two),organizedLibrary(entries,LibraryCollections(),normal.id,LibrarySort.NEWEST,false))
        assertEquals(listOf(untagged),organizedLibrary(entries,LibraryCollections(),"album-unknown",LibrarySort.NEWEST,false))
        assertTrue(organizedLibrary(entries,LibraryCollections(),"album:deleted",LibrarySort.NEWEST,false).isEmpty())
    }
    @Test fun commonAudioExtensionsAreRecognizedWhenProviderOmitsMime() {
        for(extension in listOf("mp3","m4a","flac","wav","ogg","opus","aac")) assertTrue(libraryMime("application/octet-stream","01 song.$extension")!!.startsWith("audio/"))
        assertNull(libraryMime(null,"song.flac.exe"))
    }
    @Test fun reopeningCurrentSongFromAlbumRebuildsItsQueueAtTheCurrentPosition() {
        val entries=listOf(one,two)
        val data=LibraryCollections().withAlbum(UserPlaylist("album:single","Single",listOf("one")))
        assertEquals(42_000L,albumQueueResumePosition("one","one","album:single",false,42_000))
        assertEquals(listOf(one),organizedLibrary(entries,data,"album:single",LibrarySort.NEWEST,false))
        assertEquals(0L,albumQueueResumePosition("one","one","album:single",false,0))
        // Activity recreation and the now-playing panel keep an explicitly edited queue.
        assertNull(albumQueueResumePosition("one","one","album:single",true,42_000))
        assertNull(albumQueueResumePosition("one","one","",false,42_000))
        assertNull(albumQueueResumePosition("two","one","album:single",false,42_000))
    }
}
