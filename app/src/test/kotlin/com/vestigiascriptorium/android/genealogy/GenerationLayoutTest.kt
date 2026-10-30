package com.vestigiascriptorium.android.genealogy

import org.junit.Assert.*
import org.junit.Test

class GenerationLayoutTest {
    @Test fun descendantsShareRowsWithoutOverlapAndBranchesBisectTheActualRowGap() {
        val a=Person(id="a",givenName="Otec",x=0f,y=0f)
        val b=Person(id="b",givenName="Matka",x=400f,y=90f)
        val children=(0..7).map {Person(id="c$it",givenName="Dítě $it",x=(it%3)*300f,y=300f+it*180)}
        val grandchild=Person(id="grandchild",givenName="Vnouče",y=900f)
        val events=children.map {LifeEvent(kind="genealogy.parentage",roles=mapOf("subject" to it.id,"genealogy.father" to a.id,"genealogy.mother" to b.id))}+
            LifeEvent(kind="genealogy.parentage",roles=mapOf("subject" to grandchild.id,"genealogy.father" to children.first().id))
        val tree=FamilyTree(title="Generace",people=listOf(a,b)+children+grandchild,events=events)
        val presentation=GenealogyPresentation(tree)
        val layout=GenerationLayout(tree,presentation)
        val boxes=layout.boxes(tree.people,mapOf("a" to 200f,"b" to 120f,"c0" to 180f))
        assertEquals(0f,boxes.getValue("b").y,0f)
        children.forEach {assertEquals(200f+160f/3f,boxes.getValue(it.id).y,.001f)}
        assertEquals(380f+320f/3f,boxes.getValue(grandchild.id).y,.001f)
        val sorted=children.map {boxes.getValue(it.id)}.sortedBy {it.x}
        assertTrue(sorted.zipWithNext().all {(left,right) ->right.x>=left.x+left.width+24})
        val family=presentation.families.single {it.parents.size==2}
        val segments=familySegments(family,boxes,parentRowBottom=200f)
        assertEquals(200f+80f/3f,segments[2].first.y,.001f)
        assertEquals(200f+80f/3f,segments[2].second.y,.001f)
        assertEquals(200f+80f/3f,segments[1].second.y,.001f)
        assertEquals(90f,b.y,0f) // layout never rewrites source positions
    }
    @Test fun siblingBusesShareTheRowMidpointEvenWithUnrelatedTallerBoxes() {
        val boxes=mapOf("p" to GraphBox(0f,0f,244f,100f),"c" to GraphBox(0f,400f,244f,100f))
        val lines=familySegments(FamilyConnection(listOf("p"),listOf("c")),boxes,parentRowBottom=240f)
        assertEquals(320f,lines[1].first.y,0f)
        assertEquals(320f,lines[1].second.y,0f)
    }
}
