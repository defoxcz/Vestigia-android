package com.vestigiascriptorium.android.ui.main

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vestigiascriptorium.android.aids.AidLibraryModel
import com.vestigiascriptorium.android.aids.AidLibraryState
import com.vestigiascriptorium.android.aids.AidNetwork
import com.vestigiascriptorium.android.aids.reader.AidReaderActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vestigiascriptorium.android.ui.theme.VestigiaScriptoriumTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val model: AidLibraryModel = viewModel()
    val library by model.state.collectAsStateWithLifecycle()
    val repository=model.repository
    val context=LocalContext.current
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    VestigiaApp(library=library,onDownload={ id ->
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        repository.download(id)
    },onPause=repository::pause,
        onCancel=repository::cancel,onUninstall=repository::uninstall,onWifi=repository::wifiOnly,
        onRefresh=repository::refresh,onClearError=repository::clearError,
        onOpenAid={ id -> context.startActivity(AidReaderActivity.intent(context,id)) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VestigiaApp(initialTab: Int = 0, initialGenealogy: Boolean = false,
    library: AidLibraryState = AidLibraryState(),
    onDownload: (String) -> Unit = {},onPause: (String) -> Unit = {},onCancel: (String) -> Unit = {},
    onUninstall: (String) -> Unit = {},onWifi: (Boolean) -> Unit = {},onRefresh: () -> Unit = {},onClearError: () -> Unit = {},onOpenAid: (String) -> Unit = {}) {
    val copy = Copy(LocalConfiguration.current.locales[0].language == "cs")
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var genealogy by rememberSaveable { mutableStateOf(initialGenealogy) }
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    val jobs=library.jobs.mapValues { it.value.status }
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 700.dp }
    val labels = listOf(copy.t("Moduly","Modules"),copy.t("Pomůcky","Research aids"),copy.t("Stahování","Downloads"))
    val activeCount = jobs.values.count { it in listOf("queued","active","paused","error","verifying","installing") }
    fun openAid(id: String) { if(library.installed.containsKey(id)) onOpenAid(id) else detail=id }
    fun select(index: Int) { tab=index;detail=null;genealogy=false }
    BackHandler(enabled=detail!=null || genealogy || tab!=0) {
        when { detail!=null -> detail=null;genealogy -> genealogy=false;else -> select(0) }
    }
    Scaffold(
        topBar={
            TopAppBar(
                title={Column {
                    Text("VESTIGIA",style=MaterialTheme.typography.titleLarge,fontFamily=FontFamily.Serif)
                    Text("SCRIPTORIUM",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }},
                navigationIcon={if(detail!=null || genealogy) IconButton(onClick={detail=null;genealogy=false},modifier=Modifier.semantics { contentDescription=copy.t("Zpět","Back") }) { Mark(5) }},
                actions={ TextButton(onClick={info=copy.t("Soukromé bádání","Private research")}) { Text(copy.t("Moje bádání","My research")) } }
            )
        },
        bottomBar={ if(!wide) NavigationBar {
            labels.forEachIndexed { index,label ->
                NavigationBarItem(selected=tab==index,onClick={select(index)},icon={
                    BadgedBox(badge={if(index==2 && activeCount>0) Badge { Text(activeCount.toString()) }}) { Mark(index) }
                },label={Text(label)},alwaysShowLabel=true)
            }
        }}
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if(wide) NavigationRail {
                labels.forEachIndexed { index,label -> NavigationRailItem(selected=tab==index,onClick={select(index)},icon={Mark(index)},label={Text(label)}) }
            }
            Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=androidx.compose.ui.Alignment.TopCenter) {
                Column(Modifier.widthIn(max=960.dp).fillMaxSize()) {
                    when {
                        detail!=null -> AidDetail(aids.first { it.id==detail },copy,library,onDownload={ id ->
                            onDownload(id);select(2)
                        },onManage={select(2)},onOpen={onOpenAid(it)})
                        genealogy -> GenealogyScreen(copy,onCreate={info=copy.t("Nový rodokmen","New family tree")})
                        tab==0 -> ModulesScreen(copy,onGenealogy={genealogy=true},onModule={info=it})
                        tab==1 -> AidsScreen(copy,library,onRefresh=onRefresh,onDetail={openAid(it)})
                        else -> DownloadsScreen(copy,library,onDownload,onPause,onCancel,onUninstall,onWifi,onCatalog={select(1)},onDetail={openAid(it)})
                    }
                }
            }
        }
    }
    library.actionError?.let { error ->
        AlertDialog(onDismissRequest=onClearError,title={Text(copy.t("Operaci nelze dokončit","Unable to complete operation"))},
            text={Text(copy.error(error))},confirmButton={TextButton(onClick=onClearError) { Text("OK") }})
    }
    info?.let { title ->
        AlertDialog(onDismissRequest={info=null},title={Text(title,fontFamily=FontFamily.Serif)},text={Text(copy.t("Tato obrazovka ukazuje navrženou strukturu aplikace. Funkci doplníme v další fázi vývoje; nyní se nemění ani nevytvářejí badatelská data.","This screen shows the proposed app structure. The feature will be added in a later development phase; no research data is created or changed."))},confirmButton={TextButton(onClick={info=null}) { Text(copy.t("Rozumím","Got it")) }})
    }
}

@Preview(name="Modules",showBackground=true,device="spec:width=412dp,height=915dp,dpi=420",locale="cs")
@Composable
fun ModulesPreview() { VestigiaScriptoriumTheme { VestigiaApp(0,library=previewLibrary()) } }
@Preview(name="Research aids",showBackground=true,device="spec:width=412dp,height=915dp,dpi=420",locale="cs")
@Composable
fun AidsPreview() { VestigiaScriptoriumTheme { VestigiaApp(1,library=previewLibrary()) } }
@Preview(name="Downloads",showBackground=true,device="spec:width=412dp,height=915dp,dpi=420",locale="cs")
@Composable
fun DownloadsPreview() { VestigiaScriptoriumTheme { VestigiaApp(2,library=previewLibrary()) } }
@Preview(name="Dark modules",showBackground=true,device="spec:width=412dp,height=915dp,dpi=420",locale="cs")
@Composable
fun DarkModulesPreview() { VestigiaScriptoriumTheme(darkTheme=true) { VestigiaApp(0,library=previewLibrary()) } }

@Preview(name="Genealogy",showBackground=true,device="spec:width=412dp,height=915dp,dpi=420",locale="cs")
@Composable
fun GenealogyPreview() { VestigiaScriptoriumTheme { VestigiaApp(initialGenealogy=true,library=previewLibrary()) } }
@Preview(name="Tablet",showBackground=true,device="spec:width=840dp,height=1000dp,dpi=240",locale="cs")
@Composable
fun TabletPreview() { VestigiaScriptoriumTheme { VestigiaApp(1,library=previewLibrary()) } }

@Composable
private fun previewLibrary(): AidLibraryState {
    val context=LocalContext.current
    val metadata=remember { runCatching {
        context.assets.open("research-aids.json").bufferedReader().use { AidNetwork.parse(it.readText()) }
    }.getOrDefault(emptyMap()) }
    return AidLibraryState(metadata=metadata)
}
