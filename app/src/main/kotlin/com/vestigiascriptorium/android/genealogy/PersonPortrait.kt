package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vestigiascriptorium.android.aids.reader.AidImages
import com.vestigiascriptorium.android.ui.main.Copy

@Composable
internal fun PersonPortrait(key: String,media: GenealogyMedia?,c: Copy,modifier: Modifier=Modifier,contentScale: ContentScale=ContentScale.Crop) {
    var failed by remember(key) {mutableStateOf(false)}
    Box(modifier.clip(CircleShape).testTag("person-portrait").background(MaterialTheme.colorScheme.surfaceContainer)) {
        AsyncImage(model=if(key.startsWith("content://"))android.net.Uri.parse(key) else media?.file(key),imageLoader=AidImages.loader(LocalContext.current),contentDescription=c.t("Portrét osoby","Person portrait"),contentScale=contentScale,
            modifier=Modifier.fillMaxSize(),onError={failed=true},onSuccess={failed=false})
        if(failed)Text(c.t("Obrázek není dostupný","Image unavailable"),Modifier.padding(8.dp),style=MaterialTheme.typography.bodySmall)
    }
}
