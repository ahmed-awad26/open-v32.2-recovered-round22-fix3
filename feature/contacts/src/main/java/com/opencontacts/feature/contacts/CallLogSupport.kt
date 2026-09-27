package com.opencontacts.feature.contacts

import android.content.Context
import android.provider.CallLog
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.allPhoneNumbers

data class CallLogItem(
    val id: String,
    val number: String,
    val normalizedNumber: String,
    val cachedName: String?,
    val type: String,
    val timestamp: Long,
    val durationSeconds: Long,
    val matchedContactId: String? = null,
    val matchedDisplayName: String? = null,
)

data class CallLogGroup(
    val key: String,
    val displayName: String,
    val number: String,
    val normalizedNumber: String,
    val contactId: String? = null,
    val totalCalls: Int,
    val totalDurationSeconds: Long,
    val lastType: String,
    val lastTimestamp: Long,
    val items: List<CallLogItem>,
)

internal fun normalizePhoneForMatching(phone: String?): String {
    val digits = phone.orEmpty().filter(Char::isDigit)
    if (digits.isBlank()) return ""
    return if (digits.length > 10) digits.takeLast(10) else digits
}

internal fun queryDeviceCallLogs(
    context: Context,
    contacts: List<ContactSummary> = emptyList(),
): List<CallLogItem> {
    val resolver = context.contentResolver
    val contactsByNumber = contacts
        .flatMap { contact ->
            contact.allPhoneNumbers().mapNotNull { phone ->
                val key = normalizePhoneForMatching(phone.value)
                key.takeIf { it.isNotBlank() }?.let { it to contact }
            }
        }
        .toMap()

    val cursor = runCatching {
        resolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
            ),
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        )
    }.getOrNull() ?: return emptyList()

    cursor.use {
        val items = mutableListOf<CallLogItem>()
        val idCol = it.getColumnIndexOrThrow(CallLog.Calls._ID)
        val numCol = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameCol = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeCol = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateCol = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
        val durCol = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
        while (it.moveToNext()) {
            val rawNumber = it.getString(numCol).orEmpty()
            val normalizedNumber = normalizePhoneForMatching(rawNumber)
            val matchedContact = contactsByNumber[normalizedNumber]
            items += CallLogItem(
                id = it.getString(idCol),
                number = rawNumber,
                normalizedNumber = normalizedNumber,
                cachedName = it.getString(nameCol),
                type = when (it.getInt(typeCol)) {
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    CallLog.Calls.REJECTED_TYPE -> "Rejected"
                    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                    CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
                    else -> "Incoming"
                },
                timestamp = it.getLong(dateCol),
                durationSeconds = it.getLong(durCol),
                matchedContactId = matchedContact?.id,
                matchedDisplayName = matchedContact?.displayName,
            )
        }
        return items
    }
}

internal fun groupCallLogs(items: List<CallLogItem>): List<CallLogGroup> {
    return items
        .groupBy { item ->
            when {
                !item.matchedContactId.isNullOrBlank() -> "contact:${item.matchedContactId}"
                item.normalizedNumber.isNotBlank() -> "number:${item.normalizedNumber}"
                !item.cachedName.isNullOrBlank() -> "name:${item.cachedName}"
                else -> "item:${item.id}"
            }
        }
        .values
        .map { groupItems ->
            val sorted = groupItems.sortedByDescending { it.timestamp }
            val last = sorted.first()
            CallLogGroup(
                key = when {
                    !last.matchedContactId.isNullOrBlank() -> "contact:${last.matchedContactId}"
                    last.normalizedNumber.isNotBlank() -> "number:${last.normalizedNumber}"
                    !last.cachedName.isNullOrBlank() -> "name:${last.cachedName}"
                    else -> "item:${last.id}"
                },
                displayName = last.matchedDisplayName ?: last.cachedName ?: last.number.ifBlank { "Unknown number" },
                number = last.number,
                normalizedNumber = last.normalizedNumber,
                contactId = last.matchedContactId,
                totalCalls = sorted.size,
                totalDurationSeconds = sorted.sumOf { it.durationSeconds },
                lastType = last.type,
                lastTimestamp = last.timestamp,
                items = sorted,
            )
        }
        .sortedByDescending { it.lastTimestamp }
}
