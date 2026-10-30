package com.vestigiascriptorium.android.genealogy

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import com.vestigiascriptorium.android.ui.BackButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vestigiascriptorium.android.ui.AppVersionLabel
import com.vestigiascriptorium.android.ui.main.Copy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GenealogyWorkspace(controller: GenealogyController,copy: Copy,onExit: ()->Unit,onPeople: (String)->Unit={},selectedPerson: String?=null,onSelectionConsumed: ()->Unit={}) {
    val state by controller.state.collectAsStateWithLifecycle()
    val tree=state.tree
    val latestBusy by rememberUpdatedState(state.busy)
    var importing by rememberSaveable {mutableStateOf(false)}
    var create by rememberSaveable {mutableStateOf(false)}
    var editor by rememberSaveable {mutableStateOf<String?>(null)}
    var draftId by rememberSaveable {mutableStateOf(newId())}
    var editorSession by rememberSaveable {mutableStateOf(newId())}
    var graphSize by remember {mutableStateOf(IntSize.Zero)}
    var nodeBounds by remember(tree?.id) {mutableStateOf<Map<String,GraphBox>>(emptyMap())}
    val density=LocalDensity.current.density
    var panelFraction by rememberSaveable {mutableFloatStateOf(.6f)}
    var dirty by remember {mutableStateOf(false)}
    var discard by remember {mutableStateOf(false)}
    var relative by rememberSaveable {mutableStateOf<String?>(null)}
    var evidence by rememberSaveable {mutableStateOf<String?>(null)}
    var relativeAfterSave by rememberSaveable {mutableStateOf<String?>(null)}
    var pending by remember {mutableStateOf(false)}
    var pendingRevision by remember {mutableLongStateOf(0)}
    val editorStates=rememberSaveableStateHolder()
    var localError by remember {mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val windowSize=LocalWindowInfo.current.containerSize
    val wide=with(LocalDensity.current) {windowSize.width.toDp()>=700.dp}
    fun dismissEditor() {editorStates.removeState(editorSession);editor=null;dirty=false}
    fun closeEditor() {if(state.busy)return;if(dirty)discard=true else dismissEditor()}
    fun select(id: String) {
        if(dirty) {discard=true;return}
        editorStates.removeState(editorSession);editorSession=newId()
        val p=tree?.people?.firstOrNull {it.id==id};val e=tree?.events?.firstOrNull {it.id==id}
        val x=nodeBounds[id]?.x ?: p?.x ?: e?.x ?: 0f;val y=nodeBounds[id]?.y ?: p?.y ?: e?.y ?: 0f
        val zoom=state.view.zoom
        val bounds=nodeBounds[id]
        if(bounds==null && e!=null)
            controller.view(state.view.copy(selected=id))
        else controller.view(state.view.copy(selected=id,x=graphSize.width/density/2-(x+(bounds?.width ?: 244f)/2)*zoom,y=30-y*zoom))
        editor=id;dirty=false
    }
    LaunchedEffect(selectedPerson,state.ready,tree?.id,graphSize) {
        if(selectedPerson!=null && state.ready && tree!=null && graphSize.width>0) {
            if(tree.people.any {it.id==selectedPerson})select(selectedPerson)
            onSelectionConsumed()
        }
    }
    // Keep drafts visible if saving fails. Close only after a successful committed command.
    LaunchedEffect(state.revision,state.error) {
        if(pending && state.revision>pendingRevision) {
            if(editor=="new") state.tree?.people?.firstOrNull {it.id==draftId}?.let {p ->
                controller.view(state.view.copy(selected=p.id,x=30-p.x*state.view.zoom,y=40-p.y*state.view.zoom))
            }
            dismissEditor();relative=relativeAfterSave;relativeAfterSave=null;evidence=null;pending=false
        }
        else if(pending && state.error!=null) {pending=false;relativeAfterSave=null}
    }
    BackHandler {when {evidence!=null -> evidence=null;relative!=null -> relative=null;editor!=null -> closeEditor();tree!=null -> controller.closeTree();else -> onExit()}}
    @Composable fun EditorBody() {
        val currentTree=tree ?: return
        val id=editor ?: return
        val person=if(id=="new") Person(id=draftId,x=(-state.view.x+40)/state.view.zoom,y=(-state.view.y+40)/state.view.zoom) else currentTree.people.firstOrNull {it.id==id}
        if(person!=null) PersonPanel(currentTree,person,id=="new",copy,state.busy,onDirty={dirty=it},onClose=::closeEditor,
            onExpand={},media=controller.media,
            onSave={p,b,d -> pending=true;pendingRevision=state.revision;controller.edit {GenealogyEdits.person(it,p,b,d)}},
            onSaveAndRelative={p,b,d ->relativeAfterSave=p.id;pending=true;pendingRevision=state.revision;controller.edit {GenealogyEdits.person(it,p,b,d)}},
            onRelative={relative=person.id},onEvidence={evidence=person.id},onOpenEvent={eventId ->editor=eventId;dirty=false},onEvent={kind ->
                val old=currentTree.events.firstOrNull {it.kind==kind && it.roles["subject"]==person.id && kind!="genealogy.mentioned"}
                if(old!=null) {editor=old.id;dirty=false}
                else {pending=true;pendingRevision=state.revision;controller.edit {it.copy(events=it.events+LifeEvent(kind=kind,roles=mapOf("subject" to person.id),x=person.x+260,y=person.y+120)).also(GenealogyEdits::validate)}}
            },onOpen={doc ->
                try {context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(if(doc.file.startsWith("managed:"))GenealogyFileProvider.uri(context,doc.file.removePrefix("managed:")) else Uri.parse(doc.file),doc.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}
                catch(_: Exception) {localError=copy.t("Doklad nelze otevřít. Soubor mohl být přesunut nebo chybí vhodná aplikace.","Unable to open evidence. The file may have moved or no suitable app is installed.")}
            })
        else currentTree.events.firstOrNull {it.id==id}?.let {event -> EventPanel(currentTree,event,copy,state.busy,{dirty=it},::closeEditor,{value -> pending=true;pendingRevision=state.revision;controller.edit {GenealogyEdits.event(it,value)}})}
    }
    @Composable fun Panel() {editor?.let {id -> editorStates.SaveableStateProvider(editorSession) {EditorBody()}}}
    Scaffold(topBar={TopAppBar(title={Column {Text(tree?.title ?: copy.t("Rodokmeny","Family trees"),maxLines=1,overflow=TextOverflow.Ellipsis);AppVersionLabel()}},
        navigationIcon={BackButton(enabled=!state.busy,onClick={if(editor!=null)closeEditor() else if(tree!=null)controller.closeTree() else onExit()},label=copy.t("Zpět","Back"))},
        actions={if(tree!=null) TextButton(enabled=!state.busy && editor==null,onClick={onPeople(tree.id)}) {Text(copy.t("Osoby","People"))}})},
        bottomBar={if(tree!=null) Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            GenealogyToolTip(copy.t("Vrátit změnu","Undo")) {
            IconButton(enabled=state.undo && !state.busy && editor==null,onClick=controller::undo,modifier=Modifier.semantics {contentDescription=copy.t("Vrátit změnu","Undo")}) {GenealogyToolbarIcon(0)}
            }
            GenealogyToolTip(copy.t("Znovu","Redo")) {
            IconButton(enabled=state.redo && !state.busy && editor==null,onClick=controller::redo,modifier=Modifier.semantics {contentDescription=copy.t("Znovu","Redo")}) {GenealogyToolbarIcon(1)}
            }
            GenealogyToolTip(copy.t("Přizpůsobit","Fit")) {
            IconButton(modifier=Modifier.semantics {contentDescription=copy.t("Přizpůsobit","Fit")},enabled=!state.busy && tree.people.isNotEmpty(),onClick={
                val nodes=nodeBounds.values.toList()
                if(nodes.isEmpty())return@IconButton
                val width=graphSize.width/density
                val height=graphSize.height/density
                val minX=nodes.minOf {it.x};val minY=nodes.minOf {it.y}
                val maxX=nodes.maxOf {it.x+it.width};val maxY=nodes.maxOf {it.y+it.height}
                val scale=minOf((width-40)/(maxX-minX),(height-40)/(maxY-minY),1f).coerceIn(.2f,3f)
                controller.view(state.view.copy(x=20-minX*scale,y=20-minY*scale,zoom=scale))
            }) {GenealogyToolbarIcon(2)}
            }
            GenealogyToolTip(copy.t("Manželky v boxu manžela","Wives in husband’s box")) {
            IconToggleButton(checked=state.collapseWives,enabled=!state.busy && editor==null,onCheckedChange=controller::collapseWives,modifier=Modifier.semantics {contentDescription=copy.t("Manželky v boxu manžela","Wives in husband’s box")}) {GenealogyToolbarIcon(3)}
            }
            Button(enabled=!state.busy,onClick={if(dirty)discard=true else {editorStates.removeState(editorSession);editorSession=newId();draftId=newId();editor="new";dirty=false}}) {Text(copy.t("+ Osoba","+ Person"))}
        }}
    ) {padding ->
        if(!state.ready) Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        else if(tree==null) LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {Text(copy.t("Vaše rodokmeny","Your family trees"),style=MaterialTheme.typography.headlineMedium)}
            item {Text(copy.t("Začněte osobou. Prameny můžete připojit později.","Start with a person. Attach sources later."))}
            item {Button(enabled=!state.busy,onClick={create=true}) {Text(copy.t("Nový rodokmen","New family tree"))}}
            item {OutlinedButton(enabled=!state.busy,onClick={importing=true}) {Text(copy.t("Importovat rodokmen","Import family tree"))}}
            items(state.trees,key={it.first}) {(id,title) -> OutlinedCard(onClick={controller.open(id)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text(title,Modifier.padding(20.dp),style=MaterialTheme.typography.titleLarge)}}
        }
        else Row(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                GenealogyGraph(tree,state.view,copy,controller::view,::select,{id,x,y -> if(!state.busy)controller.edit {GenealogyEdits.move(it,id,x,y)}},Modifier.fillMaxSize().onSizeChanged {graphSize=it},enabled=!state.busy,onBounds={nodeBounds=it},media=controller.media,collapseWives=state.collapseWives)
                if(tree.people.isEmpty()) Column(Modifier.align(Alignment.Center).padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(copy.t("Přidejte první osobu","Add the first person"),style=MaterialTheme.typography.headlineSmall)
                    Text(copy.t("Graf nevyžaduje stránku matriky ani jiný doklad.","The graph does not require a register page or evidence."))
                }
                if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if(wide && editor!=null) Surface(Modifier.width(360.dp).fillMaxHeight(),tonalElevation=3.dp) {Panel()}
        }
    }
    // The same editor is used in the bottom sheet and the wide-window side panel.
    if(tree!=null && editor!=null && !wide) ResizableEditorPanel(copy,panelFraction,{panelFraction=it},::closeEditor) {Panel()}
    // Wide panels are composed below to avoid separate editor state implementations.
    if(importing || state.importPreview!=null) GenealogyImportDialog(controller,state,copy,onDismiss={importing=false;controller.cancelImport()})
    if(create) NameDialog(copy,state.busy,onDismiss={create=false},onCreate={controller.create(it);create=false})
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text(copy.t("Zahodit změny?","Discard changes?"))},text={Text(copy.t("Rozpracované údaje nejsou uložené.","The edited details have not been saved."))},confirmButton={TextButton(onClick={discard=false;dismissEditor()}) {Text(copy.t("Zahodit","Discard"))}},dismissButton={TextButton(onClick={discard=false}) {Text(copy.t("Pokračovat v úpravách","Keep editing"))}})
    relative?.let {id -> if(tree!=null) RelativeDialog(tree,id,copy,state.busy,onDismiss={relative=null},onSave={existing,newPerson,kind,secondParent ->
        pending=true;pendingRevision=state.revision
        controller.edit {before ->
            val selected=before.people.first {it.id==id}
            val other=existing ?: newId()
            val dy=if(kind in listOf(Relative.FATHER,Relative.MOTHER)) -250f else if(kind==Relative.SPOUSE) 0f else 250f
            val dx=if(kind==Relative.MOTHER || kind==Relative.SPOUSE) 300f else -50f
            val pos=GenealogyEdits.freePosition(before,selected.x+dx,selected.y+dy)
            val withPerson=if(existing!=null) before else before.copy(people=before.people+newPerson.copy(id=other,x=pos.first,y=pos.second))
            val related=GenealogyEdits.relate(withPerson,id,other,kind)
            if(secondParent!=null)GenealogyEdits.relate(related,other,secondParent,if(kind==Relative.CHILD_AS_FATHER)Relative.MOTHER else Relative.FATHER) else related
        }
    })}
    evidence?.let {id -> EvidenceDialog(id,copy,state.busy,media=controller.media,onDismiss={evidence=null},onSave={doc ->pending=true;pendingRevision=state.revision;controller.edit {it.copy(evidence=it.evidence+doc).also(GenealogyEdits::validate)}})}
    (state.error?.let {problem(it,copy)} ?: localError)?.let {message -> AlertDialog(onDismissRequest={controller.clearError();localError=null},title={Text(copy.t("Změnu nelze dokončit","Unable to complete change"))},text={Text(message)},confirmButton={TextButton(onClick={controller.clearError();localError=null}) {Text("OK")}})}
}

