package com.vestigiascriptorium.android.ui.main

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vestigiascriptorium.android.aids.*
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryUiTest {
    @get:Rule val rule=createComposeRule()
    @org.junit.Before fun requireUnlockedDevice() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeFalse("Unlock the physical phone to run UI interaction tests",
            context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
    }
    @Test fun moduleCatalogueListsEveryDesktopModuleWithoutReleaseLabels() {
        rule.setContent { VestigiaScriptoriumTheme { ModulesScreen(Copy(true),{}, {}) } }
        for(title in listOf("Genealogie","Rukopisy","Heraldika","Sfragistika","Epigrafika","Paleografika")) {
            rule.onNode(hasScrollAction()).performScrollToNode(hasText(title))
            rule.onNodeWithText(title).assertIsDisplayed()
        }
        rule.onNodeWithText("Další moduly").assertDoesNotExist()
        rule.onNodeWithText("Připravujeme").assertDoesNotExist()
    }
    @Test fun installedAidOpensContentDirectly() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val meta=context.assets.open("research-aids.json").bufferedReader().use { AidNetwork.parse(it.readText()) }.getValue("historical-units")
        var opened: String?=null
        val library=AidLibraryState(metadata=mapOf(meta.id to meta),installed=mapOf(meta.id to meta),jobs=mapOf(meta.id to AidJob("installed")))
        rule.setContent {
            val configuration=android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply { setLocale(java.util.Locale.forLanguageTag("cs")) }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                VestigiaScriptoriumTheme { VestigiaApp(initialTab=1,library=library,onOpenAid={opened=it}) }
            }
        }
        rule.onNodeWithTag("aids-catalogue")
            .performScrollToNode(hasText("Historické míry a váhy"))
        rule.onNodeWithText("Historické míry a váhy").performClick()
        rule.runOnIdle { assertEquals(meta.id,opened) }
        rule.onNodeWithText("Po instalaci").assertDoesNotExist()
    }
    @Test fun desktopTemplatesAreHiddenAndOnlyOfferLegacyUninstall() {
        val meta=AidMetadata("heraldry-templates","heraldry_template_catalog.db",100,4096,"a".repeat(64),3)
        val library=AidLibraryState(installed=mapOf(meta.id to meta),jobs=mapOf(meta.id to AidJob("retired")))
        rule.setContent {
            val configuration=android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply { setLocale(java.util.Locale.forLanguageTag("cs")) }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                VestigiaScriptoriumTheme { VestigiaApp(initialTab=1,library=library) }
            }
        }
        rule.onNodeWithText("Heraldické šablony (desktop)").assertDoesNotExist()
        rule.onNode(hasText("Stahování") and hasClickAction()).performClick()
        rule.onNodeWithTag("downloads-list").performScrollToNode(hasText("Heraldické šablony (desktop)"))
        rule.onNodeWithText("Otevřít").assertDoesNotExist()
        rule.onNodeWithText("Aktualizovat").assertDoesNotExist()
        rule.onNodeWithText("Odinstalovat").assertExists()
    }
    @Test fun uninstallRequiresConfirmationAndTargetsOnlySelectedAid() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val meta=context.assets.open("research-aids.json").bufferedReader().use { AidNetwork.parse(it.readText()) }.getValue("historical-units")
        var removed: String?=null
        val state=AidLibraryState(metadata=mapOf(meta.id to meta),installed=mapOf(meta.id to meta),jobs=mapOf(meta.id to AidJob("installed")))
        rule.setContent { VestigiaScriptoriumTheme {
            DownloadsScreen(Copy(true),state,{}, {}, {}, {removed=it}, {}, {}, {})
        }}
        rule.onNodeWithText("Odinstalovat").performScrollTo().performClick()
        rule.onNodeWithText("Odinstalovat pomůcku?").assertIsDisplayed()
        rule.runOnIdle { assertEquals(null,removed) }
        rule.onNodeWithText("Zpět").performClick()
        rule.runOnIdle { assertEquals(null,removed) }
        rule.onNodeWithText("Odinstalovat").performClick()
        rule.onNode(hasText("Odinstalovat") and hasAnyAncestor(isDialog())).performClick()
        rule.runOnIdle { assertEquals(meta.id,removed) }
    }
}
