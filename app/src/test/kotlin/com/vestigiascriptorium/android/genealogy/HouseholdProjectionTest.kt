package com.vestigiascriptorium.android.genealogy

import org.junit.Assert.*
import org.junit.Test

class HouseholdProjectionTest {
    private val father=Person(id="f",givenName="Otec",gender="male")
    private val a=Person(id="a",givenName="Anna",gender="female")
    private val b=Person(id="b",givenName="Marie",gender="female")
    private val c=Person(id="c",givenName="Dítě")
    private fun partnership(wife: String)=LifeEvent(kind="genealogy.marriage",roles=mapOf("subject" to "f","subject2" to wife))
    private fun source()=FamilyTree(title="Rodina",people=listOf(father,a,b,c),events=listOf(partnership("a"),partnership("b"),LifeEvent(kind="genealogy.birth",roles=mapOf("subject" to "c","genealogy.father" to "f","genealogy.mother" to "a"))))
    @Test fun wivesRemainEntitiesWhileOnlyTheirSeparateCardsAreHidden() {
        val tree=source();val presentation=GenealogyPresentation(tree)
        val grouped=HouseholdProjection(tree,presentation,true)
        assertEquals(mapOf("a" to "f","b" to "f"),grouped.owners)
        assertEquals(listOf("f","c"),grouped.visible(tree.people).map {it.id})
        val family=grouped.family(presentation.families.single())
        assertEquals(listOf("f"),family.parents)
        val box=GraphBox(0f,0f,244f,210f)
        val child=GraphBox(0f,270f,244f,88f)
        val lines=familySegments(family,mapOf("f" to box,"c" to child))
        assertEquals(GraphPoint(122f,210f),lines.first().first)
        assertEquals(GraphPoint(122f,270f),lines.last().second)
        assertEquals(box,grouped.aliases(mapOf("f" to box,"c" to child))["a"])
        assertEquals(4,tree.people.size)
        assertTrue(HouseholdProjection(tree,presentation,false).owners.isEmpty())
    }
    @Test fun groupedFatherCentersOverAllChildrenAcrossWivesWithoutChangingSourceBoxes() {
        val tree=source();val projection=HouseholdProjection(tree,GenealogyPresentation(tree),true)
        val boxes=mapOf("f" to GraphBox(-400f,0f,244f,180f),"c" to GraphBox(500f,250f,244f,88f),"d" to GraphBox(1100f,250f,244f,88f))
        val result=projection.centerFathers(boxes,listOf(FamilyConnection(listOf("f","a"),listOf("c")),FamilyConnection(listOf("f","b"),listOf("d"))))
        assertEquals(800f,result.getValue("f").x,0f)
        assertEquals(922f,result.getValue("f").center().x,0f)
        assertEquals(-400f,boxes.getValue("f").x,0f)
        assertEquals(boxes.getValue("c"),result.getValue("c"))
        val single=projection.centerFathers(boxes,listOf(FamilyConnection(listOf("f","a"),listOf("c"))))
        assertEquals(500f,single.getValue("f").x,0f)
    }
    @Test fun unknownFatherOrMultipleHusbandsNeverBecomeAnImpliedParentalRelationship() {
        val tree=source()
        val motherOnly=tree.copy(events=tree.events.map {it.copy(roles=it.roles-"genealogy.father")})
        assertFalse(HouseholdProjection(motherOnly,GenealogyPresentation(motherOnly),true).owners.containsKey("a"))
        val other=Person(id="other",givenName="Druhý manžel",gender="male")
        val multi=tree.copy(people=tree.people+other,events=tree.events+LifeEvent(kind="genealogy.marriage",roles=mapOf("subject" to "other","subject2" to "a")))
        assertFalse(HouseholdProjection(multi,GenealogyPresentation(multi),true).owners.containsKey("a"))
    }
}
