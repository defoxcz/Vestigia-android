package com.vestigiascriptorium.android.genealogy

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.vestigiascriptorium.android.aids.reader.ImageZoom
import com.vestigiascriptorium.android.aids.reader.RecordImage
import com.vestigiascriptorium.android.aids.reader.ReferenceImage
import com.vestigiascriptorium.android.ui.main.Copy

/** Image viewing only; both copies and source references remain immutable here. */
@Composable
internal fun EvidencePreview(doc: Evidence,media: GenealogyMedia?,c: Copy,onOpen: ()->Unit) {
    var expanded by remember(doc.id,doc.file) {mutableStateOf(false)}
    val context=LocalContext.current
    val pdf by produceState<Bitmap?>(null,doc.file,doc.mime) {
        value=null
        if(doc.mime=="application/pdf") value=withContext(Dispatchers.IO) {
            try {
                val descriptor=if(doc.file.startsWith("managed:"))media?.file(doc.file.removePrefix("managed:"))?.let {ParcelFileDescriptor.open(it,ParcelFileDescriptor.MODE_READ_ONLY)} else context.contentResolver.openFileDescriptor(Uri.parse(doc.file),"r")
                descriptor?.use {fd -> PdfRenderer(fd).use {renderer ->
                    if(renderer.pageCount==0)null else renderer.openPage(0).use {page ->
                        val scale=minOf(1200f/page.width,1600f/page.height)
                        val bitmap=Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap,null,Matrix().apply {setScale(scale,scale)},PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }}
            } catch(e: Exception) {if(e is CancellationException)throw e;null}
        }
    }
    if(doc.mime.startsWith("image/") || pdf!=null) {
        val data: Any=pdf ?: if(doc.file.startsWith("managed:")) media?.file(doc.file.removePrefix("managed:")) ?: Uri.EMPTY else Uri.parse(doc.file)
        val image=remember(doc.file,doc.caption,pdf) {RecordImage(name=doc.caption,data=data,online=false)}
        if(pdf!=null)Text(c.t("První strana PDF","First PDF page"),style=MaterialTheme.typography.labelMedium)
        ReferenceImage(image,c,Modifier.fillMaxWidth().height(140.dp).testTag("evidence-preview-${doc.id}"),onOpen={expanded=true})
        if(expanded)ImageZoom(image,c,{expanded=false})
        if(pdf!=null)TextButton(onClick=onOpen) {Text(c.t("Otevřít celý dokument","Open full document"))}
    } else {
        OutlinedCard(onClick=onOpen,modifier=Modifier.fillMaxWidth().testTag("evidence-preview-${doc.id}")) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(if(doc.mime=="application/pdf")"PDF" else c.t("Dokument","Document"),style=MaterialTheme.typography.titleMedium)
                Text(doc.caption)
                Text(c.t("Otevřít doklad","Open evidence"),style=MaterialTheme.typography.labelLarge)
            }
        }
    }
}
