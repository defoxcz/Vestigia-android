package com.vestigiascriptorium.android.aids

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Uses isolated test storage, never the user's installed library or research. */
@RunWith(AndroidJUnit4::class)
class AidDeviceTest {
    @Test fun realPublicPackagesInstallPersistAndUninstallOnDevice() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,"aid-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val store=AidPackageStore(File(root,"aids"))
            val bundled=context.assets.open("research-aids.json").bufferedReader().use { AidNetwork.parse(it.readText()) }
            val live=AidNetwork.catalog()
            val catalogue=bundled+live
            val first=catalogue.getValue("manuscript-abbreviations")
            val second=catalogue.getValue("historical-units")
            fun validate(file: File,meta: AidMetadata) {
                SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("PRAGMA quick_check",null).use { cursor -> assertTrue(cursor.moveToFirst());assertEquals("ok",cursor.getString(0)) }
                    if(meta.schemaVersion!=null) assertEquals(meta.schemaVersion,db.version)
                }
            }
            store.install(first,{AidNetwork.archive(first)},::validate)
            store.install(second,{AidNetwork.archive(second)},::validate)
            val reopened=AidPackageStore(File(root,"aids"))
            assertEquals(first,reopened.installed(first.id));assertEquals(second,reopened.installed(second.id))
            val personal=File(root,"family-tree.json").apply { writeText("preserve me") }
            reopened.uninstall(first.id)
            assertNull(reopened.installed(first.id));assertEquals(second,reopened.installed(second.id));assertEquals("preserve me",personal.readText())
            reopened.install(first,{AidNetwork.archive(first)},::validate)
            assertEquals(first,reopened.installed(first.id))
        } finally { root.deleteRecursively() }
    }
}

@RunWith(AndroidJUnit4::class)
class AidWorkerDeviceTest {
    @Test fun foregroundWorkerInstallsAndRepositoryUninstalls() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repository=AidRepository.get(context)
        val id="manuscript-abbreviations"
        org.junit.Assume.assumeTrue("Do not alter an existing user-installed package",repository.store.installed(id)==null)
        val previousWifi=repository.state.value.wifiOnly
        fun await(description: String,condition: () -> Boolean) {
            val deadline=System.currentTimeMillis()+120_000
            while(!condition()) {
                if(System.currentTimeMillis()>deadline) fail("Timeout: $description; ${repository.state.value}")
                Thread.sleep(300)
            }
        }
        androidx.test.core.app.ActivityScenario.launch(com.vestigiascriptorium.android.MainActivity::class.java).use {
            try {
                repository.wifiOnly(false)
                repository.download(id)
                await("worker completion") { repository.store.installed(id)!=null || repository.state.value.jobs[id]?.status=="error" }
                assertEquals("installed",repository.state.value.jobs[id]?.status)
                assertNotNull(repository.store.installed(id))
                repository.uninstall(id)
                await("uninstall completion") { repository.store.installed(id)==null && id !in repository.state.value.busy }
                assertNull(repository.state.value.installed[id])
                repository.download(id)
                await("reinstall completion") { repository.store.installed(id)!=null || repository.state.value.jobs[id]?.status=="error" }
                assertEquals("installed",repository.state.value.jobs[id]?.status)
            } finally {
                repository.uninstall(id)
                await("cleanup") { repository.store.installed(id)==null && id !in repository.state.value.busy }
                repository.wifiOnly(previousWifi)
            }
        }
    }
}
