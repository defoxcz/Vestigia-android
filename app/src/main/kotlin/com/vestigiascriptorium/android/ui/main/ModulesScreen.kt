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

// Retain planned modules so each can be made visible when its editor is ready.
private data class AdditionalModule(val title: String,val description: String,val visible: Boolean=false)

@Composable
internal fun ModulesScreen(copy: Copy,onGenealogy: () -> Unit,onModule: (String) -> Unit) {
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Eyebrow(copy.t("VAŠE BADATELSKÁ PRACOVNA","YOUR RESEARCH WORKSPACE")) }
        item { Heading(copy.t("Moduly","Modules"),copy.t("Pokračujte ve svém genealogickém bádání.","Continue your genealogy research.")) }
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
            AdditionalModule(copy.t("Rukopisy","Manuscripts"),copy.t("Prameny, přepisy a čtení historických textů.","Sources, transcriptions and reading historical texts.")),
            AdditionalModule(copy.t("Heraldika","Heraldry"),copy.t("Erby, jejich popis a vzájemné souvislosti.","Coats of arms, their descriptions and connections.")),
            AdditionalModule(copy.t("Sfragistika","Sigillography"),copy.t("Pečeti, jejich držitelé a vazby na listiny.","Seals, their owners and links to charters.")),
            AdditionalModule(copy.t("Epigrafika","Epigraphy"),copy.t("Nápisy, jejich dokumentace a odborné edice.","Inscriptions, their documentation and scholarly editions.")),
            AdditionalModule(copy.t("Paleografika","Palaeography"),copy.t("Historická písma, jejich tvary a souvislosti.","Historical scripts, letterforms and their context."))
        ).filter {it.visible}.forEach { (title,description) -> item {
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
