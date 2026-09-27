package com.opencontacts.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.opencontacts.core.crypto.AppLockRepository
import com.opencontacts.core.crypto.VaultPassphraseManager
import com.opencontacts.core.model.BackupRecordSummary
import com.opencontacts.core.model.ContactPhoneNumber
import com.opencontacts.core.model.ContactSocialLink
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.allPhoneNumbers
import com.opencontacts.core.model.normalizedPhoneNumbers
import com.opencontacts.core.model.ImportConflictStrategy
import com.opencontacts.core.model.ImportExportExecutionReport
import com.opencontacts.core.model.ImportExportHistorySummary
import com.opencontacts.core.model.ImportExportStats
import com.opencontacts.core.model.TransferProgressUpdate
import com.opencontacts.data.db.dao.ContactsDao
import com.opencontacts.data.db.dao.VaultRegistryDao
import com.opencontacts.data.db.database.VaultDatabaseFactory
import com.opencontacts.data.db.entity.BackupRecordEntity
import com.opencontacts.data.db.entity.ContactEntity
import com.opencontacts.data.db.entity.ContactFolderCrossRef
import com.opencontacts.data.db.entity.ContactTagCrossRef
import com.opencontacts.data.db.entity.ContactWithRelations
import com.opencontacts.data.db.entity.FolderEntity
import com.opencontacts.data.db.entity.ImportExportHistoryEntity
import com.opencontacts.data.db.entity.NoteEntity
import com.opencontacts.data.db.entity.ReminderEntity
import com.opencontacts.data.db.entity.TagEntity
import com.opencontacts.data.db.entity.TimelineEntity
import com.opencontacts.data.db.entity.VaultRegistryEntity
import com.opencontacts.data.db.mapper.decodeSocialLinks
import com.opencontacts.data.db.mapper.encodeSocialLinks
import com.opencontacts.data.db.mapper.encodePhoneNumbers
import com.opencontacts.data.db.mapper.toModel
import com.opencontacts.domain.vaults.VaultTransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

@Singleton
private object TransferBuckets {
    const val ZIP = "ZIP"
    const val INTERNAL_PACKAGE = "InternalPackage"
    const val JSON = "JSON"
    const val CSV = "CSV"
    const val VCF = "VCF"
    const val EXCEL = "Excel"
}

class VaultTransferRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDatabaseFactory: VaultDatabaseFactory,
    private val vaultRegistryDao: VaultRegistryDao,
    private val vaultPassphraseManager: VaultPassphraseManager,
    private val googleDriveBackupAdapter: GoogleDriveBackupAdapter,
    private val oneDriveBackupAdapter: OneDriveBackupAdapter,
    private val phoneContactsBridge: PhoneContactsBridge,
    private val backupFileCodec: BackupFileCodec,
    private val roundTripCodec: RoundTripTransferPackageCodec,
    private val vcfHandler: VcfHandler,
    private val csvHandler: CsvHandler,
    private val transferDestinationManager: TransferDestinationManager,
    private val appLockRepository: AppLockRepository,
) : VaultTransferRepository {

    override fun observeBackupRecords(vaultId: String): Flow<List<BackupRecordSummary>> = flow {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        emitAll(db.contactsDao().observeBackupRecords().map { it.map(BackupRecordEntity::toModel) })
    }

    override fun observeImportExportHistory(vaultId: String): Flow<List<ImportExportHistorySummary>> = flow {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        emitAll(db.contactsDao().observeImportExportHistory().map { it.map(ImportExportHistoryEntity::toModel) })
    }

    override suspend fun createLocalBackup(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): BackupRecordSummary {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        reportProgress(TransferProgressUpdate(0.05f, "Preparing backup", "Reading vault snapshot…"))
        val artifact = buildBackupArtifact(vaultId)
        reportProgress(TransferProgressUpdate(0.7f, "Writing backup", "Saving backup artifact…"))
        val saved = transferDestinationManager.writeBackupDocument(
            fileName = artifact.fileName,
            mimeType = "application/octet-stream",
        ) { output ->
            output.write(artifact.bytes)
        }
        reportProgress(TransferProgressUpdate(1f, "Backup completed", saved.path))
        return persistBackupRecord(
            dao = dao,
            provider = "LOCAL",
            vaultId = vaultId,
            status = "SUCCESS",
            filePath = saved.path,
            fileSizeBytes = saved.sizeBytes,
            createdAt = artifact.createdAt,
        )
    }

    override suspend fun restoreLatestLocalBackup(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): Boolean {
        val latest = transferDestinationManager.findLatestBackupDocument(
            prefix = "vault-$vaultId",
            extension = "ocbak",
        ) ?: return false
        return restoreBackupPayload(vaultId, latest.path, latest.sizeBytes, latest.readBytes(), reportProgress)
    }

    override suspend fun restoreBackupFromUri(
        vaultId: String,
        uriString: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): Boolean {
        val uri = android.net.Uri.parse(uriString)
        val displayPath = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "Selected backup"
        val size = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        return restoreBackupPayload(vaultId, displayPath, size, bytes, reportProgress)
    }

    override suspend fun stageLatestBackupToGoogleDrive(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): BackupRecordSummary {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        reportProgress(TransferProgressUpdate(0.1f, "Preparing backup", "Creating encrypted backup for Google Drive…"))
        val artifact = buildBackupArtifact(vaultId)
        transferDestinationManager.writeBackupDocument(
            fileName = artifact.fileName,
            mimeType = "application/octet-stream",
        ) { output ->
            output.write(artifact.bytes)
        }
        val staged = googleDriveBackupAdapter.stageEncryptedBackup(artifact.fileName, artifact.bytes)
        reportProgress(TransferProgressUpdate(1f, "Google Drive staging completed", staged.absolutePath))
        return persistCloudRecord(vaultId, "GOOGLE_DRIVE_STAGED", staged, dao)
    }

    override suspend fun stageLatestBackupToOneDrive(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): BackupRecordSummary {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        reportProgress(TransferProgressUpdate(0.1f, "Preparing backup", "Creating encrypted backup for OneDrive…"))
        val artifact = buildBackupArtifact(vaultId)
        transferDestinationManager.writeBackupDocument(
            fileName = artifact.fileName,
            mimeType = "application/octet-stream",
        ) { output ->
            output.write(artifact.bytes)
        }
        val staged = oneDriveBackupAdapter.stageEncryptedBackup(artifact.fileName, artifact.bytes)
        reportProgress(TransferProgressUpdate(1f, "OneDrive staging completed", staged.absolutePath))
        return persistCloudRecord(vaultId, "ONEDRIVE_STAGED", staged, dao)
    }

    override suspend fun exportContactsPackage(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = db.contactsDao()
        reportProgress(TransferProgressUpdate(0.05f, "Preparing package", "Collecting contacts, folders, tags, notes, reminders, and timeline…", stats = ImportExportStats()))
        val contacts = dao.getAll()
        val folders = dao.getFolders()
        val tags = dao.getTags()
        val folderRefs = dao.getAllFolderCrossRefs()
        val tagRefs = dao.getAllCrossRefs()
        val notes = dao.getAllNotes()
        val reminders = dao.getAllReminders()
        val timeline = dao.getAllTimelineItems()
        val vault = vaultRegistryDao.getById(vaultId)
        val now = System.currentTimeMillis()
        val pkg = RoundTripTransferPackage(
            manifest = TransferPackageManifest(
                exportedAt = now,
                sourceVaultId = vaultId,
                counts = mapOf(
                    "contacts" to contacts.size,
                    "folders" to folders.size,
                    "tags" to tags.size,
                    "folderRefs" to folderRefs.size,
                    "tagRefs" to tagRefs.size,
                    "notes" to notes.size,
                    "reminders" to reminders.size,
                    "timeline" to timeline.size,
                ),
            ),
            vault = vault,
            contacts = contacts,
            folders = folders,
            tags = tags,
            folderRefs = folderRefs,
            tagRefs = tagRefs,
            notes = notes,
            reminders = reminders,
            timeline = timeline,
        )
        reportProgress(TransferProgressUpdate(0.65f, "Writing package", "Creating portable round-trip package…", stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size, foldersRestored = folders.size, tagsRestored = tags.size, vaultsRestored = if (vault != null) 1 else 0)))
        val fileName = stamped("contacts-package", vaultId, "ocpkg")
        val saved = transferDestinationManager.writeExportDocument(
            fileName = fileName,
            mimeType = "application/zip",
            subdirectory = TransferBuckets.INTERNAL_PACKAGE,
        ) { output ->
            roundTripCodec.write(pkg, output)
        }
        val history = persistHistory(dao, "EXPORT_PACKAGE", vaultId, now, "SUCCESS", saved.path, contacts.size)
        reportProgress(TransferProgressUpdate(1f, "Package export completed", saved.path, stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size, foldersRestored = folders.size, tagsRestored = tags.size, vaultsRestored = if (vault != null) 1 else 0)))
        return ImportExportExecutionReport(
            history = history,
            stats = ImportExportStats(
                scannedCount = contacts.size,
                importedCount = contacts.size,
                foldersRestored = folders.size,
                tagsRestored = tags.size,
                vaultsRestored = if (vault != null) 1 else 0,
            ),
            warnings = pkg.warnings,
        )
    }

    override suspend fun importLatestContactsPackage(
        vaultId: String,
        strategy: ImportConflictStrategy,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val latest = transferDestinationManager.findLatestExportDocument(
            prefix = "contacts-package-$vaultId",
            extension = "ocpkg",
            subdirectory = TransferBuckets.INTERNAL_PACKAGE,
        ) ?: importFallbackFile("contacts.ocpkg", TransferBuckets.INTERNAL_PACKAGE)
            ?: return noFileReport(vaultId, "IMPORT_PACKAGE")

        reportProgress(TransferProgressUpdate(0.05f, "Loading package", latest.path, indeterminate = false, stats = ImportExportStats()))
        val pkg = latest.openInputStream().use(roundTripCodec::read)
        val outcome = applyPackageImport(vaultId, pkg, strategy, reportProgress)
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val history = persistHistory(dao, "IMPORT_PACKAGE", vaultId, System.currentTimeMillis(), "SUCCESS", latest.path, outcome.stats.importedCount + outcome.stats.mergedCount)
        reportProgress(TransferProgressUpdate(1f, "Package import completed", outcome.summaryMessage, stats = outcome.stats))
        return ImportExportExecutionReport(history = history, stats = outcome.stats, warnings = outcome.warnings)
    }


    override suspend fun exportContactsZipBundle(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = db.contactsDao()
        reportProgress(TransferProgressUpdate(0.05f, "Preparing ZIP bundle", "Collecting contacts, tags, folders, and media…", stats = ImportExportStats()))
        val contacts = dao.getAll()
        val folders = dao.getFolders()
        val tags = dao.getTags()
        val folderRefs = dao.getAllFolderCrossRefs()
        val tagRefs = dao.getAllCrossRefs()
        val notes = dao.getAllNotes()
        val reminders = dao.getAllReminders()
        val timeline = dao.getAllTimelineItems()
        val vault = vaultRegistryDao.getById(vaultId)
        val now = System.currentTimeMillis()
        val pkg = RoundTripTransferPackage(
            manifest = TransferPackageManifest(
                exportedAt = now,
                sourceVaultId = vaultId,
                appExportVersion = "oczip-v1",
                counts = mapOf(
                    "contacts" to contacts.size,
                    "folders" to folders.size,
                    "tags" to tags.size,
                    "folderRefs" to folderRefs.size,
                    "tagRefs" to tagRefs.size,
                    "notes" to notes.size,
                    "reminders" to reminders.size,
                    "timeline" to timeline.size,
                ),
            ),
            vault = vault,
            contacts = contacts,
            folders = folders,
            tags = tags,
            folderRefs = folderRefs,
            tagRefs = tagRefs,
            notes = notes,
            reminders = reminders,
            timeline = timeline,
        )
        val mediaEntries = buildPortableZipMediaEntries(pkg)
        reportProgress(TransferProgressUpdate(0.6f, "Writing ZIP bundle", "Creating archive with media and stable relationship maps…", stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size, foldersRestored = folders.size, tagsRestored = tags.size, vaultsRestored = if (vault != null) 1 else 0)))
        val fileName = stamped("contacts-bundle", vaultId, "zip")
        val saved = transferDestinationManager.writeExportDocument(
            fileName = fileName,
            mimeType = "application/zip",
            subdirectory = TransferBuckets.ZIP,
        ) { output ->
            writePortableZipBundle(pkg, mediaEntries, output)
        }
        val history = persistHistory(dao, "EXPORT_ZIP", vaultId, now, "SUCCESS", saved.path, contacts.size)
        reportProgress(TransferProgressUpdate(1f, "ZIP export completed", saved.path, stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size, foldersRestored = folders.size, tagsRestored = tags.size, vaultsRestored = if (vault != null) 1 else 0)))
        return ImportExportExecutionReport(
            history = history,
            stats = ImportExportStats(
                scannedCount = contacts.size,
                importedCount = contacts.size,
                foldersRestored = folders.size,
                tagsRestored = tags.size,
                vaultsRestored = if (vault != null) 1 else 0,
            ),
            warnings = mediaEntries.warnings,
        )
    }

    override suspend fun importLatestContactsZipBundle(
        vaultId: String,
        strategy: ImportConflictStrategy,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val latest = transferDestinationManager.findLatestExportDocument(
            prefix = "contacts-bundle-$vaultId",
            extension = "zip",
            subdirectory = TransferBuckets.ZIP,
        ) ?: importFallbackFile("contacts_bundle.zip", TransferBuckets.ZIP)
            ?: return noFileReport(vaultId, "IMPORT_ZIP")

        reportProgress(TransferProgressUpdate(0.05f, "Loading ZIP bundle", latest.path, indeterminate = false, stats = ImportExportStats()))
        val pkg = latest.openInputStream().use { input -> readPortableZipBundle(input, vaultId) }
        val outcome = applyPackageImport(vaultId, pkg, strategy, reportProgress)
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val history = persistHistory(dao, "IMPORT_ZIP", vaultId, System.currentTimeMillis(), "SUCCESS", latest.path, outcome.stats.importedCount + outcome.stats.mergedCount)
        reportProgress(TransferProgressUpdate(1f, "ZIP import completed", outcome.summaryMessage, stats = outcome.stats))
        return ImportExportExecutionReport(history = history, stats = outcome.stats, warnings = outcome.warnings)
    }

    override suspend fun exportContactsJson(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = db.contactsDao()
        reportProgress(TransferProgressUpdate(0.05f, "Preparing JSON export", "Building structured export document…", stats = ImportExportStats()))
        val contacts = dao.getAll()
        val folders = dao.getFolders()
        val tags = dao.getTags()
        val folderRefs = dao.getAllFolderCrossRefs()
        val tagRefs = dao.getAllCrossRefs()
        val notes = dao.getAllNotes()
        val reminders = dao.getAllReminders()
        val timeline = dao.getAllTimelineItems()
        val vault = vaultRegistryDao.getById(vaultId)
        val now = System.currentTimeMillis()
        val root = buildStructuredJsonRoot(
            pkg = RoundTripTransferPackage(
                manifest = TransferPackageManifest(
                    exportedAt = now,
                    sourceVaultId = vaultId,
                    appExportVersion = "ocjson-v1",
                    counts = mapOf(
                        "contacts" to contacts.size,
                        "folders" to folders.size,
                        "tags" to tags.size,
                        "folderRefs" to folderRefs.size,
                        "tagRefs" to tagRefs.size,
                        "notes" to notes.size,
                        "reminders" to reminders.size,
                        "timeline" to timeline.size,
                    ),
                ),
                vault = vault,
                contacts = contacts,
                folders = folders,
                tags = tags,
                folderRefs = folderRefs,
                tagRefs = tagRefs,
                notes = notes,
                reminders = reminders,
                timeline = timeline,
            ),
        )
        val fileName = stamped("contacts-structured", vaultId, "json")
        val saved = transferDestinationManager.writeExportDocument(
            fileName = fileName,
            mimeType = "application/json",
            subdirectory = TransferBuckets.JSON,
        ) { output ->
            output.write(root.toString(2).encodeToByteArray())
        }
        val history = persistHistory(dao, "EXPORT_JSON", vaultId, now, "SUCCESS", saved.path, contacts.size)
        reportProgress(TransferProgressUpdate(1f, "JSON export completed", saved.path, stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size, foldersRestored = folders.size, tagsRestored = tags.size, vaultsRestored = if (vault != null) 1 else 0)))
        return ImportExportExecutionReport(
            history = history,
            stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size, foldersRestored = folders.size, tagsRestored = tags.size, vaultsRestored = if (vault != null) 1 else 0),
        )
    }

    override suspend fun importLatestContactsJson(
        vaultId: String,
        strategy: ImportConflictStrategy,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val latest = transferDestinationManager.findLatestExportDocument(
            prefix = "contacts-structured-$vaultId",
            extension = "json",
            subdirectory = TransferBuckets.JSON,
        ) ?: importFallbackFile("contacts.json", TransferBuckets.JSON)
            ?: return noFileReport(vaultId, "IMPORT_JSON")
        reportProgress(TransferProgressUpdate(0.05f, "Loading JSON", latest.path, stats = ImportExportStats()))
        val pkg = parseStructuredJsonRoot(JSONObject(latest.readText()))
        val outcome = applyPackageImport(vaultId, pkg, strategy, reportProgress)
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val history = persistHistory(dao, "IMPORT_JSON", vaultId, System.currentTimeMillis(), "SUCCESS", latest.path, outcome.stats.importedCount + outcome.stats.mergedCount)
        reportProgress(TransferProgressUpdate(1f, "JSON import completed", outcome.summaryMessage, stats = outcome.stats))
        return ImportExportExecutionReport(history = history, stats = outcome.stats, warnings = outcome.warnings)
    }

    override suspend fun exportContactsCsv(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        reportProgress(TransferProgressUpdate(0.05f, "Preparing CSV export", "Reading contacts…", stats = ImportExportStats()))
        val now = System.currentTimeMillis()
        val contacts = dao.getAllDetailed().map { it.toModel() }
        val fileName = stamped("contacts", vaultId, "csv")
        val saved = transferDestinationManager.writeExportDocument(
            fileName = fileName,
            mimeType = "text/csv",
            subdirectory = TransferBuckets.CSV,
        ) { output ->
            csvHandler.write(contacts, output)
        }
        val history = persistHistory(dao, "EXPORT_CSV", vaultId, now, "SUCCESS", saved.path, contacts.size)
        reportProgress(TransferProgressUpdate(1f, "CSV export completed", saved.path, stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size)))
        return ImportExportExecutionReport(history = history, stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size))
    }

    override suspend fun importLatestContactsCsv(
        vaultId: String,
        strategy: ImportConflictStrategy,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val latest = transferDestinationManager.findLatestExportDocument(
            prefix = "contacts-$vaultId",
            extension = "csv",
            subdirectory = TransferBuckets.CSV,
        ) ?: importFallbackFile("contacts.csv", TransferBuckets.CSV)
            ?: return noFileReport(vaultId, "IMPORT_CSV")
        reportProgress(TransferProgressUpdate(0.05f, "Parsing CSV", latest.path, stats = ImportExportStats()))
        val contacts = latest.openInputStream().use(csvHandler::parse)
        return applySummaryImport(vaultId, contacts, "IMPORT_CSV", latest.path, strategy, reportProgress)
    }

    override suspend fun exportContactsVcf(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        reportProgress(TransferProgressUpdate(0.05f, "Preparing VCF export", "Reading contacts…", stats = ImportExportStats()))
        val now = System.currentTimeMillis()
        val contacts = dao.getAllDetailed().map { it.toModel() }
        val fileName = stamped("contacts", vaultId, "vcf")
        val saved = transferDestinationManager.writeExportDocument(
            fileName = fileName,
            mimeType = "text/x-vcard",
            subdirectory = TransferBuckets.VCF,
        ) { output ->
            vcfHandler.write(contacts, output)
        }
        val history = persistHistory(dao, "EXPORT_VCF", vaultId, now, "SUCCESS", saved.path, contacts.size)
        reportProgress(TransferProgressUpdate(1f, "VCF export completed", saved.path, stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size)))
        return ImportExportExecutionReport(history = history, stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size))
    }

    override suspend fun exportContactsExcel(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val now = System.currentTimeMillis()
        val contacts = dao.getAllDetailed().map { it.toModel() }
        val fileName = stamped("contacts", vaultId, "xls")
        val excelHtml = buildString {
            append("<html><head><meta charset=\"utf-8\"></head><body><table border=\"1\">")
            append("<tr><th>Name</th><th>Phone</th><th>Tags</th><th>Folders</th><th>Favorite</th></tr>")
            contacts.forEach { contact ->
                append("<tr>")
                append("<td>${escapeHtml(contact.displayName)}</td>")
                append("<td>${escapeHtml(contact.primaryPhone.orEmpty())}</td>")
                append("<td>${escapeHtml(contact.tags.joinToString(" | "))}</td>")
                append("<td>${escapeHtml(contact.folderNames.ifEmpty { listOfNotNull(contact.folderName) }.joinToString(" | "))}</td>")
                append("<td>${if (contact.isFavorite) "Yes" else "No"}</td>")
                append("</tr>")
            }
            append("</table></body></html>")
        }
        val saved = transferDestinationManager.writeExportDocument(
            fileName = fileName,
            mimeType = "application/vnd.ms-excel",
            subdirectory = TransferBuckets.EXCEL,
        ) { output ->
            output.write(excelHtml.encodeToByteArray())
        }
        val history = persistHistory(dao, "EXPORT_EXCEL", vaultId, now, "SUCCESS", saved.path, contacts.size)
        reportProgress(TransferProgressUpdate(1f, "Excel export completed", saved.path, stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size)))
        return ImportExportExecutionReport(history = history, stats = ImportExportStats(scannedCount = contacts.size, importedCount = contacts.size))
    }

    override suspend fun importLatestContactsVcf(
        vaultId: String,
        strategy: ImportConflictStrategy,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val latest = transferDestinationManager.findLatestExportDocument(
            prefix = "contacts-$vaultId",
            extension = "vcf",
            subdirectory = TransferBuckets.VCF,
        ) ?: importFallbackFile("contacts.vcf", TransferBuckets.VCF)
            ?: return noFileReport(vaultId, "IMPORT_VCF")
        reportProgress(TransferProgressUpdate(0.05f, "Parsing VCF", latest.path, stats = ImportExportStats()))
        val contacts = latest.openInputStream().use(vcfHandler::parse)
        return applySummaryImport(vaultId, contacts, "IMPORT_VCF", latest.path, strategy, reportProgress)
    }

    override suspend fun importFromPhoneContacts(
        vaultId: String,
        strategy: ImportConflictStrategy,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        reportProgress(TransferProgressUpdate(0.05f, "Reading phone contacts", "Loading system contacts…", stats = ImportExportStats()))
        val contacts = phoneContactsBridge.importContacts()
        return applySummaryImport(vaultId, contacts, "IMPORT_PHONE", "content://contacts", strategy, reportProgress)
    }

    override suspend fun exportAllContactsToPhone(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val contacts = dao.getAllDetailed().map { it.toModel() }
        reportProgress(TransferProgressUpdate(0.1f, "Exporting to phone", "Writing active vault contacts to system contacts…", stats = ImportExportStats()))
        val inserted = phoneContactsBridge.exportContacts(contacts)
        val history = persistHistory(dao, "EXPORT_PHONE", vaultId, System.currentTimeMillis(), "SUCCESS", "content://contacts", inserted)
        reportProgress(TransferProgressUpdate(1f, "Phone export completed", "$inserted item(s) exported", stats = ImportExportStats(scannedCount = contacts.size, importedCount = inserted)))
        return ImportExportExecutionReport(history = history, stats = ImportExportStats(scannedCount = contacts.size, importedCount = inserted))
    }

    private suspend fun applySummaryImport(
        vaultId: String,
        contacts: List<ContactSummary>,
        operation: String,
        filePath: String,
        strategy: ImportConflictStrategy,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportExportExecutionReport {
        val pkg = buildPackageFromSummaries(vaultId, contacts)
        val outcome = applyPackageImport(vaultId, pkg, strategy, reportProgress)
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val history = persistHistory(dao, operation, vaultId, System.currentTimeMillis(), "SUCCESS", filePath, outcome.stats.importedCount + outcome.stats.mergedCount)
        return ImportExportExecutionReport(history = history, stats = outcome.stats, warnings = outcome.warnings)
    }

    private suspend fun applyPackageImport(
        vaultId: String,
        pkg: RoundTripTransferPackage,
        strategy: ImportConflictStrategy,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): ImportOutcome {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = db.contactsDao()
        val now = System.currentTimeMillis()
        val warnings = pkg.warnings.toMutableList()
        reportProgress(TransferProgressUpdate(0.12f, "Validating package", "Checking schema, references, and duplicate strategy…", stats = ImportExportStats(scannedCount = pkg.contacts.size)))

        val existingDetailed = dao.getAllDetailed()
        val existingById = existingDetailed.associateBy { it.contact.contactId }
        val existingByIdentity = existingDetailed.associateBy { identityKey(it.contact.displayName, it.contact.primaryPhone) }

        val existingFolders = dao.getFolders()
        val existingFoldersByPortable = existingFolders.associateBy { it.folderName.lowercase(Locale.ROOT) }
        val existingFoldersByDisplay = existingFolders.associateBy { it.displayName.trim().lowercase(Locale.ROOT) }
        val existingTags = dao.getTags()
        val existingTagsByPortable = existingTags.associateBy { it.tagName.lowercase(Locale.ROOT) }
        val existingTagsByDisplay = existingTags.associateBy { it.displayName.trim().lowercase(Locale.ROOT) }

        val folderNameMap = LinkedHashMap<String, String>()
        val tagNameMap = LinkedHashMap<String, String>()
        val foldersToUpsert = LinkedHashMap<String, FolderEntity>()
        val tagsToUpsert = LinkedHashMap<String, TagEntity>()
        var foldersCreated = 0
        var tagsCreated = 0

        pkg.folders.forEach { imported ->
            val match = existingFoldersByPortable[imported.folderName.lowercase(Locale.ROOT)]
                ?: existingFoldersByDisplay[imported.displayName.trim().lowercase(Locale.ROOT)]
            val targetName = match?.folderName ?: imported.folderName.ifBlank { slugify(imported.displayName, "folder") }
            folderNameMap[imported.folderName] = targetName
            if (match == null) foldersCreated++
            foldersToUpsert[targetName] = mergeFolderEntity(match, imported, targetName, strategy, now)
        }
        pkg.folderRefs.map { it.folderName }.distinct().forEach { name ->
            if (!folderNameMap.containsKey(name) && name.isNotBlank()) {
                val match = existingFoldersByPortable[name.lowercase(Locale.ROOT)] ?: existingFoldersByDisplay[name.trim().lowercase(Locale.ROOT)]
                val targetName = match?.folderName ?: name
                folderNameMap[name] = targetName
                if (match == null) {
                    foldersCreated++
                    foldersToUpsert[targetName] = FolderEntity(targetName, name, "folder", "blue", now)
                }
            }
        }

        pkg.tags.forEach { imported ->
            val match = existingTagsByPortable[imported.tagName.lowercase(Locale.ROOT)]
                ?: existingTagsByDisplay[imported.displayName.trim().lowercase(Locale.ROOT)]
            val targetName = match?.tagName ?: imported.tagName.ifBlank { slugify(imported.displayName, "tag") }
            tagNameMap[imported.tagName] = targetName
            if (match == null) tagsCreated++
            tagsToUpsert[targetName] = mergeTagEntity(match, imported, targetName, strategy, now)
        }
        pkg.tagRefs.map { it.tagName }.distinct().forEach { name ->
            if (!tagNameMap.containsKey(name) && name.isNotBlank()) {
                val match = existingTagsByPortable[name.lowercase(Locale.ROOT)] ?: existingTagsByDisplay[name.trim().lowercase(Locale.ROOT)]
                val targetName = match?.tagName ?: name
                tagNameMap[name] = targetName
                if (match == null) {
                    tagsCreated++
                    tagsToUpsert[targetName] = TagEntity(targetName, name, "default", now)
                }
            }
        }

        val importedFolderRefsByContact = pkg.folderRefs.groupBy({ it.contactId }, { folderNameMap[it.folderName] ?: it.folderName })
        val importedTagRefsByContact = pkg.tagRefs.groupBy({ it.contactId }, { tagNameMap[it.tagName] ?: it.tagName })
        val contactsToUpsert = ArrayList<ContactEntity>(pkg.contacts.size)
        val contactFolderRefs = LinkedHashSet<ContactFolderCrossRef>()
        val contactTagRefs = LinkedHashSet<ContactTagCrossRef>()
        val sourceToTargetContactId = HashMap<String, String>()
        val resetTargetIds = LinkedHashSet<String>()
        val importedSourceIds = LinkedHashSet<String>()
        var importedCount = 0
        var mergedCount = 0
        var skippedCount = 0

        reportProgress(TransferProgressUpdate(0.3f, "Preparing contacts", "Resolving duplicates and mapping portable IDs…", stats = ImportExportStats(scannedCount = pkg.contacts.size, foldersRestored = foldersCreated, tagsRestored = tagsCreated, vaultsRestored = if (pkg.vault != null) 1 else 0)))
        pkg.contacts.forEachIndexed { index, imported ->
            val match = existingById[imported.contactId] ?: existingByIdentity[identityKey(imported.displayName, imported.primaryPhone)]
            if (strategy == ImportConflictStrategy.SKIP && match != null) {
                skippedCount++
                return@forEachIndexed
            }
            val importedFoldersForContact = importedFolderRefsByContact[imported.contactId].orEmpty()
                .plus(listOfNotNull(imported.folderName?.let { folderNameMap[it] ?: it }))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ROOT) }
            val importedTagsForContact = importedTagRefsByContact[imported.contactId].orEmpty()
                .plus(imported.tagCsv.split(',').map { raw -> raw.trim() }.filter { it.isNotBlank() }.map { tagNameMap[it] ?: it })
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ROOT) }
            val targetId = match?.contact?.contactId ?: imported.contactId.ifBlank { UUID.randomUUID().toString() }
            sourceToTargetContactId[imported.contactId] = targetId
            importedSourceIds += imported.contactId
            val entity = if (match != null && strategy == ImportConflictStrategy.MERGE) {
                mergeContactEntity(match, imported, targetId, importedTagsForContact, importedFoldersForContact, now)
            } else {
                imported.copy(
                    contactId = targetId,
                    sortKey = imported.displayName.trim().lowercase(Locale.ROOT),
                    tagCsv = importedTagsForContact.joinToString(","),
                    folderName = importedFoldersForContact.firstOrNull(),
                    createdAt = match?.contact?.createdAt ?: imported.createdAt.takeIf { it > 0L } ?: now,
                    updatedAt = imported.updatedAt.takeIf { it > 0L } ?: now,
                )
            }
            if (match == null) importedCount++ else mergedCount++
            if (strategy == ImportConflictStrategy.REPLACE && match != null) {
                resetTargetIds += targetId
            }
            contactsToUpsert += entity
            importedFoldersForContact.forEach { folderName -> contactFolderRefs += ContactFolderCrossRef(targetId, folderName) }
            importedTagsForContact.forEach { tagName -> contactTagRefs += ContactTagCrossRef(targetId, tagName) }
            if (index % 50 == 0 && pkg.contacts.isNotEmpty()) {
                val progress = 0.3f + (index.toFloat() / pkg.contacts.size.toFloat()) * 0.25f
                reportProgress(TransferProgressUpdate(progress.coerceAtMost(0.55f), "Preparing contacts", "Resolved ${index + 1} / ${pkg.contacts.size} contacts…", stats = ImportExportStats(scannedCount = pkg.contacts.size, importedCount = importedCount, mergedCount = mergedCount, skippedCount = skippedCount, foldersRestored = foldersCreated, tagsRestored = tagsCreated, vaultsRestored = if (pkg.vault != null) 1 else 0)))
            }
        }

        val notesToUpsert = pkg.notes.mapNotNull { imported ->
            sourceToTargetContactId[imported.contactId]?.let { targetId -> imported.copy(contactId = targetId) }
                ?: run {
                    warnings += "Skipped note ${imported.noteId} because its contact was not imported."
                    null
                }
        }
        val remindersToUpsert = pkg.reminders.mapNotNull { imported ->
            sourceToTargetContactId[imported.contactId]?.let { targetId -> imported.copy(contactId = targetId) }
                ?: run {
                    warnings += "Skipped reminder ${imported.reminderId} because its contact was not imported."
                    null
                }
        }
        val timelineToUpsert = pkg.timeline.mapNotNull { imported ->
            sourceToTargetContactId[imported.contactId]?.let { targetId -> imported.copy(contactId = targetId) }
                ?: run {
                    warnings += "Skipped timeline item ${imported.timelineId} because its contact was not imported."
                    null
                }
        }

        reportProgress(TransferProgressUpdate(0.62f, "Applying import", "Writing folders, tags, contacts, and relations in a single transaction…", stats = ImportExportStats(scannedCount = pkg.contacts.size, importedCount = importedCount, mergedCount = mergedCount, skippedCount = skippedCount, foldersRestored = foldersCreated, tagsRestored = tagsCreated, vaultsRestored = if (pkg.vault != null) 1 else 0)))
        db.withTransaction {
            if (foldersToUpsert.isNotEmpty()) dao.upsertFolders(foldersToUpsert.values.toList())
            if (tagsToUpsert.isNotEmpty()) dao.upsertTags(tagsToUpsert.values.toList())
            if (contactsToUpsert.isNotEmpty()) dao.upsertAll(contactsToUpsert)
            if (strategy == ImportConflictStrategy.REPLACE && resetTargetIds.isNotEmpty()) {
                val ids = resetTargetIds.toList()
                dao.deleteFolderCrossRefsForContacts(ids)
                dao.deleteCrossRefsForContacts(ids)
                dao.deleteNotesForContacts(ids)
                dao.deleteRemindersForContacts(ids)
                dao.deleteTimelineForContacts(ids)
            }
            if (contactFolderRefs.isNotEmpty()) dao.insertContactFolderCrossRefs(contactFolderRefs.toList())
            if (contactTagRefs.isNotEmpty()) dao.insertContactTagCrossRefs(contactTagRefs.toList())
            if (notesToUpsert.isNotEmpty()) dao.upsertNotes(notesToUpsert)
            if (remindersToUpsert.isNotEmpty()) dao.upsertReminders(remindersToUpsert)
            if (timelineToUpsert.isNotEmpty()) dao.insertTimelineItems(timelineToUpsert)
            maybeRestoreActiveVaultMetadata(vaultId, pkg.vault, now)
        }

        val stats = ImportExportStats(
            scannedCount = pkg.contacts.size,
            importedCount = importedCount,
            mergedCount = mergedCount,
            skippedCount = skippedCount,
            failedCount = warnings.count { it.startsWith("Skipped") },
            foldersRestored = foldersCreated,
            tagsRestored = tagsCreated,
            vaultsRestored = if (pkg.vault != null) 1 else 0,
        )
        return ImportOutcome(
            stats = stats,
            warnings = warnings,
            summaryMessage = "Imported ${stats.importedCount}, merged ${stats.mergedCount}, skipped ${stats.skippedCount}",
        )
    }

    private suspend fun maybeRestoreActiveVaultMetadata(vaultId: String, importedVault: VaultRegistryEntity?, now: Long) {
        if (importedVault == null) return
        val current = vaultRegistryDao.getById(vaultId) ?: return
        val restored = current.copy(
            displayName = importedVault.displayName.ifBlank { current.displayName },
            colorToken = importedVault.colorToken.ifBlank { current.colorToken },
            iconToken = importedVault.iconToken.ifBlank { current.iconToken },
            isArchived = importedVault.isArchived,
            isDefault = importedVault.isDefault,
            updatedAt = now,
        )
        vaultRegistryDao.upsert(restored)
    }

    private fun mergeContactEntity(
        match: ContactWithRelations,
        imported: ContactEntity,
        targetId: String,
        importedTags: List<String>,
        importedFolders: List<String>,
        now: Long,
    ): ContactEntity {
        val existing = match.contact
        val mergedTags = (match.tags.map { it.tagName } + importedTags).distinctBy { it.lowercase(Locale.ROOT) }
        val mergedFolders = (match.folders.map { it.folderName } + importedFolders).distinctBy { it.lowercase(Locale.ROOT) }
        val mergedLinks = mergeSocialLinks(existing.externalLinksJson.decodeSocialLinks(), imported.externalLinksJson.decodeSocialLinks())
        val mergedPhones = (existing.phoneNumbersJson.decodePhoneNumbersForRepository(existing.primaryPhone) + imported.phoneNumbersJson.decodePhoneNumbersForRepository(imported.primaryPhone))
            .normalizedPhoneNumbers()
        return existing.copy(
            contactId = targetId,
            displayName = imported.displayName.ifBlank { existing.displayName },
            sortKey = imported.displayName.ifBlank { existing.displayName }.trim().lowercase(Locale.ROOT),
            primaryPhone = mergedPhones.firstOrNull()?.value ?: imported.primaryPhone ?: existing.primaryPhone,
            tagCsv = mergedTags.joinToString(","),
            isFavorite = existing.isFavorite || imported.isFavorite,
            folderName = (mergedFolders.firstOrNull() ?: existing.folderName),
            updatedAt = maxOf(now, imported.updatedAt, existing.updatedAt),
            photoUri = imported.photoUri ?: existing.photoUri,
            isBlocked = existing.isBlocked || imported.isBlocked,
            blockMode = when {
                existing.blockMode.equals("INSTANT_REJECT", true) || imported.blockMode.equals("INSTANT_REJECT", true) -> "INSTANT_REJECT"
                existing.blockMode.equals("SILENT_RING", true) || imported.blockMode.equals("SILENT_RING", true) -> "SILENT_RING"
                else -> "NONE"
            },
            externalLinksJson = mergedLinks.encodeSocialLinks(),
            phoneNumbersJson = mergedPhones.encodePhoneNumbers(),
        )
    }

    private fun mergeFolderEntity(
        match: FolderEntity?,
        imported: FolderEntity,
        targetName: String,
        strategy: ImportConflictStrategy,
        now: Long,
    ): FolderEntity {
        val base = match ?: FolderEntity(
            folderName = targetName,
            displayName = imported.displayName.ifBlank { targetName },
            iconToken = imported.iconToken.ifBlank { "folder" },
            colorToken = imported.colorToken.ifBlank { "blue" },
            createdAt = imported.createdAt.takeIf { it > 0L } ?: now,
            imageUri = imported.imageUri,
            description = imported.description,
            sortOrder = imported.sortOrder,
            isPinned = imported.isPinned,
        )
        return if (match == null || strategy != ImportConflictStrategy.SKIP) {
            base.copy(
                folderName = targetName,
                displayName = imported.displayName.ifBlank { base.displayName },
                iconToken = imported.iconToken.ifBlank { base.iconToken },
                colorToken = imported.colorToken.ifBlank { base.colorToken },
                imageUri = imported.imageUri ?: base.imageUri,
                description = imported.description ?: base.description,
                sortOrder = if (imported.sortOrder != 0) imported.sortOrder else base.sortOrder,
                isPinned = imported.isPinned || base.isPinned,
            )
        } else {
            base
        }
    }

    private fun mergeTagEntity(
        match: TagEntity?,
        imported: TagEntity,
        targetName: String,
        strategy: ImportConflictStrategy,
        now: Long,
    ): TagEntity {
        val base = match ?: TagEntity(targetName, imported.displayName.ifBlank { targetName }, imported.colorToken.ifBlank { "default" }, imported.createdAt.takeIf { it > 0L } ?: now)
        return if (match == null || strategy != ImportConflictStrategy.SKIP) {
            base.copy(
                tagName = targetName,
                displayName = imported.displayName.ifBlank { base.displayName },
                colorToken = imported.colorToken.ifBlank { base.colorToken },
            )
        } else {
            base
        }
    }


    private suspend fun restoreBackupPayload(
        vaultId: String,
        displayPath: String,
        sizeBytes: Long,
        wrappedPayload: ByteArray,
        reportProgress: (TransferProgressUpdate) -> Unit,
    ): Boolean {
        val db = vaultDatabaseFactory.getDatabase(vaultId)
        val dao = db.contactsDao()
        reportProgress(TransferProgressUpdate(0.1f, "Loading backup", displayPath))
        val root = decodeBackupRoot(
            targetVaultId = vaultId,
            displayPath = displayPath,
            wrappedPayload = wrappedPayload,
        )
        val restoredContactMedia = restoreBackupMediaArray(context, root.optJSONArray("contactMedia"), vaultId, "contacts")
        val restoredFolderMedia = restoreBackupMediaArray(context, root.optJSONArray("folderMedia"), vaultId, "folders")
        val restoredSettingsMedia = restoreBackupMediaArray(context, root.optJSONArray("settingsMedia"), vaultId, "settings")
        val restoredFolders = root.optJSONArray("folders")?.toFolderEntities().orEmpty()
            .map { entity -> entity.copy(imageUri = restoredFolderMedia[entity.folderName] ?: entity.imageUri) }
        val restoredContacts = root.optJSONArray("contacts")?.toContactEntities().orEmpty()
            .map { entity -> entity.copy(photoUri = restoredContactMedia[entity.contactId] ?: entity.photoUri) }
        val restoredSettingsSnapshot = root.optJSONObject("appSettings")?.let { snapshot ->
            JSONObject(snapshot.toString()).apply {
                val dataStore = optJSONObject("dataStore") ?: JSONObject().also { put("dataStore", it) }
                restoredSettingsMedia["lock_screen_background_uri"]?.let { dataStore.put("lock_screen_background_uri", it) }
                restoredSettingsMedia["app_icon_preview_uri"]?.let { dataStore.put("app_icon_preview_uri", it) }
            }
        }
        db.withTransaction {
            dao.clearTimeline()
            dao.clearNotes()
            dao.clearReminders()
            dao.clearAllCrossRefs()
            dao.clearAllFolderCrossRefs()
            dao.clearTags()
            dao.clearFolders()
            dao.clearAll()
            dao.clearBackupRecords()
            dao.clearImportExportHistory()
            dao.upsertFolders(restoredFolders)
            dao.upsertTags(root.optJSONArray("tags")?.toTagEntities().orEmpty())
            dao.upsertAll(restoredContacts)
            dao.insertContactFolderCrossRefs(root.optJSONArray("folderCrossRefs")?.toFolderCrossRefs().orEmpty())
            dao.insertContactTagCrossRefs(root.optJSONArray("crossRefs")?.toCrossRefs().orEmpty())
            dao.upsertNotes(root.optJSONArray("notes")?.toNoteEntities().orEmpty())
            dao.upsertReminders(root.optJSONArray("reminders")?.toReminderEntities().orEmpty())
            dao.insertTimelineItems(root.optJSONArray("timeline")?.toTimelineEntities().orEmpty())
            dao.upsertBackupRecord(
                BackupRecordEntity(
                    backupId = UUID.randomUUID().toString(),
                    provider = "LOCAL",
                    vaultId = vaultId,
                    createdAt = System.currentTimeMillis(),
                    status = "RESTORED",
                    filePath = displayPath,
                    fileSizeBytes = sizeBytes,
                ),
            )
        }
        appLockRepository.importRawSettingsSnapshot(restoredSettingsSnapshot)
        reportProgress(TransferProgressUpdate(1f, "Restore completed", displayPath))
        return true
    }

    private suspend fun buildBackupArtifact(vaultId: String): BackupArtifact {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val now = System.currentTimeMillis()
        val contacts = dao.getAll()
        val folders = dao.getFolders()
        val settingsSnapshot = appLockRepository.exportRawSettingsSnapshot()
        val payload = JSONObject().apply {
            put("vaultId", vaultId)
            put("createdAt", now)
            put("contacts", JSONArray(contacts.map(ContactEntity::toJson)))
            put("contactMedia", JSONArray(contacts.mapNotNull { it.photoUri.toMediaSnapshot(context, it.contactId, "contact") }))
            put("folderCrossRefs", JSONArray(dao.getAllFolderCrossRefs().map(ContactFolderCrossRef::toJson)))
            put("notes", JSONArray(dao.getAllNotes().map(NoteEntity::toJson)))
            put("reminders", JSONArray(dao.getAllReminders().map(ReminderEntity::toJson)))
            put("timeline", JSONArray(dao.getAllTimelineItems().map(TimelineEntity::toJson)))
            put("tags", JSONArray(dao.getTags().map(TagEntity::toJson)))
            put("folders", JSONArray(folders.map(FolderEntity::toJson)))
            put("folderMedia", JSONArray(folders.mapNotNull { it.imageUri.toMediaSnapshot(context, it.folderName, "folder") }))
            put("appSettings", settingsSnapshot)
            put("settingsMedia", JSONArray(buildList {
                settingsSnapshot.optJSONObject("dataStore")?.optString("lock_screen_background_uri")?.takeIf { it.isNotBlank() }?.let {
                    it.toMediaSnapshot(context, "lock_screen_background_uri", "setting")?.let(::add)
                }
                settingsSnapshot.optJSONObject("dataStore")?.optString("app_icon_preview_uri")?.takeIf { it.isNotBlank() }?.let {
                    it.toMediaSnapshot(context, "app_icon_preview_uri", "setting")?.let(::add)
                }
            }))
            put("crossRefs", JSONArray(dao.getAllCrossRefs().map(ContactTagCrossRef::toJson)))
        }
        val portablePayload = payload.toString(2).encodeToByteArray()
        return BackupArtifact(
            fileName = stamped("vault", vaultId, "ocbak"),
            createdAt = now,
            itemCount = dao.count(),
            bytes = backupFileCodec.wrap(portablePayload, now, dao.count()),
        )
    }

    private suspend fun persistBackupRecord(
        dao: ContactsDao,
        provider: String,
        vaultId: String,
        status: String,
        filePath: String,
        fileSizeBytes: Long,
        createdAt: Long = System.currentTimeMillis(),
    ): BackupRecordSummary {
        val entity = BackupRecordEntity(
            backupId = UUID.randomUUID().toString(),
            provider = provider,
            vaultId = vaultId,
            createdAt = createdAt,
            status = status,
            filePath = filePath,
            fileSizeBytes = fileSizeBytes,
        )
        dao.upsertBackupRecord(entity)
        return entity.toModel()
    }

    private suspend fun persistCloudRecord(
        vaultId: String,
        provider: String,
        stagedFile: File,
        dao: ContactsDao,
    ): BackupRecordSummary = persistBackupRecord(
        dao = dao,
        provider = provider,
        vaultId = vaultId,
        status = "STAGED",
        filePath = stagedFile.absolutePath,
        fileSizeBytes = stagedFile.length(),
    )

    private suspend fun persistHistory(
        dao: ContactsDao,
        operationType: String,
        vaultId: String,
        createdAt: Long,
        status: String,
        filePath: String,
        itemCount: Int,
    ): ImportExportHistorySummary {
        val entity = ImportExportHistoryEntity(
            historyId = UUID.randomUUID().toString(),
            operationType = operationType,
            vaultId = vaultId,
            createdAt = createdAt,
            status = status,
            filePath = filePath,
            itemCount = itemCount,
        )
        dao.upsertImportExportHistory(entity)
        return entity.toModel()
    }

    private suspend fun noFileReport(vaultId: String, operation: String): ImportExportExecutionReport {
        val dao = vaultDatabaseFactory.getDatabase(vaultId).contactsDao()
        val history = persistHistory(dao, operation, vaultId, System.currentTimeMillis(), "NO_FILE", "", 0)
        return ImportExportExecutionReport(history = history)
    }

    private fun importDir(): File = File(context.filesDir, "vault_imports").apply { mkdirs() }

    private fun importFallbackFile(fileName: String, category: String? = null): ReadableTransferDocument? {
        val candidates = buildList {
            add(File(importDir(), fileName))
            category?.let { add(File(importDir(), "$it/$fileName")) }
            val downloadsRoot = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            category?.let { add(File(downloadsRoot, "AW/$it/$fileName")) }
        }
        val file = candidates.firstOrNull { it.exists() } ?: return null
        val displayPath = file.absolutePath.substringAfter("/Download/", missingDelimiterValue = file.path)
            .substringAfter("/downloads/", missingDelimiterValue = file.path)
        return ReadableTransferDocument(
            path = displayPath,
            sizeBytes = file.length(),
            modifiedAt = file.lastModified(),
            byteReader = file::readBytes,
            inputStreamProvider = file::inputStream,
        )
    }

    private suspend fun stamped(prefix: String, vaultId: String, extension: String): String {
        val includeTimestamp = appLockRepository.settings.first().includeTimestampInExportFileName
        return if (includeTimestamp) {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(java.util.Date())
            "$prefix-$vaultId-$stamp.$extension"
        } else {
            "$prefix-$vaultId.$extension"
        }
    }

    private suspend fun encryptForVault(vaultId: String, payload: ByteArray): String {
        val keyBytes = vaultPassphraseManager.getOrCreatePassphrase(vaultId)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = SecretKeySpec(keyBytes.copyOf(32), "AES")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(payload)
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } finally {
            keyBytes.fill(0)
        }
    }

    private suspend fun decryptForVault(vaultId: String, payload: String): ByteArray {
        val keyBytes = vaultPassphraseManager.getOrCreatePassphrase(vaultId)
        return try {
            val parts = payload.split(':', limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = SecretKeySpec(keyBytes.copyOf(32), "AES")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(encrypted)
        } finally {
            keyBytes.fill(0)
        }
    }


    private suspend fun decodeBackupRoot(
        targetVaultId: String,
        displayPath: String,
        wrappedPayload: ByteArray,
    ): JSONObject {
        val payloadText = backupFileCodec.unwrap(wrappedPayload).decodeToString()
        val trimmed = payloadText.trimStart()
        if (trimmed.startsWith("{")) return JSONObject(payloadText)

        val candidateVaultIds = buildList {
            add(targetVaultId)
            parseBackupVaultId(displayPath)?.let(::add)
        }.distinct()

        var lastFailure: Throwable? = null
        candidateVaultIds.forEach { sourceVaultId ->
            val result = runCatching {
                JSONObject(decryptForVault(sourceVaultId, payloadText).decodeToString())
            }
            if (result.isSuccess) return result.getOrThrow()
            lastFailure = result.exceptionOrNull()
        }

        throw IllegalStateException(
            "Backup could not be restored. If this is an older backup, try restoring it to the vault it was created from.",
            lastFailure,
        )
    }

    private fun parseBackupVaultId(displayPath: String): String? {
        val fileName = displayPath.substringAfterLast('/')
        val regex = Regex("""vault-(.+?)(?:-\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2})?\.ocbak$""")
        return regex.find(fileName)?.groupValues?.getOrNull(1)
    }

    private fun buildPortableZipMediaEntries(pkg: RoundTripTransferPackage): PortableZipMediaCollection {
        val refs = ArrayList<PortableZipMediaRef>()
        val warnings = ArrayList<String>()

        pkg.contacts.forEach { contact ->
            val source = contact.photoUri ?: return@forEach
            val bytes = context.readBytesFromPathOrUri(source)
            if (bytes == null) {
                warnings += "Skipped photo for ${contact.displayName.ifBlank { contact.contactId }} because the source could not be read."
                return@forEach
            }
            val extension = inferMediaExtension(source)
            val safeName = safeArchiveComponent(contact.contactId.ifBlank { UUID.randomUUID().toString() })
            refs += PortableZipMediaRef(
                kind = "contact",
                key = contact.contactId,
                entryName = "media/contacts/$safeName.$extension",
                extension = extension,
                bytes = bytes,
            )
        }

        pkg.folders.forEach { folder ->
            val source = folder.imageUri ?: return@forEach
            val bytes = context.readBytesFromPathOrUri(source)
            if (bytes == null) {
                warnings += "Skipped image for folder ${folder.displayName.ifBlank { folder.folderName }} because the source could not be read."
                return@forEach
            }
            val extension = inferMediaExtension(source)
            val safeName = safeArchiveComponent(folder.folderName.ifBlank { folder.displayName })
            refs += PortableZipMediaRef(
                kind = "folder",
                key = folder.folderName,
                entryName = "media/folders/$safeName.$extension",
                extension = extension,
                bytes = bytes,
            )
        }
        return PortableZipMediaCollection(refs = refs, warnings = warnings)
    }

    private fun writePortableZipBundle(
        pkg: RoundTripTransferPackage,
        mediaEntries: PortableZipMediaCollection,
        output: java.io.OutputStream,
    ) {
        val packageBytes = roundTripCodec.writeBytes(pkg)
        val manifest = JSONObject().apply {
            put("schema", PORTABLE_ZIP_SCHEMA)
            put("schemaVersion", PORTABLE_ZIP_VERSION)
            put("exportedAt", pkg.manifest.exportedAt)
            put("sourceVaultId", pkg.manifest.sourceVaultId)
            put("embeddedPackage", PORTABLE_ZIP_PACKAGE_ENTRY)
            put("appExportVersion", "oczip-v1")
            put("counts", JSONObject(pkg.manifest.counts + mapOf("media" to mediaEntries.refs.size)))
        }
        val mediaIndex = JSONArray(mediaEntries.refs.map { ref ->
            JSONObject().apply {
                put("kind", ref.kind)
                put("key", ref.key)
                put("entry", ref.entryName)
                put("extension", ref.extension)
                put("sizeBytes", ref.bytes.size)
            }
        })

        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(PORTABLE_ZIP_MANIFEST_ENTRY))
            zip.write(manifest.toString(2).encodeToByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(PORTABLE_ZIP_PACKAGE_ENTRY))
            zip.write(packageBytes)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(PORTABLE_ZIP_MEDIA_INDEX_ENTRY))
            zip.write(mediaIndex.toString(2).encodeToByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(PORTABLE_ZIP_README_ENTRY))
            zip.write(buildPortableZipReadme(pkg, mediaEntries).encodeToByteArray())
            zip.closeEntry()

            mediaEntries.refs.forEach { ref ->
                zip.putNextEntry(ZipEntry(ref.entryName))
                zip.write(ref.bytes)
                zip.closeEntry()
            }
        }
    }

    private fun readPortableZipBundle(input: InputStream, targetVaultId: String): RoundTripTransferPackage {
        var manifest: JSONObject? = null
        var packageBytes: ByteArray? = null
        var mediaIndex: JSONArray? = null
        val mediaEntries = LinkedHashMap<String, ByteArray>()

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    PORTABLE_ZIP_MANIFEST_ENTRY -> manifest = JSONObject(zip.readBytes().decodeToString())
                    PORTABLE_ZIP_PACKAGE_ENTRY -> packageBytes = zip.readBytes()
                    PORTABLE_ZIP_MEDIA_INDEX_ENTRY -> mediaIndex = JSONArray(zip.readBytes().decodeToString())
                    PORTABLE_ZIP_README_ENTRY -> zip.readBytes()
                    else -> if (entry.name.startsWith("media/")) {
                        mediaEntries[entry.name] = zip.readBytes()
                    }
                }
                zip.closeEntry()
            }
        }

        val resolvedManifest = requireNotNull(manifest) { "The selected ZIP archive is missing manifest.json" }
        require(resolvedManifest.optString("schema") == PORTABLE_ZIP_SCHEMA) {
            "Unsupported ZIP archive schema: ${resolvedManifest.optString("schema")}" 
        }
        val schemaVersion = resolvedManifest.optInt("schemaVersion", PORTABLE_ZIP_VERSION)
        require(schemaVersion in 1..PORTABLE_ZIP_VERSION) { "Unsupported ZIP archive version: $schemaVersion" }
        val resolvedPackageBytes = requireNotNull(packageBytes) { "The selected ZIP archive is missing payload/package.ocpkg" }

        val pkg = roundTripCodec.read(ByteArrayInputStream(resolvedPackageBytes))
        val restoredMedia = restorePortableZipMedia(targetVaultId, mediaIndex, mediaEntries)
        return pkg.copy(
            contacts = pkg.contacts.map { entity -> entity.copy(photoUri = restoredMedia.contactMedia[entity.contactId] ?: entity.photoUri) },
            folders = pkg.folders.map { entity -> entity.copy(imageUri = restoredMedia.folderMedia[entity.folderName] ?: entity.imageUri) },
            warnings = pkg.warnings + restoredMedia.warnings,
        )
    }

    private fun restorePortableZipMedia(
        vaultId: String,
        mediaIndex: JSONArray?,
        mediaEntries: Map<String, ByteArray>,
    ): PortableZipRestoreResult {
        if (mediaIndex == null || mediaIndex.length() == 0) return PortableZipRestoreResult()
        val baseDir = File(context.filesDir, "portable_zip_media/$vaultId").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val contactMedia = LinkedHashMap<String, String>()
        val folderMedia = LinkedHashMap<String, String>()
        val warnings = ArrayList<String>()
        for (index in 0 until mediaIndex.length()) {
            val obj = mediaIndex.optJSONObject(index) ?: continue
            val kind = obj.optString("kind").trim()
            val key = obj.optString("key").trim()
            val entryName = obj.optString("entry").trim()
            val extension = obj.optString("extension").trim().ifBlank { "bin" }
            if (kind.isBlank() || key.isBlank() || entryName.isBlank()) continue
            val bytes = mediaEntries[entryName]
            if (bytes == null) {
                warnings += "Missing media entry for $kind:$key"
                continue
            }
            val bucketDir = File(baseDir, if (kind == "folder") "folders" else "contacts").apply { mkdirs() }
            val safeName = safeArchiveComponent(key)
            val target = File(bucketDir, "${safeName}_$index.$extension")
            runCatching { target.writeBytes(bytes) }
                .onSuccess {
                    if (kind == "folder") folderMedia[key] = target.absolutePath else contactMedia[key] = target.absolutePath
                }
                .onFailure { warnings += "Failed to restore media for $kind:$key" }
        }
        return PortableZipRestoreResult(contactMedia = contactMedia, folderMedia = folderMedia, warnings = warnings)
    }

    private fun buildPortableZipReadme(pkg: RoundTripTransferPackage, mediaEntries: PortableZipMediaCollection): String = buildString {
        appendLine("OpenContacts Portable ZIP Export")
        appendLine("Schema: $PORTABLE_ZIP_SCHEMA v$PORTABLE_ZIP_VERSION")
        appendLine("Source vault: ${pkg.vault?.displayName ?: pkg.manifest.sourceVaultId}")
        appendLine("Contacts: ${pkg.contacts.size}")
        appendLine("Folders: ${pkg.folders.size}")
        appendLine("Tags: ${pkg.tags.size}")
        appendLine("Media assets: ${mediaEntries.refs.size}")
        appendLine()
        appendLine("This archive preserves stable contact IDs, tag relations, folder relations, contact photos, and folder images so imports can reconnect everything correctly into any target vault.")
    }

    private fun safeArchiveComponent(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { UUID.randomUUID().toString() }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val PORTABLE_ZIP_SCHEMA = "opencontacts.transfer.bundle.zip"
        const val PORTABLE_ZIP_VERSION = 1
        const val PORTABLE_ZIP_MANIFEST_ENTRY = "manifest.json"
        const val PORTABLE_ZIP_PACKAGE_ENTRY = "payload/package.ocpkg"
        const val PORTABLE_ZIP_MEDIA_INDEX_ENTRY = "media/index.json"
        const val PORTABLE_ZIP_README_ENTRY = "README.txt"
    }
}

