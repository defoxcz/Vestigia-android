package com.vestigiascriptorium.android.genealogy

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vestigiascriptorium.android.ui.main.Copy

@Composable
internal fun GenealogyImportDialog(controller: GenealogyController,state: GenealogyState,c: Copy,onDismiss: ()->Unit) {
    val context=LocalContext.current
    var folder by rememberSaveable {mutableStateOf("")}
    val preview=state.importPreview
    var selected by rememberSaveable(preview?.root?.name) {mutableStateOf(preview?.data?.trees?.map {it.id}?.toSet().orEmpty())}
    LaunchedEffect(state.tree?.id) {if(state.tree!=null && preview==null)onDismiss()}
    val treePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri ->if(uri!=null) {
        folder=uri.toString()
    }}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->if(uri!=null) {
        val name=runCatching {context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0) else null}}.getOrNull() ?: "Importovaný rodokmen"
        controller.prepareImport(context.contentResolver,uri,name.substringBeforeLast('.',name),folder.takeIf {it.isNotEmpty()}?.let(Uri::parse),context.cacheDir)
    }}
    AlertDialog(onDismissRequest={if(!state.busy || preview==null)onDismiss()},title={Text(c.t("Import rodokmenu","Import family tree"))},text={Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(state.busy) {LinearProgressIndicator(Modifier.fillMaxWidth());Text(c.t("Zpracovávám import…","Processing import…"))}
        if(preview==null) {
            Text(c.t("GEDCOM 5.5.1, GEDCOM 7, GEDZIP 7 (.gdz) a projekt Vestigia (.vestigia).","GEDCOM 5.5.1, GEDCOM 7, GEDZIP 7 (.gdz) and Vestigia projects (.vestigia)."))
            Text(c.t("Import vytvoří nové rodokmeny. Existující data se nepřepisují.","Import creates new family trees. Existing data is not overwritten."))
            OutlinedButton(enabled=!state.busy,onClick={treePicker.launch(null)}) {Text(if(folder.isEmpty())c.t("Připojit složku médií (volitelné)","Select media folder (optional)") else c.t("Složka médií vybrána","Media folder selected"))}
            Button(enabled=!state.busy,onClick={picker.launch(arrayOf("*/*"))}) {Text(c.t("Vybrat soubor","Choose file"))}
        } else {
            Text(preview.data.format,style=MaterialTheme.typography.titleMedium)
            preview.data.trees.forEach {tree ->
                Row {Checkbox(checked=tree.id in selected,enabled=!state.busy,onCheckedChange={checked ->selected=if(checked)selected+tree.id else selected-tree.id});Column(Modifier.weight(1f)) {
                    Text(tree.title,style=MaterialTheme.typography.titleSmall)
                    Text(c.t("${tree.people.size} osob · ${tree.events.size} událostí · ${tree.people.count {it.portrait.isNotEmpty()}} portrétů","${tree.people.size} people · ${tree.events.size} events · ${tree.people.count {it.portrait.isNotEmpty()}} portraits"))
                }}
            }
            Text(c.t("Původní soubor bude zachován v soukromé zdrojové kopii. Nepodporované struktury nejsou plně editovatelné v Androidu.","The original file is retained in a private source copy. Unsupported structures are not fully editable on Android."))
            preview.data.warnings.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }},confirmButton={if(preview!=null)TextButton(enabled=!state.busy && selected.isNotEmpty(),onClick={controller.commitImport(selected)}) {Text(c.t("Potvrdit import","Confirm import"))}},dismissButton={TextButton(enabled=!state.busy || preview==null,onClick=onDismiss) {Text(c.t("Zrušit","Cancel"))}})
}
