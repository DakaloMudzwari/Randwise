package com.randwise.app

import com.randwise.app.security.ClockStamp
import com.randwise.app.security.LockoutPolicy
import com.randwise.app.security.PasscodeHasher
import com.randwise.app.security.PasscodeKind
import com.randwise.app.security.PasscodeRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasscodeTest {

    @Test
    fun `hash verifies the right secret only`() {
        val salt = PasscodeHasher.newSalt()
        val hash = PasscodeHasher.hash("482915".toCharArray(), salt, iterations = 1_000)
        assertTrue(PasscodeHasher.matches("482915".toCharArray(), salt, hash, iterations = 1_000))
        assertFalse(PasscodeHasher.matches("482916".toCharArray(), salt, hash, iterations = 1_000))
    }

    @Test
    fun `same secret with different salts gives different hashes`() {
        val a = PasscodeHasher.hash("password1".toCharArray(), PasscodeHasher.newSalt(), 1_000)
        val b = PasscodeHasher.hash("password1".toCharArray(), PasscodeHasher.newSalt(), 1_000)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `lockout escalates and caps`() {
        assertEquals(0, LockoutPolicy.lockoutMillis(4))
        assertEquals(30_000, LockoutPolicy.lockoutMillis(5))
        assertEquals(60_000, LockoutPolicy.lockoutMillis(6))
        assertEquals(120_000, LockoutPolicy.lockoutMillis(7))
        assertEquals(15 * 60_000L, LockoutPolicy.lockoutMillis(30))
    }

    @Test
    fun `changing the clock cannot skip a lockout`() {
        val at = ClockStamp(wallMs = 1_000_000, elapsedMs = 50_000, boot = 7)
        // Same boot: only real elapsed time counts, wherever the wall clock is moved.
        assertEquals(20_000, LockoutPolicy.remainingMillis(30_000, at, ClockStamp(9_999_999_999, 60_000, 7)))
        assertEquals(0, LockoutPolicy.remainingMillis(30_000, at, ClockStamp(0, 90_000, 7)))
        // After a reboot the wall clock decides, but winding it back never adds time or ends the wait.
        assertEquals(30_000, LockoutPolicy.remainingMillis(30_000, at, ClockStamp(500_000, 1_000, 8)))
        assertEquals(10_000, LockoutPolicy.remainingMillis(30_000, at, ClockStamp(1_020_000, 1_000, 8)))
        // Unknown boot count falls back to the wall clock.
        val unknown = at.copy(boot = -1)
        assertEquals(30_000, LockoutPolicy.remainingMillis(30_000, unknown, ClockStamp(1_000_000, 1_000, -1)))
    }

    @Test
    fun `weak secrets are rejected`() {
        assertNotNull(PasscodeRules.validate(PasscodeKind.PIN, "1234"))
        assertNotNull(PasscodeRules.validate(PasscodeKind.PIN, "111111"))
        assertNotNull(PasscodeRules.validate(PasscodeKind.PIN, "123456"))
        assertNotNull(PasscodeRules.validate(PasscodeKind.PIN, "65432a"))
        assertNull(PasscodeRules.validate(PasscodeKind.PIN, "482915"))
        assertNotNull(PasscodeRules.validate(PasscodeKind.PASSWORD, "short1"))
        assertNotNull(PasscodeRules.validate(PasscodeKind.PASSWORD, "onlyletters"))
        assertNull(PasscodeRules.validate(PasscodeKind.PASSWORD, "Rand2026wise"))
    }
}
