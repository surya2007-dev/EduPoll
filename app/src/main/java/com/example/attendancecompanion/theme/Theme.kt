package com.example.attendancecompanion.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = AccentRed,
    background = DarkBg,
    surface = DarkSurface,
    onPrimary = DarkBg,
    onBackground = DarkText,
    onSurface = DarkText,
    outline = DarkBorder,
    outlineVariant = DarkBorderLight,
    secondary = DarkSurface2
)

private val LightColorScheme = lightColorScheme(
    primary = AccentRed,
    background = LightBg,
    surface = LightSurface,
    onPrimary = LightBg,
    onBackground = LightText,
    onSurface = LightText,
    outline = LightBorder,
    outlineVariant = LightBorderLight,
    secondary = LightSurface2
)

data class CustomColors(
    val border: Color,
    val borderLight: Color,
    val textDim: Color,
    val textMuted: Color,
    val accentDim: Color,
    val green: Color,
    val greenDim: Color,
    val dotColor: Color,
    val surface2: Color
)

val LocalCustomColors = staticCompositionLocalOf {
    CustomColors(
        border = Color.Unspecified,
        borderLight = Color.Unspecified,
        textDim = Color.Unspecified,
        textMuted = Color.Unspecified,
        accentDim = Color.Unspecified,
        green = Color.Unspecified,
        greenDim = Color.Unspecified,
        dotColor = Color.Unspecified,
        surface2 = Color.Unspecified
    )
}

object AttendanceTheme {
    val customColors: CustomColors
        @Composable
        get() = LocalCustomColors.current
}

@Composable
fun AttendanceCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val customColors = if (darkTheme) {
        CustomColors(
            border = DarkBorder,
            borderLight = DarkBorderLight,
            textDim = DarkTextDim,
            textMuted = DarkTextMuted,
            accentDim = AccentRedDim,
            green = AccentGreen,
            greenDim = AccentGreenDim,
            dotColor = DarkDot,
            surface2 = DarkSurface2
        )
    } else {
        CustomColors(
            border = LightBorder,
            borderLight = LightBorderLight,
            textDim = LightTextDim,
            textMuted = LightTextMuted,
            accentDim = AccentRedDim,
            green = AccentGreen,
            greenDim = AccentGreenDim,
            dotColor = LightDot,
            surface2 = LightSurface2
        )
    }

    CompositionLocalProvider(LocalCustomColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Background modifier drawing a radial/square dot grid pattern (28dp spacing, ~50% opacity/clean look)
fun Modifier.drawDotGrid(dotColor: Color, spacing: Dp = 28.dp): Modifier = this.drawBehind {
    val spacingPx = spacing.toPx()
    val dotRadius = 1.dp.toPx()
    val width = size.width
    val height = size.height
    
    var x = spacingPx / 2
    while (x < width) {
        var y = spacingPx / 2
        while (y < height) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(x, y)
            )
            y += spacingPx
        }
        x += spacingPx
    }
}
