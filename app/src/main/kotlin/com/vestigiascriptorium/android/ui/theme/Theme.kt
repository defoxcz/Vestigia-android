/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vestigiascriptorium.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFADD4EA),
    background = androidx.compose.ui.graphics.Color(0xFF111C24),
    surface = androidx.compose.ui.graphics.Color(0xFF111C24),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE5E7E9),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB7C6CF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF233C4C),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD2E7F3),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF304449),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD7E6E8),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF18252E),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF15222B),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF22323D),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF2B3C47),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF40515D),
    secondary = secondaryDark,
    tertiary = tertiaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF24475C),
    background = androidx.compose.ui.graphics.Color(0xFFF9F7F2),
    surface = androidx.compose.ui.graphics.Color(0xFFF9F7F2),
    onSurface = androidx.compose.ui.graphics.Color(0xFF202F38),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF566774),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE2EBEF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF203D50),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFEEE6D5),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF24475C),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF0EEE8),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF5F2ED),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE9E9E4),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFE8E6DF),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFD5D9D8),
    secondary = secondaryLight,
    tertiary = tertiaryLight
)

@Composable
fun VestigiaScriptoriumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
