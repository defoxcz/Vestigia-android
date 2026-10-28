package com.vestigiascriptorium.android.account

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AccountGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val manager = remember { AccountManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(manager, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            manager.tick(refresh = true)
            while (true) { delay(1_000); manager.tick() }
        }
    }
    if (state.allowed) content() else AccountPanel(manager, state)
}

@Composable
internal fun AccountButton() {
    val manager = AccountManager.get(LocalContext.current)
    val state by manager.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Účet") }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text("Účet Vestigia") }, text = { AccountPanel(manager, state, compact = true) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Zavřít") } })
}

@Composable
private fun AccountPanel(manager: AccountManager, state: AccountState, compact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localError by remember { mutableStateOf("") }
    Column((if (compact) Modifier.fillMaxWidth().heightIn(max = 480.dp) else Modifier.fillMaxSize().safeDrawingPadding()).verticalScroll(rememberScrollState()).padding(if (compact) 0.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (!compact) {
            Text("VESTIGIA", style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif)
            Text("Scriptorium · testovací verze", style = MaterialTheme.typography.titleMedium)
        }
        if (!state.ready) { CircularProgressIndicator(); return@Column }
        if (state.email.isNotBlank()) Text(state.email)
        Text(if (state.allowed) "Testování povoleno" else "Pro vstup potřebujete aktivní účet s ověřeným e-mailem a povoleným testováním.")
        if (!state.allowed) Text("Použijte stejný účet jako na webu a v desktopové aplikaci. Nový účet lze zaregistrovat na webu; testování musí následně povolit správce.")
        if (state.code.isNotBlank()) {
            Text("Kód připojení: ${state.code}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge)
            Text("V prohlížeči porovnejte kód a potvrďte připojení. Potom se vraťte do aplikace. Společná webová stránka zatím používá označení desktopu.")
        }
        if (state.expiresAt > 0 && state.allowed) Text("Další online ověření nejpozději: ${DateFormat.getDateTimeInstance().format(Date(state.expiresAt))}")
        if (state.message.isNotBlank()) Text(state.message)
        if (localError.isNotBlank()) Text(localError, color = MaterialTheme.colorScheme.error)
        if (state.busy) LinearProgressIndicator()
        if (!state.signedIn) Button(enabled = !state.busy, onClick = {
            scope.launch {
                runCatching {
                    val url = manager.begin()
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.onFailure { localError = "Přihlášení nelze otevřít. Zkontrolujte dostupný prohlížeč a zkuste to znovu." }
            }
        }) { Text(if (state.code.isBlank()) "Přihlásit se / Registrovat" else "Otevřít přihlášení v prohlížeči") }
        if (state.signedIn || state.code.isNotBlank()) {
            OutlinedButton(enabled = !state.busy, onClick = { scope.launch { manager.tick(refresh = true) } }) { Text("Ověřit přístup") }
            TextButton(onClick = { scope.launch { runCatching { manager.signOut() }.onFailure { localError = "Odhlášení nelze dokončit. Zkuste to znovu." } } }) { Text(if (state.signedIn) "Odhlásit se" else "Zrušit přihlášení") }
        }
    }
}
