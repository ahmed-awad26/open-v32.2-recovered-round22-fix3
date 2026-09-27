package com.opencontacts.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencontacts.core.ui.ReliableOutlinedTextField
import com.opencontacts.core.common.startInternalCallOrPrompt
import com.opencontacts.core.crypto.AppLockSettings
import com.opencontacts.core.ui.theme.OpenContactsTheme
import com.opencontacts.data.repository.TransferDestinationManager
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val EXTRA_FORCE_SHOW = "force_show"

data class ActiveCallUiState(
    val displayName: String,
    val number: String,
    val photoUri: String? = null,
    val folderName: String? = null,
    val tags: List<String> = emptyList(),
    val contactId: String? = null,
)

object ActiveCallOverlayController {
    val state = MutableStateFlow<ActiveCallUiState?>(null)

    fun show(call: ActiveCallUiState) {
        state.value = call
    }

    fun clear() {
        state.value = null
    }
}

internal fun launchActiveCallControls(context: Context, call: ActiveCallUiState, forceShow: Boolean = false) {
    ActiveCallOverlayController.show(call)
    postOngoingCallNotification(context, call)
    if (forceShow || AppVisibilityTracker.isForeground.value || canPresentCallUiOutsideApp(context)) {
        context.startActivity(
            Intent(context, ActiveCallControlsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_FORCE_SHOW, forceShow),
        )
    }
}

internal fun canPresentCallUiOutsideApp(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(context)
}

internal fun IncomingCallUiState.toActiveCallUiState(): ActiveCallUiState {
    return ActiveCallUiState(
        displayName = displayName,
        number = number,
        photoUri = photoUri,
        folderName = folderName,
        tags = tags,
        contactId = contactId,
    )
}

class ActiveCallControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        setContent {
            val settings = EntryPointAccessors.fromApplication(applicationContext, IncomingCallEntryPoint::class.java)
                .appLockRepository()
                .settings
                .collectAsStateWithLifecycle(initialValue = AppLockSettings.DEFAULT)
                .value
            OpenContactsTheme(themeMode = settings.themeMode, themePreset = settings.themePreset, accentPalette = settings.accentPalette, cornerStyle = settings.cornerStyle, backgroundCategory = settings.backgroundCategory, appFontProfile = settings.appFontProfile, customFontPath = settings.customFontPath) {
                ActiveCallControlsRoot(onDismiss = { finish() })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Show mini call view when user navigates away from the call screen
        // but a call is still active. The service safely no-ops if no overlay
        // permission or mini call view is disabled.
        if (TelecomCallCoordinator.activeCall.value != null) {
            MiniCallViewService.show(applicationContext)
        }
    }

    override fun onResume() {
        super.onResume()
        // Hide mini call view when user returns to the full call screen
        MiniCallViewService.hide(applicationContext)
    }
}

