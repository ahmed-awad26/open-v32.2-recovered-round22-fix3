package com.opencontacts.data.repository

import com.opencontacts.data.db.entity.ContactEntity
import com.opencontacts.data.db.entity.ContactFolderCrossRef
import com.opencontacts.data.db.entity.ContactTagCrossRef
import com.opencontacts.data.db.entity.FolderEntity
import com.opencontacts.data.db.entity.NoteEntity
import com.opencontacts.data.db.entity.ReminderEntity
import com.opencontacts.data.db.entity.TagEntity
import com.opencontacts.data.db.entity.TimelineEntity
import com.opencontacts.data.db.entity.VaultRegistryEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

private const val PACKAGE_SCHEMA = "opencontacts.transfer.package"
private const val PACKAGE_VERSION = 1
private const val ENTRY_MANIFEST = "manifest.json"
private const val ENTRY_VAULT = "vault.json"
private const val ENTRY_CONTACTS = "data/contacts.ndjson"
private const val ENTRY_FOLDERS = "data/folders.ndjson"
private const val ENTRY_TAGS = "data/tags.ndjson"
private const val ENTRY_FOLDER_REFS = "data/contact_folder_refs.ndjson"
private const val ENTRY_TAG_REFS = "data/contact_tag_refs.ndjson"
private const val ENTRY_NOTES = "data/notes.ndjson"
private const val ENTRY_REMINDERS = "data/reminders.ndjson"
private const val ENTRY_TIMELINE = "data/timeline.ndjson"

data class RoundTripTransferPackage(
    val manifest: TransferPackageManifest,
    val vault: VaultRegistryEntity?,
    val contacts: List<ContactEntity>,
    val folders: List<FolderEntity>,
    val tags: List<TagEntity>,
    val folderRefs: List<ContactFolderCrossRef>,
    val tagRefs: List<ContactTagCrossRef>,
    val notes: List<NoteEntity>,
    val reminders: List<ReminderEntity>,
    val timeline: List<TimelineEntity>,
    val warnings: List<String> = emptyList(),
)

data class TransferPackageManifest(
    val schema: String = PACKAGE_SCHEMA,
    val schemaVersion: Int = PACKAGE_VERSION,
    val exportedAt: Long,
    val sourceVaultId: String,
    val appExportVersion: String = "ocpkg-v1",
    val counts: Map<String, Int>,
)

