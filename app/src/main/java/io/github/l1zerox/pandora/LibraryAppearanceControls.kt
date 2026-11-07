package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun LibraryAppearanceControls(preferences: Preferences, onChange: (Preferences)->Unit) {
    Column {
        val foreground=LocalContentColor.current
        val sliderColors=SliderDefaults.colors(thumbColor=foreground,activeTrackColor=foreground,
            inactiveTrackColor=foreground.copy(alpha=.25f))
        val transparency=libraryPanelTransparency(preferences.libraryGlass)
        val blur=preferences.libraryBlur.coerceIn(0,20)
        Text("パネルの透過 ${(transparency*100).roundToInt()}%",fontSize=13.sp)
        Slider(value=transparency,onValueChange={onChange(preferences.copy(libraryGlass=it))},valueRange=0f.. .85f,colors=sliderColors,
            modifier=Modifier.fillMaxWidth().semantics { contentDescription="ライブラリーパネルの透過" })
        Text("パネルのぼかし $blur",fontSize=13.sp)
        Slider(value=blur.toFloat(),onValueChange={onChange(preferences.copy(libraryBlur=it.roundToInt()))},
            valueRange=0f..20f,steps=19,colors=sliderColors,
            modifier=Modifier.fillMaxWidth().semantics { contentDescription="ライブラリーパネルのぼかし" })
        Text("自動保存。文字が読める濃さを保ちます。",fontSize=11.sp)
        TextButton(onClick={onChange(preferences.copy(libraryGlass=.45f,libraryBlur=10))},
            colors=ButtonDefaults.textButtonColors(contentColor=foreground)) { Text("標準に戻す") }
    }
}
