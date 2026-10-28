package com.vestigiascriptorium.android.aids.reader

import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.request.CachePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

/** Real Commons fetch and decode, using the same loader and headers as the UI. */
class AidImageNetworkTest {
    @Test fun allNineImageAidFamiliesDecodeTheirCommonsThumbnails() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val cases=listOf(
            "heraldry" to "Vidov CZ CoA.svg",
            "heraldry-nobility" to "Abensberg-traun wappen.jpg",
            "heraldry-ecclesiastical" to "Coat of Arms of diocese of Toulon.png",
            "heraldry-chivalric-orders" to "CBE AEAColl.jpg",
            "heraldry-burgher-patrician" to "Blason famille be Sweerts (Lignage de Bruxelles).svg",
            "heraldry-personal-coats-of-arms" to "George Washington Arms.svg",
            "numismatics" to "Lyon 5e - Musée Lugdunum - Trésor de Vaise, 81 pièces de monnaie, détail.jpg",
            "sigillography" to "Ring of Cheops.jpg",
            "epigraphy-noble-tombs" to "+ Kaiser- und Königsgräber in der Kathedrale von Palermo. 02.jpg")
        for((id,name) in cases) {
            val url="https://commons.wikimedia.org/wiki/Special:Redirect/file/"+URLEncoder.encode(name,"UTF-8").replace("+","%20")+"?width=1000"
            val result=AidImages.loader(context).execute(AidImages.request(context,url,size=512))
            assertTrue("$id: "+(result as? ErrorResult)?.throwable?.stackTraceToString(),result is SuccessResult)
            assertTrue((result as SuccessResult).image.width>0)
            android.util.Log.i("VestigiaImageTest","$id decoded ${result.image.width}x${result.image.height}")
            val offline=AidImages.loader(context).execute(AidImages.request(context,url,size=512).newBuilder()
                .memoryCachePolicy(CachePolicy.DISABLED).networkCachePolicy(CachePolicy.DISABLED).build())
            assertTrue("$id disk cache",offline is SuccessResult)
        }
    }
}
