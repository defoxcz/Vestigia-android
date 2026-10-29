package com.vestigiascriptorium.android.genealogy

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

internal data class PersonRow(val person: Person,val year: Int?,val fromMention: Boolean,val search: String)
internal object PeopleIndex {
    fun key(value: String)=Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
    fun rows(tree: FamilyTree,locale: Locale): List<PersonRow> {
        val births=tree.events.filter {it.kind=="genealogy.birth"}.groupBy {it.roles["subject"]}
        val collator=Collator.getInstance(locale)
        return tree.people.map {person ->
            val born=births[person.id].orEmpty().firstNotNullOfOrNull {GenealogyDates.year(it.date)}
            val year=born ?: GenealogyDates.year(person.mentionedFrom)
            PersonRow(person,year,born==null && year!=null,key("${person.name} ${year ?: ""}"))
        }.sortedWith {a,b ->
            val surname=collator.compare(a.person.surname.ifBlank {a.person.name},b.person.surname.ifBlank {b.person.name})
            val given=collator.compare("${a.person.givenName} ${a.person.middleName}","${b.person.givenName} ${b.person.middleName}")
            if(surname!=0)surname else if(given!=0)given else if(a.year!=b.year)compareValues(a.year,b.year) else a.person.id.compareTo(b.person.id)
        }
    }
    fun filter(rows: List<PersonRow>,query: String): List<PersonRow> {
        val words=key(query).trim().split(Regex("\\s+")).filter {it.isNotEmpty()}
        return if(words.isEmpty())rows else rows.filter {row -> words.all {row.search.contains(it)}}
    }
}