@Singleton
class RoundTripTransferPackageCodec @Inject constructor() {
    fun write(pkg: RoundTripTransferPackage, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(pkg.manifest.toJson().toString(2).encodeToByteArray())
            zip.closeEntry()

            pkg.vault?.let { vault ->
                zip.putNextEntry(ZipEntry(ENTRY_VAULT))
                zip.write(vault.toJson().toString(2).encodeToByteArray())
                zip.closeEntry()
            }

            writeNdJson(zip, ENTRY_CONTACTS, pkg.contacts) { it.toJson() }
            writeNdJson(zip, ENTRY_FOLDERS, pkg.folders) { it.toJson() }
            writeNdJson(zip, ENTRY_TAGS, pkg.tags) { it.toJson() }
            writeNdJson(zip, ENTRY_FOLDER_REFS, pkg.folderRefs) { it.toJson() }
            writeNdJson(zip, ENTRY_TAG_REFS, pkg.tagRefs) { it.toJson() }
            writeNdJson(zip, ENTRY_NOTES, pkg.notes) { it.toJson() }
            writeNdJson(zip, ENTRY_REMINDERS, pkg.reminders) { it.toJson() }
            writeNdJson(zip, ENTRY_TIMELINE, pkg.timeline) { it.toJson() }
        }
    }

    fun writeBytes(pkg: RoundTripTransferPackage): ByteArray = ByteArrayOutputStream().use { output ->
        write(pkg, output)
        output.toByteArray()
    }

    fun read(input: InputStream): RoundTripTransferPackage {
        var manifest: TransferPackageManifest? = null
        var vault: VaultRegistryEntity? = null
        val contacts = mutableListOf<ContactEntity>()
        val folders = mutableListOf<FolderEntity>()
        val tags = mutableListOf<TagEntity>()
        val folderRefs = mutableListOf<ContactFolderCrossRef>()
        val tagRefs = mutableListOf<ContactTagCrossRef>()
        val notes = mutableListOf<NoteEntity>()
        val reminders = mutableListOf<ReminderEntity>()
        val timeline = mutableListOf<TimelineEntity>()
        val warnings = mutableListOf<String>()

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    ENTRY_MANIFEST -> manifest = JSONObject(zip.readBytes().decodeToString()).toManifest()
                    ENTRY_VAULT -> vault = JSONObject(zip.readBytes().decodeToString()).toVaultEntity()
                    ENTRY_CONTACTS -> readNdJson(zip, warnings) { line -> contacts += JSONObject(line).toContactEntity() }
                    ENTRY_FOLDERS -> readNdJson(zip, warnings) { line -> folders += JSONObject(line).toFolderEntity() }
                    ENTRY_TAGS -> readNdJson(zip, warnings) { line -> tags += JSONObject(line).toTagEntity() }
                    ENTRY_FOLDER_REFS -> readNdJson(zip, warnings) { line -> folderRefs += JSONObject(line).toFolderCrossRef() }
                    ENTRY_TAG_REFS -> readNdJson(zip, warnings) { line -> tagRefs += JSONObject(line).toTagCrossRef() }
                    ENTRY_NOTES -> readNdJson(zip, warnings) { line -> notes += JSONObject(line).toNoteEntity() }
                    ENTRY_REMINDERS -> readNdJson(zip, warnings) { line -> reminders += JSONObject(line).toReminderEntity() }
                    ENTRY_TIMELINE -> readNdJson(zip, warnings) { line -> timeline += JSONObject(line).toTimelineEntity() }
                    else -> warnings += "Skipped unknown entry ${entry.name}"
                }
                zip.closeEntry()
            }
        }

        val resolvedManifest = requireNotNull(manifest) { "The selected package is missing manifest.json" }
        require(resolvedManifest.schema == PACKAGE_SCHEMA) { "Unsupported package schema: ${resolvedManifest.schema}" }
        require(resolvedManifest.schemaVersion in 1..PACKAGE_VERSION) { "Unsupported package version: ${resolvedManifest.schemaVersion}" }

        return RoundTripTransferPackage(
            manifest = resolvedManifest,
            vault = vault,
            contacts = contacts,
            folders = folders,
            tags = tags,
            folderRefs = folderRefs,
            tagRefs = tagRefs,
            notes = notes,
            reminders = reminders,
            timeline = timeline,
            warnings = warnings,
        )
    }

    fun read(bytes: ByteArray): RoundTripTransferPackage = read(ByteArrayInputStream(bytes))

    private fun <T> writeNdJson(zip: ZipOutputStream, name: String, items: List<T>, encode: (T) -> JSONObject) {
        zip.putNextEntry(ZipEntry(name))
        val writer = zip.bufferedWriter(Charsets.UTF_8)
        items.forEach { item ->
            writer.write(encode(item).toString())
            writer.newLine()
        }
        writer.flush()
        zip.closeEntry()
    }

    private fun readNdJson(input: InputStream, warnings: MutableList<String>, onLine: (String) -> Unit) {
        input.bufferedReader(Charsets.UTF_8).forEachLine { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEachLine
            runCatching { onLine(line) }
                .onFailure { warnings += it.message ?: "Skipped a malformed record" }
        }
    }
}

private fun TransferPackageManifest.toJson() = JSONObject().apply {
    put("schema", schema)
    put("schemaVersion", schemaVersion)
    put("exportedAt", exportedAt)
    put("sourceVaultId", sourceVaultId)
    put("appExportVersion", appExportVersion)
    put("counts", JSONObject(counts))
}

private fun JSONObject.toManifest() = TransferPackageManifest(
    schema = optString("schema", PACKAGE_SCHEMA),
    schemaVersion = optInt("schemaVersion", PACKAGE_VERSION),
    exportedAt = optLong("exportedAt"),
    sourceVaultId = optString("sourceVaultId"),
    appExportVersion = optString("appExportVersion", "ocpkg-v1"),
    counts = optJSONObject("counts")?.let { countsObject ->
        countsObject.keys().asSequence().associateWith { key -> countsObject.optInt(key, 0) }
    }.orEmpty(),
)

