package com.vestigiascriptorium.android.account

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal data class AccountState(
    val ready: Boolean = false, val allowed: Boolean = false, val busy: Boolean = false,
    val email: String = "", val code: String = "", val message: String = "",
    val expiresAt: Long = 0, val edition: String = "free", val signedIn: Boolean = false,
)
internal class AccountManager private constructor(context: Context) {
    private val app = context.applicationContext
    private val store = AccountStore(app)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(AccountState())
    val state = mutable.asStateFlow()
    private var loaded = false
    private var saved = JSONObject()
    private var claims: AccountClaims? = null
    private var baseTime = System.currentTimeMillis()
    private var baseElapsed = SystemClock.elapsedRealtime()
    private var lastRefresh = -1L
    private val publicKey by lazy {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(app.assets.open("account-public.der").use { it.readBytes() }))
    }
    private fun now() = maxOf(System.currentTimeMillis(), baseTime + SystemClock.elapsedRealtime() - baseElapsed)
    private fun secret() = saved.optString("secret").takeIf { it.matches(Regex("[0-9a-f]{64}")) }
    private fun persist() {
        saved.put("highWater", now())
        store.write(saved)
    }
    private fun verify(envelope: JSONObject, secret: String): AccountClaims {
        val payload = Base64.getDecoder().decode(envelope.getString("payload"))
        val signature = Base64.getDecoder().decode(envelope.getString("signature"))
        require(LeaseSignature.verify(publicKey, payload, signature))
        val json = JSONObject(String(payload, Charsets.UTF_8))
        require(json.get("version") == 1)
        fun time(name: String): Long {
            val value = json.get(name)
            require(value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0 && value.toDouble() in 0.0..9_007_199_254_740_991.0)
            return value.toLong()
        }
        val c = AccountClaims(json.getInt("version"), json.getString("audience"), json.getString("uid"),
            json.getString("device"), json.getString("edition"), json.getString("kind"),
            json.opt("testAccess") == true, time("issuedAt"), time("expiresAt"), time("validUntil"))
        require(!json.has("validity") || (json.getString("validity") == "until-revoked" && c.kind == "test" && c.testAccess))
        require(c.valid(deviceHash(secret), now()))
        return c
    }
    private fun load() {
        if (loaded) return
        loaded = true
        try {
            saved = store.read() ?: JSONObject()
            baseTime = maxOf(System.currentTimeMillis(), saved.optLong("highWater"))
            baseElapsed = SystemClock.elapsedRealtime()
            val s = secret()
            if (s != null) saved.optJSONObject("lease")?.let { claims = runCatching { verify(it, s) }.getOrNull() }
            publish()
        } catch (_: Exception) {
            saved = JSONObject(); claims = null
            mutable.value = AccountState(ready = true, message = "Uložené přihlášení nelze přečíst. Přihlaste se znovu.")
        }
    }
    private fun publish(message: String = mutable.value.message, busy: Boolean = false) {
        val s = secret()
        val pending = saved.optLong("pendingAt") > 0
        mutable.value = AccountState(ready = true, allowed = s != null && claims?.permitsTesting(deviceHash(s), now()) == true,
            busy = busy, email = saved.optString("email"),
            code = if (pending && s != null) deviceHash(s).take(8).uppercase() else "",
            message = message, expiresAt = claims?.expiresAt ?: 0, edition = claims?.edition ?: "free",
            signedIn = s != null && !pending)
    }
    suspend fun tick(refresh: Boolean = false) = withContext(Dispatchers.IO) { mutex.withLock {
        load(); publish()
        if (saved.optLong("pendingAt") > 0 && now() - saved.optLong("pendingAt") > 10 * 60_000) {
            saved = JSONObject(); claims = null; store.clear()
            publish("Čas pro připojení vypršel. Spusťte přihlášení znovu."); return@withLock
        }
        val s = secret() ?: return@withLock
        val interval = if (saved.optLong("pendingAt") > 0) 3_000 else 15 * 60_000L
        if (!refresh && lastRefresh >= 0 && SystemClock.elapsedRealtime() - lastRefresh < interval) return@withLock
        lastRefresh = SystemClock.elapsedRealtime()
        publish(busy = true)
        try {
            val reply = AccountNetwork.request(s)
            when (reply.status) {
                200 -> {
                    val envelope = reply.body.getJSONObject("lease")
                    val checked = verify(envelope, s)
                    saved.put("lease", envelope).put("email", reply.body.optString("email")).remove("pendingAt")
                    claims = checked; persist()
                    publish(if (checked.permitsTesting(deviceHash(s), now())) "" else "Účet nemá povolené testování, nebo je testovací program vypnutý.")
                }
                202 -> {
                    if (saved.optLong("pendingAt") == 0L) {
                        saved = JSONObject(); claims = null; store.clear()
                        publish("Relace již není platná. Přihlaste se znovu.")
                    } else publish("V prohlížeči potvrďte připojení a vraťte se do aplikace.")
                }
                401, 403 -> {
                    saved = JSONObject(); claims = null; store.clear()
                    publish("Přihlášení bylo odmítnuto nebo odvoláno. Ověřte účet a přihlaste se znovu.")
                }
                else -> publish("Ověření nyní není dostupné. Zkuste to znovu; offline přístup platí pouze do konce posledního ověření.")
            }
        } catch (_: Exception) {
            publish("Ověření se nezdařilo. Zkontrolujte připojení a datum v telefonu. Platnost offline přístupu se neprodlužuje.")
        }
    } }
    suspend fun begin(): String = withContext(Dispatchers.IO) { mutex.withLock {
        load()
        // Reopening the browser reuses a pending challenge, never an established credential.
        if (saved.optLong("pendingAt") == 0L || secret() == null) {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            saved = JSONObject().put("secret", bytes.joinToString("") { "%02x".format(it) }).put("pendingAt", now())
            claims = null; persist()
        }
        lastRefresh = -1
        publish("Porovnejte kód v prohlížeči a potvrďte připojení.")
        "https://vestigiascriptorium.com/cs/account/connect#challenge=${deviceHash(requireNotNull(secret()))}"
    } }
    suspend fun signOut() = withContext(Dispatchers.IO) {
        val old = mutex.withLock {
            load(); val s = secret()
            store.clear(); saved = JSONObject(); claims = null
            publish(""); s
        }
        if (old != null) runCatching { AccountNetwork.request(old, "DELETE") }
    }
    fun accessNow(): Boolean = mutable.value.allowed && now() < mutable.value.expiresAt
    suspend fun hasAccess(): Boolean = withContext(Dispatchers.IO) { mutex.withLock { load(); publish(); mutable.value.allowed } }
    companion object {
        @Volatile private var instance: AccountManager? = null
        fun get(context: Context): AccountManager = instance ?: synchronized(this) {
            instance ?: AccountManager(context).also { instance = it }
        }
    }
}
