package com.opencontacts.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.ui.ReliableOutlinedTextField
import com.opencontacts.core.model.ImportConflictStrategy
import com.opencontacts.core.model.ImportExportHistorySummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.vaults.VaultTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun ImportExportRoute(
    onBack: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    appViewModel: AppViewModel,
    viewModel: ImportExportViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val appSettings by appViewModel.appLockSettings.collectAsStateWithLifecycle()
    val strategy by viewModel.strategy.collectAsStateWithLifecycle()
    val selectedVaultId by viewModel.selectedVaultId.collectAsStateWithLifecycle()
    val vaults by appViewModel.vaults.collectAsStateWithLifecycle()
    val activeVaultId by appViewModel.activeVaultId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings by appViewModel.appLockSettings.collectAsStateWithLifecycle()
    val mainTransferRootDisplay = remember(settings.exportPath) { displayTransferRoot(settings.exportPath) }
    var showRootPathDialog by rememberSaveable { mutableStateOf(false) }
    var rootPathDraft by rememberSaveable { mutableStateOf(transferRootInputValue(settings.exportPath)) }

    LaunchedEffect(settings.exportPath) {
        rootPathDraft = transferRootInputValue(settings.exportPath)
    }

    LaunchedEffect(activeVaultId, vaults, selectedVaultId) {
        val hasSelected = selectedVaultId != null && vaults.any { it.id == selectedVaultId }
        if (!hasSelected) {
            val preferred = activeVaultId ?: vaults.firstOrNull()?.id
            if (preferred != null) viewModel.setSelectedVault(preferred)
        }
    }

    val packagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPackageFromUri(context, uri)
    }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importZipFromUri(context, uri)
    }
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importJsonFromUri(context, uri)
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importCsvFromUri(context, uri)
    }
    val vcfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importVcfFromUri(context, uri)
    }

    var overlayDismissedForSession by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(progress.sessionId) {
        if (progress.sessionId != 0) overlayDismissedForSession = null
    }

    SettingsScaffold(title = "Import & Export", onBack = onBack) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SettingsSection(
                        title = "Vault selection",
                        subtitle = "Choose the vault to export from or import into. Imports always go to the selected target vault.",
                    ) {
                        if (vaults.isEmpty()) {
                            Text("No vaults available.")
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                vaults.forEach { vault ->
                                    FilterChip(
                                        selected = selectedVaultId == vault.id,
                                        onClick = { viewModel.setSelectedVault(vault.id) },
                                        label = {
                                            val suffix = buildString {
                                                if (vault.id == activeVaultId) append(" • active")
                                                if (vault.isDefault) append(" • default")
                                            }
                                            Text(vault.displayName + suffix)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    SettingsSection(
                        title = "Import strategy",
                        subtitle = "Choose how duplicates are handled when importing exported files or large datasets.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            ImportConflictStrategy.values().forEach { option ->
                                FilterChip(
                                    selected = strategy == option,
                                    onClick = { viewModel.setStrategy(option) },
                                    label = { Text(option.name.lowercase().replaceFirstChar { it.titlecase() }) },
                                )
                            }
                        }
                        Text(
                            when (strategy) {
                                ImportConflictStrategy.MERGE -> "Merge updates matching contacts while preserving existing extras."
                                ImportConflictStrategy.SKIP -> "Skip matching contacts and only add new ones."
                                ImportConflictStrategy.REPLACE -> "Replace matching contact details and related notes/reminders/timeline."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    SettingsSection(
                        title = "Main transfer path",
                        subtitle = "Choose the main folder name used under Downloads/AW. Every transfer format keeps its own subfolder inside this root.",
                    ) {
                        SettingsValueRow(
                            title = "Current root",
                            subtitle = "Formats such as VCF, CSV, JSON, ZIP, and packages will keep using their own subfolders inside this root.",
                            value = mainTransferRootDisplay,
                            onClick = {
                                rootPathDraft = transferRootInputValue(settings.exportPath)
                                showRootPathDialog = true
                            },
                        )
                        ActionGridRow(
                            first = ActionSpec(
                                title = "Edit root path",
                                subtitle = "Change the main export/import root under Downloads/AW",
                                icon = Icons.Default.Description,
                                onClick = {
                                    rootPathDraft = transferRootInputValue(settings.exportPath)
                                    showRootPathDialog = true
                                },
                            ),
                            second = ActionSpec(
                                title = "Reset root",
                                subtitle = "Return to the default Downloads/AW layout",
                                icon = Icons.Default.FileDownload,
                                onClick = { appViewModel.setExportPath("") },
                            ),
                        )
                    }
                }
                item {
                    SettingsSection(
                        title = "Transfer formats",
                        subtitle = "Each format keeps its own dedicated folder inside $mainTransferRootDisplay so imports and exports stay tidy.",
                    ) {
                        TransferFormatRow(
                            title = "VCF",
                            subtitle = "Standard contact card format.",
                            importSpec = ActionSpec("Import VCF", "From $mainTransferRootDisplay/VCF or a picked file", Icons.Default.Download) { vcfPicker.launch(arrayOf("text/x-vcard", "text/vcard", "*/*")) },
                            exportSpec = ActionSpec("Export VCF", "Save to $mainTransferRootDisplay/VCF", Icons.Default.FileUpload) { viewModel.exportVcf() },
                        )
                        SettingsSpacer()
                        TransferFormatRow(
                            title = "CSV",
                            subtitle = "Spreadsheet-friendly contacts table.",
                            importSpec = ActionSpec("Import CSV", "From $mainTransferRootDisplay/CSV or a picked file", Icons.Default.FileDownload) { csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                            exportSpec = ActionSpec("Export CSV", "Save to $mainTransferRootDisplay/CSV", Icons.Default.Upload) { viewModel.exportCsv() },
                        )
                        SettingsSpacer()
                        TransferFormatRow(
                            title = "JSON",
                            subtitle = "Readable structured export with folders, tags, notes, reminders, and timeline.",
                            importSpec = ActionSpec("Import JSON", "From $mainTransferRootDisplay/JSON or a picked file", Icons.Default.Description) { jsonPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            exportSpec = ActionSpec("Export JSON", "Save to $mainTransferRootDisplay/JSON", Icons.Default.FileUpload) { viewModel.exportJson() },
                        )
                        SettingsSpacer()
                        TransferFormatRow(
                            title = "Phone contacts",
                            subtitle = "Move data between the selected vault and system contacts.",
                            importSpec = ActionSpec("Import phone", "Pull system contacts into the selected vault", Icons.Default.PhoneAndroid) { viewModel.importFromPhone() },
                            exportSpec = ActionSpec("Export phone", "Copy selected vault contacts to system contacts", Icons.Default.Upload) { viewModel.exportToPhone() },
                        )
                        SettingsSpacer()
                        TransferFormatRow(
                            title = "Excel",
                            subtitle = "Excel-compatible workbook export kept in its own folder.",
                            importSpec = ActionSpec("Excel note", "Current system keeps Excel as export-only", Icons.Default.Description, enabled = false) { },
                            exportSpec = ActionSpec("Export Excel", "Save to $mainTransferRootDisplay/Excel", Icons.Default.Upload) { viewModel.exportExcel() },
                        )
                        SettingsSpacer()
                        TransferFormatRow(
                            title = "Internal package",
                            subtitle = "Best round-trip package for the app's full data model.",
                            importSpec = ActionSpec("Import package", "From $mainTransferRootDisplay/InternalPackage or a picked file", Icons.Default.FileDownload) { packagePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                            exportSpec = ActionSpec("Export package", "Save to $mainTransferRootDisplay/InternalPackage", Icons.Default.FileUpload) { viewModel.exportPackage() },
                        )
                        SettingsSpacer()
                        TransferFormatRow(
                            title = "ZIP bundle",
                            subtitle = "Portable archive with photos, folder images, tags, and stable relationships.",
                            importSpec = ActionSpec("Import ZIP", "From $mainTransferRootDisplay/ZIP or a picked file", Icons.Default.FileDownload) { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                            exportSpec = ActionSpec("Export ZIP", "Save to $mainTransferRootDisplay/ZIP", Icons.Default.FileUpload) { viewModel.exportZipBundle() },
                        )
                    }
                }
                item {
                    SettingsSection(title = "Recent activity", subtitle = "History shown here belongs to the vault currently selected above.") {
                        if (history.isEmpty()) {
                            Text("No import or export records yet.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                history.forEach { item -> HistoryCard(item) }
                            }
                        }
                    }
                }
                item {
                    SettingsSection(
                        title = "Backup & Restore",
                        subtitle = "Open the dedicated backup screen for local backups, restore operations, history, and cloud staging.",
                    ) {
                        ActionGridRow(
                            first = ActionSpec(
                                title = "Open backup & restore",
                                subtitle = "Create backups, restore files, and review backup history",
                                icon = Icons.Default.Description,
                                onClick = onOpenBackupRestore,
                            ),
                            second = ActionSpec(
                                title = "Open backup folder",
                                subtitle = "Backups continue using the current backup destination settings",
                                icon = Icons.Default.FileDownload,
                                enabled = false,
                                onClick = {},
                            ),
                            hideSecond = true,
                        )
                    }
                }
            }

            ImportExportProgressOverlay(
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

    if (showRootPathDialog) {
        AlertDialog(
            onDismissRequest = { showRootPathDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    appViewModel.setExportPath(rootPathDraft)
                    showRootPathDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRootPathDialog = false }) { Text("Cancel") }
            },
            title = { Text("Main transfer path") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter one clean folder name. All formats will continue to use their own folders inside it.")
                    ReliableOutlinedTextField(
                        value = rootPathDraft,
                        onValueChange = { rootPathDraft = sanitizeTransferRootInput(it) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Folder name under Downloads/AW") },
                        supportingText = { Text("Leave it empty to use the default AW root.") },
                    )
                }
            },
        )
    }
}

private data class ActionSpec(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun ActionGridRow(first: ActionSpec, second: ActionSpec, hideSecond: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ActionTile(first, Modifier.weight(1f))
        if (!hideSecond) ActionTile(second, Modifier.weight(1f))
    }
}

@Composable
private fun TransferFormatRow(
    title: String,
    subtitle: String,
    importSpec: ActionSpec,
    exportSpec: ActionSpec,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ActionGridRow(importSpec, exportSpec)
    }
}

@Composable
private fun ActionTile(spec: ActionSpec, modifier: Modifier) {
    Card(modifier = modifier.heightIn(min = 144.dp), onClick = spec.onClick, enabled = spec.enabled) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Icon(
                spec.icon,
                contentDescription = null,
                tint = if (spec.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                spec.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (spec.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                spec.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressSurface(progress: ImportExportProgressUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(progress.label, style = MaterialTheme.typography.titleMedium)
                Text(if (progress.indeterminate) "…" else "${(progress.progress.coerceIn(0f, 1f) * 100f).toInt()}%", color = MaterialTheme.colorScheme.primary)
            }
            if (progress.indeterminate) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            Text(progress.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            progress.stats?.let { stats ->
                Text(
                    "Imported ${stats.importedCount} • Merged ${stats.mergedCount} • Skipped ${stats.skippedCount} • Failed ${stats.failedCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Folders ${stats.foldersRestored} • Tags ${stats.tagsRestored} • Vaults ${stats.vaultsRestored} • Scanned ${stats.scannedCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (progress.warnings.isNotEmpty()) {
                Text("Warnings: ${progress.warnings.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            if (progress.progress in 0f..0.999f && progress.label != "Idle") {
                Text("The task keeps running even if you leave this page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HistoryCard(item: ImportExportHistorySummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.operationType, style = MaterialTheme.typography.titleMedium)
            Text(item.status)
            Text("Items: ${item.itemCount}")
            Text(item.filePath)
            Text(formatTimestamp(item.createdAt), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val vaultSessionManager: VaultSessionManager,
    private val transferRepository: VaultTransferRepository,
    private val coordinator: TransferTaskCoordinator,
    private val appLockRepository: com.opencontacts.core.crypto.AppLockRepository,
) : ViewModel() {
    private val _selectedVaultId = MutableStateFlow<String?>(null)
    val selectedVaultId: StateFlow<String?> = _selectedVaultId

    val history: StateFlow<List<ImportExportHistorySummary>> = combine(
        _selectedVaultId,
        vaultSessionManager.activeVaultId,
        vaultSessionManager.isLocked,
    ) { selectedVaultId, activeVaultId, locked ->
        Triple(selectedVaultId ?: activeVaultId, activeVaultId, locked)
    }.flatMapLatest { (targetVaultId, _, locked) ->
        if (targetVaultId == null || locked) flowOf(emptyList()) else transferRepository.observeImportExportHistory(targetVaultId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val progress: StateFlow<ImportExportProgressUiState> = coordinator.importExportProgress
    private val _strategy = MutableStateFlow(ImportConflictStrategy.MERGE)
    val strategy: StateFlow<ImportConflictStrategy> = _strategy

    fun setSelectedVault(vaultId: String) {
        _selectedVaultId.value = vaultId
    }

    private fun resolvedVaultId(): String? = _selectedVaultId.value ?: vaultSessionManager.activeVaultId.value
    fun setStrategy(value: ImportConflictStrategy) { _strategy.value = value }

    fun exportZipBundle() { resolvedVaultId()?.let(coordinator::exportZipBundle) }
    fun exportPackage() { resolvedVaultId()?.let(coordinator::exportPackage) }
    fun importPackage() { resolvedVaultId()?.let { coordinator.importPackage(it, strategy.value) } }
    fun exportJson() { resolvedVaultId()?.let(coordinator::exportJson) }
    fun importJson() { resolvedVaultId()?.let { coordinator.importJson(it, strategy.value) } }
    fun exportCsv() { resolvedVaultId()?.let(coordinator::exportCsv) }
    fun exportVcf() { resolvedVaultId()?.let(coordinator::exportVcf) }
    fun exportExcel() { resolvedVaultId()?.let(coordinator::exportExcel) }
    fun importFromPhone() { resolvedVaultId()?.let { coordinator.importFromPhone(it, strategy.value) } }
    fun exportToPhone() { resolvedVaultId()?.let(coordinator::exportToPhone) }

    fun importZipFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                copyUriIntoImports(context, uri, "ZIP", "contacts_bundle.zip", appLockRepository.settings.first().exportPath)
                resolvedVaultId()?.let { coordinator.importZipBundle(it, strategy.value) }
            }
        }
    }

    fun importPackageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                copyUriIntoImports(context, uri, "InternalPackage", "contacts.ocpkg", appLockRepository.settings.first().exportPath)
                importPackage()
            }
        }
    }

    fun importJsonFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                copyUriIntoImports(context, uri, "JSON", "contacts.json", appLockRepository.settings.first().exportPath)
                resolvedVaultId()?.let { coordinator.importJson(it, strategy.value) }
            }
        }
    }

    fun importCsvFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                copyUriIntoImports(context, uri, "CSV", "contacts.csv", appLockRepository.settings.first().exportPath)
                resolvedVaultId()?.let { coordinator.importCsv(it, strategy.value) }
            }
        }
    }

    fun importVcfFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                copyUriIntoImports(context, uri, "VCF", "contacts.vcf", appLockRepository.settings.first().exportPath)
                resolvedVaultId()?.let { coordinator.importVcf(it, strategy.value) }
            }
        }
    }
}

private fun formatTimestamp(value: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))

