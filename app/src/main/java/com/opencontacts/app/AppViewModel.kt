package com.opencontacts.app

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.crypto.AppLockRepository
import com.opencontacts.core.crypto.AppLockSettings
import com.opencontacts.core.crypto.BiometricAuthManager
import com.opencontacts.core.crypto.BiometricAvailability
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.data.repository.TransferDestinationKind
import com.opencontacts.data.repository.TransferDestinationManager
import com.opencontacts.domain.contacts.ContactRepository
import com.opencontacts.domain.vaults.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
    private val contactRepository: ContactRepository,
    private val appLockRepository: AppLockRepository,
    private val transferDestinationManager: TransferDestinationManager,
    private val biometricAuthManager: BiometricAuthManager,
    private val sessionManager: VaultSessionManager,
) : ViewModel() {
    val appLockSettings: StateFlow<AppLockSettings> = appLockRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLockSettings.DEFAULT)

    val activeVaultId: StateFlow<String?> = sessionManager.activeVaultId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLocked: StateFlow<Boolean> = sessionManager.isLocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val shouldShowUnlock: StateFlow<Boolean> = combine(activeVaultId, isLocked, appLockSettings) { vaultId, locked, settings ->
        vaultId != null && locked && (settings.hasPin || settings.biometricEnabled)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val vaults = vaultRepository.observeVaults()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeVaultName: StateFlow<String> = combine(activeVaultId, vaults) { vaultId, allVaults ->
        allVaults.firstOrNull { it.id == vaultId }?.displayName ?: "No active vault"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "No active vault")

    val activeContactCount: StateFlow<Int> = activeVaultId
        .combine(isLocked) { vaultId, locked -> vaultId to locked }
        .flatMapLatest { (vaultId, locked) ->
            if (vaultId == null || locked) kotlinx.coroutines.flow.flowOf(emptyList()) else contactRepository.observeContacts(vaultId)
        }
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val blockedContacts = activeVaultId
        .combine(isLocked) { vaultId, locked -> vaultId to locked }
        .flatMapLatest { (vaultId, locked) ->
            if (vaultId == null || locked) kotlinx.coroutines.flow.flowOf(emptyList()) else contactRepository.observeBlockedContacts(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError

    private val _storageMessage = MutableStateFlow<String?>(null)
    val storageMessage: StateFlow<String?> = _storageMessage

    val appVersion: String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "v${info.versionName}"
    }.getOrDefault("v0.0.28")

    private var lastBackgroundElapsedRealtime: Long = 0L

    init {
        viewModelScope.launch {
            val defaultVault = vaultRepository.ensureDefaultVault()
            val settings = appLockRepository.settings.first()
            val shouldLock = settings.hasPin || settings.biometricEnabled
            vaultRepository.setLocked(defaultVault.id, shouldLock)
            sessionManager.setVault(defaultVault.id, locked = shouldLock)
            contactRepository.warmUpVault(defaultVault.id)
            purgeTrashIfNeeded(defaultVault.id, settings)
        }
    }

    fun setPin(pin: String) {
        if (pin.length < 4 || !pin.all(Char::isDigit)) {
            _pinError.value = "PIN must contain at least 4 digits"
            return
        }
        viewModelScope.launch {
            appLockRepository.setPin(pin.toCharArray())
            _pinError.value = null
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            appLockRepository.clearPin()
            _pinError.value = null
            activeVaultId.value?.let { vaultRepository.setLocked(it, false) }
            sessionManager.unlock()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appLockRepository.setBiometricEnabled(enabled)
            if (!enabled && !appLockSettings.value.hasPin) {
                activeVaultId.value?.let { vaultRepository.setLocked(it, false) }
                sessionManager.unlock()
            }
        }
    }

    fun setAllowDeviceCredential(enabled: Boolean) = launchSetting { appLockRepository.setAllowDeviceCredential(enabled) }
    fun setLockOnAppResume(enabled: Boolean) = launchSetting { appLockRepository.setLockOnAppResume(enabled) }
    fun setLockAfterInactivitySeconds(seconds: Int) = launchSetting { appLockRepository.setLockAfterInactivitySeconds(seconds) }
    fun setTrashRetentionDays(days: Int) = launchSetting { appLockRepository.setTrashRetentionDays(days) }
    fun setThemeMode(mode: String) = launchSetting { appLockRepository.setThemeMode(mode) }
    fun setThemePreset(value: String) = launchSetting { appLockRepository.setThemePreset(value) }
    fun setAccentPalette(value: String) = launchSetting { appLockRepository.setAccentPalette(value) }
    fun setCornerStyle(value: String) = launchSetting { appLockRepository.setCornerStyle(value) }
    fun setDefaultContactSortOrder(value: String) = launchSetting { appLockRepository.setDefaultContactSortOrder(value) }
    fun setContactListDensity(value: String) = launchSetting { appLockRepository.setContactListDensity(value) }
    fun setShowContactPhotosInList(enabled: Boolean) = launchSetting { appLockRepository.setShowContactPhotosInList(enabled) }
    fun setDefaultStartTab(value: String) = launchSetting { appLockRepository.setDefaultStartTab(value) }
    fun setConfirmBeforeDelete(enabled: Boolean) = launchSetting { appLockRepository.setConfirmBeforeDelete(enabled) }
    fun setConfirmBeforeBlockUnblock(enabled: Boolean) = launchSetting { appLockRepository.setConfirmBeforeBlockUnblock(enabled) }
    fun setShowRecentCallsPreview(enabled: Boolean) = launchSetting { appLockRepository.setShowRecentCallsPreview(enabled) }
    fun setAutoCollapseCallGroups(enabled: Boolean) = launchSetting { appLockRepository.setAutoCollapseCallGroups(enabled) }
    fun setShowBlockedContactsInSearch(enabled: Boolean) = launchSetting { appLockRepository.setShowBlockedContactsInSearch(enabled) }
    fun setIncludeTimestampInExportFileName(enabled: Boolean) = launchSetting { appLockRepository.setIncludeTimestampInExportFileName(enabled) }
    fun setHideEmptyFoldersAndTags(enabled: Boolean) = launchSetting { appLockRepository.setHideEmptyFoldersAndTags(enabled) }
    fun setOpenContactDirectlyOnTap(enabled: Boolean) = launchSetting { appLockRepository.setOpenContactDirectlyOnTap(enabled) }
    fun setShowFavoritesFirst(enabled: Boolean) = launchSetting { appLockRepository.setShowFavoritesFirst(enabled) }
    fun setEnableIncomingCallerPopup(enabled: Boolean) = launchSetting { appLockRepository.setEnableIncomingCallerPopup(enabled) }
    fun setEnableMissedCallNotification(enabled: Boolean) = launchSetting { appLockRepository.setEnableMissedCallNotification(enabled) }
    fun setShowPhotoInNotifications(enabled: Boolean) = launchSetting { appLockRepository.setShowPhotoInNotifications(enabled) }
    fun setShowFolderTagsInNotifications(enabled: Boolean) = launchSetting { appLockRepository.setShowFolderTagsInNotifications(enabled) }
    fun setLockScreenNotificationVisibility(value: String) = launchSetting { appLockRepository.setLockScreenNotificationVisibility(value) }
    fun setHeadsUpNotifications(enabled: Boolean) = launchSetting { appLockRepository.setHeadsUpNotifications(enabled) }
    fun setOverlayPopupMode(value: String) = launchSetting { appLockRepository.setOverlayPopupMode(value) }
    fun setVibrationEnabled(enabled: Boolean) = launchSetting { appLockRepository.setVibrationEnabled(enabled) }
    fun setSoundEnabled(enabled: Boolean) = launchSetting { appLockRepository.setSoundEnabled(enabled) }
    fun setHapticFeedbackEnabled(enabled: Boolean) = launchSetting { appLockRepository.setHapticFeedbackEnabled(enabled) }
    fun setDialPadShowLetters(enabled: Boolean) = launchSetting { appLockRepository.setDialPadShowLetters(enabled) }
    fun setDialPadAutoFormat(enabled: Boolean) = launchSetting { appLockRepository.setDialPadAutoFormat(enabled) }
    fun setDialPadShowT9Suggestions(enabled: Boolean) = launchSetting { appLockRepository.setDialPadShowT9Suggestions(enabled) }
    fun setDialPadLongPressBackspaceClears(enabled: Boolean) = launchSetting { appLockRepository.setDialPadLongPressBackspaceClears(enabled) }
    fun setDialPadToggleButtonSizeDp(value: Int) = launchSetting { appLockRepository.setDialPadToggleButtonSizeDp(value) }
    fun setDialPadBackspaceLongPressDurationMs(value: Int) = launchSetting { appLockRepository.setDialPadBackspaceLongPressDurationMs(value) }
    fun setGroupTagSortOrder(value: String) = launchSetting { appLockRepository.setGroupTagSortOrder(value) }
    fun setDrawerFolderDisplayMode(value: String) = launchSetting { appLockRepository.setDrawerFolderDisplayMode(value) }
    fun setIncomingCallCompactMode(enabled: Boolean) = launchSetting { appLockRepository.setIncomingCallCompactMode(enabled) }
    fun setIncomingCallShowNumber(enabled: Boolean) = launchSetting { appLockRepository.setIncomingCallShowNumber(enabled) }
    fun setIncomingCallShowTag(enabled: Boolean) = launchSetting { appLockRepository.setIncomingCallShowTag(enabled) }
    fun setIncomingCallShowGroup(enabled: Boolean) = launchSetting { appLockRepository.setIncomingCallShowGroup(enabled) }
    fun setIncomingCallPhotoBackgroundEnabled(enabled: Boolean) = launchSetting { appLockRepository.setIncomingCallPhotoBackgroundEnabled(enabled) }
    fun setIncomingCallWindowTransparency(value: Int) = launchSetting { appLockRepository.setIncomingCallWindowTransparency(value) }
    fun setIncomingCallWindowSize(value: String) = launchSetting { appLockRepository.setIncomingCallWindowSize(value) }
    fun setIncomingCallWindowPosition(value: String) = launchSetting { appLockRepository.setIncomingCallWindowPosition(value) }
    fun setIncomingCallAutoDismissSeconds(seconds: Int) = launchSetting { appLockRepository.setIncomingCallAutoDismissSeconds(seconds) }
    fun setTransferProgressOverlayOpacity(value: Int) = launchSetting { appLockRepository.setTransferProgressOverlayOpacity(value) }
    fun setAppIconAlias(value: String) = launchSetting { appLockRepository.setAppIconAlias(value) }
    fun setBackgroundCategory(value: String) = launchSetting { appLockRepository.setBackgroundCategory(value) }
    fun setCallCardCornerRadius(value: Int) = launchSetting { appLockRepository.setCallCardCornerRadius(value) }
    fun setCallButtonMode(value: String) = launchSetting { appLockRepository.setCallButtonMode(value) }
    fun setCallSimMode(value: String) = launchSetting { appLockRepository.setCallSimMode(value) }
    fun setDefaultCallSimHandleId(value: String?) = launchSetting { appLockRepository.setDefaultCallSimHandleId(value) }
    fun setSimChooserOpacity(value: Int) = launchSetting { appLockRepository.setSimChooserOpacity(value) }
    fun setSimChooserSizePercent(value: Int) = launchSetting { appLockRepository.setSimChooserSizePercent(value) }
    fun setAppFontProfile(value: String) = launchSetting { appLockRepository.setAppFontProfile(value) }
    fun clearCustomFont() {
        viewModelScope.launch {
            val previousPath = appLockSettings.value.customFontPath
            previousPath?.let { runCatching { File(it).delete() } }
            appLockRepository.setCustomFontPath(null)
            appLockRepository.setCustomFontDisplayName(null)
            appLockRepository.setAppFontProfile("SYSTEM_DEFAULT")
            showStorageMessage("Custom font cleared.")
        }
    }
    fun importCustomFont(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val previousPath = appLockSettings.value.customFontPath
            val fontsDir = File(context.filesDir, "user_fonts").apply { mkdirs() }
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "custom_font.ttf"
            val ext = displayName.substringAfterLast('.', "ttf").ifBlank { "ttf" }
            val target = File(fontsDir, "selected_font.$ext")
            runCatching { previousPath?.let { old -> if (old != target.absolutePath) File(old).delete() } }
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to read selected font")
            appLockRepository.setCustomFontPath(target.absolutePath)
            appLockRepository.setCustomFontDisplayName(displayName)
            appLockRepository.setAppFontProfile("CUSTOM_UPLOAD")
            showStorageMessage("Font applied: $displayName")
        }
    }

    fun setAppLanguage(value: String) = launchSetting { appLockRepository.setAppLanguage(value) }

    // SMS settings
    fun setSmsDeliveryReports(enabled: Boolean) = launchSetting { appLockRepository.setSmsDeliveryReports(enabled) }
    fun setFlashSmsAttempt(enabled: Boolean) = launchSetting { appLockRepository.setFlashSmsAttempt(enabled) }
    fun setPreferredSmsSimHandleId(value: String?) = launchSetting { appLockRepository.setPreferredSmsSimHandleId(value) }
    fun setQuickSmsTemplate1(value: String) = launchSetting { appLockRepository.setQuickSmsTemplate1(value) }
    fun setQuickSmsTemplate2(value: String) = launchSetting { appLockRepository.setQuickSmsTemplate2(value) }
    fun setQuickSmsTemplate3(value: String) = launchSetting { appLockRepository.setQuickSmsTemplate3(value) }
    // Mini call view
    fun setMiniCallViewEnabled(enabled: Boolean) = launchSetting { appLockRepository.setMiniCallViewEnabled(enabled) }
    fun setMiniCallViewOpacity(value: Int) = launchSetting { appLockRepository.setMiniCallViewOpacity(value) }
    fun setMiniCallViewSize(value: String) = launchSetting { appLockRepository.setMiniCallViewSize(value) }
    fun setMiniCallViewPosition(value: String) = launchSetting { appLockRepository.setMiniCallViewPosition(value) }
    // Lock screen call UI
    fun setLockScreenCallUiEnabled(enabled: Boolean) = launchSetting { appLockRepository.setLockScreenCallUiEnabled(enabled) }
    // Folder panel
    fun setFolderPanelPlacement(value: String) = launchSetting { appLockRepository.setFolderPanelPlacement(value) }
    fun setFolderPanelMode(value: String) = launchSetting { appLockRepository.setFolderPanelMode(value) }

    fun setExportPath(value: String) = launchSetting { appLockRepository.setExportPath(value) }
    fun setBackupPath(value: String) = launchSetting { appLockRepository.setBackupPath(value) }


    fun setLockScreenBackground(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appLockRepository.setLockScreenBackgroundUri(uri.toString())
        }
    }

    fun clearLockScreenBackground() = launchSetting { appLockRepository.setLockScreenBackgroundUri(null) }

    fun setAppIconPreview(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appLockRepository.setAppIconPreviewUri(uri.toString())
        }
    }

    fun clearAppIconPreview() = launchSetting { appLockRepository.setAppIconPreviewUri(null) }

    fun applyLauncherIcon(alias: String) {
        LauncherIconManager.applyAlias(context, alias)
        showStorageMessage("Launcher icon updated. Some launchers may require a short restart or refresh.")
    }

    fun showUiError(message: String) {
        _pinError.value = message
    }

    fun unlockWithPin(pin: String) {
        viewModelScope.launch {
            val valid = appLockRepository.verifyPin(pin.toCharArray())
            if (valid) {
                _pinError.value = null
                activeVaultId.value?.let { vaultRepository.setLocked(it, false) }
                sessionManager.unlock()
            } else {
                _pinError.value = "Incorrect PIN"
            }
        }
    }

    fun unlockWithBiometricSuccess() {
        viewModelScope.launch {
            _pinError.value = null
            activeVaultId.value?.let { vaultRepository.setLocked(it, false) }
            sessionManager.unlock()
        }
    }

    fun lockNow() {
        viewModelScope.launch {
            activeVaultId.value?.let { vaultRepository.setLocked(it, true) }
            sessionManager.lock()
        }
    }

    fun clearError() {
        _pinError.value = null
    }

    fun switchVault(vaultId: String) {
        viewModelScope.launch {
            val target = vaults.value.firstOrNull { it.id == vaultId } ?: return@launch
            sessionManager.setVault(vaultId, locked = target.isLocked)
            contactRepository.warmUpVault(vaultId)
            purgeTrashIfNeeded(vaultId, appLockSettings.value)
        }
    }

    fun setBackupFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = transferDestinationManager.setFolder(TransferDestinationKind.BACKUP, uri)
                _storageMessage.value = "Backup folder set to ${result.displayName}"
            } catch (error: Exception) {
                _storageMessage.value = error.message ?: "Unable to use the selected backup folder."
            }
        }
    }

    fun setExportFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = transferDestinationManager.setFolder(TransferDestinationKind.EXPORT, uri)
                _storageMessage.value = "Export folder set to ${result.displayName}"
            } catch (error: Exception) {
                _storageMessage.value = error.message ?: "Unable to use the selected export folder."
            }
        }
    }

    fun setRecordingsFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = transferDestinationManager.setFolder(TransferDestinationKind.RECORDINGS, uri)
                _storageMessage.value = "Recordings folder set to ${result.displayName}"
            } catch (error: Exception) {
                _storageMessage.value = error.message ?: "Unable to use the selected recordings folder."
            }
        }
    }

    fun resetBackupFolder() {
        viewModelScope.launch {
            transferDestinationManager.resetFolder(TransferDestinationKind.BACKUP)
            _storageMessage.value = "Backup folder reset to the default app storage location."
        }
    }

    fun resetExportFolder() {
        viewModelScope.launch {
            transferDestinationManager.resetFolder(TransferDestinationKind.EXPORT)
            _storageMessage.value = "Export folder reset to the default app storage location."
        }
    }

    fun resetRecordingsFolder() {
        viewModelScope.launch {
            transferDestinationManager.resetFolder(TransferDestinationKind.RECORDINGS)
            _storageMessage.value = "Recordings folder reset to the default app storage location."
        }
    }

    fun setShowAppNameOnLockScreen(enabled: Boolean) = launchSetting { appLockRepository.setShowAppNameOnLockScreen(enabled) }
    fun setShowAppIconOnLockScreen(enabled: Boolean) = launchSetting { appLockRepository.setShowAppIconOnLockScreen(enabled) }
    fun setShowTimeOnLockScreen(enabled: Boolean) = launchSetting { appLockRepository.setShowTimeOnLockScreen(enabled) }
    fun setUseIllustrationOnLockScreen(enabled: Boolean) = launchSetting { appLockRepository.setUseIllustrationOnLockScreen(enabled) }
    fun setLockScreenMessage(value: String) = launchSetting { appLockRepository.setLockScreenMessage(value) }
    fun setLockScreenStyle(value: String) = launchSetting { appLockRepository.setLockScreenStyle(value) }
    fun setFingerprintIconStyle(value: String) = launchSetting { appLockRepository.setFingerprintIconStyle(value) }

    fun showStorageMessage(message: String) { _storageMessage.value = message }
    fun clearStorageMessage() { _storageMessage.value = null }

    fun createVault(displayName: String) {
        viewModelScope.launch {
            vaultRepository.createVault(displayName)
        }
    }

    fun unblockContact(contactId: String) {
        val vaultId = activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.setContactBlocked(vaultId, contactId, false) }
    }

    fun deleteVault(vaultId: String) {
        viewModelScope.launch {
            val current = activeVaultId.value
            vaultRepository.deleteVault(vaultId)
            val fallback = vaultRepository.ensureDefaultVault()
            if (current == null || current == vaultId) {
                sessionManager.setVault(fallback.id, locked = fallback.isLocked)
                contactRepository.warmUpVault(fallback.id)
            }
        }
    }

    fun onAppBackgrounded() {
        lastBackgroundElapsedRealtime = SystemClock.elapsedRealtime()
    }

    fun onAppForegrounded() {
        val settings = appLockSettings.value
        if (!settings.lockOnAppResume) return
        val vaultId = activeVaultId.value ?: return
        val secured = settings.hasPin || settings.biometricEnabled
        if (!secured) return
        if (lastBackgroundElapsedRealtime == 0L) return
        val idleMillis = SystemClock.elapsedRealtime() - lastBackgroundElapsedRealtime
        val timeoutMillis = settings.lockAfterInactivitySeconds * 1000L
        if (idleMillis >= timeoutMillis) {
            viewModelScope.launch {
                vaultRepository.setLocked(vaultId, true)
                sessionManager.lock()
            }
        }
    }

    fun canUseBiometric(): Boolean = biometricAuthManager.canAuthenticate(appLockSettings.value.allowDeviceCredential)
    fun biometricAvailability(): BiometricAvailability = biometricAuthManager.availability(appLockSettings.value.allowDeviceCredential)
    fun biometricPromptInfo(title: String) = biometricAuthManager.createPromptInfo(title, appLockSettings.value.allowDeviceCredential)

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private suspend fun purgeTrashIfNeeded(vaultId: String, settings: AppLockSettings) {
        val retentionMillis = settings.trashRetentionDays * 24L * 60L * 60L * 1000L
        if (retentionMillis > 0) {
            contactRepository.purgeDeletedOlderThan(vaultId, System.currentTimeMillis() - retentionMillis)
        }
    }
}
