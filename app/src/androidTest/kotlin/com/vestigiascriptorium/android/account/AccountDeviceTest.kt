package com.vestigiascriptorium.android.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountDeviceTest {
    @Test fun keystoreEncryptsCredentialsAndRejectsTampering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "account-test-${UUID.randomUUID()}"
        val store = AccountStore(context, name)
        val file = File(context.noBackupFilesDir, "$name.enc")
        try {
            assertNull(store.read())
            val secret = "test-only-secret-${UUID.randomUUID()}"
            store.write(JSONObject().put("secret", secret).put("highWater", 1234L))
            assertEquals(secret, AccountStore(context, name).read()?.getString("secret"))
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains(secret))
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            assertTrue(runCatching { store.read() }.isFailure)
            store.clear(); assertNull(store.read())
        } finally {
            store.clear()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("vestigia-$name") }
        }
    }
    @Test fun productionServiceRequiresBrowserApproval() = runBlocking {
        // An unapproved random challenge never creates a user/session document.
        val secret = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        assertEquals(202, AccountNetwork.request(secret).status)
        assertEquals(401, AccountNetwork.request("invalid").status)
    }
}
