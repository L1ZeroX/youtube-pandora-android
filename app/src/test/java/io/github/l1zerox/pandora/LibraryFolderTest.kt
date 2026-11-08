package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class LibraryFolderTest {
    private val data=LibraryCollections(setOf("a"),listOf(UserPlaylist("p","Playlist",listOf("a"))),
        listOf(UserPlaylist("folder:a","A",listOf("a")),UserPlaylist("folder:b","B",listOf("b"))))
    @Test fun foldersPersistAndOldDataDoesNotCreateFolders() {
        assertEquals(data,LibraryCollections.parse(data.json()))
        assertTrue(LibraryCollections.parse("{\"favorites\":[\"a\"]}").folders.isEmpty())
    }
    @Test fun movingIsExclusiveAndDoesNotChangePlaylistsOrFavorites() {
        val moved=data.moveToFolder("folder:b",listOf("a","a"))
        assertEquals(emptyList<String>(),moved.folders[0].uris)
        assertEquals(listOf("b","a"),moved.folders[1].uris)
        assertEquals(data.playlists,moved.playlists); assertEquals(data.favorites,moved.favorites)
        assertEquals(moved,moved.moveToFolder("folder:b",listOf("a")))
    }
    @Test fun removalAndDeletionKeepMediaOutsideTheFolder() {
        assertEquals(listOf("b"),data.moveToFolder("",listOf("a")).folders.flatMap { it.uris })
        assertFalse(data.without("a").folders.any { "a" in it.uris })
        assertThrows(IllegalArgumentException::class.java) { data.moveToFolder("missing",listOf("a")) }
    }
    @Test fun folderFiltersAndUnfiledFilterWorkWithSortAndMediaKindFilters() {
        val a=LibraryEntry("a","A","audio/mpeg",10000,1)
        val b=LibraryEntry("b","B","video/mp4",20000,2)
        val c=LibraryEntry("c","C","audio/mpeg",30000,3)
        val entries=listOf(a,b,c)
        assertEquals(listOf(a),organizedLibrary(entries,data,"folder:a",LibrarySort.NEWEST,false))
        assertEquals(listOf(c),organizedLibrary(entries,data,"unfiled",LibrarySort.NEWEST,false))
        val removed=data.copy(folders=data.folders.filterNot { it.id == "folder:a" })
        assertEquals(listOf(a,c),organizedLibrary(entries,removed,"unfiled",LibrarySort.TITLE,false))
    }
    @Test fun duplicatePersistedMembershipIsRepairedWithoutDroppingFiles() {
        val bad=data.copy(folders=data.folders+UserPlaylist("folder:c","C",listOf("a","c")))
        val restored=LibraryCollections.parse(bad.json())
        assertEquals(listOf("a","b","c"),restored.folders.flatMap { it.uris })
    }
    @Test fun folderRootShowsOnlyUnfiledAndUsesTheSamePlaybackQueue() {
        val entries=listOf("a","b","c","d").mapIndexed { index,uri -> LibraryEntry(uri,uri,"audio/mpeg",10000,index.toLong()) }
        assertEquals(listOf("d","c"),organizedLibrary(entries,data,LIBRARY_FOLDER_ROOT,LibrarySort.NEWEST,false).map { it.uri })
        assertEquals(organizedLibrary(entries,data,"unfiled",LibrarySort.TITLE,false),
            organizedLibrary(entries,data,LIBRARY_FOLDER_ROOT,LibrarySort.TITLE,false))
        val moved=data.moveToFolder("folder:a",listOf("c"))
        assertEquals(listOf("d"),organizedLibrary(entries,moved,LIBRARY_FOLDER_ROOT,LibrarySort.NEWEST,false).map { it.uri })
        assertEquals(listOf("a","c"),organizedLibrary(entries,moved,"folder:a",LibrarySort.TITLE,false).map { it.uri })
        val deleted=moved.copy(folders=moved.folders.filterNot { it.id == "folder:a" })
        assertEquals(listOf("a","c","d"),organizedLibrary(entries,deleted,LIBRARY_FOLDER_ROOT,LibrarySort.TITLE,false).map { it.uri })
    }
    @Test fun folderCountsReflectFiltersAndKeepEmptyFoldersNavigable() {
        val a=LibraryEntry("a","A","audio/mpeg",10000)
        val b=LibraryEntry("b","B","video/mp4",20000,autoDubbed=true)
        val withMissing=data.copy(folders=listOf(data.folders[0].copy(uris=listOf("a","a","missing")),data.folders[1],UserPlaylist("folder:empty","Empty")))
        assertEquals(listOf(1,1,0),libraryFolderRows(listOf(a,b),withMissing,false).map { it.count })
        assertEquals(listOf(0,1,0),libraryFolderRows(listOf(a,b),withMissing,true).map { it.count })
        assertEquals(listOf(0,1,0),libraryFolderRows(libraryEntries(listOf(a,b),emptyList(),"動画","B"),withMissing,false).map { it.count })
        assertEquals(withMissing.folders.map { it.id },libraryFolderRows(emptyList(),withMissing,false).map { it.id })
    }
    @Test fun navigatingUpSeparatesFolderContentsUnfiledAndAllFiles() {
        val entries=listOf("a","b","c").map { LibraryEntry(it,it,"audio/mpeg",10000) }
        var location="folder:a"
        fun shown()=organizedLibrary(entries,data,location,LibrarySort.TITLE,false).map { it.uri }
        assertEquals(listOf("a"),shown())
        location=folderParent(location)
        assertTrue(isFolderCollection(location)); assertEquals(listOf("c"),shown())
        location=folderParent(location)
        assertFalse(isFolderCollection(location)); assertEquals(listOf("a","b","c"),shown())
        for(existing in listOf("favorites","p")) {
            location=existing
            assertFalse(isFolderCollection(location)); assertEquals(listOf("a"),shown())
        }
    }
}
