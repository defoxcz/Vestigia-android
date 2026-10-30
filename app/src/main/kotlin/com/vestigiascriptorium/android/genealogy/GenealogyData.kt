package com.vestigiascriptorium.android.genealogy

import java.util.UUID

internal fun newId()=UUID.randomUUID().toString()
internal data class Person(val id: String=newId(),val givenName: String="",val gender: String="unknown",val note: String="",val x: Float=0f,val y: Float=0f,
    val middleName: String="",val surname: String="",val mentionedFrom: String="",val mentionedTo: String="",val legacyName: String="",val portrait: String="") {
    val name: String get()=listOf(givenName,middleName,surname).filter {it.isNotBlank()}.joinToString(" ").ifBlank {legacyName}
}

internal data class LifeEvent(val id: String=newId(),val kind: String="genealogy.mentioned",val title: String="",val date: String="",val place: String="",val note: String="",val roles: Map<String,String> = emptyMap(),val x: Float=0f,val y: Float=0f)
internal data class Evidence(val id: String=newId(),val person: String,val caption: String,val note: String="",val file: String="",val mime: String="application/octet-stream")
internal data class FamilyTree(val id: String=newId(),val title: String,val people: List<Person> = emptyList(),val events: List<LifeEvent> = emptyList(),val evidence: List<Evidence> = emptyList(),val importSource: String="")
internal data class GraphView(val x: Float=30f,val y: Float=40f,val zoom: Float=1f,val selected: String?=null)
internal enum class Relative { FATHER,MOTHER,SPOUSE,CHILD_AS_FATHER,CHILD_AS_MOTHER }
internal class GenealogyProblem(val code: String): IllegalArgumentException(code)

