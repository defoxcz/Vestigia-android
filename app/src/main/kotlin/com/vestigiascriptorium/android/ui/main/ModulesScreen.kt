package com.vestigiascriptorium.android.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun ModulesScreen(copy: Copy,onGenealogy: () -> Unit,onModule: (String) -> Unit) {
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Eyebrow(copy.t("VAŠE BADATELSKÁ PRACOVNA","YOUR RESEARCH WORKSPACE")) }
        item { Heading(copy.t("Moduly","Modules"),copy.t("Vyberte obor a pokračujte ve svém bádání.","Choose a field and continue your research.")) }
        item {
            Card(onClick=onGenealogy,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Mark(3,Modifier.size(36.dp))

                    }
                    TreeSketch()
                    Text(copy.t("Genealogie","Genealogy"),style=MaterialTheme.typography.headlineMedium,fontFamily=FontFamily.Serif)
                    Text(copy.t("Lidé, rodinné vazby a příběhy doložené prameny.","People, family relationships and stories supported by sources."),style=MaterialTheme.typography.bodyLarge)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(copy.t("Prozkoumat modul","Explore module"),style=MaterialTheme.typography.labelLarge)
                        Mark(6)
                    }
                }
            }
        }

        listOf(
            copy.t("Rukopisy","Manuscripts") to copy.t("Prameny, přepisy a čtení historických textů.","Sources, transcriptions and reading historical texts."),
            copy.t("Heraldika","Heraldry") to copy.t("Erby, jejich popis a vzájemné souvislosti.","Coats of arms, their descriptions and connections."),
            copy.t("Sfragistika","Sigillography") to copy.t("Pečeti, jejich držitelé a vazby na listiny.","Seals, their owners and links to charters."),
            copy.t("Epigrafika","Epigraphy") to copy.t("Nápisy, jejich dokumentace a odborné edice.","Inscriptions, their documentation and scholarly editions."),
            copy.t("Paleografika","Palaeography") to copy.t("Historická písma, jejich tvary a souvislosti.","Historical scripts, letterforms and their context.")
        ).forEach { (title,description) -> item {
            OutlinedCard(onClick={onModule(title)},shape=RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(title,style=MaterialTheme.typography.titleLarge,fontFamily=FontFamily.Serif)
                    Text(description,color=MaterialTheme.colorScheme.onSurfaceVariant)

                }
            }
        }}
    }
}

@Composable
private fun TreeSketch() {
    val ink=MaterialTheme.colorScheme.primary.copy(alpha=0.45f)
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        val w=size.width;val h=size.height
        fun node(x: Float,y: Float) {
            drawRoundRect(ink,Offset(x-w*.075f,y),Size(w*.15f,h*.28f),androidx.compose.ui.geometry.CornerRadius(8f),style=Stroke(2f))
        }
        node(w*.28f,0f);node(w*.72f,0f);node(w*.5f,h*.7f)
        drawLine(ink,Offset(w*.28f,h*.28f),Offset(w*.28f,h*.49f),2f)
        drawLine(ink,Offset(w*.72f,h*.28f),Offset(w*.72f,h*.49f),2f)
        drawLine(ink,Offset(w*.28f,h*.49f),Offset(w*.72f,h*.49f),2f)
        drawLine(ink,Offset(w*.5f,h*.49f),Offset(w*.5f,h*.7f),2f)
    }
}

@Composable
internal fun GenealogyScreen(copy: Copy,onCreate: () -> Unit) {
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        item { Eyebrow(copy.t("MODUL 01 / GENEALOGIE","MODULE 01 / GENEALOGY")) }
        item { Heading(copy.t("Genealogie","Genealogy"),copy.t("Prostor pro rodokmeny, osoby a prameny, které je spojují.","A place for family trees, people and the sources connecting them.")) }
        item {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    TreeSketch()
                    Text(copy.t("Váš první rodokmen","Your first family tree"),style=MaterialTheme.typography.headlineSmall,fontFamily=FontFamily.Serif)
                    Text(copy.t("Začněte jedním člověkem. Postupně přidáte vztahy, životní události a doklady.","Start with one person. Add relationships, life events and evidence over time."))
                    Button(onClick=onCreate) { Text(copy.t("Nový rodokmen · návrh","New family tree · prototype")) }
                }
            }
        }
        item { SectionTitle(copy.t("Vše kolem jednoho výzkumu","One connected research project")) }
        item { Note(copy.t("Rodokmeny → Osoby → Detail osoby\n\nRodinné vazby, události, místa a citace pramenů patří k jednomu projektu. Diagram doplní přehledný seznam osob vhodný pro telefon.","Family trees → People → Person details\n\nRelationships, events, places and source citations belong to one project. A phone-friendly people list will complement the tree diagram.")) }
        item { Pill(copy.t("Návrh vstupu do modulu · editor bude následovat","Module entry concept · editor comes next")) }
    }
}
