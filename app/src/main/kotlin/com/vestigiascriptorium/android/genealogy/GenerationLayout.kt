package com.vestigiascriptorium.android.genealogy

/** Partners/co-parents share a row. Parent-child edges determine generation depth. */
internal class GenerationLayout(tree: FamilyTree,presentation: GenealogyPresentation) {
    val levels: Map<String,Int>
    init {
        val groups=tree.people.associate {it.id to it.id}.toMutableMap()
        fun root(id: String): String {var key=id;while(groups.getValue(key)!=key)key=groups.getValue(key);var next=id;while(groups.getValue(next)!=next) {val old=groups.getValue(next);groups[next]=key;next=old};return key}
        fun join(a: String,b: String) {val first=root(a);val second=root(b);if(first!=second)groups[second]=first}
        presentation.connections.forEach {join(it.first,it.second)}
        presentation.families.forEach {family ->family.parents.zipWithNext().forEach {(a,b) ->join(a,b)}}
        val memberships=tree.people.associate {it.id to root(it.id)}
        val edges=presentation.parents.map {memberships.getValue(it.parent) to memberships.getValue(it.child)}.filter {it.first!=it.second}.distinct()
        val children=edges.groupBy({it.first},{it.second})
        val pending=memberships.values.toSet().associateWith {0}.toMutableMap()
        edges.forEach {pending[it.second]=pending.getValue(it.second)+1}
        val depths=pending.keys.associateWith {0}.toMutableMap()
        val queue=ArrayDeque(pending.filterValues {it==0}.keys)
        while(queue.isNotEmpty()) {
            val parent=queue.removeFirst()
            children[parent].orEmpty().forEach {child ->
                depths[child]=maxOf(depths.getValue(child),depths.getValue(parent)+1)
                pending[child]=pending.getValue(child)-1;if(pending[child]==0)queue.add(child)
            }
        }
        // Incompatible cross-generation partnerships must not cause an infinite layout loop.
        levels=memberships.mapValues {depths.getValue(it.value)}
    }
    fun boxes(people: List<Person>,heights: Map<String,Float>): Map<String,GraphBox> {
        val rows=people.groupBy {levels.getValue(it.id)}.toSortedMap()
        var top=people.filter {levels[it.id]==0}.minOfOrNull {it.y} ?: 0f
        return buildMap {
            rows.forEach {(_,row) ->
                var right=Float.NEGATIVE_INFINITY
                row.sortedWith(compareBy<Person> {it.x}.thenBy {it.id}).forEach {p ->
                    val x=maxOf(p.x,right);put(p.id,GraphBox(x,top,244f,heights[p.id] ?: 88f));right=x+300f
                }
                top+=row.maxOf {heights[it.id] ?: 88f}+160f/3f
            }
        }
    }
}
