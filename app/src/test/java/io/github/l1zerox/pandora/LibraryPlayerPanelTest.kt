package io.github.l1zerox.pandora

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class LibraryPlayerPanelTest {
    @Test fun oldSettingsStartCompactAndInvalidScalesStayInTheSupportedRange() {
        assertTrue(Preferences().libraryPlayerVisible)
        assertEquals(.7f,Preferences().libraryPlayerScale,0f)
        assertEquals(.7f,libraryPlayerScale(Float.NaN),0f)
        assertEquals(.7f,libraryPlayerScale(Float.POSITIVE_INFINITY),0f)
        assertEquals(.7f,libraryPlayerScale(-1f),0f)
        assertEquals(1f,libraryPlayerScale(2f),0f)
        assertEquals(.85f,libraryPlayerScale(.85f),0f)
    }
    @Test fun artworkButtonsAndTextStayReadableEvenOnWhiteOrBlackArtwork() {
        val brightest=Color.Black.copy(alpha=LIBRARY_PLAYER_SHADE).compositeOver(Color.White)
        assertTrue(libraryContrast(Color.White,brightest) >= 4.5f)
        for(r in 0..255 step 51) for(g in 0..255 step 51) for(b in 0..255 step 51) {
            val accent=libraryPlayerAccent(Color(r,g,b))
            assertTrue(libraryContrast(accent,brightest) >= 3f)
            assertTrue(libraryContrast(accent,Color.Black) >= 3f)
        }
        assertNotEquals(libraryPlayerAccent(Color.Red),libraryPlayerAccent(Color.Blue))
        assertTrue(libraryContrast(libraryPlayerAccent(null),brightest) >= 3f)
    }

    private class Session {
        var uri="a"
        val denied=mutableSetOf<Int>()
        val calls=mutableListOf<String>()
        val player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) { _,method,args ->
            when(method.name) {
                "getCurrentMediaItem" -> MediaItem.Builder().setMediaId(uri).build()
                "isCommandAvailable" -> args!![0] !in denied
                "seekToPrevious" -> { calls+="previous"; null }
                "seekToNext" -> { calls+="next"; null }
                else -> error("Panel must not restart or replace the player: ${method.name}")
            }
        } as Player
    }
    @Test fun adjacentControlsUseTheExistingQueueWithoutStartingOrPausingPlayback() {
        val s=Session()
        seekLibraryAdjacent(s.player,"a",false); seekLibraryAdjacent(s.player,"a",true)
        assertEquals(listOf("previous","next"),s.calls)
    }
    @Test fun stalePanelTapsDoNotMoveTheNewTrack() {
        val s=Session(); s.uri="b"
        seekLibraryAdjacent(s.player,"a",false); seekLibraryAdjacent(s.player,"a",true)
        assertTrue(s.calls.isEmpty())
        seekLibraryAdjacent(null,"a",true)
    }
    @Test fun unavailableAdjacentCommandsAreIgnored() {
        val s=Session()
        s.denied+=Player.COMMAND_SEEK_TO_NEXT
        seekLibraryAdjacent(s.player,"a",true)
        assertTrue(s.calls.isEmpty())
        seekLibraryAdjacent(s.player,"a",false)
        assertEquals(listOf("previous"),s.calls)
        s.calls.clear(); s.denied+=Player.COMMAND_SEEK_TO_PREVIOUS
        seekLibraryAdjacent(s.player,"a",false)
        assertTrue(s.calls.isEmpty())
    }
}
