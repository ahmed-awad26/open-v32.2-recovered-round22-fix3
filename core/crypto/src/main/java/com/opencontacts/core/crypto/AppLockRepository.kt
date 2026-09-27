package com.opencontacts.core.crypto

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONObject

private val Context.appLockDataStore by preferencesDataStore(name = "app_lock_settings")

object SettingsDefaults {
    const val THEME_MODE = "SYSTEM"
    const val DEFAULT_EXPORT_PATH = "exports"
    const val DEFAULT_BACKUP_PATH = "vault_backups"
    const val SORT_ORDER = "FIRST_NAME"
    const val LIST_DENSITY = "COMFORTABLE"
    const val DEFAULT_START_TAB = "CONTACTS"
    const val LOCK_SCREEN_VISIBILITY = "HIDE_SENSITIVE"
    const val OVERLAY_POPUP_MODE = "OVERLAY_WINDOW"
    const val GROUP_TAG_SORT_ORDER = "MOST_USED"
    const val DRAWER_FOLDER_DISPLAY_MODE = "LIST"
    const val INCOMING_CALL_WINDOW_SIZE = "COMPACT"
    const val INCOMING_CALL_WINDOW_POSITION = "CENTER"
    const val APP_LANGUAGE = "SYSTEM"
    const val THEME_PRESET = "CLASSIC"
    const val ACCENT_PALETTE = "BLUE"
    const val CORNER_STYLE = "ROUNDED"
    const val LOCK_SCREEN_STYLE = "PREMIUM"
    const val FINGERPRINT_ICON_STYLE = "FILLED"
    const val APP_ICON_ALIAS = "DEFAULT"
    const val BACKGROUND_CATEGORY = "MINIMAL"
    const val CALL_CARD_CORNER_RADIUS = 28
    const val DEFAULT_RECORDINGS_PATH = "call_recordings"
    const val LOCK_AFTER_INACTIVITY_SECONDS = 30
    const val TRASH_RETENTION_DAYS = 30
    const val DIAL_PAD_TOGGLE_BUTTON_SIZE_DP = 64
    const val DIAL_PAD_BACKSPACE_LONG_PRESS_DURATION_MS = 700
    const val CALL_BUTTON_MODE = "OPENCONTACTS_DEFAULT_APP"
    const val CALL_SIM_MODE = "ASK_EVERY_TIME"
    const val SIM_CHOOSER_OPACITY = 90
    const val SIM_CHOOSER_SIZE_PERCENT = 92
    const val APP_FONT_PROFILE = "SYSTEM_DEFAULT"
    const val TRANSFER_PROGRESS_OVERLAY_OPACITY = 92
    // SMS
    const val SMS_DELIVERY_REPORTS = true
    const val FLASH_SMS_ATTEMPT = false
    const val PREFERRED_SMS_SIM_HANDLE_ID = ""
    const val QUICK_SMS_TEMPLATE_1 = "I'll call you back shortly."
    const val QUICK_SMS_TEMPLATE_2 = "I'm busy right now, will get back to you."
    const val QUICK_SMS_TEMPLATE_3 = "Please send the details by message."
    // Mini call view
    const val MINI_CALL_VIEW_ENABLED = true
    const val MINI_CALL_VIEW_OPACITY = 92
    const val MINI_CALL_VIEW_SIZE = "COMPACT"   // COMPACT | EXPANDED
    const val MINI_CALL_VIEW_POSITION = "BOTTOM_END" // BOTTOM_END | BOTTOM_START | TOP_END
    // Lock screen full-screen call UI
    const val LOCK_SCREEN_CALL_UI_ENABLED = true
    // Folders panel
    const val FOLDER_PANEL_PLACEMENT = "DRAWER" // DRAWER | TOP_STRIP | SIDE_PANEL
    const val FOLDER_PANEL_MODE = "LIST"         // LIST | COMPACT | ICONS
}

