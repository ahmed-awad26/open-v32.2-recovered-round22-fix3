package com.opencontacts.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.BackupRecordSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.vaults.VaultTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun BackupRoute(
    onBack: () -> Unit,
    appViewModel: AppViewModel,
    title: String = "Backup & Export",
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val settings by appViewModel.appLockSettings.collectAsStateWithLifecycle()
    val storageMessage by appViewModel.storageMessage.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val appSettings by appViewModel.appLockSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val backupFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) Toast.makeText(context, "Backup folder selection cancelled", Toast.LENGTH_SHORT).show()
        else appViewModel.setBackupFolder(uri)
    }
    val exportFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) Toast.makeText(context, "Export folder selection cancelled", Toast.LENGTH_SHORT).show()
        else appViewModel.setExportFolder(uri)
    }
    val restoreFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) Toast.makeText(context, "Restore file selection cancelled", Toast.LENGTH_SHORT).show()
        else viewModel.restoreFromFile(uri.toString())
    }

    var overlayDismissedForSession by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(progress.sessionId) {
        if (progress.sessionId != 0) overlayDismissedForSession = null
    }

    SettingsScaffold(title = title, onBack = onBack) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SettingsSection(title = "Storage locations", subtitle = "Use the Storage Access Framework to keep folder access safe, user-controlled, and persistent.") {
                        storageMessage?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.primary)
                            SettingsSpacer()
                        }
                        LocationCard(
                            title = "Backup location",
                            currentValue = settings.backupFolderName ?: "App storage/${settings.backupPath}",
                            chooseLabel = if (settings.backupFolderUri == null) "Choose backup folder" else "Change backup folder",
                            onChoose = { backupFolderPicker.launch(null) },
                            onReset = if (settings.backupFolderUri != null) appViewModel::resetBackupFolder else null,
                        )
                        SettingsSpacer()
                        LocationCard(
                            title = "Export location",
                            currentValue = settings.exportFolderName ?: "App storage/${settings.exportPath}",
                            chooseLabel = if (settings.exportFolderUri == null) "Choose export folder" else "Change export folder",
                            onChoose = { exportFolderPicker.launch(null) },
                            onReset = if (settings.exportFolderUri != null) appViewModel::resetExportFolder else null,
                        )
                    }
                }
                item {
                    SettingsSection(title = "Backup actions", subtitle = "Create encrypted local backups, stage them to cloud handoff adapters, or restore the latest available backup.") {
                        ProgressCard(progress)
                        SettingsSpacer()
                        ActionButtonRow(
                            primaryLabel = "Create backup",
                            secondaryLabel = "Restore latest",
                            onPrimary = viewModel::createBackup,
                            onSecondary = viewModel::restoreLatest,
                        )
                        SettingsSpacer()
                        ActionButtonRow(
                            primaryLabel = "Restore from file",
                            secondaryLabel = "Stage Google Drive",
                            onPrimary = { restoreFilePicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                            onSecondary = viewModel::stageGoogleDrive,
                        )
                        SettingsSpacer()
                        ActionButtonRow(
                            primaryLabel = "Stage OneDrive",
                            secondaryLabel = "Open backup folder",
                            onPrimary = viewModel::stageOneDrive,
                            onSecondary = { backupFolderPicker.launch(null) },
                        )
                    }
                }
                item {
                    SettingsSection(title = "Backup history", subtitle = "Most recent local and staged backup records for the active vault.") {
                        if (records.isEmpty()) {
                            Text("No backup records yet.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                records.forEach { record -> BackupRecordCard(record) }
                            }
                        }
                    }
                }
            }

            BackupProgressOverlay(
                progress = progress,
                visible = progress.label != "Idle" && overlayDismissedForSession != progress.sessionId,
                opacityPercent = appSettings.transferProgressOverlayOpacity,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                onDismiss = { overlayDismissedForSession = progress.sessionId },
            )
        }
    }

}

@Composable
private fun LocationCard(
    title: String,
    currentValue: String,
    chooseLabel: String,
    onChoose: () -> Unit,
    onReset: (() -> Unit)?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(currentValue, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onChoose) { Text(chooseLabel) }
            onReset?.let { TextButton(onClick = it) { Text("Reset to app storage") } }
        }
    }
}

@Composable
private fun ActionButtonRow(
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Button(modifier = Modifier.weight(1f), onClick = onPrimary) { Text(primaryLabel) }
        androidx.compose.material3.OutlinedButton(modifier = Modifier.weight(1f), onClick = onSecondary) { Text(secondaryLabel) }
    }
}

@Composable
private fun ProgressCard(progress: TransferProgressUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(progress.label, style = MaterialTheme.typography.titleMedium)
                Text(if (progress.indeterminate) "…" else "${(progress.progress.coerceIn(0f, 1f) * 100f).toInt()}%", color = MaterialTheme.colorScheme.primary)
            }
            if (progress.indeterminate) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            Text(progress.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (progress.progress in 0f..0.999f && progress.label != "Idle") {
                Text("The task keeps running even if you leave this page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BackupRecordCard(record: BackupRecordSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.status, style = MaterialTheme.typography.titleMedium)
            Text("Provider: ${record.provider}")
            Text("File: ${record.filePath}")
            Text("Size: ${record.fileSizeBytes} bytes")
            Text(formatTimestamp(record.createdAt), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val vaultSessionManager: VaultSessionManager,
    private val transferRepository: VaultTransferRepository,
    private val coordinator: TransferTaskCoordinator,
) : ViewModel() {
    val records: StateFlow<List<BackupRecordSummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, locked -> vaultId to locked }
        .flatMapLatest { (vaultId, locked) ->
            if (vaultId == null || locked) flowOf(emptyList()) else transferRepository.observeBackupRecords(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val progress: StateFlow<TransferProgressUiState> = coordinator.backupProgress

    private fun activeVaultId(): String? = vaultSessionManager.activeVaultId.value

    fun createBackup() { activeVaultId()?.let(coordinator::createBackup) }
    fun restoreLatest() { activeVaultId()?.let(coordinator::restoreLatest) }
    fun restoreFromFile(uriString: String) { activeVaultId()?.let { coordinator.restoreFromFile(it, uriString) } }
    fun stageGoogleDrive() { activeVaultId()?.let(coordinator::stageGoogleDrive) }
    fun stageOneDrive() { activeVaultId()?.let(coordinator::stageOneDrive) }
}

private fun formatTimestamp(value: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))
