package com.vestigiascriptorium.android.genealogy

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal object VestigiaImport {
    private fun id(value: String)=value.trim().removePrefix("{").removeSuffix("}")
    private fun JSONArray.objects()=(0 until length()).map {getJSONObject(it)}
    fun read(file: File,cancel: ()->Unit={}): GenealogyImportData = SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use {db ->
        val tables=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",null).use {c ->buildSet {while(c.moveToNext())add(c.getString(0))}}
        if(!tables.containsAll(setOf("project_metadata","project_nodes")))throw ImportProblem("Soubor není projektem Vestigia.")
        val metadata=db.rawQuery("SELECT key,value FROM project_metadata",null).use {c ->buildMap {while(c.moveToNext())put(c.getString(0),c.getString(1))}}
        val version=metadata["formatVersion"]?.toIntOrNull() ?: 0
        if(version !in 1..10)throw ImportProblem("Nepodporovaná verze projektu Vestigia ($version).")
        val nodes=db.rawQuery("SELECT id,display_name FROM project_nodes ORDER BY position,id",null).use {c ->buildList {while(c.moveToNext()) {if(size>50000)throw ImportProblem("Příliš mnoho projektových uzlů.");add(c.getString(0) to c.getString(1))}}}
        val mediaColumns=if("multimedia_items" in tables)db.rawQuery("PRAGMA table_info(multimedia_items)",null).use {c ->buildSet {while(c.moveToNext())add(c.getString(1))}} else emptySet()
        val recipe=if("image_edit_recipe" in mediaColumns)"image_edit_recipe" else "''"
        val mediaPaths=if("multimedia_items" in tables)db.rawQuery("SELECT id,source_path,name,$recipe FROM multimedia_items",null).use {c ->buildMap {while(c.moveToNext())put(id(c.getString(0)),Triple(c.getString(1).orEmpty(),c.getString(2).orEmpty(),c.getString(3).orEmpty()))}} else emptyMap()
        val trees=mutableListOf<FamilyTree>();val media=mutableListOf<ImportMediaRef>();val warnings=linkedSetOf<String>();val documentation=mutableListOf<JSONObject>()
        val properties=nodes.map {(key,title) ->cancel();Triple(key,title,JSONObject(readProperties(db,key)))}
        properties.forEach {(_,_,p) ->p.optJSONArray("documentationLinks")?.let {documentation+=it.objects()}}
        properties.forEach {(nodeId,title,properties) ->
            cancel();val items=properties.optJSONArray("registerGraphItems")?.objects() ?: return@forEach
            if(items.none {it.optString("type")=="person"})return@forEach
            val relations=properties.optJSONArray("registerGraphRelations")?.objects().orEmpty()
            val treeId=newId();var people=items.filter {it.optString("type")=="person"}.map {p ->
                val given=p.optString("forename",p.optString("givenName")).trim().split(Regex("\\s+"),limit=2)
                Person(id=id(p.getString("id")),givenName=given.firstOrNull().orEmpty().ifBlank {if(p.optString("surname").isBlank())"?" else ""},middleName=p.optString("middleName",given.getOrElse(1){""}),surname=p.optString("surname"),gender=p.optString("gender","unknown"),note=p.optString("note",p.optString("notes")),x=p.optDouble("x",0.0).toFloat(),y=p.optDouble("y",0.0).toFloat())
            }
            val personIds=people.map {it.id}.toSet();val events=mutableListOf<LifeEvent>();val evidence=mutableListOf<Evidence>()
            items.filter {it.optString("type")=="event"}.forEach {e ->
                val eventId=id(e.getString("id"));val related=relations.filter {id(it.optString("sourceId"))==eventId}
                val subjects=related.filter {it.optString("type")=="event-subject"}.map {id(it.optString("targetId"))}.distinct()
                if(subjects.any {it !in personIds})throw ImportProblem("Událost odkazuje na chybějící osobu.")
                val originalKind=e.optString("eventType");val kind=originalKind.takeIf {it in GenealogyEdits.kinds} ?: "genealogy.mentioned"
                if(kind!=originalKind)warnings+="Některé typy událostí jsou převedeny na obecnou událost; původní atributy zůstávají ve zdrojové kopii."
                val common=LifeEvent(id=eventId,kind=kind,title=e.optString("title"),date=GedcomImport.date(e.optString("date")),place=e.optString("place"),note=e.optString("note",e.optString("notes")),x=e.optDouble("x",0.0).toFloat(),y=e.optDouble("y",0.0).toFloat())
                if(kind in setOf("genealogy.marriage","genealogy.spousal-record")) {
                    val explicit=e.optJSONObject("subjectRoles");val groom=explicit?.optString("groom")?.takeIf {it.isNotBlank()}?.let(::id);val bride=explicit?.optString("bride")?.takeIf {it.isNotBlank()}?.let(::id)
                    val roles=if(groom!=null || bride!=null)buildMap {groom?.let {put("subject",it)};bride?.let {put("subject2",it)}} else subjects.take(2).mapIndexed {index,p ->(if(index==0)"subject" else "subject2") to p}.toMap()
                    if(subjects.size>2)throw ImportProblem("Partnerská událost obsahuje více než dvě hlavní osoby.")
                    events+=common.copy(roles=roles)
                } else {
                    val roles=related.filter {it.optString("type")=="event-participant"}.mapNotNull {r ->val role=r.optString("role");if(role in GenealogyEdits.rolesFor(kind))role to id(r.optString("targetId")) else {warnings+="Další účastnické role jsou zachovány ve zdrojové kopii.";null}}.let { entries ->
                        if(entries.groupBy {it.first}.values.any {values ->values.map {it.second}.distinct().size>1})throw ImportProblem("Událost má více osob v téže účastnické roli.")
                        entries.toMap()
                    }
                    if(subjects.size>1)warnings+="Události s více hlavními osobami jsou rozděleny do samostatných záznamů."
                    if(subjects.isEmpty())events+=common.copy(roles=roles)
                    subjects.forEachIndexed {index,p ->events+=common.copy(id=if(index==0)eventId else newId(),roles=roles+("subject" to p))}
                    if(kind=="genealogy.mentioned")subjects.forEach {p ->
                        val from=GedcomImport.date(e.optString("mentionedFrom"));val to=GedcomImport.date(e.optString("mentionedTo"))
                        people=people.map {person ->if(person.id!=p)person else person.copy(mentionedFrom=if(GenealogyDates.canonical(from) && from.isNotEmpty() && (person.mentionedFrom.isEmpty() || dateOrder(from)<dateOrder(person.mentionedFrom)))from else person.mentionedFrom,mentionedTo=if(GenealogyDates.canonical(to) && to.isNotEmpty() && (person.mentionedTo.isEmpty() || dateOrder(to)>dateOrder(person.mentionedTo)))to else person.mentionedTo)}
                    }
                }
            }
            items.filter {it.optString("type")=="person"}.forEach {p ->
                val key=id(p.getString("id"))
                listOf("birthDate" to "genealogy.birth","deathDate" to "genealogy.death").forEach {(property,kind) ->val value=GedcomImport.date(p.optString(property));if(value.isNotEmpty() && events.none {it.kind==kind && it.roles["subject"]==key})events+=LifeEvent(kind=kind,date=value,roles=mapOf("subject" to key),x=p.optDouble("x",0.0).toFloat()+280,y=p.optDouble("y",0.0).toFloat()+120)}
                people=people.map {person ->if(person.id!=key)person else person.copy(mentionedFrom=person.mentionedFrom.ifBlank {GedcomImport.date(p.optString("mentionedFrom")).takeIf(GenealogyDates::canonical).orEmpty()},mentionedTo=person.mentionedTo.ifBlank {GedcomImport.date(p.optString("mentionedTo")).takeIf(GenealogyDates::canonical).orEmpty()})}
                val portrait=mediaPaths[id(p.optString("multimediaId"))]
                if(portrait!=null && portrait.first.isNotBlank()) {
                    val edited=portrait.third.isNotBlank();if(edited)warnings+="Desktopové úpravy obrázků se nepřenášejí. Upravené portréty jsou připojeny jako původní doklady."
                    media+=ImportMediaRef(treeId,key,portrait.first,!edited,portrait.second)
                }
            }
            relations.filter {it.optString("type")=="family.parent-child"}.forEach {r ->
                val parent=id(r.optString("sourceId"));val child=id(r.optString("targetId"));val role=r.optString("role")
                if(role !in setOf("genealogy.father","genealogy.mother"))warnings+="Přímá rodičovská vazba bez určené role zůstává ve zdrojové kopii."
                else if(parent in personIds && child in personIds && events.none {it.kind in setOf("genealogy.birth","genealogy.parentage","genealogy.baptism") && it.roles["subject"]==child && it.roles[role]==parent})events+=LifeEvent(kind="genealogy.parentage",roles=mapOf("subject" to child,role to parent))
            }
            documentation.forEach {link ->
                val source=link.optJSONObject("source") ?: return@forEach
                if(source.optString("providerId")!="genealogy.person" || (source.optString("ownerNodeId").isNotEmpty() && id(source.optString("ownerNodeId"))!=id(nodeId)))return@forEach
                val person=id(source.optString("entityId"));if(person !in personIds)return@forEach
                val target=link.optJSONObject("target");val item=mediaPaths[id(target?.optString("entityId").orEmpty())]
                val caption=link.optJSONObject("metadata")?.optString("caption").orEmpty().ifBlank {item?.second ?: "Doklad"}
                evidence+=Evidence(person=person,caption=caption,note=link.optString("note"))
                if(item!=null && item.first.isNotEmpty())media+=ImportMediaRef(treeId,person,item.first,false,caption)
                if(target?.optJSONObject("anchor")!=null)warnings+="Výřezy dokumentace se přenášejí jako celé původní obrázky."
            }
            val tree=FamilyTree(id=treeId,title=if(nodes.size==1)metadata["name"] ?: title else listOf(metadata["name"],title).filterNotNull().filter {it.isNotBlank()}.distinct().joinToString(" · ").ifBlank {"Rodokmen"},people=people,events=events,evidence=evidence)
            try {GenealogyEdits.validate(tree)} catch(_: GenealogyProblem) {throw ImportProblem("Graf Vestigia obsahuje nekompatibilní nebo konfliktní role a vazby.")}
            if(items.any {it.optString("type") !in setOf("person","event")})warnings+="Pramenné a další desktopové objekty nejsou grafickými boxy Androidu; úplný projekt zůstává ve zdrojové kopii."
            trees+=tree
        }
        if(trees.isEmpty())throw ImportProblem("Projekt neobsahuje genealogický graf s osobami.")
        warnings+="Importuje se genealogický obsah. Šablony, sestavy, nastavení a ostatní moduly zůstávají v úplné zdrojové kopii projektu."
        GenealogyImportData("Vestigia (formát $version)",trees,media,warnings.toList())
    }
    private fun readProperties(db: SQLiteDatabase,id: String): String {
        val length=db.rawQuery("SELECT length(properties_json) FROM project_nodes WHERE id=?",arrayOf(id)).use {check(it.moveToFirst());it.getInt(0)}
        if(length>32*1024*1024)throw ImportProblem("Graf je příliš rozsáhlý pro tento import.")
        return buildString {var offset=1;while(offset<=length) {append(db.rawQuery("SELECT substr(properties_json,?,65536) FROM project_nodes WHERE id=?",arrayOf(offset.toString(),id)).use {check(it.moveToFirst());it.getString(0)});offset+=65536}}.ifBlank {"{}"}
    }
}
