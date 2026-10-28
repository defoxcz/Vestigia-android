package com.vestigiascriptorium.android.aids.reader

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

class AidSchemasTest {
    private val ids=listOf("historical-units","saints","places","manuscript-abbreviations","heraldry","heraldry-nobility","heraldry-ecclesiastical","heraldry-chivalric-orders","heraldry-burgher-patrician","heraldry-personal-coats-of-arms","heraldry-templates","numismatics","sigillography","epigraphy-noble-tombs")
    @Test fun everyPublishedAidHasTheExpectedSharedPresentation() {
        assertEquals(14,ids.size)
        assertEquals(4,ids.count { AidSchemas.get(it,"cs").kind==AidViewKind.TEXT })
        for(id in ids) {
            val schema=AidSchemas.get(id,"cs")
            assertTrue(schema.fields.isNotEmpty())
            assertTrue(schema.key.isNotBlank())
            assertEquals(schema.kind,AidSchemas.get(id,"en").kind)
        }
        assertThrows(IllegalArgumentException::class.java) { AidSchemas.get("../unknown","cs") }
    }
    @Test fun searchesTreatInputAsLiteralAndNormaliseDiacritics() {
        assertEquals("cesky krumlov",AidSchemas.fold("ČESKÝ Krumlov"))
        assertEquals("%a\\%\\_\\\\b%",AidSchemas.like("a%_\\b"))
        assertEquals("\"OR\"* AND \"x\"*",AidSchemas.fts("\" OR (x):*"))
        assertEquals("",AidSchemas.fts("\"%_*()"))
    }
    @Test fun exportQueriesForReadOnlyVerificationAgainstPublicDatabases() {
        val output=System.getenv("VESTIGIA_READER_PROBES") ?: return
        val rows=ids.flatMap { id -> listOf("cs","en").map { language ->
            val s=AidSchemas.get(id,language)
            listOf(id,language,s.from,s.key,s.title,s.summary,s.search,s.fts ?: "",s.titleJson,s.facet?.sql ?: "",s.facet?.optionsSql ?: "",s.fields.joinToString(","){it.sql},AidSchemas.imageExists(s),if(s.imageFamily!=null && s.imageFamily!="templates") AidSchemas.imageSql(s.imageFamily) else "",AidSchemas.extrasSql(s.imageFamily).joinToString("\n"))
                .joinToString("\t") { Base64.getEncoder().encodeToString(it.toByteArray()) }
        }}
        File(output).writeText(rows.joinToString("\n"))
    }
}