private data class BackupArtifact(
    val fileName: String,
    val createdAt: Long,
    val itemCount: Int,
    val bytes: ByteArray,
)

private data class ImportOutcome(
    val stats: ImportExportStats,
    val warnings: List<String>,
    val summaryMessage: String,
)

private data class PortableZipMediaRef(
    val kind: String,
    val key: String,
    val entryName: String,
    val extension: String,
    val bytes: ByteArray,
)

private data class PortableZipMediaCollection(
    val refs: List<PortableZipMediaRef>,
    val warnings: List<String> = emptyList(),
)

private data class PortableZipRestoreResult(
    val contactMedia: Map<String, String> = emptyMap(),
    val folderMedia: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
)

private fun String?.toMediaSnapshot(context: Context, key: String, kind: String): JSONObject? {
    if (this.isNullOrBlank()) return null
    val bytes = context.readBytesFromPathOrUri(this) ?: return null
    val extension = inferMediaExtension(this)
    return JSONObject().apply {
        put("key", key)
        put("kind", kind)
        put("extension", extension)
        put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }
}

private fun Context.readBytesFromPathOrUri(source: String): ByteArray? = runCatching {
    val asFile = File(source)
    when {
        asFile.exists() -> asFile.readBytes()
        else -> contentResolver.openInputStream(Uri.parse(source))?.use { it.readBytes() }
    }
}.getOrNull()

