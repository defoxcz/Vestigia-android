package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vestigiascriptorium.android.ui.main.Copy

@Composable
internal fun EvidenceGallery(documents: List<Evidence>,media: GenealogyMedia?,c: Copy,onOpen: (Evidence)->Unit) {
    val configuration=LocalConfiguration.current
    val landscape=configuration.screenWidthDp>configuration.screenHeightDp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=if(landscape)(maxWidth.value/100).toInt().coerceIn(4,6) else 3
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            documents.chunked(columns).forEach {row ->
                Row(Modifier.fillMaxWidth().testTag("evidence-gallery-row"),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach {doc ->key(doc.id) {
                        Column(Modifier.weight(1f).testTag("evidence-tile-${doc.id}"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            if(doc.file.isNotBlank())EvidencePreview(doc,media,c,{onOpen(doc)},thumbnailHeight=96.dp,compact=true)
                            Text(doc.caption,style=MaterialTheme.typography.labelMedium,maxLines=3,overflow=TextOverflow.Ellipsis)
                            if(doc.note.isNotBlank())Text(doc.note,style=MaterialTheme.typography.bodySmall)
                        }
                    }}
                    repeat(columns-row.size) {Spacer(Modifier.weight(1f))}
                }
            }
        }
    }
}
