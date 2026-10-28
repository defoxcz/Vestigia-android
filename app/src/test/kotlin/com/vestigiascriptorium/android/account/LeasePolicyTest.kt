package com.vestigiascriptorium.android.account

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class LeasePolicyTest {
    private val now = 1_800_000_000_000L
    private val hash = "ab".repeat(32)
    private val valid = AccountClaims(1, "vestigia-desktop", "user", hash, "premium", "test", true, now, now + DAY, now + DAY)
    @Test fun testerRequiresExplicitSignedPermission() {
        assertTrue(valid.permitsTesting(hash, now))
        assertFalse(valid.copy(testAccess = false).permitsTesting(hash, now))
        assertFalse(valid.copy(kind = "premium").permitsTesting(hash, now))
        assertFalse(valid.copy(kind = "free", edition = "free").permitsTesting(hash, now))
    }
    @Test fun validatesAudienceDeviceAndIdentity() {
        assertFalse(valid.copy(audience = "other").valid(hash, now))
        assertFalse(valid.valid("cd".repeat(32), now))
        assertFalse(valid.copy(uid = "").valid(hash, now))
        assertFalse(valid.copy(version = 2).valid(hash, now))
    }
    @Test fun enforcesOfflineAndClockLimits() {
        assertFalse(valid.permitsTesting(hash, now + DAY))
        assertFalse(valid.copy(expiresAt = now + DAY + 1, validUntil = now + DAY + 1).permitsTesting(hash, now))
        assertFalse(valid.copy(issuedAt = now + 300_001).valid(hash, now))
        assertFalse(valid.copy(validUntil = now).valid(hash, now))
        assertFalse(valid.copy(issuedAt = -1).valid(hash, now))
    }
    @Test fun validatesRsaSignatureAndRejectsTampering() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val body = "signed claims".toByteArray()
        val signed = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(body); sign() }
        assertTrue(LeaseSignature.verify(pair.public, body, signed))
        assertFalse(LeaseSignature.verify(pair.public, "modified".toByteArray(), signed))
        assertFalse(LeaseSignature.verify(pair.public, body, signed.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }))
        assertFalse(LeaseSignature.verify(pair.public, ByteArray(8193), signed))
    }
    @Test fun credentialHashUsesDecodedRandomBytes() {
        assertEquals("66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925", deviceHash("00".repeat(32)))
    }
}
