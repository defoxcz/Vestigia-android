package com.vestigiascriptorium.android.aids.reader

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.main.aids
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PlaceSearchTest {
    @get:Rule val rule=createComposeRule()
    private fun fixture(test: (AidReaderSource) -> Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"places-search-${UUID.randomUUID()}.db")
        try {
            SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
                db.execSQL("CREATE TABLE places(id TEXT PRIMARY KEY,canonical_name TEXT,administrative_units TEXT,country TEXT,search_text TEXT)")
                db.execSQL("CREATE TABLE place_names(place_id TEXT,name TEXT,normalized_name TEXT)")
                for ((id,name,country) in listOf(Triple("1","Břeclav","CZ"),Triple("2","Bavory","CZ"),Triple("3","Lundenburg","AT"))) {
                    db.execSQL("INSERT INTO places VALUES(?,?,?,?,?)",arrayOf(id,name,"okres Břeclav",country,"${AidSchemas.fold(name)} okres breclav"))
                    db.execSQL("INSERT INTO place_names VALUES(?,?,?)",arrayOf(id,name,AidSchemas.fold(name)))
                }
                db.execSQL("INSERT INTO place_names VALUES('1','Lundenburg','lundenburg')")
            }
            test(AidReaderSource { file })
        } finally { file.delete() }
    }
    @Test fun namesAndAliasesMatchButDistrictsDoNot() = fixture { source -> runBlocking {
        for (query in listOf("B","Bř","Bře","Břec","Břecl","Břecla","Břeclav","breclav","Břeclav ")) {
            val page=source.page("places","cs",query,"",0)
            assertTrue(query,page.records.any { it.id=="1" })
            if(AidSchemas.fold(query.trim()).length>1) assertEquals(query,listOf("1"),page.records.map { it.id })
        }
        assertEquals(listOf("3"),source.page("places","cs","Lundenburg","",0).records.map { it.id })
        assertEquals(setOf("1","3"),source.page("places","cs","Lundenburg","",0,searchScope=SearchScope.ALL_FIELDS).records.map { it.id }.toSet())
        assertEquals(setOf("1","2","3"),source.page("places","cs","Břeclav","",0,searchScope=SearchScope.ALL_FIELDS).records.map { it.id }.toSet())
        assertEquals(listOf("1"),source.page("places","cs","Lundenburg","CZ",0,searchScope=SearchScope.ALL_FIELDS).records.map { it.id })
        assertEquals(0,source.page("places","cs","Břeclav","AT",0).total)
    } }
    @Test fun keyboardReplacementRefreshesTheVisiblePlaces() = fixture { source ->
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeFalse(context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        rule.setContent { VestigiaScriptoriumTheme { AidReaderScreen(aids.first { it.id=="places" },Copy(true),source,{}) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Bavory").fetchSemanticsNodes().isNotEmpty() }
        val input=rule.onNode(hasSetTextAction())
        for (query in listOf("B","Bř","Bře","Břec","Břecl","Břecla","Břeclav")) input.performTextReplacement(query)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("1 záznamů").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Bavory").assertDoesNotExist()
        input.performTextReplacement("breclav")
        input.performTextReplacement("Břeclav ") // IME commits the accented suggestion and appends a space.
        rule.waitUntil(10_000) { rule.onAllNodesWithText("1 záznamů").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Bavory").assertDoesNotExist()
        rule.onNodeWithText("Lundenburg").assertDoesNotExist()
        rule.onNodeWithText("Břeclav",useUnmergedTree=true).assertExists()
        rule.onNodeWithText("Všechna pole").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Bavory").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Pouze názvy").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("1 záznamů").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Bavory").assertDoesNotExist()
    }
}
