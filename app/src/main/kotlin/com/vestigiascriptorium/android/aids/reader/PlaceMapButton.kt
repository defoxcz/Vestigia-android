package com.vestigiascriptorium.android.aids.reader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.vestigiascriptorium.android.ui.main.Copy

@Composable
internal fun PlaceMapButton(target: PlaceMapTarget, copy: Copy) {
    val context = LocalContext.current
    var unavailable by remember(target) { mutableStateOf(false) }
    Button(onClick = {
        fun viewIntent(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            if(uri.startsWith("https:")) addCategory(Intent.CATEGORY_BROWSABLE)
        }
        unavailable = !openPlaceMap(target,
            available = { uri -> context.packageManager.queryIntentActivities(viewIntent(uri), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty() },
            launch = { uri, map ->
                try {
                    context.startActivity(Intent.createChooser(viewIntent(uri), if(map) copy.t("Zvolit mapovou aplikaci", "Choose map app") else copy.t("Zvolit prohlížeč", "Choose browser")))
                    true
                } catch (_: ActivityNotFoundException) { false }
            })
    }) { Text(copy.t("Zobrazit na mapě", "Show on map")) }
    if (unavailable) AlertDialog(onDismissRequest = { unavailable = false },
        title = { Text(copy.t("Mapu nelze otevřít", "Unable to open map")) },
        text = { Text(copy.t("V zařízení není dostupná mapová aplikace ani internetový prohlížeč.", "No map app or web browser is available on this device.")) },
        confirmButton = { TextButton(onClick = { unavailable = false }) { Text("OK") } })
}
