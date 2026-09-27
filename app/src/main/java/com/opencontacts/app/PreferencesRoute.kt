package com.opencontacts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opencontacts.core.common.availableCallSimOptions
import com.opencontacts.core.common.isOpenContactsDefaultDialer
import com.opencontacts.core.common.requestOpenContactsDefaultDialer
import com.opencontacts.core.ui.localization.localizedText
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val retentionOptions = listOf(7, 30, 365, 3650)
private fun retentionLabel(days: Int): String = when (days) {
    7 -> "1 Week"
    30 -> "1 Month"
    365 -> "1 Year"
    else -> "Lifetime"
}

private fun dialPadButtonSizeLabel(sizeDp: Int): String = when {
    sizeDp <= 60 -> "Compact"
    sizeDp <= 68 -> "Large"
    else -> "Extra large"
}

private fun backspaceHoldLabel(durationMs: Int): String = when {
    durationMs <= 450 -> "Fast"
    durationMs <= 850 -> "Balanced"
    else -> "Slow"
}

private fun transferOverlayOpacityLabel(percent: Int): String = when {
    percent <= 55 -> "Very transparent"
    percent <= 72 -> "Soft"
    percent <= 88 -> "Balanced"
    else -> "Solid"
}

private fun simChooserOpacityLabel(percent: Int): String = when {
    percent <= 58 -> "Very light"
    percent <= 76 -> "Soft"
    percent <= 90 -> "Balanced"
    else -> "Solid"
}

private fun simChooserSizeLabel(percent: Int): String = when {
    percent <= 80 -> "Compact"
    percent <= 90 -> "Balanced"
    else -> "Large"
}

