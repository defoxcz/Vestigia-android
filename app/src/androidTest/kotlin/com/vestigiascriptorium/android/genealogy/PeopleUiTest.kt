package com.vestigiascriptorium.android.genealogy

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import org.junit.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PeopleUiTest {
    @get:Rule val rule=createComposeRule()
    @Before fun unlocked() {Assert.assertFalse("Unlock phone for UI tests",InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)}
    @Test fun listSuggestsAndFiltersLatestImeReplacementAndSelectsStableId() {
        val anna=Person(id="anna",givenName="Anna",middleName="Marie",surname="Nováková",mentionedFrom="1840")
        val jan=Person(id="jan",givenName="Jan",surname="Adamec",mentionedFrom="1780")
        val tree=FamilyTree(title="Seznam osob",people=listOf(anna,jan),events=listOf(LifeEvent(kind="genealogy.birth",date="1820-02-03",roles=mapOf("subject" to anna.id))))
        var selected: String?=null
        rule.setContent {VestigiaScriptoriumTheme {PeopleScreen(tree,Copy(true),{},onSelect={selected=it.id})}}
        rule.onNodeWithTag("person-row-anna").assertIsDisplayed()
        rule.onNodeWithText("1820").assertIsDisplayed()
        rule.onNodeWithText("př. 1780").assertIsDisplayed()
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap -> File(target.cacheDir,"genealogy-people-test.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        rule.onNodeWithText("Hledat osobu").performTextInput("ad")
        rule.onNodeWithTag("person-suggestion-jan").assertIsDisplayed()
        rule.onNodeWithText("Hledat osobu").performTextReplacement("novak")
        rule.onNodeWithTag("person-suggestion-anna").assertIsDisplayed()
        rule.onNodeWithTag("person-row-jan").assertDoesNotExist()
        rule.onNodeWithText("Hledat osobu").performTextReplacement("Nováková ")
        rule.onNodeWithTag("person-suggestion-anna").performClick()
        rule.runOnIdle {Assert.assertEquals("anna",selected)}
    }
    @Test fun dateEditorValidatesFullDateAndKeepsYearPrecision() {
        var value=""
        var valid=true
        rule.setContent {VestigiaScriptoriumTheme {Column {GenealogyDateField("Datum","",Copy(true)) {v,ok ->value=v;valid=ok}}}}
        val field=rule.onNode(hasSetTextAction() and hasText("Datum"))
        field.performTextInput(GenealogyDates.format("1821-02-28").replace("28","29"))
        rule.runOnIdle {Assert.assertFalse(valid)}
        field.performTextReplacement(GenealogyDates.format("1820-02-29"))
        rule.runOnIdle {Assert.assertTrue(valid);Assert.assertEquals("1820-02-29",value)}
        rule.onNodeWithContentDescription("Datum · Pouze rok").performClick()
        field.assertTextContains("1820")
        rule.runOnIdle {Assert.assertEquals("1820",value)}
        rule.onNodeWithContentDescription("Datum · Celé datum").performClick()
        rule.runOnIdle {Assert.assertEquals("",value)}
    }
}
