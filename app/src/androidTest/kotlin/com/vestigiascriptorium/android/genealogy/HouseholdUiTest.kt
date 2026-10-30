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
class HouseholdUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun toolbarIconsAndGroupedWivesPreserveEditingAndPersistDisplayPreference() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertFalse("Unlock phone for household test",context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        val root=File(context.cacheDir,"household-${newId()}")
        val store=GenealogyStore(root)
        val father=Person(givenName="Jan",gender="male")
        val wife=Person(givenName="Anna",gender="female",x=400f)
        val child=Person(givenName="Dítě",y=250f)
        var tree=FamilyTree(title="Domácnost",people=listOf(father,wife,child))
        tree=GenealogyEdits.relate(tree,father.id,wife.id,Relative.SPOUSE)
        tree=GenealogyEdits.relate(tree,child.id,father.id,Relative.FATHER)
        tree=GenealogyEdits.relate(tree,child.id,wife.id,Relative.MOTHER)
        store.save(tree);store.last(tree.id)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val controller=GenealogyController(store,scope)
        try {
            rule.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
            rule.waitUntil(10000) {rule.onAllNodesWithTag("genealogy-node-${wife.id}").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("Přizpůsobit").assertDoesNotExist()
            rule.onNodeWithContentDescription("Vrátit změnu").assertExists()
            rule.onNodeWithContentDescription("Znovu").assertExists()
            rule.onNodeWithContentDescription("Přizpůsobit").performClick()
            val toggle=rule.onNodeWithContentDescription("Manželky v boxu manžela")
            toggle.performClick()
            rule.waitUntil(10000) {controller.state.value.collapseWives && !controller.state.value.busy}
            rule.onNodeWithTag("genealogy-node-${wife.id}").assertDoesNotExist()
            rule.onNodeWithTag("genealogy-node-${father.id}").assertExists()
            Assert.assertTrue(store.collapseWives())
            GenealogyStore(root).use {Assert.assertTrue(it.collapseWives())}
            rule.onNodeWithContentDescription("Přizpůsobit").performClick()
            rule.onNodeWithContentDescription("Otevřít osobu: Anna").performClick()
            rule.onNodeWithText("Anna").assertExists()
            rule.onNodeWithText("Upravit").performClick()
            rule.onNodeWithText("Jméno").performTextReplacement("Anna Marie")
            rule.onNodeWithText("Uložit osobu").performClick()
            rule.waitUntil(10000) {controller.state.value.tree?.people?.any {it.id==wife.id && it.givenName=="Anna Marie"}==true && !controller.state.value.busy}
            rule.waitUntil(10000) {rule.onAllNodesWithText("Uložit osobu").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithContentDescription("Vrátit změnu").performClick()
            rule.waitUntil(10000) {controller.state.value.tree?.people?.any {it.id==wife.id && it.givenName=="Anna"}==true && !controller.state.value.busy}
            rule.onNodeWithContentDescription("Znovu").performClick()
            rule.waitUntil(10000) {controller.state.value.tree?.people?.any {it.id==wife.id && it.givenName=="Anna Marie"}==true && !controller.state.value.busy}
            rule.onNodeWithContentDescription("Přizpůsobit").performClick()
            rule.waitForIdle();android.os.SystemClock.sleep(300)
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap ->File(context.cacheDir,"household-test.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
            toggle.performClick()
            rule.waitUntil(10000) {!controller.state.value.collapseWives && !controller.state.value.busy}
            rule.onNodeWithTag("genealogy-node-${wife.id}").assertExists()
            Assert.assertEquals(3,store.read(tree.id).people.size)
        } finally {scope.cancel();store.close();root.deleteRecursively()}
    }
}
