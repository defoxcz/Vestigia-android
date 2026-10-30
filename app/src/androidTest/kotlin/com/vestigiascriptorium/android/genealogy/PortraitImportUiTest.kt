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
        rule.onNodeWithText("Vložit obrázek").performScrollTo()
        val localButton=rule.onNodeWithText("Vložit obrázek").fetchSemanticsNode().boundsInWindow
        val cloudButton=rule.onNodeWithText("Google Disk / soubory").fetchSemanticsNode().boundsInWindow
        Assert.assertEquals(localButton.center.y,cloudButton.center.y,2f)
        Assert.assertTrue(localButton.right<=cloudButton.left)
        rule.onNodeWithText("Vložit obrázek").performClick()
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
        rule.onNodeWithContentDescription("Vrátit změnu").performClick()
        rule.waitUntil(10_000) {controller.state.value.tree?.people?.single()?.portrait==key && !controller.state.value.busy}
        rule.onNodeWithContentDescription("Portrét osoby").assertIsDisplayed()
        Assert.assertEquals(key,store.read(tree.id).people.single().portrait)
    }
    @Test fun cloudProviderImageCopiesIntoPortraitAndEvidenceWithoutPersistentUriGrants()=isolated {store,controller ->
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val chooser=CloudImagePicker().createIntent(context,Unit)
        Assert.assertEquals(Intent.ACTION_CHOOSER,chooser.action)
        @Suppress("DEPRECATION") val intent=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        Assert.assertEquals(Intent.ACTION_GET_CONTENT,intent.action)
        Assert.assertEquals("image/*",intent.type)
        Assert.assertFalse(intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY,true))
        val person=Person(givenName="Cloud portrét");val tree=FamilyTree(title="Cloud",people=listOf(person))
        store.save(tree)
        rule.runOnIdle {controller.open(tree.id)}
        val owner=registry("cloud-portrait.png")
        rule.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}}
        waitText(person.name);rule.onNodeWithText(person.name).performClick();rule.onNodeWithText("Upravit").performClick()
        rule.onNodeWithText("Google Disk / soubory").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("Portrét osoby").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Uložit osobu").performClick()
        rule.waitUntil(10000) {controller.state.value.tree?.people?.single()?.portrait?.isNotEmpty()==true && !controller.state.value.busy}
        rule.waitUntil(10000) {rule.onAllNodesWithText("Uložit osobu").fetchSemanticsNodes().isEmpty()}
        val key=store.read(tree.id).people.single().portrait
        Assert.assertTrue(controller.media.file(key)!!.length()>0)
        rule.onNodeWithText(person.name).performClick()
        rule.onNodeWithText("Připojit dokumentaci").performScrollTo().performClick()
        rule.onNodeWithText("Popisek dokladu / citace").performTextInput("Obrázek z cloudu")
        rule.onNodeWithText("Google Disk / soubory").performClick()
        waitText("Obrázek je uložen jako vlastní kopie. Původní soubor se nemění.")
        rule.onNodeWithText("Uložit doklad").performClick()
        rule.waitUntil(10000) {controller.state.value.tree?.evidence?.size==1 && !controller.state.value.busy}
        val doc=store.read(tree.id).evidence.single()
        Assert.assertEquals("managed:$key",doc.file)
        Assert.assertEquals("image/png",doc.mime)
        rule.waitUntil(10000) {rule.onAllNodesWithText("Uložit doklad").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithText(person.name).performClick()
        rule.onNodeWithTag("evidence-preview-${doc.id}").performScrollTo().performClick()
        rule.onNodeWithText("Zavřít obrázek").assertIsDisplayed()
        rule.onNodeWithContentDescription("Zvětšit obrázek").performClick()
        rule.onNodeWithText("150 %").assertExists()
        rule.waitForIdle();android.os.SystemClock.sleep(500)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap ->File(context.cacheDir,"evidence-zoom-test.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        rule.onNodeWithText("Zavřít obrázek").performClick()
    }
    @Test fun regularFilePickerCanCreateAnImmutablePrivateCopy()=isolated {_,controller ->
        var saved: Evidence?=null
        val owner=registry("portrait.png")
        rule.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {VestigiaScriptoriumTheme {
            EvidenceDialog("person",Copy(true),false,{}, {saved=it},controller.media)
        }}}
        rule.onNodeWithText("Popisek dokladu / citace").performTextInput("Vlastní kopie")
        rule.onNodeWithText("Vybrat soubor").performScrollTo().performClick()
        waitText("Soubor vybrán")
        rule.onNodeWithText("Uložit doklad").performClick()
        rule.runOnIdle {
            Assert.assertTrue(saved!!.file.startsWith("managed:"))
            Assert.assertEquals("image/png",saved!!.mime)
            Assert.assertNotNull(controller.media.file(saved!!.file.removePrefix("managed:")))
        }
    }
    @Test fun pdfShowsFirstPageThumbnailAndLargeReadOnlyPreview()=isolated {_,controller ->
        val pdf=android.graphics.pdf.PdfDocument()
        val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(300,400,1).create())
        page.canvas.drawColor(android.graphics.Color.WHITE)
        page.canvas.drawText("Test evidence",20f,40f,android.graphics.Paint().apply {textSize=20f})
        pdf.finishPage(page)
        val bytes=java.io.ByteArrayOutputStream().also {pdf.writeTo(it)}.toByteArray();pdf.close()
        val key=controller.media.addDocument(bytes.inputStream())
        val doc=Evidence(person="person",caption="Test PDF",file="managed:$key",mime="application/pdf")
        rule.setContent {VestigiaScriptoriumTheme {androidx.compose.foundation.layout.Column {EvidencePreview(doc,controller.media,Copy(true),{})}}}
        waitText("První strana PDF")
        rule.onNodeWithTag("evidence-preview-${doc.id}").performClick()
        rule.onNodeWithText("Zavřít obrázek").assertIsDisplayed()
        rule.onNodeWithContentDescription("Zvětšit obrázek").performClick()
        rule.onNodeWithText("150 %").assertExists()
        rule.waitForIdle();android.os.SystemClock.sleep(500)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap ->File(context.cacheDir,"evidence-pdf-test.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        rule.onNodeWithText("Zavřít obrázek").performClick()
    }
    @Test fun sourceReferenceKeepsUriWithoutCreatingManagedCopy()=isolated {store,controller ->
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val target=instrumentation.targetContext
        val source=Uri.parse("content://com.vestigiascriptorium.android.debug.test.photo-fixture/portrait.png")
        instrumentation.context.grantUriPermission(target.packageName,source,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        val person=Person(givenName="Odkaz");val tree=FamilyTree(title="Doklady",people=listOf(person))
        store.save(tree);rule.runOnIdle {controller.open(tree.id)}
        val owner=registry("portrait.png")
        try {
            rule.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}}
            waitText(person.name);rule.onNodeWithText(person.name).performClick()
            rule.onNodeWithText("Připojit dokumentaci").performScrollTo().performClick()
            rule.onNodeWithText("Popisek dokladu / citace").performTextInput("Zdrojový obrázek")
            rule.onAllNodes(isSelectable()).filter(isNotSelected()).onFirst().performClick()
            rule.onNodeWithText("Vybrat soubor").performScrollTo().performClick()
            waitText("Soubor vybrán")
            rule.onNodeWithText("Uložit doklad").performClick()
            rule.waitUntil(10000) {controller.state.value.tree?.evidence?.size==1 && !controller.state.value.busy}
            val doc=store.read(tree.id).evidence.single()
            Assert.assertEquals(source.toString(),doc.file)
            Assert.assertTrue(controller.media.root.listFiles().orEmpty().isEmpty())
            rule.waitUntil(10000) {rule.onAllNodesWithText("Uložit doklad").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithText(person.name).performClick()
            rule.onNodeWithTag("evidence-preview-${doc.id}").performScrollTo().performClick()
            rule.onNodeWithText("Zavřít obrázek").assertIsDisplayed()
            rule.onNodeWithText("Zavřít obrázek").performClick()
        } finally {
            target.contentResolver.persistedUriPermissions.filter {it.uri==source}.forEach {target.contentResolver.releasePersistableUriPermission(source,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            instrumentation.context.revokeUriPermission(source,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
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
