package io.github.l1zerox.pandora

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.view.View
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as MediaUiR
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ArtworkGlassRenderTest {
    @Test fun softwareGlassRendersAtButtonAndWideTitleSizesWithoutChangingArtwork() {
        val source=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        val blurred=blurThumbnail(source)
        try {
            for((width,height) in listOf(64 to 48,320 to 48,340 to 500)) for(strong in listOf(false,true)) {
                val target=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
                try {
                    val glass=ArtworkGlassDrawable(blurred,artworkGlassTint(Color.White),1f,strongFrost=strong)
                    glass.setBounds(0,0,width,height); glass.draw(Canvas(target))
                    assertTrue(libraryContrast(Color.White,Color(target.getPixel(width/2,height/2))) >= 4.5f)
                    val outline=Outline(); glass.getOutline(outline); assertFalse(outline.isEmpty)
                    assertEquals(android.graphics.Color.WHITE,source.getPixel(24,24))
                } finally { target.recycle() }
            }
        } finally { source.recycle(); blurred.recycle() }
    }
    @androidx.annotation.OptIn(UnstableApi::class)
    @Test fun media3TimeLabelsAndSeekBarUseGlassWithoutReplacingPlaybackControls() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view=PlayerView(instrumentation.targetContext)
            stylePlaybackControls(view,Color.Blue)
            assertTrue(view.findViewById<View>(MediaUiR.id.exo_bottom_bar).background is ArtworkGlassDrawable)
            assertTrue(view.findViewById<DefaultTimeBar>(MediaUiR.id.exo_progress).background is ArtworkGlassDrawable)
            val image=Bitmap.createBitmap(200,48,Bitmap.Config.ARGB_8888)
            try {
                val background=view.findViewById<View>(MediaUiR.id.exo_bottom_bar).background
                background.setBounds(0,0,200,48); background.draw(Canvas(image))
                assertEquals("Playback controls must use a uniform color without artwork texture",image.getPixel(60,24),image.getPixel(140,24))
            } finally { image.recycle() }
            for(id in listOf(MediaUiR.id.exo_position,MediaUiR.id.exo_duration))
                assertEquals(android.graphics.Color.WHITE,view.findViewById<TextView>(id).currentTextColor)
        }
    }
}
