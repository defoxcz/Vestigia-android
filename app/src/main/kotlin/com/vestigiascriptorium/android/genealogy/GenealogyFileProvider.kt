package com.vestigiascriptorium.android.genealogy

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Read-only access to one explicitly granted managed attachment, never a filesystem path. */
class GenealogyFileProvider: ContentProvider() {
    override fun onCreate()=true
    private fun file(uri: Uri)=uri.pathSegments.singleOrNull()?.let {key ->GenealogyMedia(File(requireNotNull(context).noBackupFilesDir,"genealogy/media")).file(key)} ?: throw java.io.FileNotFoundException()
    override fun openFile(uri: Uri,mode: String): ParcelFileDescriptor {
        require(mode=="r");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri): String {
        val f=file(uri)
        if(f.extension=="img")return android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(f.path,this)}.outMimeType ?: "image/*"
        return if(f.inputStream().use {it.readPrefix(5)}.contentEquals("%PDF-".toByteArray()))"application/pdf" else "application/octet-stream"
    }
    override fun query(uri: Uri,projection: Array<out String>?,selection: String?,selectionArgs: Array<out String>?,sortOrder: String?): Cursor {
        val f=file(uri);val columns=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {addRow(columns.map {when(it){OpenableColumns.DISPLAY_NAME->f.name;OpenableColumns.SIZE->f.length();else->null}})}
    }
    override fun insert(uri: Uri,values: ContentValues?): Uri?=throw UnsupportedOperationException()
    override fun delete(uri: Uri,selection: String?,selectionArgs: Array<out String>?)=throw UnsupportedOperationException()
    override fun update(uri: Uri,values: ContentValues?,selection: String?,selectionArgs: Array<out String>?)=throw UnsupportedOperationException()
    companion object {fun uri(context: Context,key: String): Uri=Uri.Builder().scheme("content").authority(context.packageName+".genealogy-media").appendPath(key).build()}
}
