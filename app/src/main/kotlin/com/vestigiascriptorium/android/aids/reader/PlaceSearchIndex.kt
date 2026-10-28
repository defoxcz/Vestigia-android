package com.vestigiascriptorium.android.aids.reader

import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Disposable derived data only. The downloaded database always remains read-only. */
internal object PlaceSearchIndex {
    @Synchronized fun prepare(source: File, db: SQLiteDatabase, directory: File, preparing: (Boolean) -> Unit = {}): File {
        directory.mkdirs()
        val fingerprint="v4:${source.canonicalPath}:${source.length()}:${source.lastModified()}"
        val hash=MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray()).joinToString("") { "%02x".format(it) }
        val index=File(directory,"places-$hash.db")
        if(index.isFile) return index
        directory.listFiles()?.filter { it.name.startsWith("places-") && it.extension=="tmp" }?.forEach { it.delete() }
        preparing(true)
        val stage=File(directory,"places-${UUID.randomUUID()}.tmp")
        try {
            SQLiteDatabase.openOrCreateDatabase(stage,null).use { target ->
                target.rawQuery("PRAGMA journal_mode=OFF",null).use { it.moveToFirst() }
                target.execSQL("PRAGMA synchronous=OFF")
                target.execSQL("CREATE TABLE names(id TEXT PRIMARY KEY,title TEXT,folded TEXT,country TEXT,summary TEXT)")
                target.execSQL("CREATE TABLE all_fields(id TEXT PRIMARY KEY,folded TEXT,country TEXT)")
                val columns=db.rawQuery("PRAGMA table_info(places)",null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(1)) } }
                val extras=listOf("wikidata_id","national_code","latitude","longitude","place_types","wikidata_url").filter { it in columns }
                val select=listOf("id","canonical_name","country","administrative_units","search_text")+extras
                val countryLabels=mutableMapOf<String,String>()
                if(android.os.Build.VERSION.SDK_INT>=30) {
                    target.setCustomScalarFunction("reader_fold") { AidSchemas.fold(it ?: "") }
                    target.execSQL("ATTACH DATABASE ? AS source",arrayOf(source.toURI().toString()+"?mode=ro"))
                }
                target.beginTransaction()
                try {
                    if(android.os.Build.VERSION.SDK_INT>=30) {
                        target.execSQL("INSERT INTO names SELECT id,canonical_name,reader_fold(canonical_name),country,administrative_units FROM source.places")
                        val countries=db.rawQuery("SELECT DISTINCT country FROM places WHERE country<>''",null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
                        fun quote(text: String)="'"+text.replace("'","''")+"'"
                        val countryText="CASE p.country "+countries.joinToString(" ") { code ->
                            val label=listOf("cs","en").joinToString(" ") { java.util.Locale("",code).getDisplayCountry(java.util.Locale(it)) }
                            "WHEN ${quote(code)} THEN ${quote(label)}"
                        }+" ELSE '' END"
                        val extraText=(listOf("p.country")+extras.map { "p.$it" }).joinToString(" || ' ' || ") { "COALESCE($it,'')" }
                        target.execSQL("INSERT INTO all_fields SELECT p.id,reader_fold(COALESCE(p.search_text,'') || ' ' || p.canonical_name || ' ' || $countryText || ' ' || $extraText || ' ' || COALESCE((SELECT group_concat(n.normalized_name,' ') FROM source.place_names n WHERE n.place_id=p.id AND instr(COALESCE(p.search_text,''),n.normalized_name)=0),'')),p.country FROM source.places p")
                    } else {
                    target.compileStatement("INSERT INTO names VALUES(?,?,?,?,?)").use { names ->
                        target.compileStatement("INSERT INTO all_fields VALUES(?,?,?)").use { all ->
                            var lastRow=0L
                            while(true) {
                            var batch=0
                            db.rawQuery("SELECT ${select.joinToString(",") { "p.$it" }}, (SELECT group_concat(normalized_name,char(10)) FROM place_names n WHERE n.place_id=p.id),p.rowid FROM places p WHERE p.rowid>? ORDER BY p.rowid LIMIT 512",arrayOf(lastRow.toString())).use { c ->
                                while(c.moveToNext()) {
                                    fun value(i: Int)=if(c.isNull(i)) "" else c.getString(i)
                                    lastRow=c.getLong(select.size+1);batch++
                                    val id=value(0);val title=value(1);val country=value(2)
                                    val folded=AidSchemas.fold(title)
                                    val displayCountry=countryLabels.getOrPut(country) { AidSchemas.fold(listOf("cs","en").joinToString(" ") { java.util.Locale("",country).getDisplayCountry(java.util.Locale(it)) }) }
                                    val values=(listOf(folded,displayCountry,AidSchemas.fold(country),AidSchemas.fold(value(3)))+
                                        extras.indices.map { AidSchemas.fold(value(it+5)) }+
                                        value(select.size).split('\n')).filter { it.isNotBlank() }.distinct()
                                    // Keep substrings intact while avoiding repeated names/administrative text.
                                    val full=StringBuilder(AidSchemas.fold(value(4)))
                                    for(text in values) if(!full.contains(text)) { if(full.isNotEmpty()) full.append(' ');full.append(text) }

                                    listOf(id,title,folded,country,value(3)).forEachIndexed { i,v -> names.bindString(i+1,v) };names.executeInsert()
                                    listOf(id,full.toString(),country).forEachIndexed { i,v -> all.bindString(i+1,v) };all.executeInsert()
                                }
                            }
                            if(batch==0) break
                            }
                        }
                    }
                    }
                    target.execSQL("CREATE VIRTUAL TABLE search USING fts4(name,all_text,tokenize=unicode61,prefix='1,2,3,4')")
                    target.execSQL("INSERT INTO search(docid,name,all_text) SELECT n.rowid,n.folded,a.folded FROM names n JOIN all_fields a ON a.id=n.id")
                    target.execSQL("CREATE INDEX name_order ON names(title COLLATE NOCASE,id)")
                    target.execSQL("CREATE INDEX name_country ON names(country)")
                    target.execSQL("CREATE INDEX all_country ON all_fields(country)")
                    target.setTransactionSuccessful()
                } finally { target.endTransaction() }
            }
            Files.move(stage.toPath(),index.toPath(),StandardCopyOption.ATOMIC_MOVE)
            directory.listFiles()?.filter { it.name.startsWith("places-") && it.extension=="db" }
                ?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
            return index
        } finally { stage.delete() }
    }
    fun page(index: File,query: String,country: String,page: Int,scope: SearchScope,signal: CancellationSignal,cs: Boolean): RecordPage {
        SQLiteDatabase.openDatabase(index.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            val where=mutableListOf<String>();val args=mutableListOf<String>()
            if(query.isNotBlank()) {
                val terms=AidSchemas.fts(AidSchemas.fold(query.trim()))
                if(terms.isEmpty()) {
                    where+="folded LIKE ? ESCAPE '\\'";args+=AidSchemas.like(AidSchemas.fold(query.trim()))
                } else {
                    val column=if(scope==SearchScope.NAMES) "name" else "all_text"
                    where+="rowid IN (SELECT docid FROM search WHERE search MATCH ?)"
                    args+=Regex("[\\p{L}\\p{N}]+").findAll(AidSchemas.fold(query.trim())).take(12).joinToString(" AND ") { "$column:${it.value}*" }
                }
            }
            if(country.isNotEmpty()) { where+="country=?";args+=country }
            val predicate=if(where.isEmpty()) "" else " WHERE "+where.joinToString(" AND ")
            val parameters=args.toTypedArray()
            val total=db.rawQuery("SELECT count(*) FROM names$predicate",parameters,signal).use { it.moveToFirst();it.getInt(0) }
            val safePage=page.coerceIn(0,(total-1).coerceAtLeast(0)/AidReaderSource.PAGE_SIZE)
            val rows=db.rawQuery("SELECT id,title,summary FROM names$predicate ORDER BY title COLLATE NOCASE,id LIMIT ${AidReaderSource.PAGE_SIZE} OFFSET ${safePage*AidReaderSource.PAGE_SIZE}",parameters,signal).use { c -> buildList {
                while(c.moveToNext()) add(AidRecord(c.getString(0),c.getString(1),c.getString(2) ?: ""))
            } }
            val options=db.rawQuery("SELECT DISTINCT country FROM names WHERE country<>'' ORDER BY country",null,signal).use { c -> buildList {
                while(c.moveToNext()) { val code=c.getString(0);add(FacetOption(code,java.util.Locale("",code).getDisplayCountry(java.util.Locale(if(cs) "cs" else "en")))) }
            } }
            return RecordPage(rows,total,safePage,options,if(cs) "Země" else "Country",AidViewKind.TEXT)
        }
    }
}
