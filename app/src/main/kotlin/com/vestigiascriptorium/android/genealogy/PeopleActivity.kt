package com.vestigiascriptorium.android.genealogy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.vestigiascriptorium.android.ui.BackButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vestigiascriptorium.android.account.AccountGate
import com.vestigiascriptorium.android.ui.AppVersionLabel
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class PeopleActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent.getStringExtra("tree") ?: run {finish();return}
        enableEdgeToEdge()
        setContent {VestigiaScriptoriumTheme {AccountGate {
            val copy=Copy(LocalConfiguration.current.locales[0].language=="cs")
            val loaded by produceState<Pair<Boolean,FamilyTree?>>(false to null,id) {
                val tree=withContext(Dispatchers.IO) {runCatching {GenealogyStore(File(noBackupFilesDir,"genealogy")).use {it.read(id)}}.getOrNull()}
                value=true to tree
            }
            val tree=loaded.second
            when {tree!=null -> PeopleScreen(tree,copy,{finish()}) {person -> setResult(RESULT_OK,Intent().putExtra("person",person.id));finish()}
                !loaded.first -> CircularProgressIndicator()
                else -> AlertDialog(onDismissRequest={finish()},title={Text(copy.t("Seznam nelze načíst","Unable to load people"))},text={Text(copy.t("Rodokmen nebyl změněn. Zkuste seznam znovu otevřít.","The tree has not been changed. Try reopening the list."))},confirmButton={TextButton(onClick={finish()}) {Text(copy.t("Zavřít","Close"))}})}
        }}}
    }
    companion object {fun intent(context: Context,tree: String)=Intent(context,PeopleActivity::class.java).putExtra("tree",tree)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeopleScreen(tree: FamilyTree,c: Copy,onBack: ()->Unit,onSelect: (Person)->Unit) {
    val configuration=LocalConfiguration.current
    val locale=Locale.getDefault(Locale.Category.FORMAT)
    val rows=remember(tree,configuration) {PeopleIndex.rows(tree,locale)}
    var query by rememberSaveable(tree.id) {mutableStateOf("")}
    var focused by remember {mutableStateOf(false)}
    var dismissed by remember {mutableStateOf<String?>(null)}
    val listState=rememberLazyListState()
    LaunchedEffect(query) {listState.requestScrollToItem(0)}
    val filtered=remember(rows,query) {PeopleIndex.filter(rows,query)}
    Scaffold(topBar={TopAppBar(title={Column {Text(c.t("Osoby","People"));AppVersionLabel()}},navigationIcon={BackButton(onClick=onBack,label=c.t("Zpět","Back"))})}) {padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal=16.dp)) {
            Text(tree.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(vertical=8.dp))
            ExposedDropdownMenuBox(expanded=focused && query.isNotBlank() && dismissed!=query && filtered.isNotEmpty(),onExpandedChange={dismissed=if(it)null else query}) {
                OutlinedTextField(query,{query=it;dismissed=null},singleLine=true,label={Text(c.t("Hledat osobu","Find person"))},
                    modifier=Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).onFocusChanged {focused=it.isFocused},
                    trailingIcon={if(query.isNotEmpty())TextButton(onClick={query="";dismissed=null}) {Text(c.t("Smazat","Clear"))}})
                ExposedDropdownMenu(expanded=focused && query.isNotBlank() && dismissed!=query && filtered.isNotEmpty(),onDismissRequest={dismissed=query}) {
                    filtered.take(6).forEach {row -> DropdownMenuItem(text={Text(listOf(row.person.name,row.year?.toString().orEmpty()).filter {it.isNotEmpty()}.joinToString(" · "))},onClick={onSelect(row.person)},modifier=Modifier.testTag("person-suggestion-${row.person.id}"))}
                }
            }
            Text(c.t("${filtered.size} osob · rok narození, jinak připomínán od","${filtered.size} people · birth year, otherwise first mention"),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp)) {
                Text(c.t("Příjmení","Surname"),Modifier.weight(.38f),style=MaterialTheme.typography.labelMedium)
                Text(c.t("Jméno","Given name"),Modifier.weight(.4f),style=MaterialTheme.typography.labelMedium)
                Text(c.t("Rok","Year"),Modifier.weight(.22f),style=MaterialTheme.typography.labelMedium)
            }
            if(filtered.isEmpty())Text(c.t("Žádná odpovídající osoba","No matching people"),Modifier.padding(16.dp))
            LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(filtered,key={it.person.id}) {row ->
                    OutlinedCard(onClick={onSelect(row.person)},modifier=Modifier.fillMaxWidth().testTag("person-row-${row.person.id}")) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(row.person.surname.ifBlank {"—"},Modifier.weight(.38f),maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(listOf(row.person.givenName,row.person.middleName).filter {it.isNotBlank()}.joinToString(" ").ifBlank {row.person.legacyName},Modifier.weight(.4f),maxLines=3,overflow=TextOverflow.Ellipsis)
                            Text((if(row.fromMention)c.t("př. ","fl. ") else "")+(row.year?.toString() ?: "—"),Modifier.weight(.22f),style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
