package com.kovak.moyue.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = Color(0xFF7A5C3E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDCC6),
    onPrimaryContainer = Color(0xFF3B2C1B),
    secondary = Color(0xFF8A8070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E1D4),
    onSecondaryContainer = Color(0xFF332C22),
    tertiary = Color(0xFF6E7F6A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE7D6),
    onTertiaryContainer = Color(0xFF26301F),
    background = Color(0xFFFAF6EF),
    onBackground = Color(0xFF33291D),
    surface = Color(0xFFF7F1E6),
    onSurface = Color(0xFF33291D),
    surfaceVariant = Color(0xFFEDE4D4),
    onSurfaceVariant = Color(0xFF5A4F40),
    outline = Color(0xFFB9AB94),
    outlineVariant = Color(0xFFDCD2BF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD3B895),
    onPrimary = Color(0xFF3E2F1B),
    primaryContainer = Color(0xFF57452D),
    onPrimaryContainer = Color(0xFFEBDCC6),
    secondary = Color(0xFFC0B49E),
    onSecondary = Color(0xFF332C22),
    secondaryContainer = Color(0xFF453D30),
    onSecondaryContainer = Color(0xFFE8E1D4),
    tertiary = Color(0xFFB3C6AB),
    onTertiary = Color(0xFF26301F),
    tertiaryContainer = Color(0xFF47563F),
    onTertiaryContainer = Color(0xFFDCE7D6),
    background = Color(0xFF171410),
    onBackground = Color(0xFFE8DFD0),
    surface = Color(0xFF1E1A15),
    onSurface = Color(0xFFE8DFD0),
    surfaceVariant = Color(0xFF2A251E),
    onSurfaceVariant = Color(0xFFC6B9A5),
    outline = Color(0xFF574C3C),
    outlineVariant = Color(0xFF332C22),
)

@Composable
fun InkReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkScheme else LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
