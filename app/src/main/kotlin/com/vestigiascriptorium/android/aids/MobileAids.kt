package com.vestigiascriptorium.android.aids

internal object MobileAids {
    const val DESKTOP_TEMPLATES="heraldry-templates"
    val ids=setOf("places","saints","historical-units","manuscript-abbreviations","heraldry","heraldry-nobility",
        "heraldry-ecclesiastical","heraldry-chivalric-orders","heraldry-burgher-patrician","heraldry-personal-coats-of-arms",
        "numismatics","sigillography","epigraphy-noble-tombs")
    fun withCache(bundled: Map<String,AidMetadata>,cached: Map<String,AidMetadata>)=compatible(bundled+cached)
    fun compatible(metadata: Map<String,AidMetadata>)=metadata.filterKeys { it in ids }
}
