package com.vestigiascriptorium.android.aids

import java.io.File
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipFile
import kotlin.concurrent.withLock

internal class AidFailure(val code: String) : IOException(code)

internal data class AidMetadata(val id: String, val file: String, val archiveSize: Long, val size: Long, val sha256: String, val schemaVersion: Int? = null) {
    init {
        require(id.matches(Regex("[a-z0-9-]+")))
        require(file.matches(Regex("[a-z0-9_]+\\.db")))
        require(archiveSize in 22..1_000_000_000 && size in 512..4_000_000_000L)
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(schemaVersion == null || schemaVersion >= 0)
    }
}

/** Exclusively owns this root. No research projects or user documents belong here. */
internal class AidPackageStore(private val root: File) {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()
    private fun lock(id: String) = locks.getOrPut(id) { ReentrantLock() }
    private fun directory(id: String): File {
        require(id.matches(Regex("[a-z0-9-]+")))
        return File(root, id)
    }
    fun <T> exclusive(id: String, action: () -> T): T = lock(id).withLock(action)
    private fun writeMetadata(file: File, meta: AidMetadata) {
        val p = Properties().apply {
            setProperty("id",meta.id);setProperty("file",meta.file);setProperty("archiveSize",meta.archiveSize.toString())
            setProperty("size",meta.size.toString());setProperty("sha256",meta.sha256)
            meta.schemaVersion?.let { setProperty("schemaVersion",it.toString()) }
        }
        file.outputStream().use { p.store(it,null);it.fd.sync() }
    }
    private fun readMetadata(file: File): AidMetadata? = try {
        val p=Properties().apply { file.inputStream().use(::load) }
        AidMetadata(p.getProperty("id"),p.getProperty("file"),p.getProperty("archiveSize").toLong(),p.getProperty("size").toLong(),p.getProperty("sha256"),p.getProperty("schemaVersion")?.toInt())
    } catch (_: Exception) { null }
    fun installed(id: String): AidMetadata? = run {
        val dir=directory(id)
        val hash=runCatching { File(dir,"current").readText().trim() }.getOrNull()
        if(hash==null || !hash.matches(Regex("[a-f0-9]{64}"))) return@run null
        val version=File(File(dir,"versions"),hash)
        val meta=readMetadata(File(version,"metadata.properties")) ?: return@run null
        if(meta.id!=id || meta.sha256!=hash || File(version,meta.file).length()!=meta.size) null else meta
    }
    fun database(id: String): File? = installed(id)?.let { File(directory(id),"versions/${it.sha256}/${it.file}") }

    /** Bounded ZIP extraction; validate first, then atomically publish a version pointer. */
    fun install(meta: AidMetadata, source: () -> InputStream, validate: (File,AidMetadata) -> Unit,
                check: () -> Unit = {}, progress: (Long,String) -> Unit = {_,_ ->}): Unit = exclusive(meta.id) {
        check()
        root.mkdirs()
        if(root.usableSpace < meta.archiveSize + meta.size + 16L*1024*1024) throw AidFailure("space")
        val dir=directory(meta.id).apply { mkdirs() }
        // A process death may leave staging, but can never replace the installed version.
        dir.listFiles()?.filter { it.name.startsWith("stage-") }?.forEach { it.deleteRecursively() }
        val staging=File(dir,"stage-${UUID.randomUUID()}").apply { mkdirs() }
        val archive=File(staging,"download.zip")
        try {
            val digest=MessageDigest.getInstance("SHA-256")
            var received=0L
            source().use { input -> archive.outputStream().use { output ->
                val buffer=ByteArray(64*1024)
                while(true) {
                    check()
                    val count=input.read(buffer)
                    if(count<0) break
                    received+=count
                    if(received>meta.archiveSize) throw AidFailure("integrity")
                    output.write(buffer,0,count);digest.update(buffer,0,count);progress(received,"active")
                }
                output.fd.sync()
            }}
            if(received!=meta.archiveSize || digest.digest().joinToString("") { "%02x".format(it) }!=meta.sha256) throw AidFailure("integrity")
            progress(received,"verifying");check()
            val database=File(staging,meta.file)
            ZipFile(archive).use { zip ->
                val entries=zip.entries().toList()
                // Apple's ZIP creator adds resource-fork metadata. Never extract it.
                if(entries.size !in 1..3 || entries.any { entry ->
                    entry.name!=meta.file && !(entry.name=="__MACOSX/" && entry.isDirectory) &&
                        !(entry.name=="__MACOSX/._${meta.file}" && !entry.isDirectory && entry.size in 0..65536)
                }) throw AidFailure("archive")
                val databases=entries.filter { it.name==meta.file && !it.isDirectory }
                if(databases.size!=1) throw AidFailure("archive")
                val entry=databases.single()
                if(entry.size!=meta.size) throw AidFailure("integrity")
                val crc=java.util.zip.CRC32()
                var expanded=0L
                zip.getInputStream(entry).use { input -> database.outputStream().use { output ->
                    val buffer=ByteArray(64*1024)
                    while(true) {
                        check()
                        val count=input.read(buffer)
                        if(count<0) break
                        expanded+=count
                        if(expanded>meta.size) throw AidFailure("integrity")
                        output.write(buffer,0,count);crc.update(buffer,0,count)
                    }
                    output.fd.sync()
                }}
                if(expanded!=meta.size || crc.value!=entry.crc) throw AidFailure("integrity")
            }
            validate(database,meta);check()
            archive.delete()
            writeMetadata(File(staging,"metadata.properties"),meta)
            progress(received,"installing")
            val versions=File(dir,"versions").apply { mkdirs() }
            val version=File(versions,meta.sha256)
            if(!version.exists()) Files.move(staging.toPath(),version.toPath(),StandardCopyOption.ATOMIC_MOVE)
            // Revalidate an existing version too, before making it current.
            if(File(version,meta.file).length()!=meta.size) throw AidFailure("integrity")
            validate(File(version,meta.file),meta);check()
            val pointer=File(dir,"current.next")
            pointer.outputStream().use { it.write(meta.sha256.toByteArray());it.fd.sync() }
            Files.move(pointer.toPath(),File(dir,"current").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            versions.listFiles()?.filter { it.name!=meta.sha256 }?.forEach { it.deleteRecursively() }
        } finally { staging.deleteRecursively() }
    }
    /** Id is validated; operation cannot reach any sibling project or document directory. */
    fun uninstall(id: String): Unit = exclusive(id) {
        val dir=directory(id)
        if(dir.exists() && !dir.deleteRecursively()) throw AidFailure("storage")
    }
}
