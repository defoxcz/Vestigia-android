package com.vestigiascriptorium.android.aids.reader

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale

internal data class RecordField(val label: String,val value: String,val link: Boolean=false)
internal data class RecordImage(val name: String,val data: Any,val source: String?=null,val author: String="",val license: String="",val licenseUrl: String="",val credit: String="",val terms: String="",val restrictions: String="",val online: Boolean=true)
internal data class AidRecord(val id: String,val title: String,val summary: String,val fields: List<RecordField> = emptyList(),val images: List<RecordImage> = emptyList(),val hiddenImages: Int=0,val mapTarget: PlaceMapTarget?=null)
internal data class FacetOption(val value: String,val label: String)
internal data class RecordPage(val records: List<AidRecord>,val total: Int,val page: Int,val options: List<FacetOption>,val facetLabel: String?,val kind: AidViewKind)

/** One reader and one page/detail contract, regardless of the underlying dataset schema. */
internal class AidReaderSource(private val indexDirectory: File?=null,private val file: () -> File?) {
    private val mutex=Mutex()
    private val preparing= kotlinx.coroutines.flow.MutableStateFlow(false)
    val preparingIndex=preparing.asStateFlow()
    suspend fun page(aid: String,language: String,query: String,filter: String,page: Int,imagesOnly: Boolean=false,searchScope: SearchScope=SearchScope.NAMES): RecordPage = mutex.withLock {
        withContext(Dispatchers.IO) {
            val signal=CancellationSignal()
            val watcher=launch(Dispatchers.Default,start=CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { signal.cancel() }
            }
            try {
            ensureActive()
            val schema=AidSchemas.get(aid,language)
            open().use { db ->
                if(aid=="places") {
                    val source=File(db.path)
                    val directory=indexDirectory ?: File(source.parentFile,"reader-index")
                    val index=withContext(NonCancellable) {
                        try { PlaceSearchIndex.prepare(source,db,directory) { preparing.value=it } }
                        finally { preparing.value=false }
                    }
                    ensureActive()
                    return@withContext PlaceSearchIndex.page(index,query,filter,page,searchScope,signal,language=="cs")
                }
                val where=mutableListOf<String>();val args=mutableListOf<String>()
                if(query.isNotBlank()) {
                    val expression=if(searchScope==SearchScope.NAMES) {
                        if(schema.titleJson!="''") "COALESCE(NULLIF(json_extract(${schema.titleJson}, '$.$language'),''), NULLIF(json_extract(${schema.titleJson}, '$.en'),''), ${schema.title})" else schema.title
                    } else AidSchemas.allFieldsSearch(schema)
                    if(Build.VERSION.SDK_INT>=30) { where+="vestigia_fold(COALESCE(($expression),'')) LIKE ? ESCAPE '\\'";args+=AidSchemas.like(AidSchemas.fold(query.trim())) }
                    else { where+="LOWER(COALESCE(($expression),'')) LIKE ? ESCAPE '\\'";args+=AidSchemas.like(query.trim().lowercase(Locale.ROOT)) }
                }
                if(filter.isNotEmpty() && schema.facet!=null) {
                    where+=if('?' in schema.facet.sql) schema.facet.sql else "${schema.facet.sql}=?";args+=filter
                }
                if(imagesOnly && schema.kind==AidViewKind.IMAGE) where+=AidSchemas.imageExists(schema)
                val predicate=if(where.isEmpty()) "" else " WHERE "+where.joinToString(" AND ")
                val total=db.rawQuery("SELECT count(*) FROM ${schema.from}$predicate",args.toTypedArray(),signal).use { it.moveToFirst();it.getInt(0) }
                ensureActive()
                val safePage=page.coerceIn(0,((total-1).coerceAtLeast(0))/PAGE_SIZE)
                val sql="SELECT ${schema.key} AS record_id,${schema.title} AS title,${schema.summary} AS summary,${schema.titleJson} AS labels FROM ${schema.from}$predicate ORDER BY title COLLATE NOCASE, record_id LIMIT $PAGE_SIZE OFFSET ${safePage*PAGE_SIZE}"
                val rows=db.rawQuery(sql,args.toTypedArray(),signal).use { cursor -> buildList {
                    while(cursor.moveToNext()) {
                        ensureActive()
                        val id=cursor.string(0)
                        add(AidRecord(id,localized(cursor.string(3),language).ifEmpty { cursor.string(1) },cursor.string(2)))
                    }
                }}
                val withImages=if(schema.kind==AidViewKind.IMAGE) rows.map { val preview=images(db,schema,it.id,limit=1);it.copy(images=preview.first,hiddenImages=preview.second) } else rows
                val options=schema.facet?.let { facet -> db.rawQuery(facet.optionsSql,null).use { cursor -> buildList {
                    while(cursor.moveToNext()) { val v=cursor.string(0);if(v.isNotBlank()) add(FacetOption(v,format(v,facet.type,language))) }
                }}} ?: emptyList()
                RecordPage(withImages,total,safePage,options,schema.facet?.let { if(language=="cs") it.cs else it.en },schema.kind)
            }
            } catch(error: android.os.OperationCanceledException) {
                // SQLite cancellation is not a Kotlin CancellationException. Preserve structured cancellation.
                ensureActive()
                throw error
            } finally { watcher.cancel() }
        }
    }
    suspend fun detail(aid: String,language: String,id: String): AidRecord? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val schema=AidSchemas.get(aid,language)
            open().use { db ->
                val expressions=schema.fields.joinToString(",") { it.sql }
                db.rawQuery("SELECT ${schema.key},${schema.title},${schema.summary},${schema.titleJson},$expressions FROM ${schema.from} WHERE ${schema.key}=? LIMIT 1",arrayOf(id)).use { cursor ->
                    if(!cursor.moveToFirst()) return@withContext null
                    val baseFields=schema.fields.mapIndexedNotNull { index,field ->
                        val value=format(cursor.string(index+4),field.type,language)
                        if(value.isBlank()) null else RecordField(if(language=="cs") field.cs else field.en,value,field.type=="url")
                    }
                    val fields=baseFields+extraFields(db,schema,id,language)
                    val (images,hidden)=images(db,schema,id,limit=40)
                    val title=localized(cursor.string(3),language).ifEmpty { cursor.string(1) }
                    val mapTarget=if(aid=="places") {
                        fun value(sql: String): String = schema.fields.indexOfFirst { it.sql==sql }.let { if(it<0) "" else cursor.string(it+4) }
                        PlaceMapTarget(title,value("p.country"),value("p.latitude").toDoubleOrNull(),value("p.longitude").toDoubleOrNull())
                    } else null
                    AidRecord(id,title,cursor.string(2),fields,images,hidden,mapTarget)
                }
            }
        }
    }
    private fun open(): SQLiteDatabase {
        val path=file() ?: throw java.io.FileNotFoundException("Research aid not installed")
        val db=SQLiteDatabase.openDatabase(path.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        if(Build.VERSION.SDK_INT>=30) db.setCustomScalarFunction("vestigia_fold") { AidSchemas.fold(it ?: "") }
        return db
    }
    private fun images(db: SQLiteDatabase,s: AidSchema,id: String,limit: Int): Pair<List<RecordImage>,Int> {
        if(s.imageFamily==null) return emptyList<RecordImage>() to 0
        val sql=AidSchemas.imageSql(s.imageFamily)
        var hidden=0
        val result=db.rawQuery(sql,arrayOf(id)).use { c -> buildList {
            val seen=hashSetOf<String>()
            while(c.moveToNext()) {
                val policy=c.string(1)
                if(policy !in setOf("public_domain","attribution_required")) { hidden++;continue }
                val name=c.string(0)
                if(name.isBlank() || !seen.add(name) || size>=limit) continue
                val encoded=URLEncoder.encode(name,"UTF-8").replace("+","%20")
                add(RecordImage(name,"https://commons.wikimedia.org/wiki/Special:Redirect/file/$encoded?width=1000",
                    "https://commons.wikimedia.org/wiki/File:$encoded",c.string(4),c.string(2),c.string(3),c.string(5),c.string(6),c.string(7)))
            }
        }}
        return result to hidden
    }
    private fun extraFields(db: SQLiteDatabase,schema: AidSchema,id: String,language: String): List<RecordField> {
        val queries=AidSchemas.extrasSql(schema.imageFamily)
        if(queries.isEmpty()) return emptyList()
        if(schema.imageFamily=="objects") {
            val groups=linkedMapOf<String,MutableSet<String>>()
            db.rawQuery(queries.first(),arrayOf(id)).use { c ->
                while(c.moveToNext()) {
                    val label=localized(c.string(0),language)
                    if(label.isBlank()) continue
                    val value=when {
                        c.string(2).isNotBlank() -> localized(c.string(9),language)
                        c.string(5).isNotBlank() -> date(c.string(5),if(c.isNull(6)) 9 else c.getInt(6))
                        c.string(3).isNotBlank() -> {
                            val unitId=c.string(4).substringAfterLast('/')
                            val unit=if(unitId.matches(Regex("Q[0-9]+"))) db.rawQuery("SELECT labels_json FROM entities WHERE wikidata_id=?",arrayOf(unitId)).use { u -> if(u.moveToFirst()) localized(u.string(0),language) else "" } else ""
                            listOf(c.string(3),unit).filter { it.isNotBlank() }.joinToString(" ")
                        }
                        !c.isNull(7) && !c.isNull(8) -> "${c.string(7)}, ${c.string(8)}"
                        else -> c.string(1)
                    }
                    if(value.isNotBlank()) groups.getOrPut(label) { linkedSetOf() }.add(value)
                }
            }
            return groups.map { RecordField(it.key,it.value.joinToString("\n")) }
        }
        val fields=mutableListOf<RecordField>()
        val labels=if(language=="cs") listOf("Druh památky","Země","Umístění","Materiál","Tvůrce") else listOf("Monument type","Country","Location","Material","Creator")
        db.rawQuery(queries[0],arrayOf(id)).use { c -> if(c.moveToFirst()) {
            for(index in 0..4) {
                val ids=linkedSetOf<String>()
                fun collect(value: Any?) {
                    when(value) {
                        is org.json.JSONArray -> for(i in 0 until value.length()) collect(value.opt(i))
                        is JSONObject -> value.keys().forEach { collect(value.opt(it)) }
                        is String -> if(value.matches(Regex("Q[0-9]+"))) ids.add(value)
                    }
                }
                runCatching { collect(org.json.JSONTokener(c.string(index)).nextValue()) }
                val values=ids.take(64).mapNotNull { key -> db.rawQuery("SELECT labels_json,label FROM entities WHERE qid=?",arrayOf(key)).use { entity ->
                    if(entity.moveToFirst()) localized(entity.string(0),language).ifEmpty { entity.string(1) } else null
                }}
                if(values.isNotEmpty()) fields+=RecordField(labels[index],values.joinToString(", "))
            }
            val times=runCatching { org.json.JSONArray(c.string(5)) }.getOrNull()
            val dates=if(times==null) emptyList() else (0 until times.length()).mapNotNull { i ->
                times.optJSONObject(i)?.let { time -> time.optString("time").takeIf { it.isNotBlank() }?.let { date(it,time.optInt("precision",9)) } }
            }
            if(dates.isNotEmpty()) fields+=RecordField(if(language=="cs") "Datace" else "Date",dates.distinct().joinToString(", "))
        }}
        val people=db.rawQuery(queries[1],arrayOf(id)).use { c -> buildList { while(c.moveToNext()) add(localized(c.string(0),language).ifEmpty { c.string(1) }) } }
        if(people.isNotEmpty()) fields+=RecordField(if(language=="cs") "Osoby" else "People",people.distinct().joinToString(", "))
        return fields
    }
    private fun date(value: String,precision: Int): String {
        val parts=value.substringBefore('T').removePrefix("+").split('-')
        return if(parts.size<3 || value.startsWith('-')) value.substringBefore('T').removePrefix("+")
            else when { precision<=9 -> parts[0];precision==10 -> parts.take(2).joinToString("-");else -> parts.take(3).joinToString("-") }
    }
    private fun Cursor.string(index: Int)=if(isNull(index)) "" else getString(index)
    private fun localized(value: String,language: String): String = runCatching { JSONObject(value).optString(language).ifEmpty { JSONObject(value).optString("en") } }.getOrDefault("")
    private fun format(value: String,type: String,language: String): String {
        if(value.isBlank()) return ""
        val locale=Locale.forLanguageTag(language)
        return when(type) {
            "localized" -> localized(value,language)
            "country" -> value.split(",").joinToString(", ") { code -> runCatching { Locale.Builder().setRegion(code.trim()).build().getDisplayCountry(locale).ifEmpty { code } }.getOrDefault(code) }
            "language" -> Locale.forLanguageTag(value).getDisplayLanguage(locale).ifEmpty { value }
            "feast" -> value.split(",").joinToString(", ") { raw ->
                val day=raw.trim();val parts=day.split('-');if(language=="cs" && parts.size==2) "${parts[1].toIntOrNull() ?: parts[1]}. ${parts[0].toIntOrNull() ?: parts[0]}." else day
            }
            else -> value
        }
    }
    companion object { const val PAGE_SIZE=24 }
}
