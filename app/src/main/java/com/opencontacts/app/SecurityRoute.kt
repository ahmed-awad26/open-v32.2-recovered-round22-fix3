package com.opencontacts.app

import com.opencontacts.core.ui.ReliableOutlinedTextField
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SecurityRoute(onBack: () -> Unit, appViewModel: AppViewModel) {
    val settings by appViewModel.appLockSettings.collectAsStateWithLifecycle()
    val pinError by appViewModel.pinError.collectAsStateWithLifecycle()
    val availability = appViewModel.biometricAvailability()
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var lockMessage by remember(settings.lockScreenMessage) { mutableStateOf(settings.lockScreenMessage) }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(appViewModel::setLockScreenBackground) }
    SettingsScaffold(title = "Security", onBack = onBack) { modifier ->
        androidx.compose.foundation.lazy.LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                StatusCard(
                    title = "Security overview",
                    status = if (settings.biometricEnabled || settings.hasPin) "Protected" else "Open",
                    icon = if (settings.biometricEnabled || settings.hasPin) Icons.Default.Lock else Icons.Default.LockOpen,
                    tint = if (settings.biometricEnabled || settings.hasPin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                )
            }
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Lock screen appearance", style = MaterialTheme.typography.titleLarge)
                        LockPreview(settings.lockScreenMessage.ifBlank { "Authenticate to continue" }, settings.showTimeOnLockScreen)
                        SettingsSwitchRow("Show time", "Display the current time in the lock header.", settings.showTimeOnLockScreen, onCheckedChange = appViewModel::setShowTimeOnLockScreen)
                        SettingsSpacer()
                        SettingsSwitchRow("Illustration background", "Add a richer background while keeping text readable.", settings.useIllustrationOnLockScreen, onCheckedChange = appViewModel::setUseIllustrationOnLockScreen)
                        SettingsSpacer()
                        ReliableOutlinedTextField(value = lockMessage, onValueChange = { lockMessage = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Custom message") })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { appViewModel.setLockScreenMessage(lockMessage) }) { Text("Save message") }
                            TextButton(onClick = { bgPicker.launch(arrayOf("image/*")) }) { Text("Choose background") }
                            TextButton(onClick = appViewModel::clearLockScreenBackground) { Text("Clear") }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("PIN & biometrics", style = MaterialTheme.typography.titleLarge)
                        SettingsSwitchRow("Biometric unlock", if (availability.canAuthenticate) "Tap the fingerprint icon on the lock screen to authenticate." else availability.message, settings.biometricEnabled, enabled = availability.canAuthenticate, onCheckedChange = { enabled ->
                            if (!enabled) appViewModel.setBiometricEnabled(false) else {
                                val activity = context.findFragmentActivity() ?: return@SettingsSwitchRow
                                val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { appViewModel.setBiometricEnabled(true) }
                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { appViewModel.showUiError(errString.toString()) }
                                })
                                prompt.authenticate(appViewModel.biometricPromptInfo("Enable app lock"))
                            }
                        })
                        SettingsSpacer()
                        SettingsSwitchRow("Allow device credentials", "Fallback to the device PIN, pattern, or password.", settings.allowDeviceCredential, onCheckedChange = appViewModel::setAllowDeviceCredential)
                        SettingsSpacer()
                        ReliableOutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit); appViewModel.clearError() }, label = { Text(if (settings.hasPin) "Change PIN" else "Set PIN") }, singleLine = true)
                        if (!pinError.isNullOrBlank()) Text(pinError!!, color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { appViewModel.setPin(pin) }) { Text(if (settings.hasPin) "Update PIN" else "Save PIN") }
                            if (settings.hasPin) TextButton(onClick = appViewModel::clearPin) { Text("Remove PIN") }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Auto-lock", style = MaterialTheme.typography.titleLarge)
                        SettingsChoiceRow("Lock after inactivity", "How quickly the app should secure itself after being backgrounded.", settings.lockAfterInactivitySeconds.toString(), listOf("0", "15", "30", "60", "300")) { appViewModel.setLockAfterInactivitySeconds(it.toInt()) }
                        SettingsSpacer()
                        Button(onClick = appViewModel::lockNow) { Text("Lock now") }
                    }
                }
            }
        }
    }
}

@Composable private fun StatusCard(title: String, status: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Card(shape = RoundedCornerShape(26.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(title, style = MaterialTheme.typography.titleLarge); Text(status, color = tint) }; Icon(icon, null, tint = tint) } }
}

@Composable private fun LockPreview(message: String, showTime: Boolean) {
    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(180.dp).background(MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showTime) Text("09:41", style = MaterialTheme.typography.headlineMedium)
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun android.content.Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
