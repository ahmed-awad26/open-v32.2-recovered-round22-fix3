package com.opencontacts.app

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// ─── Delivery state ───────────────────────────────────────────────────────────

enum class SmsStatus { IDLE, SENDING, SENT, DELIVERED, FAILED }

data class SmsDeliveryState(
    val id: String,
    val number: String,
    val message: String,
    val status: SmsStatus = SmsStatus.IDLE,
    val sentAt: Long = 0L,
    val deliveredAt: Long = 0L,
    val errorReason: String? = null,
)

// ─── In-process tracker (no DB for now — kept in memory per session) ──────────

object SmsDeliveryTracker {
    private val _states = MutableStateFlow<List<SmsDeliveryState>>(emptyList())
    val states: StateFlow<List<SmsDeliveryState>> = _states

    fun add(state: SmsDeliveryState) {
        _states.value = listOf(state) + _states.value.take(49)
    }

    fun update(id: String, block: SmsDeliveryState.() -> SmsDeliveryState) {
        _states.value = _states.value.map { if (it.id == id) it.block() else it }
    }

    fun clear() { _states.value = emptyList() }
}

// ─── Broadcast receiver for sent/delivered callbacks ─────────────────────────

const val ACTION_SMS_SENT = "com.opencontacts.app.SMS_SENT"
const val ACTION_SMS_DELIVERED = "com.opencontacts.app.SMS_DELIVERED"
const val EXTRA_SMS_TRACKING_ID = "sms_tracking_id"

class SmsSentBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_SMS_TRACKING_ID) ?: return
        when (intent.action) {
            ACTION_SMS_SENT -> {
                val status = when (resultCode) {
                    Activity.RESULT_OK -> SmsStatus.SENT
                    else -> SmsStatus.FAILED
                }
                val reason = when (resultCode) {
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off"
                    else -> if (resultCode != Activity.RESULT_OK) "Unknown error ($resultCode)" else null
                }
                SmsDeliveryTracker.update(id) {
                    copy(status = status, sentAt = System.currentTimeMillis(), errorReason = reason)
                }
            }
            ACTION_SMS_DELIVERED -> {
                SmsDeliveryTracker.update(id) {
                    copy(status = SmsStatus.DELIVERED, deliveredAt = System.currentTimeMillis())
                }
            }
        }
    }
}

// ─── SMS capability detection ─────────────────────────────────────────────────

/**
 * Returns true if we hold SEND_SMS permission.
 */
fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

/**
 * Returns a human-readable explanation of why Class 0 / Flash SMS is not
 * supported through standard Android public APIs. We are honest here:
 * Android's SmsManager has no public setMessageClass() API. OEM modems may
 * expose it via AT commands at root level, but that is not available to
 * regular apps. We do NOT fake flash SMS.
 */
fun flashSmsSupportReason(): String = buildString {
    append("Flash SMS (Class 0) is not supported through Android's public SmsManager API. ")
    append("Android does not expose a message-class parameter for app-level SMS sending. ")
    append("Some OEM dialer apps implement this by writing directly to the modem via AT commands, ")
    append("which is only possible with privileged system-level access not available to third-party apps. ")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        append("On Android 9+ the restriction is enforced at the SELinux policy level. ")
    }
    append("This app will send a normal SMS instead if Flash SMS attempt is enabled in settings.")
}

// ─── Core send function ───────────────────────────────────────────────────────

/**
 * Sends an SMS from a background thread.
 * - Tracks status via [SmsDeliveryTracker] if [trackDelivery] is true.
 * - Falls back gracefully when no service is available.
 * - Supports dual-SIM via [subscriptionId] (pass -1 for default).
 * - [flashAttempt]: documented no-op — we inform user but still send normal SMS.
 */
fun sendSms(
    context: Context,
    number: String,
    message: String,
    trackDelivery: Boolean,
    subscriptionId: Int = -1,
    flashAttempt: Boolean = false,
    trackingId: String = java.util.UUID.randomUUID().toString(),
): SmsDeliveryState {
    val state = SmsDeliveryState(id = trackingId, number = number, message = message, status = SmsStatus.SENDING)
    SmsDeliveryTracker.add(state)

    if (!hasSmsPermission(context)) {
        SmsDeliveryTracker.update(trackingId) { copy(status = SmsStatus.FAILED, errorReason = "SEND_SMS permission not granted") }
        return state.copy(status = SmsStatus.FAILED, errorReason = "SEND_SMS permission not granted")
    }

    // flashAttempt: not technically possible via public APIs — we document this and send normal SMS
    // We do not attempt anything that would silently fail or mislead the user

    val sentIntent = if (trackDelivery) {
        val intent = Intent(ACTION_SMS_SENT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_SMS_TRACKING_ID, trackingId)
        }
        PendingIntent.getBroadcast(
            context,
            trackingId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
        )
    } else null

    val deliveredIntent = if (trackDelivery) {
        val intent = Intent(ACTION_SMS_DELIVERED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_SMS_TRACKING_ID, trackingId)
        }
        PendingIntent.getBroadcast(
            context,
            (trackingId.hashCode() xor 0x1000),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
        )
    } else null

    runCatching {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (subscriptionId >= 0) context.getSystemService(SmsManager::class.java)
                ?.createForSubscriptionId(subscriptionId)
                ?: SmsManager.getDefault()
            else context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            @Suppress("DEPRECATION")
            if (subscriptionId >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getDefault()
            }
        }

        val parts = smsManager.divideMessage(message)
        if (parts.size == 1) {
            smsManager.sendTextMessage(number, null, message, sentIntent, deliveredIntent)
        } else {
            val sentList = if (sentIntent != null) ArrayList(List(parts.size) { sentIntent }) else null
            val deliveredList = if (deliveredIntent != null) ArrayList(List(parts.size) { deliveredIntent }) else null
            smsManager.sendMultipartTextMessage(number, null, parts, sentList, deliveredList)
        }
    }.onFailure { e ->
        SmsDeliveryTracker.update(trackingId) { copy(status = SmsStatus.FAILED, errorReason = e.message ?: "Send failed") }
    }

    return state
}

// ─── SIM options for SMS ──────────────────────────────────────────────────────

data class SmsSim(val subscriptionId: Int, val displayName: String, val slotIndex: Int)

fun availableSmsSims(context: Context): List<SmsSim> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return emptyList()
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
    return runCatching {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        @Suppress("DEPRECATION")
        val subs = sm.activeSubscriptionInfoList ?: return emptyList()
        subs.map { info ->
            SmsSim(
                subscriptionId = info.subscriptionId,
                displayName = info.displayName?.toString()?.ifBlank { "SIM ${info.simSlotIndex + 1}" } ?: "SIM ${info.simSlotIndex + 1}",
                slotIndex = info.simSlotIndex,
            )
        }
    }.getOrDefault(emptyList())
}
