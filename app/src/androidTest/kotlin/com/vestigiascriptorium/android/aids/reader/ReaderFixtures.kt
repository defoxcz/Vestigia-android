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

internal fun withReaderFixtures(test: (File,Map<String,String>) -> Unit) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val assets=instrumentation.context.assets
        assumeTrue("Generate public test fixtures with scripts/prepare-reader-fixtures.py",!assets.list("reference-fixtures").isNullOrEmpty())
        val root=File(instrumentation.targetContext.cacheDir,"reader-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val names=instrumentation.targetContext.assets.open("research-aids.json").bufferedReader().use { AidNetwork.parse(it.readText()) }.mapValues { it.value.file }
            names.values.forEach { name -> assets.open("reference-fixtures/$name").use { input -> File(root,name).outputStream().use { input.copyTo(it) } } }
            test(root,names)
        } finally { root.deleteRecursively() }
    }
