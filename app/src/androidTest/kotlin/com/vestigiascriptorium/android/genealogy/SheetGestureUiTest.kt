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
    @Test fun fastUpwardFlingsSettleAtOneStableExpandedPosition() {
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
            rule.onNodeWithText(p.name).performClick()
            val panel=rule.onNodeWithTag("genealogy-editor-panel")
            val initial=panel.fetchSemanticsNode().boundsInWindow.top
            panel.performTouchInput {swipe(Offset(width/2f,height*.2f),Offset(width/2f,0f),80)}
            rule.waitForIdle()
            val expanded=panel.fetchSemanticsNode().boundsInWindow.top
            Assert.assertTrue("Fast swipe must expand the panel",expanded<initial-100f)
            repeat(3) {
                panel.performTouchInput {swipe(Offset(width/2f,height*.2f),Offset(width/2f,0f),60)}
                rule.waitForIdle()
                Assert.assertEquals(expanded,panel.fetchSemanticsNode().boundsInWindow.top,2f)
            }
            repeat(12) {
                rule.mainClock.advanceTimeBy(100)
                Assert.assertEquals(expanded,panel.fetchSemanticsNode().boundsInWindow.top,2f)
            }
            rule.onNodeWithText("Upravit").performClick()
            rule.onNodeWithText("Jméno").performTextReplacement("Změněný návrh")
            panel.performTouchInput {swipe(Offset(width/2f,80f),Offset(width/2f,0f),60)}
            rule.waitForIdle()
            rule.onNodeWithText("Jméno").assertTextContains("Změněný návrh")
            rule.onNodeWithText("Zavřít").performClick()
            rule.onNodeWithText("Zahodit změny?").assertExists()
        } finally {scope.cancel();store.close();root.deleteRecursively()}
    }
}
