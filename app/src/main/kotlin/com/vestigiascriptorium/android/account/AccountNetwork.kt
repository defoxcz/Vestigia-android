package com.vestigiascriptorium.android.account

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal data class AccountReply(val status: Int, val body: JSONObject)
internal object AccountNetwork {
    // Existing deployed pairing protocol, shared with desktop. No Firebase password enters the app.
    fun request(secret: String, method: String = "POST"): AccountReply {
        val connection = URL("https://vestigiascriptorium.com/api/desktop/license").openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $secret")
            connection.setRequestProperty("X-Vestigia-Channel", "test")
            connection.setRequestProperty("Cache-Control", "no-store")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() <= 32_768) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray() } ?: byteArrayOf()
            require(bytes.size <= 32_768)
            return AccountReply(status, if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8)))
        } finally { connection.disconnect() }
    }
}
