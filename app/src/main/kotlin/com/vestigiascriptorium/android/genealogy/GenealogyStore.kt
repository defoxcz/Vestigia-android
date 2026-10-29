package com.vestigiascriptorium.android.genealogy

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File

/** Standalone Android format v3 (reads v1/v2). No desktop database or research-aid file is opened. */
internal class GenealogyStore(val root: File): Closeable {
    private val db: SQLiteDatabase
    init {
        check(root.isDirectory || root.mkdirs())
        db=SQLiteDatabase.openOrCreateDatabase(File(root,"genealogy.sqlite"),null)
        require(db.version<=3) {"Newer genealogy storage"}
        db.execSQL("CREATE TABLE IF NOT EXISTS trees(id TEXT PRIMARY KEY,title TEXT NOT NULL,payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS workspace(id TEXT PRIMARY KEY,x REAL NOT NULL,y REAL NOT NULL,zoom REAL NOT NULL,selected TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT)")
        if(db.version<3)db.version=3
    }
    fun list(): List<Pair<String,String>> = db.rawQuery("SELECT id,title FROM trees ORDER BY title COLLATE NOCASE,id",null).use {c -> buildList {while(c.moveToNext()) add(c.getString(0) to c.getString(1))} }
    fun read(id: String): FamilyTree {
        val length=db.rawQuery("SELECT length(payload) FROM trees WHERE id=?",arrayOf(id)).use {check(it.moveToFirst());it.getInt(0)}
        require(length<=64*1024*1024)
        val payload=buildString {var start=1;while(start<=length) {
            append(db.rawQuery("SELECT substr(payload,?,?) FROM trees WHERE id=?",arrayOf(start.toString(),"65536",id)).use {check(it.moveToFirst());it.getString(0)})
            start+=65536
        }}
        return decode(JSONObject(payload)).also(GenealogyEdits::validate)
    }
    fun saveNew(trees: List<FamilyTree>) {
        require(trees.isNotEmpty() && trees.map {it.id}.toSet().size==trees.size)
        trees.forEach(GenealogyEdits::validate)
        db.beginTransaction()
        try {trees.forEach {tree ->
            check(db.insertOrThrow("trees",null,ContentValues().apply {put("id",tree.id);put("title",tree.title);put("payload",payload(tree))})!=-1L)
        };db.setTransactionSuccessful()} finally {db.endTransaction()}
    }
    fun save(tree: FamilyTree) {
        GenealogyEdits.validate(tree)
        db.beginTransaction()
        try {db.insertWithOnConflict("trees",null,ContentValues().apply {put("id",tree.id);put("title",tree.title);put("payload",payload(tree))},SQLiteDatabase.CONFLICT_REPLACE).also {check(it!=-1L)};db.setTransactionSuccessful()}
        finally {db.endTransaction()}
    }
    fun last(): String?=db.rawQuery("SELECT value FROM settings WHERE key='open'",null).use {if(it.moveToFirst()) it.getString(0) else null}
    fun last(id: String?) {db.insertWithOnConflict("settings",null,ContentValues().apply {put("key","open");put("value",id)},SQLiteDatabase.CONFLICT_REPLACE).also {check(it!=-1L)}}
    fun view(id: String): GraphView=db.rawQuery("SELECT x,y,zoom,selected FROM workspace WHERE id=?",arrayOf(id)).use {if(it.moveToFirst()) GraphView(it.getFloat(0),it.getFloat(1),it.getFloat(2),it.getString(3)) else GraphView()}
    fun view(id: String,value: GraphView) {
        require(value.x.isFinite() && value.y.isFinite() && value.zoom in .2f..3f)
        db.insertWithOnConflict("workspace",null,ContentValues().apply {put("id",id);put("x",value.x);put("y",value.y);put("zoom",value.zoom);put("selected",value.selected)},SQLiteDatabase.CONFLICT_REPLACE).also {check(it!=-1L)}
    }
    override fun close()=db.close()
    private fun payload(tree: FamilyTree): String=encode(tree).toString().also {
        if(it.codePointCount(0,it.length)>64*1024*1024)throw GenealogyProblem("storage")
    }
    private fun encode(t: FamilyTree)=JSONObject().apply {
        put("format",3);put("id",t.id);put("title",t.title);put("importSource",t.importSource)
        put("people",JSONArray(t.people.map {p -> JSONObject().apply {put("id",p.id);put("givenName",p.givenName);put("middleName",p.middleName);put("surname",p.surname);put("legacyName",p.legacyName);put("portrait",p.portrait);put("mentionedFrom",p.mentionedFrom);put("mentionedTo",p.mentionedTo);put("gender",p.gender);put("note",p.note);put("x",p.x);put("y",p.y)} }))
        put("events",JSONArray(t.events.map {e -> JSONObject().apply {put("id",e.id);put("kind",e.kind);put("title",e.title);put("date",e.date);put("place",e.place);put("note",e.note);put("roles",JSONObject(e.roles));put("x",e.x);put("y",e.y)} }))
        put("evidence",JSONArray(t.evidence.map {e -> JSONObject().apply {put("id",e.id);put("person",e.person);put("caption",e.caption);put("note",e.note);put("file",e.file);put("mime",e.mime)} }))
    }
    private fun decode(o: JSONObject): FamilyTree {
        val version=o.getInt("format");require(version in 1..3)
        fun <T> rows(key: String,read: (JSONObject)->T)=o.getJSONArray(key).let {a -> (0 until a.length()).map {read(a.getJSONObject(it))} }
        return FamilyTree(o.getString("id"),o.getString("title"),rows("people") {Person(id=it.getString("id"),givenName=it.optString("givenName"),middleName=it.optString("middleName"),surname=it.optString("surname"),legacyName=if(version==1)it.getString("name") else it.optString("legacyName"),mentionedFrom=it.optString("mentionedFrom"),mentionedTo=it.optString("mentionedTo"),portrait=it.optString("portrait"),gender=it.getString("gender"),note=it.getString("note"),x=it.getDouble("x").toFloat(),y=it.getDouble("y").toFloat())},
            rows("events") {e -> val roles=e.getJSONObject("roles");LifeEvent(e.getString("id"),e.getString("kind"),e.getString("title"),e.getString("date"),e.getString("place"),e.getString("note"),roles.keys().asSequence().associateWith {roles.getString(it)},e.getDouble("x").toFloat(),e.getDouble("y").toFloat())},
            rows("evidence") {Evidence(it.getString("id"),it.getString("person"),it.getString("caption"),it.getString("note"),it.getString("file"),it.getString("mime"))},o.optString("importSource"))
    }
}