@Composable
fun PreferencesRoute(
    onBack: () -> Unit,
    appViewModel: AppViewModel,
) {
    val settings by appViewModel.appLockSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sliderValue by remember(settings.trashRetentionDays) {
        mutableFloatStateOf(retentionOptions.indexOfFirst { it >= settings.trashRetentionDays }.coerceAtLeast(0).toFloat())
    }
    val retentionDays = retentionOptions[sliderValue.toInt().coerceIn(retentionOptions.indices)]
    var dialPadButtonSizeValue by remember(settings.dialPadToggleButtonSizeDp) {
        mutableFloatStateOf(((settings.dialPadToggleButtonSizeDp - 56) / 4f).coerceIn(0f, 8f))
    }
    val dialPadButtonSizeDp = (56 + dialPadButtonSizeValue.toInt().coerceIn(0, 8) * 4)
    var backspaceHoldValue by remember(settings.dialPadBackspaceLongPressDurationMs) {
        mutableFloatStateOf(((settings.dialPadBackspaceLongPressDurationMs - 300) / 100f).coerceIn(0f, 12f))
    }
    val backspaceHoldDurationMs = (300 + backspaceHoldValue.toInt().coerceIn(0, 12) * 100)
    var transferOverlayOpacityValue by remember(settings.transferProgressOverlayOpacity) {
        mutableFloatStateOf((settings.transferProgressOverlayOpacity.coerceIn(40, 100) - 40).toFloat())
    }
    val transferOverlayOpacity = (40 + transferOverlayOpacityValue.toInt().coerceIn(0, 60))
    val simOptions = remember(context) { availableCallSimOptions(context) }
    var simChooserOpacityValue by remember(settings.simChooserOpacity) {
        mutableFloatStateOf((settings.simChooserOpacity.coerceIn(45, 100) - 45).toFloat())
    }
    val simChooserOpacity = (45 + simChooserOpacityValue.toInt().coerceIn(0, 55))
    var simChooserSizeValue by remember(settings.simChooserSizePercent) {
        mutableFloatStateOf((settings.simChooserSizePercent.coerceIn(72, 100) - 72).toFloat())
    }
    val simChooserSizePercent = (72 + simChooserSizeValue.toInt().coerceIn(0, 28))

    SettingsScaffold(title = "Preferences", onBack = onBack) { modifier ->
        LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SettingsSection(title = "Appearance & list") {
                    SettingsChoiceRow(
                        title = "Default sort order",
                        subtitle = "Choose how contacts are ordered when no search query is active.",
                        selected = settings.defaultContactSortOrder,
                        choices = listOf("FIRST_NAME", "LAST_NAME", "RECENTLY_ADDED", "MOST_CONTACTED"),
                        onSelect = appViewModel::setDefaultContactSortOrder,
                    )
                    SettingsSpacer()
                    SettingsChoiceRow(
                        title = "List density",
                        subtitle = "Compact fits more contacts on screen. Comfortable keeps larger spacing.",
                        selected = settings.contactListDensity,
                        choices = listOf("COMPACT", "COMFORTABLE"),
                        onSelect = appViewModel::setContactListDensity,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Show contact photos in list",
                        subtitle = "Hide photos for a cleaner and lighter scrolling experience.",
                        checked = settings.showContactPhotosInList,
                        onCheckedChange = appViewModel::setShowContactPhotosInList,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Show favorites first",
                        subtitle = "Keep favorite contacts near the top of the contacts tab.",
                        checked = settings.showFavoritesFirst,
                        onCheckedChange = appViewModel::setShowFavoritesFirst,
                    )
                }
            }
            item {
                SettingsSection(title = "Search & behavior") {
                    SettingsChoiceRow(
                        title = "Default start tab",
                        subtitle = "Choose what opens first on the home screen.",
                        selected = settings.defaultStartTab,
                        choices = listOf("CONTACTS", "CALL_LOG"),
                        onSelect = appViewModel::setDefaultStartTab,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Open contact directly on tap",
                        subtitle = "When disabled, tapping during selection mode only adjusts selection.",
                        checked = settings.openContactDirectlyOnTap,
                        onCheckedChange = appViewModel::setOpenContactDirectlyOnTap,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Show blocked contacts in search",
                        subtitle = "Hide blocked contacts from inline search results when disabled.",
                        checked = settings.showBlockedContactsInSearch,
                        onCheckedChange = appViewModel::setShowBlockedContactsInSearch,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Show recent calls preview",
                        subtitle = "Display recent call entries inside the contact details page.",
                        checked = settings.showRecentCallsPreview,
                        onCheckedChange = appViewModel::setShowRecentCallsPreview,
                    )
                }
            }
            item {
                val defaultPhoneStatus = if (isOpenContactsDefaultDialer(context)) {
                    localizedText("OpenContacts is the current default")
                } else {
                    localizedText("Another phone app is currently the default")
                }
                SettingsSection(title = "Calling") {
                    SettingsChoiceRow(
                        title = "Call button behavior",
                        subtitle = "Choose whether the call button should place the call inside OpenContacts after taking the default phone role, or hand the number off to the phone app on the device.",
                        selected = settings.callButtonMode,
                        choices = listOf("OPENCONTACTS_DEFAULT_APP", "SYSTEM_PHONE_APP"),
                        onSelect = appViewModel::setCallButtonMode,
                    )
                    SettingsSpacer()
                    SettingsChoiceRow(
                        title = "SIM choice behavior",
                        subtitle = "Pick a default SIM or ask every time before placing the call.",
                        selected = settings.callSimMode,
                        choices = listOf("ASK_EVERY_TIME", "DEFAULT_SIM"),
                        onSelect = appViewModel::setCallSimMode,
                    )
                    if (simOptions.isNotEmpty()) {
                        SettingsSpacer()
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(localizedText("Default SIM"), style = MaterialTheme.typography.titleMedium)
                            Text(localizedText("Choose which SIM should be used when the calling mode is set to the default SIM option."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                simOptions.forEach { option ->
                                    FilterChip(
                                        selected = settings.defaultCallSimHandleId == option.handleId,
                                        onClick = { appViewModel.setDefaultCallSimHandleId(option.handleId) },
                                        label = { Text(option.label) },
                                    )
                                }
                            }
                        }
                        SettingsSpacer()
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsValueRow(
                                title = "SIM chooser transparency",
                                subtitle = "Controls how solid the centered SIM chooser card should look.",
                                value = "$simChooserOpacity% · ${localizedText(simChooserOpacityLabel(simChooserOpacity))}",
                            )
                            Slider(
                                value = simChooserOpacityValue,
                                onValueChange = { simChooserOpacityValue = it },
                                valueRange = 0f..55f,
                                steps = 10,
                                onValueChangeFinished = { appViewModel.setSimChooserOpacity(simChooserOpacity) },
                            )
                        }
                        SettingsSpacer()
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsValueRow(
                                title = "SIM chooser size",
                                subtitle = "Adjust how wide the centered SIM chooser card should be.",
                                value = "$simChooserSizePercent% · ${localizedText(simChooserSizeLabel(simChooserSizePercent))}",
                            )
                            Slider(
                                value = simChooserSizeValue,
                                onValueChange = { simChooserSizeValue = it },
                                valueRange = 0f..28f,
                                steps = 12,
                                onValueChangeFinished = { appViewModel.setSimChooserSizePercent(simChooserSizePercent) },
                            )
                        }
                    }
                    SettingsSpacer()
                    SettingsValueRow(
                        title = "Default phone app status",
                        subtitle = "OpenContacts must become the default phone app if you want in-app calling, incoming call UI, and ongoing call controls through Telecom.",
                        value = defaultPhoneStatus,
                    )
                    SettingsSpacer()
                    SettingsValueRow(
                        title = "Set OpenContacts as default phone app",
                        subtitle = "Open Android's dialer-role prompt directly from inside the app for a faster setup path.",
                        value = localizedText(if (isOpenContactsDefaultDialer(context)) "Already enabled" else "Open prompt"),
                        onClick = { requestOpenContactsDefaultDialer(context) },
                    )
                }
            }
            item {
                SettingsSection(title = "Dial pad") {
                    SettingsSwitchRow(
                        title = "Show keypad letters",
                        subtitle = "Display ABC/DEF style hints on keypad buttons.",
                        checked = settings.dialPadShowLetters,
                        onCheckedChange = appViewModel::setDialPadShowLetters,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Auto-format numbers",
                        subtitle = "Add lightweight spacing while typing for easier reading.",
                        checked = settings.dialPadAutoFormat,
                        onCheckedChange = appViewModel::setDialPadAutoFormat,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Show T9 suggestions",
                        subtitle = "Suggest matching contacts while typing on the dial pad.",
                        checked = settings.dialPadShowT9Suggestions,
                        onCheckedChange = appViewModel::setDialPadShowT9Suggestions,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Long-press backspace clears all",
                        subtitle = "Keep single tap for one digit and long press for full clear.",
                        checked = settings.dialPadLongPressBackspaceClears,
                        onCheckedChange = appViewModel::setDialPadLongPressBackspaceClears,
                    )
                    SettingsSpacer()
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsValueRow(
                            title = "Dial pad toggle size",
                            subtitle = "Make the show/hide button a little bigger and keep it comfortable.",
                            value = "${dialPadButtonSizeDp} dp · ${localizedText(dialPadButtonSizeLabel(dialPadButtonSizeDp))}",
                        )
                        Slider(
                            value = dialPadButtonSizeValue,
                            onValueChange = { dialPadButtonSizeValue = it },
                            onValueChangeFinished = { appViewModel.setDialPadToggleButtonSizeDp(dialPadButtonSizeDp) },
                            valueRange = 0f..8f,
                            steps = 7,
                        )
                    }
                    SettingsSpacer()
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsValueRow(
                            title = "Backspace hold duration",
                            subtitle = "Choose how long you need to hold delete before the whole number clears.",
                            value = "${backspaceHoldDurationMs} ms · ${localizedText(backspaceHoldLabel(backspaceHoldDurationMs))}",
                        )
                        Slider(
                            value = backspaceHoldValue,
                            onValueChange = { backspaceHoldValue = it },
                            onValueChangeFinished = { appViewModel.setDialPadBackspaceLongPressDurationMs(backspaceHoldDurationMs) },
                            valueRange = 0f..12f,
                            steps = 11,
                        )
                    }
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Haptic feedback",
                        subtitle = "Use subtle vibration for quick toggles and keypad interactions.",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = appViewModel::setHapticFeedbackEnabled,
                    )
                }
            }
            item {
                SettingsSection(title = "Groups, folders & tags") {
                    SettingsChoiceRow(
                        title = "Tag sorting",
                        subtitle = "Choose how tags are ordered inside the workspace.",
                        selected = settings.groupTagSortOrder,
                        choices = listOf("MOST_USED", "ALPHABETICAL", "RECENT"),
                        onSelect = appViewModel::setGroupTagSortOrder,
                    )
                    SettingsSpacer()
                    SettingsChoiceRow(
                        title = "Folder drawer layout",
                        subtitle = "Show folders in the side drawer as the classic vertical list or as a horizontal visual strip with cover images.",
                        selected = settings.drawerFolderDisplayMode,
                        choices = listOf("LIST", "CAROUSEL"),
                        onSelect = appViewModel::setDrawerFolderDisplayMode,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Hide empty folders and tags",
                        subtitle = "Keep lists cleaner by hiding classifications with no contacts.",
                        checked = settings.hideEmptyFoldersAndTags,
                        onCheckedChange = appViewModel::setHideEmptyFoldersAndTags,
                    )
                    SettingsSpacer()
                    SettingsChoiceRow(
                        title = "Folder panel placement",
                        subtitle = "DRAWER keeps folders in the side drawer. TOP_STRIP shows a scrollable chip row above the contact list. SIDE_PANEL pins the strip vertically on the left edge (mirrors TOP_STRIP on smaller phones).",
                        selected = settings.folderPanelPlacement,
                        choices = listOf("DRAWER", "TOP_STRIP", "SIDE_PANEL"),
                        onSelect = appViewModel::setFolderPanelPlacement,
                    )
                    SettingsSpacer()
                    SettingsChoiceRow(
                        title = "Folder panel mode",
                        subtitle = "LIST shows full labels with contact counts. COMPACT reduces padding to fit more folders. ICONS shows only the folder initial/color chip.",
                        selected = settings.folderPanelMode,
                        choices = listOf("LIST", "COMPACT", "ICONS"),
                        onSelect = appViewModel::setFolderPanelMode,
                    )
                }
            }
            item {
                SettingsSection(title = "Trash retention", subtitle = "Choose how long deleted contacts stay in the trash before permanent removal.") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = { appViewModel.setTrashRetentionDays(retentionDays) },
                            valueRange = 0f..3f,
                            steps = 2,
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            retentionOptions.forEach { option ->
                                Text(localizedText(retentionLabel(option)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            localizedText("Deleted contacts will be kept for ${retentionLabel(retentionDays)} before permanent removal."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                SettingsSection(title = "Import, export & backup") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsValueRow(
                            title = "Progress panel opacity",
                            subtitle = "Control how solid the floating progress card looks while import, export, backup, and restore tasks are running.",
                            value = "$transferOverlayOpacity% · ${localizedText(transferOverlayOpacityLabel(transferOverlayOpacity))}",
                        )
                        Slider(
                            value = transferOverlayOpacityValue,
                            onValueChange = { transferOverlayOpacityValue = it },
                            onValueChangeFinished = { appViewModel.setTransferProgressOverlayOpacity(transferOverlayOpacity) },
                            valueRange = 0f..60f,
                            steps = 59,
                        )
                    }
                }
            }
            item {
                SettingsSection(title = "Safety and export") {
                    SettingsSwitchRow(
                        title = "Confirm before delete",
                        subtitle = "Ask before deleting a contact or multiple selected contacts.",
                        checked = settings.confirmBeforeDelete,
                        onCheckedChange = appViewModel::setConfirmBeforeDelete,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Confirm before block/unblock",
                        subtitle = "Require confirmation before changing blocked state.",
                        checked = settings.confirmBeforeBlockUnblock,
                        onCheckedChange = appViewModel::setConfirmBeforeBlockUnblock,
                    )
                    SettingsSpacer()
                    SettingsSwitchRow(
                        title = "Include timestamp in exported filenames",
                        subtitle = "Safer for repeated exports and easier to trace later.",
                        checked = settings.includeTimestampInExportFileName,
                        onCheckedChange = appViewModel::setIncludeTimestampInExportFileName,
                    )
                }
            }
        }
    }
}
