package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vestigiascriptorium.android.aids.reader.AidReaderSource
import com.vestigiascriptorium.android.aids.reader.AidRecord
import com.vestigiascriptorium.android.aids.reader.SearchScope
import com.vestigiascriptorium.android.ui.main.Copy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Suggestions never replace manual input until the user selects a concrete place. */
@Composable
internal fun GenealogyPlaceField(label: String,value: String,c: Copy,source: AidReaderSource?,onChange: (String)->Unit) {
    var focused by remember {mutableStateOf(false)}
    var chosen by remember {mutableStateOf<String?>(null)}
    var matches by remember {mutableStateOf<List<AidRecord>>(emptyList())}
    var resultQuery by remember {mutableStateOf("")}
    var loading by remember {mutableStateOf(false)}
    var unavailable by remember {mutableStateOf(false)}
    val preparing=source?.preparingIndex?.collectAsStateWithLifecycle()?.value ?: false
    LaunchedEffect(value,focused,source,chosen) {
        matches=emptyList();loading=false;unavailable=false
        if(!focused || value.trim().length<2 || chosen==value || source==null)return@LaunchedEffect
        delay(200);loading=true
        try {
            val language=if(c.cs)"cs" else "en"
            val names=source.page("places",language,value,"",0,searchScope=SearchScope.NAMES).records
            val additional=if(names.size<6)source.page("places",language,value,"",0,searchScope=SearchScope.ALL_FIELDS).records else emptyList()
            matches=(names+additional).distinctBy {it.id}.take(6);resultQuery=value
        } catch(e: Exception) {if(e is CancellationException)throw e;unavailable=true}
        finally {loading=false}
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(value,{chosen=null;matches=emptyList();onChange(it)},Modifier.fillMaxWidth().onFocusChanged {focused=it.isFocused},label={Text(label)},singleLine=true)
        if(focused) {
            if(loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if(preparing)c.t("Připravuji místní rejstřík obcí…","Preparing local place index…") else c.t("Hledám v Historické topografii…","Searching Historical topography…"),style=MaterialTheme.typography.bodySmall)
            }
            if(source==null || unavailable)Text(c.t("Obec lze zadat ručně. Našeptávání vyžaduje dostupnou nainstalovanou Historickou topografii.","Enter a municipality manually. Suggestions require an available installed Historical topography aid."),style=MaterialTheme.typography.bodySmall)
            if(resultQuery==value && chosen!=value)matches.forEach {place ->
                TextButton(onClick={chosen=place.title;matches=emptyList();onChange(place.title)},modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(place.title)
                        if(place.summary.isNotBlank())Text(place.summary,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
