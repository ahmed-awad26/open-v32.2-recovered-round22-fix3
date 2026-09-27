package com.opencontacts.core.common

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast

private const val DEFAULT_DIALER_REQUEST_CODE = 4042
private const val BOOTSTRAP_PREFS = "opencontacts_bootstrap"
private const val KEY_CALL_BUTTON_MODE = "call_button_mode"
private const val KEY_CALL_SIM_MODE = "call_sim_mode"
private const val KEY_DEFAULT_CALL_SIM_HANDLE_ID = "default_call_sim_handle_id"
private const val KEY_SIM_CHOOSER_OPACITY = "sim_chooser_opacity"
private const val KEY_SIM_CHOOSER_SIZE_PERCENT = "sim_chooser_size_percent"
private const val MODE_OPENCONTACTS_DEFAULT_APP = "OPENCONTACTS_DEFAULT_APP"
private const val MODE_SYSTEM_PHONE_APP = "SYSTEM_PHONE_APP"
private const val MODE_ASK_EVERY_TIME = "ASK_EVERY_TIME"
private const val MODE_DEFAULT_SIM = "DEFAULT_SIM"
private const val DEFAULT_SIM_CHOOSER_OPACITY = 90
private const val DEFAULT_SIM_CHOOSER_SIZE_PERCENT = 92

private const val EXTRA_PHONE_NUMBER = "extra_phone_number"
private const val EXTRA_CALL_MODE = "extra_call_mode"
private const val EXTRA_PREFILLED_HANDLE_ID = "extra_prefilled_handle_id"

data class CallSimOption(
    val handleId: String,
    val label: String,
    val slotIndex: Int,
)

fun isOpenContactsDefaultDialer(context: Context): Boolean {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    return telecomManager?.defaultDialerPackage == context.packageName
}

fun currentCallButtonMode(context: Context): String =
    bootstrapPrefs(context)
        .getString(KEY_CALL_BUTTON_MODE, MODE_OPENCONTACTS_DEFAULT_APP)
        ?.uppercase()
        ?: MODE_OPENCONTACTS_DEFAULT_APP

fun currentCallSimMode(context: Context): String =
    bootstrapPrefs(context)
        .getString(KEY_CALL_SIM_MODE, MODE_ASK_EVERY_TIME)
        ?.uppercase()
        ?: MODE_ASK_EVERY_TIME

fun currentDefaultCallSimHandleId(context: Context): String? =
    bootstrapPrefs(context)
        .getString(KEY_DEFAULT_CALL_SIM_HANDLE_ID, null)
        ?.takeIf { it.isNotBlank() }

fun currentSimChooserOpacity(context: Context): Int =
    bootstrapPrefs(context).getInt(KEY_SIM_CHOOSER_OPACITY, DEFAULT_SIM_CHOOSER_OPACITY).coerceIn(45, 100)

fun currentSimChooserSizePercent(context: Context): Int =
    bootstrapPrefs(context).getInt(KEY_SIM_CHOOSER_SIZE_PERCENT, DEFAULT_SIM_CHOOSER_SIZE_PERCENT).coerceIn(72, 100)

fun availableCallSimOptions(context: Context): List<CallSimOption> {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return emptyList()
    return telecomManager.callCapablePhoneAccounts.mapIndexed { index, handle ->
        val phoneAccount = telecomManager.getPhoneAccount(handle)
        val label = phoneAccount?.label?.toString()?.takeIf { it.isNotBlank() }
            ?: "SIM ${index + 1}"
        CallSimOption(
            handleId = handle.id.orEmpty(),
            label = label,
            slotIndex = index,
        )
    }
}

fun requestOpenContactsDefaultDialer(context: Context): Boolean {
    val activity = context as? Activity
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            return if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                true
            } else {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                if (activity != null) {
                    activity.startActivityForResult(intent, DEFAULT_DIALER_REQUEST_CODE)
                } else {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                false
            }
        }
    }
    context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return false
}

fun handoffCallToSystemPhoneApp(context: Context, phone: String?): Boolean {
    val number = phone?.trim().orEmpty()
    if (number.isBlank()) return false
    return handoffCallToSystemPhoneApp(context, number, null)
}

