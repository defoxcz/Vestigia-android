package com.vestigiascriptorium.android.ui.main

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vestigiascriptorium.android.aids.*
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class InstalledOnlyUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun switchStaysEnabledAfterTheCatalogueIsRecreated() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeFalse(context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        val name="installed-only-ui-${UUID.randomUUID()}"
        val settings=AidPreferences(context,name)
        val metadata=context.assets.open("research-aids.json").bufferedReader().use { AidNetwork.parse(it.readText()) }
        val installed=metadata.getValue("historical-units")
        val generation=mutableIntStateOf(0)
        try {
            rule.setContent {
                key(generation.intValue) {
                    var library by remember { mutableStateOf(AidLibraryState(metadata=metadata,installed=mapOf(installed.id to installed),installedOnly=AidPreferences(context,name).installedOnly)) }
                    VestigiaScriptoriumTheme { AidsScreen(Copy(true),library,{}, {},onInstalledOnly={ settings.installedOnly=it;library=library.copy(installedOnly=it) }) }
                }
            }
            rule.onNodeWithTag("aids-catalogue").performScrollToNode(hasContentDescription("Pouze nainstalované"))
            rule.onNodeWithContentDescription("Pouze nainstalované").assertIsOff().performClick().assertIsOn()
            rule.runOnIdle { generation.intValue++ }
            rule.onNodeWithTag("aids-catalogue").performScrollToNode(hasContentDescription("Pouze nainstalované"))
            rule.onNodeWithContentDescription("Pouze nainstalované").assertIsOn()
            rule.onNodeWithTag("aids-catalogue").performScrollToNode(hasText("Historické míry a váhy"))
            rule.onNodeWithText("Historické míry a váhy").assertIsDisplayed()
        } finally { context.deleteSharedPreferences(name) }
    }
}
