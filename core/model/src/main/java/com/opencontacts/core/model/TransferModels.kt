package com.opencontacts.core.model

enum class ImportConflictStrategy {
    MERGE,
    SKIP,
    REPLACE,
}

data class ImportExportStats(
    val scannedCount: Int = 0,
    val importedCount: Int = 0,
    val mergedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val foldersRestored: Int = 0,
    val tagsRestored: Int = 0,
    val vaultsRestored: Int = 0,
)

data class TransferProgressUpdate(
    val progress: Float,
    val label: String,
    val message: String,
    val indeterminate: Boolean = false,
    val stats: ImportExportStats? = null,
)

data class ImportExportExecutionReport(
    val history: ImportExportHistorySummary,
    val stats: ImportExportStats = ImportExportStats(),
    val warnings: List<String> = emptyList(),
)
