package com.vestigiascriptorium.android.genealogy

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vestigiascriptorium.android.aids.reader.AidReaderSource
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import kotlinx.coroutines.*
import org.junit.*
import java.io.File

class PlacesAndLayoutUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Before fun unlocked() {Assert.assertFalse("Unlock phone for places/layout tests",InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)}
    private fun isolated(action: (GenealogyStore,GenealogyController)->Unit) {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"places-layout-${newId()}")
        val store=GenealogyStore(root);val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        try {action(store,GenealogyController(store,scope))} finally {scope.cancel();store.close();root.deleteRecursively()}
    }
    @Test fun manualPlacesSurviveDraftRestoreSaveAndUndo()=isolated {store,controller ->
        val person=Person(givenName="Jan");val spouse=Person(givenName="Anna",x=350f)
        val marriage=LifeEvent(kind="genealogy.marriage",date="1820",roles=mapOf("subject" to person.id,"subject2" to spouse.id))
        val tree=FamilyTree(title="Obce",people=listOf(person,spouse),events=listOf(marriage))
        store.save(tree);rule.runOnIdle {controller.open(tree.id)}
        val restoration=StateRestorationTester(rule)
        restoration.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
        waitTag("genealogy-node-${person.id}");rule.onNodeWithTag("genealogy-node-${person.id}").performClick()
        rule.onNodeWithText("Upravit").performClick()
        rule.onNodeWithText("Obec narození").performScrollTo().performTextInput("Břeclav")
        rule.onNodeWithText("Obec úmrtí").performScrollTo().performTextInput("Brno")
        rule.onNode(hasSetTextAction() and hasText("Obec svatby",substring=true)).performScrollTo().performTextInput("Praha")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Obec narození").assertTextContains("Břeclav")
        rule.onNodeWithText("Uložit osobu").performClick()
        rule.waitUntil(10000) {controller.state.value.tree?.events?.any {it.place=="Praha"}==true && !controller.state.value.busy}
        val saved=store.read(tree.id)
        Assert.assertEquals("Břeclav",saved.events.single {it.kind=="genealogy.birth"}.place)
        Assert.assertEquals("Brno",saved.events.single {it.kind=="genealogy.death"}.place)
        Assert.assertEquals("Praha",saved.events.single {it.id==marriage.id}.place)
        Assert.assertTrue(saved.events.filter {it.id!=marriage.id}.all {it.date.isEmpty()})
        rule.waitUntil(10000) {rule.onAllNodesWithText("Uložit osobu").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithContentDescription("Vrátit změnu").performClick()
        rule.waitUntil(10000) {controller.state.value.tree?.events?.size==1 && !controller.state.value.busy}
        Assert.assertEquals("",store.read(tree.id).events.single().place)
    }
    @Test fun suggestionsUseOnlyIsolatedInstalledDataAndRefreshAfterImeReplacement()=isolated {store,_ ->
        val file=File(store.root,"places.db")
        SQLiteDatabase.openOrCreateDatabase(file,null).use {db ->
            db.execSQL("CREATE TABLE places(id TEXT PRIMARY KEY,canonical_name TEXT,administrative_units TEXT,country TEXT,search_text TEXT)")
            db.execSQL("CREATE TABLE place_names(place_id TEXT,name TEXT,normalized_name TEXT)")
            db.execSQL("INSERT INTO places VALUES('one','Břeclav','Jižní Morava','CZ','breclav')")
            db.execSQL("INSERT INTO places VALUES('two','Brno','Jižní Morava','CZ','brno')")
            db.execSQL("INSERT INTO places VALUES('three','Adamov','okres Brno','CZ','adamov brno')")
            db.execSQL("INSERT INTO place_names VALUES('one','Lundenburg','lundenburg')")
        }
        val source=AidReaderSource(File(store.root,"index")) {file}
        rule.setContent {VestigiaScriptoriumTheme {
            var value by remember {mutableStateOf("")}
            Column {GenealogyPlaceField("Obec",value,Copy(true),source) {value=it}}
        }}
        val input=rule.onNodeWithText("Obec")
        input.performClick().performTextInput("lunden")
        waitText("Břeclav")
        rule.onNode(hasClickAction() and hasText("Břeclav")).performClick()
        input.assertTextContains("Břeclav")
        input.performTextReplacement("Brno")
        rule.waitUntil(10000) {rule.onAllNodes(hasClickAction() and hasText("Brno") and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodes(hasClickAction() and !hasSetTextAction())[0].assert(hasText("Brno"))
        rule.onNode(hasClickAction() and hasText("Břeclav") and !hasSetTextAction()).assertDoesNotExist()
        rule.onNode(hasClickAction() and hasText("Brno") and !hasSetTextAction()).performClick()
        input.assertTextContains("Brno")
    }
    @Test fun evidenceUsesThreeColumnsAndMoreInLandscape()=isolated {_,controller ->
        val key=picture(controller.media)
        val docs=(1..7).map {Evidence(person="person",caption="Citace $it",note="Poznámka",file="managed:$key",mime="image/png")}
        val landscape=mutableStateOf(false)
        rule.setContent {
            val config=Configuration(LocalConfiguration.current).apply {screenWidthDp=if(landscape.value)900 else 400;screenHeightDp=if(landscape.value)400 else 900}
            CompositionLocalProvider(LocalConfiguration provides config) {VestigiaScriptoriumTheme {
                Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {EvidenceGallery(docs,controller.media,Copy(true),{})}
            }}
        }
        fun bounds(i: Int)=rule.onNodeWithTag("evidence-tile-${docs[i].id}").fetchSemanticsNode().boundsInWindow
        Assert.assertEquals(bounds(0).top,bounds(2).top,2f)
        Assert.assertTrue(bounds(3).top>bounds(0).top)
        rule.runOnIdle {landscape.value=true}
        Assert.assertEquals(bounds(0).top,bounds(3).top,2f)
        Assert.assertTrue(bounds(4).top>bounds(0).top)
    }
    @Test fun landscapeCentersGraphAndKeepsPersonInSidePanel()=isolated {store,controller ->
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        try {
            rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
            val father=Person(givenName="Otec",x=-250f);val child=Person(givenName="Dítě",x=800f)
            val key=picture(controller.media)
            val documents=(1..5).map {Evidence(person=father.id,caption="Doklad $it",file="managed:$key",mime="image/png")}
            val tree=GenealogyEdits.relate(FamilyTree(title="Na šířku",people=listOf(father,child),evidence=documents),child.id,father.id,Relative.FATHER)
            store.save(tree);rule.runOnIdle {controller.open(tree.id)}
            rule.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
            waitTag("genealogy-node-${father.id}")
            rule.waitUntil(10000) {controller.state.value.view.zoom<.99f}
            rule.waitForIdle()
            fun assertCentered() {
                val graph=rule.onNodeWithContentDescription("Graf vazeb. Posun prstem, přiblížení dvěma prsty.").fetchSemanticsNode().boundsInWindow
                val a=rule.onNodeWithTag("genealogy-node-${father.id}").fetchSemanticsNode().boundsInWindow
                val b=rule.onNodeWithTag("genealogy-node-${child.id}").fetchSemanticsNode().boundsInWindow
                Assert.assertEquals("view=${controller.state.value.view}; graph=$graph; a=$a; b=$b",graph.center.x,(minOf(a.left,b.left)+maxOf(a.right,b.right))/2,8f)
                Assert.assertEquals(graph.center.y,(minOf(a.top,b.top)+maxOf(a.bottom,b.bottom))/2,8f)
            }
            val contextBefore=InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap ->File(contextBefore.cacheDir,"genealogy-landscape-before.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
            assertCentered()
            val beforeZoom=controller.state.value.view.zoom
            rule.onNodeWithTag("genealogy-node-${father.id}").performClick()
            rule.onNodeWithTag("genealogy-side-panel").assertIsDisplayed()
            rule.waitUntil(10000) {controller.state.value.view.zoom<beforeZoom-.01f}
            rule.waitForIdle();android.os.SystemClock.sleep(350)
            assertCentered()
            rule.onNodeWithTag("evidence-tile-${documents.first().id}").performScrollTo()
            val first=rule.onNodeWithTag("evidence-tile-${documents.first().id}").fetchSemanticsNode().boundsInWindow
            val fourth=rule.onNodeWithTag("evidence-tile-${documents[3].id}").fetchSemanticsNode().boundsInWindow
            Assert.assertEquals(first.top,fourth.top,2f)
            rule.waitForIdle();android.os.SystemClock.sleep(500)
            rule.onNodeWithText("Upravit").performClick()
            rule.onNodeWithText("Jméno").performScrollTo().performTextReplacement("Upravený otec")
            rule.onNodeWithText("Obec narození").performScrollTo().performTextInput("Břeclav")
            rule.onNodeWithText("Jméno").assertTextContains("Upravený otec")
            rule.onNodeWithText("Obec narození").assertTextContains("Břeclav")
            val capture=InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap ->File(capture.cacheDir,"genealogy-landscape-edit.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
            rule.onNodeWithText("Uložit osobu").assertIsDisplayed().assertIsEnabled().performClick()
            try {rule.waitUntil(10000) {controller.state.value.tree?.people?.any {it.id==father.id && it.givenName=="Upravený otec"}==true && !controller.state.value.busy}}
            catch(e: Exception) {throw AssertionError("Save state: ${controller.state.value}",e)}
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap ->File(context.cacheDir,"genealogy-landscape-test.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        } finally {rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}}
    }
    private fun picture(media: GenealogyMedia): String {
        val bitmap=android.graphics.Bitmap.createBitmap(80,100,android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(36,71,92))
        val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
        bitmap.recycle();return media.add(bytes.inputStream())
    }
    private fun waitTag(tag: String) {rule.waitUntil(10000) {rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun waitText(text: String) {rule.waitUntil(10000) {rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
}
