package com.opencontacts.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencontacts.core.ui.localization.localizedText

private val themePresets = listOf("CLASSIC", "GLASS", "AMOLED", "SOFT", "MINIMAL", "ELEGANT", "PASTEL")
private val accentPalettes = listOf("BLUE", "EMERALD", "SUNSET", "LAVENDER", "ROSE", "AMBER", "SLATE")
private val backgroundCategories = listOf("MINIMAL", "NATURE", "ABSTRACT", "DARK")
private val appFontProfiles = listOf("SYSTEM_DEFAULT", "ROUNDED_SYSTEM", "CONDENSED", "SERIF", "MONO", "CURSIVE", "ARABIC_NASKH", "CUSTOM_UPLOAD")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceRoute(onBack: () -> Unit, appViewModel: AppViewModel) {
    val settings by appViewModel.appLockSettings.collectAsStateWithLifecycle()
    var presetSheet by remember { mutableStateOf(false) }
    var paletteSheet by remember { mutableStateOf(false) }
    var backgroundSheet by remember { mutableStateOf(false) }
    var fontSheet by remember { mutableStateOf(false) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(appViewModel::importCustomFont)
    }
    SettingsScaffold(title = "Appearance", onBack = onBack) { modifier ->
        LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SettingsSection(title = "Theme mode", subtitle = "Apply light, dark, or system theming instantly across the app.") {
                    SettingsChoiceRow(title = "Mode", subtitle = "Changes apply live without restarting.", selected = settings.themeMode, choices = listOf("SYSTEM", "LIGHT", "DARK"), onSelect = appViewModel::setThemeMode)
                }
            }
            item {
                SettingsSection(title = "Theme studio", subtitle = "Google Keep inspired palette and background customization with live preview.") {
                    ThemeActionCard("Theme preset", settings.themePreset, Icons.Default.Palette) { presetSheet = true }
                    SettingsSpacer()
                    ThemeActionCard("Accent palette", settings.accentPalette, Icons.Default.Check) { paletteSheet = true }
                    SettingsSpacer()
                    ThemeActionCard("Background category", settings.backgroundCategory, Icons.Default.Check) { backgroundSheet = true }
                    SettingsSpacer()
                    SettingsChoiceRow(title = "Corner style", subtitle = "Choose how rounded cards and surfaces should feel.", selected = settings.cornerStyle, choices = listOf("ROUNDED", "COMPACT", "SHARP"), onSelect = appViewModel::setCornerStyle)
                }
            }
            item {
                SettingsSection(title = "Language", subtitle = "Arabic and English switch with real locale changes.") {
                    SettingsChoiceRow(title = "App language", subtitle = "Uses translated strings and correct layout direction.", selected = settings.appLanguage, choices = listOf("SYSTEM", "EN", "AR"), onSelect = appViewModel::setAppLanguage)
                }
            }
            item {
                SettingsSection(title = "Fonts", subtitle = "Choose a calmer app-wide font, or bring your own TTF / OTF file from the phone.") {
                    ThemeActionCard(
                        title = "App font",
                        value = if (settings.appFontProfile == "CUSTOM_UPLOAD") settings.customFontDisplayName ?: settings.appFontProfile else settings.appFontProfile,
                        icon = Icons.Default.FontDownload,
                    ) { fontSheet = true }
                    SettingsSpacer()
                    SettingsValueRow(
                        title = "Custom font file",
                        subtitle = "Imported fonts are copied into app storage and applied across the interface.",
                        value = settings.customFontDisplayName ?: localizedText("None"),
                    )
                    SettingsSpacer()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilledTonalButton(onClick = { fontPicker.launch(arrayOf("font/*", "application/font-sfnt", "application/x-font-ttf", "application/x-font-opentype")) }) {
                            Text(localizedText("Import custom font"))
                        }
                        androidx.compose.material3.TextButton(onClick = appViewModel::clearCustomFont, enabled = !settings.customFontPath.isNullOrBlank()) {
                            Text(localizedText("Clear custom font"))
                        }
                    }
                }
            }
            item {
                SettingsSection(title = "Live preview", subtitle = "Check cards, backgrounds, accents, and text feel before leaving the screen.") {
                    ThemePreviewCard(title = settings.themePreset, subtitle = "${localizedText(settings.accentPalette)} • ${localizedText(settings.backgroundCategory)}")
                }
            }
        }
    }
    if (presetSheet) SelectionSheet(title = "Choose a preset", options = themePresets, selected = settings.themePreset, onSelect = { appViewModel.setThemePreset(it); presetSheet = false }, onDismiss = { presetSheet = false })
    if (paletteSheet) SelectionSheet(title = "Choose an accent", options = accentPalettes, selected = settings.accentPalette, onSelect = { appViewModel.setAccentPalette(it); paletteSheet = false }, onDismiss = { paletteSheet = false }, isPalette = true)
    if (backgroundSheet) SelectionSheet(title = "Choose a background", options = backgroundCategories, selected = settings.backgroundCategory, onSelect = { appViewModel.setBackgroundCategory(it); backgroundSheet = false }, onDismiss = { backgroundSheet = false })
    if (fontSheet) SelectionSheet(title = "App font", options = appFontProfiles, selected = settings.appFontProfile, onSelect = { appViewModel.setAppFontProfile(it); fontSheet = false }, onDismiss = { fontSheet = false })
}

@Composable
private fun ThemeActionCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.large) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(localizedText(title), style = MaterialTheme.typography.titleMedium)
                Text(localizedText(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionSheet(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit, isPalette: Boolean = false) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(localizedText(title), style = MaterialTheme.typography.titleLarge)
            if (isPalette) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    options.forEach { palette ->
                        val color = when (palette) {
                            "EMERALD" -> Color(0xFF10B981)
                            "SUNSET" -> Color(0xFFF97316)
                            "LAVENDER" -> Color(0xFF8B5CF6)
                            "ROSE" -> Color(0xFFE11D48)
                            "AMBER" -> Color(0xFFF59E0B)
                            "SLATE" -> Color(0xFF475569)
                            else -> Color(0xFF2563EB)
                        }
                        Box(modifier = Modifier.size(44.dp).background(color, CircleShape).border(if (selected.equals(palette, true)) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected.equals(palette, true)) 1f else 0.18f), CircleShape).clickable { onSelect(palette) }, contentAlignment = Alignment.Center) {
                            if (selected.equals(palette, true)) Icon(Icons.Default.Check, null, tint = Color.White)
                        }
                    }
                }
            } else {
                options.forEach { option ->
                    FilterChip(selected = selected.equals(option, true), onClick = { onSelect(option) }, label = { Text(localizedText(option)) }, leadingIcon = { if (selected.equals(option, true)) Icon(Icons.Default.Check, null) })
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(title: String, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(localizedText(title), style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(modifier = Modifier.fillMaxWidth().height(112.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)), MaterialTheme.shapes.large))
        }
    }
}
