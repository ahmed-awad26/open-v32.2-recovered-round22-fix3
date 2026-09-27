package com.opencontacts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ImportExportProgressOverlay(
    progress: ImportExportProgressUiState,
    visible: Boolean,
    opacityPercent: Int = 92,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    if (!visible || progress.sessionId == 0) return
    TransferProgressOverlayContainer(
        modifier = modifier,
        opacityPercent = opacityPercent,
        onDismiss = onDismiss,
    ) {
        ProgressOverlayHeader(label = progress.label, progress = progress.progress, indeterminate = progress.indeterminate, onDismiss = onDismiss)
        ProgressIndicatorBlock(progress = progress.progress, indeterminate = progress.indeterminate)
        Text(progress.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatsGrid(stats = progress.stats)
        if (progress.warnings.isNotEmpty()) {
            Text(
                "Warnings: ${progress.warnings.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (progress.progress in 0f..0.999f && progress.label != "Idle") {
            Text(
                "You can close this panel. The task will keep running in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun BackupProgressOverlay(
    progress: TransferProgressUiState,
    visible: Boolean,
    opacityPercent: Int = 92,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    if (!visible || progress.sessionId == 0) return
    TransferProgressOverlayContainer(
        modifier = modifier,
        opacityPercent = opacityPercent,
        onDismiss = onDismiss,
    ) {
        ProgressOverlayHeader(label = progress.label, progress = progress.progress, indeterminate = progress.indeterminate, onDismiss = onDismiss)
        ProgressIndicatorBlock(progress = progress.progress, indeterminate = progress.indeterminate)
        Text(progress.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (progress.progress in 0f..0.999f && progress.label != "Idle") {
            Text(
                "You can close this panel. The backup or restore task will keep running in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TransferProgressOverlayContainer(
    modifier: Modifier = Modifier,
    opacityPercent: Int = 92,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val opacity = (opacityPercent.coerceIn(40, 100) / 100f)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ProgressOverlayHeader(
    label: String,
    progress: Float,
    indeterminate: Boolean,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            val percentText = if (indeterminate) "…" else "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%"
            Text(
                percentText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close progress panel")
            }
        }
    }
}

@Composable
private fun ProgressIndicatorBlock(progress: Float, indeterminate: Boolean) {
    if (indeterminate) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatsGrid(stats: com.opencontacts.core.model.ImportExportStats?) {
    if (stats == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsRow(
            first = "Imported" to stats.importedCount,
            second = "Merged" to stats.mergedCount,
            third = "Skipped" to stats.skippedCount,
        )
        StatsRow(
            first = "Failed" to stats.failedCount,
            second = "Folders" to stats.foldersRestored,
            third = "Tags" to stats.tagsRestored,
        )
        StatsRow(
            first = "Vaults" to stats.vaultsRestored,
            second = "Scanned" to stats.scannedCount,
            third = null,
        )
    }
}

@Composable
private fun StatsRow(
    first: Pair<String, Int>?,
    second: Pair<String, Int>?,
    third: Pair<String, Int>?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(first, second, third).forEach { item ->
            if (item == null) return@forEach
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(item.first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(item.second.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