@Composable
private fun ActiveCallControlsRoot(onDismiss: () -> Unit) {
    val overlayCall by ActiveCallOverlayController.state.collectAsStateWithLifecycle()
    val telecomCall by TelecomCallCoordinator.activeCall.collectAsStateWithLifecycle()
    val call = telecomCall ?: overlayCall
    if (call == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    ActiveCallControlsScreen(call = call, onDismiss = onDismiss)
}

private data class ControlButtonModel(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun ActiveCallControlsScreen(call: ActiveCallUiState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, IncomingCallEntryPoint::class.java)
    }
    val scope = rememberCoroutineScope()
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val telecomState by TelecomCallCoordinator.telecomState.collectAsStateWithLifecycle()
    val speakerEnabled = telecomState.isSpeakerOn || currentSpeakerState(audioManager)
    val muted = telecomState.isMuted || currentMicMuteState(audioManager)
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var lastRecordingLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var noteDraft by rememberSaveable { mutableStateOf("") }
    var showNoteDialog by rememberSaveable { mutableStateOf(false) }
    val recorder = remember(context) { CallAudioRecorder(context, entryPoint.transferDestinationManager()) }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) recorder.stop()
            isRecording = false
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                runCatching { recorder.start() }
                    .onSuccess {
                        isRecording = true
                        lastRecordingLabel = null
                        context.toast(context.getString(R.string.recording_started))
                    }
                    .onFailure { context.toast(it.message ?: context.getString(R.string.recording_unavailable)) }
            }
        } else {
            context.toast(context.getString(R.string.recording_permission_required))
        }
    }

    val controls = listOf(
        ControlButtonModel(label = "Speaker", icon = Icons.Default.VolumeUp, active = speakerEnabled) {
            val enabled = !speakerEnabled
            TelecomCallCoordinator.setSpeaker(enabled)
            setSpeakerEnabled(audioManager, enabled)
        },
        ControlButtonModel(label = "Mute", icon = Icons.Default.Mic, active = muted) {
            val enabled = !muted
            TelecomCallCoordinator.setMuted(enabled)
            setMicMuted(audioManager, enabled)
        },
        ControlButtonModel(label = "Keypad", icon = Icons.Default.Dialpad) {
            TelecomCallCoordinator.showSystemDialpad()
        },
        ControlButtonModel(label = if (telecomState.isOnHold) "Resume" else "Hold", icon = Icons.Default.PauseCircle, active = telecomState.isOnHold) {
            TelecomCallCoordinator.toggleHold()
        },
        ControlButtonModel(label = "Add call", icon = Icons.Default.Add) {
            startInternalCallOrPrompt(context, call.number)
        },
        ControlButtonModel(label = "Note", icon = Icons.Default.NoteAdd) {
            showNoteDialog = true
        },
        ControlButtonModel(label = if (isRecording) "Stop" else "Record", icon = Icons.Default.FiberManualRecord, active = isRecording) {
            if (isRecording) {
                lastRecordingLabel = recorder.stop()?.displayPath
                isRecording = false
                context.toast(context.getString(R.string.recording_saved))
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                scope.launch {
                    runCatching { recorder.start() }
                        .onSuccess {
                            isRecording = true
                            lastRecordingLabel = null
                            context.toast(context.getString(R.string.recording_started))
                        }
                        .onFailure { context.toast(it.message ?: context.getString(R.string.recording_unavailable)) }
                }
            } else {
                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.active_call_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = call.displayName.ifBlank { call.number.ifBlank { stringResource(R.string.unknown_caller) } },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (call.number.isNotBlank() && call.displayName != call.number) {
                        Text(call.number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val extras = buildList {
                        call.folderName?.takeIf(String::isNotBlank)?.let(::add)
                        addAll(call.tags.take(3))
                    }
                    if (extras.isNotEmpty()) {
                        Text(extras.joinToString(" • "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (lastRecordingLabel != null) {
                        Text(
                            text = stringResource(R.string.last_recording_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(controls) { control ->
                    CallControlButton(control)
                }
            }

            Button(
                onClick = {
                    TelecomCallCoordinator.disconnect()
                    endActiveCall(context)
                    ActiveCallOverlayController.clear()
                    cancelOngoingCallNotification(context)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.end_call))
            }
        }
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text(stringResource(R.string.add_note)) },
            text = {
                ReliableOutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text(stringResource(R.string.call_note_hint)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val vaultId = entryPoint.vaultSessionManager().activeVaultId.value
                    val contactId = call.contactId
                    if (vaultId != null && contactId != null && noteDraft.isNotBlank()) {
                        val noteText = noteDraft.trim()
                        scope.launch(Dispatchers.IO) {
                            entryPoint.contactRepository().addNote(vaultId, contactId, noteText)
                        }
                        context.toast(context.getString(R.string.save))
                    } else {
                        context.toast("OpenContacts can only save notes for matched contacts")
                    }
                    noteDraft = ""
                    showNoteDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CallControlButton(control: ControlButtonModel) {
    val container = if (control.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val border = if (control.active) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
    Card(
        onClick = control.onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .height(122.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(control.icon, contentDescription = null, tint = if (control.active) MaterialTheme.colorScheme.primary else Color.Unspecified)
            }
            Text(control.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Start)
        }
    }
}

private class CallAudioRecorder(
    private val context: Context,
    private val transferDestinationManager: TransferDestinationManager,
) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var lastDisplayPath: String? = null
    private var descriptor: android.os.ParcelFileDescriptor? = null

    suspend fun start() {
        if (recorder != null) return
        val target = transferDestinationManager.prepareRecordingOutput("call-${System.currentTimeMillis()}.m4a")
        lastDisplayPath = target.displayPath
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            if (target.absolutePath != null) {
                val outputFile = File(target.absolutePath)
                outputFile.parentFile?.mkdirs()
                file = outputFile
                setOutputFile(outputFile.absolutePath)
            } else {
                descriptor = target.parcelFileDescriptor
                setOutputFile(descriptor?.fileDescriptor)
            }
            prepare()
            start()
        }
        recorder = mediaRecorder
    }

    fun stop(): SavedRecording? {
        val current = recorder ?: return lastDisplayPath?.let { SavedRecording(it) }
        runCatching { current.stop() }
        current.reset()
        current.release()
        recorder = null
        descriptor?.close()
        descriptor = null
        return lastDisplayPath?.let { SavedRecording(it) }.also {
            file = null
            lastDisplayPath = null
        }
    }
}

private data class SavedRecording(val displayPath: String)

private fun openSystemHoldControls(context: Context) {
    runCatching {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecomManager.showInCallScreen(false)
        context.toast("Opened system in-call controls")
    }.onFailure {
        context.toast("System hold controls are not available on this device")
    }
}

private fun endActiveCall(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.endCall()
        }
    }.onFailure {
        context.toast("Unable to end the call from OpenContacts")
    }
}

@Suppress("DEPRECATION")
private fun currentSpeakerState(audioManager: AudioManager): Boolean = audioManager.isSpeakerphoneOn

@Suppress("DEPRECATION")
private fun setSpeakerEnabled(audioManager: AudioManager, enabled: Boolean) {
    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    audioManager.isSpeakerphoneOn = enabled
}

@Suppress("DEPRECATION")
private fun currentMicMuteState(audioManager: AudioManager): Boolean = audioManager.isMicrophoneMute

@Suppress("DEPRECATION")
private fun setMicMuted(audioManager: AudioManager, enabled: Boolean) {
    audioManager.isMicrophoneMute = enabled
}

private fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
