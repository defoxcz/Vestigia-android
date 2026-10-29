package com.vestigiascriptorium.android.genealogy

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.text.Normalizer
import java.time.LocalDate

internal class ImportProblem(val reason: String): IllegalArgumentException(reason)
internal data class ImportMediaRef(val tree: String,val person: String,val path: String,val portrait: Boolean,val caption: String="")
internal data class GenealogyImportData(val format: String,val trees: List<FamilyTree>,val media: List<ImportMediaRef> = emptyList(),val warnings: List<String> = emptyList())
internal data class GedNode(val tag: String,val value: String="",val xref: String="",val children: MutableList<GedNode> = mutableListOf()) {
    fun first(tag: String)=children.firstOrNull {it.tag==tag}
    fun all(tag: String)=children.filter {it.tag==tag}
    fun text()=buildString {append(value);children.forEach {if(it.tag=="CONT")append("\n"+it.value) else if(it.tag=="CONC")append(it.value)}}.replace("@@","@")
}
internal object GedcomImport {
    fun parse(bytes: ByteArray,title: String,cancel: ()->Unit={}): GenealogyImportData {
        if(bytes.size>32*1024*1024)throw ImportProblem("GEDCOM přesahuje limit 32 MB.")
        val text=decode(bytes)
        val records=mutableListOf<GedNode>();val stack=mutableListOf<GedNode>();val ids=mutableMapOf<String,GedNode>()
        val expression=Regex("^([0-9]+) (?:(@[^@\\s]+@) )?([A-Za-z0-9_]+)(?: (.*))?$")
        var count=0
        text.lineSequence().forEachIndexed {index,line ->
            cancel();if(line.isEmpty())return@forEachIndexed
            if(++count>250000 || line.length>65536)throw ImportProblem("GEDCOM obsahuje příliš mnoho nebo příliš dlouhé řádky.")
            val match=expression.matchEntire(line) ?: throw ImportProblem("Neplatný řádek GEDCOM ${index+1}.")
            val level=match.groupValues[1].toIntOrNull() ?: throw ImportProblem("Neplatná úroveň GEDCOM.")
            if(level>64 || level>stack.size)throw ImportProblem("Neplatné zanoření na řádku ${index+1}.")
            val node=GedNode(match.groupValues[3].uppercase(),match.groupValues[4],match.groupValues[2])
            if(node.xref.isNotEmpty()) {if(node.xref in ids)throw ImportProblem("Duplicitní identifikátor GEDCOM.");ids[node.xref]=node}
            while(stack.size>level)stack.removeAt(stack.lastIndex)
            if(level==0)records+=node else stack.last().children+=node
            stack+=node
        }
        if(records.firstOrNull()?.tag!="HEAD" || records.lastOrNull()?.tag!="TRLR")throw ImportProblem("Chybí hlavička HEAD nebo ukončení TRLR.")
        val version=records.first().first("GEDC")?.first("VERS")?.value.orEmpty()
        if(version!="5.5.1" && !version.startsWith("7."))throw ImportProblem("Podporován je GEDCOM 5.5.1 nebo 7.")
        if(version.startsWith("7.") && !isUtf8(bytes))throw ImportProblem("GEDCOM 7 vyžaduje UTF-8.")
        val warnings=linkedSetOf<String>()
        val treeId=newId();val people=mutableListOf<Person>();val events=mutableListOf<LifeEvent>();val evidence=mutableListOf<Evidence>();val media=mutableListOf<ImportMediaRef>()
        val peopleIds=records.filter {it.tag=="INDI"}.associate {node ->if(node.xref.isEmpty())throw ImportProblem("Osoba nemá identifikátor.");node.xref to newId()}
        if(peopleIds.isEmpty())throw ImportProblem("Soubor neobsahuje osoby.")
        fun person(ref: String): String=peopleIds[ref] ?: throw ImportProblem("Vazba odkazuje na chybějící osobu.")
        fun note(node: GedNode): String=node.all("NOTE").plus(node.all("SNOTE")).joinToString("\n") {n ->ids[n.value]?.text() ?: n.text()}
        fun citation(node: GedNode,person: String) {node.all("SOUR").forEach {source ->
            val record=ids[source.value]
            val caption=listOf(record?.first("TITL")?.text() ?: source.value,source.first("PAGE")?.text().orEmpty()).filter {it.isNotBlank()}.joinToString(" · ")
            record?.all("OBJE").orEmpty().forEach {ref ->val objectNode=ids[ref.value] ?: ref;objectNode.all("FILE").forEach {file ->if(file.value.isNotBlank())media+=ImportMediaRef(treeId,person,file.value,false,caption)}}
            evidence+=Evidence(person=person,caption=caption.ifBlank {"Pramen"},note=listOf(record?.first("AUTH")?.text().orEmpty(),record?.first("PUBL")?.text().orEmpty(),source.first("DATA")?.first("TEXT")?.text().orEmpty(),note(source)).filter {it.isNotBlank()}.joinToString("\n"))
        }}
        fun objectRefs(node: GedNode,id: String) {node.all("OBJE").forEachIndexed {index,ref ->
            val objectNode=ids[ref.value] ?: ref
            objectNode.all("FILE").forEachIndexed {fileIndex,file -> if(file.value.isNotBlank())media+=ImportMediaRef(treeId,id,file.value,index==0 && fileIndex==0,objectNode.first("TITL")?.text() ?: file.first("TITL")?.text().orEmpty())}
        }}
        val knownEvents=mapOf("BIRT" to "genealogy.birth","CHR" to "genealogy.baptism","BAPM" to "genealogy.baptism","DEAT" to "genealogy.death","BURI" to "genealogy.burial")
        records.filter {it.tag=="INDI"}.forEach {individual ->
            cancel();val id=person(individual.xref);val name=individual.first("NAME")
            val raw=name?.text().orEmpty();val before=raw.substringBefore('/').trim();val surname=name?.first("SURN")?.value ?: if('/' in raw)raw.substringAfter('/').substringBefore('/').trim() else ""
            val given=name?.first("GIVN")?.value ?: before
            val parts=given.trim().split(Regex("\\s+"),limit=2)
            val names=individual.all("NAME").drop(1).map {it.text()}
            val details=mutableListOf(note(individual))
            if(names.isNotEmpty())details+="Další jména: "+names.joinToString("; ")
            individual.children.filter {it.tag !in setOf("NAME","SEX","NOTE","SNOTE","FAMC","FAMS","OBJE","SOUR") && it.tag !in knownEvents && it.tag !in setOf("EVEN","RESI","OCCU")}.forEach {warnings+="Další atributy a rozšíření jsou zachovány ve zdrojové kopii."}
            people+=Person(id=id,givenName=parts.firstOrNull().orEmpty().ifBlank {if(surname.isBlank())"?" else ""},middleName=parts.getOrElse(1){""},surname=surname,gender=when(individual.first("SEX")?.value){"M"->"male";"F"->"female";else->"unknown"},note=details.filter {it.isNotBlank()}.joinToString("\n"))
            citation(individual,id);objectRefs(individual,id)
            individual.children.filter {it.tag in knownEvents || it.tag in setOf("EVEN","RESI","OCCU")}.forEach {event ->
                events+=LifeEvent(kind=knownEvents[event.tag] ?: "genealogy.mentioned",title=if(event.tag in knownEvents)"" else event.first("TYPE")?.value ?: event.tag,
                    date=date(event.first("DATE")?.value.orEmpty()),place=event.first("PLAC")?.text().orEmpty(),note=listOf(event.value.takeIf {it!="Y"}.orEmpty(),note(event)).filter {it.isNotBlank()}.joinToString("\n"),roles=mapOf("subject" to id))
                citation(event,id);objectRefs(event,id)
            }
        }
        val primaryEvents=mutableMapOf<String,Int>()
        events.forEachIndexed {index,event ->if(event.kind=="genealogy.birth")event.roles["subject"]?.let {primaryEvents.putIfAbsent(it,index)}}
        records.filter {it.tag=="FAM"}.forEach {family ->
            cancel()
            val father=family.first("HUSB")?.value?.takeIf {it.isNotBlank() && it!="@VOID@"}?.let(::person);val mother=family.first("WIFE")?.value?.takeIf {it.isNotBlank() && it!="@VOID@"}?.let(::person)
            val marriages=family.all("MARR")
            val subjects=listOfNotNull(father,mother)
            if(subjects.size==2) {
                val nodes=if(marriages.isEmpty())listOf<GedNode?>(null) else marriages
                nodes.forEach {wedding ->events+=LifeEvent(kind=if(wedding==null)"genealogy.spousal-record" else "genealogy.marriage",date=date(wedding?.first("DATE")?.value.orEmpty()),place=wedding?.first("PLAC")?.text().orEmpty(),note=if(wedding==null)note(family) else note(wedding),roles=mapOf("subject" to subjects[0],"subject2" to subjects[1]))}
            } else if(marriages.isNotEmpty())warnings+="Některý sňatek nemá oba partnery; zdroj je zachován."
            family.all("CHIL").forEach childLoop@{child ->
                if(child.value=="@VOID@")return@childLoop
                val id=person(child.value)
                val pedigree=ids[child.value]?.all("FAMC")?.firstOrNull {it.value==family.xref}?.first("PEDI")?.value?.uppercase()?.takeIf {it.isNotBlank()}
                if(pedigree!=null && pedigree!="BIRTH")warnings+="Adoptivní a jiné nebiologické rodičovství nebylo převedeno na běžné rodiče; zůstává ve zdrojové kopii."
                else if(subjects.isNotEmpty()) {
                    val oldIndex=primaryEvents[id];val old=oldIndex?.let {events[it]}
                    val roles=mutableMapOf("subject" to id);father?.let {roles["genealogy.father"]=it};mother?.let {roles["genealogy.mother"]=it}
                    if(old!=null) {if(roles.any {(role,p) ->old.roles[role]!=null && old.roles[role]!=p})throw ImportProblem("Konfliktní rodiče v rodinných záznamech.");events[oldIndex!!]=old.copy(roles=old.roles+roles)}
                    else {primaryEvents[id]=events.size;events+=LifeEvent(kind="genealogy.parentage",roles=roles)}
                }
                citation(family,id)
            }
            if(family.children.any {it.tag !in setOf("HUSB","WIFE","CHIL","MARR","NOTE","SNOTE","SOUR","_VESTIGIA_SPOUSES")})warnings+="Další rodinné události jsou zachovány ve zdrojové kopii."
        }
        if(records.any {it.tag !in setOf("HEAD","TRLR","INDI","FAM","NOTE","SNOTE","SOUR","OBJE","SUBM")})warnings+="Další typy záznamů jsou zachovány ve zdrojové kopii."
        var tree=FamilyTree(id=treeId,title=title,people=people,events=events,evidence=evidence)
        try {GenealogyEdits.validate(tree)} catch(_: GenealogyProblem) {throw ImportProblem("Vztahy obsahují cyklus, konflikt rodičovských rolí nebo neplatné přiřazení. Import nebyl uložen.")}
        tree=layout(tree)
        return GenealogyImportData("GEDCOM $version",listOf(tree),media,warnings.toList())
    }
    fun date(value: String): String {
        val text=value.trim()
        if(text.matches(Regex("[0-9]{1,4}")))return text.toIntOrNull()?.takeIf {it in 1..9999}?.toString()?.padStart(4,'0') ?: text
        val m=Regex("([0-9]{1,2}) ([A-Z]{3}) ([0-9]{1,4})").matchEntire(text)
        if(m!=null) {
            val month=listOf("JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC").indexOf(m.groupValues[2])+1
            if(month>0)return try {LocalDate.of(m.groupValues[3].toInt(),month,m.groupValues[1].toInt()).toString()} catch(_: Exception){text}
        }
        return text
    }
    fun layout(tree: FamilyTree): FamilyTree {
        val parents=mutableMapOf<String,MutableSet<String>>()
        tree.events.filter {it.kind in setOf("genealogy.parentage","genealogy.birth")}.forEach {e ->e.roles["subject"]?.let {child ->listOfNotNull(e.roles["genealogy.father"],e.roles["genealogy.mother"]).forEach {parents.getOrPut(child){mutableSetOf()}.add(it)}}}
        val children=mutableMapOf<String,MutableList<String>>()
        parents.forEach {(child,set) ->set.forEach {children.getOrPut(it){mutableListOf()}.add(child)}}
        val left=tree.people.associate {it.id to parents[it.id].orEmpty().size}.toMutableMap();val levels=tree.people.associate {it.id to 0}.toMutableMap();val queue=ArrayDeque(left.filterValues {it==0}.keys)
        while(queue.isNotEmpty()) {val parent=queue.removeFirst();children[parent].orEmpty().forEach {child ->levels[child]=maxOf(levels.getValue(child),levels.getValue(parent)+1);left[child]=left.getValue(child)-1;if(left[child]==0)queue.add(child)}}
        val eventCounts=tree.events.groupingBy {it.roles["subject"] ?: it.roles["subject2"]}.eachCount()
        val rowHeight=maxOf(440,(eventCounts.values.maxOrNull() ?: 0)*120+100)
        val columns=mutableMapOf<Int,Int>()
        val people=tree.people.map {p ->val level=levels.getValue(p.id);val column=columns.getOrDefault(level,0);columns[level]=column+1;p.copy(x=column*560f,y=level*rowHeight.toFloat())}
        val positions=people.associateBy {it.id};val slots=mutableMapOf<String,Int>()
        return tree.copy(people=people,events=tree.events.map {e ->val id=e.roles["subject"] ?: e.roles.values.firstOrNull();val p=positions[id];val slot=slots.getOrDefault(id,0);if(id!=null)slots[id]=slot+1;e.copy(x=(p?.x ?: 0f)+280,y=(p?.y ?: 0f)+slot*120f+80)})
    }
    private fun isUtf8(bytes: ByteArray)=!(bytes.size>=2 && ((bytes[0]==0xff.toByte() && bytes[1]==0xfe.toByte()) || (bytes[0]==0xfe.toByte() && bytes[1]==0xff.toByte()) || bytes[0]==0.toByte() || bytes[1]==0.toByte()))
    private fun decode(bytes: ByteArray): String {
        val charset=when {bytes.size>=2 && bytes[0]==0xff.toByte() && bytes[1]==0xfe.toByte()->Charsets.UTF_16LE
            bytes.size>=2 && bytes[0]==0xfe.toByte() && bytes[1]==0xff.toByte()->Charsets.UTF_16BE
            bytes.size>=2 && bytes[1]==0.toByte()->Charsets.UTF_16LE
            bytes.size>=2 && bytes[0]==0.toByte()->Charsets.UTF_16BE
            else ->null}
        val header=bytes.take(2048).toByteArray().toString(Charsets.ISO_8859_1)
        val declared=Regex("(?m)^1 CHAR ([A-Za-z0-9_-]+)").find(header)?.groupValues?.get(1)?.uppercase()
        if(charset==null && declared=="ANSEL")return ansel(bytes)
        val selected=charset ?: when(declared){null,"UTF-8" ->Charsets.UTF_8;"ASCII"->Charsets.US_ASCII;else ->throw ImportProblem("Nepodporované kódování GEDCOM: $declared. Použijte UTF-8 nebo Unicode.")}
        return try {selected.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")} catch(_: Exception) {throw ImportProblem("Text neodpovídá deklarovanému kódování.")}
    }
    private fun ansel(bytes: ByteArray): String {
        val marks=mapOf(0xe1 to '\u0300',0xe2 to '\u0301',0xe3 to '\u0302',0xe4 to '\u0303',0xe5 to '\u0304',0xe6 to '\u0306',0xe7 to '\u0307',0xe8 to '\u0308',0xe9 to '\u030c',0xea to '\u030a',0xed to '\u0315',0xee to '\u030b',0xf0 to '\u0327',0xf1 to '\u0328',0xf6 to '\u0332',0xfe to '\u0313')
        val spacing=mapOf(0xa1 to 'Ł',0xa2 to 'Ø',0xa3 to 'Đ',0xa4 to 'Þ',0xa5 to 'Æ',0xa6 to 'Œ',0xa8 to '·',0xa9 to '♭',0xaa to '®',0xab to '±',0xae to 'ʼ',0xb0 to 'ʻ',0xb1 to 'ł',0xb2 to 'ø',0xb3 to 'đ',0xb4 to 'þ',0xb5 to 'æ',0xb6 to 'œ',0xb8 to 'ı',0xb9 to '£',0xba to 'ð',0xc3 to '©',0xc5 to '¿',0xc6 to '¡',0xcf to 'ß')
        val combining=StringBuilder()
        val result=buildString {bytes.forEach {byte ->val n=byte.toInt() and 255
            if(n in marks)combining.append(marks.getValue(n)) else {append(if(n<128)n.toChar() else spacing[n] ?: throw ImportProblem("Nerozpoznaný znak ANSEL."));append(combining);combining.clear()}
        }}
        if(combining.isNotEmpty())throw ImportProblem("Neúplná diakritika ANSEL.")
        return Normalizer.normalize(result,Normalizer.Form.NFC)
    }
}
