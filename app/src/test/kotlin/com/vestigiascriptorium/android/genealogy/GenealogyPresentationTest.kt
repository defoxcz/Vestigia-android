package com.vestigiascriptorium.android.genealogy

import org.junit.Assert.*
import org.junit.Test

class GenealogyPresentationTest {
    private val a=Person(id="a",givenName="Jan",surname="Novák",mentionedFrom="1810-02-03",mentionedTo="1860")
    private val b=Person(id="b",givenName="Anna")
    private val c=Person(id="c",givenName="Marie")
    private val tree=FamilyTree(title="Rod",people=listOf(a,b,c))
    private fun event(kind: String,date: String="",other: String="b",id: String=newId())=LifeEvent(id=id,kind=kind,date=date,roles=mapOf("subject" to "a","subject2" to other))
    @Test fun spousesAreNotParentsOfEachOtherAndChildRolesHaveExplicitContext() {
        val spouses=GenealogyEdits.relate(tree,"a","b",Relative.SPOUSE)
        assertTrue(GenealogyPresentation(spouses).parents.isEmpty())
        val birth=LifeEvent(kind="genealogy.birth",roles=mapOf("subject" to "c","genealogy.father" to "a","genealogy.mother" to "b"))
        val family=spouses.copy(events=spouses.events+birth)
        val copy=com.vestigiascriptorium.android.ui.main.Copy(true)
        assertEquals("Matka dítěte Marie",personEventRoleLabel(family,birth,"genealogy.mother","a",copy))
        assertEquals("Otec dítěte Marie",personEventRoleLabel(family,birth,"genealogy.father","b",copy))
        assertEquals("Matka",personEventRoleLabel(family,birth,"genealogy.mother","c",copy))
        assertEquals("Partner",personEventRoleLabel(family,spouses.events.single(),"subject2","a",copy))
    }
    @Test fun boxesPreferLifeYearsAndOtherwiseUseMentionPeriod() {
        assertEquals("1810–1860",GenealogyPresentation(tree).person("a").years)
        val born=LifeEvent(kind="genealogy.birth",date="1820-02-03",roles=mapOf("subject" to "a"))
        val died=LifeEvent(kind="genealogy.death",date="1890",roles=mapOf("subject" to "a"))
        assertEquals("* 1820  + 1890",GenealogyPresentation(tree.copy(events=listOf(born,died))).person("a").years)
        assertEquals("+ 1890",GenealogyPresentation(tree.copy(events=listOf(died))).person("a").years)
        assertEquals("",GenealogyPresentation(tree).person("b").years)
        assertEquals("1810",GenealogyPresentation(tree.copy(people=listOf(a.copy(mentionedTo="")))).person("a").years)
    }
    @Test fun partnershipIsAConnectionWhileStoredRecordAndIncompleteEventsRemainAvailable() {
        val relation=event("genealogy.spousal-record",date="1840",id="rel")
        val incomplete=LifeEvent(kind="genealogy.spousal-record",roles=mapOf("subject" to "a"))
        val source=tree.copy(events=listOf(relation,incomplete))
        val view=GenealogyPresentation(source)
        assertEquals("rel",view.connections.single().editId)
        assertEquals(listOf(relation,incomplete),source.events)
        assertTrue(view.person("a").partners.single().connection.weddingDates.isEmpty())
        assertEquals("a",view.person("b").partners.single().person.id)
    }
    @Test fun partnersAreOrderedByWeddingsGroupedPerPersonAndKeepAllMarriageDates() {
        val late=event("genealogy.marriage","1880-03-04",other="c",id="late")
        val early=event("genealogy.marriage","1840",id="early")
        val record=event("genealogy.spousal-record","1839",id="record")
        val repeated=event("genealogy.marriage","1845-02-03",id="again")
        val view=GenealogyPresentation(tree.copy(events=listOf(late,record,early,repeated)))
        val rows=view.person("a").partners
        assertEquals(listOf("b","c"),rows.map {it.person.id})
        assertEquals(listOf("1840","1845-02-03"),rows.first().connection.weddingDates)
        assertEquals("early",rows.first().connection.editId)
        assertEquals(2,view.connections.size)
    }
    @Test fun parentalEventsProduceDirectDeduplicatedPersonConnections() {
        val roles=mapOf("subject" to "c","genealogy.father" to "a","genealogy.mother" to "b")
        val source=tree.copy(events=listOf(LifeEvent(kind="genealogy.birth",roles=roles),LifeEvent(kind="genealogy.baptism",roles=roles)))
        assertEquals(setOf(ParentConnection("a","c","genealogy.father"),ParentConnection("b","c","genealogy.mother")),GenealogyPresentation(source).parents.toSet())
        assertEquals(2,source.events.size)
    }
    @Test fun siblingsShareOneParentJunctionWhileHalfSiblingsRemainSeparate() {
        val d=Person(id="d",givenName="Další dítě")
        val e=Person(id="e",givenName="Polorodý sourozenec")
        val events=listOf(
            LifeEvent(kind="genealogy.birth",roles=mapOf("subject" to "c","genealogy.father" to "a","genealogy.mother" to "b")),
            LifeEvent(kind="genealogy.parentage",roles=mapOf("subject" to "d","genealogy.father" to "a","genealogy.mother" to "b")),
            LifeEvent(kind="genealogy.parentage",roles=mapOf("subject" to "e","genealogy.father" to "a")))
        val families=GenealogyPresentation(tree.copy(people=tree.people+listOf(d,e),events=events)).families
        assertEquals(2,families.size)
        val family=families.single {it.parents.size==2}
        assertEquals(listOf("c","d"),family.children)
        val boxes=mapOf("a" to GraphBox(0f,0f,200f,100f),"b" to GraphBox(400f,0f,200f,100f),"c" to GraphBox(0f,400f,200f,100f),"d" to GraphBox(400f,400f,200f,100f))
        val segments=familySegments(family,boxes)
        assertEquals(5,segments.size) // parent line, one trunk, one sibling bus, two branches
        assertEquals(GraphPoint(300f,50f),segments[1].first)
        assertEquals(setOf(GraphPoint(100f,400f),GraphPoint(500f,400f)),segments.takeLast(2).map {it.second}.toSet())
        assertEquals(4,familySegments(family,boxes,false).size)
    }
    @Test fun segmentAnchorsRespectDifferentBoxSizesAndTouchDistanceIsFinite() {
        val a=GraphBox(0f,0f,244f,210f);val b=GraphBox(400f,30f,244f,160f)
        val (from,to)=connectionEnds(a,b)
        assertEquals(244f,from.x,.01f);assertEquals(400f,to.x,.01f)
        val middle=(from+to)*.5f
        assertEquals(0f,segmentDistance(middle,from,to),.001f)
        assertTrue(segmentDistance(GraphPoint(0f,500f),from,to)>200)
        assertEquals(5f,segmentDistance(GraphPoint(3f,4f),GraphPoint(0f,0f),GraphPoint(0f,0f)),.001f)
    }
}
