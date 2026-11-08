package io.github.l1zerox.pandora

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun LibraryPlayerAppearanceControls(preferences: Preferences, onChange: (Preferences)->Unit) {
    Column {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("再生パネルを表示",Modifier.weight(1f))
            Switch(checked=preferences.libraryPlayerVisible,onCheckedChange={onChange(preferences.copy(libraryPlayerVisible=it))},
                modifier=Modifier.semantics { contentDescription="再生パネルを表示" })
        }
        val scale=libraryPlayerScale(preferences.libraryPlayerScale)
        Text("大きさ ${(scale*100).roundToInt()}%",fontSize=13.sp)
        Slider(value=scale,valueRange=.7f..1f,steps=5,enabled=preferences.libraryPlayerVisible,
            onValueChange={onChange(preferences.copy(libraryPlayerScale=libraryPlayerScale(it)))},
            modifier=Modifier.fillMaxWidth().semantics { contentDescription="再生パネルの大きさ" })
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("再生バーのエフェクト",Modifier.weight(1f))
            Switch(checked=preferences.libraryPlayerWave,enabled=preferences.libraryPlayerVisible,
                onCheckedChange={onChange(preferences.copy(libraryPlayerWave=it))},
                modifier=Modifier.semantics { contentDescription="再生バーのエフェクト" })
        }
        PlaybackEffect.entries.forEach { effect ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected=PlaybackEffect.fromId(preferences.libraryPlayerEffect) == effect,
                    onClick={onChange(preferences.copy(libraryPlayerEffect=effect.id))},
                    enabled=preferences.libraryPlayerVisible && preferences.libraryPlayerWave,
                    modifier=Modifier.semantics { contentDescription=effect.label })
                TextButton(onClick={onChange(preferences.copy(libraryPlayerEffect=effect.id))},
                    enabled=preferences.libraryPlayerVisible && preferences.libraryPlayerWave,
                    colors=ButtonDefaults.textButtonColors(contentColor=LocalContentColor.current)) { Text(effect.label) }
            }
        }
        Text("非表示にしても再生は続きます。",fontSize=11.sp)
        TextButton(onClick={onChange(preferences.copy(libraryPlayerVisible=true,libraryPlayerScale=DEFAULT_LIBRARY_PLAYER_SCALE,libraryPlayerWave=true,libraryPlayerEffect="wave"))}) {
            Text("再生パネルを初期設定に戻す")
        }
        Spacer(Modifier.height(8.dp))
    }
}

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
