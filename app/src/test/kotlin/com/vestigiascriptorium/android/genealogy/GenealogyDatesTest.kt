package com.vestigiascriptorium.android.genealogy

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class GenealogyDatesTest {
    @Test fun fullDateUsesLocaleOrderAndFourDigitHistoricalYear() {
        for(locale in listOf(Locale.forLanguageTag("cs-CZ"),Locale.US,Locale.UK,Locale.GERMANY,Locale.FRANCE)) {
            val text=GenealogyDates.format("1820-02-03",locale)
            assertTrue(text.contains("1820"));assertEquals("1820-02-03",GenealogyDates.parse(text,"date",locale))
        }
        assertEquals("1820-02-03",GenealogyDates.parse("2/3/1820","date",Locale.US))
        assertEquals("1820-03-02",GenealogyDates.parse("02/03/1820","date",Locale.UK))
    }
    @Test fun unpaddedDayAndMonthRemainStrictCalendarDatesInLocaleOrder() {
        assertEquals("1691-03-21",GenealogyDates.parse("21.3.1691","date",Locale.forLanguageTag("cs-CZ")))
        assertEquals("1691-03-21",GenealogyDates.parse("21.03.1691","date",Locale.forLanguageTag("cs-CZ")))
        assertEquals("1691-03-02",GenealogyDates.parse("2/3/1691","date",Locale.UK))
        assertEquals("1691-02-03",GenealogyDates.parse("2/3/1691","date",Locale.US))
        assertNull(GenealogyDates.parse("31.4.1691","date",Locale.forLanguageTag("cs-CZ")))
        assertNull(GenealogyDates.parse("29.2.1691","date",Locale.forLanguageTag("cs-CZ")))
    }
    @Test fun yearPrecisionAndInvalidDatesDoNotBecomeInventedFullDates() {
        assertEquals("1820",GenealogyDates.parse("1820","year",Locale.US))
        assertNull(GenealogyDates.full("1820"));assertNull(GenealogyDates.parse("1820","date",Locale.US))
        assertNull(GenealogyDates.parse("2/29/1821","date",Locale.US))
        assertEquals("1820-02-29",GenealogyDates.parse("2/29/1820","date",Locale.US))
        assertNull(GenealogyDates.parse("0000","year",Locale.US))
    }
    @Test fun mentionRangeRespectsYearPrecisionAndLegacyDatesStayVerbatim() {
        assertTrue(GenealogyDates.ordered("1820","1820-01-01"))
        assertFalse(GenealogyDates.ordered("1821","1820"))
        assertEquals("kolem 1820",GenealogyDates.format("kolem 1820",Locale.US))
        assertEquals(1820,GenealogyDates.year("kolem 1820"));assertNull(GenealogyDates.year("1820–1830"))
    }
}
