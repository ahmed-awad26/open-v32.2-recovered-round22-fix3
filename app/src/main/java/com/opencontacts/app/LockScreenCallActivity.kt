package com.opencontacts.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencontacts.core.crypto.AppLockSettings
import com.opencontacts.core.ui.theme.OpenContactsTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LockScreenCallActivity — full-screen incoming call UI shown over the lock screen.
 *
 * Shown when:
 *   - Device is locked / screen off
 *   - [AppLockSettings.lockScreenCallUiEnabled] is true
 *   - A call arrives (routed here from [presentIncomingCallExperience])
 *
 * Uses proper window flags for lock-screen display:
 *   - FLAG_SHOW_WHEN_LOCKED / setShowWhenLocked (API 27+)
 *   - FLAG_TURN_SCREEN_ON / setTurnScreenOn (API 27+)
 *   - FLAG_KEEP_SCREEN_ON
 *   - FLAG_DISMISS_KEYGUARD
 *
 * UI:
 *   - Blurred contact photo as full background (or themed gradient fallback)
 *   - Contact name, number, folder/tags
 *   - Swipe right to answer, swipe left to decline
 *   - Quick SMS button
 *   - Vibration/ring state from settings
 *
 * Dismisses itself when the call overlay controller state clears.
 */
class LockScreenCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Window flags for lock-screen display ──────────────────────────
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val settings by EntryPointAccessors
                .fromApplication(applicationContext, IncomingCallEntryPoint::class.java)
                .appLockRepository()
                .settings
                .collectAsStateWithLifecycle(initialValue = AppLockSettings.DEFAULT)

            OpenContactsTheme(
                themeMode = settings.themeMode,
                themePreset = settings.themePreset,
                accentPalette = settings.accentPalette,
                cornerStyle = settings.cornerStyle,
                backgroundCategory = settings.backgroundCategory,
                appFontProfile = settings.appFontProfile,
                customFontPath = settings.customFontPath,
            ) {
                LockScreenCallScreen(settings = settings, onDismiss = { finish() })
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LockScreenCallScreen(
    settings: AppLockSettings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val call by IncomingCallOverlayController.state.collectAsStateWithLifecycle()
    var smsComposerOpen by remember { mutableStateOf(false) }

    // Auto-dismiss when call state clears
    LaunchedEffect(call) {
        if (call == null) onDismiss()
    }

    // Load contact photo asynchronously
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(call?.photoUri) {
        photoBitmap = null
        val uri = call?.photoUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            photoBitmap = runCatching {
                context.contentResolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }

    if (call == null) return

    val currentCall = call!!

    // Swipe gesture offset for answer/decline
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedDrag by animateFloatAsState(targetValue = dragOffset, animationSpec = tween(80), label = "drag")
    val bgTint by animateColorAsState(
        targetValue = when {
            animatedDrag > 60f -> Color(0x4416A34A)  // green tint → answer
            animatedDrag < -60f -> Color(0x44DC2626) // red tint → decline
            else -> Color.Transparent
        },
        label = "bgtint",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffset > 120f -> {
                                answerIncomingCallFromLockScreen(context)
                                onDismiss()
                            }
                            dragOffset < -120f -> {
                                declineIncomingCallFromLockScreen(context)
                                onDismiss()
                            }
                        }
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, delta -> dragOffset = (dragOffset + delta).coerceIn(-200f, 200f) },
                )
            },
    ) {
        // ── Background ─────────────────────────────────────────────────────
        if (settings.showPhotoInNotifications && photoBitmap != null) {
            Image(
                bitmap = photoBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(24.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E293B),
                                Color(0xFF0F172A),
                            ),
                        ),
                    ),
            )
        }

        // Dimming overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
        )

        // Swipe color feedback
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgTint),
        )

        // ── Content ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {

            // Top: incoming call label
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (currentCall.blockMode.equals("SILENT_RING", ignoreCase = true))
                        "Incoming call · Silent"
                    else
                        "Incoming call",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }

            // Middle: avatar + name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (settings.showPhotoInNotifications && photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = currentCall.displayName.take(1).uppercase(),
                            fontSize = 40.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Name
                Text(
                    text = currentCall.displayName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                // Number
                if (settings.incomingCallShowNumber && currentCall.number.isNotBlank()) {
                    Text(
                        text = currentCall.number,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }

                // Folder / tags
                if (settings.incomingCallShowGroup && !currentCall.folderName.isNullOrBlank()) {
                    Text(
                        text = "📁 ${currentCall.folderName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                if (settings.incomingCallShowTag && currentCall.tags.isNotEmpty()) {
                    Text(
                        text = currentCall.tags.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }

            // Bottom: action buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Swipe hint
                Text(
                    text = "← Swipe left to decline   Swipe right to answer →",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Decline
                    LockScreenCallButton(
                        color = Color(0xFFDC2626),
                        icon = { Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(32.dp)) },
                        label = "Decline",
                        onClick = {
                            declineIncomingCallFromLockScreen(context)
                            onDismiss()
                        },
                    )

                    // Quick SMS
                    LockScreenCallButton(
                        color = Color(0xFF1D4ED8),
                        icon = { Icon(Icons.Default.Message, contentDescription = "SMS", tint = Color.White, modifier = Modifier.size(28.dp)) },
                        label = "SMS",
                        onClick = { smsComposerOpen = true },
                    )

                    // Dismiss (silent)
                    LockScreenCallButton(
                        color = Color(0xFF6B7280),
                        icon = { Icon(Icons.Default.VolumeOff, contentDescription = "Silence", tint = Color.White, modifier = Modifier.size(26.dp)) },
                        label = "Silence",
                        onClick = {
                            dismissIncomingUi(context)
                            onDismiss()
                        },
                    )

                    // Answer
                    LockScreenCallButton(
                        color = Color(0xFF16A34A),
                        icon = { Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(32.dp)) },
                        label = "Answer",
                        onClick = {
                            answerIncomingCallFromLockScreen(context)
                            onDismiss()
                        },
                    )
                }

                // Quick SMS template shortcuts
                val templates = listOf(
                    settings.quickSmsTemplate1,
                    settings.quickSmsTemplate2,
                    settings.quickSmsTemplate3,
                ).filter { it.isNotBlank() }

                if (templates.isNotEmpty() && currentCall.number.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        templates.take(3).forEach { tpl ->
                            androidx.compose.material3.AssistChip(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        sendSms(
                                            context = context,
                                            number = currentCall.number,
                                            message = tpl,
                                            trackDelivery = false,
                                        )
                                    }
                                    dismissIncomingUi(context)
                                    onDismiss()
                                },
                                label = {
                                    Text(
                                        text = tpl.take(28) + if (tpl.length > 28) "…" else "",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    labelColor = Color.White,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Quick SMS composer sheet ───────────────────────────────────────────
    if (smsComposerOpen && currentCall.number.isNotBlank()) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { smsComposerOpen = false },
        ) {
            SmsComposerRoute(
                onBack = { smsComposerOpen = false },
                settings = settings,
                prefillNumber = currentCall.number,
            )
        }
    }
}

@Composable
private fun LockScreenCallButton(
    color: Color,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                icon()
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

// ── Wrappers that route through TelecomCallCoordinator ────────────────────────

private fun answerIncomingCallFromLockScreen(context: android.content.Context) {
    runCatching { TelecomCallCoordinator.answer() }
    dismissIncomingUi(context)
    val ringing = IncomingCallOverlayController.state.value
    if (ringing != null) {
        launchActiveCallControls(context, ringing.toActiveCallUiState(), forceShow = true)
    }
}

private fun declineIncomingCallFromLockScreen(context: android.content.Context) {
    runCatching { TelecomCallCoordinator.decline() }
    dismissIncomingUi(context)
}
