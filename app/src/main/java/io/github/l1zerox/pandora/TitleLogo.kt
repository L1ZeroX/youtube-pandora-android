package io.github.l1zerox.pandora

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun TitleLogo(modifier: Modifier = Modifier) {
    // Keep the supplied transparent PNG unchanged; a light backing keeps its dark letters legible.
    Surface(modifier.widthIn(max=420.dp).fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=Color(0xFFF7F8FC)) {
        Image(painterResource(R.drawable.pandora_title_logo),contentDescription="Youtube Pandora!",
            modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp).fillMaxWidth().aspectRatio(2056f/765f),
            contentScale=ContentScale.Fit)
    }
}
