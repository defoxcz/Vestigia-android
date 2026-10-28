package com.vestigiascriptorium.android.aids.reader

import java.net.URLEncoder

internal data class PlaceMapTarget(val name: String, val country: String = "", val latitude: Double? = null, val longitude: Double? = null) {
    private val hasCoordinates get() = latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    private val label get() = listOf(name, country).filter { it.isNotBlank() }.joinToString(", ")
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    fun geoUri(): String = if (hasCoordinates) "geo:$latitude,$longitude?q=$latitude,$longitude(${encode(label)})" else "geo:0,0?q=${encode(label)}"
    fun webUri(): String = if (hasCoordinates) "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=14/$latitude/$longitude" else "https://www.openstreetmap.org/search?query=${encode(label)}"
}

/** Returns false only when neither an installed map app nor a browser can open the place. */
internal fun openPlaceMap(target: PlaceMapTarget, available: (String) -> Boolean, launch: (String, Boolean) -> Boolean): Boolean {
    val geo=target.geoUri()
    if(available(geo) && launch(geo,true)) return true
    val web=target.webUri()
    return available(web) && launch(web,false)
}
