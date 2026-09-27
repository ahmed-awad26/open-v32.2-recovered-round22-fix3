package com.opencontacts.core.model

import java.util.UUID

data class VaultSummary(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val colorToken: String,
    val iconToken: String,
    val isLocked: Boolean,
    val isArchived: Boolean,
    val isDefault: Boolean = false,
)

data class ContactSummary(
    val id: String,
    val displayName: String,
    val primaryPhone: String?,
    val phoneNumbers: List<ContactPhoneNumber> = primaryPhone?.takeIf { it.isNotBlank() }?.let { listOf(ContactPhoneNumber(value = it)) } ?: emptyList(),
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val folderName: String? = null,
    val folderNames: List<String> = emptyList(),
    val deletedAt: Long? = null,
    val photoUri: String? = null,
    val isBlocked: Boolean = false,
    val blockMode: String = "NONE",
    val socialLinks: List<ContactSocialLink> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class ContactPhoneNumber(
    val value: String,
    val type: String = "mobile",
    val label: String? = null,
)

data class NoteSummary(
    val id: String,
    val contactId: String,
    val body: String,
    val createdAt: Long,
)

data class ReminderSummary(
    val id: String,
    val contactId: String,
    val title: String,
    val dueAt: Long,
    val isDone: Boolean,
    val createdAt: Long,
)

data class TimelineItemSummary(
    val id: String,
    val contactId: String,
    val type: String,
    val title: String,
    val subtitle: String?,
    val createdAt: Long,
)

data class ContactDetails(
    val contact: ContactSummary,
    val notes: List<NoteSummary> = emptyList(),
    val reminders: List<ReminderSummary> = emptyList(),
    val timeline: List<TimelineItemSummary> = emptyList(),
)

data class ContactDraft(
    val id: String? = null,
    val displayName: String,
    val primaryPhone: String? = null,
    val phoneNumbers: List<ContactPhoneNumber> = primaryPhone?.takeIf { it.isNotBlank() }?.let { listOf(ContactPhoneNumber(value = it)) } ?: emptyList(),
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val folderName: String? = null,
    val folderNames: List<String> = emptyList(),
    val photoUri: String? = null,
    val isBlocked: Boolean = false,
    val blockMode: String = "NONE",
    val socialLinks: List<ContactSocialLink> = emptyList(),
)

data class ContactSocialLink(
    val type: String,
    val value: String,
    val label: String? = null,
)

data class TagSummary(
    val name: String,
    val colorToken: String = "default",
    val usageCount: Int = 0,
)

data class FolderSummary(
    val name: String,
    val iconToken: String = "folder",
    val colorToken: String = "blue",
    val usageCount: Int = 0,
    val imageUri: String? = null,
    val description: String? = null,
    val sortOrder: Int = 0,
    val isPinned: Boolean = false,
)

data class BackupRecordSummary(
    val id: String = UUID.randomUUID().toString(),
    val provider: String,
    val vaultId: String,
    val createdAt: Long,
    val status: String,
    val filePath: String,
    val fileSizeBytes: Long,
)

data class ImportExportHistorySummary(
    val id: String = UUID.randomUUID().toString(),
    val operationType: String,
    val vaultId: String,
    val createdAt: Long,
    val status: String,
    val filePath: String,
    val itemCount: Int,
)

enum class ImportExportFormat {
    VCF,
    CSV,
    JSON,
    EXCEL,
}


fun List<ContactPhoneNumber>.normalizedPhoneNumbers(): List<ContactPhoneNumber> =
    this.mapNotNull { phone ->
        val cleanValue = phone.value.trim()
        if (cleanValue.isBlank()) null else ContactPhoneNumber(
            value = cleanValue,
            type = phone.type.trim().ifBlank { "mobile" },
            label = phone.label?.trim()?.takeIf { it.isNotBlank() },
        )
    }.distinctBy { phone -> phone.value.filter(Char::isDigit).ifBlank { phone.value.lowercase() } }

fun ContactSummary.allPhoneNumbers(): List<ContactPhoneNumber> =
    phoneNumbers.normalizedPhoneNumbers().ifEmpty {
        primaryPhone?.takeIf { it.isNotBlank() }?.let { listOf(ContactPhoneNumber(value = it)) } ?: emptyList()
    }

fun ContactDraft.allPhoneNumbers(): List<ContactPhoneNumber> =
    phoneNumbers.normalizedPhoneNumbers().ifEmpty {
        primaryPhone?.takeIf { it.isNotBlank() }?.let { listOf(ContactPhoneNumber(value = it)) } ?: emptyList()
    }
