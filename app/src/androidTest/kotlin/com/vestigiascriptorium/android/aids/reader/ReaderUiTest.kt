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
    @Test fun galleryOpensEmbeddedImageAndZoom() = withReaderFixtures { root,names ->
        val source=AidReaderSource { File(root,names.getValue("heraldry-templates")) }
        val first=runBlocking { source.page("heraldry-templates","cs","","",0).records.first() }
        rule.setContent { VestigiaScriptoriumTheme { AidReaderScreen(aids.first {it.id=="heraldry-templates"},Copy(true),source,{}) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(first.title).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(first.title)[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription(first.images.first().name).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(first.images.first().name).performClick()
        rule.onNodeWithText("Zavřít obrázek").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Zpět").performClick()
        rule.onNodeWithText("Hledat v záznamech").assertExists()
    }
}
