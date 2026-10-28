package com.vestigiascriptorium.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.vestigiascriptorium.android.BuildConfig

@Composable
internal fun AppVersionLabel() {
    Text("${BuildConfig.VERSION_NAME} · ${if(BuildConfig.DEBUG) "DEBUG" else "RELEASE"}",
        style=MaterialTheme.typography.labelSmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines=1)
}
