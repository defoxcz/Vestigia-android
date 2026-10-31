package com.vestigiascriptorium.android.genealogy

import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal class GenealogyMedia(val root: File) {
    init {check(root.isDirectory || root.mkdirs())}
    fun file(key: String): File?=key.takeIf {it.matches(Regex("[a-f0-9]{64}\\.(img|bin)"))}?.let {File(root,it)}?.takeIf {it.isFile}
    fun importImage(resolver: android.content.ContentResolver,uri: android.net.Uri,cancel: ()->Unit={}): String {
        try {
            return add(resolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(),cancel)
        } catch(original: java.io.FileNotFoundException) {
            // Some cloud providers export an image representation instead of a raw file.
            val asset=resolver.openTypedAssetFileDescriptor(uri,"image/*",null) ?: throw original
            return asset.use {add(it.createInputStream(),cancel)}
        }
    }
    fun validateImageReference(resolver: android.content.ContentResolver,uri: android.net.Uri) {
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        try {
            (resolver.openInputStream(uri) ?: throw java.io.FileNotFoundException()).use {BitmapFactory.decodeStream(it,null,bounds)}
        } catch(original: java.io.FileNotFoundException) {
            val asset=resolver.openTypedAssetFileDescriptor(uri,"image/*",null) ?: throw original
            asset.use {it.createInputStream().use {input ->BitmapFactory.decodeStream(input,null,bounds)}}
        }
        if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth.toLong()*bounds.outHeight>220_000_000)throw GenealogyProblem("image")
    }
    fun imageMime(key: String): String=file(key)?.let {source ->
        android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(source.path,this)}.outMimeType
    } ?: "image/*"
    fun addDocument(input: InputStream,cancel: ()->Unit={}): String {
        val temp=File.createTempFile("document-",".tmp",root)
        try {
            val digest=MessageDigest.getInstance("SHA-256");var total=0L
            input.use {source ->temp.outputStream().use {target ->val bytes=ByteArray(65536);while(true) {cancel();val n=source.read(bytes);if(n<0)break;total+=n;if(total>32L*1024*1024)throw GenealogyProblem("imageSize");target.write(bytes,0,n);digest.update(bytes,0,n)}}}
            val key=digest.digest().joinToString("") {"%02x".format(it)}+".bin"
            val destination=File(root,key);if(!destination.exists())check(temp.renameTo(destination));return key
        } finally {temp.delete()}
    }
    /** Originals are never edited. Immutable copies are retained for undo and shared uses. */
    fun add(input: InputStream,cancel: ()->Unit={}): String {
        val temp=File.createTempFile("portrait-",".tmp",root)
        try {
            val hash=MessageDigest.getInstance("SHA-256")
            var total=0L
            input.use {source ->temp.outputStream().use {target ->
                val buffer=ByteArray(65536)
                while(true) {cancel();val n=source.read(buffer);if(n<0)break;total+=n
                    if(total>32L*1024*1024)throw GenealogyProblem("imageSize")
                    target.write(buffer,0,n);hash.update(buffer,0,n)
                }
            }}
            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeFile(temp.path,bounds)
            if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth.toLong()*bounds.outHeight>220_000_000)throw GenealogyProblem("image")
            val key=hash.digest().joinToString("") {"%02x".format(it)}+".img"
            val destination=File(root,key)
            if(!destination.exists())check(temp.renameTo(destination))
            return key
        } finally {temp.delete()}
    }
}

internal fun InputStream.readPrefix(count: Int): ByteArray {
    val bytes=ByteArray(count);var read=0
    while(read<count) {val n=read(bytes,read,count-read);if(n<0)break;read+=n}
    return bytes.copyOf(read)
}
