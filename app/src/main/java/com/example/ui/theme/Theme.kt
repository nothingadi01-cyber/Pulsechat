package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldMint,
    onPrimary = Color(0xFF032118),
    primaryContainer = DeepEmerald,
    onPrimaryContainer = SoftMint,
    secondary = CyberCyan,
    onSecondary = Color(0xFF041E2B),
    secondaryContainer = DeepCyanContainer,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = WarmAmber,
    onTertiary = Color(0xFF281A00),
    background = ObsidianBg,
    onBackground = Color(0xFFE6F2EC),
    surface = ObsidianSurface,
    onSurface = Color(0xFFE6F2EC),
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = Color(0xFF9DB5AA),
    outline = ObsidianOutline,
    error = AlertCoral,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ForestEmerald,
    onPrimary = Color.White,
    primaryContainer = MintWash,
    onPrimaryContainer = DeepForestText,
    secondary = TealAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = CallGold,
    onTertiary = Color.White,
    background = AlabasterBg,
    onBackground = Color(0xFF0F241C),
    surface = PureSurface,
    onSurface = Color(0xFF0F241C),
    surfaceVariant = SoftSlateSurface,
    onSurfaceVariant = Color(0xFF476357),
    outline = LightOutline,
    error = AlertCoral,
    onError = Color.White
)

val PulseTalkShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PulseTalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
        typography = PulseTalkTypography,
        shapes = PulseTalkShapes,
        content = content
    )
}
