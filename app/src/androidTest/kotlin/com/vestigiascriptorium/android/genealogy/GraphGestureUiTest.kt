package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import org.junit.*
import java.io.File

class GraphGestureUiTest {
    @get:Rule val rule=createComposeRule()
    @Before fun unlocked() {Assert.assertFalse("Unlock phone for gesture tests",InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)}
    private val person=Person(id="person",givenName="Jan",gender="male",x=20f)
    private val tree=mutableStateOf(FamilyTree(title="Gesta",people=listOf(person)))
    private val view=mutableStateOf(GraphView(x=30f,y=30f,zoom=.8f))
    private var opened: String?=null
    private var tapped: String?=null
    private var moves=0
    private val graphDescription="Graf vazeb. Posun prstem, přiblížení dvěma prsty."
    private fun content(media: GenealogyMedia?=null,grouped: Boolean=false) {
        rule.setContent {VestigiaScriptoriumTheme {GenealogyGraph(tree.value,view.value,Copy(true),{view.value=it},{opened=it},{id,x,y ->
            moves++;tree.value=tree.value.copy(people=tree.value.people.map {if(it.id==id)it.copy(x=x,y=y) else it})
        },Modifier.fillMaxSize(),media=media,collapseWives=grouped,onTap={tapped=it})}}
        rule.waitForIdle()
    }
    private fun graph()=rule.onNodeWithContentDescription(graphDescription)
    private fun pointOnCard(): Offset {
        val card=rule.onNodeWithTag("genealogy-node-${person.id}").fetchSemanticsNode().boundsInRoot
        return card.center-graph().fetchSemanticsNode().boundsInRoot.topLeft
    }
    @Test fun shortTapDoesNotSelectAndSwipeOverCardPansOnly() {
        content();val point=pointOnCard();val before=view.value
        graph().performTouchInput {click(point)}
        rule.runOnIdle {Assert.assertNull(opened);Assert.assertNull(view.value.selected);Assert.assertEquals(0,moves)}
        graph().performTouchInput {swipe(point,point+Offset(120f,40f),200)}
        rule.runOnIdle {
            Assert.assertTrue(view.value.x>before.x);Assert.assertTrue(view.value.y>before.y)
            Assert.assertNull(view.value.selected);Assert.assertNull(opened)
            Assert.assertEquals(person.x,tree.value.people.single().x);Assert.assertEquals(0,moves)
        }
    }
    @Test fun doubleTapOpensPersonWithoutMovingBox() {
        content();val point=pointOnCard()
        graph().performTouchInput {doubleClick(point)}
        rule.runOnIdle {Assert.assertEquals(person.id,opened);Assert.assertEquals(0,moves)}
    }
    @Test fun tapsOnDifferentPeopleDoNotCountAsDoubleTap() {
        val other=Person(id="other",givenName="Anna",x=320f)
        tree.value=tree.value.copy(people=listOf(person,other))
        content()
        val first=pointOnCard()
        graph().performTouchInput {click(first)}
        val bounds=rule.onNodeWithTag("genealogy-node-${other.id}").fetchSemanticsNode().boundsInRoot
        val point=bounds.center-graph().fetchSemanticsNode().boundsInRoot.topLeft
        graph().performTouchInput {advanceEventTime(100);click(point)}
        rule.runOnIdle {Assert.assertEquals(other.id,tapped);Assert.assertNull(opened);Assert.assertEquals(0,moves)}
    }
    @Test fun holdOpensDetailsBeforeFingerIsReleased() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"hold-details-${newId()}")
        val store=GenealogyStore(root)
        val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Main.immediate)
        val controller=GenealogyController(store,scope)
        try {
            store.save(tree.value)
            rule.setContent {VestigiaScriptoriumTheme {GenealogyWorkspace(controller,Copy(true),{})}}
            rule.runOnIdle {controller.open(tree.value.id)}
            rule.waitUntil(10000) {rule.onAllNodesWithTag("genealogy-node-${person.id}").fetchSemanticsNodes().isNotEmpty()}
            val point=pointOnCard()
            graph().performTouchInput {down(point)}
            rule.waitUntil(5000) {rule.onAllNodesWithText("Upravit").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("Upravit").assertIsDisplayed()
            graph().performTouchInput {moveTo(point+Offset(140f,0f));up()}
            rule.waitUntil(10000) {controller.state.value.tree?.people?.single()?.x!=person.x && !controller.state.value.busy}
            Assert.assertTrue(store.read(tree.value.id).people.single().x>person.x)
            rule.onNodeWithText("Upravit").assertIsDisplayed()
        } finally {scope.coroutineContext[kotlinx.coroutines.Job]?.cancel();store.close();root.deleteRecursively()}
    }
    @Test fun longPressOpensPersonAndLongPressDragMovesOneBox() {
        content();val point=pointOnCard()
        graph().performTouchInput {longClick(point)}
        rule.runOnIdle {Assert.assertEquals(person.id,opened);Assert.assertEquals(person.id,view.value.selected);opened=null}
        val before=view.value
        graph().performTouchInput {down(point);advanceEventTime(700);moveTo(point+Offset(140f,0f));up()}
        rule.runOnIdle {
            Assert.assertEquals(1,moves);Assert.assertTrue(tree.value.people.single().x>person.x)
            Assert.assertEquals(person.y,tree.value.people.single().y)
            Assert.assertEquals(before.x,view.value.x);Assert.assertEquals(person.id,opened)
        }
    }
    @Test fun secondFingerCancelsPendingMoveAndZoomsInstead() {
        content();val point=pointOnCard()
        val other=Offset(graph().fetchSemanticsNode().boundsInRoot.width*.85f,point.y+80f)
        val before=view.value.zoom
        graph().performTouchInput {
            down(0,point);advanceEventTime(700);moveTo(0,point+Offset(70f,0f))
            down(1,other)
            moveTo(0,point+Offset(20f,0f));moveTo(1,other+Offset(130f,0f))
            up(1);up(0)
        }
        rule.runOnIdle {
            Assert.assertTrue(view.value.zoom>before);Assert.assertEquals(0,moves)
            Assert.assertEquals(person.x,tree.value.people.single().x);Assert.assertEquals(person.id,opened)
        }
    }
    @Test fun pinchWorksAcrossCardPortraitAndGroupedWifeName() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"graph-gestures-${newId()}")
        val media=GenealogyMedia(root)
        val bitmap=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val portrait=media.add(bytes.inputStream())
        val wife=Person(id="wife",givenName="Anna",gender="female",portrait=portrait,x=350f)
        tree.value=GenealogyEdits.relate(FamilyTree(title="Pár",people=listOf(person.copy(portrait=portrait),wife)),person.id,wife.id,Relative.SPOUSE)
        try {
            content(media,true)
            for(target in listOf("card","portrait","wife")) {
                rule.runOnIdle {view.value=GraphView(x=30f,y=30f,zoom=.8f)}
                val graphBounds=graph().fetchSemanticsNode().boundsInRoot
                val rect=when(target) {
                    "portrait" ->rule.onNodeWithContentDescription("Portrét: Jan").fetchSemanticsNode().boundsInRoot
                    "wife" ->rule.onNodeWithContentDescription("Otevřít osobu: Anna").fetchSemanticsNode().boundsInRoot
                    else ->rule.onNodeWithTag("genealogy-node-${person.id}").fetchSemanticsNode().boundsInRoot
                }
                val first=rect.center-graphBounds.topLeft
                val second=Offset(graphBounds.width*.85f,first.y)
                val before=view.value.zoom
                graph().performTouchInput {pinch(first,second,first-Offset(50f,0f),second+Offset(90f,0f),300)}
                rule.runOnIdle {
                    Assert.assertTrue("Pinch over $target",view.value.zoom>before+.02f)
                    Assert.assertEquals(0,moves);Assert.assertNull(opened);Assert.assertNull(view.value.selected)
                }
            }
            rule.runOnIdle {view.value=GraphView(x=30f,y=30f,zoom=.8f)}
            rule.onNodeWithContentDescription("Otevřít osobu: Anna").performTouchInput {longClick()}
            rule.runOnIdle {Assert.assertEquals(wife.id,opened)}
        } finally {root.deleteRecursively()}
    }
}
