package com.vestigiascriptorium.android.aids.reader

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.main.aids
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderUiTest {
    @get:Rule val rule=createComposeRule()
    @Before fun unlocked() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeFalse("Unlock phone for UI tests",context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
    }
    @Test fun textReaderOpensRecordReturnsAndSearches() = withReaderFixtures { root,names ->
        val source=AidReaderSource { File(root,names.getValue("historical-units")) }
        val first=runBlocking { source.page("historical-units","cs","","",0).records.first() }
        rule.setContent { VestigiaScriptoriumTheme { AidReaderScreen(aids.first {it.id=="historical-units"},Copy(true),source,{}) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(first.title).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(first.title)[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() && rule.onAllNodesWithText(first.title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Pramen"))
        rule.onNodeWithText("Pramen").assertExists()
        rule.onNodeWithContentDescription("Zpět").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("zzzxqv")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Žádné odpovídající záznamy. Zkuste změnit hledání nebo filtr.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Smazat").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(first.title).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun galleryOpensRecord() = withReaderFixtures { root,names ->
        val source=AidReaderSource { File(root,names.getValue("sigillography")) }
        val first=runBlocking { source.page("sigillography","cs","","",0).records.first() }
        rule.setContent { VestigiaScriptoriumTheme { AidReaderScreen(aids.first {it.id=="sigillography"},Copy(true),source,{}) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(first.title).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(first.title)[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() && rule.onAllNodesWithText(first.title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Zpět").performClick()
        rule.onNodeWithText("Hledat v záznamech").assertExists()
    }    @Test fun placeDetailOffersMapButton() = withReaderFixtures { root,names ->
        val source=AidReaderSource { File(root,names.getValue("places")) }
        val first=runBlocking { source.page("places","cs","","",0).records.first() }
        rule.setContent { VestigiaScriptoriumTheme { AidReaderScreen(aids.first { it.id=="places" },Copy(true),source,{}) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(first.title).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(first.title)[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Zobrazit na mapě").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Zobrazit na mapě").assertIsDisplayed()
    }
    @Test fun sharedImageZoomClosesAndResets() {
        val bitmap=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888)
        val bytes=java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        val closed=androidx.compose.runtime.mutableStateOf(false)
        rule.setContent { VestigiaScriptoriumTheme { if(!closed.value) ImageZoom(RecordImage("Test",bytes,online=false),Copy(true),onClose={closed.value=true}) } }
        rule.onNodeWithText("100 %").performClick()
        rule.onNodeWithText("Zavřít obrázek").performClick()
        rule.runOnIdle { org.junit.Assert.assertTrue(closed.value) }
    }

}
