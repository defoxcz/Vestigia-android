package com.vestigiascriptorium.android.genealogy

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GenealogyDeviceTest {
    @Test fun reopenPreservesRelationshipsEvidenceAndViewAndRejectsInvalidSave() {
        isolated {root ->
            val p=Person(name="Anna",note="Poznámka",x=-200f,y=300f);val father=Person(name="Jan")
            val tree=GenealogyEdits.relate(FamilyTree(title="Zkušební rod",people=listOf(p,father)),p.id,father.id,Relative.FATHER).let {it.copy(evidence=listOf(Evidence(person=p.id,caption="Matrika 12, str. 45",note="Citace bez souboru")))}
            val view=GraphView(-25f,43f,.75f,p.id)
            GenealogyStore(root).use {store ->
                store.save(tree);store.last(tree.id);store.view(tree.id,view)
                try {store.save(tree.copy(people=emptyList()));fail("Must reject dangling roles")} catch(_: GenealogyProblem) {}
            }
            GenealogyStore(root).use {store ->assertEquals(tree,store.read(tree.id));assertEquals(tree.id,store.last());assertEquals(view,store.view(tree.id));assertEquals(1,store.list().size)}
        }
    }
    @Test fun undoRedoArePersistedAndFailedRelationshipDoesNotEnterHistory()=runBlocking {
        isolated {root ->
            GenealogyStore(root).use {store ->
                val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
                try {
                    val controller=GenealogyController(store,scope)
                    suspend fun waitFor(test: (GenealogyState)->Boolean)=withTimeout(10_000) {controller.state.first {it.ready && !it.busy && test(it)}}
                    waitFor {it.tree==null};controller.create("Rodina");val created=waitFor {it.tree!=null}.tree!!
                    val p=Person(name="Anna");val q=Person(name="Jan")
                    controller.edit {it.copy(people=listOf(p,q))};waitFor {it.revision==1L}
                    controller.edit {GenealogyEdits.relate(it,p.id,q.id,Relative.FATHER)};val linked=waitFor {it.revision==2L}.tree!!
                    controller.edit {GenealogyEdits.relate(it,q.id,p.id,Relative.FATHER)};assertEquals("cycle",waitFor {it.error!=null}.error)
                    assertEquals(linked,store.read(created.id));controller.clearError()
                    controller.undo();assertTrue(waitFor {it.revision==3L}.tree!!.events.isEmpty());assertTrue(store.read(created.id).events.isEmpty())
                    controller.redo();assertEquals(linked,waitFor {it.revision==4L}.tree);assertEquals(linked,store.read(created.id))
                    controller.closeTree();waitFor {it.tree==null};controller.open(created.id);assertEquals(linked,waitFor {it.tree!=null}.tree)
                } finally {scope.cancel()}
            }
        }
    }
    private inline fun isolated(block: (File)->Unit) {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"genealogy-test-${newId()}").apply {mkdirs()}
        try {block(root)} finally {root.deleteRecursively()}
    }
}
