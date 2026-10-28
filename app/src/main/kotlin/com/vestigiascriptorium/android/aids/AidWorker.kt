package com.vestigiascriptorium.android.aids

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import java.io.File

internal class AidWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id=inputData.getString("aid") ?: return@withContext Result.failure()
        if(id !in MobileAids.ids) return@withContext Result.failure(workDataOf("error" to "unavailable"))
        if (!com.vestigiascriptorium.android.account.AccountManager.get(applicationContext).hasAccess()) return@withContext Result.retry()
        val repo=AidRepository.get(applicationContext)
        val jobContext=currentCoroutineContext()
        val account = com.vestigiascriptorium.android.account.AccountManager.get(applicationContext)
        val check={ jobContext.ensureActive();if(isStopped) throw CancellationException();if(!account.accessNow()) throw AccountRequired() }
        try {
            setForeground(notification(id))
            var meta=runCatching { repo.refreshNow()[id] }.getOrNull() ?: repo.metadata(id) ?: throw AidFailure("unavailable")
            var lastProgress=0L
            var changed=false
            while(true) {
                try {
                    val selected=meta
                    repo.store.install(selected,source={ AidNetwork.archive(selected) },validate=::validateDatabase,check=check,progress={ bytes,phase ->
                        check()
                        val now=System.currentTimeMillis()
                        if(phase!="active" || now-lastProgress>300) {
                            setProgressAsync(workDataOf("phase" to phase,"bytes" to bytes,"total" to selected.archiveSize))
                            lastProgress=now
                        }
                    })
                    break
                } catch(_: CatalogueChanged) {
                    if(changed) throw AidFailure("changed")
                    changed=true
                    meta=repo.refreshNow()[id] ?: throw AidFailure("unavailable")
                }
            }
            Result.success()
        } catch(error: CancellationException) { throw error }
        catch (_: AccountRequired) { Result.retry() }
        catch(error: Exception) {
            val code=(error as? AidFailure)?.code ?: "network"
            Result.failure(workDataOf("error" to code))
        }
    }
    private class AccountRequired: Exception()
    private fun validateDatabase(file: File,meta: AidMetadata) {
        try {
            SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                db.rawQuery("PRAGMA quick_check",null).use { cursor ->
                    if(!cursor.moveToFirst() || cursor.getString(0)!="ok" || cursor.moveToNext()) throw AidFailure("integrity")
                }
                if(meta.schemaVersion!=null && db.version!=meta.schemaVersion) throw AidFailure("unsupported")
                db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table'",null).use { cursor ->
                    if(!cursor.moveToFirst() || cursor.getInt(0)==0) throw AidFailure("integrity")
                }
            }
        } catch(error: AidFailure) { throw error }
        catch(_: Exception) { throw AidFailure("integrity") }
    }
    private fun notification(id: String): ForegroundInfo {
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        val cs=applicationContext.resources.configuration.locales[0].language=="cs"
        manager.createNotificationChannel(NotificationChannel("research-aids",if(cs) "Stahování pomůcek" else "Research aid downloads",NotificationManager.IMPORTANCE_LOW))
        val open=android.app.PendingIntent.getActivity(applicationContext,0,applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName),android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(applicationContext,"research-aids")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Vestigia Scriptorium")
            .setContentText(if(cs) "Stahování a ověřování badatelské pomůcky" else "Downloading and verifying a research aid")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
        val notificationId=1000+(id.hashCode() and 0x7fffffff)%10000
        return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(notificationId,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(notificationId,notification)
    }
}
