package com.omniai.studio.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniai.studio.settings.AppFontFamily
import com.omniai.studio.settings.UiCustomizationConfig
import com.omniai.studio.settings.UiThemePreset

val AmoledBlack = Color(0xFF09090C)
val AmoledSurface = Color(0xFF13131A)
val AmoledSurfaceVariant = Color(0xFF1B1B25)
val AmoledBorder = Color(0xFF232330)
val EmeraldAccent = Color(0xFF00E676)
val EmeraldContainer = Color(0xFF063626)
val CyanSecondary = Color(0xFF00E5FF)
val AmberWarning = Color(0xFFF59E0B)
val CrimsonDanger = Color(0xFFFF5252)
val TextPrimary = Color(0xFFF3F6FA)
val TextSecondary = Color(0xFF94A3B8)

val LocalUiConfig = staticCompositionLocalOf { UiCustomizationConfig() }

enum class ThemeAccent(
    val label: String,
    val hexLabel: String,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color
) {
    CYBER_PURPLE(
        label = "Cyber Purple",
        hexLabel = "#BB86FC",
        primary = Color(0xFFBB86FC),
        onPrimary = Color(0xFF1D063A),
        primaryContainer = Color(0xFF2E174D),
        onPrimaryContainer = Color(0xFFEBD9FF)
    ),
    MATRIX_GREEN(
        label = "Matrix Green",
        hexLabel = "#00E676",
        primary = Color(0xFF00E676),
        onPrimary = Color(0xFF002111),
        primaryContainer = Color(0xFF063622),
        onPrimaryContainer = Color(0xFFB7FFD8)
    ),
    ELECTRIC_CYAN(
        label = "Electric Cyan",
        hexLabel = "#00E5FF",
        primary = Color(0xFF00E5FF),
        onPrimary = Color(0xFF002026),
        primaryContainer = Color(0xFF08323B),
        onPrimaryContainer = Color(0xFFBDF7FF)
    ),
    CRIMSON_RED(
        label = "Crimson Red",
        hexLabel = "#FF5252",
        primary = Color(0xFFFF5252),
        onPrimary = Color(0xFF2E0404),
        primaryContainer = Color(0xFF471216),
        onPrimaryContainer = Color(0xFFFFD6D6)
    );

    companion object {
        fun fromStoredName(raw: String?): ThemeAccent =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MATRIX_GREEN
    }
}

fun parseHexColorOrFallback(hex: String, fallback: Color = EmeraldAccent): Color {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return fallback
    return runCatching {
        Color((0xFF000000L or clean.toLong(16)).toInt())
    }.getOrDefault(fallback)
}

private fun applyCustomAccent(base: ColorScheme, customHex: String, isLight: Boolean): ColorScheme {
    val accent = parseHexColorOrFallback(customHex, base.primary)
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF090D12) else Color.White
    val container = if (isLight) {
        accent.copy(alpha = 0.16f)
    } else {
        Color(
            red = (accent.red * 0.24f).coerceIn(0f, 1f),
            green = (accent.green * 0.24f).coerceIn(0f, 1f),
            blue = (accent.blue * 0.24f).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
    val onContainer = if (isLight) Color(0xFF10141C) else Color(0xFFF3F6FA)
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer
    )
}

