package com.vestigiascriptorium.android.genealogy

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PeopleIndexTest {
    @Test fun rowsSortBySurnameAndUseBirthYearBeforeFirstMention() {
        val anna=Person(givenName="Anna",middleName="Marie",surname="Nováková",mentionedFrom="1840")
        val jan=Person(givenName="Jan",surname="Adamec",mentionedFrom="1780-05-06")
        val tree=FamilyTree(title="Rod",people=listOf(anna,jan),events=listOf(LifeEvent(kind="genealogy.birth",date="1820-03-04",roles=mapOf("subject" to anna.id))))
        val rows=PeopleIndex.rows(tree,Locale.forLanguageTag("cs-CZ"))
        assertEquals(listOf(jan.id,anna.id),rows.map {it.person.id})
        assertEquals(1780,rows[0].year);assertTrue(rows[0].fromMention)
        assertEquals(1820,rows[1].year);assertFalse(rows[1].fromMention)
        assertEquals(listOf(anna.id),PeopleIndex.filter(rows,"marie nova").map {it.person.id})
        assertEquals(listOf(anna.id),PeopleIndex.filter(rows,"Nováková ").map {it.person.id})
        assertTrue(PeopleIndex.filter(rows,"novakova 1780").isEmpty())
    }
}
