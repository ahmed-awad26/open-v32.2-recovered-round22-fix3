package com.opencontacts.core.ui.theme

import android.graphics.Typeface as AndroidTypeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun rememberAppFontFamily(profile: String, customFontPath: String?): FontFamily? = remember(profile, customFontPath) {
    when (profile.uppercase()) {
        "SYSTEM_DEFAULT" -> null
        "SERIF" -> FontFamily.Serif
        "MONO" -> FontFamily.Monospace
        "CURSIVE" -> FontFamily.Cursive
        "ROUNDED_SYSTEM" -> FontFamily(ComposeTypeface(AndroidTypeface.create("sans-serif-medium", AndroidTypeface.NORMAL)))
        "CONDENSED" -> FontFamily(ComposeTypeface(AndroidTypeface.create("sans-serif-condensed", AndroidTypeface.NORMAL)))
        "ARABIC_NASKH" -> FontFamily(ComposeTypeface(AndroidTypeface.create("serif", AndroidTypeface.NORMAL)))
        "CUSTOM_UPLOAD" -> customFontPath
            ?.takeIf { it.isNotBlank() }
            ?.let {
                runCatching { FontFamily(ComposeTypeface(AndroidTypeface.createFromFile(it))) }.getOrNull()
            }
        else -> null
    }
}

private fun buildTypography(fontFamily: FontFamily?) = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, fontFamily = fontFamily),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = fontFamily),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = fontFamily),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFamily = fontFamily),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = fontFamily),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFamily = fontFamily),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontFamily = fontFamily),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFamily = fontFamily),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFamily = fontFamily),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, fontFamily = fontFamily),
)

@Composable
fun OpenContactsTheme(
    themeMode: String = "SYSTEM",
    themePreset: String = "CLASSIC",
    accentPalette: String = "BLUE",
    cornerStyle: String = "ROUNDED",
    backgroundCategory: String = "MINIMAL",
    appFontProfile: String = "SYSTEM_DEFAULT",
    customFontPath: String? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode.uppercase()) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) darkScheme(themePreset, accentPalette, backgroundCategory) else lightScheme(themePreset, accentPalette, backgroundCategory)
    val radius = when (cornerStyle.uppercase()) {
        "COMPACT" -> 14.dp
        "SHARP" -> 6.dp
        else -> 22.dp
    }
    val fontFamily = rememberAppFontFamily(appFontProfile, customFontPath)
    MaterialTheme(
        colorScheme = colors,
        typography = buildTypography(fontFamily),
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(radius * 0.4f),
            small = androidx.compose.foundation.shape.RoundedCornerShape(radius * 0.7f),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(radius),
            large = androidx.compose.foundation.shape.RoundedCornerShape(radius * 1.2f),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(radius * 1.4f),
        ),
        content = content,
    )
}

private fun lightScheme(preset: String, accent: String, backgroundCategory: String) = lightColorScheme(
    primary = primary(accent, false),
    onPrimary = Color.White,
    primaryContainer = primaryContainer(accent, false),
    onPrimaryContainer = onPrimaryContainer(accent, false),
    secondary = secondary(accent, false),
    onSecondary = Color.White,
    secondaryContainer = secondaryContainer(accent, false),
    onSecondaryContainer = onPrimaryContainer(accent, false),
    tertiary = tertiary(accent, false),
    background = when (backgroundCategory.uppercase()) {
        "NATURE" -> Color(0xFFF0F7F4)
        "ABSTRACT" -> Color(0xFFF7F4FF)
        "DARK" -> Color(0xFFF1F4F9)
        else -> when (preset.uppercase()) {
            "AMOLED" -> Color(0xFFF3F5FA)
            "GLASS" -> Color(0xFFF6F8FC)
            "SOFT" -> Color(0xFFFFF8FB)
            "MINIMAL" -> Color(0xFFF8FAFC)
            "ELEGANT" -> Color(0xFFF6F4FF)
            "PASTEL" -> Color(0xFFFFFBFF)
            else -> Color(0xFFF7F8FC)
        }
    },
    onBackground = Color(0xFF0F172A),
    surface = when (preset.uppercase()) {
        "GLASS" -> Color(0xFFFDFEFF)
        "AMOLED" -> Color(0xFFFAFBFF)
        "ELEGANT" -> Color(0xFFFCFAFF)
        "PASTEL" -> Color(0xFFFFFCFE)
        else -> Color(0xFFFCFDFF)
    },
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE7ECF5),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
)

