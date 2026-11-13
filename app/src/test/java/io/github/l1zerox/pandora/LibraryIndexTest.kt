package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class LibraryIndexTest {
    @Test fun kanaKatakanaAndVoicedSoundsShareTheirRow() {
        for((titles,row) in listOf(
            listOf("あいうえお","アイドル","ｱｲﾄﾞﾙ","ヴォーカル") to "あ",
            listOf("かげ","がくえん","ギター","ヶ丘") to "か",listOf("さくら","ジャズ") to "さ",
            listOf("ちいさな","ヅラ") to "た",listOf("にじ","ノイズ") to "な",
            listOf("ばら","パレード") to "は",listOf("みらい","モノローグ") to "ま",
            listOf("ゃくそく","ユメ") to "や",listOf("りんご","ロック") to "ら",listOf("わたし","ヲト") to "わ"))
            for(title in titles) assertEquals(title,row,libraryIndexLabel(title))
    }
    @Test fun latinHanNumbersAndQuotedTitlesAreClassifiedWithoutGuessingKanjiReadings() {
        for(title in listOf("Album","album","Ａｌｂｕｍ","【Album】")) assertEquals("A",libraryIndexLabel(title))
        assertEquals("漢",libraryIndexLabel("彩音")); assertEquals("漢",libraryIndexLabel("【初音ミク】曲"))
        assertEquals("漢",libraryIndexLabel("𠮷野"))
        for(title in listOf("01 Song","１２３","♪","","  ")) assertEquals("#",libraryIndexLabel(title))
        assertEquals("B",libraryIndexLabel("  ♪ Best"))
    }
    @Test fun targetsKeepActualItemOffsetsAcrossHeadersAndRepeatedLetters() {
        val rows=listOf(LibraryIndexRow("heading",""),LibraryIndexRow("one","Beta"),LibraryIndexRow("two","Bravo"),
            LibraryIndexRow("caption",""),LibraryIndexRow("three","あい"),LibraryIndexRow("four","Alpha"))
        assertEquals(listOf("あ","A","B"),libraryIndexTargets(rows).keys.toList())
        assertEquals(mapOf("あ" to 4,"A" to 5,"B" to 1),libraryIndexTargets(rows))
        val sorted=rows.filter { it.title.isNotEmpty() }.sortedBy { libraryTitleKey(it.title) }
        assertEquals(listOf("あ","A","B","B"),sorted.map { libraryIndexLabel(it.title) })
        val songs=listOf(LibraryEntry("b","Beta","audio/mpeg",album="Test",track=1),LibraryEntry("a","Alpha","audio/mpeg",album="Test",track=2))
        val album=libraryAlbums(songs,LibraryCollections()).single()
        assertEquals(listOf("Alpha","Beta"),organizedLibrary(songs,LibraryCollections(),album.id,LibrarySort.TITLE,false).map { it.title })
        assertEquals(listOf("Beta","Alpha"),organizedLibrary(songs,LibraryCollections(),album.id,LibrarySort.NEWEST,false).map { it.title })
    }
    @Test fun draggingMapsEdgesAndOutOfBoundsToValidExistingLetters() {
        val labels=listOf("あ","漢","A","Z")
        assertEquals("あ",libraryIndexAt(labels,-20f,400)); assertEquals("あ",libraryIndexAt(labels,0f,400))
        assertEquals("漢",libraryIndexAt(labels,100f,400)); assertEquals("A",libraryIndexAt(labels,299f,400))
        assertEquals("Z",libraryIndexAt(labels,400f,400)); assertEquals("Z",libraryIndexAt(labels,600f,400))
        assertNull(libraryIndexAt(labels,Float.NaN,400)); assertNull(libraryIndexAt(labels,1f,0)); assertNull(libraryIndexAt(emptyList(),1f,100))
    }
}