fun startInternalCallOrPrompt(context: Context, phone: String?): Boolean {
    val number = phone?.trim().orEmpty()
    if (number.isBlank()) return false
    val sims = availableCallSimOptions(context)
    val simMode = currentCallSimMode(context)
    val configuredHandleId = currentDefaultCallSimHandleId(context)
    val chosenHandleId = when {
        sims.isEmpty() -> null
        sims.size == 1 -> sims.first().handleId
        simMode == MODE_DEFAULT_SIM && !configuredHandleId.isNullOrBlank() && sims.any { it.handleId == configuredHandleId } -> configuredHandleId
        else -> {
            launchSimChooser(context, number, currentCallButtonMode(context), configuredHandleId)
            return true
        }
    }
    return performCallWithMode(context, number, currentCallButtonMode(context), chosenHandleId)
}

fun performCallWithMode(context: Context, phone: String?, mode: String, handleId: String?): Boolean {
    val number = phone?.trim().orEmpty()
    if (number.isBlank()) return false
    val normalizedMode = mode.uppercase()
    return when (normalizedMode) {
        MODE_SYSTEM_PHONE_APP -> handoffCallToSystemPhoneApp(context, number, handleId)
        else -> placeCallInsideOpenContactsOrPrompt(context, number, handleId)
    }
}

private fun placeCallInsideOpenContactsOrPrompt(context: Context, phone: String, handleId: String?): Boolean {
    val encodedUri = createCallUri(phone)
    if (isOpenContactsDefaultDialer(context)) {
        return runCatching {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val extras = android.os.Bundle().apply {
                resolvePhoneAccountHandle(context, handleId)?.let { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
            }
            telecomManager.placeCall(encodedUri, extras)
            true
        }.getOrElse {
            Toast.makeText(context, "Unable to place the call right now.", Toast.LENGTH_SHORT).show()
            false
        }
    }
    Toast.makeText(context, "Make OpenContacts the default phone app to place calls inside the app.", Toast.LENGTH_LONG).show()
    requestOpenContactsDefaultDialer(context)
    return false
}

private fun handoffCallToSystemPhoneApp(context: Context, phone: String, handleId: String?): Boolean {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    val systemDialerPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) telecomManager?.systemDialerPackage else null
    val encodedUri = createCallUri(phone)
    val handle = resolvePhoneAccountHandle(context, handleId)
    val action = if (handle != null || looksLikeUssd(phone)) Intent.ACTION_CALL else Intent.ACTION_DIAL
    val intent = Intent(action, encodedUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!systemDialerPackage.isNullOrBlank() && systemDialerPackage != context.packageName) {
            `package` = systemDialerPackage
        }
        handle?.let { putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.recoverCatching {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, encodedUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!systemDialerPackage.isNullOrBlank() && systemDialerPackage != context.packageName) {
                    `package` = systemDialerPackage
                }
            },
        )
        true
    }.getOrElse {
        Toast.makeText(context, "Unable to open the phone app right now.", Toast.LENGTH_SHORT).show()
        false
    }
}

private fun looksLikeUssd(value: String): Boolean = value.contains('*') || value.contains('#')

private fun createCallUri(number: String): Uri =
    if (looksLikeUssd(number)) Uri.parse("tel:${Uri.encode(number)}") else Uri.parse("tel:$number")

private fun resolvePhoneAccountHandle(context: Context, handleId: String?): PhoneAccountHandle? {
    val wantedId = handleId?.takeIf { it.isNotBlank() } ?: return null
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return null
    return telecomManager.callCapablePhoneAccounts.firstOrNull { it.id == wantedId }
}

private fun launchSimChooser(context: Context, number: String, mode: String, prefilledHandleId: String?) {
    val intent = Intent()
        .setClassName(context.packageName, "${context.packageName}.SimChooserActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_PHONE_NUMBER, number)
        .putExtra(EXTRA_CALL_MODE, mode.uppercase())
        .putExtra(EXTRA_PREFILLED_HANDLE_ID, prefilledHandleId)
    context.startActivity(intent)
}

private fun bootstrapPrefs(context: Context) = context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