@Singleton
class AppLockRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppLockSettings> = context.appLockDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map(::mapSettings)

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Synchronous, always-available snapshot of [settings] for call sites that cannot suspend
     * (BroadcastReceiver.onReceive reached via goAsync, Service lifecycle callbacks, etc.) and would
     * otherwise have to block the calling thread with `runBlocking` to read a value. Backed by
     * `stateIn(Eagerly)`, so collection starts immediately when the repository is created and the
     * cached value is kept fresh in the background — reading [currentSettings] never blocks.
     */
    val currentSettings: AppLockSettings
        get() = cachedSettings.value

    private val cachedSettings: StateFlow<AppLockSettings> = settings.stateIn(
        scope = repositoryScope,
        started = SharingStarted.Eagerly,
        initialValue = AppLockSettings.DEFAULT,
    )

    suspend fun setPin(pin: CharArray) {
        val salt = ByteArray(SALT_LENGTH).also(SecureRandom()::nextBytes)
        val hash = hashPin(pin, salt)
        context.appLockDataStore.edit { prefs ->
            prefs[PIN_HASH] = encode(hash)
            prefs[PIN_SALT] = encode(salt)
        }
        pin.fill('\u0000')
    }

    suspend fun clearPin() {
        context.appLockDataStore.edit { prefs ->
            prefs.remove(PIN_HASH)
            prefs.remove(PIN_SALT)
        }
    }

    suspend fun verifyPin(pin: CharArray): Boolean {
        val snapshot = context.appLockDataStore.data.first()
        val storedHash = snapshot[PIN_HASH] ?: return false
        val storedSalt = snapshot[PIN_SALT] ?: return false
        val computed = hashPin(pin, decode(storedSalt))
        pin.fill('\u0000')
        return encode(computed) == storedHash
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = edit { it[BIOMETRIC_ENABLED] = enabled }
    suspend fun setAllowDeviceCredential(enabled: Boolean) = edit { it[ALLOW_DEVICE_CREDENTIAL] = enabled }
    suspend fun setLockOnAppResume(enabled: Boolean) = edit { it[LOCK_ON_APP_RESUME] = enabled }
    suspend fun setLockAfterInactivitySeconds(seconds: Int) = edit { it[LOCK_AFTER_INACTIVITY_SECONDS] = seconds.coerceIn(0, 3600) }
    suspend fun setTrashRetentionDays(days: Int) = edit { it[TRASH_RETENTION_DAYS] = days.coerceIn(7, 3650) }
    suspend fun setThemeMode(mode: String) = edit { it[THEME_MODE] = mode.uppercase() }
    suspend fun setThemePreset(value: String) = edit { it[THEME_PRESET] = value.uppercase() }
    suspend fun setAccentPalette(value: String) = edit { it[ACCENT_PALETTE] = value.uppercase() }
    suspend fun setCornerStyle(value: String) = edit { it[CORNER_STYLE] = value.uppercase() }
    suspend fun setExportPath(path: String) = edit { it[EXPORT_PATH] = path.ifBlank { SettingsDefaults.DEFAULT_EXPORT_PATH } }
    suspend fun setBackupPath(path: String) = edit { it[BACKUP_PATH] = path.ifBlank { SettingsDefaults.DEFAULT_BACKUP_PATH } }

    suspend fun setExportFolder(uri: String?, displayName: String?) {
        edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(EXPORT_FOLDER_URI) else prefs[EXPORT_FOLDER_URI] = uri
            if (displayName.isNullOrBlank()) prefs.remove(EXPORT_FOLDER_NAME) else prefs[EXPORT_FOLDER_NAME] = displayName
        }
    }

    suspend fun setBackupFolder(uri: String?, displayName: String?) {
        edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(BACKUP_FOLDER_URI) else prefs[BACKUP_FOLDER_URI] = uri
            if (displayName.isNullOrBlank()) prefs.remove(BACKUP_FOLDER_NAME) else prefs[BACKUP_FOLDER_NAME] = displayName
        }
    }

    suspend fun setRecordingsPath(path: String) = edit { it[RECORDINGS_PATH] = path.ifBlank { SettingsDefaults.DEFAULT_RECORDINGS_PATH } }

    suspend fun setRecordingsFolder(uri: String?, displayName: String?) {
        edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(RECORDINGS_FOLDER_URI) else prefs[RECORDINGS_FOLDER_URI] = uri
            if (displayName.isNullOrBlank()) prefs.remove(RECORDINGS_FOLDER_NAME) else prefs[RECORDINGS_FOLDER_NAME] = displayName
        }
    }

    suspend fun setShowAppNameOnLockScreen(enabled: Boolean) = edit { it[SHOW_APP_NAME_ON_LOCK_SCREEN] = enabled }
    suspend fun setShowAppIconOnLockScreen(enabled: Boolean) = edit { it[SHOW_APP_ICON_ON_LOCK_SCREEN] = enabled }
    suspend fun setShowTimeOnLockScreen(enabled: Boolean) = edit { it[SHOW_TIME_ON_LOCK_SCREEN] = enabled }
    suspend fun setUseIllustrationOnLockScreen(enabled: Boolean) = edit { it[USE_ILLUSTRATION_ON_LOCK_SCREEN] = enabled }
    suspend fun setLockScreenMessage(value: String) = edit { it[LOCK_SCREEN_MESSAGE] = value.take(120) }
    suspend fun setLockScreenStyle(value: String) = edit { it[LOCK_SCREEN_STYLE] = value.uppercase() }
    suspend fun setFingerprintIconStyle(value: String) = edit { it[FINGERPRINT_ICON_STYLE] = value.uppercase() }
    suspend fun setLockScreenBackgroundUri(value: String?) = edit { prefs -> if (value.isNullOrBlank()) prefs.remove(LOCK_SCREEN_BACKGROUND_URI) else prefs[LOCK_SCREEN_BACKGROUND_URI] = value }
    suspend fun setAppIconAlias(value: String) = edit { it[APP_ICON_ALIAS] = value.uppercase() }
    suspend fun setBackgroundCategory(value: String) = edit { it[BACKGROUND_CATEGORY] = value.uppercase() }
    suspend fun setCallCardCornerRadius(value: Int) = edit { it[CALL_CARD_CORNER_RADIUS] = value.coerceIn(16, 40) }
    suspend fun setAppIconPreviewUri(value: String?) = edit { prefs -> if (value.isNullOrBlank()) prefs.remove(APP_ICON_PREVIEW_URI) else prefs[APP_ICON_PREVIEW_URI] = value }

    suspend fun setDefaultContactSortOrder(value: String) = edit { it[DEFAULT_CONTACT_SORT_ORDER] = value.uppercase() }
    suspend fun setContactListDensity(value: String) = edit { it[CONTACT_LIST_DENSITY] = value.uppercase() }
    suspend fun setShowContactPhotosInList(enabled: Boolean) = edit { it[SHOW_CONTACT_PHOTOS_IN_LIST] = enabled }
    suspend fun setDefaultStartTab(value: String) = edit { it[DEFAULT_START_TAB] = value.uppercase() }
    suspend fun setConfirmBeforeDelete(enabled: Boolean) = edit { it[CONFIRM_BEFORE_DELETE] = enabled }
    suspend fun setConfirmBeforeBlockUnblock(enabled: Boolean) = edit { it[CONFIRM_BEFORE_BLOCK_UNBLOCK] = enabled }
    suspend fun setShowRecentCallsPreview(enabled: Boolean) = edit { it[SHOW_RECENT_CALLS_PREVIEW] = enabled }
    suspend fun setAutoCollapseCallGroups(enabled: Boolean) = edit { it[AUTO_COLLAPSE_CALL_GROUPS] = enabled }
    suspend fun setShowBlockedContactsInSearch(enabled: Boolean) = edit { it[SHOW_BLOCKED_CONTACTS_IN_SEARCH] = enabled }
    suspend fun setIncludeTimestampInExportFileName(enabled: Boolean) = edit { it[INCLUDE_TIMESTAMP_IN_EXPORT_FILENAME] = enabled }
    suspend fun setHideEmptyFoldersAndTags(enabled: Boolean) = edit { it[HIDE_EMPTY_FOLDERS_AND_TAGS] = enabled }
    suspend fun setOpenContactDirectlyOnTap(enabled: Boolean) = edit { it[OPEN_CONTACT_DIRECTLY_ON_TAP] = enabled }
    suspend fun setShowFavoritesFirst(enabled: Boolean) = edit { it[SHOW_FAVORITES_FIRST] = enabled }
    suspend fun setEnableIncomingCallerPopup(enabled: Boolean) = edit { it[ENABLE_INCOMING_CALLER_POPUP] = enabled }
    suspend fun setEnableMissedCallNotification(enabled: Boolean) = edit { it[ENABLE_MISSED_CALL_NOTIFICATION] = enabled }
    suspend fun setShowPhotoInNotifications(enabled: Boolean) = edit { it[SHOW_PHOTO_IN_NOTIFICATIONS] = enabled }
    suspend fun setShowFolderTagsInNotifications(enabled: Boolean) = edit { it[SHOW_FOLDER_TAGS_IN_NOTIFICATIONS] = enabled }
    suspend fun setLockScreenNotificationVisibility(value: String) = edit { it[LOCK_SCREEN_NOTIFICATION_VISIBILITY] = value.uppercase() }
    suspend fun setHeadsUpNotifications(enabled: Boolean) = edit { it[HEADS_UP_NOTIFICATIONS] = enabled }
    suspend fun setOverlayPopupMode(value: String) = edit { it[OVERLAY_POPUP_MODE] = value.uppercase() }
    suspend fun setVibrationEnabled(enabled: Boolean) = edit { it[VIBRATION_ENABLED] = enabled }
    suspend fun setSoundEnabled(enabled: Boolean) = edit { it[SOUND_ENABLED] = enabled }
    suspend fun setHapticFeedbackEnabled(enabled: Boolean) = edit { it[HAPTIC_FEEDBACK_ENABLED] = enabled }
    suspend fun setDialPadShowLetters(enabled: Boolean) = edit { it[DIAL_PAD_SHOW_LETTERS] = enabled }
    suspend fun setDialPadAutoFormat(enabled: Boolean) = edit { it[DIAL_PAD_AUTO_FORMAT] = enabled }
    suspend fun setDialPadShowT9Suggestions(enabled: Boolean) = edit { it[DIAL_PAD_SHOW_T9_SUGGESTIONS] = enabled }
    suspend fun setDialPadLongPressBackspaceClears(enabled: Boolean) = edit { it[DIAL_PAD_LONG_PRESS_BACKSPACE_CLEARS] = enabled }
    suspend fun setDialPadToggleButtonSizeDp(value: Int) = edit { it[DIAL_PAD_TOGGLE_BUTTON_SIZE_DP] = value.coerceIn(56, 88) }
    suspend fun setDialPadBackspaceLongPressDurationMs(value: Int) = edit { it[DIAL_PAD_BACKSPACE_LONG_PRESS_DURATION_MS] = value.coerceIn(300, 1500) }
    suspend fun setGroupTagSortOrder(value: String) = edit { it[GROUP_TAG_SORT_ORDER] = value.uppercase() }
    suspend fun setDrawerFolderDisplayMode(value: String) = edit { it[DRAWER_FOLDER_DISPLAY_MODE] = value.uppercase() }
    suspend fun setIncomingCallCompactMode(enabled: Boolean) = edit { it[INCOMING_CALL_COMPACT_MODE] = enabled }
    suspend fun setIncomingCallShowNumber(enabled: Boolean) = edit { it[INCOMING_CALL_SHOW_NUMBER] = enabled }
    suspend fun setIncomingCallShowTag(enabled: Boolean) = edit { it[INCOMING_CALL_SHOW_TAG] = enabled }
    suspend fun setIncomingCallShowGroup(enabled: Boolean) = edit { it[INCOMING_CALL_SHOW_GROUP] = enabled }
    suspend fun setIncomingCallPhotoBackgroundEnabled(enabled: Boolean) = edit { it[INCOMING_CALL_PHOTO_BACKGROUND_ENABLED] = enabled }
    suspend fun setIncomingCallWindowTransparency(value: Int) = edit { it[INCOMING_CALL_WINDOW_TRANSPARENCY] = value.coerceIn(20, 100) }
    suspend fun setIncomingCallWindowSize(value: String) = edit { it[INCOMING_CALL_WINDOW_SIZE] = value.uppercase() }
    suspend fun setIncomingCallWindowPosition(value: String) = edit { it[INCOMING_CALL_WINDOW_POSITION] = value.uppercase() }
    suspend fun setIncomingCallAutoDismissSeconds(seconds: Int) = edit { it[INCOMING_CALL_AUTO_DISMISS_SECONDS] = seconds.coerceIn(0, 60) }
    suspend fun setTransferProgressOverlayOpacity(value: Int) = edit { it[TRANSFER_PROGRESS_OVERLAY_OPACITY] = value.coerceIn(40, 100) }
    // SMS
    suspend fun setSmsDeliveryReports(enabled: Boolean) = edit { it[SMS_DELIVERY_REPORTS] = enabled }
    suspend fun setFlashSmsAttempt(enabled: Boolean) = edit { it[FLASH_SMS_ATTEMPT] = enabled }
    suspend fun setPreferredSmsSimHandleId(value: String?) = edit { prefs -> if (value.isNullOrBlank()) prefs.remove(PREFERRED_SMS_SIM_HANDLE_ID_KEY) else prefs[PREFERRED_SMS_SIM_HANDLE_ID_KEY] = value }
    suspend fun setQuickSmsTemplate1(value: String) = edit { it[QUICK_SMS_TEMPLATE_1_KEY] = value.take(160) }
    suspend fun setQuickSmsTemplate2(value: String) = edit { it[QUICK_SMS_TEMPLATE_2_KEY] = value.take(160) }
    suspend fun setQuickSmsTemplate3(value: String) = edit { it[QUICK_SMS_TEMPLATE_3_KEY] = value.take(160) }
    // Mini call view
    suspend fun setMiniCallViewEnabled(enabled: Boolean) = edit { it[MINI_CALL_VIEW_ENABLED_KEY] = enabled }
    suspend fun setMiniCallViewOpacity(value: Int) = edit { it[MINI_CALL_VIEW_OPACITY_KEY] = value.coerceIn(40, 100) }
    suspend fun setMiniCallViewSize(value: String) = edit { it[MINI_CALL_VIEW_SIZE_KEY] = value.uppercase() }
    suspend fun setMiniCallViewPosition(value: String) = edit { it[MINI_CALL_VIEW_POSITION_KEY] = value.uppercase() }
    // Lock screen full-screen call
    suspend fun setLockScreenCallUiEnabled(enabled: Boolean) = edit { it[LOCK_SCREEN_CALL_UI_ENABLED_KEY] = enabled }
    // Folder panel
    suspend fun setFolderPanelPlacement(value: String) = edit { it[FOLDER_PANEL_PLACEMENT_KEY] = value.uppercase() }
    suspend fun setFolderPanelMode(value: String) = edit { it[FOLDER_PANEL_MODE_KEY] = value.uppercase() }
    suspend fun setCallButtonMode(value: String) {
        val normalized = value.uppercase()
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CALL_BUTTON_MODE, normalized)
            .apply()
        edit { it[CALL_BUTTON_MODE] = normalized }
    }
    suspend fun setAppFontProfile(value: String) = edit { it[APP_FONT_PROFILE] = value.uppercase() }
    suspend fun setCustomFontPath(value: String?) = edit { prefs -> if (value.isNullOrBlank()) prefs.remove(CUSTOM_FONT_PATH) else prefs[CUSTOM_FONT_PATH] = value }
    suspend fun setCustomFontDisplayName(value: String?) = edit { prefs -> if (value.isNullOrBlank()) prefs.remove(CUSTOM_FONT_DISPLAY_NAME) else prefs[CUSTOM_FONT_DISPLAY_NAME] = value }

    suspend fun setCallSimMode(value: String) {
        val normalized = value.uppercase()
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CALL_SIM_MODE, normalized)
            .apply()
        edit { it[CALL_SIM_MODE] = normalized }
    }
    suspend fun setDefaultCallSimHandleId(value: String?) {
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            .edit().apply {
                if (value.isNullOrBlank()) remove(KEY_DEFAULT_CALL_SIM_HANDLE_ID) else putString(KEY_DEFAULT_CALL_SIM_HANDLE_ID, value)
            }.apply()
        edit { prefs -> if (value.isNullOrBlank()) prefs.remove(DEFAULT_CALL_SIM_HANDLE_ID) else prefs[DEFAULT_CALL_SIM_HANDLE_ID] = value }
    }
    suspend fun setSimChooserOpacity(value: Int) {
        val normalized = value.coerceIn(45, 100)
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SIM_CHOOSER_OPACITY, normalized).apply()
        edit { it[SIM_CHOOSER_OPACITY] = normalized }
    }
    suspend fun setSimChooserSizePercent(value: Int) {
        val normalized = value.coerceIn(72, 100)
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SIM_CHOOSER_SIZE_PERCENT, normalized).apply()
        edit { it[SIM_CHOOSER_SIZE_PERCENT] = normalized }
    }

    suspend fun setAppLanguage(value: String) {
        val normalized = value.uppercase()
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, normalized)
            .apply()
        edit { it[APP_LANGUAGE] = normalized }
    }


    suspend fun exportRawSettingsSnapshot(): JSONObject {
        val prefs = context.appLockDataStore.data.first()
        val dataStore = JSONObject().apply {
            putStringIfPresent(PIN_HASH, prefs[PIN_HASH])
            putStringIfPresent(PIN_SALT, prefs[PIN_SALT])
            putBooleanIfPresent(BIOMETRIC_ENABLED, prefs[BIOMETRIC_ENABLED])
            putBooleanIfPresent(ALLOW_DEVICE_CREDENTIAL, prefs[ALLOW_DEVICE_CREDENTIAL])
            putBooleanIfPresent(LOCK_ON_APP_RESUME, prefs[LOCK_ON_APP_RESUME])
            putIntIfPresent(LOCK_AFTER_INACTIVITY_SECONDS, prefs[LOCK_AFTER_INACTIVITY_SECONDS])
            putIntIfPresent(TRASH_RETENTION_DAYS, prefs[TRASH_RETENTION_DAYS])
            putStringIfPresent(THEME_MODE, prefs[THEME_MODE])
            putStringIfPresent(THEME_PRESET, prefs[THEME_PRESET])
            putStringIfPresent(ACCENT_PALETTE, prefs[ACCENT_PALETTE])
            putStringIfPresent(CORNER_STYLE, prefs[CORNER_STYLE])
            putStringIfPresent(EXPORT_PATH, prefs[EXPORT_PATH])
            putStringIfPresent(BACKUP_PATH, prefs[BACKUP_PATH])
            putStringIfPresent(EXPORT_FOLDER_URI, prefs[EXPORT_FOLDER_URI])
            putStringIfPresent(EXPORT_FOLDER_NAME, prefs[EXPORT_FOLDER_NAME])
            putStringIfPresent(BACKUP_FOLDER_URI, prefs[BACKUP_FOLDER_URI])
            putStringIfPresent(BACKUP_FOLDER_NAME, prefs[BACKUP_FOLDER_NAME])
            putStringIfPresent(RECORDINGS_PATH, prefs[RECORDINGS_PATH])
            putStringIfPresent(RECORDINGS_FOLDER_URI, prefs[RECORDINGS_FOLDER_URI])
            putStringIfPresent(RECORDINGS_FOLDER_NAME, prefs[RECORDINGS_FOLDER_NAME])
            putBooleanIfPresent(SHOW_APP_NAME_ON_LOCK_SCREEN, prefs[SHOW_APP_NAME_ON_LOCK_SCREEN])
            putBooleanIfPresent(SHOW_APP_ICON_ON_LOCK_SCREEN, prefs[SHOW_APP_ICON_ON_LOCK_SCREEN])
            putBooleanIfPresent(SHOW_TIME_ON_LOCK_SCREEN, prefs[SHOW_TIME_ON_LOCK_SCREEN])
            putBooleanIfPresent(USE_ILLUSTRATION_ON_LOCK_SCREEN, prefs[USE_ILLUSTRATION_ON_LOCK_SCREEN])
            putStringIfPresent(LOCK_SCREEN_MESSAGE, prefs[LOCK_SCREEN_MESSAGE])
            putStringIfPresent(LOCK_SCREEN_STYLE, prefs[LOCK_SCREEN_STYLE])
            putStringIfPresent(FINGERPRINT_ICON_STYLE, prefs[FINGERPRINT_ICON_STYLE])
            putStringIfPresent(LOCK_SCREEN_BACKGROUND_URI, prefs[LOCK_SCREEN_BACKGROUND_URI])
            putStringIfPresent(DEFAULT_CONTACT_SORT_ORDER, prefs[DEFAULT_CONTACT_SORT_ORDER])
            putStringIfPresent(CONTACT_LIST_DENSITY, prefs[CONTACT_LIST_DENSITY])
            putBooleanIfPresent(SHOW_CONTACT_PHOTOS_IN_LIST, prefs[SHOW_CONTACT_PHOTOS_IN_LIST])
            putStringIfPresent(DEFAULT_START_TAB, prefs[DEFAULT_START_TAB])
            putBooleanIfPresent(CONFIRM_BEFORE_DELETE, prefs[CONFIRM_BEFORE_DELETE])
            putBooleanIfPresent(CONFIRM_BEFORE_BLOCK_UNBLOCK, prefs[CONFIRM_BEFORE_BLOCK_UNBLOCK])
            putBooleanIfPresent(SHOW_RECENT_CALLS_PREVIEW, prefs[SHOW_RECENT_CALLS_PREVIEW])
            putBooleanIfPresent(AUTO_COLLAPSE_CALL_GROUPS, prefs[AUTO_COLLAPSE_CALL_GROUPS])
            putBooleanIfPresent(SHOW_BLOCKED_CONTACTS_IN_SEARCH, prefs[SHOW_BLOCKED_CONTACTS_IN_SEARCH])
            putBooleanIfPresent(INCLUDE_TIMESTAMP_IN_EXPORT_FILENAME, prefs[INCLUDE_TIMESTAMP_IN_EXPORT_FILENAME])
            putBooleanIfPresent(HIDE_EMPTY_FOLDERS_AND_TAGS, prefs[HIDE_EMPTY_FOLDERS_AND_TAGS])
            putBooleanIfPresent(OPEN_CONTACT_DIRECTLY_ON_TAP, prefs[OPEN_CONTACT_DIRECTLY_ON_TAP])
            putBooleanIfPresent(SHOW_FAVORITES_FIRST, prefs[SHOW_FAVORITES_FIRST])
            putBooleanIfPresent(ENABLE_INCOMING_CALLER_POPUP, prefs[ENABLE_INCOMING_CALLER_POPUP])
            putBooleanIfPresent(ENABLE_MISSED_CALL_NOTIFICATION, prefs[ENABLE_MISSED_CALL_NOTIFICATION])
            putBooleanIfPresent(SHOW_PHOTO_IN_NOTIFICATIONS, prefs[SHOW_PHOTO_IN_NOTIFICATIONS])
            putBooleanIfPresent(SHOW_FOLDER_TAGS_IN_NOTIFICATIONS, prefs[SHOW_FOLDER_TAGS_IN_NOTIFICATIONS])
            putStringIfPresent(LOCK_SCREEN_NOTIFICATION_VISIBILITY, prefs[LOCK_SCREEN_NOTIFICATION_VISIBILITY])
            putBooleanIfPresent(HEADS_UP_NOTIFICATIONS, prefs[HEADS_UP_NOTIFICATIONS])
            putStringIfPresent(OVERLAY_POPUP_MODE, prefs[OVERLAY_POPUP_MODE])
            putBooleanIfPresent(VIBRATION_ENABLED, prefs[VIBRATION_ENABLED])
            putBooleanIfPresent(SOUND_ENABLED, prefs[SOUND_ENABLED])
            putBooleanIfPresent(HAPTIC_FEEDBACK_ENABLED, prefs[HAPTIC_FEEDBACK_ENABLED])
            putBooleanIfPresent(DIAL_PAD_SHOW_LETTERS, prefs[DIAL_PAD_SHOW_LETTERS])
            putBooleanIfPresent(DIAL_PAD_AUTO_FORMAT, prefs[DIAL_PAD_AUTO_FORMAT])
            putBooleanIfPresent(DIAL_PAD_SHOW_T9_SUGGESTIONS, prefs[DIAL_PAD_SHOW_T9_SUGGESTIONS])
            putBooleanIfPresent(DIAL_PAD_LONG_PRESS_BACKSPACE_CLEARS, prefs[DIAL_PAD_LONG_PRESS_BACKSPACE_CLEARS])
            putIntIfPresent(DIAL_PAD_TOGGLE_BUTTON_SIZE_DP, prefs[DIAL_PAD_TOGGLE_BUTTON_SIZE_DP])
            putIntIfPresent(DIAL_PAD_BACKSPACE_LONG_PRESS_DURATION_MS, prefs[DIAL_PAD_BACKSPACE_LONG_PRESS_DURATION_MS])
            putStringIfPresent(GROUP_TAG_SORT_ORDER, prefs[GROUP_TAG_SORT_ORDER])
            putStringIfPresent(DRAWER_FOLDER_DISPLAY_MODE, prefs[DRAWER_FOLDER_DISPLAY_MODE])
            putBooleanIfPresent(INCOMING_CALL_COMPACT_MODE, prefs[INCOMING_CALL_COMPACT_MODE])
            putBooleanIfPresent(INCOMING_CALL_SHOW_NUMBER, prefs[INCOMING_CALL_SHOW_NUMBER])
            putBooleanIfPresent(INCOMING_CALL_SHOW_TAG, prefs[INCOMING_CALL_SHOW_TAG])
            putBooleanIfPresent(INCOMING_CALL_SHOW_GROUP, prefs[INCOMING_CALL_SHOW_GROUP])
            putBooleanIfPresent(INCOMING_CALL_PHOTO_BACKGROUND_ENABLED, prefs[INCOMING_CALL_PHOTO_BACKGROUND_ENABLED])
            putIntIfPresent(INCOMING_CALL_WINDOW_TRANSPARENCY, prefs[INCOMING_CALL_WINDOW_TRANSPARENCY])
            putStringIfPresent(INCOMING_CALL_WINDOW_SIZE, prefs[INCOMING_CALL_WINDOW_SIZE])
            putStringIfPresent(INCOMING_CALL_WINDOW_POSITION, prefs[INCOMING_CALL_WINDOW_POSITION])
            putIntIfPresent(INCOMING_CALL_AUTO_DISMISS_SECONDS, prefs[INCOMING_CALL_AUTO_DISMISS_SECONDS])
            putIntIfPresent(TRANSFER_PROGRESS_OVERLAY_OPACITY, prefs[TRANSFER_PROGRESS_OVERLAY_OPACITY])
            putStringIfPresent(APP_ICON_ALIAS, prefs[APP_ICON_ALIAS])
            putStringIfPresent(BACKGROUND_CATEGORY, prefs[BACKGROUND_CATEGORY])
            putIntIfPresent(CALL_CARD_CORNER_RADIUS, prefs[CALL_CARD_CORNER_RADIUS])
            putStringIfPresent(APP_ICON_PREVIEW_URI, prefs[APP_ICON_PREVIEW_URI])
            putStringIfPresent(CALL_BUTTON_MODE, prefs[CALL_BUTTON_MODE])
            putStringIfPresent(CALL_SIM_MODE, prefs[CALL_SIM_MODE])
            putStringIfPresent(DEFAULT_CALL_SIM_HANDLE_ID, prefs[DEFAULT_CALL_SIM_HANDLE_ID])
            putIntIfPresent(SIM_CHOOSER_OPACITY, prefs[SIM_CHOOSER_OPACITY])
            putIntIfPresent(SIM_CHOOSER_SIZE_PERCENT, prefs[SIM_CHOOSER_SIZE_PERCENT])
            putStringIfPresent(APP_FONT_PROFILE, prefs[APP_FONT_PROFILE])
            putStringIfPresent(CUSTOM_FONT_PATH, prefs[CUSTOM_FONT_PATH])
            putStringIfPresent(CUSTOM_FONT_DISPLAY_NAME, prefs[CUSTOM_FONT_DISPLAY_NAME])
            putStringIfPresent(APP_LANGUAGE, prefs[APP_LANGUAGE])
            // SMS
            putBooleanIfPresent(SMS_DELIVERY_REPORTS, prefs[SMS_DELIVERY_REPORTS])
            putBooleanIfPresent(FLASH_SMS_ATTEMPT, prefs[FLASH_SMS_ATTEMPT])
            putStringIfPresent(PREFERRED_SMS_SIM_HANDLE_ID_KEY, prefs[PREFERRED_SMS_SIM_HANDLE_ID_KEY])
            putStringIfPresent(QUICK_SMS_TEMPLATE_1_KEY, prefs[QUICK_SMS_TEMPLATE_1_KEY])
            putStringIfPresent(QUICK_SMS_TEMPLATE_2_KEY, prefs[QUICK_SMS_TEMPLATE_2_KEY])
            putStringIfPresent(QUICK_SMS_TEMPLATE_3_KEY, prefs[QUICK_SMS_TEMPLATE_3_KEY])
            // Mini call view
            putBooleanIfPresent(MINI_CALL_VIEW_ENABLED_KEY, prefs[MINI_CALL_VIEW_ENABLED_KEY])
            putIntIfPresent(MINI_CALL_VIEW_OPACITY_KEY, prefs[MINI_CALL_VIEW_OPACITY_KEY])
            putStringIfPresent(MINI_CALL_VIEW_SIZE_KEY, prefs[MINI_CALL_VIEW_SIZE_KEY])
            putStringIfPresent(MINI_CALL_VIEW_POSITION_KEY, prefs[MINI_CALL_VIEW_POSITION_KEY])
            // Lock screen call
            putBooleanIfPresent(LOCK_SCREEN_CALL_UI_ENABLED_KEY, prefs[LOCK_SCREEN_CALL_UI_ENABLED_KEY])
            // Folder panel
            putStringIfPresent(FOLDER_PANEL_PLACEMENT_KEY, prefs[FOLDER_PANEL_PLACEMENT_KEY])
            putStringIfPresent(FOLDER_PANEL_MODE_KEY, prefs[FOLDER_PANEL_MODE_KEY])
        }
        val bootstrap = JSONObject().apply {
            context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE).all.forEach { (key, value) -> putAny(key, value) }
        }
        val startup = JSONObject().apply {
            context.getSharedPreferences("opencontacts_startup_permissions", Context.MODE_PRIVATE).all.forEach { (key, value) -> putAny(key, value) }
        }
        return JSONObject().apply {
            put("dataStore", dataStore)
            put("bootstrapPrefs", bootstrap)
            put("startupPermissions", startup)
        }
    }

    suspend fun importRawSettingsSnapshot(snapshot: JSONObject?) {
        if (snapshot == null) return
        val dataStore = snapshot.optJSONObject("dataStore")
        context.appLockDataStore.edit { prefs ->
            clearKnownKeys(prefs)
            dataStore?.let { raw ->
                applyString(raw, PIN_HASH, prefs)
                applyString(raw, PIN_SALT, prefs)
                applyBoolean(raw, BIOMETRIC_ENABLED, prefs)
                applyBoolean(raw, ALLOW_DEVICE_CREDENTIAL, prefs)
                applyBoolean(raw, LOCK_ON_APP_RESUME, prefs)
                applyInt(raw, LOCK_AFTER_INACTIVITY_SECONDS, prefs)
                applyInt(raw, TRASH_RETENTION_DAYS, prefs)
                applyString(raw, THEME_MODE, prefs)
                applyString(raw, THEME_PRESET, prefs)
                applyString(raw, ACCENT_PALETTE, prefs)
                applyString(raw, CORNER_STYLE, prefs)
                applyString(raw, EXPORT_PATH, prefs)
                applyString(raw, BACKUP_PATH, prefs)
                applyString(raw, EXPORT_FOLDER_URI, prefs)
                applyString(raw, EXPORT_FOLDER_NAME, prefs)
                applyString(raw, BACKUP_FOLDER_URI, prefs)
                applyString(raw, BACKUP_FOLDER_NAME, prefs)
                applyString(raw, RECORDINGS_PATH, prefs)
                applyString(raw, RECORDINGS_FOLDER_URI, prefs)
                applyString(raw, RECORDINGS_FOLDER_NAME, prefs)
                applyBoolean(raw, SHOW_APP_NAME_ON_LOCK_SCREEN, prefs)
                applyBoolean(raw, SHOW_APP_ICON_ON_LOCK_SCREEN, prefs)
                applyBoolean(raw, SHOW_TIME_ON_LOCK_SCREEN, prefs)
                applyBoolean(raw, USE_ILLUSTRATION_ON_LOCK_SCREEN, prefs)
                applyString(raw, LOCK_SCREEN_MESSAGE, prefs)
                applyString(raw, LOCK_SCREEN_STYLE, prefs)
                applyString(raw, FINGERPRINT_ICON_STYLE, prefs)
                applyString(raw, LOCK_SCREEN_BACKGROUND_URI, prefs)
                applyString(raw, DEFAULT_CONTACT_SORT_ORDER, prefs)
                applyString(raw, CONTACT_LIST_DENSITY, prefs)
                applyBoolean(raw, SHOW_CONTACT_PHOTOS_IN_LIST, prefs)
                applyString(raw, DEFAULT_START_TAB, prefs)
                applyBoolean(raw, CONFIRM_BEFORE_DELETE, prefs)
                applyBoolean(raw, CONFIRM_BEFORE_BLOCK_UNBLOCK, prefs)
                applyBoolean(raw, SHOW_RECENT_CALLS_PREVIEW, prefs)
                applyBoolean(raw, AUTO_COLLAPSE_CALL_GROUPS, prefs)
                applyBoolean(raw, SHOW_BLOCKED_CONTACTS_IN_SEARCH, prefs)
                applyBoolean(raw, INCLUDE_TIMESTAMP_IN_EXPORT_FILENAME, prefs)
                applyBoolean(raw, HIDE_EMPTY_FOLDERS_AND_TAGS, prefs)
                applyBoolean(raw, OPEN_CONTACT_DIRECTLY_ON_TAP, prefs)
                applyBoolean(raw, SHOW_FAVORITES_FIRST, prefs)
                applyBoolean(raw, ENABLE_INCOMING_CALLER_POPUP, prefs)
                applyBoolean(raw, ENABLE_MISSED_CALL_NOTIFICATION, prefs)
                applyBoolean(raw, SHOW_PHOTO_IN_NOTIFICATIONS, prefs)
                applyBoolean(raw, SHOW_FOLDER_TAGS_IN_NOTIFICATIONS, prefs)
                applyString(raw, LOCK_SCREEN_NOTIFICATION_VISIBILITY, prefs)
                applyBoolean(raw, HEADS_UP_NOTIFICATIONS, prefs)
                applyString(raw, OVERLAY_POPUP_MODE, prefs)
                applyBoolean(raw, VIBRATION_ENABLED, prefs)
                applyBoolean(raw, SOUND_ENABLED, prefs)
                applyBoolean(raw, HAPTIC_FEEDBACK_ENABLED, prefs)
                applyBoolean(raw, DIAL_PAD_SHOW_LETTERS, prefs)
                applyBoolean(raw, DIAL_PAD_AUTO_FORMAT, prefs)
                applyBoolean(raw, DIAL_PAD_SHOW_T9_SUGGESTIONS, prefs)
                applyBoolean(raw, DIAL_PAD_LONG_PRESS_BACKSPACE_CLEARS, prefs)
                applyInt(raw, DIAL_PAD_TOGGLE_BUTTON_SIZE_DP, prefs)
                applyInt(raw, DIAL_PAD_BACKSPACE_LONG_PRESS_DURATION_MS, prefs)
                applyString(raw, GROUP_TAG_SORT_ORDER, prefs)
                applyString(raw, DRAWER_FOLDER_DISPLAY_MODE, prefs)
                applyBoolean(raw, INCOMING_CALL_COMPACT_MODE, prefs)
                applyBoolean(raw, INCOMING_CALL_SHOW_NUMBER, prefs)
                applyBoolean(raw, INCOMING_CALL_SHOW_TAG, prefs)
                applyBoolean(raw, INCOMING_CALL_SHOW_GROUP, prefs)
                applyBoolean(raw, INCOMING_CALL_PHOTO_BACKGROUND_ENABLED, prefs)
                applyInt(raw, INCOMING_CALL_WINDOW_TRANSPARENCY, prefs)
                applyString(raw, INCOMING_CALL_WINDOW_SIZE, prefs)
                applyString(raw, INCOMING_CALL_WINDOW_POSITION, prefs)
                applyInt(raw, INCOMING_CALL_AUTO_DISMISS_SECONDS, prefs)
                applyInt(raw, TRANSFER_PROGRESS_OVERLAY_OPACITY, prefs)
                applyString(raw, APP_ICON_ALIAS, prefs)
                applyString(raw, BACKGROUND_CATEGORY, prefs)
                applyInt(raw, CALL_CARD_CORNER_RADIUS, prefs)
                applyString(raw, APP_ICON_PREVIEW_URI, prefs)
                applyString(raw, CALL_BUTTON_MODE, prefs)
                applyString(raw, CALL_SIM_MODE, prefs)
                applyString(raw, DEFAULT_CALL_SIM_HANDLE_ID, prefs)
                applyInt(raw, SIM_CHOOSER_OPACITY, prefs)
                applyInt(raw, SIM_CHOOSER_SIZE_PERCENT, prefs)
                applyString(raw, APP_FONT_PROFILE, prefs)
                applyString(raw, CUSTOM_FONT_PATH, prefs)
                applyString(raw, CUSTOM_FONT_DISPLAY_NAME, prefs)
                applyString(raw, APP_LANGUAGE, prefs)
                // SMS
                applyBoolean(raw, SMS_DELIVERY_REPORTS, prefs)
                applyBoolean(raw, FLASH_SMS_ATTEMPT, prefs)
                applyString(raw, PREFERRED_SMS_SIM_HANDLE_ID_KEY, prefs)
                applyString(raw, QUICK_SMS_TEMPLATE_1_KEY, prefs)
                applyString(raw, QUICK_SMS_TEMPLATE_2_KEY, prefs)
                applyString(raw, QUICK_SMS_TEMPLATE_3_KEY, prefs)
                // Mini call view
                applyBoolean(raw, MINI_CALL_VIEW_ENABLED_KEY, prefs)
                applyInt(raw, MINI_CALL_VIEW_OPACITY_KEY, prefs)
                applyString(raw, MINI_CALL_VIEW_SIZE_KEY, prefs)
                applyString(raw, MINI_CALL_VIEW_POSITION_KEY, prefs)
                // Lock screen call
                applyBoolean(raw, LOCK_SCREEN_CALL_UI_ENABLED_KEY, prefs)
                // Folder panel
                applyString(raw, FOLDER_PANEL_PLACEMENT_KEY, prefs)
                applyString(raw, FOLDER_PANEL_MODE_KEY, prefs)
            }
        }
        restoreSharedPreferences(context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE), snapshot.optJSONObject("bootstrapPrefs"))
        restoreSharedPreferences(context.getSharedPreferences("opencontacts_startup_permissions", Context.MODE_PRIVATE), snapshot.optJSONObject("startupPermissions"))
    }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.appLockDataStore.edit { prefs -> block(prefs) }
    }

    private fun mapSettings(prefs: Preferences): AppLockSettings {
        return AppLockSettings(
            hasPin = prefs[PIN_HASH] != null,
            biometricEnabled = prefs[BIOMETRIC_ENABLED] ?: false,
            allowDeviceCredential = prefs[ALLOW_DEVICE_CREDENTIAL] ?: true,
            lockOnAppResume = prefs[LOCK_ON_APP_RESUME] ?: true,
            lockAfterInactivitySeconds = prefs[LOCK_AFTER_INACTIVITY_SECONDS] ?: SettingsDefaults.LOCK_AFTER_INACTIVITY_SECONDS,
            trashRetentionDays = prefs[TRASH_RETENTION_DAYS] ?: SettingsDefaults.TRASH_RETENTION_DAYS,
            themeMode = prefs[THEME_MODE] ?: SettingsDefaults.THEME_MODE,
            themePreset = prefs[THEME_PRESET] ?: SettingsDefaults.THEME_PRESET,
            accentPalette = prefs[ACCENT_PALETTE] ?: SettingsDefaults.ACCENT_PALETTE,
            cornerStyle = prefs[CORNER_STYLE] ?: SettingsDefaults.CORNER_STYLE,
            exportPath = prefs[EXPORT_PATH] ?: SettingsDefaults.DEFAULT_EXPORT_PATH,
            backupPath = prefs[BACKUP_PATH] ?: SettingsDefaults.DEFAULT_BACKUP_PATH,
            exportFolderUri = prefs[EXPORT_FOLDER_URI],
            exportFolderName = prefs[EXPORT_FOLDER_NAME],
            backupFolderUri = prefs[BACKUP_FOLDER_URI],
            backupFolderName = prefs[BACKUP_FOLDER_NAME],
            recordingsPath = prefs[RECORDINGS_PATH] ?: SettingsDefaults.DEFAULT_RECORDINGS_PATH,
            recordingsFolderUri = prefs[RECORDINGS_FOLDER_URI],
            recordingsFolderName = prefs[RECORDINGS_FOLDER_NAME],
            showAppNameOnLockScreen = prefs[SHOW_APP_NAME_ON_LOCK_SCREEN] ?: true,
            showAppIconOnLockScreen = prefs[SHOW_APP_ICON_ON_LOCK_SCREEN] ?: true,
            showTimeOnLockScreen = prefs[SHOW_TIME_ON_LOCK_SCREEN] ?: true,
            useIllustrationOnLockScreen = prefs[USE_ILLUSTRATION_ON_LOCK_SCREEN] ?: true,
            lockScreenMessage = prefs[LOCK_SCREEN_MESSAGE] ?: "Authenticate to access your private contacts workspace.",
            lockScreenStyle = prefs[LOCK_SCREEN_STYLE] ?: SettingsDefaults.LOCK_SCREEN_STYLE,
            fingerprintIconStyle = prefs[FINGERPRINT_ICON_STYLE] ?: SettingsDefaults.FINGERPRINT_ICON_STYLE,
            lockScreenBackgroundUri = prefs[LOCK_SCREEN_BACKGROUND_URI],
            defaultContactSortOrder = prefs[DEFAULT_CONTACT_SORT_ORDER] ?: SettingsDefaults.SORT_ORDER,
            contactListDensity = prefs[CONTACT_LIST_DENSITY] ?: SettingsDefaults.LIST_DENSITY,
            showContactPhotosInList = prefs[SHOW_CONTACT_PHOTOS_IN_LIST] ?: true,
            defaultStartTab = prefs[DEFAULT_START_TAB] ?: SettingsDefaults.DEFAULT_START_TAB,
            confirmBeforeDelete = prefs[CONFIRM_BEFORE_DELETE] ?: true,
            confirmBeforeBlockUnblock = prefs[CONFIRM_BEFORE_BLOCK_UNBLOCK] ?: true,
            showRecentCallsPreview = prefs[SHOW_RECENT_CALLS_PREVIEW] ?: true,
            autoCollapseCallGroups = prefs[AUTO_COLLAPSE_CALL_GROUPS] ?: false,
            showBlockedContactsInSearch = prefs[SHOW_BLOCKED_CONTACTS_IN_SEARCH] ?: true,
            includeTimestampInExportFileName = prefs[INCLUDE_TIMESTAMP_IN_EXPORT_FILENAME] ?: true,
            hideEmptyFoldersAndTags = prefs[HIDE_EMPTY_FOLDERS_AND_TAGS] ?: false,
            openContactDirectlyOnTap = prefs[OPEN_CONTACT_DIRECTLY_ON_TAP] ?: true,
            showFavoritesFirst = prefs[SHOW_FAVORITES_FIRST] ?: false,
            enableIncomingCallerPopup = prefs[ENABLE_INCOMING_CALLER_POPUP] ?: true,
            enableMissedCallNotification = prefs[ENABLE_MISSED_CALL_NOTIFICATION] ?: true,
            showPhotoInNotifications = prefs[SHOW_PHOTO_IN_NOTIFICATIONS] ?: true,
            showFolderTagsInNotifications = prefs[SHOW_FOLDER_TAGS_IN_NOTIFICATIONS] ?: true,
            lockScreenNotificationVisibility = prefs[LOCK_SCREEN_NOTIFICATION_VISIBILITY] ?: SettingsDefaults.LOCK_SCREEN_VISIBILITY,
            headsUpNotifications = prefs[HEADS_UP_NOTIFICATIONS] ?: true,
            overlayPopupMode = prefs[OVERLAY_POPUP_MODE] ?: SettingsDefaults.OVERLAY_POPUP_MODE,
            vibrationEnabled = prefs[VIBRATION_ENABLED] ?: true,
            soundEnabled = prefs[SOUND_ENABLED] ?: true,
            hapticFeedbackEnabled = prefs[HAPTIC_FEEDBACK_ENABLED] ?: true,
            dialPadShowLetters = prefs[DIAL_PAD_SHOW_LETTERS] ?: true,
            dialPadAutoFormat = prefs[DIAL_PAD_AUTO_FORMAT] ?: true,
            dialPadShowT9Suggestions = prefs[DIAL_PAD_SHOW_T9_SUGGESTIONS] ?: true,
            dialPadLongPressBackspaceClears = prefs[DIAL_PAD_LONG_PRESS_BACKSPACE_CLEARS] ?: true,
            dialPadToggleButtonSizeDp = prefs[DIAL_PAD_TOGGLE_BUTTON_SIZE_DP] ?: SettingsDefaults.DIAL_PAD_TOGGLE_BUTTON_SIZE_DP,
            dialPadBackspaceLongPressDurationMs = prefs[DIAL_PAD_BACKSPACE_LONG_PRESS_DURATION_MS] ?: SettingsDefaults.DIAL_PAD_BACKSPACE_LONG_PRESS_DURATION_MS,
            groupTagSortOrder = prefs[GROUP_TAG_SORT_ORDER] ?: SettingsDefaults.GROUP_TAG_SORT_ORDER,
            drawerFolderDisplayMode = prefs[DRAWER_FOLDER_DISPLAY_MODE] ?: SettingsDefaults.DRAWER_FOLDER_DISPLAY_MODE,
            incomingCallCompactMode = prefs[INCOMING_CALL_COMPACT_MODE] ?: true,
            incomingCallShowNumber = prefs[INCOMING_CALL_SHOW_NUMBER] ?: true,
            incomingCallShowTag = prefs[INCOMING_CALL_SHOW_TAG] ?: true,
            incomingCallShowGroup = prefs[INCOMING_CALL_SHOW_GROUP] ?: true,
            incomingCallPhotoBackgroundEnabled = prefs[INCOMING_CALL_PHOTO_BACKGROUND_ENABLED] ?: true,
            incomingCallWindowTransparency = prefs[INCOMING_CALL_WINDOW_TRANSPARENCY] ?: 88,
            incomingCallWindowSize = prefs[INCOMING_CALL_WINDOW_SIZE] ?: SettingsDefaults.INCOMING_CALL_WINDOW_SIZE,
            incomingCallWindowPosition = prefs[INCOMING_CALL_WINDOW_POSITION] ?: SettingsDefaults.INCOMING_CALL_WINDOW_POSITION,
            incomingCallAutoDismissSeconds = prefs[INCOMING_CALL_AUTO_DISMISS_SECONDS] ?: 0,
            transferProgressOverlayOpacity = prefs[TRANSFER_PROGRESS_OVERLAY_OPACITY] ?: SettingsDefaults.TRANSFER_PROGRESS_OVERLAY_OPACITY,
            appIconAlias = prefs[APP_ICON_ALIAS] ?: SettingsDefaults.APP_ICON_ALIAS,
            backgroundCategory = prefs[BACKGROUND_CATEGORY] ?: SettingsDefaults.BACKGROUND_CATEGORY,
            callCardCornerRadius = prefs[CALL_CARD_CORNER_RADIUS] ?: SettingsDefaults.CALL_CARD_CORNER_RADIUS,
            appIconPreviewUri = prefs[APP_ICON_PREVIEW_URI],
            callButtonMode = prefs[CALL_BUTTON_MODE]
                ?: context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE).getString(KEY_CALL_BUTTON_MODE, null)
                ?: SettingsDefaults.CALL_BUTTON_MODE,
            callSimMode = prefs[CALL_SIM_MODE]
                ?: context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE).getString(KEY_CALL_SIM_MODE, null)
                ?: SettingsDefaults.CALL_SIM_MODE,
            defaultCallSimHandleId = prefs[DEFAULT_CALL_SIM_HANDLE_ID]
                ?: context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE).getString(KEY_DEFAULT_CALL_SIM_HANDLE_ID, null),
            simChooserOpacity = prefs[SIM_CHOOSER_OPACITY]
                ?: context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE).getInt(KEY_SIM_CHOOSER_OPACITY, SettingsDefaults.SIM_CHOOSER_OPACITY),
            simChooserSizePercent = prefs[SIM_CHOOSER_SIZE_PERCENT]
                ?: context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE).getInt(KEY_SIM_CHOOSER_SIZE_PERCENT, SettingsDefaults.SIM_CHOOSER_SIZE_PERCENT),
            appFontProfile = prefs[APP_FONT_PROFILE] ?: SettingsDefaults.APP_FONT_PROFILE,
            customFontPath = prefs[CUSTOM_FONT_PATH],
            customFontDisplayName = prefs[CUSTOM_FONT_DISPLAY_NAME],
            appLanguage = prefs[APP_LANGUAGE] ?: SettingsDefaults.APP_LANGUAGE,
            // SMS
            smsDeliveryReports = prefs[SMS_DELIVERY_REPORTS] ?: SettingsDefaults.SMS_DELIVERY_REPORTS,
            flashSmsAttempt = prefs[FLASH_SMS_ATTEMPT] ?: SettingsDefaults.FLASH_SMS_ATTEMPT,
            preferredSmsSimHandleId = prefs[PREFERRED_SMS_SIM_HANDLE_ID_KEY],
            quickSmsTemplate1 = prefs[QUICK_SMS_TEMPLATE_1_KEY] ?: SettingsDefaults.QUICK_SMS_TEMPLATE_1,
            quickSmsTemplate2 = prefs[QUICK_SMS_TEMPLATE_2_KEY] ?: SettingsDefaults.QUICK_SMS_TEMPLATE_2,
            quickSmsTemplate3 = prefs[QUICK_SMS_TEMPLATE_3_KEY] ?: SettingsDefaults.QUICK_SMS_TEMPLATE_3,
            // Mini call view
            miniCallViewEnabled = prefs[MINI_CALL_VIEW_ENABLED_KEY] ?: SettingsDefaults.MINI_CALL_VIEW_ENABLED,
            miniCallViewOpacity = prefs[MINI_CALL_VIEW_OPACITY_KEY] ?: SettingsDefaults.MINI_CALL_VIEW_OPACITY,
            miniCallViewSize = prefs[MINI_CALL_VIEW_SIZE_KEY] ?: SettingsDefaults.MINI_CALL_VIEW_SIZE,
            miniCallViewPosition = prefs[MINI_CALL_VIEW_POSITION_KEY] ?: SettingsDefaults.MINI_CALL_VIEW_POSITION,
            // Lock screen call UI
            lockScreenCallUiEnabled = prefs[LOCK_SCREEN_CALL_UI_ENABLED_KEY] ?: SettingsDefaults.LOCK_SCREEN_CALL_UI_ENABLED,
            // Folder panel
            folderPanelPlacement = prefs[FOLDER_PANEL_PLACEMENT_KEY] ?: SettingsDefaults.FOLDER_PANEL_PLACEMENT,
            folderPanelMode = prefs[FOLDER_PANEL_MODE_KEY] ?: SettingsDefaults.FOLDER_PANEL_MODE,
        )
    }



    private fun clearKnownKeys(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        listOf(
            PIN_HASH, PIN_SALT, BIOMETRIC_ENABLED, ALLOW_DEVICE_CREDENTIAL, LOCK_ON_APP_RESUME, LOCK_AFTER_INACTIVITY_SECONDS,
            TRASH_RETENTION_DAYS, THEME_MODE, THEME_PRESET, ACCENT_PALETTE, CORNER_STYLE, EXPORT_PATH, BACKUP_PATH, EXPORT_FOLDER_URI, EXPORT_FOLDER_NAME,
            BACKUP_FOLDER_URI, BACKUP_FOLDER_NAME, RECORDINGS_PATH, RECORDINGS_FOLDER_URI, RECORDINGS_FOLDER_NAME, SHOW_APP_NAME_ON_LOCK_SCREEN,
            SHOW_APP_ICON_ON_LOCK_SCREEN, SHOW_TIME_ON_LOCK_SCREEN, USE_ILLUSTRATION_ON_LOCK_SCREEN, LOCK_SCREEN_MESSAGE, LOCK_SCREEN_STYLE,
            FINGERPRINT_ICON_STYLE, LOCK_SCREEN_BACKGROUND_URI, DEFAULT_CONTACT_SORT_ORDER, CONTACT_LIST_DENSITY, SHOW_CONTACT_PHOTOS_IN_LIST, DEFAULT_START_TAB,
            CONFIRM_BEFORE_DELETE, CONFIRM_BEFORE_BLOCK_UNBLOCK, SHOW_RECENT_CALLS_PREVIEW, AUTO_COLLAPSE_CALL_GROUPS, SHOW_BLOCKED_CONTACTS_IN_SEARCH,
            INCLUDE_TIMESTAMP_IN_EXPORT_FILENAME, HIDE_EMPTY_FOLDERS_AND_TAGS, OPEN_CONTACT_DIRECTLY_ON_TAP, SHOW_FAVORITES_FIRST, ENABLE_INCOMING_CALLER_POPUP,
            ENABLE_MISSED_CALL_NOTIFICATION, SHOW_PHOTO_IN_NOTIFICATIONS, SHOW_FOLDER_TAGS_IN_NOTIFICATIONS, LOCK_SCREEN_NOTIFICATION_VISIBILITY, HEADS_UP_NOTIFICATIONS,
            OVERLAY_POPUP_MODE, VIBRATION_ENABLED, SOUND_ENABLED, HAPTIC_FEEDBACK_ENABLED, DIAL_PAD_SHOW_LETTERS, DIAL_PAD_AUTO_FORMAT,
            DIAL_PAD_SHOW_T9_SUGGESTIONS, DIAL_PAD_LONG_PRESS_BACKSPACE_CLEARS, DIAL_PAD_TOGGLE_BUTTON_SIZE_DP, DIAL_PAD_BACKSPACE_LONG_PRESS_DURATION_MS,
            GROUP_TAG_SORT_ORDER, DRAWER_FOLDER_DISPLAY_MODE, INCOMING_CALL_COMPACT_MODE, INCOMING_CALL_SHOW_NUMBER,
            INCOMING_CALL_SHOW_TAG, INCOMING_CALL_SHOW_GROUP, INCOMING_CALL_PHOTO_BACKGROUND_ENABLED, INCOMING_CALL_WINDOW_TRANSPARENCY, INCOMING_CALL_WINDOW_SIZE,
            INCOMING_CALL_WINDOW_POSITION, INCOMING_CALL_AUTO_DISMISS_SECONDS, TRANSFER_PROGRESS_OVERLAY_OPACITY, APP_ICON_ALIAS, BACKGROUND_CATEGORY, CALL_CARD_CORNER_RADIUS, APP_ICON_PREVIEW_URI, CALL_BUTTON_MODE, CALL_SIM_MODE, DEFAULT_CALL_SIM_HANDLE_ID, SIM_CHOOSER_OPACITY, SIM_CHOOSER_SIZE_PERCENT, APP_FONT_PROFILE, CUSTOM_FONT_PATH, CUSTOM_FONT_DISPLAY_NAME, APP_LANGUAGE,
            SMS_DELIVERY_REPORTS, FLASH_SMS_ATTEMPT, PREFERRED_SMS_SIM_HANDLE_ID_KEY, QUICK_SMS_TEMPLATE_1_KEY, QUICK_SMS_TEMPLATE_2_KEY, QUICK_SMS_TEMPLATE_3_KEY,
            MINI_CALL_VIEW_ENABLED_KEY, MINI_CALL_VIEW_OPACITY_KEY, MINI_CALL_VIEW_SIZE_KEY, MINI_CALL_VIEW_POSITION_KEY,
            LOCK_SCREEN_CALL_UI_ENABLED_KEY, FOLDER_PANEL_PLACEMENT_KEY, FOLDER_PANEL_MODE_KEY,
        ).forEach { prefs.remove(it) }
    }

    private fun applyString(raw: JSONObject, key: androidx.datastore.preferences.core.Preferences.Key<String>, prefs: androidx.datastore.preferences.core.MutablePreferences) {
        if (raw.has(key.name) && !raw.isNull(key.name)) prefs[key] = raw.optString(key.name)
    }

    private fun applyBoolean(raw: JSONObject, key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, prefs: androidx.datastore.preferences.core.MutablePreferences) {
        if (raw.has(key.name) && !raw.isNull(key.name)) prefs[key] = raw.optBoolean(key.name)
    }

    private fun applyInt(raw: JSONObject, key: androidx.datastore.preferences.core.Preferences.Key<Int>, prefs: androidx.datastore.preferences.core.MutablePreferences) {
        if (raw.has(key.name) && !raw.isNull(key.name)) prefs[key] = raw.optInt(key.name)
    }

    private fun hashPin(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, HASH_ITERATIONS, DERIVED_KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val ALLOW_DEVICE_CREDENTIAL = booleanPreferencesKey("allow_device_credential")
        val LOCK_ON_APP_RESUME = booleanPreferencesKey("lock_on_app_resume")
        val LOCK_AFTER_INACTIVITY_SECONDS = intPreferencesKey("lock_after_inactivity_seconds")
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        val CORNER_STYLE = stringPreferencesKey("corner_style")
        val EXPORT_PATH = stringPreferencesKey("export_path")
        val BACKUP_PATH = stringPreferencesKey("backup_path")
        val EXPORT_FOLDER_URI = stringPreferencesKey("export_folder_uri")
        val EXPORT_FOLDER_NAME = stringPreferencesKey("export_folder_name")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val BACKUP_FOLDER_NAME = stringPreferencesKey("backup_folder_name")
        val RECORDINGS_PATH = stringPreferencesKey("recordings_path")
        val RECORDINGS_FOLDER_URI = stringPreferencesKey("recordings_folder_uri")
        val RECORDINGS_FOLDER_NAME = stringPreferencesKey("recordings_folder_name")
        val SHOW_APP_NAME_ON_LOCK_SCREEN = booleanPreferencesKey("show_app_name_on_lock_screen")
        val SHOW_APP_ICON_ON_LOCK_SCREEN = booleanPreferencesKey("show_app_icon_on_lock_screen")
        val SHOW_TIME_ON_LOCK_SCREEN = booleanPreferencesKey("show_time_on_lock_screen")
        val USE_ILLUSTRATION_ON_LOCK_SCREEN = booleanPreferencesKey("use_illustration_on_lock_screen")
        val LOCK_SCREEN_MESSAGE = stringPreferencesKey("lock_screen_message")
        val LOCK_SCREEN_STYLE = stringPreferencesKey("lock_screen_style")
        val FINGERPRINT_ICON_STYLE = stringPreferencesKey("fingerprint_icon_style")
        val LOCK_SCREEN_BACKGROUND_URI = stringPreferencesKey("lock_screen_background_uri")
        val DEFAULT_CONTACT_SORT_ORDER = stringPreferencesKey("default_contact_sort_order")
        val CONTACT_LIST_DENSITY = stringPreferencesKey("contact_list_density")
        val SHOW_CONTACT_PHOTOS_IN_LIST = booleanPreferencesKey("show_contact_photos_in_list")
        val DEFAULT_START_TAB = stringPreferencesKey("default_start_tab")
        val CONFIRM_BEFORE_DELETE = booleanPreferencesKey("confirm_before_delete")
        val CONFIRM_BEFORE_BLOCK_UNBLOCK = booleanPreferencesKey("confirm_before_block_unblock")
        val SHOW_RECENT_CALLS_PREVIEW = booleanPreferencesKey("show_recent_calls_preview")
        val AUTO_COLLAPSE_CALL_GROUPS = booleanPreferencesKey("auto_collapse_call_groups")
        val SHOW_BLOCKED_CONTACTS_IN_SEARCH = booleanPreferencesKey("show_blocked_contacts_in_search")
        val INCLUDE_TIMESTAMP_IN_EXPORT_FILENAME = booleanPreferencesKey("include_timestamp_in_export_filename")
        val HIDE_EMPTY_FOLDERS_AND_TAGS = booleanPreferencesKey("hide_empty_folders_and_tags")
        val OPEN_CONTACT_DIRECTLY_ON_TAP = booleanPreferencesKey("open_contact_directly_on_tap")
        val SHOW_FAVORITES_FIRST = booleanPreferencesKey("show_favorites_first")
        val ENABLE_INCOMING_CALLER_POPUP = booleanPreferencesKey("enable_incoming_caller_popup")
        val ENABLE_MISSED_CALL_NOTIFICATION = booleanPreferencesKey("enable_missed_call_notification")
        val SHOW_PHOTO_IN_NOTIFICATIONS = booleanPreferencesKey("show_photo_in_notifications")
        val SHOW_FOLDER_TAGS_IN_NOTIFICATIONS = booleanPreferencesKey("show_folder_tags_in_notifications")
        val LOCK_SCREEN_NOTIFICATION_VISIBILITY = stringPreferencesKey("lock_screen_notification_visibility")
        val HEADS_UP_NOTIFICATIONS = booleanPreferencesKey("heads_up_notifications")
        val OVERLAY_POPUP_MODE = stringPreferencesKey("overlay_popup_mode")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val DIAL_PAD_SHOW_LETTERS = booleanPreferencesKey("dial_pad_show_letters")
        val DIAL_PAD_AUTO_FORMAT = booleanPreferencesKey("dial_pad_auto_format")
        val DIAL_PAD_SHOW_T9_SUGGESTIONS = booleanPreferencesKey("dial_pad_show_t9_suggestions")
        val DIAL_PAD_LONG_PRESS_BACKSPACE_CLEARS = booleanPreferencesKey("dial_pad_long_press_backspace_clears")
        val DIAL_PAD_TOGGLE_BUTTON_SIZE_DP = intPreferencesKey("dial_pad_toggle_button_size_dp")
        val DIAL_PAD_BACKSPACE_LONG_PRESS_DURATION_MS = intPreferencesKey("dial_pad_backspace_long_press_duration_ms")
        val GROUP_TAG_SORT_ORDER = stringPreferencesKey("group_tag_sort_order")
        val DRAWER_FOLDER_DISPLAY_MODE = stringPreferencesKey("drawer_folder_display_mode")
        val INCOMING_CALL_COMPACT_MODE = booleanPreferencesKey("incoming_call_compact_mode")
        val INCOMING_CALL_SHOW_NUMBER = booleanPreferencesKey("incoming_call_show_number")
        val INCOMING_CALL_SHOW_TAG = booleanPreferencesKey("incoming_call_show_tag")
        val INCOMING_CALL_SHOW_GROUP = booleanPreferencesKey("incoming_call_show_group")
        val INCOMING_CALL_PHOTO_BACKGROUND_ENABLED = booleanPreferencesKey("incoming_call_photo_background_enabled")
        val INCOMING_CALL_WINDOW_TRANSPARENCY = intPreferencesKey("incoming_call_window_transparency")
        val INCOMING_CALL_WINDOW_SIZE = stringPreferencesKey("incoming_call_window_size")
        val INCOMING_CALL_WINDOW_POSITION = stringPreferencesKey("incoming_call_window_position")
        val INCOMING_CALL_AUTO_DISMISS_SECONDS = intPreferencesKey("incoming_call_auto_dismiss_seconds")
        val TRANSFER_PROGRESS_OVERLAY_OPACITY = intPreferencesKey("transfer_progress_overlay_opacity")
        val APP_ICON_ALIAS = stringPreferencesKey("app_icon_alias")
        val BACKGROUND_CATEGORY = stringPreferencesKey("background_category")
        val CALL_CARD_CORNER_RADIUS = intPreferencesKey("call_card_corner_radius")
        val APP_ICON_PREVIEW_URI = stringPreferencesKey("app_icon_preview_uri")
        val CALL_BUTTON_MODE = stringPreferencesKey("call_button_mode")
        val CALL_SIM_MODE = stringPreferencesKey("call_sim_mode")
        val DEFAULT_CALL_SIM_HANDLE_ID = stringPreferencesKey("default_call_sim_handle_id")
        val SIM_CHOOSER_OPACITY = intPreferencesKey("sim_chooser_opacity")
        val SIM_CHOOSER_SIZE_PERCENT = intPreferencesKey("sim_chooser_size_percent")
        val APP_FONT_PROFILE = stringPreferencesKey("app_font_profile")
        val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        val CUSTOM_FONT_DISPLAY_NAME = stringPreferencesKey("custom_font_display_name")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        // SMS keys
        val SMS_DELIVERY_REPORTS = booleanPreferencesKey("sms_delivery_reports")
        val FLASH_SMS_ATTEMPT = booleanPreferencesKey("flash_sms_attempt")
        val PREFERRED_SMS_SIM_HANDLE_ID_KEY = stringPreferencesKey("preferred_sms_sim_handle_id")
        val QUICK_SMS_TEMPLATE_1_KEY = stringPreferencesKey("quick_sms_template_1")
        val QUICK_SMS_TEMPLATE_2_KEY = stringPreferencesKey("quick_sms_template_2")
        val QUICK_SMS_TEMPLATE_3_KEY = stringPreferencesKey("quick_sms_template_3")
        // Mini call view keys
        val MINI_CALL_VIEW_ENABLED_KEY = booleanPreferencesKey("mini_call_view_enabled")
        val MINI_CALL_VIEW_OPACITY_KEY = intPreferencesKey("mini_call_view_opacity")
        val MINI_CALL_VIEW_SIZE_KEY = stringPreferencesKey("mini_call_view_size")
        val MINI_CALL_VIEW_POSITION_KEY = stringPreferencesKey("mini_call_view_position")
        // Lock screen call UI
        val LOCK_SCREEN_CALL_UI_ENABLED_KEY = booleanPreferencesKey("lock_screen_call_ui_enabled")
        // Folder panel keys
        val FOLDER_PANEL_PLACEMENT_KEY = stringPreferencesKey("folder_panel_placement")
        val FOLDER_PANEL_MODE_KEY = stringPreferencesKey("folder_panel_mode")
        const val BOOTSTRAP_PREFS = "opencontacts_bootstrap"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_CALL_BUTTON_MODE = "call_button_mode"
        const val KEY_CALL_SIM_MODE = "call_sim_mode"
        const val KEY_DEFAULT_CALL_SIM_HANDLE_ID = "default_call_sim_handle_id"
        const val KEY_SIM_CHOOSER_OPACITY = "sim_chooser_opacity"
        const val KEY_SIM_CHOOSER_SIZE_PERCENT = "sim_chooser_size_percent"

        const val SALT_LENGTH = 16
        const val HASH_ITERATIONS = 120_000
        const val DERIVED_KEY_LENGTH_BITS = 256
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}



