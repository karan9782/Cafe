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
    primary = CopperGlow,
    onPrimary = Color(0xFF331200),
    primaryContainer = CopperGlowContainer,
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = MatchaSoft,
    onSecondary = Color(0xFF003822),
    secondaryContainer = MatchaDarkContainer,
    onSecondaryContainer = Color(0xFF9DF2C6),
    tertiary = AmberGlow,
    onTertiary = Color(0xFF3A2400),
    tertiaryContainer = AmberDarkContainer,
    onTertiaryContainer = Color(0xFFFFDEA8),
    error = UrgentCoral,
    onError = Color(0xFF4F0004),
    errorContainer = UrgentDarkContainer,
    onErrorContainer = Color(0xFFFFDAD8),
    background = NightEspressoBg,
    onBackground = NightCreamText,
    surface = NightUmberSurface,
    onSurface = NightCreamText,
    surfaceVariant = NightUmberElevated,
    onSurfaceVariant = NightMutedText,
    outline = NightHairline,
    outlineVariant = Color(0xFF352822)
)

private val LightColorScheme = lightColorScheme(
    primary = CopperPrimary,
    onPrimary = Color.White,
    primaryContainer = CopperContainer,
    onPrimaryContainer = CopperDeep,
    secondary = MatchaSecondary,
    onSecondary = Color.White,
    secondaryContainer = MatchaContainer,
    onSecondaryContainer = MatchaDeep,
    tertiary = AmberTertiary,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberDeep,
    error = UrgentCrimson,
    onError = Color.White,
    errorContainer = UrgentCrimsonContainer,
    onErrorContainer = UrgentCrimsonDeep,
    background = CreamCanvas,
    onBackground = EspressoInk,
    surface = AlabasterCard,
    onSurface = EspressoInk,
    surfaceVariant = LinenSurface,
    onSurfaceVariant = EspressoMuted,
    outline = HairlineWarm,
    outlineVariant = LinenElevated
)

val CafeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CafeKitchenTheme(
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
        typography = CafeTypography,
        shapes = CafeShapes,
        content = content
    )
}
