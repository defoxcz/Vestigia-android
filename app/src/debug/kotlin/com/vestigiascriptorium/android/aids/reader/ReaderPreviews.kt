package com.vestigiascriptorium.android.aids.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.main.aids
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme

@Preview(name="Text research aid",showBackground=true,device="spec:width=412dp,height=915dp,dpi=420",locale="cs")
@Composable
fun TextReaderPreview() {
    val page=RecordPage(listOf(AidRecord("1","1 loket","Délková míra"),AidRecord("2","1 stopa","Délková míra"),AidRecord("3","1 sáh","Délková míra")),819,0,listOf(FacetOption("CZ","Česko"),FacetOption("AT","Rakousko")),"Země",AidViewKind.TEXT)
    VestigiaScriptoriumTheme { AidReaderScreen(aids.first { it.id=="historical-units" },Copy(true),AidReaderSource {null},{},page) }
}

@Preview(name="Image research aid",showBackground=true,device="spec:width=412dp,height=915dp,dpi=420",locale="cs")
@Composable
fun ImageReaderPreview() {
    val records=listOf(AidRecord("Q131226179","Jacobi",""),AidRecord("Q131621255","Pfister",""),AidRecord("Q131634701","Sättelin",""),AidRecord("Q132571619","Marquard",""))
    VestigiaScriptoriumTheme { AidReaderScreen(aids.first {it.id=="heraldry-burgher-patrician"},Copy(true),AidReaderSource {null},{},RecordPage(records,22,0,listOf(FacetOption("BE","Belgie")),"Země",AidViewKind.IMAGE)) }
}
