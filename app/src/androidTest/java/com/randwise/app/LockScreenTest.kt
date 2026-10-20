package com.randwise.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.randwise.app.security.PasscodeKind
import com.randwise.app.ui.screens.LockScreen
import com.randwise.app.ui.theme.RandWiseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(kind: PasscodeKind?, lockedUntil: Long = 0, biometric: Boolean = false, onSubmit: (String) -> Unit = {}) {
        compose.setContent {
            RandWiseTheme {
                LockScreen(
                    kind = kind, biometricAvailable = biometric, error = null, lockedUntil = lockedUntil, checking = false,
                    onSubmit = { onSubmit(String(it)) }, onBiometric = {}, onForgot = {},
                )
            }
        }
    }

    @Test
    fun pinKeypadSubmitsTheDigitsTyped() {
        var submitted: String? = null
        show(PasscodeKind.PIN) { submitted = it }
        "482915".forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.onNodeWithContentDescription("6 digits entered").assertIsDisplayed()
        compose.onNodeWithText("Unlock").performClick()
        assertEquals("482915", submitted)
        // Digits are cleared immediately so they never linger on screen.
        compose.onNodeWithContentDescription("0 digits entered").assertIsDisplayed()
    }

    @Test
    fun unlockDoesNothingBeforeSixDigits() {
        var submitted: String? = null
        show(PasscodeKind.PIN) { submitted = it }
        "4829".forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.onNodeWithText("Unlock").performClick()
        assertNull(submitted)
    }

    @Test
    fun deleteRemovesTheLastDigit() {
        show(PasscodeKind.PIN)
        "123".forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.onNodeWithContentDescription("Delete digit").performClick()
        compose.onNodeWithContentDescription("2 digits entered").assertIsDisplayed()
    }

    @Test
    fun lockoutShowsACountdown() {
        show(PasscodeKind.PIN, lockedUntil = System.currentTimeMillis() + 90_000)
        compose.onNodeWithText("Too many attempts", substring = true).assertIsDisplayed()
    }

    @Test
    fun fingerprintShortcutOnlyWhenAvailable() {
        show(PasscodeKind.PIN, biometric = true)
        compose.onNodeWithContentDescription("Use fingerprint or face").assertIsDisplayed()
    }
}
