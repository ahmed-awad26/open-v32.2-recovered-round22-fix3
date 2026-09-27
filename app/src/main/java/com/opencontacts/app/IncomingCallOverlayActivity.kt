package com.opencontacts.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencontacts.core.crypto.AppLockSettings
import com.opencontacts.core.ui.theme.OpenContactsTheme
import dagger.hilt.android.EntryPointAccessors

class IncomingCallOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        setContent {
            val settings by EntryPointAccessors.fromApplication(applicationContext, IncomingCallEntryPoint::class.java)
                .appLockRepository()
                .settings
                .collectAsStateWithLifecycle(initialValue = AppLockSettings.DEFAULT)
            OpenContactsTheme(themeMode = settings.themeMode, themePreset = settings.themePreset, accentPalette = settings.accentPalette, cornerStyle = settings.cornerStyle, backgroundCategory = settings.backgroundCategory, appFontProfile = settings.appFontProfile, customFontPath = settings.customFontPath) {
                IncomingCallOverlayActivityScreen(onDismiss = { finish() })
            }
        }
    }
}
