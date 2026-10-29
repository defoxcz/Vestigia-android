package com.vestigiascriptorium.android.genealogy

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
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
class GenealogyUiTest {
    @get:Rule val rule=createComposeRule()
    @Before fun unlocked() {Assert.assertFalse("Unlock phone for genealogy UI tests",InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)}
    @Test fun createPersonAttachRelativeUndoRedoAndReopenTree() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"genealogy-ui-${newId()}")
        val store=GenealogyStore(root)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val controller=GenealogyController(store,scope)
        try {
            rule.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
            waitText("Nový rodokmen");rule.onNodeWithText("Nový rodokmen").performClick()
            rule.onNodeWithText("Název rodokmenu").performTextInput("Testovací rodina")
            rule.onNodeWithText("Vytvořit").performClick()
            waitText("+ Osoba");rule.onNodeWithText("+ Osoba").performClick()
            rule.onNodeWithText("Jméno osoby").performTextInput("Anna Nováková")
            rule.onNodeWithText("Uložit osobu").performClick()
            rule.waitUntil(10_000) {controller.state.value.tree?.people?.size==1 && !controller.state.value.busy}
            rule.waitUntil(10_000) {rule.onAllNodesWithText("Uložit osobu").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithText("Anna Nováková").performClick()
            waitText("Přidat příbuzného");rule.onNodeWithText("Přidat příbuzného").performClick()
            rule.onNodeWithText("Jméno nové osoby").performTextInput("Jan Novák")
            rule.onNodeWithText("Připojit osobu").performClick()
            rule.waitUntil(10_000) {controller.state.value.tree?.people?.size==2 && !controller.state.value.busy}
            rule.waitUntil(10_000) {rule.onAllNodesWithText("Připojit osobu").fetchSemanticsNodes().isEmpty()}
            Assert.assertEquals("genealogy.parentage",controller.state.value.tree!!.events.single().kind)
            rule.onNodeWithText("Zpět").performClick()
            rule.waitUntil(10_000) {controller.state.value.tree?.people?.size==1 && !controller.state.value.busy}
            rule.onNodeWithText("Znovu").performClick()
            rule.waitUntil(10_000) {controller.state.value.tree?.people?.size==2 && !controller.state.value.busy}
            rule.onNodeWithText("Přizpůsobit").performClick()
            rule.onNodeWithText("Jan Novák").assertIsDisplayed()
            screenshot("genealogy-graph-test.png")
            rule.onNodeWithContentDescription("Zpět").performClick()
            waitText("Nový rodokmen");rule.onNodeWithText("Testovací rodina").performClick()
            waitText("+ Osoba")
            Assert.assertEquals(2,controller.state.value.tree!!.people.size)
        } finally {scope.cancel();store.close();root.deleteRecursively()}
    }
    @Test fun editedDraftSurvivesRestorationAndCitationIsStoredWithPerson() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"genealogy-draft-${newId()}")
        val store=GenealogyStore(root)
        val p=Person(name="Anna Nováková")
        val tree=FamilyTree(title="Rodina",people=listOf(p))
        store.save(tree);store.last(tree.id)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val controller=GenealogyController(store,scope)
        try {
            val restore=StateRestorationTester(rule)
            restore.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
            waitText(p.name);rule.onNodeWithText(p.name).performClick()
            rule.onNodeWithText("Připojit dokumentaci").performScrollTo().performClick()
            rule.onNodeWithText("Popisek dokladu / citace").performTextInput("Matrika 12, strana 45")
            rule.onNodeWithText("Poznámka k dokladu").performTextInput("Narození v Břeclavi")
            rule.onNodeWithText("Uložit doklad").performClick()
            rule.waitUntil(10_000) {controller.state.value.tree?.evidence?.size==1 && !controller.state.value.busy}
            rule.waitUntil(10_000) {rule.onAllNodesWithText("Uložit doklad").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithText(p.name).performClick()
            rule.onNodeWithText("Upravit").performClick()
            rule.onNodeWithText("Jméno osoby").performTextReplacement("Anna Nováková upravená")
            rule.onNodeWithText("Datum narození").performScrollTo().performTextInput("kolem 1820")
            restore.emulateSavedInstanceStateRestore()
            rule.onNodeWithText("Jméno osoby").assertTextContains("Anna Nováková upravená")
            rule.onNodeWithText("Datum narození").assertTextContains("kolem 1820")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            screenshot("genealogy-editor-test.png",hasAnyDescendant(hasText("Uložit osobu")))
            rule.onNodeWithText("Zavřít").performClick()
            rule.onNodeWithText("Pokračovat v úpravách").performClick()
            rule.onNodeWithText("Uložit osobu").performClick()
            rule.waitUntil(10_000) {controller.state.value.tree?.people?.single()?.name=="Anna Nováková upravená" && !controller.state.value.busy}
            rule.waitUntil(10_000) {rule.onAllNodesWithText("Uložit osobu").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithText("Přizpůsobit").performClick()
            rule.onNodeWithText("Narození").performClick()
            rule.onNodeWithText("Místo události").performScrollTo().performTextInput("Břeclav")
            rule.onNodeWithText("Uložit událost").performClick()
            rule.waitUntil(10_000) {controller.state.value.tree?.events?.single()?.place=="Břeclav" && !controller.state.value.busy}
            val stored=store.read(tree.id)
            Assert.assertEquals("Matrika 12, strana 45",stored.evidence.single().caption)
            Assert.assertEquals(p.id,stored.evidence.single().person)
            Assert.assertEquals("kolem 1820",stored.events.single().date)
            Assert.assertEquals("Břeclav",stored.events.single().place)
        } finally {scope.cancel();store.close();root.deleteRecursively()}
    }
    private fun screenshot(name: String,filter: SemanticsMatcher?=null) {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        if(filter!=null)rule.onNode(isRoot() and filter).assertExists()
        rule.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(target.cacheDir,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun waitText(value: String) {rule.waitUntil(10_000) {rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}}
}