private fun normalizeTransferRoot(path: String): String {
    val cleaned = path.trim().replace('\\', '/').trim('/')
    return when {
        cleaned.isBlank() -> ""
        cleaned.equals("exports", ignoreCase = true) -> ""
        cleaned.equals("vault_exports", ignoreCase = true) -> ""
        cleaned.equals("AW", ignoreCase = true) -> ""
        else -> cleaned
    }
}

private fun transferRootInputValue(path: String): String = normalizeTransferRoot(path)

private fun displayTransferRoot(path: String): String {
    val normalized = normalizeTransferRoot(path)
    return if (normalized.isBlank()) "Downloads/AW" else "Downloads/AW/$normalized"
}

private fun sanitizeTransferRootInput(input: String): String =
    input.replace('\\', '/').replace(Regex("""[^A-Za-z0-9 _/-]"""), "").trimStart('/').take(48)

private suspend fun copyUriIntoImports(context: Context, uri: Uri, category: String, fileName: String, exportPath: String) {
    val appDir = File(context.filesDir, "vault_imports/$category").apply { mkdirs() }
    val appTarget = File(appDir, fileName)
    val downloadsRoot = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
    val baseDir = normalizeTransferRoot(exportPath).takeIf { it.isNotBlank() }?.let { File(downloadsRoot, "AW/$it") } ?: File(downloadsRoot, "AW")
    val formatDir = File(baseDir, category).apply { mkdirs() }
    val awTarget = File(formatDir, fileName)
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to open selected file")
    appTarget.writeBytes(bytes)
    runCatching { awTarget.writeBytes(bytes) }
}