private fun VaultRegistryEntity.toJson() = JSONObject().apply {
    put("vaultId", vaultId)
    put("displayName", displayName)
    put("colorToken", colorToken)
    put("iconToken", iconToken)
    put("isArchived", isArchived)
    put("isDefault", isDefault)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONObject.toVaultEntity() = VaultRegistryEntity(
    vaultId = optString("vaultId").ifBlank { UUID.randomUUID().toString() },
    displayName = optString("displayName", "Vault"),
    colorToken = optString("colorToken", "blue"),
    iconToken = optString("iconToken", "lock"),
    dbFilename = "",
    keyAlias = "",
    isLocked = false,
    isArchived = optBoolean("isArchived", false),
    isDefault = optBoolean("isDefault", false),
    requiresBiometric = false,
    hasPin = false,
    createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt"),
)

private fun ContactEntity.toJson() = JSONObject().apply {
    put("contactId", contactId)
    put("displayName", displayName)
    put("sortKey", sortKey)
    put("primaryPhone", primaryPhone)
    put("tagCsv", tagCsv)
    put("isFavorite", isFavorite)
    put("folderName", folderName)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("isDeleted", isDeleted)
    put("deletedAt", deletedAt)
    put("photoUri", photoUri)
    put("isBlocked", isBlocked)
    put("blockMode", blockMode)
    put("externalLinksJson", externalLinksJson)
    put("phoneNumbersJson", phoneNumbersJson)
}

private fun JSONObject.toContactEntity() = ContactEntity(
    contactId = optString("contactId").ifBlank { UUID.randomUUID().toString() },
    displayName = optString("displayName", "Unnamed"),
    sortKey = optString("sortKey").ifBlank { optString("displayName", "Unnamed").trim().lowercase() },
    primaryPhone = optString("primaryPhone").takeIf { it.isNotBlank() },
    tagCsv = optString("tagCsv", ""),
    isFavorite = optBoolean("isFavorite", false),
    folderName = optString("folderName").takeIf { it.isNotBlank() },
    createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt"),
    isDeleted = optBoolean("isDeleted", false),
    deletedAt = optLong("deletedAt").takeIf { it > 0L },
    photoUri = optString("photoUri").takeIf { it.isNotBlank() },
    isBlocked = optBoolean("isBlocked", false),
    blockMode = optString("blockMode", if (optBoolean("isBlocked", false)) "INSTANT_REJECT" else "NONE"),
    externalLinksJson = optString("externalLinksJson", "[]"),
    phoneNumbersJson = optString("phoneNumbersJson", "[]"),
)

private fun FolderEntity.toJson() = JSONObject().apply {
    put("folderName", folderName)
    put("displayName", displayName)
    put("iconToken", iconToken)
    put("colorToken", colorToken)
    put("createdAt", createdAt)
    put("imageUri", imageUri)
    put("description", description)
    put("sortOrder", sortOrder)
    put("isPinned", isPinned)
}

private fun JSONObject.toFolderEntity() = FolderEntity(
    folderName = optString("folderName").ifBlank { optString("displayName", "folder") },
    displayName = optString("displayName").ifBlank { optString("folderName", "Folder") },
    iconToken = optString("iconToken", "folder"),
    colorToken = optString("colorToken", "blue"),
    createdAt = optLong("createdAt"),
    imageUri = optString("imageUri").takeIf { it.isNotBlank() },
    description = optString("description").takeIf { it.isNotBlank() },
    sortOrder = optInt("sortOrder", 0),
    isPinned = optBoolean("isPinned", false),
)

private fun TagEntity.toJson() = JSONObject().apply {
    put("tagName", tagName)
    put("displayName", displayName)
    put("colorToken", colorToken)
    put("createdAt", createdAt)
}

private fun JSONObject.toTagEntity() = TagEntity(
    tagName = optString("tagName").ifBlank { optString("displayName", "tag") },
    displayName = optString("displayName").ifBlank { optString("tagName", "Tag") },
    colorToken = optString("colorToken", "default"),
    createdAt = optLong("createdAt"),
)

private fun ContactFolderCrossRef.toJson() = JSONObject().apply {
    put("contactId", contactId)
    put("folderName", folderName)
}

private fun JSONObject.toFolderCrossRef() = ContactFolderCrossRef(
    contactId = optString("contactId"),
    folderName = optString("folderName"),
)

private fun ContactTagCrossRef.toJson() = JSONObject().apply {
    put("contactId", contactId)
    put("tagName", tagName)
}

private fun JSONObject.toTagCrossRef() = ContactTagCrossRef(
    contactId = optString("contactId"),
    tagName = optString("tagName"),
)

private fun NoteEntity.toJson() = JSONObject().apply {
    put("noteId", noteId)
    put("contactId", contactId)
    put("body", body)
    put("createdAt", createdAt)
}

private fun JSONObject.toNoteEntity() = NoteEntity(
    noteId = optString("noteId").ifBlank { UUID.randomUUID().toString() },
    contactId = optString("contactId"),
    body = optString("body"),
    createdAt = optLong("createdAt"),
)

private fun ReminderEntity.toJson() = JSONObject().apply {
    put("reminderId", reminderId)
    put("contactId", contactId)
    put("title", title)
    put("dueAt", dueAt)
    put("isDone", isDone)
    put("createdAt", createdAt)
}

private fun JSONObject.toReminderEntity() = ReminderEntity(
    reminderId = optString("reminderId").ifBlank { UUID.randomUUID().toString() },
    contactId = optString("contactId"),
    title = optString("title"),
    dueAt = optLong("dueAt"),
    isDone = optBoolean("isDone", false),
    createdAt = optLong("createdAt"),
)

private fun TimelineEntity.toJson() = JSONObject().apply {
    put("timelineId", timelineId)
    put("contactId", contactId)
    put("type", type)
    put("title", title)
    put("subtitle", subtitle)
    put("createdAt", createdAt)
}

private fun JSONObject.toTimelineEntity() = TimelineEntity(
    timelineId = optString("timelineId").ifBlank { UUID.randomUUID().toString() },
    contactId = optString("contactId"),
    type = optString("type"),
    title = optString("title"),
    subtitle = optString("subtitle").takeIf { it.isNotBlank() },
    createdAt = optLong("createdAt"),
)
