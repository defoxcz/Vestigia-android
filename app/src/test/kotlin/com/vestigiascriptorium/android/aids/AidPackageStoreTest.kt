package com.vestigiascriptorium.android.aids

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AidPackageStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private fun bytes(value: Int=7)=ByteArray(4096) { value.toByte() }
    private fun zip(data: ByteArray,name: String="test.db"): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> zip.putNextEntry(ZipEntry(name));zip.write(data);zip.closeEntry() }
    }.toByteArray()
    private fun meta(zip: ByteArray,id: String="test",data: ByteArray=bytes())=AidMetadata(id,"test.db",zip.size.toLong(),data.size.toLong(),MessageDigest.getInstance("SHA-256").digest(zip).joinToString("") { "%02x".format(it) })
    private fun expectFailure(action: () -> Unit) { try { action();fail("Expected failure") } catch(_: java.io.IOException) { } }
    @Test fun installsVerifiedVersionAndUninstallsOnlyItsOwnFiles() {
        val root=temp.newFolder("packages");val store=AidPackageStore(root)
        val archive=zip(bytes());val first=meta(archive);val other=meta(archive,"other")
        store.install(first,{ByteArrayInputStream(archive)},{file,_ -> assertArrayEquals(bytes(),file.readBytes())})
        store.install(other,{ByteArrayInputStream(archive)},{_,_ ->})
        val research=temp.newFile("family-tree.json").apply { writeText("personal research") }
        assertEquals(first,store.installed("test"))
        store.uninstall("test")
        assertNull(store.installed("test"));assertEquals(other,store.installed("other"))
        assertEquals("personal research",research.readText())
        store.uninstall("test") // Idempotent; re-download remains possible.
        store.install(first,{ByteArrayInputStream(archive)},{_,_ ->})
        assertEquals(first,store.installed("test"))
    }
    @Test fun badHashCannotReplaceInstalledVersion() {
        val store=AidPackageStore(temp.newFolder());val firstZip=zip(bytes());val first=meta(firstZip)
        store.install(first,{ByteArrayInputStream(firstZip)},{_,_ ->})
        val secondZip=zip(bytes(8));val bad=meta(secondZip,data=bytes(8)).copy(sha256="a".repeat(64))
        expectFailure { store.install(bad,{ByteArrayInputStream(secondZip)},{_,_ ->}) }
        assertEquals(first,store.installed("test"));assertArrayEquals(bytes(),store.database("test")!!.readBytes())
    }
    @Test fun traversalAndUnexpectedEntriesAreRejected() {
        val root=temp.newFolder();val store=AidPackageStore(root)
        val archive=zip(bytes(),"../escaped.db")
        expectFailure { store.install(meta(archive),{ByteArrayInputStream(archive)},{_,_ ->}) }
        assertFalse(File(root,"escaped.db").exists());assertNull(store.installed("test"))
        assertThrows(IllegalArgumentException::class.java) { store.uninstall("../project") }
    }
    @Test fun ignoresOnlyRecognisedMacResourceForkWithoutExtractingIt() {
        val root=temp.newFolder();val store=AidPackageStore(root)
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("test.db"));zip.write(bytes());zip.closeEntry()
            zip.putNextEntry(ZipEntry("__MACOSX/._test.db"));zip.write(byteArrayOf(1,2,3));zip.closeEntry()
        }
        val archive=output.toByteArray()
        store.install(meta(archive),{ByteArrayInputStream(archive)},{_,_ ->})
        assertNotNull(store.installed("test"))
        assertTrue(root.walkTopDown().none { it.name=="__MACOSX" })
    }
    @Test fun wrongExpandedSizeIsRejected() {
        val store=AidPackageStore(temp.newFolder());val archive=zip(bytes())
        expectFailure { store.install(meta(archive).copy(size=8192),{ByteArrayInputStream(archive)},{_,_ ->}) }
        assertNull(store.installed("test"))
    }
    @Test fun validationFailureAndCancellationPreserveOldDatabase() {
        val store=AidPackageStore(temp.newFolder());val firstZip=zip(bytes());val first=meta(firstZip)
        store.install(first,{ByteArrayInputStream(firstZip)},{_,_ ->})
        val secondZip=zip(bytes(9));val second=meta(secondZip,data=bytes(9))
        expectFailure { store.install(second,{ByteArrayInputStream(secondZip)},{_,_ -> throw AidFailure("unsupported")}) }
        assertEquals(first,store.installed("test"))
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            store.install(second,{ByteArrayInputStream(secondZip)},{_,_ ->},check={throw java.util.concurrent.CancellationException()})
        }
        assertEquals(first,store.installed("test"))
    }
    @Test fun truncatedArchiveNeverPublishesAndStagingIsCleaned() {
        val root=temp.newFolder();val store=AidPackageStore(root);val archive=zip(bytes())
        expectFailure { store.install(meta(archive),{ByteArrayInputStream(archive.copyOf(archive.size-2))},{_,_ ->}) }
        assertNull(store.installed("test"))
        assertTrue(File(root,"test").listFiles()!!.none { it.name.startsWith("stage-") })
    }
    @Test fun storageDiagnosticsAreReadOnlyAndIdentifyIncompletePackages() {
        val root=temp.newFolder();val store=AidPackageStore(root)
        assertTrue(store.storageStatus("test").startsWith("directory-missing"))
        val archive=zip(bytes());val metadata=meta(archive)
        store.install(metadata,{ByteArrayInputStream(archive)},{_,_ ->})
        val before=store.database("test")!!.readBytes()
        assertTrue(store.storageStatus("test").startsWith("installed"))
        assertArrayEquals(before,store.database("test")!!.readBytes())
        File(root,"test/current").delete()
        assertTrue(store.storageStatus("test").startsWith("pointer-missing-or-unreadable"))
        assertTrue(File(root,"test/versions/${metadata.sha256}/test.db").isFile)
    }

}
