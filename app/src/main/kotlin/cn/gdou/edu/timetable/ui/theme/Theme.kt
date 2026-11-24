package cn.gdou.edu.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cn.gdou.edu.timetable.data.local.AppSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val LocalThemeSeedColor = staticCompositionLocalOf { Color(AppSettings.DEFAULT_THEME_SEED_ARGB) }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val DefaultDarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark
)

private val DefaultLightColorScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight
)

@Composable
fun GdouTheme(
    seedColorArgb: Long = AppSettings.DEFAULT_THEME_SEED_ARGB,
    darkThemeMode: String = AppSettings.THEME_MODE_SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (darkThemeMode) {
        AppSettings.THEME_MODE_DARK -> true
        AppSettings.THEME_MODE_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val seedColor = Color(seedColorArgb)
    val colorScheme = if (seedColorArgb == AppSettings.DEFAULT_THEME_SEED_ARGB) {
        if (darkTheme) DefaultDarkColorScheme else DefaultLightColorScheme
    } else {
        if (darkTheme) buildCustomDarkColorScheme(seedColor) else buildCustomLightColorScheme(seedColor)
    }

    CompositionLocalProvider(
        LocalThemeSeedColor provides seedColor,
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

fun themeHeroGradientColors(seedColor: Color, darkTheme: Boolean): List<Color> {
    val (h, s, l) = rgbToHsl(seedColor)
    val sat = if (s < 0.08f) s else s.coerceIn(0.45f, 0.88f)
    return if (darkTheme) {
        listOf(
            hslToColor(h, sat, (l * 0.55f).coerceIn(0.18f, 0.28f)),
            hslToColor((h + 10f) % 360f, sat, (l * 0.72f).coerceIn(0.26f, 0.38f)),
            hslToColor((h + 26f) % 360f, (sat * 0.9f), (l * 0.85f).coerceIn(0.32f, 0.44f))
        )
    } else {
        listOf(
            hslToColor(h, sat, 0.25f),
            hslToColor((h + 10f) % 360f, sat, 0.35f),
            hslToColor((h + 28f) % 360f, (sat * 0.88f), 0.43f)
        )
    }
}

fun buildCustomLightColorScheme(seed: Color): ColorScheme {
    val (h, s, l) = rgbToHsl(seed)
    val primarySat = if (s < 0.10f) s else s.coerceIn(0.40f, 0.88f)
    val primaryLightness = l.coerceIn(0.28f, 0.45f)
    val primary = hslToColor(h, primarySat, primaryLightness)
    val onPrimary = if (primary.luminance() > 0.52f) Color(0xFF14181C) else Color.White

    val secHue = (h + 18f) % 360f
    val secSat = (primarySat * 0.48f).coerceAtMost(0.46f)
    val tertHue = (h + 46f) % 360f
    val tertSat = (primarySat * 0.68f).coerceAtMost(0.68f)
    val neuSat = (primarySat * 0.16f).coerceAtMost(0.14f)

    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = hslToColor(h, (primarySat * 0.86f).coerceAtMost(0.86f), 0.90f),
        onPrimaryContainer = hslToColor(h, (primarySat * 0.92f).coerceAtMost(0.92f), 0.15f),
        secondary = hslToColor(secHue, secSat, 0.38f),
        onSecondary = Color.White,
        secondaryContainer = hslToColor(secHue, (secSat * 1.15f).coerceAtMost(0.55f), 0.90f),
        onSecondaryContainer = hslToColor(secHue, (secSat * 1.15f).coerceAtMost(0.55f), 0.17f),
        tertiary = hslToColor(tertHue, tertSat, 0.36f),
        onTertiary = Color.White,
        tertiaryContainer = hslToColor(tertHue, (tertSat * 1.05f).coerceAtMost(0.75f), 0.90f),
        onTertiaryContainer = hslToColor(tertHue, tertSat, 0.15f),
        error = errorLight,
        onError = onErrorLight,
        errorContainer = errorContainerLight,
        onErrorContainer = onErrorContainerLight,
        background = hslToColor(h, neuSat, 0.97f),
        onBackground = Color(0xFF171C20),
        surface = hslToColor(h, neuSat, 0.97f),
        onSurface = Color(0xFF171C20),
        surfaceVariant = hslToColor(h, neuSat * 1.25f, 0.90f),
        onSurfaceVariant = hslToColor(h, neuSat * 1.2f, 0.30f),
        outline = hslToColor(h, neuSat * 1.1f, 0.48f),
        outlineVariant = hslToColor(h, neuSat * 1.15f, 0.80f),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = hslToColor(h, neuSat, 0.985f),
        surfaceContainer = hslToColor(h, neuSat * 1.1f, 0.95f),
        surfaceContainerHigh = hslToColor(h, neuSat * 1.2f, 0.925f),
        surfaceContainerHighest = hslToColor(h, neuSat * 1.25f, 0.89f)
    )
}

fun buildCustomDarkColorScheme(seed: Color): ColorScheme {
    val (h, s, _) = rgbToHsl(seed)
    val primarySat = if (s < 0.10f) s else s.coerceIn(0.42f, 0.88f)
    val secHue = (h + 18f) % 360f
    val secSat = (primarySat * 0.48f).coerceAtMost(0.46f)
    val tertHue = (h + 46f) % 360f
    val tertSat = (primarySat * 0.68f).coerceAtMost(0.68f)
    val neuSat = (primarySat * 0.18f).coerceAtMost(0.16f)

    return darkColorScheme(
        primary = hslToColor(h, (primarySat * 0.88f).coerceAtMost(0.88f), 0.76f),
        onPrimary = hslToColor(h, primarySat, 0.15f),
        primaryContainer = hslToColor(h, (primarySat * 0.82f).coerceAtMost(0.82f), 0.27f),
        onPrimaryContainer = hslToColor(h, (primarySat * 0.90f).coerceAtMost(0.90f), 0.91f),
        secondary = hslToColor(secHue, secSat, 0.78f),
        onSecondary = hslToColor(secHue, secSat, 0.18f),
        secondaryContainer = hslToColor(secHue, secSat, 0.28f),
        onSecondaryContainer = hslToColor(secHue, secSat, 0.90f),
        tertiary = hslToColor(tertHue, tertSat, 0.75f),
        onTertiary = hslToColor(tertHue, tertSat, 0.16f),
        tertiaryContainer = hslToColor(tertHue, tertSat, 0.26f),
        onTertiaryContainer = hslToColor(tertHue, tertSat, 0.90f),
        error = errorDark,
        onError = onErrorDark,
        errorContainer = errorContainerDark,
        onErrorContainer = onErrorContainerDark,
        background = hslToColor(h, neuSat, 0.08f),
        onBackground = Color(0xFFE3E7EC),
        surface = hslToColor(h, neuSat, 0.08f),
        onSurface = Color(0xFFE3E7EC),
        surfaceVariant = hslToColor(h, neuSat * 1.2f, 0.26f),
        onSurfaceVariant = hslToColor(h, neuSat * 1.1f, 0.78f),
        outline = hslToColor(h, neuSat, 0.56f),
        outlineVariant = hslToColor(h, neuSat * 1.1f, 0.28f),
        surfaceContainerLowest = hslToColor(h, neuSat, 0.055f),
        surfaceContainerLow = hslToColor(h, neuSat, 0.11f),
        surfaceContainer = hslToColor(h, neuSat * 1.1f, 0.14f),
        surfaceContainerHigh = hslToColor(h, neuSat * 1.15f, 0.18f),
        surfaceContainerHighest = hslToColor(h, neuSat * 1.2f, 0.22f)
    )
}

fun rgbToHsl(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val l = (maxC + minC) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))
    val h = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Triple(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}

fun colorToHex(argb: Long): String =
    String.format("#%06X", (argb and 0xFFFFFFL).toInt())

fun parseHexToArgb(raw: String): Long? {
    val cleaned = raw.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}
