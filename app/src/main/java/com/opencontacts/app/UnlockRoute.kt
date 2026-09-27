package com.opencontacts.app

import android.graphics.BitmapFactory
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencontacts.core.ui.ReliableOutlinedTextField
import com.opencontacts.core.ui.OpenContactsLogo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UnlockRoute(
    viewModel: AppViewModel = hiltViewModel(),
) {
    val settings by viewModel.appLockSettings.collectAsStateWithLifecycle()
    val error by viewModel.pinError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    var pin by remember { mutableStateOf("") }
    val timeLabel = if (!settings.showTimeOnLockScreen) "" else remember(settings.showTimeOnLockScreen) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }
    val backgroundBitmap = remember(settings.lockScreenBackgroundUri) {
        runCatching {
            settings.lockScreenBackgroundUri?.let { uri ->
                context.contentResolver.openInputStream(uri.toUri())?.use(BitmapFactory::decodeStream)
            }
        }.getOrNull()
    }
    val cardShape = when (settings.lockScreenStyle.uppercase()) {
        "MINIMAL" -> RoundedCornerShape(20.dp)
        "GLASS" -> RoundedCornerShape(32.dp)
        else -> RoundedCornerShape(28.dp)
    }
    val fingerprintIcon = when (settings.fingerprintIconStyle.uppercase()) {
        "OUTLINE" -> Icons.Outlined.Fingerprint
        else -> Icons.Default.Fingerprint
    }

    Surface {
        Box(modifier = Modifier.fillMaxSize()) {
            if (backgroundBitmap != null) {
                Image(bitmap = backgroundBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (settings.useIllustrationOnLockScreen) {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (settings.lockScreenStyle.equals("GLASS", true)) 0.28f else 0.16f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                                ),
                            )
                        } else {
                            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))
                        },
                    ),
            )
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.lockScreenStyle.equals("GLASS", true)) MaterialTheme.colorScheme.surface.copy(alpha = 0.78f) else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (settings.showAppIconOnLockScreen) {
                        OpenContactsLogo(
                            modifier = Modifier.size(58.dp),
                            cornerRadius = 16.dp,
                        )
                    }
                    if (settings.showAppNameOnLockScreen) {
                        Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    }
                    if (settings.showTimeOnLockScreen) {
                        Text(timeLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        settings.lockScreenMessage.ifBlank { stringResource(R.string.unlock_default_message) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    if (settings.biometricEnabled && viewModel.canUseBiometric()) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
                                .clickable {
                                    val activity = context.findFragmentActivity() ?: run {
                                        viewModel.showUiError("Biometric unlock is unavailable on this screen.")
                                        return@clickable
                                    }
                                    runCatching {
                                        val prompt = BiometricPrompt(
                                            activity,
                                            ContextCompat.getMainExecutor(activity),
                                            object : BiometricPrompt.AuthenticationCallback() {
                                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                    viewModel.unlockWithBiometricSuccess()
                                                }

                                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                    viewModel.showUiError(errString.toString())
                                                }
                                            },
                                        )
                                        prompt.authenticate(viewModel.biometricPromptInfo(appName))
                                    }.onFailure {
                                        viewModel.showUiError(it.message ?: "Unable to start biometric prompt")
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(fingerprintIcon, contentDescription = stringResource(R.string.unlock_with_biometrics), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                        }
                        Text(stringResource(R.string.unlock_with_biometrics), style = MaterialTheme.typography.bodyMedium)
                    }

                    if (settings.hasPin) {
                        ReliableOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = pin,
                            onValueChange = {
                                pin = it
                                viewModel.clearError()
                            },
                            singleLine = true,
                            label = { Text(stringResource(R.string.unlock_pin_label)) },
                            supportingText = { if (error != null) Text(error ?: "") },
                        )
                        androidx.compose.material3.Button(onClick = { viewModel.unlockWithPin(pin) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.unlock_with_pin))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun android.content.Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
