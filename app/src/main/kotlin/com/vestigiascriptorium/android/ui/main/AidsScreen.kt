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
internal fun AidsScreen(copy: Copy,library: AidLibraryState,onRefresh: () -> Unit,onDetail: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(-1) }
    var offline by rememberSaveable { mutableStateOf(false) }
    val filtered=aids.filter { aid ->
        (category==-1 || aid.category==category) && (!offline || library.installed.containsKey(aid.id)) &&
            (copy.title(aid)+" "+copy.description(aid)).contains(query,ignoreCase=true)
    }
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Eyebrow(copy.t("KNIHOVNA PRO VAŠE BÁDÁNÍ","A LIBRARY FOR YOUR RESEARCH")) }
        item { Heading(copy.t("Badatelské pomůcky","Research aids"),copy.t("Vyberte si podklady pro práci bez připojení.","Choose reference material for working offline.")) }
        item {
            TextButton(onClick=onRefresh,enabled=!library.refreshing) { Text(if(library.refreshing) copy.t("Obnovuji katalog…","Refreshing catalogue…") else copy.t("Obnovit katalog","Refresh catalogue")) }
            if(library.catalogError) Note(copy.t("Katalog nelze obnovit. Zobrazeny jsou poslední známé údaje.","Unable to refresh the catalogue. Showing the last known metadata."))
        }
        item { OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text(copy.t("Hledat v pomůckách","Search research aids"))},leadingIcon={Mark(4)},shape=RoundedCornerShape(16.dp)) }
        item { LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected=category==-1,onClick={category=-1},label={Text(copy.t("Vše","All"))}) }
            items(copy.categories.size) { index -> FilterChip(selected=category==index,onClick={category=index},label={Text(copy.categories[index])}) }
        }}
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(copy.t("Pouze nainstalované","Installed only"),modifier=Modifier.padding(top=12.dp))
            Switch(checked=offline,onCheckedChange={offline=it})
        }}
        if(filtered.isEmpty()) item { Note(copy.t("Žádné odpovídající pomůcky. Zkuste změnit hledání nebo filtr.","No matching research aids. Try changing the search or filters.")) }
        items(filtered,key={it.id}) { aid ->
            OutlinedCard(onClick={onDetail(aid.id)},shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Eyebrow(copy.categories[aid.category].uppercase())
                        Text(library.metadata[aid.id]?.let { copy.size(it.archiveSize) } ?: "—",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(copy.title(aid),style=MaterialTheme.typography.titleLarge,fontFamily=FontFamily.Serif)
                    Text(copy.description(aid),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Pill(copy.status(library.jobs[aid.id]?.status),library.installed.containsKey(aid.id))
                        Mark(6)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AidDetail(aid: Aid,copy: Copy,library: AidLibraryState,onDownload: (String) -> Unit,onManage: () -> Unit,onOpen: (String) -> Unit) {
    val status=library.jobs[aid.id]?.status
    val installed=library.installed[aid.id]
    val meta=library.metadata[aid.id]
    if(installed!=null) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Heading(copy.title(aid),copy.description(aid))
            Button(onClick={onOpen(aid.id)}) { Text(copy.t("Otevřít pomůcku","Open research aid")) }
            TextButton(onClick=onManage) { Text(copy.t("Správa ve Stahování","Manage in Downloads")) }
        }
        return
    }
    val working=status in listOf("active","queued","verifying","installing","busy")
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        item { Eyebrow(copy.categories[aid.category].uppercase()) }
        item { Heading(copy.title(aid),copy.description(aid)) }
        item { Pill(copy.status(status),installed!=null) }
        item {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Mark(1,Modifier.size(36.dp))
                    Text(copy.t("Knihovna dostupná offline","Your offline reference library"),style=MaterialTheme.typography.titleLarge,fontFamily=FontFamily.Serif)
                    Text(copy.t("Stáhne se ověřená databáze do úložiště aplikace. Přenos a instalaci lze sledovat ve správci stahování.","A verified database will be installed in app storage. Follow the transfer and installation in Downloads."))
                    HorizontalDivider()
                    Metric(copy.t("Ke stažení","Download size"),meta?.let { copy.size(it.archiveSize) } ?: "—")
                    Metric(copy.t("Po instalaci","Installed size"),meta?.let { copy.size(it.size) } ?: "—")
                    if(installed!=null) Metric(copy.t("Nainstalováno","Installed"),copy.size(installed.size))
                    if(installed==null || status=="update" || status=="error") {
                        Button(onClick={if(working) onManage() else onDownload(aid.id)},enabled=meta!=null && status!="busy",modifier=Modifier.fillMaxWidth()) {
                            Text(when { working -> copy.t("Zobrazit stahování","View download");installed!=null -> copy.t("Aktualizovat","Update");else -> copy.t("Stáhnout a nainstalovat","Download and install") })
                        }
                    }
                    TextButton(onClick=onManage) { Text(copy.t("Otevřít správce stahování","Open download manager")) }
                }
            }
        }
        item { SectionTitle(copy.t("Bezpečná aktualizace","Safe updates")) }
        item { Note(copy.t("Před instalací ověříme velikost, SHA-256, obsah ZIPu a databázi. Při chybě aktualizace zůstane původní pomůcka zachovaná.","Before installation, we verify the size, SHA-256, ZIP content and database. A failed update preserves the existing reference.")) }
    }
}

@Composable
internal fun UninstallDialog(copy: Copy,title: String,onDismiss: () -> Unit,onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(copy.t("Odinstalovat pomůcku?","Uninstall research aid?"))},text={Text(copy.t("$title\n\nOdstraní se stažená databáze a případný přenos. Rodokmeny, vlastní dokumenty a ostatní pomůcky zůstanou zachovány. Pomůcku můžete později znovu stáhnout.","$title\n\nThe downloaded database and any transfer will be removed. Family trees, personal documents and other reference packages will be preserved. You can download this package again later."))},confirmButton={TextButton(onClick=onConfirm) { Text(copy.t("Odinstalovat","Uninstall")) }},dismissButton={TextButton(onClick=onDismiss) { Text(copy.t("Zpět","Back")) }})
}

@Composable
private fun Metric(label: String,value: String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.titleSmall)
    }
}
