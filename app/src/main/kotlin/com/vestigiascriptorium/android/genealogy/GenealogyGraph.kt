package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import com.vestigiascriptorium.android.ui.main.Copy
import kotlin.math.roundToInt

internal fun eventLabel(kind: String,c: Copy)=when(kind) {
    "genealogy.parentage" -> c.t("Rodičovství","Parentage")
    "genealogy.spousal-record" -> c.t("Partnerský vztah","Partnership")
    "genealogy.birth" -> c.t("Narození","Birth")
    "genealogy.baptism" -> c.t("Křest","Baptism")
    "genealogy.marriage" -> c.t("Sňatek","Marriage")
    "genealogy.death" -> c.t("Úmrtí","Death")
    "genealogy.burial" -> c.t("Pohřeb","Burial")
    else -> c.t("Zmínka","Mention")
}
internal fun roleLabel(role: String,c: Copy)=when(role) {
    "genealogy.father" -> c.t("Otec","Father")
    "genealogy.mother" -> c.t("Matka","Mother")
    "subject2" -> c.t("Druhá osoba","Second person")
    else -> c.t("Hlavní osoba","Main person")
}
internal fun eventRoleLabel(kind: String,role: String,c: Copy)=if(role=="subject") when(kind) {
    "genealogy.parentage" -> c.t("Dítě","Child")
    "genealogy.birth","genealogy.baptism" -> c.t("Dítě","Child")
    "genealogy.spousal-record","genealogy.marriage" -> c.t("Partner","Partner")
    else -> roleLabel(role,c)
} else if(role=="subject2") c.t("Partner","Partner") else roleLabel(role,c)
internal fun lifeDates(tree: FamilyTree,id: String)=listOf("genealogy.birth" to "*", "genealogy.death" to "†").mapNotNull { (kind,mark) -> tree.events.firstOrNull {it.kind==kind && it.roles["subject"]==id && it.date.isNotBlank()}?.let {"$mark ${GenealogyDates.format(it.date)}"} }.joinToString(" · ")

@Composable
internal fun GenealogyGraph(tree: FamilyTree,view: GraphView,copy: Copy,onView: (GraphView)->Unit,onSelect: (String)->Unit,onMove: (String,Float,Float)->Unit,modifier: Modifier=Modifier,enabled: Boolean=true) {
    val density=LocalDensity.current.density
    var size by remember {mutableStateOf(IntSize.Zero)}
    val latest by rememberUpdatedState(view)
    val ink=MaterialTheme.colorScheme.outline
    val paper=MaterialTheme.colorScheme.background
    val textMeasurer=rememberTextMeasurer()
    var liveDrag by remember(tree.id) {mutableStateOf<Pair<String,Offset>?>(null)}
    LaunchedEffect(tree) {liveDrag=null}
    val points=remember(tree.people) {tree.people.associate {it.id to Offset(it.x,it.y)}}
    val locale=java.util.Locale.getDefault(java.util.Locale.Category.FORMAT)
    val dates=remember(tree.events,locale) {tree.people.associate {it.id to lifeDates(tree,it.id)}}
    fun delta(id: String)=liveDrag?.takeIf {it.first==id}?.second ?: Offset.Zero
    Box(modifier.onSizeChanged {size=it}.pointerInput(tree.id) {
        detectTransformGestures { centroid,pan,zoom,_ ->
            val old=latest;val next=(old.zoom*zoom).coerceIn(.2f,3f)
            val focus=centroid/density
            val origin=Offset(old.x,old.y)
            val moved=focus-(focus-origin)*(next/old.zoom)+pan/density
            onView(old.copy(x=moved.x,y=moved.y,zoom=next))
        }
    }.semantics {contentDescription=copy.t("Graf vazeb. Posun prstem, přiblížení dvěma prsty.","Connections graph. Drag to pan, pinch to zoom.")}) {
        Canvas(Modifier.fillMaxSize()) {
            tree.events.forEach {event ->
                val shift=delta(event.id)
                val start=Offset((event.x+shift.x+98)*view.zoom+view.x,(event.y+shift.y+44)*view.zoom+view.y)*density
                event.roles.forEach {(role,id) -> points[id]?.let {p ->
                    val d=delta(id)
                    val end=Offset((p.x+d.x+98)*view.zoom+view.x,(p.y+d.y+44)*view.zoom+view.y)*density
                    drawLine(ink,start,end,2.dp.toPx())
                    if(view.zoom>=.5f) {
                        val vector=end-start
                        val tx=if(vector.x==0f)Float.POSITIVE_INFINITY else 98*view.zoom*density/kotlin.math.abs(vector.x)
                        val ty=if(vector.y==0f)Float.POSITIVE_INFINITY else 44*view.zoom*density/kotlin.math.abs(vector.y)
                        val fraction=minOf(tx,ty,.5f)
                        val middle=(start+vector*fraction+end-vector*fraction)/2f
                        val label=textMeasurer.measure(eventRoleLabel(event.kind,role,copy),TextStyle(color=ink,fontSize=11.sp))
                        val origin=middle-Offset(label.size.width/2f,label.size.height/2f)
                        drawRect(paper,origin-Offset(3.dp.toPx(),1.dp.toPx()),androidx.compose.ui.geometry.Size(label.size.width+6.dp.toPx(),label.size.height+2.dp.toPx()))
                        drawText(label,topLeft=origin)
                    }
                } }
            }
        }
        fun visible(x: Float,y: Float)=((x+196)*view.zoom+view.x)>0 && ((y+88)*view.zoom+view.y)>0 && x*view.zoom+view.x<size.width/density && y*view.zoom+view.y<size.height/density
        @Composable fun Node(id: String,x: Float,y: Float,title: String,subtitle: String,event: Boolean) {
            var drag by remember(id,x,y) {mutableStateOf(Offset.Zero)}
            val currentMove by rememberUpdatedState(onMove)
            Card(enabled=enabled,onClick={onSelect(id)},modifier=Modifier.offset {IntOffset(((x+drag.x)*view.zoom*density+view.x*density).roundToInt(),((y+drag.y)*view.zoom*density+view.y*density).roundToInt())}
                .size((196*view.zoom).dp,(88*view.zoom).dp)
                .pointerInput(id,x,y,enabled) {if(enabled)detectDragGesturesAfterLongPress(onDragStart={drag=Offset.Zero},onDragCancel={drag=Offset.Zero;liveDrag=null},onDragEnd={currentMove(id,x+drag.x,y+drag.y)}) {change,amount -> change.consume();drag+=amount/(density*latest.zoom);liveDrag=id to drag}},
                shape=RoundedCornerShape((12*view.zoom).dp),border=if(view.selected==id) androidx.compose.foundation.BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null,
                colors=CardDefaults.cardColors(containerColor=if(event)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding((10*view.zoom).dp)) {
                    Text(title,fontSize=(14*view.zoom).sp,lineHeight=(17*view.zoom).sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(subtitle.isNotBlank()) Text(subtitle,fontSize=(11*view.zoom).sp,lineHeight=(14*view.zoom).sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        tree.events.filter {visible(it.x,it.y)}.forEach {e -> key(e.id) {Node(e.id,e.x,e.y,e.title.ifBlank {eventLabel(e.kind,copy)},listOf(GenealogyDates.format(e.date),e.place).filter {it.isNotBlank()}.joinToString(" · "),true)} }
        tree.people.filter {visible(it.x,it.y)}.forEach {p -> key(p.id) {Node(p.id,p.x,p.y,p.name,dates[p.id].orEmpty(),false)} }
    }
}
