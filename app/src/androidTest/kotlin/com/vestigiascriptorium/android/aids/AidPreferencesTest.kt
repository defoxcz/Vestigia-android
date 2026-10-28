package com.vestigiascriptorium.android.aids

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.io.File

class AidPreferencesTest {
    @Test fun installedOnlyIsPersistedToDiskAndRestored() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="aid-preferences-test-${UUID.randomUUID()}"
        try {
            val preferences=AidPreferences(context,name)
            assertFalse(preferences.installedOnly)
            preferences.installedOnly=true
            val xml=File(context.applicationInfo.dataDir,"shared_prefs/$name.xml")
            val deadline=System.currentTimeMillis()+3000
            while(System.currentTimeMillis()<deadline && (!xml.exists() || !xml.readText().contains("value=\"true\""))) Thread.sleep(10)
            assertTrue(xml.readText().contains("name=\"installedOnly\" value=\"true\""))
            assertTrue(AidPreferences(context,name).installedOnly)
        } finally { context.deleteSharedPreferences(name) }
    }
}
