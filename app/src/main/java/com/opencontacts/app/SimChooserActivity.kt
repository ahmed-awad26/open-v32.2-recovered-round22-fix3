package com.opencontacts.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencontacts.core.common.CallSimOption
import com.opencontacts.core.common.availableCallSimOptions
import com.opencontacts.core.common.currentDefaultCallSimHandleId
import com.opencontacts.core.common.performCallWithMode
import com.opencontacts.core.crypto.AppLockSettings
import com.opencontacts.core.ui.localization.localizedText
import com.opencontacts.core.ui.theme.OpenContactsTheme
import dagger.hilt.android.EntryPointAccessors

class SimChooserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phoneValue = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
        val modeValue = intent.getStringExtra(EXTRA_CALL_MODE).orEmpty()
        val prefilledHandleId = intent.getStringExtra(EXTRA_PREFILLED_HANDLE_ID)
        setContent {
            val settings by EntryPointAccessors.fromApplication(applicationContext, IncomingCallEntryPoint::class.java)
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
                SimChooserScreen(
                    phone = phoneValue,
                    mode = modeValue,
                    prefilledHandleId = prefilledHandleId,
                    settings = settings,
                    onDismiss = { finish() },
                    onChoose = { handleId ->
                        performCallWithMode(this, phoneValue, modeValue, handleId)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        private const val EXTRA_CALL_MODE = "extra_call_mode"
        private const val EXTRA_PREFILLED_HANDLE_ID = "extra_prefilled_handle_id"
    }
}

@Composable
private fun SimChooserScreen(
    phone: String,
    mode: String,
    prefilledHandleId: String?,
    settings: AppLockSettings,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    val context = LocalContext.current
    val sims = availableCallSimOptions(context)
    val chosenId = prefilledHandleId ?: currentDefaultCallSimHandleId(context)
    val widthFraction = (settings.simChooserSizePercent.coerceIn(72, 100) / 100f)
    val cardAlpha = (settings.simChooserOpacity.coerceIn(45, 100) / 100f)

    if (sims.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(28.dp))
                .clickable(enabled = false) {},
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha),
            ),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = localizedText("Choose SIM"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = localizedText(if (phone.contains('*') || phone.contains('#')) "Choose the SIM to run this USSD code." else "Choose the SIM to place this call."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = phone,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(sims) { sim ->
                        SimOptionCard(
                            sim = sim,
                            selected = sim.handleId == chosenId,
                            onClick = { onChoose(sim.handleId) },
                        )
                    }
                }
                Text(
                    text = localizedText(if (mode.equals("SYSTEM_PHONE_APP", true)) "The system phone app will complete the action after you choose a SIM." else "OpenContacts will place the action after you choose a SIM."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SimOptionCard(sim: CallSimOption, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(text = "SIM ${sim.slotIndex + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(sim.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(localizedText("Tap to use this SIM now"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Text(text = localizedText("Default"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
