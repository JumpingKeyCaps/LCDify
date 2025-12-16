package com.lebaillyapp.lcdify.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val GameBoyColorScheme = lightColorScheme(
    // Surfaces
    background = GB_ShellPrimary,
    surface = GB_ShellSecondary,
    surfaceVariant = GB_ShellDivider,

    // Accents
    primary = GB_ButtonAB,
    secondary = GB_DPad,

    // Texte
    onBackground = GB_TextDark,
    onSurface = GB_TextDark,
    onPrimary = GB_TextLight,
    onSecondary = GB_TextLight,

    // États
    outline = GB_DPad,
)

@Composable
fun LCDifyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GameBoyColorScheme,
        typography = Typography,
        content = content
    )
}