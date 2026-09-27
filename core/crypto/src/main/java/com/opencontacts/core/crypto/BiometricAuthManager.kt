package com.opencontacts.core.crypto

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun canAuthenticate(allowDeviceCredential: Boolean): Boolean {
        val authenticators = authenticators(allowDeviceCredential)
        return BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun availability(allowDeviceCredential: Boolean): BiometricAvailability {
        return when (BiometricManager.from(context).canAuthenticate(authenticators(allowDeviceCredential))) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability(true, "Ready")
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability(false, "This device does not support biometric authentication.")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability(false, "Biometric hardware is currently unavailable.")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability(false, "No biometric or device credential is enrolled on this device.")
            else -> BiometricAvailability(false, "Biometric authentication is unavailable on this device.")
        }
    }

    fun createPromptInfo(title: String, allowDeviceCredential: Boolean): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Authenticate to unlock your private contacts")
            .setAllowedAuthenticators(authenticators(allowDeviceCredential))
            .setConfirmationRequired(false)
        if (!allowDeviceCredential) {
            builder.setNegativeButtonText("Cancel")
        }
        return builder.build()
    }

    private fun authenticators(allowDeviceCredential: Boolean): Int {
        return if (allowDeviceCredential) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
    }
}


data class BiometricAvailability(
    val canAuthenticate: Boolean,
    val message: String,
)
