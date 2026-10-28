package com.vestigiascriptorium.android.aids.reader

import java.text.Normalizer
import java.util.Locale

internal enum class SearchScope { NAMES, ALL_FIELDS }
internal enum class AidViewKind { TEXT, IMAGE }
internal data class AidField(val cs: String,val en: String,val sql: String,val type: String="text")
internal data class AidFacet(val cs: String,val en: String,val sql: String,val optionsSql: String,val type: String="text")
internal data class AidSchema(val id: String,val from: String,val key: String,val title: String,val summary: String,
    val search: String,val fields: List<AidField>,val facet: AidFacet?,val kind: AidViewKind=AidViewKind.TEXT,
    val imageFamily: String?=null,val fts: String?=null,val titleJson: String="''")

internal object AidSchemas {
    fun get(id: String,language: String): AidSchema {
        val lang=if(language=="cs") "cs" else "en"
        val source={ sql: String -> AidField("Pramen","Source",sql,"url") }
        val country={ sql: String -> AidField("Země","Country",sql,"country") }
        val countryFacet={ sql: String,options: String -> AidFacet("Země","Country",sql,options,"country") }
        return when(id) {
            "historical-units" -> AidSchema(id,"units u LEFT JOIN unit_types t ON t.id=u.unit_type","u.id","u.canonical_name","u.conversion_raw","u.search_name || ' ' || COALESCE(u.additional_info,'')",
                listOf(AidField("Převod","Conversion","u.conversion_raw"),country("u.country"),AidField("Druh","Type","t.name"),AidField("Doplňující údaje","Additional information","u.additional_info"),AidField("Měrné soustavy","Measurement systems","(SELECT group_concat(s.name, ', ') FROM unit_measurement_systems us JOIN measurement_systems s ON s.id=us.system_id WHERE us.unit_id=u.id)"),AidField("Oblasti použití","Regions of use","(SELECT group_concat(r.name, ', ') FROM unit_region_groups ur JOIN region_groups r ON r.id=ur.region_id WHERE ur.unit_id=u.id)"),source("u.source_url")),
                countryFacet("u.country","SELECT DISTINCT country FROM units WHERE country<>'' ORDER BY country"))
            "saints" -> AidSchema(id,"saints s","s.id","s.display_name","(SELECT group_concat(iso_day, ', ') FROM feast_days f WHERE f.saint_id=s.id)","s.search_text",
                listOf(AidField("Svátky","Feast days","(SELECT group_concat(iso_day, ', ') FROM feast_days f WHERE f.saint_id=s.id)","feast"))+
                    listOf("cs" to "Česky","la" to "Latinsky","de" to "Německy","pl" to "Polsky").map { (code,title) ->
                        AidField(title,when(code){"cs"->"Czech";"la"->"Latin";"de"->"German";else->"Polish"},"(SELECT group_concat(name, ', ') FROM saint_names n WHERE n.saint_id=s.id AND n.language='$code')")
                    }+source("s.wikidata_url"),null)
            "places" -> AidSchema(id,"places p","p.id","p.canonical_name","p.administrative_units","p.search_text",
                listOf(country("p.country"),AidField("Správní jednotky","Administrative units","p.administrative_units"),AidField("Druh místa","Place type","p.place_types"),AidField("Národní kód","National code","p.national_code"),AidField("Zeměpisná šířka","Latitude","p.latitude"),AidField("Zeměpisná délka","Longitude","p.longitude"),AidField("Historické a jazykové názvy","Historical and language variants","(SELECT group_concat(language || ': ' || name, char(10)) FROM place_names n WHERE n.place_id=p.id)"),source("p.wikidata_url")),
                countryFacet("p.country","SELECT DISTINCT country FROM places WHERE country<>'' ORDER BY country"))
            "manuscript-abbreviations" -> AidSchema(id,"abbreviations a","CAST(a.id AS TEXT)","a.abbreviation","a.expansion","a.search_text",
                listOf(AidField("Rozvedení","Expansion","a.expansion"),AidField("Jazyk","Language","a.language","language"),AidField("Kategorie","Category","a.category"),AidField("Období","Period","a.period"),AidField("Oblast","Region","a.region"),AidField("Varianty","Variants","a.variants"),AidField("Poznámka","Notes","a.notes"),source("a.source_url")),
                AidFacet("Jazyk","Language","a.language","SELECT DISTINCT language FROM abbreviations ORDER BY language","language"))
            "numismatics","sigillography" -> AidSchema(id,"objects o","o.wikidata_id","o.display_name","o.description","o.display_name || ' ' || o.description",
                listOf(AidField("Popis","Description","o.description"),AidField("Datové sady","Datasets","(SELECT group_concat(d.name, ', ') FROM object_datasets od JOIN datasets d ON d.id=od.dataset_id WHERE od.object_id=o.wikidata_id)"),source("o.wikidata_url")),
                null,AidViewKind.IMAGE,"objects","o.wikidata_id IN (SELECT object_id FROM object_search WHERE object_search MATCH ?)")
            "epigraphy-noble-tombs" -> AidSchema(id,"monuments m JOIN entities e ON e.qid=m.qid","m.qid","e.label","''","e.label",
                listOf(AidField("Popis","Description","e.descriptions_json","localized"),AidField("Nápis","Inscription","(SELECT group_concat(text, char(10)) FROM inscriptions i WHERE i.monument_qid=m.qid)"),source("e.source_url")),null,AidViewKind.IMAGE,"monuments","m.qid IN (SELECT qid FROM search WHERE search MATCH ?)","e.labels_json")
            else -> {
                require(id in setOf("heraldry","heraldry-nobility","heraldry-ecclesiastical","heraldry-chivalric-orders","heraldry-burgher-patrician","heraldry-personal-coats-of-arms"))
                val order="CASE language WHEN '$lang' THEN 0 WHEN 'en' THEN 1 WHEN 'cs' THEN 2 ELSE 3 END, language"
                val title="COALESCE((SELECT NULLIF(label,'') FROM entity_labels l WHERE l.wikidata_id=e.wikidata_id AND NULLIF(label,'') IS NOT NULL ORDER BY $order LIMIT 1),e.wikidata_id)"
                val description="(SELECT NULLIF(description,'') FROM entity_labels l WHERE l.wikidata_id=e.wikidata_id AND NULLIF(description,'') IS NOT NULL ORDER BY $order LIMIT 1)"
                AidSchema(id,"entities e","e.wikidata_id",title,description,
                    "e.wikidata_id || ' ' || COALESCE((SELECT group_concat(label || ' ' || COALESCE(description,''),' ') FROM entity_labels l WHERE l.wikidata_id=e.wikidata_id),'') || ' ' || COALESCE((SELECT group_concat(alias,' ') FROM entity_aliases a WHERE a.wikidata_id=e.wikidata_id),'')",
                    listOf(AidField("Popis","Description",description),country("(SELECT group_concat(country_code,', ') FROM entity_countries c WHERE c.wikidata_id=e.wikidata_id)"),AidField("Zeměpisná šířka","Latitude","e.latitude"),AidField("Zeměpisná délka","Longitude","e.longitude"),source("'https://www.wikidata.org/wiki/' || e.wikidata_id")),
                    countryFacet("e.wikidata_id IN (SELECT wikidata_id FROM entity_countries WHERE country_code=?)","SELECT DISTINCT country_code FROM entity_countries ORDER BY country_code"),AidViewKind.IMAGE,"heraldry")
            }
        }
    }
    fun allFieldsSearch(schema: AidSchema): String {
        val extra=when(schema.imageFamily) {
            "objects" -> listOf("(SELECT group_concat(COALESCE(c.value_text,'') || ' ' || COALESCE(c.amount,'') || ' ' || COALESCE(c.time_value,'') || ' ' || COALESCE(c.latitude,'') || ' ' || COALESCE(c.longitude,'') || ' ' || COALESCE(e.labels_json,'') || ' ' || COALESCE(u.labels_json,''),' ') FROM claims c LEFT JOIN entities e ON e.wikidata_id=c.entity_id LEFT JOIN entities u ON u.wikidata_id=REPLACE(c.unit_id,'http://www.wikidata.org/entity/','') WHERE c.object_id=o.wikidata_id AND COALESCE(c.rank,'')<>'deprecated')")
            "monuments" -> listOf("(SELECT group_concat(COALESCE(x.labels_json,x.label),' ') FROM entities x WHERE instr(COALESCE(m.type_ids_json,'') || COALESCE(m.country_ids_json,'') || COALESCE(m.location_ids_json,'') || COALESCE(m.material_ids_json,'') || COALESCE(m.creator_ids_json,''),'\"' || x.qid || '\"')>0)","m.inception_json","(SELECT group_concat(COALESCE(x.labels_json,x.label),' ') FROM monument_person mp JOIN entities x ON x.qid=mp.person_qid WHERE mp.monument_qid=m.qid)")
            else -> emptyList()
        }
        return (listOf(schema.search)+schema.fields.map { it.sql }+extra).joinToString(" || ' ' || ") { "COALESCE(($it),'')" }
    }
    fun imageExists(s: AidSchema): String = when(s.imageFamily) {
        "heraldry" -> "EXISTS(SELECT 1 FROM images i WHERE i.wikidata_id=e.wikidata_id AND i.missing=0 AND i.display_policy IN ('public_domain','attribution_required'))"
        "objects" -> "EXISTS(SELECT 1 FROM images i WHERE i.object_id=o.wikidata_id AND i.display_policy IN ('public_domain','attribution_required'))"
        "monuments" -> "EXISTS(SELECT 1 FROM monument_media mm JOIN media i ON i.file_name=mm.file_name WHERE mm.monument_qid=m.qid AND i.missing=0 AND i.reuse_status IN ('public_domain','attribution_required'))"
        else -> "0"
    }
    fun imageSql(family: String?): String = when(family) {
        "heraldry" -> "SELECT COALESCE(NULLIF(i.resolved_file_name,''),i.file_name),i.display_policy,i.license_short_name,i.license_url,i.author,i.credit,i.usage_terms,COALESCE((SELECT group_concat(restriction,'; ') FROM image_restrictions r WHERE r.image_id=i.id),'') FROM images i WHERE i.wikidata_id=? AND i.missing=0 ORDER BY i.file_name,i.id"
        "objects" -> "SELECT COALESCE(NULLIF(resolved_filename,''),filename),display_policy,license_short_name,license_url,author,credit,usage_terms,restrictions_json FROM images WHERE object_id=? ORDER BY filename"
        else -> "SELECT COALESCE(NULLIF(i.resolved_file_name,''),i.file_name),i.reuse_status,i.license_name,i.license_url,i.author,COALESCE(NULLIF(i.attribution,''),i.credit),i.usage_terms,i.restrictions FROM media i JOIN monument_media mm ON mm.file_name=i.file_name WHERE mm.monument_qid=? AND i.missing=0 ORDER BY i.file_name"
        }
    fun extrasSql(family: String?): List<String> = when(family) {
        "objects" -> listOf("SELECT p.labels_json,c.value_text,c.entity_id,c.amount,c.unit_id,c.time_value,c.time_precision,c.latitude,c.longitude,e.labels_json FROM claims c LEFT JOIN properties p ON p.property_id=c.property_id LEFT JOIN entities e ON e.wikidata_id=c.entity_id WHERE c.object_id=? AND COALESCE(c.rank,'')<>'deprecated' ORDER BY c.property_id,c.statement_order LIMIT 128")
        "monuments" -> listOf("SELECT type_ids_json,country_ids_json,location_ids_json,material_ids_json,creator_ids_json,inception_json FROM monuments WHERE qid=?", "SELECT DISTINCT e.labels_json,e.label FROM monument_person mp JOIN entities e ON e.qid=mp.person_qid WHERE mp.monument_qid=?")
        else -> emptyList()
    }
    private val combiningMarks=Regex("\\p{M}+")
    fun fold(value: String): String = if(value.all { it.code<128 }) value.lowercase(Locale.ROOT)
        else combiningMarks.replace(Normalizer.normalize(value,Normalizer.Form.NFD),"").lowercase(Locale.ROOT)
    fun like(value: String): String = "%"+value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"
    fun fts(value: String): String = Regex("[\\p{L}\\p{N}]+").findAll(value).take(12).joinToString(" AND ") { "\"${it.value}\"*" }
}
