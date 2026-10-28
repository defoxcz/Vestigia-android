package com.vestigiascriptorium.android.aids

import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal class CatalogueChanged : java.io.IOException()

internal object AidNetwork {
    private const val ORIGIN="https://vestigiascriptorium.com"
    fun parse(text: String): Map<String,AidMetadata> {
        val json=JSONObject(text)
        require(json.getInt("version")==1)
        val array=json.getJSONArray("aids")
        require(array.length() in 1..100)
        val result=linkedMapOf<String,AidMetadata>()
        for(index in 0 until array.length()) {
            val item=array.getJSONObject(index)
            val meta=AidMetadata(item.getString("id"),item.getString("file"),item.getLong("archiveSize"),item.getLong("size"),item.getString("sha256"),if(item.has("schemaVersion")) item.getInt("schemaVersion") else null)
            require(result.put(meta.id,meta)==null)
        }
        return result
    }
    fun catalog(): Map<String,AidMetadata> = open(URL("$ORIGIN/api/research-aids/catalog?scope=all")).use { input ->
        val output=java.io.ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        while(true) {
            val count=input.read(buffer)
            if(count<0) break
            if(output.size()+count>128*1024) throw AidFailure("unavailable")
            output.write(buffer,0,count)
        }
        parse(output.toString("UTF-8"))
    }
    fun archive(meta: AidMetadata): InputStream = open(URL("$ORIGIN/api/research-aids/${meta.id}/download?sha256=${meta.sha256}"))

    private fun open(start: URL): InputStream {
        var url=start
        repeat(5) {
            if(url.protocol!="https" || url.userInfo!=null) throw AidFailure("unavailable")
            val connection=url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects=false
            connection.connectTimeout=15_000;connection.readTimeout=15_000
            connection.useCaches=false
            connection.setRequestProperty("Accept-Encoding","identity")
            try {
                val code=connection.responseCode
                if(code==409) throw CatalogueChanged()
                if(code in listOf(301,302,303,307,308)) {
                    val location=connection.getHeaderField("Location") ?: throw AidFailure("unavailable")
                    url=URL(url,location);connection.disconnect()
                } else {
                    if(code!=200) throw AidFailure(if(code==404) "unavailable" else "network")
                    return object : FilterInputStream(connection.inputStream) {
                        override fun close() { try { super.close() } finally { connection.disconnect() } }
                    }
                }
            } catch(error: Exception) { connection.disconnect();throw error }
        }
        throw AidFailure("network")
    }
}
