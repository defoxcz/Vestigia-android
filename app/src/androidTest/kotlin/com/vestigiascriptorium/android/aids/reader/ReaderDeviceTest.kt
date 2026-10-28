package com.vestigiascriptorium.android.aids.reader

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vestigiascriptorium.android.aids.AidNetwork
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Reads only generated public fixtures in a unique cache directory. Never alters the installed library. */
@RunWith(AndroidJUnit4::class)
class ReaderDeviceTest {
    private fun fixtures(test: (File,Map<String,String>) -> Unit) = withReaderFixtures(test)
    @Test fun allThirteenDatabasesSharePageSearchFilterAndDetailContracts() = fixtures { root,names -> runBlocking {
        for((id,name) in names) {
            val source=AidReaderSource { File(root,name) }
            for(language in listOf("cs","en")) {
                val page=source.page(id,language,"","",0)
                assertTrue("$id/$language",page.total>0)
                assertTrue(page.records.size in 1..AidReaderSource.PAGE_SIZE)
                assertTrue(page.records.all { it.title.isNotBlank() })
                val detail=source.detail(id,language,page.records.first().id)
                assertNotNull(detail);assertTrue(detail!!.fields.isNotEmpty())
                if(id=="places") {
                    assertNotNull(detail.mapTarget)
                    assertEquals(detail.title,detail.mapTarget!!.name)
                    assertTrue(detail.mapTarget!!.webUri().startsWith("https://www.openstreetmap.org/"))
                } else assertNull(detail.mapTarget)
                val second=source.page(id,language,"","",1)
                if(page.total>AidReaderSource.PAGE_SIZE) assertTrue(page.records.map { it.id }.intersect(second.records.map { it.id }.toSet()).isEmpty())
                if(page.options.isNotEmpty()) {
                    val filtered=source.page(id,language,"",page.options.first().value,0)
                    assertTrue(filtered.total in 1..page.total)
                }
                val nameMatches=source.page(id,language,page.records.first().title,"",0)
                assertTrue("$id title-only",nameMatches.records.all { AidSchemas.fold(it.title).contains(AidSchemas.fold(page.records.first().title)) })
                source.page(id,language,"test","",0,searchScope=SearchScope.ALL_FIELDS)
                source.page(id,language,"%' OR 1=1 --","",0) // A bound literal, never SQL syntax.
                if(page.kind==AidViewKind.IMAGE) {
                    val onlyImages=source.page(id,language,"","",0,true)
                    assertTrue(onlyImages.records.all { it.images.isNotEmpty() })

                }
            }
        }
    }}
    @Test fun cancelledQueriesDoNotBlockTheNextReaderRequest() = fixtures { root,names -> runBlocking {
        val source=AidReaderSource { File(root,names.getValue("places")) }
        val search=launch { repeat(50) { source.page("places","cs","a","",0) } }
        delay(50);search.cancelAndJoin()
        withTimeout(5000) { assertTrue(source.page("places","cs","","",0).total>0) }
    }}
}
