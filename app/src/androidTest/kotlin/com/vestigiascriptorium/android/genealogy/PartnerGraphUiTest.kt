package com.vestigiascriptorium.android.genealogy

import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
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
class PartnerGraphUiTest {
    @get:Rule val rule=createComposeRule()
    @Before fun unlocked() {Assert.assertFalse("Unlock phone for graph UI tests",InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)}
    @Test fun partnershipIsDirectAndWeddingCanBeEditedWithoutCreatingABox() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"partner-ui-${newId()}")
        val store=GenealogyStore(root)
        val a=Person(id="jan",givenName="Jan",surname="Novák",x=0f,y=0f)
        val b=Person(id="anna",givenName="Anna",surname="Nováková",x=350f,y=0f)
        val relation=LifeEvent(id="union",kind="genealogy.spousal-record",roles=mapOf("subject" to a.id,"subject2" to b.id),x=10000f,y=10000f)
        val tree=FamilyTree(title="Partnerské vazby",people=listOf(a,b),events=listOf(relation))
        store.save(tree);store.last(tree.id)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val controller=GenealogyController(store,scope)
        try {
            rule.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
            rule.waitUntil(10_000) {rule.onAllNodesWithTag("partner-link-union").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithContentDescription("Přizpůsobit").performClick()
            rule.onNodeWithTag("genealogy-node-union").assertDoesNotExist()
            rule.onNodeWithText("Partnerský vztah").assertDoesNotExist()
            rule.onNodeWithText("Partner: Anna Nováková").assertExists()
            rule.onNodeWithTag("partner-link-union").performTouchInput {click(center)}
            rule.onNodeWithText("Typ události: Partnerský vztah").assertDoesNotExist()
            rule.onNodeWithText("Upravit").performClick()
            rule.onNodeWithText("Typ události: Partnerský vztah").performScrollTo().performClick()
            rule.onNodeWithText("Sňatek").performClick()
            rule.onNode(hasSetTextAction() and hasText("Datum svatby")).performScrollTo().performTextInput(GenealogyDates.format("1845-02-03"))
            rule.onNodeWithText("Uložit událost").performClick()
            rule.waitUntil(10_000) {controller.state.value.tree?.events?.single()?.date=="1845-02-03" && !controller.state.value.busy}
            rule.waitUntil(10_000) {rule.onAllNodesWithText("Uložit událost").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithTag("genealogy-node-union").assertDoesNotExist()
            rule.onAllNodesWithText("Svatba: "+GenealogyDates.format("1845-02-03")).assertCountEquals(2)
            Assert.assertEquals("genealogy.marriage",store.read(tree.id).events.single().kind)
            val before=controller.state.value.view.x
            rule.onNodeWithContentDescription("Graf vazeb. Posun prstem, přiblížení dvěma prsty.").performTouchInput {swipeLeft()}
            rule.waitUntil(5_000) {controller.state.value.view.x<before}
        } finally {scope.cancel();store.close();root.deleteRecursively()}
    }
    @Test fun personBoxShowsYearsMentionFallbackAndEveryNumberedPartner() {
        val a=Person(id="a",givenName="Jan",surname="Novák",x=10f,y=10f)
        val b=Person(id="b",givenName="Anna",x=800f)
        val c=Person(id="c",givenName="Marie",x=10f,y=350f,mentionedFrom="1830",mentionedTo="1880")
        val tree=FamilyTree(title="Rod",people=listOf(a,b,c),events=listOf(
            LifeEvent(kind="genealogy.birth",date="1800-03-04",roles=mapOf("subject" to a.id),x=800f,y=100f),
            LifeEvent(kind="genealogy.death",date="1880-05-06",roles=mapOf("subject" to a.id),x=800f,y=250f),
            LifeEvent(kind="genealogy.marriage",date="1825",roles=mapOf("subject" to a.id,"subject2" to b.id)),
            LifeEvent(kind="genealogy.marriage",date="1850",roles=mapOf("subject" to a.id,"subject2" to c.id))))
        val camera=mutableStateOf(GraphView())
        rule.setContent {VestigiaScriptoriumTheme {GenealogyGraph(tree,camera.value,Copy(true),{camera.value=it}, {}, {_,_,_ ->})}}
        rule.onNodeWithText("* 1800  + 1880").assertIsDisplayed()
        rule.onNodeWithText("Partner: Anna").assertIsDisplayed()
        rule.onNodeWithText("2. partner: Marie").assertIsDisplayed()
        rule.onAllNodesWithText("Svatba: 1850")[0].assertIsDisplayed()
        rule.onNodeWithText("1830–1880").assertIsDisplayed()
        val last=rule.onAllNodesWithText("Svatba: 1850",useUnmergedTree=true)[0].getUnclippedBoundsInRoot()
        val reference=rule.onNodeWithText("Partner: Anna",useUnmergedTree=true).getUnclippedBoundsInRoot()
        Assert.assertTrue("Last wedding row is clipped: $last vs $reference",last.bottom-last.top>=reference.bottom-reference.top-1.dp)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap ->File(context.cacheDir,"partner-box-test.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        rule.runOnIdle {camera.value=camera.value.copy(zoom=3f)}
        val zoomed=rule.onNodeWithTag("genealogy-node-a",useUnmergedTree=true).getUnclippedBoundsInRoot()
        Assert.assertTrue("Zoomed card must retain its world width",zoomed.right-zoomed.left>700.dp)
        val zoomedLast=rule.onAllNodesWithText("Svatba: 1850",useUnmergedTree=true)[0].getUnclippedBoundsInRoot()
        val zoomedReference=rule.onNodeWithText("Partner: Anna",useUnmergedTree=true).getUnclippedBoundsInRoot()
        Assert.assertTrue("Last row must also fit at 3x zoom",zoomedLast.bottom-zoomedLast.top>=zoomedReference.bottom-zoomedReference.top-1.dp)
    }
}