private fun inferMediaExtension(source: String): String {
    val candidate = source.substringAfterLast('/', source).substringBefore('?').substringAfterLast('.', "")
    return candidate.takeIf { it.isNotBlank() && it.length <= 8 } ?: "bin"
}

private fun restoreBackupMediaArray(context: Context, items: JSONArray?, vaultId: String, bucket: String): Map<String, String> {
    if (items == null || items.length() == 0) return emptyMap()
    val baseDir = File(context.filesDir, "restored_backup_media/${vaultId}/$bucket").apply {
        if (exists()) deleteRecursively()
        mkdirs()
    }
    val result = LinkedHashMap<String, String>()
    for (index in 0 until items.length()) {
        val obj = items.optJSONObject(index) ?: continue
        val key = obj.optString("key").trim().takeIf { it.isNotBlank() } ?: continue
        val base64 = obj.optString("base64").trim().takeIf { it.isNotBlank() } ?: continue
        val extension = obj.optString("extension").trim().takeIf { it.isNotBlank() } ?: "bin"
        val safeKey = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(baseDir, "${safeKey}_${index}.$extension")
        runCatching {
            target.writeBytes(Base64.decode(base64, Base64.NO_WRAP))
            result[key] = target.absolutePath
        }
    }
    return result
}

private fun buildPackageFromSummaries(vaultId: String, contacts: List<ContactSummary>): RoundTripTransferPackage {
    val now = System.currentTimeMillis()
    val folders = LinkedHashMap<String, FolderEntity>()
    val tags = LinkedHashMap<String, TagEntity>()
    val folderRefs = ArrayList<ContactFolderCrossRef>()
    val tagRefs = ArrayList<ContactTagCrossRef>()
    val entities = contacts.map { summary ->
        val folderNames = summary.folderNames.ifEmpty { listOfNotNull(summary.folderName) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        folderNames.forEach { folder ->
            folders.putIfAbsent(folder, FolderEntity(folder, folder, "folder", "blue", now))
            folderRefs += ContactFolderCrossRef(summary.id, folder)
        }
        summary.tags.map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .forEach { tag ->
                tags.putIfAbsent(tag, TagEntity(tag, tag, "default", now))
                tagRefs += ContactTagCrossRef(summary.id, tag)
            }
        summary.toEntity(now)
    }
    return RoundTripTransferPackage(
        manifest = TransferPackageManifest(
            exportedAt = now,
            sourceVaultId = vaultId,
            counts = mapOf(
                "contacts" to entities.size,
                "folders" to folders.size,
                "tags" to tags.size,
                "folderRefs" to folderRefs.size,
                "tagRefs" to tagRefs.size,
                "notes" to 0,
                "reminders" to 0,
                "timeline" to 0,
            ),
        ),
        vault = null,
        contacts = entities,
        folders = folders.values.toList(),
        tags = tags.values.toList(),
        folderRefs = folderRefs,
        tagRefs = tagRefs,
        notes = emptyList(),
        reminders = emptyList(),
        timeline = emptyList(),
    )
}

private fun buildStructuredJsonRoot(pkg: RoundTripTransferPackage): JSONObject = JSONObject().apply {
    put("schema", "opencontacts.structured.export")
    put("schemaVersion", 1)
    put("manifest", pkg.manifest.toJson())
    put("vault", pkg.vault?.toJson())
    put("contacts", JSONArray(pkg.contacts.map(ContactEntity::toJson)))
    put("folders", JSONArray(pkg.folders.map(FolderEntity::toJson)))
    put("tags", JSONArray(pkg.tags.map(TagEntity::toJson)))
    put("folderRefs", JSONArray(pkg.folderRefs.map(ContactFolderCrossRef::toJson)))
    put("tagRefs", JSONArray(pkg.tagRefs.map(ContactTagCrossRef::toJson)))
    put("notes", JSONArray(pkg.notes.map(NoteEntity::toJson)))
    put("reminders", JSONArray(pkg.reminders.map(ReminderEntity::toJson)))
    put("timeline", JSONArray(pkg.timeline.map(TimelineEntity::toJson)))
}

private fun parseStructuredJsonRoot(root: JSONObject): RoundTripTransferPackage {
    require(root.optString("schema") == "opencontacts.structured.export") { "Unsupported JSON export schema." }
    require(root.optInt("schemaVersion", 1) in 1..1) { "Unsupported JSON export version." }
    return RoundTripTransferPackage(
        manifest = root.getJSONObject("manifest").toManifest(),
        vault = root.optJSONObject("vault")?.toVaultEntity(),
        contacts = root.optJSONArray("contacts")?.toContactEntities().orEmpty(),
        folders = root.optJSONArray("folders")?.toFolderEntities().orEmpty(),
        tags = root.optJSONArray("tags")?.toTagEntities().orEmpty(),
        folderRefs = root.optJSONArray("folderRefs")?.toFolderCrossRefs().orEmpty(),
        tagRefs = root.optJSONArray("tagRefs")?.toCrossRefs().orEmpty(),
        notes = root.optJSONArray("notes")?.toNoteEntities().orEmpty(),
        reminders = root.optJSONArray("reminders")?.toReminderEntities().orEmpty(),
        timeline = root.optJSONArray("timeline")?.toTimelineEntities().orEmpty(),
    )
}

private fun identityKey(displayName: String, primaryPhone: String?): String {
    val normalizedName = displayName.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    val normalizedPhone = primaryPhone.orEmpty().filter(Char::isDigit)
    return if (normalizedPhone.isNotBlank()) "$normalizedName|$normalizedPhone" else normalizedName
}

private fun mergeSocialLinks(existing: List<ContactSocialLink>, imported: List<ContactSocialLink>): List<ContactSocialLink> =
    (existing + imported)
        .filter { it.type.isNotBlank() && it.value.isNotBlank() }
        .distinctBy { "${it.type.trim().lowercase(Locale.ROOT)}|${it.value.trim()}" }

private fun slugify(value: String, fallback: String): String {
    val slug = value.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
    return slug.ifBlank { "$fallback-${UUID.randomUUID().toString().take(8)}" }
}

private fun ContactSummary.toEntity(now: Long): ContactEntity {
    val normalizedPhones = allPhoneNumbers()
    return ContactEntity(
        contactId = id.ifBlank { UUID.randomUUID().toString() },
        displayName = displayName,
        sortKey = displayName.trim().lowercase(Locale.ROOT),
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

private fun ContactEntity.toJson(): JSONObject = JSONObject().apply {
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

private fun FolderEntity.toJson(): JSONObject = JSONObject().apply {
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

private fun TagEntity.toJson(): JSONObject = JSONObject().apply {
    put("tagName", tagName)
    put("displayName", displayName)
    put("colorToken", colorToken)
    put("createdAt", createdAt)
}

private fun ContactFolderCrossRef.toJson(): JSONObject = JSONObject().apply {
    put("contactId", contactId)
    put("folderName", folderName)
}

private fun ContactTagCrossRef.toJson(): JSONObject = JSONObject().apply {
    put("contactId", contactId)
    put("tagName", tagName)
}

private fun NoteEntity.toJson(): JSONObject = JSONObject().apply {
    put("noteId", noteId)
    put("contactId", contactId)
    put("body", body)
    put("createdAt", createdAt)
}

private fun ReminderEntity.toJson(): JSONObject = JSONObject().apply {
    put("reminderId", reminderId)
    put("contactId", contactId)
    put("title", title)
    put("dueAt", dueAt)
    put("isDone", isDone)
    put("createdAt", createdAt)
}

private fun TimelineEntity.toJson(): JSONObject = JSONObject().apply {
    put("timelineId", timelineId)
    put("contactId", contactId)
    put("type", type)
    put("title", title)
    put("subtitle", subtitle)
    put("createdAt", createdAt)
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
    schema = optString("schema", "opencontacts.transfer.package"),
    schemaVersion = optInt("schemaVersion", 1),
    exportedAt = optLong("exportedAt"),
    sourceVaultId = optString("sourceVaultId"),
    appExportVersion = optString("appExportVersion", "ocpkg-v1"),
    counts = optJSONObject("counts")?.let { countsObject -> countsObject.keys().asSequence().associateWith { key -> countsObject.optInt(key, 0) } }.orEmpty(),
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

private fun JSONArray.toContactEntities(): List<ContactEntity> = (0 until length()).map { index ->
    val obj = getJSONObject(index)
    ContactEntity(
        obj.getString("contactId"),
        obj.getString("displayName"),
        obj.getString("sortKey"),
        obj.optString("primaryPhone").takeIf { it.isNotBlank() },
        obj.optString("tagCsv"),
        obj.optBoolean("isFavorite", false),
        obj.optString("folderName").takeIf { it.isNotBlank() },
        obj.optLong("createdAt"),
        obj.optLong("updatedAt"),
        obj.optBoolean("isDeleted", false),
        obj.optLong("deletedAt").takeIf { it > 0L },
        obj.optString("photoUri").takeIf { it.isNotBlank() },
        obj.optBoolean("isBlocked", false),
        obj.optString("blockMode", if (obj.optBoolean("isBlocked", false)) "INSTANT_REJECT" else "NONE"),
        obj.optString("externalLinksJson", "[]"),
        obj.optString("phoneNumbersJson", "[]"),
    )
}

private fun JSONArray.toTagEntities(): List<TagEntity> = (0 until length()).map { i ->
    getJSONObject(i).let { TagEntity(it.getString("tagName"), it.getString("displayName"), it.optString("colorToken", "default"), it.optLong("createdAt")) }
}

private fun JSONArray.toFolderEntities(): List<FolderEntity> = (0 until length()).map { i ->
    getJSONObject(i).let {
        FolderEntity(
            it.getString("folderName"),
            it.getString("displayName"),
            it.optString("iconToken", "folder"),
            it.optString("colorToken", "blue"),
            it.optLong("createdAt"),
            it.optString("imageUri").takeIf { value -> value.isNotBlank() },
            it.optString("description").takeIf { value -> value.isNotBlank() },
            it.optInt("sortOrder", 0),
            it.optBoolean("isPinned", false),
        )
    }
}

private fun JSONArray.toFolderCrossRefs(): List<ContactFolderCrossRef> = (0 until length()).map { i ->
    getJSONObject(i).let { ContactFolderCrossRef(it.getString("contactId"), it.getString("folderName")) }
}

private fun JSONArray.toCrossRefs(): List<ContactTagCrossRef> = (0 until length()).map { i ->
    getJSONObject(i).let { ContactTagCrossRef(it.getString("contactId"), it.getString("tagName")) }
}

private fun JSONArray.toNoteEntities(): List<NoteEntity> = (0 until length()).map { i ->
    getJSONObject(i).let { NoteEntity(it.getString("noteId"), it.getString("contactId"), it.getString("body"), it.optLong("createdAt")) }
}

private fun JSONArray.toReminderEntities(): List<ReminderEntity> = (0 until length()).map { i ->
    getJSONObject(i).let { ReminderEntity(it.getString("reminderId"), it.getString("contactId"), it.getString("title"), it.optLong("dueAt"), it.optBoolean("isDone", false), it.optLong("createdAt")) }
}

private fun JSONArray.toTimelineEntities(): List<TimelineEntity> = (0 until length()).map { i ->
    getJSONObject(i).let { TimelineEntity(it.getString("timelineId"), it.getString("contactId"), it.getString("type"), it.getString("title"), it.optString("subtitle").takeIf { s -> s.isNotBlank() }, it.optLong("createdAt")) }
}

private fun String?.decodePhoneNumbersForRepository(fallbackPrimaryPhone: String?): List<ContactPhoneNumber> {
    val raw = this?.trim().takeUnless { it.isNullOrBlank() || it == "[]" } ?: return fallbackPrimaryPhone
        ?.takeIf { it.isNotBlank() }
        ?.let { listOf(ContactPhoneNumber(value = it)) }
        ?: emptyList()
    return runCatching {
        JSONArray(raw).let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { item ->
                    val value = item.optString("value").trim()
                    if (value.isBlank()) null else ContactPhoneNumber(
                        value = value,
                        type = item.optString("type", "mobile"),
                        label = item.optString("label").takeIf { label -> label.isNotBlank() },
                    )
                }
            }
        }
    }.getOrDefault(emptyList()).normalizedPhoneNumbers().ifEmpty {
        fallbackPrimaryPhone?.takeIf { it.isNotBlank() }?.let { listOf(ContactPhoneNumber(value = it)) } ?: emptyList()
    }
}

private fun escapeHtml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
