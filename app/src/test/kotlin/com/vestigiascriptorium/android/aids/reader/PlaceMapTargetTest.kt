package com.vestigiascriptorium.android.aids.reader

import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.util.Locale

class PlaceMapTargetTest {
    @Test fun coordinatesPinThePlaceAndEscapeItsLabel() {
        val target=PlaceMapTarget("Břeclav (centrum) & okolí","CZ",48.758,16.882)
        assertTrue(target.geoUri().startsWith("geo:48.758,16.882?q=48.758,16.882("))
        assertTrue(URLDecoder.decode(target.geoUri(),"UTF-8").contains("Břeclav (centrum) & okolí, CZ"))
        assertTrue(target.webUri().contains("mlat=48.758&mlon=16.882"))
    }
    @Test fun missingOrInvalidCoordinatesSearchByNameAndCountry() {
        for ((lat,lon) in listOf(null to null,48.0 to null,Double.NaN to 16.0,91.0 to 16.0,48.0 to 181.0)) {
            val target=PlaceMapTarget("Břeclav","CZ",lat,lon)
            assertEquals("geo:0,0?q=B%C5%99eclav%2C%20CZ",target.geoUri())
            assertEquals("https://www.openstreetmap.org/search?query=B%C5%99eclav%2C%20CZ",target.webUri())
        }
    }
    @Test fun coordinatesAreIndependentOfDeviceLocaleAndAllowZero() {
        val previous=Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            assertTrue(PlaceMapTarget("Test","",0.0,0.0).geoUri().startsWith("geo:0.0,0.0?"))
            assertTrue(PlaceMapTarget("Test","",48.5,16.5).webUri().contains("mlat=48.5&mlon=16.5"))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun offersMapsBeforeBrowserAndFallsBackIfMapAppDisappears() {
        val target=PlaceMapTarget("Břeclav","CZ",48.758,16.882)
        val launched=mutableListOf<Boolean>()
        assertTrue(openPlaceMap(target,{true},{_,map -> launched+=map;true}))
        assertEquals(listOf(true),launched)
        launched.clear()
        assertTrue(openPlaceMap(target,{it.startsWith("https:")},{_,map -> launched+=map;true}))
        assertEquals(listOf(false),launched)
        launched.clear()
        assertTrue(openPlaceMap(target,{true},{_,map -> launched+=map;!map}))
        assertEquals(listOf(true,false),launched)
        assertFalse(openPlaceMap(target,{false},{_,_ -> fail("No app available");true}))
    }

}