private fun buildPresetColorScheme(
    preset: UiThemePreset,
    fallbackAccent: ThemeAccent,
    dynamicScheme: ColorScheme?
): ColorScheme {
    return when (preset) {
        UiThemePreset.AMOLED_BLACK -> darkColorScheme(
            primary = fallbackAccent.primary,
            onPrimary = fallbackAccent.onPrimary,
            primaryContainer = fallbackAccent.primaryContainer,
            onPrimaryContainer = fallbackAccent.onPrimaryContainer,
            secondary = CyanSecondary,
            onSecondary = Color(0xFF002030),
            secondaryContainer = Color(0xFF0C2B3E),
            onSecondaryContainer = Color(0xFFBAE6FD),
            tertiary = AmberWarning,
            background = AmoledBlack,
            onBackground = TextPrimary,
            surface = AmoledSurface,
            onSurface = TextPrimary,
            surfaceVariant = AmoledSurfaceVariant,
            onSurfaceVariant = TextSecondary,
            outline = AmoledBorder,
            error = CrimsonDanger,
            onError = Color.White
        )

        UiThemePreset.CYBERPUNK_NEON -> darkColorScheme(
            primary = Color(0xFF00F0FF),
            onPrimary = Color(0xFF02161E),
            primaryContainer = Color(0xFF092A38),
            onPrimaryContainer = Color(0xFFC5FAFF),
            secondary = Color(0xFFFF2A85),
            onSecondary = Color(0xFF2B0014),
            secondaryContainer = Color(0xFF420A27),
            onSecondaryContainer = Color(0xFFFFD6E8),
            tertiary = Color(0xFFFDE047),
            background = Color(0xFF05040D),
            onBackground = Color(0xFFF4F4FF),
            surface = Color(0xFF0D0B1D),
            onSurface = Color(0xFFF4F4FF),
            surfaceVariant = Color(0xFF181433),
            onSurfaceVariant = Color(0xFFA7A2C9),
            outline = Color(0xFF33295E),
            error = Color(0xFFFF4D6D),
            onError = Color.White
        )

        UiThemePreset.MATERIAL_YOU -> dynamicScheme ?: darkColorScheme(
            primary = Color(0xFF8AB4F8),
            onPrimary = Color(0xFF0B2545),
            primaryContainer = Color(0xFF173B66),
            onPrimaryContainer = Color(0xFFDCEBFF),
            secondary = Color(0xFF81C995),
            onSecondary = Color(0xFF072A16),
            secondaryContainer = Color(0xFF153D26),
            onSecondaryContainer = Color(0xFFD2F7DE),
            tertiary = Color(0xFFFDD663),
            background = Color(0xFF0C0F14),
            onBackground = Color(0xFFE8EAED),
            surface = Color(0xFF141922),
            onSurface = Color(0xFFE8EAED),
            surfaceVariant = Color(0xFF1F2735),
            onSurfaceVariant = Color(0xFF9AA4B4),
            outline = Color(0xFF303B4F),
            error = CrimsonDanger,
            onError = Color.White
        )

        UiThemePreset.MATRIX_TERMINAL -> darkColorScheme(
            primary = Color(0xFF00FF66),
            onPrimary = Color(0xFF00220D),
            primaryContainer = Color(0xFF052E15),
            onPrimaryContainer = Color(0xFFC0FFD9),
            secondary = Color(0xFF34D399),
            onSecondary = Color(0xFF032318),
            secondaryContainer = Color(0xFF083525),
            onSecondaryContainer = Color(0xFFD1FAE5),
            tertiary = Color(0xFFA3E635),
            background = Color(0xFF010602),
            onBackground = Color(0xFFD4FFE4),
            surface = Color(0xFF051109),
            onSurface = Color(0xFFD4FFE4),
            surfaceVariant = Color(0xFF0B1F13),
            onSurfaceVariant = Color(0xFF74B88E),
            outline = Color(0xFF164228),
            error = Color(0xFFFF5C5C),
            onError = Color.White
        )

        UiThemePreset.NORD_SLATE -> darkColorScheme(
            primary = Color(0xFF88C0D0),
            onPrimary = Color(0xFF13222A),
            primaryContainer = Color(0xFF213744),
            onPrimaryContainer = Color(0xFFD8F0F7),
            secondary = Color(0xFF81A1C1),
            onSecondary = Color(0xFF152230),
            secondaryContainer = Color(0xFF28364A),
            onSecondaryContainer = Color(0xFFDDE8F5),
            tertiary = Color(0xFFEBCB8B),
            background = Color(0xFF1D222B),
            onBackground = Color(0xFFECEFF4),
            surface = Color(0xFF242A35),
            onSurface = Color(0xFFECEFF4),
            surfaceVariant = Color(0xFF2E3440),
            onSurfaceVariant = Color(0xFFA8B3C5),
            outline = Color(0xFF434C5E),
            error = Color(0xFFBF616A),
            onError = Color.White
        )

        UiThemePreset.MINIMAL_LIGHT -> lightColorScheme(
            primary = Color(0xFF0F766E),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFCCFBF1),
            onPrimaryContainer = Color(0xFF0A3D39),
            secondary = Color(0xFF2563EB),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDBEAFE),
            onSecondaryContainer = Color(0xFF1E3A8A),
            tertiary = Color(0xFFD97706),
            background = Color(0xFFF6F8FB),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFEAEFF6),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            error = Color(0xFFDC2626),
            onError = Color.White
        )
    }
}

private fun buildTypography(fontPref: AppFontFamily, colorScheme: ColorScheme): Typography {
    val primaryFamily = when (fontPref) {
        AppFontFamily.DEFAULT_SANS -> FontFamily.SansSerif
        AppFontFamily.MONOSPACE -> FontFamily.Monospace
        AppFontFamily.SERIF -> FontFamily.Serif
    }
    return Typography(
        headlineSmall = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            color = colorScheme.onSurface
        ),
        titleMedium = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = colorScheme.onSurface
        ),
        bodyMedium = TextStyle(
            fontFamily = primaryFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = colorScheme.onSurface
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun OmniAiAmoledTheme(
    accent: ThemeAccent = ThemeAccent.MATRIX_GREEN,
    uiConfig: UiCustomizationConfig = UiCustomizationConfig(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicScheme = if (
        uiConfig.themePreset == UiThemePreset.MATERIAL_YOU &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        dynamicDarkColorScheme(context)
    } else {
        null
    }

    val baseScheme = buildPresetColorScheme(
        preset = uiConfig.themePreset,
        fallbackAccent = accent,
        dynamicScheme = dynamicScheme
    )
    val finalScheme = if (uiConfig.useCustomAccent) {
        applyCustomAccent(baseScheme, uiConfig.customAccentHex, uiConfig.themePreset.isLight)
    } else {
        baseScheme
    }

    val radius = uiConfig.cardCornerRadiusDp.coerceIn(0, 24)
    val shapes = Shapes(
        extraSmall = RoundedCornerShape((radius / 3).coerceAtLeast(0).dp),
        small = RoundedCornerShape((radius / 2).coerceAtLeast(0).dp),
        medium = RoundedCornerShape(radius.dp),
        large = RoundedCornerShape((radius + 4).coerceAtMost(28).dp),
        extraLarge = RoundedCornerShape((radius + 8).coerceAtMost(32).dp)
    )

    CompositionLocalProvider(LocalUiConfig provides uiConfig) {
        MaterialTheme(
            colorScheme = finalScheme,
            typography = buildTypography(uiConfig.appFontFamily, finalScheme),
            shapes = shapes,
            content = content
        )
    }
}
