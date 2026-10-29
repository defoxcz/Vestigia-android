package com.vestigiascriptorium.android.genealogy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vestigiascriptorium.android.account.AccountGate
import com.vestigiascriptorium.android.ui.main.Copy
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme
import androidx.compose.material3.*

class GenealogyActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent {VestigiaScriptoriumTheme {AccountGate {
            val model: GenealogyModel=viewModel()
            val controller by model.controller.collectAsStateWithLifecycle()
            val failed by model.failed.collectAsStateWithLifecycle()
            val copy=Copy(LocalConfiguration.current.locales[0].language=="cs")
            var selected by rememberSaveable {mutableStateOf<String?>(null)}
            val people=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
                if(result.resultCode==RESULT_OK)selected=result.data?.getStringExtra("person")
            }
            if(failed) AlertDialog(onDismissRequest={finish()},title={Text(copy.t("Rodokmeny nelze otevřít","Unable to open family trees"))},text={Text(copy.t("Data nebyla změněna. Zkuste aplikaci znovu otevřít.","Data has not been changed. Try reopening the app."))},confirmButton={TextButton(onClick={finish()}) {Text(copy.t("Zavřít","Close"))}})
            else controller?.let {GenealogyWorkspace(it,copy,{finish()},onPeople={id ->people.launch(PeopleActivity.intent(this,id))},selectedPerson=selected,onSelectionConsumed={selected=null})} ?: CircularProgressIndicator()
        }}}
    }
    companion object {fun intent(context: Context)=Intent(context,GenealogyActivity::class.java)}
}
