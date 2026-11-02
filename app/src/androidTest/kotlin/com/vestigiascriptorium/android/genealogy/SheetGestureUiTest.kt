package com.vestigiascriptorium.android.genealogy

import androidx.compose.ui.geometry.Offset
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
class SheetGestureUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun graphRemainsInteractiveWithOpenPanel() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertFalse("Unlock phone",context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        val root=File(context.cacheDir,"panel-selection-${newId()}")
        val store=GenealogyStore(root)
        val first=Person(givenName="První osoba")
        val second=Person(givenName="Druhá osoba",x=300f)
        val tree=FamilyTree(title="Výběr",people=listOf(first,second))
        store.save(tree);store.last(tree.id)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val controller=GenealogyController(store,scope)
        try {
            rule.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
            rule.waitUntil(10000) {rule.onAllNodesWithTag("genealogy-node-${first.id}").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithTag("genealogy-node-${first.id}").performTouchInput {doubleClick()}
            rule.onNodeWithTag("genealogy-editor-panel").assertIsDisplayed()
            // Keep both boxes visible in the exposed top portion of the graph.
            rule.runOnIdle {controller.view(GraphView(x=10f,y=10f,zoom=.45f,selected=first.id))}
            val graph=rule.onNodeWithContentDescription("Graf vazeb. Posun prstem, přiblížení dvěma prsty.")
            val graphBounds=graph.fetchSemanticsNode().boundsInRoot
            val panelTop=rule.onNodeWithTag("genealogy-editor-panel").fetchSemanticsNode().boundsInRoot.top
            val card=rule.onNodeWithTag("genealogy-node-${second.id}").fetchSemanticsNode().boundsInRoot
            Assert.assertTrue("Second box must be above panel",card.center.y<panelTop)
            graph.performTouchInput {click(card.center-graphBounds.topLeft)}
            rule.runOnIdle {Assert.assertEquals(second.id,controller.state.value.view.selected)}
            rule.onNodeWithTag("genealogy-editor-panel").assertIsDisplayed()
            rule.onNodeWithText("Upravit").performClick()
            rule.onNodeWithText("Jméno").assertTextContains("Druhá osoba")
            graph.performTouchInput {click(Offset(5f,(panelTop-graphBounds.top)/2))}
            rule.onNodeWithTag("genealogy-editor-panel").assertIsDisplayed()
            val before=controller.state.value.view
            graph.performTouchInput {swipe(Offset(10f,20f),Offset(90f,30f),200)}
            rule.runOnIdle {Assert.assertTrue(controller.state.value.view.x>before.x)}
            rule.onNodeWithTag("genealogy-editor-panel").assertIsDisplayed()
        } finally {scope.cancel();store.close();root.deleteRecursively()}
    }
    @Test fun dragHandleKeepsTheChosenHeightWithoutFlingOrSnap() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertFalse("Unlock phone for sheet gesture test",context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        val root=File(context.cacheDir,"sheet-gesture-${newId()}")
        val store=GenealogyStore(root)
        val p=Person(givenName="Test panelu")
        val tree=FamilyTree(title="Gesta",people=listOf(p))
        store.save(tree);store.last(tree.id)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val controller=GenealogyController(store,scope)
        try {
            rule.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
            rule.waitUntil(10000) {rule.onAllNodesWithText(p.name).fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText(p.name).performTouchInput {longClick()}
            val panel=rule.onNodeWithTag("genealogy-editor-panel")
            val handle=rule.onNodeWithTag("genealogy-panel-handle")
            val initial=panel.fetchSemanticsNode().boundsInWindow.top
            handle.performTouchInput {swipe(Offset(width/2f,height/2f),Offset(width/2f,height/2f-160f),80)}
            rule.waitForIdle()
            val expanded=panel.fetchSemanticsNode().boundsInWindow.top
            val slop=android.view.ViewConfiguration.get(context).scaledTouchSlop
            Assert.assertTrue(initial-expanded in (160f-slop-4f)..164f)
            repeat(12) {
                rule.mainClock.advanceTimeBy(100)
                Assert.assertEquals(expanded,panel.fetchSemanticsNode().boundsInWindow.top,2f)
            }
            handle.performTouchInput {swipe(Offset(width/2f,height/2f),Offset(width/2f,height/2f+90f),60)}
            rule.waitForIdle()
            val lowered=panel.fetchSemanticsNode().boundsInWindow.top
            Assert.assertTrue(lowered-expanded in (90f-slop-4f)..94f)
            rule.onNodeWithText("Upravit").performClick()
            rule.onNodeWithText("Jméno").performTextReplacement("Změněný návrh")
            handle.performTouchInput {swipe(Offset(width/2f,height/2f),Offset(width/2f,height/2f-80f),60)}
            rule.waitForIdle()
            rule.onNodeWithText("Jméno").assertTextContains("Změněný návrh")
            rule.onNodeWithText("Zavřít").performClick()
            rule.onNodeWithText("Zahodit změny?").assertExists()
        } finally {scope.cancel();store.close();root.deleteRecursively()}
    }
}
