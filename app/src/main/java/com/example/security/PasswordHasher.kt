package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Established password hashing using PBKDF2WithHmacSHA256 (120,000 iterations, 16-byte salt)
 * and constant-time comparison, plus CSPRNG single-use invitation code generation.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()
    private val secureRandom = SecureRandom()

    data class HashedPassword(
        val hashBase64: String,
        val saltBase64: String
    )

    fun hashPassword(password: CharArray): HashedPassword {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val derived = deriveKey(password, salt)
        return HashedPassword(
            hashBase64 = Base64.encodeToString(derived, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun verifyPassword(password: CharArray, expectedHashBase64: String, saltBase64: String): Boolean {
        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val expected = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
            val actual = deriveKey(password, salt)
            MessageDigest.isEqual(actual, expected)
        } catch (_: Exception) {
            false
        }
    }

    fun generateSingleUseInviteCode(): String {
        val part1 = buildString(4) {
            repeat(4) { append(INVITE_ALPHABET[secureRandom.nextInt(INVITE_ALPHABET.size)]) }
        }
        val part2 = buildString(4) {
            repeat(4) { append(INVITE_ALPHABET[secureRandom.nextInt(INVITE_ALPHABET.size)]) }
        }
        return "CX-$part1-$part2"
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