/** Immutable commands. Event roles carry meaning; geometry never establishes a relationship. */
internal object GenealogyEdits {
    fun validate(tree: FamilyTree) {
        fun check(ok: Boolean,code: String) { if(!ok) throw GenealogyProblem(code) }
        check(tree.title.isNotBlank(),"name")
        val ids=tree.people.map {it.id}+tree.events.map {it.id}+tree.evidence.map {it.id}
        check(ids.toSet().size==ids.size && ids.all {it.isNotBlank()},"invalid")
        val people=tree.people.map {it.id}.toSet()
        check(tree.people.all {it.name.isNotBlank() && it.x.isFinite() && it.y.isFinite()},"name")
        check(tree.people.all {GenealogyDates.canonical(it.mentionedFrom) && GenealogyDates.canonical(it.mentionedTo)},"date")
        check(tree.people.all {GenealogyDates.ordered(it.mentionedFrom,it.mentionedTo)},"dateRange")
        check(tree.people.all {it.portrait.isEmpty() || it.portrait.matches(Regex("[a-f0-9]{64}\\.img"))},"image")
        val parents=mutableMapOf<String,MutableSet<String>>()
        val parentSlots=mutableMapOf<Pair<String,String>,String>()
        tree.events.forEach { event ->
            check(event.kind in kinds && event.x.isFinite() && event.y.isFinite(),"invalid")
            check(event.roles.values.all {it in people},"missing")
            check(event.roles.values.toSet().size==event.roles.size,"self")
            check(event.roles.keys.all {it in rolesFor(event.kind)},"invalid")
            val child=event.roles["subject"]
            if(event.kind in listOf("genealogy.birth","genealogy.parentage") && child!=null) {
                listOf("genealogy.father","genealogy.mother").forEach { role -> event.roles[role]?.let { parent ->
                    val opposite=if(role=="genealogy.father")"genealogy.mother" else "genealogy.father"
                    check(parentSlots[child to opposite]!=parent,"self")
                    val old=parentSlots.put(child to role,parent)
                    check(old==null || old==parent,"occupied")
                    parents.getOrPut(child) { mutableSetOf() }.add(parent)
                } }
            }
        }
        // Kahn's algorithm detects ancestry cycles without recursion on large families.
        val counts=people.associateWith {0}.toMutableMap()
        parents.values.forEach { set -> set.forEach {counts[it]=counts.getValue(it)+1} }
        val queue=ArrayDeque(counts.filterValues {it==0}.keys)
        var visited=0
        while(queue.isNotEmpty()) { val id=queue.removeFirst();visited++
            parents[id].orEmpty().forEach { parent -> counts[parent]=counts.getValue(parent)-1;if(counts[parent]==0) queue.add(parent) }
        }
        check(visited==people.size,"cycle")
        check(tree.evidence.all {it.person in people && it.caption.isNotBlank() && (it.file.isEmpty() || (it.file.startsWith("content://") || it.file.matches(Regex("managed:[a-f0-9]{64}\\.(img|bin)"))))},"invalid")
    }
    val kinds=listOf("genealogy.parentage","genealogy.spousal-record","genealogy.birth","genealogy.baptism","genealogy.marriage","genealogy.death","genealogy.burial","genealogy.mentioned")
    fun rolesFor(kind: String)=when(kind) {
        "genealogy.parentage","genealogy.birth","genealogy.baptism" -> listOf("subject","genealogy.father","genealogy.mother")
        "genealogy.spousal-record","genealogy.marriage" -> listOf("subject","subject2")
        else -> listOf("subject")
    }
    fun freePosition(tree: FamilyTree,x: Float,y: Float,width: Float=244f,height: Float=88f): Pair<Float,Float> {
        val presentation=GenealogyPresentation(tree)
        val occupied=tree.people.map {GraphBox(it.x,it.y,244f,88f+presentation.person(it.id).partners.size*54f)}
        for(i in 0..10000) {
            val px=x+(i%5)*300f;val py=y+(i/5)*180f
            if(occupied.none {px+width+24>it.x && px<it.x+it.width+24 && py+height+24>it.y && py<it.y+it.height+24}) return px to py
        }
        throw GenealogyProblem("invalid")
    }
    fun person(tree: FamilyTree,value: Person,birth: String,death: String): FamilyTree {
        val previous=tree.people.firstOrNull {it.id==value.id}
        val oldBirth=tree.events.firstOrNull {it.kind=="genealogy.birth" && it.roles["subject"]==value.id}?.date.orEmpty()
        val oldDeath=tree.events.firstOrNull {it.kind=="genealogy.death" && it.roles["subject"]==value.id}?.date.orEmpty()
        listOf(birth to oldBirth,death to oldDeath,value.mentionedFrom to previous?.mentionedFrom.orEmpty(),value.mentionedTo to previous?.mentionedTo.orEmpty()).forEach {(date,old) ->
            if(date!=old && !GenealogyDates.canonical(date))throw GenealogyProblem("date")
        }
        if(!GenealogyDates.ordered(value.mentionedFrom,value.mentionedTo))throw GenealogyProblem("dateRange")
        val position=if(tree.people.any {it.id==value.id}) value.x to value.y else freePosition(tree,value.x,value.y)
        var next=tree.copy(people=tree.people.filterNot {it.id==value.id}+value.copy(givenName=value.givenName.trim(),middleName=value.middleName.trim(),surname=value.surname.trim(),legacyName=if(listOf(value.givenName,value.middleName,value.surname).any {it.isNotBlank()})"" else value.legacyName,x=position.first,y=position.second))
        listOf("genealogy.birth" to birth,"genealogy.death" to death).forEach { (kind,date) ->
            val old=next.events.firstOrNull {it.kind==kind && it.roles["subject"]==value.id}
            if(old!=null) next=next.copy(events=next.events.map {if(it.id==old.id) it.copy(date=date.trim()) else it})
            else if(date.isNotBlank()) {val p=freePosition(next,position.first+260,position.second+if(kind.endsWith("birth")) -100 else 100,width=196f);next=next.copy(events=next.events+LifeEvent(kind=kind,date=date.trim(),roles=mapOf("subject" to value.id),x=p.first,y=p.second))}
        }
        validate(next);return next
    }
    fun relate(tree: FamilyTree,selected: String,other: String,kind: Relative): FamilyTree {
        if(selected==other) throw GenealogyProblem("self")
        if(tree.people.none {it.id==selected} || tree.people.none {it.id==other}) throw GenealogyProblem("missing")
        val p=tree.people.first {it.id==selected};val q=tree.people.first {it.id==other}
        val spouse=kind==Relative.SPOUSE
        val child=if(kind in listOf(Relative.CHILD_AS_FATHER,Relative.CHILD_AS_MOTHER)) other else selected
        val parent=if(child==other) selected else other
        val role=if(kind in listOf(Relative.MOTHER,Relative.CHILD_AS_MOTHER)) "genealogy.mother" else "genealogy.father"
        val old=if(spouse) tree.events.firstOrNull {it.kind in listOf("genealogy.spousal-record","genealogy.marriage") && it.roles.values.toSet()==setOf(selected,other)}
            else tree.events.firstOrNull {it.kind in listOf("genealogy.parentage","genealogy.birth") && it.roles["subject"]==child}
        if(spouse && old!=null || !spouse && old?.roles?.get(role)==parent) throw GenealogyProblem("duplicate")
        if(!spouse && old?.roles?.get(role)!=null) throw GenealogyProblem("occupied")
        val event=if(spouse) LifeEvent(kind="genealogy.spousal-record",roles=mapOf("subject" to selected,"subject2" to other),x=(p.x+q.x)/2,y=(p.y+q.y)/2+130)
            else (old ?: LifeEvent(kind="genealogy.parentage",roles=mapOf("subject" to child),x=(p.x+q.x)/2+160,y=(p.y+q.y)/2)).let {it.copy(roles=it.roles+(role to parent))}
        val position=if(old==null)freePosition(tree,event.x,event.y) else event.x to event.y
        return tree.copy(events=tree.events.filterNot {it.id==event.id}+event.copy(x=position.first,y=position.second)).also(::validate)
    }
    fun event(tree: FamilyTree,value: LifeEvent): FamilyTree {
        val old=tree.events.first {it.id==value.id}
        if(value.date!=old.date && !GenealogyDates.canonical(value.date))throw GenealogyProblem("date")
        return tree.copy(events=tree.events.map {if(it.id==value.id)value else it}).also(::validate)
    }
    fun move(tree: FamilyTree,id: String,x: Float,y: Float)=tree.copy(people=tree.people.map {if(it.id==id)it.copy(x=x,y=y) else it},events=tree.events.map {if(it.id==id)it.copy(x=x,y=y) else it}).also(::validate)
}
