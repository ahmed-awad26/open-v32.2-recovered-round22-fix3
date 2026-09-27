package com.opencontacts.data.db.mapper

import com.opencontacts.core.model.BackupRecordSummary
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactPhoneNumber
import com.opencontacts.core.model.ContactSocialLink
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.allPhoneNumbers
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.ImportExportHistorySummary
import com.opencontacts.core.model.NoteSummary
import com.opencontacts.core.model.ReminderSummary
import com.opencontacts.core.model.TagSummary
import com.opencontacts.core.model.TimelineItemSummary
import com.opencontacts.core.model.VaultSummary
import com.opencontacts.data.db.entity.BackupRecordEntity
import com.opencontacts.data.db.entity.ContactEntity
import com.opencontacts.data.db.entity.ContactWithRelations
import com.opencontacts.data.db.entity.FolderEntity
import com.opencontacts.data.db.entity.ImportExportHistoryEntity
import com.opencontacts.data.db.entity.NoteEntity
import com.opencontacts.data.db.entity.ReminderEntity
import com.opencontacts.data.db.entity.TagEntity
import com.opencontacts.data.db.entity.TimelineEntity
import com.opencontacts.data.db.entity.VaultRegistryEntity
import org.json.JSONArray
import org.json.JSONObject

fun VaultRegistryEntity.toModel() = VaultSummary(
    id = vaultId,
    displayName = displayName,
    colorToken = colorToken,
    iconToken = iconToken,
    isLocked = isLocked,
    isArchived = isArchived,
    isDefault = isDefault,
)

fun ContactWithRelations.toModel(): ContactSummary {
    val folderNames = folders.map { it.displayName }.distinct()
    val phoneNumbers = contact.phoneNumbersJson.decodePhoneNumbers(contact.primaryPhone)
    return ContactSummary(
        id = contact.contactId,
        displayName = contact.displayName,
        primaryPhone = phoneNumbers.firstOrNull()?.value ?: contact.primaryPhone,
        phoneNumbers = phoneNumbers,
        tags = tags.map { it.displayName },
        isFavorite = contact.isFavorite,
        folderName = folder?.displayName ?: folderNames.firstOrNull(),
        folderNames = folderNames,
        deletedAt = contact.deletedAt,
        photoUri = contact.photoUri,
        isBlocked = contact.isBlocked,
        blockMode = contact.blockMode,
        socialLinks = contact.externalLinksJson.decodeSocialLinks(),
        createdAt = contact.createdAt,
        updatedAt = contact.updatedAt,
    )
}

