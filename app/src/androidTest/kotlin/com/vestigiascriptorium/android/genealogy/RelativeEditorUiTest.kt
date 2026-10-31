package com.vestigiascriptorium.android.genealogy

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RelativeEditorUiTest {
    @get:Rule val rule=createComposeRule()
    @Before fun unlocked() {Assert.assertFalse("Unlock phone for relative editor tests",InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)}
    @Test fun newPersonOpensRelativePickerAndSearchConnectsTheSelectedExistingPerson()=isolated {store,controller ->
        val father=Person(givenName="Jiří",surname="Dvořák")
        val other=Person(givenName="Jan",surname="Novák")
        val tree=FamilyTree(title="Test příbuzných",people=listOf(father,other))
        store.save(tree);store.last(tree.id)
        rule.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
        rule.runOnIdle {controller.open(tree.id)}
        waitText("+ Osoba");rule.onNodeWithText("+ Osoba").performClick()
        rule.onNodeWithText("Jméno").performScrollTo().performTextInput("Anna")
        rule.onNodeWithText("Příjmení").performScrollTo().performTextInput("Dvořáková")
        val date=GenealogyDates.format("1691-03-21").replace(Regex("(?<![0-9])0(?=[1-9])"),"")
        rule.onNode(hasSetTextAction() and hasText("Datum narození")).performScrollTo().performTextInput(date)
        rule.onNodeWithText("Přidat příbuzného").performScrollTo().performClick()
        waitText("Hledat existující osobu")
        rule.onNodeWithText("Hledat existující osobu").performTextInput("jiri dvorak")
        rule.onNode(hasText("Jiří Dvořák") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        rule.onNodeWithText("Vybraná osoba: Jiří Dvořák").assertExists()
        // A new IME value invalidates the previous selection and updates suggestions.
        rule.onNodeWithText("Hledat existující osobu").performTextReplacement("jan novak")
        rule.onNode(hasText("Jan Novák") and hasAnyAncestor(isDialog())).performScrollTo().assertExists()
        rule.onNodeWithText("Vybraná osoba: Jiří Dvořák").assertDoesNotExist()
        rule.onNodeWithText("Hledat existující osobu").performTextReplacement("jiri dvorak")
        rule.onNode(hasText("Jiří Dvořák") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        rule.onNodeWithText("Připojit osobu").performClick()
        rule.waitUntil(10_000) {controller.state.value.tree?.events?.any {it.roles["genealogy.father"]==father.id}==true && !controller.state.value.busy}
        rule.waitUntil(10_000) {rule.onAllNodesWithText("Připojit osobu").fetchSemanticsNodes().isEmpty()}
        val result=controller.state.value.tree!!
        Assert.assertEquals(3,result.people.size)
        val anna=result.people.single {it.givenName=="Anna"}
        Assert.assertEquals("1691-03-21",result.events.single {it.kind=="genealogy.birth"}.date)
        result.events.forEach {rule.onNodeWithTag("genealogy-node-${it.id}").assertDoesNotExist()}
        Assert.assertEquals(ParentConnection(father.id,anna.id,"genealogy.father"),GenealogyPresentation(result).parents.single())
        rule.onNodeWithContentDescription("Přizpůsobit").performClick()
        rule.onNodeWithText("Anna Dvořáková").performClick()
        rule.onNodeWithText("Upravit").performClick()
        rule.onNodeWithText("Jméno").performScrollTo().performTextReplacement("Anna Marie")
        rule.onNodeWithText("Přidat příbuzného").performScrollTo().performClick()
        waitText("Hledat existující osobu")
        rule.onNodeWithText("Zrušit").performClick()
        Assert.assertEquals("Anna Marie",store.read(tree.id).people.single {it.id==anna.id}.givenName)
    }
    @Test fun childrenShareGenerationRowAndFamilyBranch()=isolated {store,controller ->
        val father=Person(givenName="Otec",x=0f,y=0f)
        val mother=Person(givenName="Matka",x=400f,y=0f)
        val source=GenealogyEdits.relate(FamilyTree(title="Rodina",people=listOf(father,mother)),father.id,mother.id,Relative.SPOUSE)
        store.save(source)
        rule.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
        rule.runOnIdle {controller.open(source.id)}
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("Přizpůsobit").fetchSemanticsNodes().isNotEmpty()}
        repeat(6) {index ->
            rule.onNodeWithContentDescription("Přizpůsobit").performClick()
            rule.onNodeWithTag("genealogy-node-${father.id}").performClick()
            rule.onNodeWithText("Přidat příbuzného").performScrollTo().performClick()
            rule.onNodeWithText("Vztah k vybrané osobě: Otec").performClick()
            rule.onNodeWithText("Dítě · vybraná osoba je otec").performClick()
            rule.onNodeWithText("Druhý rodič dítěte: Neurčený druhý rodič").performClick()
            rule.onNode(hasText("Matka") and hasAnyAncestor(isPopup())).performClick()
            rule.onNodeWithText("Jméno").performScrollTo().performTextInput("Dítě ${index+1}")
            rule.onNodeWithText("Připojit osobu").performClick()
            rule.waitUntil(10000) {controller.state.value.tree?.people?.size==index+3 && !controller.state.value.busy}
            rule.waitUntil(10000) {rule.onAllNodesWithText("Připojit osobu").fetchSemanticsNodes().isEmpty()}
        }
        val tree=store.read(source.id)
        val family=GenealogyPresentation(tree).families.single()
        Assert.assertEquals(setOf(father.id,mother.id),family.parents.toSet())
        Assert.assertEquals(6,family.children.size)
        rule.onNodeWithContentDescription("Přizpůsobit").performClick()
        rule.waitForIdle()
        val tops=family.children.map {rule.onNodeWithTag("genealogy-node-$it").fetchSemanticsNode().boundsInWindow.top}
        Assert.assertTrue("All six descendants must share a row",tops.max()-tops.min()<2f)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        android.os.SystemClock.sleep(500) // Allow platform dialog/IME dismissal to finish before the screenshot.
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap ->File(context.cacheDir,"family-branch-test.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    }
    private inline fun isolated(action: (GenealogyStore,GenealogyController)->Unit) {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"relative-editor-${newId()}")
        val store=GenealogyStore(root);val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);val controller=GenealogyController(store,scope)
        try {action(store,controller)} finally {scope.cancel();store.close();root.deleteRecursively()}
    }
    private fun waitText(text: String) {rule.waitUntil(10_000) {rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
}
