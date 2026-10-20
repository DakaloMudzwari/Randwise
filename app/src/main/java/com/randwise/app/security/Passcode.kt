package com.randwise.app.security

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A numeric PIN (keypad) or a free-form password (keyboard). */
enum class PasscodeKind { PIN, PASSWORD }

/** Pure, testable hashing: PBKDF2-HMAC-SHA256 with a per-install random salt. Only the hash is stored. */
object PasscodeHasher {
    const val ITERATIONS = 120_000

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(secret: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison so timing can't reveal how much of a guess was right. */
    fun matches(secret: CharArray, salt: ByteArray, expected: ByteArray, iterations: Int = ITERATIONS): Boolean =
        MessageDigest.isEqual(hash(secret, salt, iterations), expected)
}

/** Free attempts, then an escalating wait: 30 s, 1 min, 2 min … capped at 15 min. */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private const val BASE_MS = 30_000L
    private const val MAX_MS = 15 * 60_000L

    fun lockoutMillis(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val step = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (BASE_MS shl step).coerceAtMost(MAX_MS)
    }
}

object PasscodeRules {
    /** Returns a user-facing problem with [secret], or null when it is acceptable. */
    fun validate(kind: PasscodeKind, secret: String): String? = when (kind) {
        PasscodeKind.PIN -> when {
            secret.length !in 6..10 || !secret.all(Char::isDigit) -> "Use 6 to 10 digits"
            secret.toSet().size == 1 -> "Avoid repeating one digit"
            "0123456789012".contains(secret) || "9876543210987".contains(secret) -> "Avoid sequences like 123456"
            else -> null
        }
        PasscodeKind.PASSWORD -> when {
            secret.length < 8 -> "Use at least 8 characters"
            secret.none(Char::isLetter) || secret.none(Char::isDigit) -> "Mix letters and numbers"
            else -> null
        }
    }
}

sealed interface VerifyResult {
    data object Success : VerifyResult
    data class Wrong(val attemptsBeforeLockout: Int) : VerifyResult
    data class LockedOut(val untilMillis: Long) : VerifyResult
}

/**
 * The user's own app password. Stored as salt + PBKDF2 hash in private prefs; failed attempts and
 * lockout survive app restarts so the wait can't be skipped by force-closing.
 */
class PasscodeManager(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("rw_lock", Context.MODE_PRIVATE)
    private val _kind = MutableStateFlow(readKind())
    /** null when no app password is set. */
    val kind: StateFlow<PasscodeKind?> = _kind.asStateFlow()

    val isSet: Boolean get() = _kind.value != null

    private fun readKind(): PasscodeKind? = prefs.getString(KIND, null)?.let { runCatching { PasscodeKind.valueOf(it) }.getOrNull() }

    fun set(secret: CharArray, kind: PasscodeKind) {
        val salt = PasscodeHasher.newSalt()
        val hash = PasscodeHasher.hash(secret, salt)
        prefs.edit()
            .putString(KIND, kind.name)
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(ITER, PasscodeHasher.ITERATIONS)
            .putInt(FAILS, 0)
            .putLong(UNTIL, 0)
            .commit()
        _kind.value = kind
    }

    fun lockoutRemainingMillis(): Long = (prefs.getLong(UNTIL, 0) - now()).coerceAtLeast(0)

    fun verify(secret: CharArray): VerifyResult {
        val until = prefs.getLong(UNTIL, 0)
        if (until > now()) return VerifyResult.LockedOut(until)
        val salt = prefs.getString(SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return VerifyResult.Success
        val hash = prefs.getString(HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return VerifyResult.Success
        if (PasscodeHasher.matches(secret, salt, hash, prefs.getInt(ITER, PasscodeHasher.ITERATIONS))) {
            prefs.edit().putInt(FAILS, 0).putLong(UNTIL, 0).apply()
            return VerifyResult.Success
        }
        val fails = prefs.getInt(FAILS, 0) + 1
        val wait = LockoutPolicy.lockoutMillis(fails)
        prefs.edit().putInt(FAILS, fails).putLong(UNTIL, if (wait > 0) now() + wait else 0).apply()
        return if (wait > 0) VerifyResult.LockedOut(now() + wait)
        else VerifyResult.Wrong(LockoutPolicy.FREE_ATTEMPTS - fails)
    }

    fun clear() {
        prefs.edit().clear().commit()
        _kind.value = null
    }

    private companion object {
        const val KIND = "kind"
        const val SALT = "salt"
        const val HASH = "hash"
        const val ITER = "iter"
        const val FAILS = "fails"
        const val UNTIL = "locked_until"
    }
}
