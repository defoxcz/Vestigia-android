package com.vestigiascriptorium.android.aids.reader

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.request.ImageRequest
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import com.vestigiascriptorium.android.BuildConfig
import coil3.svg.SvgDecoder
import com.vestigiascriptorium.android.ui.main.Copy
import java.io.File
import okio.Path.Companion.toOkioPath

internal object AidImages {
    fun request(context: Context,data: Any,retry: Int=0,size: Int=1200): ImageRequest {
        val key=when(data) {
            is ByteArray -> java.security.MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
            else -> data.toString()
        }
        return ImageRequest.Builder(context).data(data).size(size)
            .httpHeaders(NetworkHeaders.Builder().set("User-Agent","VestigiaScriptoriumAndroid/${BuildConfig.VERSION_NAME} (+https://vestigiascriptorium.com)").build())
            .diskCacheKey("aid-image-v2:$key").memoryCacheKey("aid-image-v2:$key:$retry")
            .diskCachePolicy(if(retry>0) CachePolicy.WRITE_ONLY else CachePolicy.ENABLED).build()
    }
    @Volatile private var instance: ImageLoader?=null
    fun loader(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext).components { add(SvgDecoder.Factory()) }
            .diskCache { DiskCache.Builder().directory(File(context.applicationContext.cacheDir,"aid-images").toOkioPath()).maxSizeBytes(64L*1024*1024).build() }
            .build().also { instance=it }
    }
}

@Composable
internal fun ReferenceImage(image: RecordImage,copy: Copy,modifier: Modifier=Modifier,onOpen: (() -> Unit)?=null,resolution: Int=1200) {
    val context=LocalContext.current
    var failed by remember(image.data) { mutableStateOf(false) }
    var loading by remember(image.data) { mutableStateOf(true) }
    var retry by remember(image.data) { mutableIntStateOf(0) }
    val request=remember(image.data,retry,resolution) {
        AidImages.request(context,image.data,retry,resolution)
    }
    Box(modifier.background(Color(0xFFF8F8F5)).then(if(onOpen!=null && !failed) Modifier.clickable(onClick=onOpen) else Modifier),contentAlignment=Alignment.Center) {
        val preview=LocalInspectionMode.current
        val bitmap=remember(image.data,preview) {
            if(preview && image.data is ByteArray) BitmapFactory.decodeByteArray(image.data,0,image.data.size)?.asImageBitmap() else null
        }
        if(bitmap!=null) Image(bitmap,contentDescription=image.name,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize())
        else AsyncImage(model=request,imageLoader=AidImages.loader(context),contentDescription=image.name,contentScale=ContentScale.Fit,
            modifier=Modifier.fillMaxSize(),onLoading={loading=true;failed=false},onError={loading=false;failed=true},onSuccess={loading=false;failed=false})
        if(loading && bitmap==null) CircularProgressIndicator(modifier=Modifier.size(24.dp),color=Color(0xFF24475C))
        if(failed) Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(copy.t("Obrázek nelze načíst. Bez připojení se zobrazí pouze obrázky z mezipaměti.","Unable to load image. Offline, only cached images can be displayed."),style=MaterialTheme.typography.bodySmall,color=Color(0xFF34454F))
            TextButton(onClick={retry++},colors=ButtonDefaults.textButtonColors(contentColor=Color(0xFF24475C))) { Text(copy.t("Zkusit znovu","Retry")) }
        }
    }
}

@Composable
internal fun ImageZoom(image: RecordImage,copy: Copy,onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    fun bounded(value: Offset): Offset {
        val x=viewport.width*(scale-1f)/2f
        val y=viewport.height*(scale-1f)/2f
        return Offset(value.x.coerceIn(-x,x),value.y.coerceIn(-y,y))
    }
    val transform=rememberTransformableState { zoom,pan,_ ->
        scale=(scale*zoom).coerceIn(1f,5f)
        offset=if(scale==1f) Offset.Zero else bounded(offset+pan)
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(color=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick=onClose) { Text(copy.t("Zavřít obrázek","Close image")) }
                    TextButton(onClick={scale=(scale-.5f).coerceAtLeast(1f);offset=bounded(offset)},modifier=Modifier.semantics { contentDescription=copy.t("Zmenšit obrázek","Zoom out") }) { Text("−") }
                    TextButton(onClick={scale=(scale+.5f).coerceAtMost(5f);offset=bounded(offset)},modifier=Modifier.semantics { contentDescription=copy.t("Zvětšit obrázek","Zoom in") }) { Text("+") }
                    TextButton(onClick={scale=1f;offset=Offset.Zero}) { Text("${(scale*100).toInt()} %") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewport=it }.transformable(transform)) {
                    ReferenceImage(image,copy,Modifier.fillMaxSize().graphicsLayer { scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y },resolution=2600)
                }
                Text(copy.t("Přiblížení dvěma prsty · tažením posunete","Pinch to zoom · drag to pan"),modifier=Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
