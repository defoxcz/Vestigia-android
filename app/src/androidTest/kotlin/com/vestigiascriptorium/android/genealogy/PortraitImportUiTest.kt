package com.vestigiascriptorium.android.genealogy

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
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
class PortraitImportUiTest {
    @get:Rule val rule=createComposeRule()
    @Before fun unlocked() {Assert.assertFalse("Unlock phone for portrait/import UI",InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)}
    private fun registry(path: String)=object: ActivityResultRegistryOwner {
        override val activityResultRegistry=object: ActivityResultRegistry() {
            override fun <I,O> onLaunch(requestCode: Int,contract: ActivityResultContract<I,O>,input: I,options: ActivityOptionsCompat?) {
                dispatchResult(requestCode,Activity.RESULT_OK,Intent().setData(Uri.parse("content://com.vestigiascriptorium.android.debug.test.photo-fixture/$path")))
            }
        }
    }
    @Test fun selectingPortraitCopiesItThenRemoveAndUndoUpdateThePersonBox()=isolated {store,controller ->
        val person=Person(givenName="Anna",surname="Nováková");val tree=FamilyTree(title="Portréty",people=listOf(person))
        store.save(tree)
        rule.runOnIdle {controller.open(tree.id)}
        val owner=registry("portrait.png")
        rule.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}}
        waitText(person.name);rule.onNodeWithText(person.name).performClick();rule.onNodeWithText("Upravit").performClick()
        rule.onNodeWithText("Vložit obrázek").performScrollTo().performClick()
        rule.waitUntil(10_000) {rule.onAllNodesWithContentDescription("Portrét osoby").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Uložit osobu").performClick()
        rule.waitUntil(10_000) {controller.state.value.tree?.people?.single()?.portrait?.isNotEmpty()==true && !controller.state.value.busy}
        rule.waitUntil(10_000) {rule.onAllNodesWithText("Uložit osobu").fetchSemanticsNodes().isEmpty()}
        val key=controller.state.value.tree!!.people.single().portrait
        Assert.assertNotNull(controller.media.file(key))
        rule.onNodeWithContentDescription("Portrét osoby").assertIsDisplayed()
        rule.onNodeWithText(person.name).performClick();rule.onNodeWithText("Upravit").performClick()
        rule.onNodeWithText("Odstranit obrázek").performScrollTo().performClick();rule.onNodeWithText("Uložit osobu").performClick()
        rule.waitUntil(10_000) {controller.state.value.tree?.people?.single()?.portrait?.isEmpty()==true && !controller.state.value.busy}
        rule.waitUntil(10_000) {rule.onAllNodesWithText("Uložit osobu").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithContentDescription("Portrét osoby").assertDoesNotExist()
        rule.onNodeWithText("Zpět").performClick()
        rule.waitUntil(10_000) {controller.state.value.tree?.people?.single()?.portrait==key && !controller.state.value.busy}
        rule.onNodeWithContentDescription("Portrét osoby").assertIsDisplayed()
        Assert.assertEquals(key,store.read(tree.id).people.single().portrait)
    }
    @Test fun importPreviewRequiresConfirmationAndCreatesANewTree()=isolated {store,controller ->
        val owner=registry("family.ged")
        rule.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}}
        waitText("Importovat rodokmen");rule.onNodeWithText("Importovat rodokmen").performClick()
        rule.onNodeWithText("Vybrat soubor").performClick()
        rule.waitUntil(10_000) {controller.state.value.importPreview!=null && !controller.state.value.busy}
        Assert.assertTrue(store.list().isEmpty())
        rule.onNodeWithText("GEDCOM 7.0").assertExists()
        rule.onNodeWithText("Potvrdit import").performClick()
        rule.waitUntil(10_000) {controller.state.value.tree!=null && controller.state.value.importPreview==null && !controller.state.value.busy}
        val tree=controller.state.value.tree!!
        Assert.assertEquals("Anna Nováková",tree.people.single().name)
        Assert.assertEquals("1820",tree.events.single().date)
        Assert.assertTrue(File(store.root,"sources/${tree.importSource}").isFile)
        Assert.assertEquals(1,store.list().size)
    }
    private inline fun isolated(action: (GenealogyStore,GenealogyController)->Unit) {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"portrait-import-ui-${newId()}")
        val store=GenealogyStore(root);val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);val controller=GenealogyController(store,scope)
        try {action(store,controller)} finally {scope.cancel();store.close();root.deleteRecursively()}
    }
    private fun waitText(text: String) {rule.waitUntil(10_000) {rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
}
