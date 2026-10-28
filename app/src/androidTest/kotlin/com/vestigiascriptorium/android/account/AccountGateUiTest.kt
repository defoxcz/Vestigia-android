package com.vestigiascriptorium.android.account

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.vestigiascriptorium.android.MainActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountGateUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun unauthenticatedLaunchShowsRequiredAccountGate() {
        val manager = AccountManager.get(rule.activity)
        // Never sign out a real user to prepare a test.
        assumeFalse(runBlocking { manager.hasAccess() })
        rule.waitUntil(10_000) { manager.state.value.ready }
        rule.onNodeWithText("Přihlásit se / Registrovat").assertIsDisplayed()
        rule.onNodeWithText("Moduly").assertDoesNotExist()
    }
}
