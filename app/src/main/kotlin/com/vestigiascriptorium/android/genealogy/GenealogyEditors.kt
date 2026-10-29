package com.vestigiascriptorium.android.genealogy

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
internal fun NameDialog(c: Copy,busy: Boolean,onDismiss: ()->Unit,onCreate: (String)->Unit) {
    var name by rememberSaveable {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(c.t("Nový rodokmen","New family tree"))},text={OutlinedTextField(name,{name=it},label={Text(c.t("Název rodokmenu","Tree title"))},singleLine=true)},
        confirmButton={TextButton(enabled=name.isNotBlank() && !busy,onClick={onCreate(name)}) {Text(c.t("Vytvořit","Create"))}},dismissButton={TextButton(onClick=onDismiss) {Text(c.t("Zrušit","Cancel"))}})
}

@Composable
internal fun PersonPanel(tree: FamilyTree,person: Person,isNew: Boolean,c: Copy,busy: Boolean,onDirty: (Boolean)->Unit,onClose: ()->Unit,onExpand: ()->Unit,onSave: (Person,String,String)->Unit,onRelative: ()->Unit,onEvidence: ()->Unit,onEvent: (String)->Unit,onOpen: (Evidence)->Unit) {
    var editing by rememberSaveable(person.id) {mutableStateOf(isNew)}
    var name by rememberSaveable(person.id) {mutableStateOf(person.name)}
    var note by rememberSaveable(person.id) {mutableStateOf(person.note)}
    var gender by rememberSaveable(person.id) {mutableStateOf(person.gender)}
    val birthValue=tree.events.firstOrNull {it.kind=="genealogy.birth" && it.roles["subject"]==person.id}?.date.orEmpty()
    val deathValue=tree.events.firstOrNull {it.kind=="genealogy.death" && it.roles["subject"]==person.id}?.date.orEmpty()
    var birth by rememberSaveable(person.id) {mutableStateOf(birthValue)}
    var death by rememberSaveable(person.id) {mutableStateOf(deathValue)}
    val changed=name!=person.name || note!=person.note || gender!=person.gender || birth!=birthValue || death!=deathValue
    LaunchedEffect(changed) {onDirty(changed)}
    Column(Modifier.fillMaxSize().padding(20.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(isNew)c.t("Nová osoba","New person") else person.name,style=MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(enabled=!busy,onClick=onClose) {Text(c.t("Zavřít","Close"))}
            if(editing) Button(enabled=!busy && name.isNotBlank(),onClick={onSave(person.copy(name=name,gender=gender,note=note),birth,death)}) {Text(c.t("Uložit osobu","Save person"))}
            else Button(enabled=!busy,onClick={editing=true;onExpand()}) {Text(c.t("Upravit","Edit"))}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(editing) {
                OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text(c.t("Jméno osoby","Person name"))},singleLine=true)
                Choice(c.t("Pohlaví","Gender"),listOf("unknown","male","female"),gender,{gender=it}) {when(it) {"male" -> c.t("Muž","Male");"female" -> c.t("Žena","Female");else -> c.t("Neurčeno","Unknown")}}
                Text(c.t("Datum lze zapsat i přibližně, např. kolem 1820. Prázdné datum zůstane neznámé.","Dates can be approximate, e.g. about 1820. Empty dates remain unknown."),style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(birth,{birth=it},Modifier.fillMaxWidth(),label={Text(c.t("Datum narození","Birth date"))},singleLine=true)
                OutlinedTextField(death,{death=it},Modifier.fillMaxWidth(),label={Text(c.t("Datum úmrtí","Death date"))},singleLine=true)
                OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text(c.t("Poznámky k osobě","Person notes"))},minLines=3)
            } else {
                lifeDates(tree,person.id).takeIf {it.isNotBlank()}?.let {Text(it)}
                if(person.note.isNotBlank()) Text(person.note)
                Text(c.t("Události a vazby","Events and relationships"),style=MaterialTheme.typography.titleMedium)
                OutlinedButton(enabled=!busy,onClick=onRelative) {Text(c.t("Přidat příbuzného","Add relative"))}
                tree.events.filter {person.id in it.roles.values}.forEach {event ->
                    Text(listOf(event.title.ifBlank {eventLabel(event.kind,c)},event.date,event.place).filter {it.isNotBlank()}.joinToString(" · "))
                    event.roles.forEach {(role,id) -> if(id!=person.id) Text("${eventRoleLabel(event.kind,role,c)}: ${tree.people.firstOrNull {it.id==id}?.name.orEmpty()}",style=MaterialTheme.typography.bodySmall)}
                }
                var eventKind by rememberSaveable(person.id) {mutableStateOf("genealogy.mentioned")}
                Choice(c.t("Nová událost","New event"),GenealogyEdits.kinds.filter {it !in listOf("genealogy.parentage","genealogy.spousal-record","genealogy.marriage")},eventKind,{eventKind=it}) {eventLabel(it,c)}
                TextButton(enabled=!busy,onClick={onEvent(eventKind)}) {Text(c.t("Přidat událost","Add event"))}
                Text(c.t("Údaje události upravíte klepnutím na její box v grafu.","Tap an event box in the graph to edit its details."),style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(c.t("Dokumentace","Documentation"),style=MaterialTheme.typography.titleMedium)
                tree.evidence.filter {it.person==person.id}.forEach {doc ->
                    Text(doc.caption,style=MaterialTheme.typography.titleSmall)
                    if(doc.note.isNotBlank())Text(doc.note)
                    if(doc.file.isNotBlank()) TextButton(onClick={onOpen(doc)}) {Text(c.t("Otevřít doklad","Open evidence"))}
                }
                OutlinedButton(enabled=!busy,onClick=onEvidence) {Text(c.t("Připojit dokumentaci","Attach documentation"))}
            }
        }
    }
}

