package com.vestigiascriptorium.android.aids.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.main.aids
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import org.json.JSONArray

@Preview(name="Text research aid",showBackground=true,device="spec:width=412dp,height=915dp,dpi=420",locale="cs")
@Composable
fun TextReaderPreview() {
    val page=RecordPage(listOf(AidRecord("1","1 loket","Délková míra"),AidRecord("2","1 stopa","Délková míra"),AidRecord("3","1 sáh","Délková míra")),819,0,listOf(FacetOption("CZ","Česko"),FacetOption("AT","Rakousko")),"Země",AidViewKind.TEXT)
    VestigiaScriptoriumTheme { AidReaderScreen(aids.first { it.id=="historical-units" },Copy(true),AidReaderSource {null},{},page) }
}

@Preview(name="Image research aid",showBackground=true,device="spec:width=412dp,height=915dp,dpi=420",locale="cs")
@Composable
fun ImageReaderPreview() {
    val assets=LocalContext.current.assets
    val data=JSONArray(assets.open("reader-preview.json").bufferedReader().use {it.readText()})
    val records=(0 until data.length()).map { index ->
        val item=data.getJSONObject(index)
        val name=item.getString("title")
        AidRecord(item.getString("id"),name,"",images=listOf(RecordImage(name,assets.open(item.getString("image")).use {it.readBytes()},online=false)))
    }
    VestigiaScriptoriumTheme { AidReaderScreen(aids.first {it.id=="heraldry-templates"},Copy(true),AidReaderSource {null},{},RecordPage(records,99,0,listOf(FacetOption("shield","Štít")),"Druh šablony",AidViewKind.IMAGE)) }
}
