package com.opencontacts.domain.vaults

import com.opencontacts.core.model.BackupRecordSummary
import com.opencontacts.core.model.ImportConflictStrategy
import com.opencontacts.core.model.ImportExportExecutionReport
import com.opencontacts.core.model.ImportExportHistorySummary
import com.opencontacts.core.model.TransferProgressUpdate
import kotlinx.coroutines.flow.Flow

interface VaultTransferRepository {
    fun observeBackupRecords(vaultId: String): Flow<List<BackupRecordSummary>>
    fun observeImportExportHistory(vaultId: String): Flow<List<ImportExportHistorySummary>>

    suspend fun createLocalBackup(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): BackupRecordSummary

    suspend fun restoreLatestLocalBackup(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): Boolean

    suspend fun restoreBackupFromUri(
        vaultId: String,
        uriString: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): Boolean

    suspend fun stageLatestBackupToGoogleDrive(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): BackupRecordSummary

    suspend fun stageLatestBackupToOneDrive(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): BackupRecordSummary

    suspend fun exportContactsPackage(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun importLatestContactsPackage(
        vaultId: String,
        strategy: ImportConflictStrategy = ImportConflictStrategy.MERGE,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun exportContactsZipBundle(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun importLatestContactsZipBundle(
        vaultId: String,
        strategy: ImportConflictStrategy = ImportConflictStrategy.MERGE,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun exportContactsJson(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun importLatestContactsJson(
        vaultId: String,
        strategy: ImportConflictStrategy = ImportConflictStrategy.MERGE,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun exportContactsCsv(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun importLatestContactsCsv(
        vaultId: String,
        strategy: ImportConflictStrategy = ImportConflictStrategy.MERGE,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun exportContactsVcf(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun exportContactsExcel(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun importLatestContactsVcf(
        vaultId: String,
        strategy: ImportConflictStrategy = ImportConflictStrategy.MERGE,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun importFromPhoneContacts(
        vaultId: String,
        strategy: ImportConflictStrategy = ImportConflictStrategy.MERGE,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport

    suspend fun exportAllContactsToPhone(
        vaultId: String,
        reportProgress: (TransferProgressUpdate) -> Unit = {},
    ): ImportExportExecutionReport
}
