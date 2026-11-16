package com.example.data.crypto

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class RiderTelemetryPayload(
    val lat: Double,
    val lng: Double,
    val speedKmh: Double = 0.0,
    val headingDeg: Float = 0f,
    val altitudeM: Double = 0.0,
    val isOfflineQueued: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

object PackCrypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    fun derivePackKey(inviteCode: String, encryptionSalt: String): SecretKey {
        val material = "${inviteCode.trim().uppercase()}:$encryptionSalt:MotoPackE2E"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    fun encryptString(plainText: String, key: SecretKey): String {
        val iv = ByteArray(IV_LENGTH_BYTES)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptString(cipherBase64: String, key: SecretKey): String? {
        return try {
            val combined = Base64.decode(cipherBase64, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH_BYTES) return null
            val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
            val encrypted = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun encryptTelemetry(payload: RiderTelemetryPayload, key: SecretKey): String {
        return encryptString(json.encodeToString(payload), key)
    }

    fun decryptTelemetry(cipherBase64: String, key: SecretKey): RiderTelemetryPayload? {
        val plain = decryptString(cipherBase64, key) ?: return null
        return try {
            json.decodeFromString<RiderTelemetryPayload>(plain)
        } catch (_: Exception) {
            null
        }
    }
}
