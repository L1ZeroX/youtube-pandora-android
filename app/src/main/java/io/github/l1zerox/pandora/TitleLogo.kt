package io.github.l1zerox.pandora

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun TitleLogo(modifier: Modifier = Modifier) {
    // Render only the original transparent artwork, centered at roughly half the previous width.
    Box(modifier.widthIn(max=420.dp).fillMaxWidth(),contentAlignment=Alignment.Center) {
        Image(painterResource(R.drawable.pandora_title_logo),contentDescription="Youtube Pandora!",
            modifier=Modifier.fillMaxWidth(.5f).aspectRatio(2056f/765f),
            contentScale=ContentScale.Fit)
    }
}
