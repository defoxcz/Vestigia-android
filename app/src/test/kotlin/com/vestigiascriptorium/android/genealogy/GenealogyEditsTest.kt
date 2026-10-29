package com.vestigiascriptorium.android.genealogy

import org.junit.Assert.*
import org.junit.Test

class GenealogyEditsTest {
    private val child=Person(id="child",givenName="Dítě")
    private val father=Person(id="father",givenName="Otec")
    private val mother=Person(id="mother",givenName="Matka")
    private val tree=FamilyTree(title="Rod",people=listOf(child,father,mother))
    @Test fun relativesUseUndatedSharedParentageAndDoNotInventBirthOrMarriage() {
        val withFather=GenealogyEdits.relate(tree,child.id,father.id,Relative.FATHER)
        val result=GenealogyEdits.relate(withFather,child.id,mother.id,Relative.MOTHER)
        assertEquals(1,result.events.size)
        assertEquals("genealogy.parentage",result.events.single().kind)
        assertTrue(result.events.single().date.isEmpty())
        assertEquals(mapOf("subject" to child.id,"genealogy.father" to father.id,"genealogy.mother" to mother.id),result.events.single().roles)
        val spouse=GenealogyEdits.relate(result,father.id,mother.id,Relative.SPOUSE)
        assertEquals("genealogy.spousal-record",spouse.events.last().kind)
        assertTrue(spouse.events.last().date.isEmpty())
    }
    @Test fun rejectsCyclesOccupiedRolesDuplicatesAndSelfLinksWithoutChangingInput() {
        val linked=GenealogyEdits.relate(tree,child.id,father.id,Relative.FATHER)
        problem("cycle") {GenealogyEdits.relate(linked,father.id,child.id,Relative.FATHER)}
        problem("occupied") {GenealogyEdits.relate(linked,child.id,mother.id,Relative.FATHER)}
        problem("duplicate") {GenealogyEdits.relate(linked,child.id,father.id,Relative.FATHER)}
        problem("self") {GenealogyEdits.relate(linked,child.id,child.id,Relative.MOTHER)}
        assertEquals(1,linked.events.size)
    }
    @Test fun eventRoleEditsCannotBypassAncestryValidation() {
        val linked=GenealogyEdits.relate(tree,child.id,father.id,Relative.FATHER)
        val cyclic=linked.copy(events=linked.events+LifeEvent(kind="genealogy.birth",roles=mapOf("subject" to father.id,"genealogy.mother" to child.id)))
        problem("cycle") {GenealogyEdits.validate(cyclic)}
    }
    @Test fun personDateEditPreservesOtherBirthRolesAndGeometry() {
        val born=tree.copy(events=listOf(LifeEvent(kind="genealogy.birth",date="1820",roles=mapOf("subject" to child.id,"genealogy.father" to father.id),x=99f,y=11f)))
        val result=GenealogyEdits.person(born,child.copy(givenName="Nové jméno"),"1821","")
        assertEquals("1821",result.events.single().date)
        assertEquals(born.events.single().roles,result.events.single().roles)
        assertEquals(99f,result.events.single().x)
        assertEquals("Nové jméno",result.people.last().name)
    }
    @Test fun moveChangesOnlyGeometryAndEvidenceRequiresExistingPerson() {
        val moved=GenealogyEdits.move(tree,child.id,240f,-50f)
        assertEquals(tree.events,moved.events);assertEquals(240f,moved.people.first().x)
        problem("invalid") {GenealogyEdits.validate(tree.copy(evidence=listOf(Evidence(person="missing",caption="Doklad"))))}
    }
    @Test fun mentionDatesRejectInvalidOrderAndKeepYearPrecision() {
        val person=child.copy(givenName="Jan",middleName="Karel",surname="Novák",mentionedFrom="1820",mentionedTo="1820-02-03")
        val result=GenealogyEdits.person(tree,person,"","")
        assertEquals("Jan Karel Novák",result.people.last().name)
        assertEquals("1820",result.people.last().mentionedFrom)
        problem("dateRange") {GenealogyEdits.person(tree,person.copy(mentionedFrom="1821"),"","")}
        problem("date") {GenealogyEdits.person(tree,person.copy(mentionedFrom="0000"),"","")}
    }
    private fun problem(expected: String,action: ()->Unit) {try {action();fail("Expected $expected")} catch(e: GenealogyProblem) {assertEquals(expected,e.code)}}
}
