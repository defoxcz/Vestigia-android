package com.vestigiascriptorium.android.aids.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.vestigiascriptorium.android.ui.main.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AidReaderScreen(aid: Aid,copy: Copy,source: AidReaderSource,onBack: () -> Unit,previewPage: RecordPage?=null) {
    val language=if(copy.cs) "cs" else "en"
    var query by rememberSaveable(aid.id) { mutableStateOf("") }
    var filter by rememberSaveable(aid.id) { mutableStateOf("") }
    var imagesOnly by rememberSaveable(aid.id) { mutableStateOf(false) }
    var page by rememberSaveable(aid.id) { mutableIntStateOf(0) }
    var selected by rememberSaveable(aid.id) { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<RecordPage?>(null) }
    var record by remember { mutableStateOf<AidRecord?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val listState=rememberLazyListState();val gridState=rememberLazyGridState()
    LaunchedEffect(source,language,query,filter,page,imagesOnly,retry,selected) {
        if(previewPage!=null) { data=previewPage;loading=false;return@LaunchedEffect }
        loading=true;error=false
        try {
            if(selected==null) {
                data=null // Never show rows for the previous text while the IME replacement is loading.
                if(query.isNotEmpty()) delay(250)
                val next=source.page(aid.id,language,query,filter,page,imagesOnly)
                ensureActive()
                data=next
                if(data!!.page!=page) page=data!!.page
            } else {
                record=source.detail(aid.id,language,selected!!)
                error=record==null
            }
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { if(isActive) error=true else throw CancellationException() }
        finally { if(isActive) loading=false }
    }
    LaunchedEffect(page) { listState.scrollToItem(0);gridState.scrollToItem(0) }
    BackHandler(enabled=selected!=null) { selected=null;record=null }
    Scaffold(topBar={TopAppBar(title={Text(copy.title(aid),maxLines=1,overflow=TextOverflow.Ellipsis)},navigationIcon={IconButton(onClick={if(selected!=null) {selected=null;record=null} else onBack()},modifier=Modifier.semantics { contentDescription=copy.t("Zpět","Back") }) { Mark(5) }})}) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding()) {
            when {
                error -> Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Note(copy.t("Obsah nelze načíst. Pomůcka mohla být odinstalována, aktualizována nebo má nepodporovaný formát.","Unable to read this reference. It may have been uninstalled, updated, or use an unsupported format."))
                    Button(onClick={retry++}) { Text(copy.t("Zkusit znovu","Retry")) }
                    TextButton(onClick=onBack) { Text(copy.t("Zpět do katalogu","Back to catalogue")) }
                }
                selected!=null -> if(loading || record==null) Loading() else RecordDetail(record!!,copy,AidSchemas.get(aid.id,language).kind==AidViewKind.IMAGE)
                else -> {
                    OutlinedTextField(value=query,onValueChange={query=it;page=0},modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp),singleLine=true,
                        label={Text(copy.t("Hledat v záznamech","Search records"))},leadingIcon={Mark(4)},trailingIcon={if(query.isNotEmpty()) TextButton(onClick={query="";page=0}) { Text(copy.t("Smazat","Clear")) }},shape=RoundedCornerShape(16.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        var expanded by remember { mutableStateOf(false) }
                        data?.let { result ->
                            if(result.options.isNotEmpty()) Box {
                                OutlinedButton(onClick={expanded=true}) { Text(result.options.firstOrNull { it.value==filter }?.label ?: (result.facetLabel ?: copy.t("Filtr","Filter")),maxLines=1,overflow=TextOverflow.Ellipsis) }
                                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=320.dp)) {
                                    DropdownMenuItem(text={Text(copy.t("Vše","All"))},onClick={filter="";page=0;expanded=false})
                                    result.options.forEach { option -> DropdownMenuItem(text={Text(option.label)},onClick={filter=option.value;page=0;expanded=false}) }
                                }
                            }
                            if(result.kind==AidViewKind.IMAGE) FilterChip(selected=imagesOnly,onClick={imagesOnly=!imagesOnly;page=0},label={Text(copy.t("S obrázkem","With images"))})
                        }
                    }
                    if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    val result=data
                    if(result==null) Loading()
                    else if(result.total==0) Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) {
                        Text(copy.t("Žádné odpovídající záznamy. Zkuste změnit hledání nebo filtr.","No matching records. Try changing your search or filter."))
                    } else if(result.kind==AidViewKind.IMAGE) {
                        LazyVerticalGrid(columns=GridCells.Adaptive(150.dp),state=gridState,modifier=Modifier.weight(1f),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            items(result.records,key={it.id}) { row -> RecordCard(row,copy,true,!loading) { selected=row.id } }
                        }
                    } else {
                        LazyColumn(state=listState,modifier=Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            items(result.records,key={it.id}) { row -> RecordCard(row,copy,false,!loading) { selected=row.id } }
                        }
                    }
                    if(result!=null) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={page--},enabled=page>0 && !loading) { Text(copy.t("Předchozí","Previous")) }
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(if(result.total==0) "0 / 0" else "${result.page+1} / ${(result.total+AidReaderSource.PAGE_SIZE-1)/AidReaderSource.PAGE_SIZE}",style=MaterialTheme.typography.labelLarge)
                            Text(copy.t("${result.total} záznamů","${result.total} records"),style=MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick={page++},enabled=(page+1)*AidReaderSource.PAGE_SIZE<result.total && !loading) { Text(copy.t("Další","Next")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading() { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() } }

@Composable
private fun RecordCard(record: AidRecord,copy: Copy,image: Boolean,enabled: Boolean,onOpen: () -> Unit) {
    OutlinedCard(onClick=onOpen,enabled=enabled,shape=RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth()) {
            if(image) {
                if(record.images.isNotEmpty()) ReferenceImage(record.images.first(),copy,Modifier.fillMaxWidth().height(140.dp),onOpen)
                else Box(Modifier.fillMaxWidth().height(110.dp).padding(12.dp),contentAlignment=Alignment.Center) { Text(copy.t("Bez dostupného náhledu","No available preview"),style=MaterialTheme.typography.bodySmall) }
            }
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(record.title,style=MaterialTheme.typography.titleMedium,fontFamily=FontFamily.Serif,maxLines=if(image) 3 else 2,overflow=TextOverflow.Ellipsis)
                if(record.summary.isNotBlank()) Text(record.summary,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=if(image) 2 else 3,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RecordDetail(record: AidRecord,copy: Copy,hasImages: Boolean) {
    var zoom by remember { mutableStateOf<RecordImage?>(null) }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { SelectionContainer { Text(record.title,style=MaterialTheme.typography.headlineMedium,fontFamily=FontFamily.Serif) } }
        if(hasImages && record.images.isEmpty() && record.hiddenImages==0) item {
            Note(copy.t("U tohoto záznamu není dostupný obrázek.","No image is available for this record."))
        }
        items(record.images) { image ->
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                ReferenceImage(image,copy,Modifier.fillMaxWidth().height(280.dp),onOpen={zoom=image})
                if(image.online) Text(copy.t("Online obrázek · dříve načtené jsou v mezipaměti","Online image · previously loaded images are cached"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val attribution=listOf(image.author,image.credit,image.license,image.terms,image.restrictions.takeUnless { it=="[]" } ?: "").filter { it.isNotBlank() }.distinct().joinToString("\n")
                if(attribution.isNotBlank()) SelectionContainer { Text(HtmlCompat.fromHtml(attribution.replace("\n","<br>"),HtmlCompat.FROM_HTML_MODE_LEGACY).toString(),style=MaterialTheme.typography.bodySmall) }
                image.licenseUrl.takeIf { it.isNotBlank() }?.let { SafeLink(copy.t("Licence obrázku","Image licence"),it) }
                image.source?.let { SafeLink(copy.t("Zdroj obrázku","Image source"),it) }
            }
        }
        if(record.hiddenImages>0) item { Note(copy.t("${record.hiddenImages} obrázků nemá v katalogu povolený automatický náhled.","${record.hiddenImages} images are not approved for automatic display in the catalogue.")) }
        items(record.fields) { field ->
            Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Eyebrow(field.label)
                    if(field.link) SafeLink(field.value,field.value) else SelectionContainer { Text(field.value,style=MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
    zoom?.let { ImageZoom(it,copy,onClose={zoom=null}) }
}

@Composable
private fun SafeLink(label: String,value: String) {
    val handler=LocalUriHandler.current
    val valid=runCatching { val uri=java.net.URI(value);uri.scheme in listOf("https","http") && !uri.host.isNullOrBlank() }.getOrDefault(false)
    if(valid) TextButton(onClick={runCatching { handler.openUri(value) }}) { Text(label) }
    else SelectionContainer { Text(label) }
}
