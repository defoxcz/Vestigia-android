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
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vestigiascriptorium.android.ui.main.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

@Composable
internal fun NameDialog(c: Copy,busy: Boolean,onDismiss: ()->Unit,onCreate: (String)->Unit) {
    var name by rememberSaveable {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(c.t("Nový rodokmen","New family tree"))},text={OutlinedTextField(name,{name=it},label={Text(c.t("Název rodokmenu","Tree title"))},singleLine=true)},
        confirmButton={TextButton(enabled=name.isNotBlank() && !busy,onClick={onCreate(name)}) {Text(c.t("Vytvořit","Create"))}},dismissButton={TextButton(onClick=onDismiss) {Text(c.t("Zrušit","Cancel"))}})
}

@Composable
internal fun PersonPanel(tree: FamilyTree,person: Person,isNew: Boolean,c: Copy,busy: Boolean,onDirty: (Boolean)->Unit,onClose: ()->Unit,onExpand: ()->Unit,onSave: (Person,String,String)->Unit,onRelative: ()->Unit,onEvidence: ()->Unit,onOpenEvent: (String)->Unit,onEvent: (String)->Unit,onOpen: (Evidence)->Unit,media: GenealogyMedia?=null,onSaveAndRelative: (Person,String,String)->Unit={_,_,_->}) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var portrait by rememberSaveable(person.id) {mutableStateOf(person.portrait)}
    var photoBusy by remember {mutableStateOf(false)}
    var photoError by remember {mutableStateOf(false)}
    fun importPortrait(uri: Uri?) {if(uri!=null && media!=null)scope.launch {
        photoBusy=true;photoError=false
        try {portrait=withContext(Dispatchers.IO) {val job=kotlin.coroutines.coroutineContext;media.importImage(context.contentResolver,uri) {job.ensureActive()}}}
        catch(e: Exception) {if(e is kotlinx.coroutines.CancellationException)throw e;photoError=true}
        finally {photoBusy=false}
    }}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {importPortrait(it)}
    val cloudPicker=rememberLauncherForActivityResult(CloudImagePicker()) {importPortrait(it)}
    var editing by rememberSaveable(person.id) {mutableStateOf(isNew)}
    var given by rememberSaveable(person.id) {mutableStateOf(person.givenName)}
    var middle by rememberSaveable(person.id) {mutableStateOf(person.middleName)}
    var surname by rememberSaveable(person.id) {mutableStateOf(person.surname)}
    var mentionedFrom by rememberSaveable(person.id) {mutableStateOf(person.mentionedFrom)}
    var mentionedTo by rememberSaveable(person.id) {mutableStateOf(person.mentionedTo)}
    var validDates by remember {mutableStateOf(mapOf("birth" to true,"death" to true,"from" to true,"to" to true))}
    val datesValid=validDates.values.all {it} && GenealogyDates.ordered(mentionedFrom,mentionedTo)
    val named=listOf(given,middle,surname,person.legacyName).any {it.isNotBlank()}
    var note by rememberSaveable(person.id) {mutableStateOf(person.note)}
    var gender by rememberSaveable(person.id) {mutableStateOf(person.gender)}
    val birthValue=tree.events.firstOrNull {it.kind=="genealogy.birth" && it.roles["subject"]==person.id}?.date.orEmpty()
    val deathValue=tree.events.firstOrNull {it.kind=="genealogy.death" && it.roles["subject"]==person.id}?.date.orEmpty()
    var birth by rememberSaveable(person.id) {mutableStateOf(birthValue)}
    var death by rememberSaveable(person.id) {mutableStateOf(deathValue)}
    val changed=portrait!=person.portrait || photoBusy || given!=person.givenName || middle!=person.middleName || surname!=person.surname || mentionedFrom!=person.mentionedFrom || mentionedTo!=person.mentionedTo || !datesValid || note!=person.note || gender!=person.gender || birth!=birthValue || death!=deathValue
    LaunchedEffect(changed) {onDirty(changed)}
    Column(Modifier.fillMaxSize().padding(20.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(isNew)c.t("Nová osoba","New person") else person.name,style=MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(enabled=!busy,onClick=onClose) {Text(c.t("Zavřít","Close"))}
            if(editing) Button(enabled=!busy && !photoBusy && named && datesValid,onClick={onSave(person.copy(portrait=portrait,givenName=given,middleName=middle,surname=surname,mentionedFrom=mentionedFrom,mentionedTo=mentionedTo,gender=gender,note=note),birth,death)}) {Text(c.t("Uložit osobu","Save person"))}
            else Button(enabled=!busy,onClick={editing=true;onExpand()}) {Text(c.t("Upravit","Edit"))}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(portrait.isNotEmpty())PersonPortrait(portrait,media,c,Modifier.size(160.dp))
            if(editing) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier=Modifier.weight(1f).fillMaxHeight(),enabled=!busy && !photoBusy && media!=null,onClick={picker.launch(arrayOf("image/*"))}) {Text(if(portrait.isEmpty())c.t("Vložit obrázek","Add portrait") else c.t("Změnit obrázek","Change portrait"))}
                    OutlinedButton(modifier=Modifier.weight(1f).fillMaxHeight(),enabled=!busy && !photoBusy && media!=null,onClick={cloudPicker.launch(Unit)}) {Text(c.t("Google Disk / soubory","Google Drive / files"))}
                }
                if(portrait.isNotEmpty())TextButton(enabled=!busy && !photoBusy,onClick={portrait=""}) {Text(c.t("Odstranit obrázek","Remove portrait"))}
                if(photoBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(photoError)Text(c.t("Obrázek nelze načíst. Zkontrolujte připojení a přístup ke zdroji. Vyberte podporovaný obrázek do 32 MB.","Unable to read image. Check your connection and access. Choose a supported image up to 32 MB."),color=MaterialTheme.colorScheme.error)
                if(person.legacyName.isNotBlank())Text(c.t("Dosavadní celé jméno: ","Legacy full name: ")+person.legacyName,style=MaterialTheme.typography.bodySmall)
                NameFields(given,middle,surname,c,{given=it},{middle=it},{surname=it})
                Choice(c.t("Pohlaví","Gender"),listOf("unknown","male","female"),gender,{gender=it}) {when(it) {"male" -> c.t("Muž","Male");"female" -> c.t("Žena","Female");else -> c.t("Neurčeno","Unknown")}}
                GenealogyDateField(c.t("Datum narození","Birth date"),birth,c) {v,ok ->birth=v;validDates=validDates+("birth" to ok)}
                GenealogyDateField(c.t("Datum úmrtí","Death date"),death,c) {v,ok ->death=v;validDates=validDates+("death" to ok)}
                GenealogyDateField(c.t("Připomínán od","Mentioned from"),mentionedFrom,c) {v,ok ->mentionedFrom=v;validDates=validDates+("from" to ok)}
                GenealogyDateField(c.t("Připomínán do","Mentioned until"),mentionedTo,c) {v,ok ->mentionedTo=v;validDates=validDates+("to" to ok)}
                if(!GenealogyDates.ordered(mentionedFrom,mentionedTo))Text(c.t("Připomínán do nesmí předcházet údaji od.","Mentioned until cannot precede mentioned from."),color=MaterialTheme.colorScheme.error)
                OutlinedButton(enabled=!busy && !photoBusy && named && datesValid,onClick={onSaveAndRelative(person.copy(portrait=portrait,givenName=given,middleName=middle,surname=surname,mentionedFrom=mentionedFrom,mentionedTo=mentionedTo,gender=gender,note=note),birth,death)}) {Text(c.t("Přidat příbuzného","Add relative"))}
                Text(c.t("Uloží osobu a otevře výběr příbuzného.","Saves the person and opens the relative picker."),style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text(c.t("Poznámky k osobě","Person notes"))},minLines=3)
            } else {
                lifeDates(tree,person.id).takeIf {it.isNotBlank()}?.let {Text(it)}
                if(person.mentionedFrom.isNotBlank())Text(c.t("Připomínán od: ","Mentioned from: ")+GenealogyDates.format(person.mentionedFrom))
                if(person.mentionedTo.isNotBlank())Text(c.t("Připomínán do: ","Mentioned until: ")+GenealogyDates.format(person.mentionedTo))
                if(person.note.isNotBlank()) Text(person.note)
                Text(c.t("Události a vazby","Events and relationships"),style=MaterialTheme.typography.titleMedium)
                OutlinedButton(enabled=!busy,onClick=onRelative) {Text(c.t("Přidat příbuzného","Add relative"))}
                tree.events.filter {person.id in it.roles.values}.forEach {event ->
                    TextButton(enabled=!busy,onClick={onOpenEvent(event.id)}) {Text(listOf(event.title.ifBlank {eventLabel(event.kind,c)},event.roles["subject"]?.takeIf {it!=person.id && event.kind in listOf("genealogy.birth","genealogy.baptism","genealogy.parentage")}?.let {id ->tree.people.firstOrNull {it.id==id}?.name}.orEmpty(),GenealogyDates.format(event.date),event.place).filter {it.isNotBlank()}.joinToString(" · "))}
                    event.roles.forEach {(role,id) -> if(id!=person.id) Text("${personEventRoleLabel(tree,event,role,person.id,c)}: ${tree.people.firstOrNull {it.id==id}?.name.orEmpty()}",style=MaterialTheme.typography.bodySmall)}
                }
                var eventKind by rememberSaveable(person.id) {mutableStateOf("genealogy.mentioned")}
                Choice(c.t("Nová událost","New event"),GenealogyEdits.kinds.filter {it !in listOf("genealogy.parentage","genealogy.spousal-record","genealogy.marriage")},eventKind,{eventKind=it}) {eventLabel(it,c)}
                TextButton(enabled=!busy,onClick={onEvent(eventKind)}) {Text(c.t("Přidat událost","Add event"))}
                Text(c.t("Údaje události upravíte v tomto seznamu u osoby.","Edit event details in this person’s event list."),style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(c.t("Dokumentace","Documentation"),style=MaterialTheme.typography.titleMedium)
                tree.evidence.filter {it.person==person.id}.forEach {doc ->
                    Text(doc.caption,style=MaterialTheme.typography.titleSmall)
                    if(doc.note.isNotBlank())Text(doc.note)
                    if(doc.file.isNotBlank()) EvidencePreview(doc,media,c,{onOpen(doc)})
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
    var validDate by remember {mutableStateOf(true)}
    var place by rememberSaveable(event.id) {mutableStateOf(event.place)}
    var note by rememberSaveable(event.id) {mutableStateOf(event.note)}
    var kind by rememberSaveable(event.id) {mutableStateOf(event.kind)}
    var roles by rememberSaveable(event.id) {mutableStateOf(event.roles)}
    val changed=!validDate || kind!=event.kind || title!=event.title || date!=event.date || place!=event.place || note!=event.note || roles!=event.roles
    LaunchedEffect(changed) {onDirty(changed)}
    Column(Modifier.fillMaxSize().padding(20.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(eventLabel(event.kind,c),style=MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(enabled=!busy,onClick=onClose) {Text(c.t("Zavřít","Close"))}
            Button(enabled=!busy && validDate,onClick={onSave(event.copy(kind=kind,title=title,date=date,place=place,note=note,roles=roles))}) {Text(c.t("Uložit událost","Save event"))}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Choice(c.t("Typ události","Event type"),GenealogyEdits.kinds.filter {GenealogyEdits.rolesFor(it)==GenealogyEdits.rolesFor(event.kind)},kind,{kind=it}) {eventLabel(it,c)}
            OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text(c.t("Název události","Event title"))})
            GenealogyDateField(if(kind=="genealogy.marriage")c.t("Datum svatby","Wedding date") else c.t("Datum události","Event date"),date,c) {v,ok ->date=v;validDate=ok}
            if(kind=="genealogy.spousal-record")Text(c.t("Pro doloženou svatbu zvolte typ Sňatek a vyplňte datum svatby.","For a documented wedding, select Marriage and enter its date."),style=MaterialTheme.typography.bodySmall)
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
internal fun RelativeDialog(tree: FamilyTree,person: String,c: Copy,busy: Boolean,onDismiss: ()->Unit,onSave: (String?,Person,Relative,String?)->Unit) {
    var kind by rememberSaveable {mutableStateOf(Relative.FATHER)}
    var secondParent by rememberSaveable {mutableStateOf("")}
    var existing by rememberSaveable {mutableStateOf("")}
    var given by rememberSaveable {mutableStateOf("")}
    var middle by rememberSaveable {mutableStateOf("")}
    var surname by rememberSaveable {mutableStateOf("")}
    var query by rememberSaveable {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(c.t("Přidat příbuzného","Add relative"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Choice(c.t("Vztah k vybrané osobě","Relationship to selected person"),Relative.entries.toList(),kind,{kind=it;secondParent=""}) {when(it) {
            Relative.FATHER ->c.t("Otec","Father");Relative.MOTHER ->c.t("Matka","Mother");Relative.SPOUSE ->c.t("Partner / partnerka","Partner");Relative.CHILD_AS_FATHER ->c.t("Dítě · vybraná osoba je otec","Child · selected person is father");Relative.CHILD_AS_MOTHER ->c.t("Dítě · vybraná osoba je matka","Child · selected person is mother")}}
        if(kind in listOf(Relative.CHILD_AS_FATHER,Relative.CHILD_AS_MOTHER)) {
            val partners=remember(tree,person) {GenealogyPresentation(tree).person(person).partners.map {it.person}}
            if(partners.isNotEmpty())Choice(c.t("Druhý rodič dítěte","Child’s other parent"),listOf("")+partners.map {it.id},secondParent,{secondParent=it}) {key ->partners.firstOrNull {it.id==key}?.name ?: c.t("Neurčený druhý rodič","Other parent unspecified")}
        }
        OutlinedTextField(query,{query=it;existing=""},Modifier.fillMaxWidth(),label={Text(c.t("Hledat existující osobu","Find existing person"))},singleLine=true)
        val rows=remember(tree) {PeopleIndex.rows(tree,java.util.Locale.getDefault()).filter {it.person.id!=person}}
        val matches=remember(rows,query) {PeopleIndex.filter(rows,query)}
        if(query.isNotBlank() && existing.isEmpty()) {
            if(matches.isEmpty())Text(c.t("Žádná odpovídající osoba","No matching person"))
            matches.take(20).forEach {row ->
                TextButton(modifier=Modifier.fillMaxWidth(),onClick={existing=row.person.id;query=row.person.name}) {
                    Text(listOf(row.person.name,row.year?.toString().orEmpty()).filter {it.isNotEmpty()}.joinToString(" · "))
                }
            }
            if(matches.size>20)Text(c.t("Zpřesněte hledání pro další osoby.","Refine the search for more people."))
        }
        if(existing.isNotEmpty()) {
            Text(c.t("Vybraná osoba: ","Selected person: ")+tree.people.first {it.id==existing}.name)
            TextButton(onClick={existing="";query=""}) {Text(c.t("Vytvořit novou osobu","Create new person"))}
        }
        if(existing.isEmpty())NameFields(given,middle,surname,c,{given=it},{middle=it},{surname=it})
        Text(c.t("Uloží se záznam vztahu. Datum ani matriční doklad nejsou povinné.","A relationship record will be saved. No date or register page is required."),style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=!busy && (existing.isNotEmpty() || listOf(given,middle,surname).any {it.isNotBlank()}),onClick={onSave(existing.takeIf {it.isNotEmpty()},Person(givenName=given.trim(),middleName=middle.trim(),surname=surname.trim()),kind,secondParent.takeIf {it.isNotEmpty()})}) {Text(c.t("Připojit osobu","Connect person"))}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss) {Text(c.t("Zrušit","Cancel"))}})
}

@Composable
internal fun EvidenceDialog(person: String,c: Copy,busy: Boolean,onDismiss: ()->Unit,onSave: (Evidence)->Unit,media: GenealogyMedia?=null) {
    var caption by rememberSaveable {mutableStateOf("")}
    var note by rememberSaveable {mutableStateOf("")}
    var uri by rememberSaveable {mutableStateOf("")}
    var mime by rememberSaveable {mutableStateOf("application/octet-stream")}
    var makeCopy by rememberSaveable {mutableStateOf(true)}
    var error by remember {mutableStateOf(false)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var copying by remember {mutableStateOf(false)}
    fun selectFile(selected: Uri?,imageOnly: Boolean=false) {if(selected!=null)scope.launch {
        copying=true;error=false
        try {
            val result=withContext(Dispatchers.IO) {
                val resolver=context.contentResolver
                val type=resolver.getType(selected) ?: "application/octet-stream"
                if(makeCopy) {
                    val storage=media ?: throw GenealogyProblem("storage")
                    val job=kotlin.coroutines.coroutineContext
                    if(imageOnly || type.startsWith("image/")) {
                        val key=storage.importImage(resolver,selected) {job.ensureActive()}
                        "managed:$key" to storage.imageMime(key)
                    } else {
                        val input=resolver.openInputStream(selected) ?: throw java.io.FileNotFoundException()
                        "managed:"+storage.addDocument(input) {job.ensureActive()} to type
                    }
                } else {
                    resolver.takePersistableUriPermission(selected,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    selected.toString() to type
                }
            }
            uri=result.first;mime=result.second
        } catch(e: Exception) {if(e is kotlinx.coroutines.CancellationException)throw e;error=true}
        finally {copying=false}
    }}
    val cloudPicker=rememberLauncherForActivityResult(CloudImagePicker()) {selectFile(it,true)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {selectFile(it)}
    AlertDialog(onDismissRequest={if(!copying && !busy)onDismiss()},title={Text(c.t("Dokumentace osoby","Person documentation"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(caption,{caption=it},Modifier.fillMaxWidth(),label={Text(c.t("Popisek dokladu / citace","Evidence caption / citation"))})
        OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text(c.t("Poznámka k dokladu","Evidence note"))},minLines=2)
        Text(c.t("Uložení souboru","File storage"),style=MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().selectable(selected=makeCopy,enabled=!busy && !copying,role=Role.RadioButton,onClick={if(!makeCopy) {makeCopy=true;uri="";error=false}}),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected=makeCopy,onClick=null)
            Text(c.t("Vytvořit kopii ve Vestigii","Create a copy in Vestigia"))
        }
        Row(Modifier.fillMaxWidth().selectable(selected=!makeCopy,enabled=!busy && !copying,role=Role.RadioButton,onClick={if(makeCopy) {makeCopy=false;uri="";error=false}}),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected=!makeCopy,onClick=null)
            Text(c.t("Odkazovat na zdroj","Link to the source"))
        }
        OutlinedButton(enabled=!busy && !copying,onClick={picker.launch(arrayOf("image/*","application/pdf","text/plain"))}) {Text(if(uri.isEmpty())c.t("Vybrat soubor","Choose file") else c.t("Změnit vybraný soubor","Change selected file"))}
        if(media!=null)OutlinedButton(enabled=!busy && !copying,onClick={if(makeCopy)cloudPicker.launch(Unit) else picker.launch(arrayOf("image/*"))}) {Text(c.t("Google Disk / soubory","Google Drive / files"))}
        if(copying)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(uri.isNotEmpty()) {
            Text(c.t("Soubor vybrán","File selected"))
            if(mime.startsWith("image/") || mime=="application/pdf")EvidencePreview(Evidence(person=person,caption=caption.ifBlank {c.t("Náhled dokladu","Evidence preview")},file=uri,mime=mime),media,c,{
                try {context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(if(uri.startsWith("managed:"))GenealogyFileProvider.uri(context,uri.removePrefix("managed:")) else Uri.parse(uri),mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}
                catch(_: Exception) {error=true}
            })
        }
        Text(c.t("Doklady se zde pouze prohlížejí. Obrázek ani zdrojový soubor se neupravují.","Evidence is read-only here. Images and source files are never edited."),style=MaterialTheme.typography.bodySmall)
        if(makeCopy)Text(c.t("Kopie jsou v soukromé složce Vestigie v telefonu. Odinstalace aplikace je smaže; původní soubor zůstane zachován.","Copies are in Vestigia’s private folder on this phone. Uninstalling the app deletes them; the original remains unchanged."),style=MaterialTheme.typography.bodySmall)
        Text(if(uri.startsWith("managed:")) {if(mime.startsWith("image/"))c.t("Obrázek je uložen jako vlastní kopie. Původní soubor se nemění.","The image is stored as a local copy. The original is unchanged.") else c.t("Doklad je uložen jako vlastní kopie. Původní soubor se nemění.","The evidence is stored as a local copy. The original is unchanged.")} else if(makeCopy)c.t("Vyberte soubor pro vytvoření vlastní kopie.","Choose a file to create a private copy.") else c.t("Soubor zůstává ve vybraném úložišti. Pokud jej přesunete nebo odeberete přístup, doklad nepůjde otevřít. Lze uložit také samotnou citaci.","The file stays in its selected location. Moving it or revoking access may prevent opening it. You can also save a citation alone."),style=MaterialTheme.typography.bodySmall)
        if(error)Text(c.t("Soubor se nepodařilo načíst. Zkontrolujte připojení a přístup ke zdroji. Kopie může mít nejvýše 32 MB; odkaz vyžaduje trvalé oprávnění ke čtení.","Unable to read the file. Check your connection and access. Copies are limited to 32 MB; links require persistent read access."),color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(enabled=!busy && !copying && caption.isNotBlank(),onClick={onSave(Evidence(person=person,caption=caption.trim(),note=note,file=uri,mime=mime))}) {Text(c.t("Uložit doklad","Save evidence"))}},dismissButton={TextButton(enabled=!busy && !copying,onClick=onDismiss) {Text(c.t("Zrušit","Cancel"))}})
}

@Composable
internal fun NameFields(given: String,middle: String,surname: String,c: Copy,onGiven: (String)->Unit,onMiddle: (String)->Unit,onSurname: (String)->Unit) {
    OutlinedTextField(given,onGiven,Modifier.fillMaxWidth(),label={Text(c.t("Jméno","Given name"))},singleLine=true)
    OutlinedTextField(middle,onMiddle,Modifier.fillMaxWidth(),label={Text(c.t("Druhé jméno","Middle name"))},singleLine=true)
    OutlinedTextField(surname,onSurname,Modifier.fillMaxWidth(),label={Text(c.t("Příjmení","Surname"))},singleLine=true)
}