@Composable
internal fun EventPanel(tree: FamilyTree,event: LifeEvent,c: Copy,busy: Boolean,onDirty: (Boolean)->Unit,onClose: ()->Unit,onSave: (LifeEvent)->Unit) {
    var title by rememberSaveable(event.id) {mutableStateOf(event.title)}
    var date by rememberSaveable(event.id) {mutableStateOf(event.date)}
    var place by rememberSaveable(event.id) {mutableStateOf(event.place)}
    var note by rememberSaveable(event.id) {mutableStateOf(event.note)}
    var kind by rememberSaveable(event.id) {mutableStateOf(event.kind)}
    var roles by rememberSaveable(event.id) {mutableStateOf(event.roles)}
    val changed=kind!=event.kind || title!=event.title || date!=event.date || place!=event.place || note!=event.note || roles!=event.roles
    LaunchedEffect(changed) {onDirty(changed)}
    Column(Modifier.fillMaxSize().padding(20.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(eventLabel(event.kind,c),style=MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(enabled=!busy,onClick=onClose) {Text(c.t("Zavřít","Close"))}
            Button(enabled=!busy,onClick={onSave(event.copy(kind=kind,title=title,date=date,place=place,note=note,roles=roles))}) {Text(c.t("Uložit událost","Save event"))}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Choice(c.t("Typ události","Event type"),GenealogyEdits.kinds.filter {GenealogyEdits.rolesFor(it)==GenealogyEdits.rolesFor(event.kind)},kind,{kind=it}) {eventLabel(it,c)}
            OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text(c.t("Název události","Event title"))})
            OutlinedTextField(date,{date=it},Modifier.fillMaxWidth(),label={Text(c.t("Datum události","Event date"))})
            OutlinedTextField(place,{place=it},Modifier.fillMaxWidth(),label={Text(c.t("Místo události","Event place"))})
            OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text(c.t("Poznámka k události","Event note"))},minLines=2)
            Text(c.t("Osoby a role","People and roles"),style=MaterialTheme.typography.titleMedium)
            GenealogyEdits.rolesFor(event.kind).forEach {role ->
                Choice(eventRoleLabel(kind,role,c),listOf("")+tree.people.map {it.id},roles[role].orEmpty(),{id ->roles=if(id.isEmpty())roles-role else roles+(role to id)}) {id ->tree.people.firstOrNull {it.id==id}?.name ?: c.t("Nepřiřazeno","Unassigned")}
            }
        }
    }
}

@Composable
internal fun <T> Choice(label: String,options: List<T>,value: T,onChange: (T)->Unit,text: (T)->String) {
    var open by remember {mutableStateOf(false)}
    Box {OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()) {Text("$label: ${text(value)}")}
        DropdownMenu(open,{open=false},modifier=Modifier.heightIn(max=300.dp)) {options.forEach {option -> DropdownMenuItem(text={Text(text(option))},onClick={onChange(option);open=false})} }
    }
}

