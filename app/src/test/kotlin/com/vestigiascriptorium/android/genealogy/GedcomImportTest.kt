package com.vestigiascriptorium.android.genealogy

import org.junit.Assert.*
import org.junit.Test

class GedcomImportTest {
    private fun file(version: String,body: String,charset: String="UTF-8")="0 HEAD\n1 GEDC\n2 VERS $version\n1 CHAR $charset\n$body\n0 TRLR\n"
    private val family="""0 @I1@ INDI
1 NAME Jan Karel /Novák/
1 SEX M
1 BIRT
2 DATE 3 FEB 1820
1 NOTE První řádek
2 CONT Druhý řádek
0 @I2@ INDI
1 NAME Anna /Nováková/
0 @I3@ INDI
1 NAME Marie /Nováková/
1 BIRT
2 DATE 1845
0 @F1@ FAM
1 HUSB @I1@
1 WIFE @I2@
1 MARR
2 DATE 1840
2 PLAC Břeclav
1 CHIL @I3@"""
    @Test fun bothGedcomVersionsKeepNamesDatesRolesAndContinuations() {
        for(version in listOf("5.5.1","7.0")) {
            val result=GedcomImport.parse(file(version,family).toByteArray(),"Rod")
            val tree=result.trees.single();assertEquals(3,tree.people.size)
            val jan=tree.people.first {it.givenName=="Jan"};assertEquals("Karel",jan.middleName);assertEquals("Novák",jan.surname);assertEquals("První řádek\nDruhý řádek",jan.note)
            assertEquals("1820-02-03",tree.events.first {it.kind=="genealogy.birth" && it.roles["subject"]==jan.id}.date)
            val daughter=tree.people.first {it.givenName=="Marie"};val birth=tree.events.first {it.roles["subject"]==daughter.id}
            assertEquals("1845",birth.date);assertEquals(jan.id,birth.roles["genealogy.father"])
            assertEquals("1840",tree.events.first {it.kind=="genealogy.marriage"}.date)
        }
    }
    @Test fun familyWithoutMarriageDoesNotInventAWedding() {
        val parsed=GedcomImport.parse(file("7.0",family.replace("1 MARR\n2 DATE 1840\n2 PLAC Břeclav\n","")).toByteArray(),"Rod")
        assertTrue(parsed.trees.single().events.none {it.kind=="genealogy.marriage"})
        assertEquals(1,parsed.trees.single().events.count {it.kind=="genealogy.spousal-record"})
    }
    @Test fun utf16AndAnselAreDecodedWithoutReplacementAndYearDatesKeepPrecision() {
        val source=file("5.5.1","0 @I@ INDI\n1 NAME Čeněk /Novák/","UNICODE")
        val unicode=byteArrayOf(0xff.toByte(),0xfe.toByte())+source.toByteArray(Charsets.UTF_16LE)
        assertEquals("Čeněk",GedcomImport.parse(unicode,"Rod").trees.single().people.single().givenName)
        val ansel=file("5.5.1","0 @I@ INDI\n1 NAME #Cenek /Novak/","ANSEL").toByteArray().let {bytes ->bytes.flatMap {if(it=='#'.code.toByte())listOf(0xe9.toByte()) else listOf(it)}.toByteArray()}
        assertEquals("Čenek",GedcomImport.parse(ansel,"Rod").trees.single().people.single().givenName)
        assertEquals("0900",GedcomImport.date("900"));assertEquals("ABT 1820",GedcomImport.date("ABT 1820"))
    }
    @Test fun rejectsDuplicateIdsDanglingParentsLevelJumpsAndCycles() {
        listOf(family+"\n0 @I1@ INDI",family.replace("1 HUSB @I1@","1 HUSB @MISSING@"),family.replace("1 SEX M","3 SEX M"),family+"\n0 @F2@ FAM\n1 HUSB @I3@\n1 CHIL @I1@").forEach {body ->
            try {GedcomImport.parse(file("7.0",body).toByteArray(),"Rod");fail("Should reject invalid input")}catch(_: IllegalArgumentException){}
        }
    }
    @Test fun adoptionIsReportedRatherThanInventedAsBiologicalParentage() {
        val input=family.replace("1 BIRT\n2 DATE 1845","1 FAMC @F1@\n2 PEDI ADOPTED")
        val result=GedcomImport.parse(file("7.0",input).toByteArray(),"Rod")
        assertTrue(result.warnings.any {it.contains("Adoptivní")})
        val child=result.trees.single().people.first {it.givenName=="Marie"}
        assertTrue(result.trees.single().events.none {it.roles["subject"]==child.id})
    }
    @Test fun fourThousandPeopleImportUsesIterativeValidationAndLayout() {
        val body=buildString {repeat(4000) {append("0 @I$it@ INDI\n1 NAME Osoba $it /Rod/\n")};repeat(3999) {append("0 @F$it@ FAM\n1 HUSB @I$it@\n1 CHIL @I${it+1}@\n")}}
        val result=GedcomImport.parse(file("7.0",body).toByteArray(),"Velký rod")
        assertEquals(4000,result.trees.single().people.size);assertEquals(3999,result.trees.single().events.size)
    }
}
