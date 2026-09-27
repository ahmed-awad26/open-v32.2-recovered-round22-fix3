package com.opencontacts.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencontacts.core.crypto.AppLockSettings
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.allPhoneNumbers
import com.opencontacts.core.ui.localization.localizedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standalone SMS Composer screen.
 *
 * Features:
 * - Type phone number or choose a contact/number from [contacts]
 * - Compose and send SMS
 * - Delivery report tracking (when permission + settings allow)
 * - Flash SMS: user-visible disclaimer that Class 0 is NOT supported via public API
 * - Dual SIM chooser
 * - Quick templates from settings
 * - Draft saves on rotation via rememberSaveable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsComposerRoute(
    onBack: () -> Unit,
    settings: AppLockSettings,
    contacts: List<ContactSummary> = emptyList(),
    prefillNumber: String = "",
    prefillMessage: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deliveryStates by SmsDeliveryTracker.states.collectAsStateWithLifecycle()

    // --- Permission state ---
    var hasSmsPerm by remember { mutableStateOf(hasSmsPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasSmsPerm = granted
    }

    // --- Draft state (survives rotation) ---
    var recipientNumber by rememberSaveable { mutableStateOf(prefillNumber) }
    var messageText by rememberSaveable { mutableStateOf(prefillMessage) }
    var contactPickerOpen by remember { mutableStateOf(false) }
    var simPickerOpen by remember { mutableStateOf(false) }
    var flashInfoOpen by remember { mutableStateOf(false) }
    var selectedSimId by rememberSaveable { mutableStateOf(-1) }

    val sims = remember(context) { availableSmsSims(context) }
    val selectedSimLabel = sims.firstOrNull { it.subscriptionId == selectedSimId }?.displayName
        ?: if (sims.size > 1) "Choose SIM" else "Default SIM"

    val templates = listOf(settings.quickSmsTemplate1, settings.quickSmsTemplate2, settings.quickSmsTemplate3)
        .filter { it.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(localizedText("Send SMS")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = localizedText("Back"))
                    }
                },
                actions = {
                    if (settings.flashSmsAttempt) {
                        IconButton(onClick = { flashInfoOpen = true }) {
                            Icon(Icons.Default.FlashOn, contentDescription = localizedText("Flash SMS info"), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Permission banner ──────────────────────────────────────────
            if (!hasSmsPerm) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SEND_SMS permission required", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Grant permission to send messages from this app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        TextButton(onClick = { permLauncher.launch(Manifest.permission.SEND_SMS) }) {
                            Text("Grant")
                        }
                    }
                }
            }

            // ── Recipient field ────────────────────────────────────────────
            OutlinedTextField(
                value = recipientNumber,
                onValueChange = { recipientNumber = it },
                label = { Text(localizedText("Phone number")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (recipientNumber.isNotBlank()) {
                            IconButton(onClick = { recipientNumber = "" }) {
                                Icon(Icons.Default.Close, contentDescription = localizedText("Clear"))
                            }
                        }
                        if (contacts.isNotEmpty()) {
                            TextButton(onClick = { contactPickerOpen = true }) {
                                Text(localizedText("Contact"))
                            }
                        }
                    }
                },
            )

            // ── SIM chooser (only when multiple SIMs available) ────────────
            if (sims.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(localizedText("Send from:"), style = MaterialTheme.typography.bodyMedium)
                    sims.forEach { sim ->
                        FilterChip(
                            selected = sim.subscriptionId == selectedSimId,
                            onClick = { selectedSimId = sim.subscriptionId },
                            label = { Text(sim.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }

            // ── Message body ───────────────────────────────────────────────
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text(localizedText("Message")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                maxLines = 6,
            )

            // ── Character count ────────────────────────────────────────────
            val charCount = messageText.length
            val smsCount = ((charCount - 1) / 160 + 1).coerceAtLeast(1)
            Text(
                text = "$charCount chars · $smsCount SMS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Quick templates ────────────────────────────────────────────
            if (templates.isNotEmpty()) {
                Text(localizedText("Quick templates"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { tpl ->
                        AssistChip(
                            onClick = { messageText = tpl },
                            label = { Text(tpl, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp)) },
                        )
                    }
                }
            }

            // ── Send button ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.Button(
                    onClick = {
                        if (!hasSmsPerm) {
                            permLauncher.launch(Manifest.permission.SEND_SMS)
                            return@Button
                        }
                        if (recipientNumber.isBlank() || messageText.isBlank()) return@Button
                        scope.launch(Dispatchers.IO) {
                            sendSms(
                                context = context,
                                number = recipientNumber.trim(),
                                message = messageText,
                                trackDelivery = settings.smsDeliveryReports,
                                subscriptionId = selectedSimId,
                                flashAttempt = settings.flashSmsAttempt,
                            )
                        }
                        messageText = ""
                    },
                    enabled = recipientNumber.isNotBlank() && messageText.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(localizedText("Send"))
                }
            }

            // ── Delivery history (this session) ────────────────────────────
            if (settings.smsDeliveryReports && deliveryStates.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(localizedText("This session"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                deliveryStates.take(10).forEach { ds ->
                    SmsDeliveryRow(state = ds)
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }

    // ── Contact picker dialog ──────────────────────────────────────────────
    if (contactPickerOpen) {
        AlertDialog(
            onDismissRequest = { contactPickerOpen = false },
            title = { Text(localizedText("Choose contact")) },
            text = {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    contacts.forEach { contact ->
                        contact.allPhoneNumbers().forEach { phone ->
                            item(key = "${contact.id}_${phone.value}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            recipientNumber = phone.value
                                            contactPickerOpen = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(contact.displayName.take(1).uppercase(), style = MaterialTheme.typography.labelLarge)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(phone.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { contactPickerOpen = false }) { Text(localizedText("Cancel")) }
            },
        )
    }

    // ── Flash SMS info dialog ──────────────────────────────────────────────
    if (flashInfoOpen) {
        AlertDialog(
            onDismissRequest = { flashInfoOpen = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text(localizedText("Flash SMS / Class 0 — Platform Limitations")) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        flashSmsSupportReason(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Your message will be sent as a normal SMS regardless of this setting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { flashInfoOpen = false }) { Text(localizedText("Understood")) }
            },
        )
    }
}

@Composable
private fun SmsDeliveryRow(state: SmsDeliveryState) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val statusColor = when (state.status) {
        SmsStatus.DELIVERED -> Color(0xFF16A34A)
        SmsStatus.SENT -> MaterialTheme.colorScheme.primary
        SmsStatus.FAILED -> MaterialTheme.colorScheme.error
        SmsStatus.SENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        SmsStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusIcon = when (state.status) {
        SmsStatus.DELIVERED -> Icons.Default.CheckCircle
        SmsStatus.SENT -> Icons.Default.Check
        SmsStatus.FAILED -> Icons.Default.Error
        SmsStatus.SENDING -> null
        SmsStatus.IDLE -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.status == SmsStatus.SENDING) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                statusIcon?.let {
                    Icon(it, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(state.number, style = MaterialTheme.typography.labelMedium)
                Text(
                    state.message.take(60) + if (state.message.length > 60) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                state.errorReason?.let { err ->
                    Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    state.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
                if (state.sentAt > 0) {
                    Text(dateFormat.format(Date(state.sentAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