@Composable
internal fun RelativeDialog(tree: FamilyTree,person: String,c: Copy,busy: Boolean,onDismiss: ()->Unit,onSave: (String?,String,Relative)->Unit) {
    var kind by rememberSaveable {mutableStateOf(Relative.FATHER)}
    var existing by rememberSaveable {mutableStateOf("")}
    var name by rememberSaveable {mutableStateOf("")}
    var query by rememberSaveable {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(c.t("Přidat příbuzného","Add relative"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Choice(c.t("Vztah k vybrané osobě","Relationship to selected person"),Relative.entries.toList(),kind,{kind=it}) {when(it) {
            Relative.FATHER ->c.t("Otec","Father");Relative.MOTHER ->c.t("Matka","Mother");Relative.SPOUSE ->c.t("Partner / partnerka","Partner");Relative.CHILD_AS_FATHER ->c.t("Dítě · vybraná osoba je otec","Child · selected person is father");Relative.CHILD_AS_MOTHER ->c.t("Dítě · vybraná osoba je matka","Child · selected person is mother")}}
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(c.t("Hledat existující osobu","Find existing person"))},singleLine=true)
        Choice(c.t("Osoba","Person"),listOf("")+tree.people.filter {it.id!=person && (it.name.contains(query,true) || it.id==existing)}.take(60).map {it.id},existing,{existing=it}) {id -> tree.people.firstOrNull {it.id==id}?.let {"${it.name} ${lifeDates(tree,id)}"} ?: c.t("Vytvořit novou osobu","Create new person")}
        if(existing.isEmpty()) OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text(c.t("Jméno nové osoby","New person's name"))},singleLine=true)
        Text(c.t("Uloží se záznam vztahu. Datum ani matriční doklad nejsou povinné.","A relationship record will be saved. No date or register page is required."),style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=!busy && (existing.isNotEmpty() || name.isNotBlank()),onClick={onSave(existing.takeIf {it.isNotEmpty()},name,kind)}) {Text(c.t("Připojit osobu","Connect person"))}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss) {Text(c.t("Zrušit","Cancel"))}})
}

@Composable
internal fun EvidenceDialog(person: String,c: Copy,busy: Boolean,onDismiss: ()->Unit,onSave: (Evidence)->Unit) {
    var caption by rememberSaveable {mutableStateOf("")}
    var note by rememberSaveable {mutableStateOf("")}
    var uri by rememberSaveable {mutableStateOf("")}
    var mime by rememberSaveable {mutableStateOf("application/octet-stream")}
    var error by remember {mutableStateOf(false)}
    val context=LocalContext.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {value -> if(value!=null) {
        try {context.contentResolver.takePersistableUriPermission(value,Intent.FLAG_GRANT_READ_URI_PERMISSION);uri=value.toString();mime=context.contentResolver.getType(value) ?: "application/octet-stream";error=false}
        catch(_: Exception) {error=true}
    }}
    AlertDialog(onDismissRequest=onDismiss,title={Text(c.t("Dokumentace osoby","Person documentation"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(caption,{caption=it},Modifier.fillMaxWidth(),label={Text(c.t("Popisek dokladu / citace","Evidence caption / citation"))})
        OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text(c.t("Poznámka k dokladu","Evidence note"))},minLines=2)
        OutlinedButton(onClick={picker.launch(arrayOf("image/*","application/pdf","text/plain"))}) {Text(if(uri.isEmpty())c.t("Vybrat soubor","Choose file") else c.t("Změnit vybraný soubor","Change selected file"))}
        if(uri.isNotEmpty()) Text(c.t("Soubor vybrán","File selected"))
        Text(c.t("Soubor zůstává ve vybraném úložišti. Pokud jej přesunete nebo odeberete přístup, doklad nepůjde otevřít. Lze uložit také samotnou citaci.","The file stays in its selected location. Moving it or revoking access may prevent opening it. You can also save a citation alone."),style=MaterialTheme.typography.bodySmall)
        if(error)Text(c.t("Trvalý přístup k souboru se nepodařilo získat.","Unable to retain access to the file."),color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(enabled=!busy && caption.isNotBlank(),onClick={onSave(Evidence(person=person,caption=caption.trim(),note=note,file=uri,mime=mime))}) {Text(c.t("Uložit doklad","Save evidence"))}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss) {Text(c.t("Zrušit","Cancel"))}})
}

@Composable
internal fun PeopleDialog(tree: FamilyTree,c: Copy,onDismiss: ()->Unit,onSelect: (Person)->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(c.t("Osoby v rodokmenu","People in tree"))},text={Column {
        OutlinedTextField(query,{query=it},label={Text(c.t("Hledat osobu","Find person"))},singleLine=true)
        LazyColumn(Modifier.heightIn(max=400.dp)) {items(tree.people.filter {it.name.contains(query,true)}.sortedBy {it.name},key={it.id}) {p -> TextButton(onClick={onSelect(p)}) {Text("${p.name} ${lifeDates(tree,p.id)}")}}}
    }},confirmButton={TextButton(onClick=onDismiss) {Text(c.t("Zavřít","Close"))}})
}
