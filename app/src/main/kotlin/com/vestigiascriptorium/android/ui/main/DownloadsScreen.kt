package com.vestigiascriptorium.android.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vestigiascriptorium.android.aids.AidLibraryState

@Composable
internal fun DownloadsScreen(copy: Copy,library: AidLibraryState,onDownload: (String) -> Unit,onPause: (String) -> Unit,
    onCancel: (String) -> Unit,onUninstall: (String) -> Unit,onWifi: (Boolean) -> Unit,onCatalog: () -> Unit,onDetail: (String) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected=aids.filter { aid -> library.jobs[aid.id]?.let { job ->
        when(filter) { 1 -> job.status !in listOf("installed","update","error");2 -> library.installed.containsKey(aid.id);3 -> job.status=="error";else -> true }
    } ?: false }
    val installedSize=library.installed.values.sumOf { it.size }
    val filterLabels=listOf(copy.t("Vše","All"),copy.t("Fronta","Queue"),copy.t("Nainstalované","Installed"),copy.t("Chyby","Errors"))
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Eyebrow(copy.t("SPRÁVA BADATELSKÝCH POMŮCEK","RESEARCH AID MANAGEMENT")) }
        item { Heading(copy.t("Stahování","Downloads"),copy.t("Stažení, instalace a odinstalace databázových pomůcek.","Download, install and uninstall reference databases.")) }
        item {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(copy.t("Offline knihovna","Offline library"),style=MaterialTheme.typography.titleMedium)
                    Text(copy.size(installedSize),style=MaterialTheme.typography.titleLarge,fontFamily=FontFamily.Serif)
                    Text(copy.t("Nainstalované pomůcky: ${library.installed.size}","Installed research aids: ${library.installed.size}"),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(end=12.dp)) {
                Text(copy.t("Jen neměřené připojení","Unmetered connections only"),style=MaterialTheme.typography.titleSmall)
                Text(copy.t("Obvykle Wi-Fi. Fronta počká na povolenou síť.","Usually Wi-Fi. Queued downloads wait for an allowed network."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked=library.wifiOnly,onCheckedChange=onWifi)
        }}
        item { LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(filterLabels.size) { index -> FilterChip(selected=filter==index,onClick={filter=index},label={Text(filterLabels[index])}) }
        }}
        if(selected.isEmpty()) item {
            Note(copy.t("Žádné balíčky v tomto přehledu. Pomůcky si můžete vybrat z katalogu.","No packages in this view. Choose research aids from the catalogue."))
            TextButton(onClick=onCatalog) { Text(copy.t("Prohlédnout katalog","Browse catalogue")) }
        }
        items(selected,key={it.id}) { aid ->
            val job=library.jobs.getValue(aid.id)
            val installed=library.installed[aid.id]
            val status=job.status
            OutlinedCard(shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(copy.title(aid),style=MaterialTheme.typography.titleLarge,fontFamily=FontFamily.Serif)
                    Pill(copy.status(status),installed!=null)
                    if(status=="active") {
                        LinearProgressIndicator(progress={if(job.total>0) (job.bytes.toFloat()/job.total).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth())
                        Text("${copy.size(job.bytes)} / ${copy.size(job.total)}",style=MaterialTheme.typography.bodySmall)
                    }
                    if(status in listOf("verifying","installing","busy")) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                    if(status=="queued") Text(copy.t("Čeká na povolenou síť nebo spuštění systémem.","Waiting for an allowed network or system scheduling."),style=MaterialTheme.typography.bodySmall)
                    if(status=="paused") Text(copy.t("Po pokračování se balíček stáhne od začátku.","Resuming will download the package from the beginning."),style=MaterialTheme.typography.bodySmall)
                    if(status=="error") Text(copy.error(job.error),color=MaterialTheme.colorScheme.error)
                    if(installed!=null && status !in listOf("installed","update")) Text(copy.t("Původní databáze je stále nainstalovaná.","The existing database is still installed."),style=MaterialTheme.typography.bodySmall)
                    if(status!="busy") {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            when(status) {
                                "installed" -> TextButton(onClick={onDetail(aid.id)}) { Text(copy.t("Otevřít","Open")) }
                                "update" -> TextButton(onClick={onDownload(aid.id)}) { Text(copy.t("Aktualizovat","Update")) }
                                "active","queued" -> TextButton(onClick={onPause(aid.id)}) { Text(copy.t("Pozastavit","Pause")) }
                                "error" -> TextButton(onClick={onDownload(aid.id)}) { Text(copy.t("Zkusit znovu","Retry")) }
                                "paused" -> TextButton(onClick={onDownload(aid.id)}) { Text(copy.t("Pokračovat","Resume")) }
                            }
                            if(status !in listOf("installed","update")) TextButton(onClick={onCancel(aid.id)}) { Text(copy.t("Zrušit přenos","Cancel transfer")) }
                            if(installed!=null) TextButton(onClick={removeId=aid.id}) { Text(copy.t("Odinstalovat","Uninstall"),color=MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        item { Note(copy.t("Stahují se pouze badatelské pomůcky. Rodokmeny a vlastní dokumenty nejsou součástí tohoto správce.","Only research aids are downloaded. Family trees and personal documents are not part of this manager.")) }
    }
    removeId?.let { id -> UninstallDialog(copy,copy.title(aids.first { it.id==id }),onDismiss={removeId=null},onConfirm={onUninstall(id);removeId=null}) }
}
