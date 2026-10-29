package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vestigiascriptorium.android.ui.main.Copy
import java.util.Locale

@Composable
internal fun GenealogyDateField(label: String,initial: String,c: Copy,onChange: (String,Boolean)->Unit) {
    LocalConfiguration.current // Recompose when the system configuration changes.
    val locale=Locale.getDefault(Locale.Category.FORMAT)
    var localeTag by rememberSaveable {mutableStateOf(locale.toLanguageTag())}
    var mode by rememberSaveable {mutableStateOf(when {GenealogyDates.yearOnly(initial)!=null -> "year";GenealogyDates.canonical(initial) -> "date";else -> "legacy"})}
    var text by rememberSaveable {mutableStateOf(GenealogyDates.format(initial,locale))}
    val parsed=if(mode=="legacy")initial else GenealogyDates.parse(text,mode,locale)
    LaunchedEffect(locale.toLanguageTag()) {
        if(localeTag!=locale.toLanguageTag()) {
            val old=GenealogyDates.parse(text,mode,Locale.forLanguageTag(localeTag))
            if(old!=null)text=GenealogyDates.format(old,locale)
            localeTag=locale.toLanguageTag()
        }
    }
    LaunchedEffect(mode,text,locale.toLanguageTag()) {onChange(parsed ?: text,parsed!=null)}
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("date" to c.t("Celé datum","Full date"),"year" to c.t("Pouze rok","Year only")).forEach {(value,title) ->
                FilterChip(selected=mode==value,onClick={
                    val old=if(mode=="legacy")null else GenealogyDates.parse(text,mode,locale)
                    mode=value
                    text=if(value=="year")old?.let {GenealogyDates.year(it)?.toString()?.padStart(4,'0')}.orEmpty()
                        else if(old!=null && GenealogyDates.full(old)!=null)GenealogyDates.format(old,locale) else ""
                    onChange(GenealogyDates.parse(text,mode,locale) ?: text,true)
                },label={Text(title)},modifier=Modifier.semantics {contentDescription="$label · $title"})
            }
        }
        OutlinedTextField(text,{input ->text=input;val next=GenealogyDates.parse(input,mode,locale);onChange(next ?: input,next!=null)},
            modifier=Modifier.fillMaxWidth().onFocusChanged {focus -> if(!focus.isFocused && parsed!=null && mode=="date")text=GenealogyDates.format(parsed,locale)},
            label={Text(label)},singleLine=true,readOnly=mode=="legacy",isError=parsed==null,
            placeholder={Text(if(mode=="year")"1820" else GenealogyDates.format("1820-12-31",locale))},
            supportingText={Text(when {mode=="legacy" -> c.t("Starší zápis zůstane zachován. Pro změnu vyberte přesnost.","The legacy entry is preserved. Choose precision to replace it.")
                parsed==null -> c.t("Neplatné datum. Příklad: ","Invalid date. Example: ")+(if(mode=="year")"1820" else GenealogyDates.format("1820-12-31",locale))
                else -> c.t("Prázdné = neznámé","Empty = unknown")})})
    }
}
