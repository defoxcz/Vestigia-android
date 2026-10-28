package com.vestigiascriptorium.android.aids.reader

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import android.os.SystemClock
import android.util.Log

class PlaceSearchPerformanceTest {
    @Test fun fullPublicDatabaseSearchesAcrossAllCountriesQuickly() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        assumeTrue(instrumentation.context.assets.list("performance")?.contains("places.zip")==true)
        val root=File(instrumentation.targetContext.cacheDir,"places-performance-${UUID.randomUUID()}").apply { mkdirs() }
        val power=instrumentation.targetContext.getSystemService(android.os.PowerManager::class.java)
        val wake=power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"Vestigia:SearchBenchmark")
        wake.acquire(180_000)
        try {
            val database=File(root,"places.db")
            ZipInputStream(instrumentation.context.assets.open("performance/places.zip")).use { zip ->
                var entry=zip.nextEntry
                while(entry!=null) {
                    if(entry.name=="places.db") database.outputStream().use { zip.copyTo(it) }
                    entry=zip.nextEntry
                }
            }
            assertTrue("Full public fixture was extracted",database.length()>100_000_000)
            val source=AidReaderSource(File(root,"index")) { database }
            val start=SystemClock.elapsedRealtime()
            val all=source.page("places","cs","","",0)
            assertTrue(all.total>200_000)
            Log.i("VestigiaSearchPerf","First preparation: ${SystemClock.elapsedRealtime()-start} ms; records=${all.total}")
            for(scope in SearchScope.entries) {
                for(query in listOf("B","Bř","Bře","Břec","Břeclav","breclav")) {
                    val before=SystemClock.elapsedRealtime()
                    val result=source.page("places","cs",query,"",0,searchScope=scope)
                    val elapsed=SystemClock.elapsedRealtime()-before
                    assertTrue(result.total>0)
                    if(scope==SearchScope.NAMES) assertTrue(result.records.all { AidSchemas.fold(it.title).contains(AidSchemas.fold(query)) })
                    Log.i("VestigiaSearchPerf","$scope '$query': $elapsed ms, matches=${result.total}")
                    assertTrue("$scope '$query' took $elapsed ms",elapsed < if(scope==SearchScope.NAMES) 250 else 500)
                }
            }
            // A new reader instance must reuse the prepared index, never normalize 212k rows again.
            val reopened=AidReaderSource(File(root,"index")) { database }
            val before=SystemClock.elapsedRealtime()
            assertTrue(reopened.page("places","cs","Břeclav","",0).total>0)
            assertTrue(SystemClock.elapsedRealtime()-before<250)
        } finally { root.deleteRecursively();if(wake.isHeld) wake.release() }
    }
}
