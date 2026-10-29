package com.vestigiascriptorium.android.genealogy

import org.junit.Assert.*
import org.junit.Test

class GenealogyPresentationTest {
    private val a=Person(id="a",givenName="Jan",surname="Novák",mentionedFrom="1810-02-03",mentionedTo="1860")
    private val b=Person(id="b",givenName="Anna")
    private val c=Person(id="c",givenName="Marie")
    private val tree=FamilyTree(title="Rod",people=listOf(a,b,c))
    private fun event(kind: String,date: String="",other: String="b",id: String=newId())=LifeEvent(id=id,kind=kind,date=date,roles=mapOf("subject" to "a","subject2" to other))
    @Test fun boxesPreferLifeYearsAndOtherwiseUseMentionPeriod() {
        assertEquals("připomínán 1810–1860",GenealogyPresentation(tree).person("a").years)
        val born=LifeEvent(kind="genealogy.birth",date="1820-02-03",roles=mapOf("subject" to "a"))
        val died=LifeEvent(kind="genealogy.death",date="1890",roles=mapOf("subject" to "a"))
        assertEquals("* 1820  + 1890",GenealogyPresentation(tree.copy(events=listOf(born,died))).person("a").years)
        assertEquals("+ 1890",GenealogyPresentation(tree.copy(events=listOf(died))).person("a").years)
        assertEquals("",GenealogyPresentation(tree).person("b").years)
        assertEquals("připomínán od 1810",GenealogyPresentation(tree.copy(people=listOf(a.copy(mentionedTo="")))).person("a").years)
    }
    @Test fun partnershipIsAConnectionWhileStoredRecordAndIncompleteEventsRemainAvailable() {
        val relation=event("genealogy.spousal-record",date="1840",id="rel")
        val incomplete=LifeEvent(kind="genealogy.spousal-record",roles=mapOf("subject" to "a"))
        val source=tree.copy(events=listOf(relation,incomplete))
        val view=GenealogyPresentation(source)
        assertEquals(listOf(incomplete),view.visibleEvents)
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
        assertTrue(view.visibleEvents.isEmpty())
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