private fun JSONObject.putAny(key: String, value: Any?) {
    when (value) {
        null -> put(key, JSONObject.NULL)
        is Boolean, is Int, is Long, is Float, is Double, is String -> put(key, value)
        else -> put(key, value.toString())
    }
}

private fun JSONObject.putStringIfPresent(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String?) {
    value?.let { put(key.name, it) }
}

private fun JSONObject.putBooleanIfPresent(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean?) {
    value?.let { put(key.name, it) }
}

private fun JSONObject.putIntIfPresent(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int?) {
    value?.let { put(key.name, it) }
}

private fun restoreSharedPreferences(sharedPreferences: android.content.SharedPreferences, snapshot: JSONObject?) {
    sharedPreferences.edit().clear().apply()
    if (snapshot == null) return
    val editor = sharedPreferences.edit()
    snapshot.keys().forEach { key ->
        when (val value = snapshot.opt(key)) {
            null, JSONObject.NULL -> editor.remove(key)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            else -> editor.putString(key, value.toString())
        }
    }
    editor.apply()
}

data class AppLockSettings(
    val hasPin: Boolean,
    val biometricEnabled: Boolean,
    val allowDeviceCredential: Boolean,
    val lockOnAppResume: Boolean,
    val lockAfterInactivitySeconds: Int,
    val trashRetentionDays: Int,
    val themeMode: String,
    val themePreset: String,
    val accentPalette: String,
    val cornerStyle: String,
    val exportPath: String,
    val backupPath: String,
    val exportFolderUri: String? = null,
    val exportFolderName: String? = null,
    val backupFolderUri: String? = null,
    val backupFolderName: String? = null,
    val recordingsPath: String,
    val recordingsFolderUri: String? = null,
    val recordingsFolderName: String? = null,
    val showAppNameOnLockScreen: Boolean,
    val showAppIconOnLockScreen: Boolean,
    val showTimeOnLockScreen: Boolean,
    val useIllustrationOnLockScreen: Boolean,
    val lockScreenMessage: String,
    val lockScreenStyle: String,
    val fingerprintIconStyle: String,
    val lockScreenBackgroundUri: String? = null,
    val defaultContactSortOrder: String,
    val contactListDensity: String,
    val showContactPhotosInList: Boolean,
    val defaultStartTab: String,
    val confirmBeforeDelete: Boolean,
    val confirmBeforeBlockUnblock: Boolean,
    val showRecentCallsPreview: Boolean,
    val autoCollapseCallGroups: Boolean,
    val showBlockedContactsInSearch: Boolean,
    val includeTimestampInExportFileName: Boolean,
    val hideEmptyFoldersAndTags: Boolean,
    val openContactDirectlyOnTap: Boolean,
    val showFavoritesFirst: Boolean,
    val enableIncomingCallerPopup: Boolean,
    val enableMissedCallNotification: Boolean,
    val showPhotoInNotifications: Boolean,
    val showFolderTagsInNotifications: Boolean,
    val lockScreenNotificationVisibility: String,
    val headsUpNotifications: Boolean,
    val overlayPopupMode: String,
    val vibrationEnabled: Boolean,
    val soundEnabled: Boolean,
    val hapticFeedbackEnabled: Boolean,
    val dialPadShowLetters: Boolean,
    val dialPadAutoFormat: Boolean,
    val dialPadShowT9Suggestions: Boolean,
    val dialPadLongPressBackspaceClears: Boolean,
    val dialPadToggleButtonSizeDp: Int,
    val dialPadBackspaceLongPressDurationMs: Int,
    val groupTagSortOrder: String,
    val drawerFolderDisplayMode: String,
    val incomingCallCompactMode: Boolean,
    val incomingCallShowNumber: Boolean,
    val incomingCallShowTag: Boolean,
    val incomingCallShowGroup: Boolean,
    val incomingCallPhotoBackgroundEnabled: Boolean,
    val incomingCallWindowTransparency: Int,
    val incomingCallWindowSize: String,
    val incomingCallWindowPosition: String,
    val incomingCallAutoDismissSeconds: Int,
    val transferProgressOverlayOpacity: Int,
    val appIconAlias: String,
    val backgroundCategory: String,
    val callCardCornerRadius: Int,
    val appIconPreviewUri: String? = null,
    val callButtonMode: String,
    val callSimMode: String,
    val defaultCallSimHandleId: String? = null,
    val simChooserOpacity: Int,
    val simChooserSizePercent: Int,
    val appFontProfile: String,
    val customFontPath: String? = null,
    val customFontDisplayName: String? = null,
    val appLanguage: String,
    // SMS
    val smsDeliveryReports: Boolean = true,
    val flashSmsAttempt: Boolean = false,
    val preferredSmsSimHandleId: String? = null,
    val quickSmsTemplate1: String = SettingsDefaults.QUICK_SMS_TEMPLATE_1,
    val quickSmsTemplate2: String = SettingsDefaults.QUICK_SMS_TEMPLATE_2,
    val quickSmsTemplate3: String = SettingsDefaults.QUICK_SMS_TEMPLATE_3,
    // Mini call view
    val miniCallViewEnabled: Boolean = true,
    val miniCallViewOpacity: Int = 92,
    val miniCallViewSize: String = SettingsDefaults.MINI_CALL_VIEW_SIZE,
    val miniCallViewPosition: String = SettingsDefaults.MINI_CALL_VIEW_POSITION,
    // Lock screen full-screen call UI
    val lockScreenCallUiEnabled: Boolean = true,
    // Folder panel
    val folderPanelPlacement: String = SettingsDefaults.FOLDER_PANEL_PLACEMENT,
    val folderPanelMode: String = SettingsDefaults.FOLDER_PANEL_MODE,
) {
    companion object {
        val DEFAULT = AppLockSettings(
            hasPin = false,
            biometricEnabled = false,
            allowDeviceCredential = true,
            lockOnAppResume = true,
            lockAfterInactivitySeconds = SettingsDefaults.LOCK_AFTER_INACTIVITY_SECONDS,
            trashRetentionDays = SettingsDefaults.TRASH_RETENTION_DAYS,
            themeMode = SettingsDefaults.THEME_MODE,
            themePreset = SettingsDefaults.THEME_PRESET,
            accentPalette = SettingsDefaults.ACCENT_PALETTE,
            cornerStyle = SettingsDefaults.CORNER_STYLE,
            exportPath = SettingsDefaults.DEFAULT_EXPORT_PATH,
            backupPath = SettingsDefaults.DEFAULT_BACKUP_PATH,
            recordingsPath = SettingsDefaults.DEFAULT_RECORDINGS_PATH,
            showAppNameOnLockScreen = true,
            showAppIconOnLockScreen = true,
            showTimeOnLockScreen = true,
            useIllustrationOnLockScreen = true,
            lockScreenMessage = "Authenticate to access your private contacts workspace.",
            lockScreenStyle = SettingsDefaults.LOCK_SCREEN_STYLE,
            fingerprintIconStyle = SettingsDefaults.FINGERPRINT_ICON_STYLE,
            defaultContactSortOrder = SettingsDefaults.SORT_ORDER,
            contactListDensity = SettingsDefaults.LIST_DENSITY,
            showContactPhotosInList = true,
            defaultStartTab = SettingsDefaults.DEFAULT_START_TAB,
            confirmBeforeDelete = true,
            confirmBeforeBlockUnblock = true,
            showRecentCallsPreview = true,
            autoCollapseCallGroups = false,
            showBlockedContactsInSearch = true,
            includeTimestampInExportFileName = true,
            hideEmptyFoldersAndTags = false,
            openContactDirectlyOnTap = true,
            showFavoritesFirst = false,
            enableIncomingCallerPopup = true,
            enableMissedCallNotification = true,
            showPhotoInNotifications = true,
            showFolderTagsInNotifications = true,
            lockScreenNotificationVisibility = SettingsDefaults.LOCK_SCREEN_VISIBILITY,
            headsUpNotifications = true,
            overlayPopupMode = SettingsDefaults.OVERLAY_POPUP_MODE,
            vibrationEnabled = true,
            soundEnabled = true,
            hapticFeedbackEnabled = true,
            dialPadShowLetters = true,
            dialPadAutoFormat = true,
            dialPadShowT9Suggestions = true,
            dialPadLongPressBackspaceClears = true,
            dialPadToggleButtonSizeDp = SettingsDefaults.DIAL_PAD_TOGGLE_BUTTON_SIZE_DP,
            dialPadBackspaceLongPressDurationMs = SettingsDefaults.DIAL_PAD_BACKSPACE_LONG_PRESS_DURATION_MS,
            groupTagSortOrder = SettingsDefaults.GROUP_TAG_SORT_ORDER,
            drawerFolderDisplayMode = SettingsDefaults.DRAWER_FOLDER_DISPLAY_MODE,
            incomingCallCompactMode = true,
            incomingCallShowNumber = true,
            incomingCallShowTag = true,
            incomingCallShowGroup = true,
            incomingCallPhotoBackgroundEnabled = true,
            incomingCallWindowTransparency = 88,
            incomingCallWindowSize = SettingsDefaults.INCOMING_CALL_WINDOW_SIZE,
            incomingCallWindowPosition = SettingsDefaults.INCOMING_CALL_WINDOW_POSITION,
            incomingCallAutoDismissSeconds = 0,
            transferProgressOverlayOpacity = SettingsDefaults.TRANSFER_PROGRESS_OVERLAY_OPACITY,
            appIconAlias = SettingsDefaults.APP_ICON_ALIAS,
            backgroundCategory = SettingsDefaults.BACKGROUND_CATEGORY,
            callCardCornerRadius = SettingsDefaults.CALL_CARD_CORNER_RADIUS,
            callButtonMode = SettingsDefaults.CALL_BUTTON_MODE,
            callSimMode = SettingsDefaults.CALL_SIM_MODE,
            defaultCallSimHandleId = null,
            simChooserOpacity = SettingsDefaults.SIM_CHOOSER_OPACITY,
            simChooserSizePercent = SettingsDefaults.SIM_CHOOSER_SIZE_PERCENT,
            appFontProfile = SettingsDefaults.APP_FONT_PROFILE,
            customFontPath = null,
            customFontDisplayName = null,
            appLanguage = SettingsDefaults.APP_LANGUAGE,
            smsDeliveryReports = SettingsDefaults.SMS_DELIVERY_REPORTS,
            flashSmsAttempt = SettingsDefaults.FLASH_SMS_ATTEMPT,
            preferredSmsSimHandleId = null,
            quickSmsTemplate1 = SettingsDefaults.QUICK_SMS_TEMPLATE_1,
            quickSmsTemplate2 = SettingsDefaults.QUICK_SMS_TEMPLATE_2,
            quickSmsTemplate3 = SettingsDefaults.QUICK_SMS_TEMPLATE_3,
            miniCallViewEnabled = SettingsDefaults.MINI_CALL_VIEW_ENABLED,
            miniCallViewOpacity = SettingsDefaults.MINI_CALL_VIEW_OPACITY,
            miniCallViewSize = SettingsDefaults.MINI_CALL_VIEW_SIZE,
            miniCallViewPosition = SettingsDefaults.MINI_CALL_VIEW_POSITION,
            lockScreenCallUiEnabled = SettingsDefaults.LOCK_SCREEN_CALL_UI_ENABLED,
            folderPanelPlacement = SettingsDefaults.FOLDER_PANEL_PLACEMENT,
            folderPanelMode = SettingsDefaults.FOLDER_PANEL_MODE,
        )
    }
}