fun ContactEntity.toModel() = ContactSummary(
    id = contactId,
    displayName = displayName,
    primaryPhone = phoneNumbersJson.decodePhoneNumbers(primaryPhone).firstOrNull()?.value ?: primaryPhone,
    phoneNumbers = phoneNumbersJson.decodePhoneNumbers(primaryPhone),
    tags = tagCsv.split(',').mapNotNull { tag -> tag.trim().takeIf(String::isNotEmpty) },
    isFavorite = isFavorite,
    folderName = folderName,
    folderNames = folderName?.let(::listOf).orEmpty(),
    deletedAt = deletedAt,
    photoUri = photoUri,
    isBlocked = isBlocked,
    blockMode = blockMode,
    socialLinks = externalLinksJson.decodeSocialLinks(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ContactSummary.toEntity(now: Long = System.currentTimeMillis()): ContactEntity {
    val normalizedPhones = allPhoneNumbers()
    return ContactEntity(
        contactId = id,
        displayName = displayName,
        sortKey = displayName.trim().lowercase(),
        primaryPhone = normalizedPhones.firstOrNull()?.value ?: primaryPhone,
        tagCsv = tags.joinToString(","),
        isFavorite = isFavorite,
    folderName = folderName ?: folderNames.firstOrNull(),
        createdAt = if (createdAt == 0L) now else createdAt,
    updatedAt = if (updatedAt == 0L) now else updatedAt,
        isDeleted = deletedAt != null,
    deletedAt = deletedAt,
    photoUri = photoUri,
    isBlocked = isBlocked,
    blockMode = blockMode,
    externalLinksJson = socialLinks.encodeSocialLinks(),
        phoneNumbersJson = normalizedPhones.encodePhoneNumbers(),
    )
}

fun ContactDraft.toEntity(contactId: String, createdAt: Long, now: Long = System.currentTimeMillis()): ContactEntity {
    val normalizedPhones = allPhoneNumbers()
    return ContactEntity(
        contactId = contactId,
        displayName = displayName,
        sortKey = displayName.trim().lowercase(),
        primaryPhone = normalizedPhones.firstOrNull()?.value ?: primaryPhone,
        tagCsv = tags.joinToString(","),
        isFavorite = isFavorite,
    folderName = folderName ?: folderNames.firstOrNull(),
        createdAt = createdAt,
    updatedAt = now,
    isDeleted = false,
    deletedAt = null,
    photoUri = photoUri,
    isBlocked = isBlocked,
    blockMode = blockMode,
        externalLinksJson = socialLinks.encodeSocialLinks(),
        phoneNumbersJson = normalizedPhones.encodePhoneNumbers(),
    )
}

fun TagEntity.toModel(usageCount: Int = 0) = TagSummary(
    name = displayName,
    colorToken = colorToken,
    usageCount = usageCount,
)

fun FolderEntity.toModel(usageCount: Int = 0) = FolderSummary(
    name = displayName,
    iconToken = iconToken,
    colorToken = colorToken,
    usageCount = usageCount,
    imageUri = imageUri,
    description = description,
    sortOrder = sortOrder,
    isPinned = isPinned,
)

fun NoteEntity.toModel() = NoteSummary(
    id = noteId,
    contactId = contactId,
    body = body,
    createdAt = createdAt,
)

fun ReminderEntity.toModel() = ReminderSummary(
    id = reminderId,
    contactId = contactId,
    title = title,
    dueAt = dueAt,
    isDone = isDone,
    createdAt = createdAt,
)

fun TimelineEntity.toModel() = TimelineItemSummary(
    id = timelineId,
    contactId = contactId,
    type = type,
    title = title,
    subtitle = subtitle,
    createdAt = createdAt,
)

fun BackupRecordEntity.toModel() = BackupRecordSummary(
    id = backupId,
    provider = provider,
    vaultId = vaultId,
    createdAt = createdAt,
    status = status,
    filePath = filePath,
    fileSizeBytes = fileSizeBytes,
)

fun ImportExportHistoryEntity.toModel() = ImportExportHistorySummary(
    id = historyId,
    operationType = operationType,
    vaultId = vaultId,
    createdAt = createdAt,
    status = status,
    filePath = filePath,
    itemCount = itemCount,
)

fun List<ContactSocialLink>.encodeSocialLinks(): String = JSONArray().apply {
    forEach { link ->
        put(JSONObject().apply {
            put("type", link.type)
            put("value", link.value)
            put("label", link.label)
        })
    }
}.toString()

fun String?.decodeSocialLinks(): List<ContactSocialLink> {
    if (this.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(this)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { obj ->
                val type = obj.optString("type").trim()
                val value = obj.optString("value").trim()
                if (type.isBlank() || value.isBlank()) null else ContactSocialLink(type = type, value = value, label = obj.optString("label").takeIf { it.isNotBlank() })
            }
        }
    }.getOrDefault(emptyList())
}


fun List<ContactPhoneNumber>.encodePhoneNumbers(): String = JSONArray().apply {
    forEach { phone ->
        put(JSONObject().apply {
            put("value", phone.value)
            put("type", phone.type)
            put("label", phone.label)
        })
    }
}.toString()

fun String?.decodePhoneNumbers(fallbackPrimaryPhone: String? = null): List<ContactPhoneNumber> {
    val parsed = if (this.isNullOrBlank()) {
        emptyList()
    } else {
        runCatching {
            val array = JSONArray(this)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { obj ->
                    val value = obj.optString("value").trim()
                    if (value.isBlank()) null else ContactPhoneNumber(
                        value = value,
                        type = obj.optString("type").trim().ifBlank { "mobile" },
                        label = obj.optString("label").trim().takeIf { it.isNotBlank() },
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
    return if (parsed.isNotEmpty()) parsed else fallbackPrimaryPhone?.takeIf { it.isNotBlank() }?.let { listOf(ContactPhoneNumber(value = it)) } ?: emptyList()
}
