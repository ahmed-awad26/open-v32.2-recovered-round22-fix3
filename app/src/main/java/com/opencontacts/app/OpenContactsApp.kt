package com.opencontacts.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OpenContactsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyBootstrapLocaleSafely()
    }

    private fun applyBootstrapLocaleSafely() {
        runCatching {
            val localeTag = when (
                getSharedPreferences(BOOTSTRAP_PREFS, MODE_PRIVATE)
                    .getString(KEY_APP_LANGUAGE, DEFAULT_LANGUAGE)
                    ?.uppercase()
            ) {
                "AR" -> "ar"
                "EN" -> "en"
                else -> ""
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeTag))
        }
    }

    private companion object {
        const val BOOTSTRAP_PREFS = "opencontacts_bootstrap"
        const val KEY_APP_LANGUAGE = "app_language"
        const val DEFAULT_LANGUAGE = "SYSTEM"
    }
}
