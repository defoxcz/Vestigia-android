package com.vestigiascriptorium.android.account

import java.security.PublicKey
import java.security.Signature
import java.security.MessageDigest

internal const val DAY = 86_400_000L
internal data class AccountClaims(
    val version: Int, val audience: String, val uid: String, val device: String,
    val edition: String, val kind: String, val testAccess: Boolean,
    val issuedAt: Long, val expiresAt: Long, val validUntil: Long,
) {
    fun valid(deviceHash: String, now: Long): Boolean =
        version == 1 && audience == "vestigia-desktop" && uid.isNotBlank() &&
        deviceHash.matches(Regex("[0-9a-f]{64}")) && device == deviceHash &&
        issuedAt >= 0 && issuedAt <= now + 300_000 && expiresAt > now &&
        expiresAt > issuedAt && expiresAt <= validUntil &&
        expiresAt - issuedAt <= (if (kind == "premium") 30 * DAY else DAY) &&
        ((edition == "premium" && kind in listOf("test", "premium")) || (edition == "free" && kind == "free"))

    // Account identity, edition and tester permission remain separate concepts.
    fun permitsTesting(deviceHash: String, now: Long) = valid(deviceHash, now) &&
        testAccess && kind == "test" && edition == "premium" && expiresAt - issuedAt <= DAY
}
internal object LeaseSignature {
    fun verify(key: PublicKey, payload: ByteArray, signature: ByteArray): Boolean = runCatching {
        payload.isNotEmpty() && payload.size <= 8192 && Signature.getInstance("SHA256withRSA").run {
            initVerify(key); update(payload); verify(signature)
        }
    }.getOrDefault(false)
}
internal fun deviceHash(secret: String): String = MessageDigest.getInstance("SHA-256")
    .digest(secret.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    .joinToString("") { "%02x".format(it) }
