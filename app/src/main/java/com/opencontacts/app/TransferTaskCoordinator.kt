package com.opencontacts.app

import com.opencontacts.core.model.BackupRecordSummary
import com.opencontacts.core.model.ImportConflictStrategy
import com.opencontacts.core.model.ImportExportExecutionReport
import com.opencontacts.core.model.ImportExportStats
import com.opencontacts.core.model.TransferProgressUpdate
import com.opencontacts.domain.vaults.VaultTransferRepository
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TransferTaskCoordinator @Inject constructor(
    private val transferRepository: VaultTransferRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val importExportMutex = Mutex()
    private val backupMutex = Mutex()
    private val importExportSessionCounter = AtomicInteger(0)
    private val backupSessionCounter = AtomicInteger(0)

    private val _importExportProgress = MutableStateFlow(ImportExportProgressUiState.idle())
    val importExportProgress: StateFlow<ImportExportProgressUiState> = _importExportProgress

    private val _backupProgress = MutableStateFlow(TransferProgressUiState.idle("Backup is idle"))
    val backupProgress: StateFlow<TransferProgressUiState> = _backupProgress

    fun exportPackage(vaultId: String) = launchImportExport { progress -> transferRepository.exportContactsPackage(vaultId, progress) }
    fun importPackage(vaultId: String, strategy: ImportConflictStrategy) = launchImportExport { progress -> transferRepository.importLatestContactsPackage(vaultId, strategy, progress) }
    fun exportZipBundle(vaultId: String) = launchImportExport { progress -> transferRepository.exportContactsZipBundle(vaultId, progress) }
    fun importZipBundle(vaultId: String, strategy: ImportConflictStrategy) = launchImportExport { progress -> transferRepository.importLatestContactsZipBundle(vaultId, strategy, progress) }
    fun exportJson(vaultId: String) = launchImportExport { progress -> transferRepository.exportContactsJson(vaultId, progress) }
    fun importJson(vaultId: String, strategy: ImportConflictStrategy) = launchImportExport { progress -> transferRepository.importLatestContactsJson(vaultId, strategy, progress) }
    fun exportCsv(vaultId: String) = launchImportExport { progress -> transferRepository.exportContactsCsv(vaultId, progress) }
    fun exportVcf(vaultId: String) = launchImportExport { progress -> transferRepository.exportContactsVcf(vaultId, progress) }
    fun exportExcel(vaultId: String) = launchImportExport { progress -> transferRepository.exportContactsExcel(vaultId, progress) }
    fun importFromPhone(vaultId: String, strategy: ImportConflictStrategy) = launchImportExport { progress -> transferRepository.importFromPhoneContacts(vaultId, strategy, progress) }
    fun exportToPhone(vaultId: String) = launchImportExport { progress -> transferRepository.exportAllContactsToPhone(vaultId, progress) }
    fun importCsv(vaultId: String, strategy: ImportConflictStrategy) = launchImportExport { progress -> transferRepository.importLatestContactsCsv(vaultId, strategy, progress) }
    fun importVcf(vaultId: String, strategy: ImportConflictStrategy) = launchImportExport { progress -> transferRepository.importLatestContactsVcf(vaultId, strategy, progress) }

    fun createBackup(vaultId: String) = launchBackup { progress -> transferRepository.createLocalBackup(vaultId, progress) }
    fun restoreLatest(vaultId: String) = launchBackup { progress -> transferRepository.restoreLatestLocalBackup(vaultId, progress) }
    fun restoreFromFile(vaultId: String, uriString: String) = launchBackup { progress -> transferRepository.restoreBackupFromUri(vaultId, uriString, progress) }
    fun stageGoogleDrive(vaultId: String) = launchBackup { progress -> transferRepository.stageLatestBackupToGoogleDrive(vaultId, progress) }
    fun stageOneDrive(vaultId: String) = launchBackup { progress -> transferRepository.stageLatestBackupToOneDrive(vaultId, progress) }

    private fun launchImportExport(
        block: suspend ((TransferProgressUpdate) -> Unit) -> ImportExportExecutionReport,
    ) {
        scope.launch {
            importExportMutex.withLock {
                runCatching {
                    val sessionId = importExportSessionCounter.incrementAndGet()
                    _importExportProgress.value = ImportExportProgressUiState(
                        sessionId = sessionId,
                        indeterminate = false,
                        progress = 0.02f,
                        label = "Queued",
                        message = "Task queued and running even if you leave this screen.",
                    )
                    val result = block(::emitImportExportProgress)
                    _importExportProgress.value = ImportExportProgressUiState(
                        sessionId = _importExportProgress.value.sessionId,
                        indeterminate = false,
                        progress = 1f,
                        label = result.history.operationType,
                        message = buildSummaryMessage(result.stats, result.warnings),
                        stats = result.stats,
                        warnings = result.warnings,
                    )
                }.onFailure { error ->
                    _importExportProgress.value = ImportExportProgressUiState.failed(
                        message = error.message ?: "Import/export failed",
                        sessionId = _importExportProgress.value.sessionId,
                        stats = _importExportProgress.value.stats,
                        warnings = _importExportProgress.value.warnings,
                    )
                }
            }
        }
    }

    private fun emitImportExportProgress(update: TransferProgressUpdate) {
        _importExportProgress.value = ImportExportProgressUiState(
            sessionId = _importExportProgress.value.sessionId,
            indeterminate = update.indeterminate,
            progress = update.progress,
            label = update.label,
            message = update.message,
            stats = update.stats ?: _importExportProgress.value.stats,
            warnings = _importExportProgress.value.warnings,
        )
    }

    private fun buildSummaryMessage(stats: ImportExportStats, warnings: List<String>): String {
        val parts = mutableListOf<String>()
        if (stats.importedCount > 0) parts += "Imported ${stats.importedCount}"
        if (stats.mergedCount > 0) parts += "Merged ${stats.mergedCount}"
        if (stats.skippedCount > 0) parts += "Skipped ${stats.skippedCount}"
        if (stats.failedCount > 0) parts += "Failed ${stats.failedCount}"
        if (stats.foldersRestored > 0) parts += "Folders ${stats.foldersRestored}"
        if (stats.tagsRestored > 0) parts += "Tags ${stats.tagsRestored}"
        if (stats.vaultsRestored > 0) parts += "Vault ${stats.vaultsRestored}"
        if (parts.isEmpty()) parts += "Task completed"
        return parts.joinToString(" • ") + if (warnings.isNotEmpty()) " • ${warnings.size} warning(s)" else ""
    }

    private fun launchBackup(
        block: suspend ((TransferProgressUpdate) -> Unit) -> Any,
    ) {
        scope.launch {
            backupMutex.withLock {
                runCatching {
                    val sessionId = backupSessionCounter.incrementAndGet()
                    _backupProgress.value = TransferProgressUiState(sessionId = sessionId, indeterminate = false, progress = 0.02f, label = "Queued", message = "Task queued and running even if you leave this screen.")
                    val result = block(::emitBackupProgress)
                    val finalMessage = when (result) {
                        is BackupRecordSummary -> "${result.status} • ${result.filePath}"
                        is Boolean -> if (result) "Restore completed successfully" else "No backup file was found"
                        else -> result.toString()
                    }
                    _backupProgress.value = TransferProgressUiState(sessionId = _backupProgress.value.sessionId, indeterminate = false, progress = 1f, label = "Completed", message = finalMessage)
                }.onFailure { error ->
                    _backupProgress.value = TransferProgressUiState.failed(error.message ?: "Backup flow failed", _backupProgress.value.sessionId)
                }
            }
        }
    }

    private fun emitBackupProgress(update: TransferProgressUpdate) {
        _backupProgress.value = TransferProgressUiState(
            sessionId = _backupProgress.value.sessionId,
            indeterminate = update.indeterminate,
            progress = update.progress,
            label = update.label,
            message = update.message,
        )
    }
}

data class TransferProgressUiState(
    val sessionId: Int,
    val indeterminate: Boolean,
    val progress: Float,
    val label: String,
    val message: String,
) {
    companion object {
        fun idle(message: String) = TransferProgressUiState(sessionId = 0, indeterminate = false, progress = 0f, label = "Idle", message = message)
        fun failed(message: String, sessionId: Int) = TransferProgressUiState(sessionId = sessionId, indeterminate = false, progress = 1f, label = "Failed", message = message)
    }
}

data class ImportExportProgressUiState(
    val sessionId: Int,
    val indeterminate: Boolean,
    val progress: Float,
    val label: String,
    val message: String,
    val stats: ImportExportStats? = null,
    val warnings: List<String> = emptyList(),
) {
    companion object {
        fun idle() = ImportExportProgressUiState(sessionId = 0, indeterminate = false, progress = 0f, label = "Idle", message = "No transfer is running.")
        fun failed(message: String, sessionId: Int, stats: ImportExportStats? = null, warnings: List<String> = emptyList()) =
            ImportExportProgressUiState(sessionId = sessionId, indeterminate = false, progress = 1f, label = "Failed", message = message, stats = stats, warnings = warnings)
    }
}