private fun problem(code: String,c: Copy)=if(code.startsWith("import:"))code.removePrefix("import:") else when(code) {
    "image" -> c.t("Vybraný soubor není podporovaný obrázek.","The selected file is not a supported image.")
    "imageSize" -> c.t("Obrázek přesahuje limit 32 MB.","The image exceeds the 32 MB limit.")
    "date" -> c.t("Zadejte platné celé datum nebo čtyřmístný rok.","Enter a valid full date or four-digit year.")
    "dateRange" -> c.t("Údaj připomínán do nesmí předcházet údaji od.","Mentioned until cannot precede mentioned from.")
    "name" -> c.t("Vyplňte název rodokmenu a jméno osoby.","Enter a tree title and a person's name.")
    "self" -> c.t("Osoba nemůže být svým vlastním příbuzným ani obsadit dvě role v jedné události.","A person cannot be related to themselves or occupy two roles in one event.")
    "cycle" -> c.t("Vazba by vytvořila cyklus rodičovství.","The relationship would create an ancestry cycle.")
    "occupied" -> c.t("Tato rodičovská role je už obsazená. Nejprve zkontrolujte existující událost.","This parent role is already occupied. Review the existing event first.")
    "duplicate" -> c.t("Tato vazba již existuje.","This relationship already exists.")
    else -> c.t("Data se nepodařilo bezpečně načíst nebo uložit. Změna nebyla potvrzena; zkuste ji znovu.","Unable to safely read or save data. The change was not committed; try again.")
}
