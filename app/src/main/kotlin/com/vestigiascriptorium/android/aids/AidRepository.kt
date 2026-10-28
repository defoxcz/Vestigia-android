package com.vestigiascriptorium.android.aids

import android.content.Context
import android.app.Application
import android.annotation.SuppressLint
import android.util.AtomicFile
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal data class AidJob(val status: String, val bytes: Long=0, val total: Long=0, val error: String?=null)
internal data class AidLibraryState(
    val metadata: Map<String,AidMetadata> = emptyMap(),
    val installed: Map<String,AidMetadata> = emptyMap(),
    val jobs: Map<String,AidJob> = emptyMap(),
    val wifiOnly: Boolean=true,
    val installedOnly: Boolean=false,
    val refreshing: Boolean=false,
    val catalogError: Boolean=false,
    val actionError: String?=null,
    val busy: Set<String> = emptySet(),
)

// Durable control flags reach disk on the IO dispatcher before scheduling/cancelling work.
@SuppressLint("ApplySharedPref", "UseKtx")
internal class AidRepository private constructor(private val app: Application) {
    private val work=WorkManager.getInstance(app)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val prefs=app.getSharedPreferences("research-aids",Context.MODE_PRIVATE)
    val store=AidPackageStore(File(app.noBackupFilesDir,"research-aids"))
    private val catalogFile=AtomicFile(File(app.noBackupFilesDir,"research-aids-catalog.json"))
    private val preferences=AidPreferences(app)
    private val stateFlow=MutableStateFlow(AidLibraryState(wifiOnly=prefs.getBoolean("wifiOnly",true),installedOnly=preferences.installedOnly))
    val state=stateFlow.asStateFlow()
    @Volatile private var metadata=MobileAids.withCache(
        app.assets.open("research-aids.json").bufferedReader().use { AidNetwork.parse(it.readText()) },
        runCatching { catalogFile.openRead().bufferedReader().use { AidNetwork.parse(it.readText()) } }.getOrDefault(emptyMap()))
    private val busy=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var infos: List<WorkInfo> = emptyList()
    init {
        android.util.Log.i("VestigiaAidStorage", "package=${app.packageName} version=${com.vestigiascriptorium.android.BuildConfig.VERSION_NAME} places=${store.storageStatus("places")}")
        scope.launch {
            val history=work.getWorkInfosByTag(TAG).get()
            android.util.Log.i("VestigiaAidStorage", "package=${app.packageName} placesControl=${prefs.getString("state:places",null)} work="+history.joinToString { "${it.tags.firstOrNull { tag -> tag.startsWith("aid:") }}:${it.state}:${it.outputData.getString("error")}" })
            work.getWorkInfosByTagFlow(TAG).collect { infos=it;publish() }
        }
        scope.launch { while(isActive) { publish();delay(750) } }
        scope.launch { work.cancelUniqueWork("aid:${MobileAids.DESKTOP_TEMPLATES}").result.get() }
        refresh()
    }
    @Synchronized private fun publish() {
        val installed=(metadata.keys+MobileAids.DESKTOP_TEMPLATES).mapNotNull { id -> store.installed(id)?.let { id to it } }.toMap()
        val jobs=linkedMapOf<String,AidJob>()
        metadata.forEach { (id,meta) ->
            val saved=prefs.getString("state:$id",null)
            val info=infos.filter { "aid:$id" in it.tags }.maxByOrNull { item -> item.tags.firstOrNull { it.startsWith("created:") }?.removePrefix("created:")?.toLongOrNull() ?: 0L }
            val current=when {
                id in busy -> AidJob("busy")
                saved=="paused" -> AidJob("paused")
                saved=="cancelled" -> if(installed.containsKey(id)) AidJob(if(installed[id]?.sha256!=meta.sha256) "update" else "installed") else null
                info?.state==WorkInfo.State.RUNNING -> AidJob(info.progress.getString("phase") ?: "active",info.progress.getLong("bytes",0),info.progress.getLong("total",meta.archiveSize))
                info?.state==WorkInfo.State.ENQUEUED || info?.state==WorkInfo.State.BLOCKED -> AidJob("queued",total=meta.archiveSize)
                info?.state==WorkInfo.State.FAILED && saved!="cancelled" -> AidJob("error",error=info.outputData.getString("error") ?: "network")
                installed.containsKey(id) -> AidJob(if(installed[id]?.sha256!=meta.sha256) "update" else "installed")
                else -> null
            }
            if(current!=null) jobs[id]=current
        }
        if(store.hasFiles(MobileAids.DESKTOP_TEMPLATES) || MobileAids.DESKTOP_TEMPLATES in busy) {
            jobs[MobileAids.DESKTOP_TEMPLATES]=AidJob(if(MobileAids.DESKTOP_TEMPLATES in busy) "busy" else "retired")
        }
        stateFlow.value=stateFlow.value.copy(metadata=metadata,installed=installed,jobs=jobs,wifiOnly=prefs.getBoolean("wifiOnly",true),installedOnly=preferences.installedOnly,busy=busy.toSet())
    }
    fun refresh() { scope.launch {
        stateFlow.value=stateFlow.value.copy(refreshing=true,catalogError=false)
        try { refreshNow() } catch(_: Exception) { stateFlow.value=stateFlow.value.copy(catalogError=true) }
        finally { stateFlow.value=stateFlow.value.copy(refreshing=false);publish() }
    }}
    fun refreshNow(): Map<String,AidMetadata> {
        val next=MobileAids.compatible(AidNetwork.catalog())
        // Older web deployments return just heraldic templates. Keep other cached metadata.
        synchronized(catalogFile) {
        metadata=metadata+next
        val json=JSONObject().put("version",1).put("aids",org.json.JSONArray().apply {
            metadata.values.forEach { m -> put(JSONObject().put("id",m.id).put("file",m.file).put("archiveSize",m.archiveSize).put("size",m.size).put("sha256",m.sha256).apply { m.schemaVersion?.let { put("schemaVersion",it) } }) }
        })
        val stream=catalogFile.startWrite()
        try { stream.write(json.toString().toByteArray());catalogFile.finishWrite(stream) }
        catch(e: Exception) { catalogFile.failWrite(stream);throw e }
        return metadata
        }
    }
    fun metadata(id: String) = metadata[id]
    fun download(id: String) = action(id) {
        require(metadata.containsKey(id))
        prefs.edit().remove("state:$id").commit()
        val request=OneTimeWorkRequestBuilder<AidWorker>()
            .setInputData(workDataOf("aid" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if(prefs.getBoolean("wifiOnly",true)) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
            .addTag(TAG).addTag("aid:$id").addTag("created:${System.currentTimeMillis()}").build()
        work.enqueueUniqueWork("aid:$id",ExistingWorkPolicy.REPLACE,request).result.get()
        // Only one retained WorkInfo per package keeps the UI deterministic after retries.
        infos=work.getWorkInfosByTag(TAG).get()
    }
    fun pause(id: String) = action(id) {
        prefs.edit().putString("state:$id","paused").commit()
        work.cancelUniqueWork("aid:$id").result.get()
        store.exclusive(id) { /* Wait until the cancelled writer has released its staging files. */ }
    }
    fun cancel(id: String) = action(id) {
        prefs.edit().putString("state:$id","cancelled").commit()
        work.cancelUniqueWork("aid:$id").result.get()
        store.exclusive(id) { }
    }
    fun uninstall(id: String) = action(id) {
        prefs.edit().putString("state:$id","cancelled").commit()
        work.cancelUniqueWork("aid:$id").result.get()
        store.uninstall(id)
    }
    fun wifiOnly(value: Boolean) {
        prefs.edit().putBoolean("wifiOnly",value).apply();publish()
        // Existing requests retain their constraints; restart pending requests under the new policy.
        infos.filter { !it.state.isFinished }.flatMap { it.tags }.filter { it.startsWith("aid:") }.distinct()
            .filter { it.removePrefix("aid:") in MobileAids.ids }.forEach { tag -> download(tag.removePrefix("aid:")) }
    }
    fun installedOnly(value: Boolean) { preferences.installedOnly=value;publish() }
    fun clearError() { stateFlow.value=stateFlow.value.copy(actionError=null) }
    private fun action(id: String, block: () -> Unit) {
        if(!busy.add(id)) return
        scope.launch {
            try { block() } catch(_: Exception) { stateFlow.value=stateFlow.value.copy(actionError="storage") }
            finally { busy.remove(id);publish() }
        }
    }
    companion object {
        const val TAG="research-aid-download"
        @Volatile private var instance: AidRepository?=null
        fun get(context: Context): AidRepository = instance ?: synchronized(this) { instance ?: AidRepository(context.applicationContext as Application).also { instance=it } }
    }
}
