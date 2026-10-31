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
    @Test fun formatThreeCopiesUpgradeAndFormatFourPortraitReferencesReload()=isolated {root ->
        val person=Person(givenName="Starší portrét",portrait="a".repeat(64)+".img")
        val tree=FamilyTree(title="Migrace",people=listOf(person))
        GenealogyStore(root).use {it.save(tree)}
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(File(root,"genealogy.sqlite"),null).use {db ->
            db.execSQL("UPDATE trees SET payload=replace(payload,?,?)",arrayOf("\"format\":4","\"format\":3"));db.version=3
        }
        GenealogyStore(root).use {store ->
            assertEquals(person,store.read(tree.id).people.single())
            store.save(tree.copy(people=listOf(person.copy(portrait="content://test-provider/document/portrait"))))
        }
        GenealogyStore(root).use {store ->
            assertEquals("content://test-provider/document/portrait",store.read(tree.id).people.single().portrait)
            assertEquals(tree.id,store.read(tree.id).id)
        }
    }
    @Test fun reopenPreservesRelationshipsEvidenceAndViewAndRejectsInvalidSave() {
        isolated {root ->
            val p=Person(givenName="Anna",note="Poznámka",x=-200f,y=300f);val father=Person(givenName="Jan")
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
                    val p=Person(givenName="Anna");val q=Person(givenName="Jan")
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
    @Test fun legacyV1NamesDatesRolesAndIdsSurviveAnEditAndV2Reopen() {
        isolated {root ->
            val legacy="""{"format":1,"id":"legacy-tree","title":"Starý rod","people":[{"id":"p","name":"Jan Karel de Novák","gender":"male","note":"Původní poznámka","x":12,"y":34}],"events":[{"id":"birth","kind":"genealogy.birth","title":"","date":"kolem 1820","place":"","note":"","roles":{"subject":"p"},"x":150,"y":80}],"evidence":[]}"""
            GenealogyStore(root).close()
            android.database.sqlite.SQLiteDatabase.openDatabase(File(root,"genealogy.sqlite").path,null,0).use {db ->
                db.execSQL("INSERT INTO trees(id,title,payload) VALUES(?,?,?)",arrayOf("legacy-tree","Starý rod",legacy))
                db.version=1
            }
            GenealogyStore(root).use {store ->
                val old=store.read("legacy-tree")
                assertEquals("Jan Karel de Novák",old.people.single().legacyName)
                val edited=GenealogyEdits.person(old,old.people.single().copy(note="Nová poznámka"),"kolem 1820","")
                store.save(edited)
            }
            GenealogyStore(root).use {store ->
                val reopened=store.read("legacy-tree")
                assertEquals("Jan Karel de Novák",reopened.people.single().name)
                assertEquals("p",reopened.people.single().id)
                assertEquals("kolem 1820",reopened.events.single().date)
                assertEquals(mapOf("subject" to "p"),reopened.events.single().roles)
                val split=GenealogyEdits.person(reopened,reopened.people.single().copy(givenName="Jan",middleName="Karel",surname="de Novák",mentionedFrom="1840",mentionedTo="1870-01-02"),"kolem 1820","")
                store.save(split)
                assertEquals("",store.read("legacy-tree").people.single().legacyName)
                assertEquals("Karel",store.read("legacy-tree").people.single().middleName)
            }
        }
    }
    private inline fun isolated(block: (File)->Unit) {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"genealogy-test-${newId()}").apply {mkdirs()}
        try {block(root)} finally {root.deleteRecursively()}
    }
}
