package com.vestigiascriptorium.android.genealogy

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ImportDeviceTest {
    @Test fun gedzipImportsPortraitsAndKeepsAnOriginalCopyWithoutOverwritingTrees()=isolated {root ->
        val source=File(root,"family.gdz")
        val image=image()
        val ged="0 HEAD\n1 GEDC\n2 VERS 7.0\n0 @I@ INDI\n1 NAME Anna Marie /Nováková/\n1 OBJE\n2 FILE media/portr%C3%A9t.png\n0 TRLR\n"
        ZipOutputStream(source.outputStream()).use {zip ->mapOf("gedcom.ged" to ged.toByteArray(),"media/portrét.png" to image).forEach {(name,bytes) ->zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}}
        val stage=File(root,"stage").apply {mkdirs()}
        val preview=GenealogyImporter.prepareFile(source,"Rod",stage)
        val person=preview.data.trees.single().people.single()
        assertEquals("Marie",person.middleName);assertTrue(person.portrait.isNotEmpty())
        GenealogyStore(File(root,"library")).use {store ->
            val existing=FamilyTree(title="Původní rod");store.save(existing)
            val media=GenealogyMedia(File(store.root,"media"))
            val imported=GenealogyImporter.commit(preview,setOf(preview.data.trees.single().id),store,media).single()
            assertEquals(existing,store.read(existing.id));assertEquals(2,store.list().size)
            assertTrue(media.file(person.portrait)!!.readBytes().contentEquals(image))
            assertTrue(File(store.root,"sources/${imported.importSource}").readBytes().contentEquals(source.readBytes()))
        }
    }
    @Test fun zipTraversalAndInvalidDataCannotCreateAStoredTree()=isolated {root ->
        val source=File(root,"bad.gdz")
        ZipOutputStream(source.outputStream()).use {zip ->zip.putNextEntry(ZipEntry("../escape"));zip.write(byteArrayOf(1));zip.closeEntry()}
        try {GenealogyImporter.prepareFile(source,"Rod",File(root,"stage"));fail("Reject traversal")}catch(_: ImportProblem){}
        assertFalse(File(root.parentFile,"escape").exists())
        assertNull(GenealogyImporter.safePath("../../secret"));assertNull(GenealogyImporter.safePath("https://example.com/a.png"));assertNull(GenealogyImporter.safePath("media/%2e%2e/file"))
    }
    @Test fun desktopSqliteGraphRolesDatesPortraitsAndDocumentsAreTranslatedReadonly()=isolated {root ->
        val source=File(root,"export.vestigia")
        val before: ByteArray
        SQLiteDatabase.openOrCreateDatabase(source,null).use {db ->
            db.execSQL("CREATE TABLE project_metadata(key TEXT PRIMARY KEY,value TEXT)")
            db.execSQL("INSERT INTO project_metadata VALUES('formatVersion','10'),('name','Rod Nováků')")
            db.execSQL("CREATE TABLE project_nodes(id TEXT,display_name TEXT,position INTEGER,properties_json TEXT)")
            db.execSQL("CREATE TABLE multimedia_items(id TEXT,source_path TEXT,name TEXT,image_edit_recipe TEXT)")
            db.execSQL("INSERT INTO multimedia_items VALUES('photo','media/portrait.png','Portrét','')")
            val properties="""{"registerGraphItems":[{"id":"p1","type":"person","forename":"Jan Karel","surname":"Novák","multimediaId":"photo","x":50,"y":80},{"id":"p2","type":"person","forename":"Anna","surname":"Nováková","x":400,"y":80},{"id":"w","type":"event","eventType":"genealogy.marriage","date":"1840-02-03","subjectRoles":{"groom":"p1","bride":"p2"}}],"registerGraphRelations":[{"sourceId":"w","targetId":"p1","type":"event-subject"},{"sourceId":"w","targetId":"p2","type":"event-subject"}]}"""
            db.execSQL("INSERT INTO project_nodes VALUES('graph','Vazby',0,?)",arrayOf(properties))
        }
        before=source.readBytes()
        val preview=GenealogyImporter.prepareFile(source,"Rod",File(root,"stage").apply {mkdirs()},external={path ->if(path=="media/portrait.png")image().inputStream() else null})
        val tree=preview.data.trees.single()
        assertEquals("Karel",tree.people.first().middleName);assertEquals(50f,tree.people.first().x)
        assertEquals(mapOf("subject" to "p1","subject2" to "p2"),tree.events.single().roles)
        assertEquals("1840-02-03",tree.events.single().date);assertTrue(tree.people.first().portrait.isNotEmpty())
        assertTrue(before.contentEquals(source.readBytes()))
    }
    @Test fun batchSaveRollsBackCollisionsAndLargePayloadCanBeReopened()=isolated {root ->
        GenealogyStore(root).use {store ->
            val original=FamilyTree(title="Původní");store.save(original)
            val incoming=FamilyTree(title="Nový")
            try {store.saveNew(listOf(incoming,original));fail("Must not overwrite")}catch(_: Exception){}
            assertEquals(listOf(original.id),store.list().map {it.first})
            val big=FamilyTree(title="Velký",people=listOf(Person(givenName="Jan",note="x".repeat(5*1024*1024))))
            store.save(big);assertEquals(big,store.read(big.id))
        }
    }
    @Test fun portraitRemovalAndUndoKeepOwnedImageAvailable()=isolated {root ->
        val media=GenealogyMedia(File(root,"media"));val key=media.add(image().inputStream())
        val person=Person(givenName="Anna",portrait=key);val tree=FamilyTree(title="Rod",people=listOf(person))
        GenealogyStore(root).use {store ->
            store.save(tree);store.save(GenealogyEdits.person(tree,person.copy(portrait=""),"",""))
            assertTrue(store.read(tree.id).people.single().portrait.isEmpty());assertNotNull(media.file(key))
            store.save(tree);assertEquals(key,store.read(tree.id).people.single().portrait)
        }
        assertNull(media.file("../../secret"))
    }
    private fun image(): ByteArray {val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.GREEN);return java.io.ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle()}.toByteArray()}
    private inline fun isolated(action: (File)->Unit) {val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"import-test-${newId()}").apply {mkdirs()};try {action(root)}finally {root.deleteRecursively()}}
}
