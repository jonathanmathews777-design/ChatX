package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val ChatXDarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = ObsidianBlack,
    primaryContainer = SurfaceHighest,
    onPrimaryContainer = PureWhite,
    secondary = StatusActiveWhite,
    onSecondary = ObsidianBlack,
    secondaryContainer = SurfaceCard,
    onSecondaryContainer = SoftWhite,
    tertiary = TextSecondary,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = PureWhite,
    surface = VoidBlack,
    onSurface = PureWhite,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = VoidBlack,
    surfaceContainer = SurfaceElevated,
    surfaceContainerHigh = SurfaceCard,
    surfaceContainerHighest = SurfaceHighest,
    outline = BorderHairline,
    outlineVariant = SurfaceHighest,
    error = AlertRed,
    onError = ObsidianBlack,
    errorContainer = AlertRedContainer,
    onErrorContainer = AlertRed
)

private val ChatXLightColorScheme = lightColorScheme(
    primary = LightTextPrimary,
    onPrimary = PureWhite,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = LightTextPrimary,
    secondary = LightTextSecondary,
    onSecondary = PureWhite,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = LightBorder,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = AlertRed,
    onError = PureWhite
)

val ChatXShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ChatXTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ChatXDarkColorScheme else ChatXLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChatXTypography,
        shapes = ChatXShapes,
        content = content
    )
}
