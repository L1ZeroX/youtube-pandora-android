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
}
