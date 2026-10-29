package com.vestigiascriptorium.android.genealogy

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.*
import java.net.URI
import java.util.zip.CRC32
import java.util.zip.ZipFile

internal data class ImportPreview(val root: File,val source: File,val data: GenealogyImportData)
internal object GenealogyImporter {
    fun prepare(resolver: ContentResolver,uri: Uri,title: String,folder: Uri?,cache: File,cancel: ()->Unit={}): ImportPreview {
        val root=File(cache,"genealogy-import-${newId()}").apply {check(mkdirs())}
        try {
            val source=File(root,"source")
            (resolver.openInputStream(uri) ?: throw ImportProblem("Soubor nelze otevřít.")).use {input ->source.outputStream().use {output ->copy(input,output,256L*1024*1024,cancel)}}
            return prepareFile(source,title,root,{path ->if(folder==null)null else folderFile(resolver,folder,path)},cancel)
        } catch(e: Throwable) {root.deleteRecursively();throw e}
    }
    /** Used by isolated tests too; never opens or alters the supplied source file. */
    fun prepareFile(source: File,title: String,root: File,external: (String)->InputStream?={null},cancel: ()->Unit={}): ImportPreview {
        val magic=source.inputStream().use {it.readPrefix(16)}
        val zip=if(magic.size>=4 && magic[0]=='P'.code.toByte() && magic[1]=='K'.code.toByte()) try {ZipFile(source)} catch(_: IOException) {throw ImportProblem("GEDZIP je poškozený, šifrovaný nebo obsahuje neplatné cesty.")} else null
        try {
            if(zip!=null)validateZip(zip)
            val parsed=when {
                magic.contentEquals("SQLite format 3\u0000".toByteArray())->VestigiaImport.read(source,cancel)
                zip!=null -> {
                    val entry=zip.getEntry("gedcom.ged") ?: throw ImportProblem("GEDZIP neobsahuje gedcom.ged.")
                    val bytes=checked(zip,entry.name).use {input ->ByteArrayOutputStream().also {copy(input,it,32L*1024*1024,cancel)}.toByteArray()}
                    GedcomImport.parse(bytes,title,cancel).also {if(!it.format.startsWith("GEDCOM 7."))throw ImportProblem("GEDZIP vyžaduje GEDCOM 7.")}.copy(format="GEDZIP 7")
                }
                else -> {if(source.length()>32L*1024*1024)throw ImportProblem("GEDCOM přesahuje limit 32 MB.");GedcomImport.parse(source.readBytes(),title,cancel)}
            }
            val media=GenealogyMedia(File(root,"media"));val warnings=parsed.warnings.toMutableList();var missing=0;var unsupported=0
            val photos=mutableMapOf<Pair<String,String>,String>();val docs=mutableMapOf<String,MutableList<Evidence>>()
            val imported=mutableMapOf<String,Pair<String,String>?>()
            parsed.media.forEach {ref ->
                cancel();val path=safePath(ref.path)
                if(path==null) {missing++;return@forEach}
                val result=if(imported.containsKey(path))imported[path] else {
                    val mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(path.substringAfterLast('.').lowercase()) ?: if(path.endsWith(".jpg",true))"image/jpeg" else "application/octet-stream"
                    if(!(mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("video/") || mime in setOf("application/pdf","text/plain"))) {unsupported++;imported[path]=null;null}
                    else {
                        val input=if(zip!=null)zip.getEntry(path)?.let {checked(zip,path)} else external(path)
                        if(input==null) {missing++;imported[path]=null;null}
                        else try {val key=if(mime.startsWith("image/"))media.add(input,cancel) else media.addDocument(input,cancel);(key to mime).also {imported[path]=it}}
                        catch(e: GenealogyProblem) {unsupported++;imported[path]=null;null}
                    }
                }
                if(result!=null) {
                    val (key,mime)=result
                    if(ref.portrait && key.endsWith(".img"))photos.putIfAbsent(ref.tree to ref.person,key)
                    val list=docs.getOrPut(ref.tree){mutableListOf()}
                    if(list.none {it.person==ref.person && it.file=="managed:$key"})list+=Evidence(person=ref.person,caption=ref.caption.ifBlank {path.substringAfterLast('/')},file="managed:$key",mime=mime)
                }
            }
            if(missing>0)warnings+="Nedostupná nebo nepřenosná média: $missing. Zkontrolujte relativní cesty a případně vyberte složku médií. Síťové a absolutní cesty se automaticky nenačítají."
            if(unsupported>0)warnings+="Nepodporovaná, poškozená nebo příliš velká média: $unsupported. Jejich odkazy zůstávají ve zdrojové kopii."
            val trees=parsed.trees.map {tree ->tree.copy(people=tree.people.map {p ->p.copy(portrait=photos[tree.id to p.id].orEmpty())},evidence=tree.evidence+docs[tree.id].orEmpty()).also(GenealogyEdits::validate)}
            return ImportPreview(root,source,parsed.copy(trees=trees,warnings=warnings.distinct()))
        } finally {zip?.close()}
    }
    fun commit(preview: ImportPreview,selected: Set<String>,store: GenealogyStore,media: GenealogyMedia): List<FamilyTree> {
        val chosen=preview.data.trees.filter {it.id in selected};require(chosen.isNotEmpty())
        val key=newId()+when {preview.data.format.startsWith("Vestigia")->".vestigia";preview.data.format.startsWith("GEDZIP")->".gdz";else->".ged"}
        val sources=File(store.root,"sources").apply {check(isDirectory || mkdirs())}
        val original=File(sources,key)
        try {
            File(preview.root,"media").listFiles().orEmpty().forEach {file ->
                val destination=File(media.root,file.name)
                if(!destination.exists()) {
                    val temp=File.createTempFile("import-",".tmp",media.root)
                    try {file.copyTo(temp,overwrite=true);if(!destination.exists())check(temp.renameTo(destination))} finally {temp.delete()}
                }
            }
            preview.source.copyTo(original)
            val trees=chosen.map {it.copy(importSource=key)}
            store.saveNew(trees)
            preview.root.deleteRecursively()
            return trees
        } catch(e: Throwable) {original.delete();throw e}
    }
    fun safePath(value: String): String? {
        val path=try {URI(value).let {uri ->if(uri.scheme!=null || uri.rawQuery!=null || uri.rawFragment!=null)return null;uri.path}} catch(_: Exception){value}
        if(path.isNullOrBlank() || path.startsWith('/') || path.contains('\\') || path.contains(':') || path.any {it<' '} || path.split('/').any {it in setOf("..",".","")})return null
        return path
    }
    private fun validateZip(zip: ZipFile) {
        val entries=zip.entries().asSequence().toList();if(entries.size>20000)throw ImportProblem("GEDZIP obsahuje příliš mnoho souborů.")
        val names=mutableSetOf<String>();var total=0L
        entries.forEach {entry ->
            if(!names.add(entry.name) || safePath(entry.name.removeSuffix("/"))==null)throw ImportProblem("Neplatná nebo duplicitní cesta v GEDZIP.")
            if(entry.size<0 || entry.size>64L*1024*1024)throw ImportProblem("Soubor v GEDZIP je příliš velký.")
            total+=entry.size;if(total>512L*1024*1024)throw ImportProblem("Rozbalený GEDZIP přesahuje limit 512 MB.")
        }
    }
    private fun checked(zip: ZipFile,path: String): InputStream {
        val entry=zip.getEntry(path) ?: throw FileNotFoundException();val crc=CRC32()
        return object: FilterInputStream(zip.getInputStream(entry)) {
            var total=0L
            override fun read(): Int {val n=`in`.read();if(n>=0){crc.update(n);total++}else verify();return n}
            override fun read(b: ByteArray,off: Int,len: Int): Int {val n=`in`.read(b,off,len);if(n>0){total+=n;if(total>entry.size || total>64L*1024*1024)throw ImportProblem("Překročena velikost souboru v GEDZIP.");crc.update(b,off,n)}else if(n<0)verify();return n}
            fun verify() {if(total!=entry.size || crc.value!=entry.crc)throw ImportProblem("Nesouhlasí kontrola integrity souboru GEDZIP.")}
        }
    }
    private fun copy(input: InputStream,output: OutputStream,limit: Long,cancel: ()->Unit) {
        val bytes=ByteArray(65536);var total=0L
        while(true){cancel();val n=input.read(bytes);if(n<0)break;total+=n;if(total>limit)throw ImportProblem("Soubor překračuje povolenou velikost.");output.write(bytes,0,n)}
    }
    private fun folderFile(resolver: ContentResolver,root: Uri,path: String): InputStream? {
        var document=DocumentsContract.getTreeDocumentId(root)
        var parts=path.split('/')
        fun name(id: String)=resolver.query(DocumentsContract.buildDocumentUriUsingTree(root,id),arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0) else null}
        if(parts.firstOrNull()==name(document))parts=parts.drop(1)
        for(part in parts) {
            val child=resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(root,document),arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use {cursor ->var found: String?=null;while(cursor.moveToNext())if(cursor.getString(1)==part){if(found!=null)throw ImportProblem("Nejednoznačný název souboru v médiích.");found=cursor.getString(0)};found} ?: return null
            document=child
        }
        return resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(root,document))
    }
}
