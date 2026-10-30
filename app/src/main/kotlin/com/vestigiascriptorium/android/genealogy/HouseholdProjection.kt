package com.vestigiascriptorium.android.genealogy

/** Display-only grouping. UUIDs, event roles and source positions are unchanged. */
internal class HouseholdProjection(tree: FamilyTree,presentation: GenealogyPresentation,enabled: Boolean) {
    val owners: Map<String,String>
    init {
        val people=tree.people.associateBy {it.id}
        val fathers=presentation.parents.filter {it.role=="genealogy.father"}.map {it.parent}.toSet()
        val mothers=presentation.parents.filter {it.role=="genealogy.mother"}.map {it.parent}.toSet()
        fun male(id: String)=people.getValue(id).gender=="male" || id in fathers
        fun female(id: String)=people.getValue(id).gender=="female" || id in mothers
        val byParent=presentation.parents.groupBy {it.parent}
        val fatherByChild=presentation.parents.filter {it.role=="genealogy.father"}.associate {it.child to it.parent}
        owners=if(!enabled)emptyMap() else buildMap {
            tree.people.filter {female(it.id) && !male(it.id)}.forEach {wife ->
                val partners=presentation.person(wife.id).partners.map {it.person.id}
                val husband=partners.singleOrNull()?.takeIf(::male)
                // A mother-only family must not acquire a visually implied father.
                val children=byParent[wife.id].orEmpty().map {it.child}
                if(husband!=null && children.all {child ->fatherByChild[child]==husband})put(wife.id,husband)
            }
        }
    }
    fun owner(id: String)=owners[id] ?: id
    fun visible(people: List<Person>)=people.filter {it.id !in owners}
    fun family(family: FamilyConnection): FamilyConnection {
        val hosts=family.parents.map(::owner).distinct()
        return FamilyConnection(hosts,family.children.map(::owner).distinct().filter {it !in hosts})
    }
    fun aliases(boxes: Map<String,GraphBox>)=boxes+owners.mapValues {(_,host) ->boxes.getValue(host)}
}
