package com.vestigiascriptorium.android.genealogy

/** Read-only projection: hidden relationship records remain the source of truth. */
internal data class PartnerConnection(val first: String,val second: String,val events: List<LifeEvent>) {
    val editId: String get()=events.firstOrNull {it.kind=="genealogy.marriage" && it.date.isNotBlank()}?.id
        ?: events.firstOrNull {it.kind=="genealogy.marriage"}?.id ?: events.first().id
    val weddingDates: List<String> get()=events.filter {it.kind=="genealogy.marriage"}.map {it.date}.filter {it.isNotBlank()}.distinct().sortedBy(::dateOrder)
}
internal data class ParentConnection(val parent: String,val child: String,val role: String)
internal data class FamilyConnection(val parents: List<String>,val children: List<String>)
internal data class PartnerEntry(val person: Person,val connection: PartnerConnection)
internal data class PersonBoxData(val years: String,val partners: List<PartnerEntry>)
internal data class GraphBox(val x: Float,val y: Float,val width: Float,val height: Float)
internal fun dateOrder(value: String): String = GenealogyDates.full(value)?.toString()
    ?: GenealogyDates.year(value)?.toString()?.padStart(4,'0')?.plus("-00-00") ?: "99999"
internal class GenealogyPresentation(val tree: FamilyTree) {
    private val people=tree.people.associateBy {it.id}
    val connections: List<PartnerConnection> = tree.events.filter {event ->
        event.kind in listOf("genealogy.spousal-record","genealogy.marriage") && event.roles.size==2 && event.roles.values.all {it in people}
    }.groupBy {it.roles.values.sorted()}.map {(ids,events) ->PartnerConnection(ids[0],ids[1],events)}
    private val order=tree.events.mapIndexed {index,event ->event.id to index}.toMap()
    private val connectionsByPerson=connections.flatMap {listOf(it.first to it,it.second to it)}.groupBy({it.first},{it.second})
    // Events remain editable through the person; only people have graph cards.
    val parents=tree.events.filter {it.kind in setOf("genealogy.birth","genealogy.baptism","genealogy.parentage")}.flatMap {event ->
        val child=event.roles["subject"]
        if(child !in people)emptyList() else listOf("genealogy.father","genealogy.mother").mapNotNull {role ->
            event.roles[role]?.takeIf {it in people}?.let {ParentConnection(it,child!!,role)}
        }
    }.distinct()
    val families=parents.groupBy {it.child}.map { (child,links) ->links.map {it.parent}.distinct().sorted() to child }
        .groupBy({it.first},{it.second}).map {(parents,children) ->FamilyConnection(parents,children.distinct())}
    private val lifeEvents=tree.events.filter {it.kind in listOf("genealogy.birth","genealogy.death")}.groupBy {it.roles["subject"]}
    fun person(id: String,cs: Boolean=true): PersonBoxData {
        val subject=people.getValue(id)
        fun year(kind: String)=lifeEvents[id].orEmpty().filter {it.kind==kind}.firstNotNullOfOrNull {GenealogyDates.year(it.date)}
        val birth=year("genealogy.birth");val death=year("genealogy.death")
        val dates=if(birth!=null || death!=null)listOfNotNull(birth?.let {"* $it"},death?.let {"+ $it"}).joinToString("  ") else {
            val from=GenealogyDates.year(subject.mentionedFrom);val to=GenealogyDates.year(subject.mentionedTo)
            when {from!=null && to!=null -> if(cs)"připomínán $from–$to" else "mentioned $from–$to"
                from!=null -> if(cs)"připomínán od $from" else "mentioned from $from"
                to!=null -> if(cs)"připomínán do $to" else "mentioned until $to"
                else -> ""}
        }
        val partners=connectionsByPerson[id].orEmpty()
            .sortedWith(compareBy<PartnerConnection> {it.weddingDates.firstOrNull()?.let(::dateOrder) ?: "99999"}.thenBy {connection ->order.getValue(connection.events.first().id)})
            .map {PartnerEntry(people.getValue(if(it.first==id)it.second else it.first),it)}
        return PersonBoxData(dates,partners)
    }
}

/** Shared line geometry for drawing, accessible target placement and touch hit testing. */
internal data class GraphPoint(val x: Float,val y: Float) {
    operator fun plus(other: GraphPoint)=GraphPoint(x+other.x,y+other.y)
    operator fun minus(other: GraphPoint)=GraphPoint(x-other.x,y-other.y)
    operator fun times(scale: Float)=GraphPoint(x*scale,y*scale)
}
internal fun GraphBox.center()=GraphPoint(x+width/2,y+height/2)
internal fun connectionEnds(a: GraphBox,b: GraphBox): Pair<GraphPoint,GraphPoint> {
    val from=a.center();val to=b.center();val vector=to-from
    fun fraction(box: GraphBox): Float {
        val x=if(vector.x==0f)Float.POSITIVE_INFINITY else box.width/2/kotlin.math.abs(vector.x)
        val y=if(vector.y==0f)Float.POSITIVE_INFINITY else box.height/2/kotlin.math.abs(vector.y)
        return minOf(x,y,.5f)
    }
    return (from+vector*fraction(a)) to (to-vector*fraction(b))
}
internal fun segmentDistance(point: GraphPoint,a: GraphPoint,b: GraphPoint): Float {
    val v=b-a;val length=v.x*v.x+v.y*v.y
    val t=if(length==0f)0f else (((point.x-a.x)*v.x+(point.y-a.y)*v.y)/length).coerceIn(0f,1f)
    val nearest=a+v*t
    return kotlin.math.hypot(point.x-nearest.x,point.y-nearest.y)
}

/** One parental junction and sibling bus for each explicitly recorded parent set. */
internal fun familySegments(family: FamilyConnection,boxes: Map<String,GraphBox>,includeParentLine: Boolean=true,parentRowBottom: Float?=null): List<Pair<GraphPoint,GraphPoint>> {
    val parents=family.parents.map {boxes.getValue(it)}
    val children=family.children.map {boxes.getValue(it)}
    if(parents.isEmpty() || children.isEmpty())return emptyList()
    val pair=if(parents.size==2)connectionEnds(parents[0],parents[1]) else null
    val origin=pair?.let {(it.first+it.second)*.5f} ?: parents.single().let {GraphPoint(it.x+it.width/2,it.y+it.height)}
    val below=children.minOf {it.y}>origin.y
    val targets=children.map {GraphPoint(it.x+it.width/2,if(below)it.y else it.y+it.height)}
    val busY=if(below)((parentRowBottom ?: parents.maxOf {it.y+it.height})+targets.minOf {it.y})/2 else origin.y+48f
    val junction=GraphPoint(origin.x,busY)
    return buildList {
        if(pair!=null && includeParentLine)add(pair)
        add(origin to junction)
        add(GraphPoint(minOf(origin.x,targets.minOf {it.x}),busY) to GraphPoint(maxOf(origin.x,targets.maxOf {it.x}),busY))
        targets.forEach {add(GraphPoint(it.x,busY) to it)}
    }
}
