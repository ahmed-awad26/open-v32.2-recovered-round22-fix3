package com.opencontacts.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.InputStream
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import com.opencontacts.core.ui.OpenContactsLogo

@Composable
fun IconCustomizationRoute(
    onBack: () -> Unit,
    appViewModel: AppViewModel,
) {
    val settings by appViewModel.appLockSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(appViewModel::setAppIconPreview)
    }
    SettingsScaffold(title = "Launcher icon", onBack = onBack) { modifier ->
        LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SettingsSection(
                    title = "Icon behavior",
                    subtitle = "Android does not reliably support arbitrary live launcher icon replacement on every launcher, so OpenContacts uses safe activity-alias switching for the actual launcher icon and an optional imported image for preview/theme inspiration.",
                ) {
                    val bitmap = runCatching {
                        settings.appIconPreviewUri?.let { uri ->
                            context.contentResolver.openInputStream(uri.toUri())?.use(BitmapFactory::decodeStream)
                        }
                    }.getOrNull()
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bitmap != null) {
                                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(96.dp))
                            } else {
                                OpenContactsLogo(modifier = Modifier.size(96.dp), cornerRadius = 24.dp)
                            }
                        }
                    }
                    SettingsSpacer()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                            Text("Choose preview image")
                        }
                        Button(onClick = appViewModel::clearAppIconPreview, modifier = Modifier.weight(1f)) {
                            Text("Clear preview")
                        }
                    }
                    SettingsSpacer()
                    SettingsChoiceRow(
                        title = "Launcher icon style",
                        subtitle = "This is the icon that actually appears in the launcher after a restart or launcher refresh.",
                        selected = settings.appIconAlias,
                        choices = LauncherIconManager.allAliases(),
                        onSelect = {
                            appViewModel.setAppIconAlias(it)
                            appViewModel.applyLauncherIcon(it)
                        },
                    )
                    SettingsSpacer()
                    Text(
                        "Most launchers apply the change immediately, but some require a short restart or launcher refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