private fun darkScheme(preset: String, accent: String, backgroundCategory: String) = darkColorScheme(
    primary = primary(accent, true),
    onPrimary = Color(0xFF08111C),
    primaryContainer = primaryContainer(accent, true),
    onPrimaryContainer = onPrimaryContainer(accent, true),
    secondary = secondary(accent, true),
    onSecondary = Color(0xFF05221F),
    secondaryContainer = secondaryContainer(accent, true),
    onSecondaryContainer = Color(0xFFE5FFFA),
    tertiary = tertiary(accent, true),
    background = when (backgroundCategory.uppercase()) {
        "NATURE" -> Color(0xFF081712)
        "ABSTRACT" -> Color(0xFF131022)
        "DARK" -> Color(0xFF03050B)
        else -> when (preset.uppercase()) {
            "AMOLED" -> Color(0xFF000000)
            "GLASS" -> Color(0xFF080D17)
            "SOFT" -> Color(0xFF11111A)
            "MINIMAL" -> Color(0xFF0F172A)
            "ELEGANT" -> Color(0xFF130F1F)
            "PASTEL" -> Color(0xFF161220)
            else -> Color(0xFF09101D)
        }
    },
    onBackground = Color(0xFFE5EAF4),
    surface = when (preset.uppercase()) {
        "AMOLED" -> Color(0xFF030303)
        "GLASS" -> Color(0xFF111827)
        "PASTEL" -> Color(0xFF1E1B2E)
        else -> Color(0xFF111827)
    },
    onSurface = Color(0xFFE5EAF4),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFB9C5D9),
    outline = Color(0xFF7E8BA1),
)

private fun primary(accent: String, dark: Boolean): Color = when (accent.uppercase()) {
    "EMERALD" -> if (dark) Color(0xFF6EE7B7) else Color(0xFF059669)
    "SUNSET" -> if (dark) Color(0xFFFDBA74) else Color(0xFFEA580C)
    "LAVENDER" -> if (dark) Color(0xFFD8B4FE) else Color(0xFF8B5CF6)
    "ROSE" -> if (dark) Color(0xFFF9A8D4) else Color(0xFFE11D48)
    "AMBER" -> if (dark) Color(0xFFFCD34D) else Color(0xFFD97706)
    "SLATE" -> if (dark) Color(0xFFCBD5E1) else Color(0xFF475569)
    else -> if (dark) Color(0xFF93C5FD) else Color(0xFF2563EB)
}

private fun primaryContainer(accent: String, dark: Boolean): Color = when (accent.uppercase()) {
    "EMERALD" -> if (dark) Color(0xFF065F46) else Color(0xFFD1FAE5)
    "SUNSET" -> if (dark) Color(0xFF7C2D12) else Color(0xFFFFEDD5)
    "LAVENDER" -> if (dark) Color(0xFF5B21B6) else Color(0xFFEDE9FE)
    "ROSE" -> if (dark) Color(0xFF881337) else Color(0xFFFFE4E6)
    "AMBER" -> if (dark) Color(0xFF78350F) else Color(0xFFFFF1C2)
    "SLATE" -> if (dark) Color(0xFF334155) else Color(0xFFE2E8F0)
    else -> if (dark) Color(0xFF2647C7) else Color(0xFFD9E2FF)
}

private fun onPrimaryContainer(accent: String, dark: Boolean): Color = if (dark) Color.White else Color(0xFF0A1F5A)
private fun secondary(accent: String, dark: Boolean): Color = when (accent.uppercase()) {
    "EMERALD" -> if (dark) Color(0xFF5EEAD4) else Color(0xFF0F766E)
    "SUNSET" -> if (dark) Color(0xFFF9A8D4) else Color(0xFFDB2777)
    "LAVENDER" -> if (dark) Color(0xFFA5B4FC) else Color(0xFF4F46E5)
    "ROSE" -> if (dark) Color(0xFFFDA4AF) else Color(0xFFBE123C)
    "AMBER" -> if (dark) Color(0xFFFDE68A) else Color(0xFFCA8A04)
    "SLATE" -> if (dark) Color(0xFFE2E8F0) else Color(0xFF334155)
    else -> if (dark) Color(0xFF5EEAD4) else Color(0xFF14B8A6)
}
private fun secondaryContainer(accent: String, dark: Boolean): Color = if (dark) Color(0xFF123A37) else Color(0xFFC7FFF7)
private fun tertiary(accent: String, dark: Boolean): Color = when (accent.uppercase()) {
    "EMERALD" -> if (dark) Color(0xFFBBF7D0) else Color(0xFF16A34A)
    "SUNSET" -> if (dark) Color(0xFFFCD34D) else Color(0xFFD97706)
    "LAVENDER" -> if (dark) Color(0xFFE9D5FF) else Color(0xFF7C3AED)
    "ROSE" -> if (dark) Color(0xFFFBCFE8) else Color(0xFFDB2777)
    "AMBER" -> if (dark) Color(0xFFFFF0B3) else Color(0xFFEA580C)
    "SLATE" -> if (dark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    else -> if (dark) Color(0xFFE9B6FF) else Color(0xFF8B5CF6)
}
