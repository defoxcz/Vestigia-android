package com.vestigiascriptorium.android.aids.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.vestigiascriptorium.android.aids.AidPackageStore
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.main.aids
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import java.io.File

class AidReaderActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent.getStringExtra("aid")
        if(id==null || aids.none { it.id==id }) { finish();return }
        enableEdgeToEdge()
        setContent { VestigiaScriptoriumTheme { com.vestigiascriptorium.android.account.AccountGate { ReaderRoute(id,onBack={finish()}) } } }
    }
    companion object {
        fun intent(context: Context,id: String)=Intent(context,AidReaderActivity::class.java).putExtra("aid",id)
    }
}

@Composable
internal fun ReaderRoute(id: String,onBack: () -> Unit) {
    val context=LocalContext.current
    val copy=Copy(LocalConfiguration.current.locales[0].language=="cs")
    val source=remember(id) { val store=AidPackageStore(File(context.noBackupFilesDir,"research-aids"));AidReaderSource { store.database(id) } }
    AidReaderScreen(aids.first { it.id==id },copy,source,onBack)
}
