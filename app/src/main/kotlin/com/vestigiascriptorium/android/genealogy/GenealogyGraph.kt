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
private data class BoxLine(val text: String,val title: Boolean=false,val limit: Int=Int.MAX_VALUE,val personId: String?=null)

@Composable
internal fun GenealogyGraph(tree: FamilyTree,view: GraphView,copy: Copy,onView: (GraphView)->Unit,onSelect: (String)->Unit,onMove: (String,Float,Float)->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,onBounds: (Map<String,GraphBox>)->Unit={},media: GenealogyMedia?=null,collapseWives: Boolean=false) {
    val density=LocalDensity.current
    val latest by rememberUpdatedState(view)
    val latestSelect by rememberUpdatedState(onSelect)
    val ink=MaterialTheme.colorScheme.outline
    val primary=MaterialTheme.colorScheme.primary
    var liveDrag by remember(tree.id) {mutableStateOf<Pair<String,Offset>?>(null)}
    LaunchedEffect(tree) {liveDrag=null}
    val locale=java.util.Locale.getDefault(java.util.Locale.Category.FORMAT)
    val presentation=remember(tree) {GenealogyPresentation(tree)}
    val household=remember(presentation,collapseWives) {HouseholdProjection(tree,presentation,collapseWives)}
    val visiblePeople=remember(household) {household.visible(tree.people)}
    val visibleConnections=remember(household) {presentation.connections.filter {it.first !in household.owners && it.second !in household.owners}}
    val people=remember(tree.people) {tree.people.associateBy {it.id}}
    val lines=remember(presentation,locale,copy.cs,household) {buildMap<String,List<BoxLine>> {
        tree.people.forEach {p ->
            val data=presentation.person(p.id,copy.cs)
            put(p.id,buildList {
                add(BoxLine(p.name,true,2))
                if(data.years.isNotBlank())add(BoxLine(data.years))
                data.partners.forEachIndexed {index,partner ->
                    val grouped=household.owners[partner.person.id]==p.id
                    val label=if(grouped) {if(index==0)copy.t("Manželka","Wife") else copy.t("${index+1}. manželka","Wife ${index+1}")} else if(index==0)copy.t("Partner","Partner") else copy.t("${index+1}. partner","Partner ${index+1}")
                    add(BoxLine("$label: ${partner.person.name}",personId=if(household.owners[partner.person.id]==p.id)partner.person.id else null))
                    if(household.owners[partner.person.id]==p.id)presentation.person(partner.person.id,copy.cs).years.takeIf {it.isNotBlank()}?.let {add(BoxLine(it))}
                    val dates=partner.connection.weddingDates.map {GenealogyDates.format(it,locale)}
                    add(BoxLine(copy.t("Svatba: ","Wedding: ")+dates.joinToString(" · ").ifBlank {"—"}))
                }
            })
        }

    }}
    var measuredSizes by remember(tree.id) {mutableStateOf<Map<String,IntSize>>(emptyMap())}
    val generationLayout=remember(presentation) {GenerationLayout(tree,presentation)}
    val positions=remember(generationLayout,visiblePeople,measuredSizes,density,view.zoom) {
        generationLayout.boxes(visiblePeople,measuredSizes.mapValues {(_,size) ->size.height/(density.density*view.zoom)})
    }
    val boxes=positions
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
    LaunchedEffect(boxes,household) {onBounds(household.aliases(boxes))}
    fun liveBox(id: String): GraphBox {val b=boxes.getValue(id);val delta=liveDrag?.takeIf {it.first==id}?.second ?: Offset.Zero;return b.copy(x=b.x+delta.x,y=b.y+delta.y)}
    fun screen(point: GraphPoint)=Offset((point.x*view.zoom+view.x)*density.density,(point.y*view.zoom+view.y)*density.density)
    val currentBoxes by rememberUpdatedState(boxes)
    val currentConnections by rememberUpdatedState(visibleConnections)
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
            val familyBoxes=boxes.mapValues {(id,_) ->liveBox(id)}
            val rowBottoms=familyBoxes.values.groupBy {it.y}.mapValues {(_,row) ->row.maxOf {it.y+it.height}}
            presentation.families.map(household::family).filter {it.children.isNotEmpty()}.distinct().forEach {family ->
                val partnerLine=visibleConnections.any {setOf(it.first,it.second)==family.parents.toSet()}
                familySegments(family,familyBoxes,!partnerLine,rowBottoms[familyBoxes.getValue(family.parents.first()).y]).forEach {(a,b) ->
                    drawLine(ink,screen(a),screen(b),2.dp.toPx())
                }
            }
            visibleConnections.forEach {connection ->
                val ends=connectionEnds(liveBox(connection.first),liveBox(connection.second))
                drawLine(if(connection.events.any {it.id==view.selected})primary else ink,screen(ends.first),screen(ends.second),if(connection.events.any {it.id==view.selected})3.dp.toPx() else 2.dp.toPx())
            }
        }
        // Transparent accessible targets supplement touch hit testing along the whole line.
        visibleConnections.forEach {connection ->
            val ends=connectionEnds(liveBox(connection.first),liveBox(connection.second));val middle=screen((ends.first+ends.second)*.5f)
            Box(Modifier.offset {IntOffset((middle.x-24.dp.toPx()).roundToInt(),(middle.y-24.dp.toPx()).roundToInt())}.size(48.dp)
                .testTag("partner-link-${connection.editId}").clickable(enabled=enabled,role=Role.Button) {onSelect(connection.editId)}
                .semantics {contentDescription=copy.t("Upravit vztah: ","Edit relationship: ")+people.getValue(connection.first).name+" – "+people.getValue(connection.second).name})
        }
        // Graph geometry scales with zoom. Material's 48dp layout padding would
        // otherwise put invisible margins between measured bounds and visible cards.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Layout(modifier=Modifier.matchParentSize(),measurePolicy=measureCards,content={
        positions.forEach {(id,box) -> key(id) {
            var drag by remember(id,box.x,box.y) {mutableStateOf(Offset.Zero)}
            Card(enabled=enabled,onClick={onSelect(id)},modifier=Modifier.layoutId(id).testTag("genealogy-node-$id")
                .onSizeChanged {actual ->if(measuredSizes[id]!=actual)measuredSizes=measuredSizes+(id to actual)}
                .pointerInput(id,box.x,box.y,enabled) {if(enabled)detectDragGesturesAfterLongPress(onDragStart={drag=Offset.Zero},onDragCancel={drag=Offset.Zero;liveDrag=null},onDragEnd={onMove(id,box.x+drag.x,people.getValue(id).y)}) {change,amount ->change.consume();drag+=Offset(amount.x,0f)/(density.density*latest.zoom);liveDrag=id to drag}},
                shape=RoundedCornerShape((12*view.zoom).dp),border=if(household.owner(view.selected.orEmpty())==id)androidx.compose.foundation.BorderStroke(2.dp,primary) else null,
                colors=CardDefaults.cardColors(containerColor=if(id in people)MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding((10*view.zoom).dp),verticalArrangement=Arrangement.spacedBy((5*view.zoom).dp)) {
                    people[id]?.portrait?.takeIf {it.isNotBlank()}?.let {PersonPortrait(it,media,copy,Modifier.fillMaxWidth().height((112*view.zoom).dp))}
                    lines.getValue(id).forEach {line ->Text(line.text,modifier=if(line.personId!=null)Modifier.clickable(enabled=enabled) {onSelect(line.personId)}.semantics {contentDescription=copy.t("Otevřít osobu: ","Open person: ")+people.getValue(line.personId).name} else Modifier,letterSpacing=0.sp,fontSize=((if(line.title)14 else 11)*view.zoom).sp,lineHeight=((if(line.title)17 else 14)*view.zoom).sp,maxLines=line.limit,overflow=TextOverflow.Ellipsis,textDecoration=if(line.personId!=null)androidx.compose.ui.text.style.TextDecoration.Underline else null,color=if(line.personId!=null)primary else if(line.title)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
        }}
        })
        }
    }
}
