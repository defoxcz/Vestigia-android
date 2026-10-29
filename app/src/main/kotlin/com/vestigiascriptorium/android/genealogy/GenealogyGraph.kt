package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
    "genealogy.parentage","genealogy.birth","genealogy.baptism" -> c.t("Dítě","Child")
    "genealogy.spousal-record","genealogy.marriage" -> c.t("Partner","Partner")
    else -> roleLabel(role,c)
} else if(role=="subject2") c.t("Partner","Partner") else roleLabel(role,c)
internal fun lifeDates(tree: FamilyTree,id: String)=listOf("genealogy.birth" to "*", "genealogy.death" to "†").mapNotNull { (kind,mark) -> tree.events.firstOrNull {it.kind==kind && it.roles["subject"]==id && it.date.isNotBlank()}?.let {"$mark ${GenealogyDates.format(it.date)}"} }.joinToString(" · ")
private data class BoxLine(val text: String,val title: Boolean=false,val limit: Int=Int.MAX_VALUE)

@Composable
internal fun GenealogyGraph(tree: FamilyTree,view: GraphView,copy: Copy,onView: (GraphView)->Unit,onSelect: (String)->Unit,onMove: (String,Float,Float)->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,onBounds: (Map<String,GraphBox>)->Unit={},media: GenealogyMedia?=null) {
    val density=LocalDensity.current
    val latest by rememberUpdatedState(view)
    val latestSelect by rememberUpdatedState(onSelect)
    val ink=MaterialTheme.colorScheme.outline
    val primary=MaterialTheme.colorScheme.primary
    val paper=MaterialTheme.colorScheme.background
    val textMeasurer=rememberTextMeasurer()
    var liveDrag by remember(tree.id) {mutableStateOf<Pair<String,Offset>?>(null)}
    LaunchedEffect(tree) {liveDrag=null}
    val locale=java.util.Locale.getDefault(java.util.Locale.Category.FORMAT)
    val presentation=remember(tree) {GenealogyPresentation(tree)}
    val people=remember(tree.people) {tree.people.associateBy {it.id}}
    val lines=remember(presentation,locale,copy.cs) {buildMap<String,List<BoxLine>> {
        tree.people.forEach {p ->
            val data=presentation.person(p.id,copy.cs)
            put(p.id,buildList {
                add(BoxLine(p.name,true,2))
                if(data.years.isNotBlank())add(BoxLine(data.years))
                data.partners.forEachIndexed {index,partner ->
                    val label=if(index==0)copy.t("Partner","Partner") else copy.t("${index+1}. partner","Partner ${index+1}")
                    add(BoxLine("$label: ${partner.person.name}"))
                    val dates=partner.connection.weddingDates.map {GenealogyDates.format(it,locale)}
                    add(BoxLine(copy.t("Svatba: ","Wedding: ")+dates.joinToString(" · ").ifBlank {"—"}))
                }
            })
        }
        presentation.visibleEvents.forEach {e -> put(e.id,buildList {
            add(BoxLine(e.title.ifBlank {eventLabel(e.kind,copy)},true,2))
            val text=listOf(GenealogyDates.format(e.date,locale),e.place).filter {it.isNotBlank()}.joinToString(" · ")
            if(text.isNotBlank())add(BoxLine(text,limit=2))
        })}
    }}
    var measuredSizes by remember(tree.id) {mutableStateOf<Map<String,IntSize>>(emptyMap())}
    val positions=remember(presentation) {buildMap {
        tree.people.forEach {put(it.id,GraphBox(it.x,it.y,244f,88f))}
        presentation.visibleEvents.forEach {put(it.id,GraphBox(it.x,it.y,196f,88f))}
    }}
    val boxes=remember(positions,measuredSizes,density,view.zoom) {positions.mapValues {(id,box) ->
        measuredSizes[id]?.let {box.copy(height=it.height/(density.density*view.zoom))} ?: box
    }}
    // Measure cards with a fixed world width and unbounded height. Their actual content
    // supplies the geometry, including system font metrics and all partner rows.
    val measureCards=remember(positions,density,view.zoom) {MeasurePolicy {measurables,constraints ->
        val cards=measurables.map {measurable ->
            val id=measurable.layoutId as String
            val box=positions.getValue(id)
            val width=(box.width*view.zoom*density.density).roundToInt().coerceAtLeast(1)
            id to measurable.measure(Constraints(minWidth=width,maxWidth=width,minHeight=(88*view.zoom*density.density).roundToInt().coerceAtLeast(1)))
        }
        layout(constraints.maxWidth,constraints.maxHeight) {
            val camera=latest
            cards.forEach {(id,card) ->
                val box=positions.getValue(id)
                val drag=liveDrag?.takeIf {it.first==id}?.second ?: Offset.Zero
                card.place(((box.x+drag.x)*camera.zoom*density.density+camera.x*density.density).roundToInt(),((box.y+drag.y)*camera.zoom*density.density+camera.y*density.density).roundToInt())
            }
        }
    }}
    LaunchedEffect(boxes) {onBounds(boxes)}
    fun liveBox(id: String): GraphBox {val b=boxes.getValue(id);val delta=liveDrag?.takeIf {it.first==id}?.second ?: Offset.Zero;return b.copy(x=b.x+delta.x,y=b.y+delta.y)}
    fun screen(point: GraphPoint)=Offset((point.x*view.zoom+view.x)*density.density,(point.y*view.zoom+view.y)*density.density)
    val currentBoxes by rememberUpdatedState(boxes)
    val currentConnections by rememberUpdatedState(presentation.connections)
    Box(modifier.clipToBounds()
        .pointerInput(tree.id,density.density) {detectTransformGestures {centroid,pan,zoom,_ ->
            val old=latest;val next=(old.zoom*zoom).coerceIn(.2f,3f);val focus=centroid/density.density
            val moved=focus-(focus-Offset(old.x,old.y))*(next/old.zoom)+pan/density.density
            onView(old.copy(x=moved.x,y=moved.y,zoom=next))
        }}
        .pointerInput(tree.id,enabled) {if(enabled)detectTapGestures {point ->
            val v=latest;val world=GraphPoint((point.x/density.density-v.x)/v.zoom,(point.y/density.density-v.y)/v.zoom)
            currentConnections.mapNotNull {connection ->
                val a=currentBoxes[connection.first];val b=currentBoxes[connection.second]
                if(a==null || b==null)null else {val ends=connectionEnds(a,b);connection to segmentDistance(world,ends.first,ends.second)}
            }.minByOrNull {it.second}?.takeIf {it.second<=12/v.zoom}?.let {latestSelect(it.first.editId)}
        }}
        .semantics {contentDescription=copy.t("Graf vazeb. Posun prstem, přiblížení dvěma prsty.","Connections graph. Drag to pan, pinch to zoom.")}) {
        Canvas(Modifier.fillMaxSize()) {
            presentation.visibleEvents.forEach {event -> event.roles.forEach {(role,id) -> if(id in boxes) {
                val ends=connectionEnds(liveBox(event.id),liveBox(id));val start=screen(ends.first);val end=screen(ends.second)
                drawLine(ink,start,end,2.dp.toPx())
                if(view.zoom>=.5f) {
                    val label=textMeasurer.measure(eventRoleLabel(event.kind,role,copy),TextStyle(color=ink,fontSize=11.sp))
                    val origin=(start+end)/2f-Offset(label.size.width/2f,label.size.height/2f)
                    drawRect(paper,origin-Offset(3.dp.toPx(),1.dp.toPx()),androidx.compose.ui.geometry.Size(label.size.width+6.dp.toPx(),label.size.height+2.dp.toPx()))
                    drawText(label,topLeft=origin)
                }
            }}}
            presentation.connections.forEach {connection ->
                val ends=connectionEnds(liveBox(connection.first),liveBox(connection.second))
                drawLine(if(connection.events.any {it.id==view.selected})primary else ink,screen(ends.first),screen(ends.second),if(connection.events.any {it.id==view.selected})3.dp.toPx() else 2.dp.toPx())
            }
        }
        // Transparent accessible targets supplement touch hit testing along the whole line.
        presentation.connections.forEach {connection ->
            val ends=connectionEnds(liveBox(connection.first),liveBox(connection.second));val middle=screen((ends.first+ends.second)*.5f)
            Box(Modifier.offset {IntOffset((middle.x-24.dp.toPx()).roundToInt(),(middle.y-24.dp.toPx()).roundToInt())}.size(48.dp)
                .testTag("partner-link-${connection.editId}").clickable(enabled=enabled,role=Role.Button) {onSelect(connection.editId)}
                .semantics {contentDescription=copy.t("Upravit vztah: ","Edit relationship: ")+people.getValue(connection.first).name+" – "+people.getValue(connection.second).name})
        }
        Layout(modifier=Modifier.matchParentSize(),measurePolicy=measureCards,content={
        positions.forEach {(id,box) -> key(id) {
            var drag by remember(id,box.x,box.y) {mutableStateOf(Offset.Zero)}
            Card(enabled=enabled,onClick={onSelect(id)},modifier=Modifier.layoutId(id).testTag("genealogy-node-$id")
                .onSizeChanged {actual ->if(measuredSizes[id]!=actual)measuredSizes=measuredSizes+(id to actual)}
                .pointerInput(id,box.x,box.y,enabled) {if(enabled)detectDragGesturesAfterLongPress(onDragStart={drag=Offset.Zero},onDragCancel={drag=Offset.Zero;liveDrag=null},onDragEnd={onMove(id,box.x+drag.x,box.y+drag.y)}) {change,amount ->change.consume();drag+=amount/(density.density*latest.zoom);liveDrag=id to drag}},
                shape=RoundedCornerShape((12*view.zoom).dp),border=if(view.selected==id)androidx.compose.foundation.BorderStroke(2.dp,primary) else null,
                colors=CardDefaults.cardColors(containerColor=if(id in people)MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding((10*view.zoom).dp),verticalArrangement=Arrangement.spacedBy((5*view.zoom).dp)) {
                    people[id]?.portrait?.takeIf {it.isNotBlank()}?.let {PersonPortrait(it,media,copy,Modifier.fillMaxWidth().height((112*view.zoom).dp))}
                    lines.getValue(id).forEach {line ->Text(line.text,letterSpacing=0.sp,fontSize=((if(line.title)14 else 11)*view.zoom).sp,lineHeight=((if(line.title)17 else 14)*view.zoom).sp,maxLines=line.limit,overflow=TextOverflow.Ellipsis,color=if(line.title)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
        }}
        })
    }
}
