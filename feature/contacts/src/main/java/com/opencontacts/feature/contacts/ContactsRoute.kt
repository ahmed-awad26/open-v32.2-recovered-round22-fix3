package com.opencontacts.feature.contacts

import android.Manifest
import android.net.Uri
import androidx.core.content.ContextCompat
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.opencontacts.core.ui.ReliableOutlinedTextField
import com.opencontacts.core.common.startInternalCallOrPrompt
import com.opencontacts.core.crypto.AppLockSettings
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.allPhoneNumbers
import com.opencontacts.core.model.VaultSummary
import com.opencontacts.core.ui.localization.isArabicUi
import com.opencontacts.core.ui.localization.localizeText
import com.opencontacts.core.ui.localization.localizedText
import com.opencontacts.feature.contacts.R
import com.opencontacts.feature.contacts.fastscroll.AlphabetFastScroller
import com.opencontacts.feature.contacts.fastscroll.buildAlphabetSectionIndex
import com.opencontacts.feature.contacts.fastscroll.normalizeSectionLetter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsRoute(
    activeVaultName: String = "Active vault",
    vaults: List<VaultSummary> = emptyList(),
    onOpenDetails: (String) -> Unit,
    onOpenWorkspace: (() -> Unit)? = null,
    onOpenImportExport: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    onOpenSecurity: (() -> Unit)? = null,
    themeMode: String = "SYSTEM",
    onToggleThemeMode: (() -> Unit)? = null,
    onOpenBackup: (() -> Unit)? = null,
    onOpenTrash: (() -> Unit)? = null,
    onOpenVaults: (() -> Unit)? = null,
    onSwitchVault: ((String) -> Unit)? = null,
    onOpenSmsComposer: ((String?) -> Unit)? = null,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val callLogGroups by viewModel.callLogGroups.collectAsStateWithLifecycle()
    val editing by viewModel.editingContact.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val arabicUi = isArabicUi()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var defaultTabApplied by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var qrPayload by remember { mutableStateOf<String?>(null) }
    var dialPadVisible by remember { mutableStateOf(false) }
    var dialNumber by rememberSaveable { mutableStateOf("") }
    var dialPadPanelHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    var bulkFolderEditor by remember { mutableStateOf<String?>(null) }
    var bulkTagEditor by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var debouncedSearchQuery by rememberSaveable { mutableStateOf("") }
    var activeFolderFilter by remember { mutableStateOf<String?>(null) }
    var activeTagFilter by remember { mutableStateOf<String?>(null) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>?>(null) }
    var favoritesPickerOpen by remember { mutableStateOf(false) }
    var favoritesSelection by remember { mutableStateOf(setOf<String>()) }

    androidx.compose.runtime.LaunchedEffect(searchQuery) {
        delay(250)
        debouncedSearchQuery = searchQuery
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val selectedTab = pagerState.currentPage
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (selectedTab == 1) viewModel.refreshCallLogs()
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(Manifest.permission.ANSWER_PHONE_CALLS)
            if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val startupPermissionPrefs = remember(context) { context.getSharedPreferences("opencontacts_startup_permissions", android.content.Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        val alreadyPrompted = startupPermissionPrefs.getBoolean("runtime_permissions_prompted_once", false)
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!alreadyPrompted && missingPermissions.isNotEmpty()) {
            delay(500)
            startupPermissionPrefs.edit().putBoolean("runtime_permissions_prompted_once", true).apply()
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    LaunchedEffect(settings.defaultStartTab, defaultTabApplied) {
        if (!defaultTabApplied) {
            val targetTab = if (settings.defaultStartTab.equals("CALL_LOG", ignoreCase = true)) 1 else 0
            pagerState.scrollToPage(targetTab)
            defaultTabApplied = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                activeFolderFilter = null
                activeTagFilter = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    BackHandler(enabled = searchQuery.isNotBlank() && selectedIds.isEmpty()) {
        searchQuery = ""
    }

    val callWeightByContactId = remember(callLogGroups) {
        callLogGroups.filter { it.contactId != null }.associate { it.contactId!! to it.totalCalls }
    }
    val callWeightByNumber = remember(callLogGroups) {
        callLogGroups.filter { it.number.isNotBlank() }.associate { normalizePhoneForMatching(it.number) to it.totalCalls }
    }

    val filteredContacts = remember(
        contacts,
        favoritesOnly,
        settings,
        debouncedSearchQuery,
        callWeightByContactId,
        callWeightByNumber,
        activeFolderFilter,
        activeTagFilter,
    ) {
        val normalizedQuery = debouncedSearchQuery.trim().lowercase()
        contacts
            .asSequence()
            .filter { contact -> !favoritesOnly || contact.isFavorite }
            .filter { contact ->
                when {
                    activeTagFilter != null -> activeTagFilter in contact.tags
                    activeFolderFilter != null -> activeFolderFilter == contact.folderName || contact.folderNames.any { folder -> folder.equals(activeFolderFilter, ignoreCase = true) }
                    else -> true
                }
            }
            .filter { contact -> normalizedQuery.isBlank() || settings.showBlockedContactsInSearch || !contact.isBlocked }
            .filter { contact ->
                if (normalizedQuery.isBlank()) true else {
                    contact.displayName.lowercase().contains(normalizedQuery) ||
                        (contact.allPhoneNumbers().any { it.value.lowercase().contains(normalizedQuery) }) ||
                        contact.tags.any { it.lowercase().contains(normalizedQuery) } ||
                        (contact.folderName?.lowercase()?.contains(normalizedQuery) == true) ||
                        contact.folderNames.any { it.lowercase().contains(normalizedQuery) }
                }
            }
            .sortedWith { left, right ->
                if (settings.showFavoritesFirst && left.isFavorite != right.isFavorite) {
                    return@sortedWith if (left.isFavorite) -1 else 1
                }
                val primaryCompare = when (settings.defaultContactSortOrder.uppercase()) {
                    "LAST_NAME" -> left.displayName.substringAfterLast(' ', left.displayName).compareTo(right.displayName.substringAfterLast(' ', right.displayName), ignoreCase = true)
                    "RECENTLY_ADDED" -> right.createdAt.compareTo(left.createdAt)
                    "MOST_CONTACTED" -> {
                        val leftCount = callWeightByContactId[left.id] ?: left.allPhoneNumbers().maxOfOrNull { phone -> callWeightByNumber[normalizePhoneForMatching(phone.value)] ?: 0 } ?: 0
                        val rightCount = callWeightByContactId[right.id] ?: right.allPhoneNumbers().maxOfOrNull { phone -> callWeightByNumber[normalizePhoneForMatching(phone.value)] ?: 0 } ?: 0
                        rightCount.compareTo(leftCount)
                    }
                    else -> left.displayName.compareTo(right.displayName, ignoreCase = true)
                }
                if (primaryCompare != 0) primaryCompare else left.displayName.compareTo(right.displayName, ignoreCase = true)
            }
            .toList()
    }

    val selectedContacts = remember(selectedIds, contacts) { contacts.filter { it.id in selectedIds } }
    val selectedFavoritesAll = remember(selectedContacts) { selectedContacts.isNotEmpty() && selectedContacts.all { it.isFavorite } }
    val nonFavoriteContacts = remember(contacts) { contacts.filterNot { it.isFavorite } }
    val sectionIndex = remember(filteredContacts) { buildAlphabetSectionIndex(filteredContacts) { it.displayName } }
    val listState = rememberLazyListState()
    val currentScrollerLetter by remember(filteredContacts, listState) {
        derivedStateOf {
            filteredContacts
                .getOrNull(listState.firstVisibleItemIndex)
                ?.let { normalizeSectionLetter(it.displayName) }
                ?: "#"
        }
    }

    fun openDial(phone: String?) {
        startInternalCallOrPrompt(context, phone)
    }

    fun shareSelectedAsText() {
        if (selectedContacts.isEmpty()) return
        val payload = selectedContacts.joinToString("\n\n") { contact ->
            buildString {
                append(contact.displayName)
                contact.primaryPhone?.takeIf(String::isNotBlank)?.let { append("\n$it") }
                if (contact.tags.isNotEmpty()) append("\nTags: ${contact.tags.joinToString()}")
                contact.folderName?.let { append("\nFolder: $it") }
            }
        }
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, payload)
                },
                localizeText("Share contact(s)", arabicUi),
            ),
        )
    }

    fun shareQr(contact: ContactSummary) {
        qrPayload = contact.toVCardPayload()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(localizedText("Vaults"), modifier = Modifier.padding(start = 16.dp, top = 18.dp, end = 16.dp), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = localizedText("Choose the active vault, then jump into all contacts, folders, or tags from the same panel."),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        vaults.forEach { vault ->
                            DrawerItem(
                                title = vault.displayName,
                                subtitle = if (vault.displayName == activeVaultName) localizedText("Current vault") else if (vault.isLocked) localizedText("Locked") else localizedText("Switch vault"),
                                icon = Icons.Default.Folder,
                                selected = vault.displayName == activeVaultName,
                            ) {
                                activeFolderFilter = null
                                activeTagFilter = null
                                favoritesOnly = false
                                onSwitchVault?.invoke(vault.id)
                                scope.launch { drawerState.close() }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(localizedText("Current vault view"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
                        DrawerItem(
                            title = localizedText("All contacts"),
                            subtitle = localizedText("Default view for $activeVaultName"),
                            icon = Icons.Default.Groups,
                            selected = activeFolderFilter == null && activeTagFilter == null && !favoritesOnly,
                        ) {
                            activeFolderFilter = null
                            activeTagFilter = null
                            favoritesOnly = false
                            scope.launch { drawerState.close() }
                        }
                        DrawerItem(
                            title = localizedText("Favorites"),
                            subtitle = if (favoritesOnly) localizedText("Showing starred contacts only") else localizedText("Show only favorite contacts in this vault"),
                            icon = Icons.Default.Star,
                            selected = favoritesOnly,
                        ) {
                            favoritesOnly = !favoritesOnly
                            if (favoritesOnly) {
                                activeFolderFilter = null
                                activeTagFilter = null
                            }
                            scope.launch { drawerState.close() }
                        }
                        Text(localizedText("Folders"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
                        if (folders.isEmpty()) {
                            Text(
                                text = localizedText("No folders yet in this vault."),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (settings.drawerFolderDisplayMode.equals("CAROUSEL", ignoreCase = true)) {
                            Text(
                                text = localizedText("Visual strip — swipe left and right to browse folders."),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(folders, key = { it.name }) { folder ->
                                    DrawerFolderCarouselTile(
                                        folder = folder,
                                        selected = activeFolderFilter == folder.name,
                                        onClick = {
                                            activeFolderFilter = folder.name
                                            activeTagFilter = null
                                            favoritesOnly = false
                                            scope.launch { drawerState.close() }
                                        },
                                    )
                                }
                            }
                        } else {
                            folders.forEach { folder ->
                                DrawerItem(
                                    title = folder.name,
                                    subtitle = localizedText("${folder.usageCount} contact(s)"),
                                    icon = Icons.Default.Folder,
                                    selected = activeFolderFilter == folder.name,
                                ) {
                                    activeFolderFilter = folder.name
                                    activeTagFilter = null
                                    favoritesOnly = false
                                    scope.launch { drawerState.close() }
                                }
                            }
                        }
                        Text(localizedText("Tags"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
                        if (tags.isEmpty()) {
                            Text(
                                text = localizedText("No tags yet in this vault."),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .requiredHeightIn(max = 300.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    tags.forEach { tag ->
                                        DrawerItem(
                                            title = tag.name,
                                            subtitle = localizedText("${tag.usageCount} contact(s)"),
                                            icon = Icons.Default.Groups,
                                            selected = activeTagFilter == tag.name,
                                        ) {
                                            activeTagFilter = tag.name
                                            activeFolderFilter = null
                                            favoritesOnly = false
                                            scope.launch { drawerState.close() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch { drawerState.close() }
                                onOpenSecurity?.invoke()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(localizedText("Settings"))
                        }
                        FilledTonalButton(
                            onClick = {
                                onToggleThemeMode?.invoke()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(localizedText(if (themeMode.equals("DARK", ignoreCase = true)) "Light mode" else "Dark mode"))
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu)) }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.contacts_search_placeholder),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Backspace, contentDescription = stringResource(R.string.clear_search))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.startCreate() }) { Icon(Icons.Default.Add, contentDescription = localizedText("Add contact")) }
                        }
                    }
                }

                AnimatedVisibility(visible = selectedTab == 0 && (activeFolderFilter != null || activeTagFilter != null)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeFolderFilter ?: activeTagFilter ?: "All contacts",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = if (activeFolderFilter != null) "Folder filter active — Contacts will return to the full vault view when you move around the app." else "Tag filter active — Contacts will return to the full vault view when you move around the app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            TextButton(onClick = {
                                activeFolderFilter = null
                                activeTagFilter = null
                            }) {
                                Text(localizedText("Show all"))
                            }
                        }
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(stringResource(R.string.contacts_tab_contacts)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(stringResource(R.string.contacts_tab_call_log)) },
                    )
                }

                // ── TOP_STRIP folder panel (when placement = TOP_STRIP or SIDE_PANEL) ──
                if (folders.isNotEmpty() && !settings.folderPanelPlacement.equals("DRAWER", ignoreCase = true)) {
                    val isCompact = settings.folderPanelMode.equals("COMPACT", ignoreCase = true)
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isCompact) 4.dp else 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // "All" shortcut
                        item(key = "__all__") {
                            FilterChip(
                                selected = activeFolderFilter == null && activeTagFilter == null && !favoritesOnly,
                                onClick = {
                                    activeFolderFilter = null
                                    activeTagFilter = null
                                    favoritesOnly = false
                                },
                                label = { Text(if (isCompact) "All" else localizedText("All contacts")) },
                                leadingIcon = if (!isCompact) {
                                    { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                        // "Favorites" shortcut
                        item(key = "__fav__") {
                            FilterChip(
                                selected = favoritesOnly,
                                onClick = {
                                    favoritesOnly = !favoritesOnly
                                    activeFolderFilter = null
                                    activeTagFilter = null
                                },
                                label = { Text(if (isCompact) "★" else localizedText("Favorites")) },
                            )
                        }
                        // Folder chips
                        items(folders, key = { "folder_strip_${it.name}" }) { folder ->
                            val accent = drawerFolderAccent(folder.colorToken)
                            FilterChip(
                                selected = activeFolderFilter == folder.name,
                                onClick = {
                                    activeFolderFilter = if (activeFolderFilter == folder.name) null else folder.name
                                    activeTagFilter = null
                                    favoritesOnly = false
                                },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(folder.name)
                                        if (!isCompact && folder.usageCount > 0) {
                                            Text(
                                                "(${folder.usageCount})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                leadingIcon = if (activeFolderFilter == folder.name) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent) }
                                } else null,
                            )
                        }
                    }
                }

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        if (page == 1) viewModel.refreshCallLogs()
                    }
                }

                AnimatedVisibility(visible = selectedTab == 0 && selectedIds.isNotEmpty()) {
                    SelectionBar(
                        count = selectedIds.size,
                        allSelected = selectedIds.size == filteredContacts.size && filteredContacts.isNotEmpty(),
                        onSelectAll = { selectedIds = if (selectedIds.size == filteredContacts.size) emptySet() else filteredContacts.map { it.id }.toSet() },
                        onShare = ::shareSelectedAsText,
                        onDelete = { if (settings.confirmBeforeDelete) pendingDeleteIds = selectedIds else { viewModel.deleteMany(selectedIds); selectedIds = emptySet() } },
                        onEdit = { selectedContacts.singleOrNull()?.let(viewModel::startEdit) },
                        onQr = { selectedContacts.singleOrNull()?.let(::shareQr) },
                        onAssignFolder = { bulkFolderEditor = selectedContacts.firstOrNull()?.folderName.orEmpty() },
                        onAssignTag = { bulkTagEditor = selectedContacts.firstOrNull()?.tags?.firstOrNull().orEmpty() },
                        onToggleFavorites = { viewModel.toggleFavoriteMany(selectedIds, if (selectedFavoritesAll) false else true); selectedIds = emptySet() },
                        favoritesActive = selectedFavoritesAll,
                        qrEnabled = selectedIds.size == 1,
                        editEnabled = selectedIds.size == 1,
                    )
                }
                AnimatedVisibility(visible = selectedTab == 0 && favoritesOnly && selectedIds.isEmpty()) {
                    FavoritesQuickAddCard(
                        favoriteCount = filteredContacts.size,
                        onAdd = {
                            favoritesSelection = emptySet()
                            favoritesPickerOpen = true
                        },
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1,
                ) { page ->
                    if (page == 0) {
                        if (filteredContacts.isEmpty()) {
                            EmptyStateCard(stringResource(R.string.no_contacts_yet), stringResource(R.string.no_contacts_yet_subtitle))
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                var lastJumpPosition by remember(sectionIndex) { mutableStateOf(-1) }
                                LazyColumn(
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize().padding(end = 16.dp),
                                ) {
                                    items(filteredContacts, key = { it.id }, contentType = { "contact" }) { contact ->
                                        val selected = contact.id in selectedIds
                                        ContactModernCard(
                                            contact = contact,
                                            selected = selected,
                                            selectionMode = selectedIds.isNotEmpty(),
                                            onToggleSelection = { selectedIds = if (selected) selectedIds - contact.id else selectedIds + contact.id },
                                            onLongPress = { selectedIds = if (selected) selectedIds - contact.id else selectedIds + contact.id },
                                            onOpen = {
                                                if (selectedIds.isNotEmpty()) {
                                                    selectedIds = if (selected) selectedIds - contact.id else selectedIds + contact.id
                                                } else if (settings.openContactDirectlyOnTap) {
                                                    onOpenDetails(contact.id)
                                                } else {
                                                    selectedIds = setOf(contact.id)
                                                }
                                            },
                                            onCall = { openDial(contact.allPhoneNumbers().firstOrNull()?.value) },
                                            compact = settings.contactListDensity.equals("COMPACT", ignoreCase = true),
                                            showPhoto = settings.showContactPhotosInList,
                                        )
                                    }
                                    item { Spacer(Modifier.height(96.dp)) }
                                }
                                AlphabetFastScroller(
                                    letters = sectionIndex.letters,
                                    availableLetters = sectionIndex.availableLetters,
                                    activeLetter = currentScrollerLetter,
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                    onLetterChanged = { selectedLetter ->
                                        sectionIndex.resolveTargetPosition(selectedLetter)
                                            ?.takeIf { it != lastJumpPosition }
                                            ?.let { targetPosition ->
                                                lastJumpPosition = targetPosition
                                                scope.launch { listState.scrollToItem(targetPosition) }
                                            }
                                    },
                                    onInteractionEnd = { lastJumpPosition = -1 },
                                )
                            }
                        }
                    } else {
                        if (callLogGroups.isEmpty()) {
                            EmptyStateCard(stringResource(R.string.no_recent_calls), stringResource(R.string.no_recent_calls_subtitle))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(callLogGroups, key = { it.key }, contentType = { "call_group" }) { group ->
                                    CallLogGroupCard(
                                        group = group,
                                        onDial = { openDial(group.number) },
                                        onOpenDetails = group.contactId?.let { contactId -> { onOpenDetails(contactId) } },
                                        onSendSms = if (group.number.isNotBlank()) {
                                            { onOpenSmsComposer?.invoke(group.number) }
                                        } else null,
                                    )
                                }
                                item { Spacer(Modifier.height(84.dp)) }
                            }
                        }
                    }
                }

                }

                if (dialPadVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.14f))
                            .clickable { dialPadVisible = false },
                    )
                }

                AnimatedVisibility(
                    visible = dialPadVisible,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                ) {
                    DialPadPanel(
                        number = dialNumber,
                        contacts = filteredContacts,
                        settings = settings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { dialPadPanelHeightPx = it.size.height },
                        onNumberChange = { dialNumber = it },
                        onDismiss = { dialPadVisible = false },
                        onCall = { openDial(dialNumber) },
                        onSaveContact = { value ->
                            dialPadVisible = false
                            viewModel.startCreate(prefilledPhone = value)
                        },
                    )
                }

                FloatingActionButton(
                    onClick = { dialPadVisible = !dialPadVisible },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = if (dialPadVisible) with(density) { dialPadPanelHeightPx.toDp() } + 20.dp else 24.dp,
                        )
                        .size(settings.dialPadToggleButtonSizeDp.dp)
                        .zIndex(2f),
                    containerColor = Color(0xFF16A34A),
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = if (dialPadVisible) Icons.Default.Close else Icons.Default.Dialpad,
                        contentDescription = localizedText(if (dialPadVisible) "Hide dial pad" else "Show dial pad"),
                        modifier = Modifier.size((settings.dialPadToggleButtonSizeDp * 0.42f).dp),
                    )
                }

            editing?.let { editor ->
                FullScreenContactEditor(
                    state = editor,
                    onStateChange = viewModel::updateEditor,
                    onDismiss = viewModel::dismissEditor,
                    onConfirm = viewModel::saveEditor,
                )
            }
            bulkFolderEditor?.let { value ->
                ClassificationPickerDialog(
                    title = localizedText("Assign folder"),
                    value = value,
                    existingNames = folders.map { it.name },
                    label = "Folder name",
                    onValueChange = { bulkFolderEditor = it },
                    onDismiss = { bulkFolderEditor = null },
                    onConfirm = {
                        val clean = bulkFolderEditor?.trim().orEmpty()
                        if (clean.isNotBlank()) viewModel.assignFolderToMany(selectedIds, clean)
                        bulkFolderEditor = null
                        selectedIds = emptySet()
                    },
                )
            }
            bulkTagEditor?.let { value ->
                ClassificationPickerDialog(
                    title = localizedText("Assign tag"),
                    value = value,
                    existingNames = tags.map { it.name },
                    label = "Tag name",
                    onValueChange = { bulkTagEditor = it },
                    onDismiss = { bulkTagEditor = null },
                    onConfirm = {
                        val clean = bulkTagEditor?.trim().orEmpty().removePrefix("#")
                        if (clean.isNotBlank()) viewModel.assignTagToMany(selectedIds, clean)
                        bulkTagEditor = null
                        selectedIds = emptySet()
                    },
                )
            }
            if (favoritesPickerOpen) {
                FavoritesPickerDialog(
                    contacts = nonFavoriteContacts,
                    selected = favoritesSelection,
                    onToggle = { id -> favoritesSelection = if (id in favoritesSelection) favoritesSelection - id else favoritesSelection + id },
                    onDismiss = { favoritesPickerOpen = false },
                    onConfirm = {
                        viewModel.toggleFavoriteMany(favoritesSelection, true)
                        favoritesPickerOpen = false
                        favoritesOnly = true
                    },
                )
            }
            qrPayload?.let { payload -> QrCodeDialog(payload = payload, onDismiss = { qrPayload = null }) }
            pendingDeleteIds?.let { ids ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteIds = null },
                    title = { Text(localizedText("Delete selected contacts?")) },
                    text = { Text(localizedText("${ids.size} selected contact(s) will be moved to trash.")) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteMany(ids)
                            selectedIds = emptySet()
                            pendingDeleteIds = null
                        }) { Text(localizedText("Delete")) }
                    },
                    dismissButton = { TextButton(onClick = { pendingDeleteIds = null }) { Text(localizedText("Cancel")) } },
                )
            }
        }
    }
}
}

@Composable
private fun DrawerItem(title: String, subtitle: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HomeAction(label: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.combinedClickable(onClick = onClick)) {
        Surface(shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
            Icon(icon, contentDescription = label, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    qrEnabled: Boolean,
    editEnabled: Boolean,
    favoritesActive: Boolean,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onQr: () -> Unit,
    onAssignFolder: () -> Unit,
    onAssignTag: () -> Unit,
    onToggleFavorites: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(localizedText("$count selected"), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSelectAll) { Icon(if (allSelected) Icons.Default.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, contentDescription = localizedText("Select all")) }
                IconButton(onClick = onToggleFavorites) { Icon(Icons.Default.Star, contentDescription = localizedText(if (favoritesActive) "Remove from favorites" else "Add to favorites"), tint = if (favoritesActive) Color(0xFFF59E0B) else LocalContentColor.current) }
                IconButton(onClick = onAssignFolder) { Icon(Icons.Default.CreateNewFolder, contentDescription = localizedText("Assign folder")) }
                IconButton(onClick = onAssignTag) { Icon(Icons.Default.Groups, contentDescription = localizedText("Assign tag")) }
                IconButton(onClick = onEdit, enabled = editEnabled) { Icon(Icons.Default.Edit, contentDescription = localizedText("Edit")) }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = localizedText("Share")) }
                IconButton(onClick = onQr, enabled = qrEnabled) { Icon(Icons.Default.QrCode2, contentDescription = "QR") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = localizedText("Delete")) }
            }
        }
    }
}


@Composable
private fun FavoritesQuickAddCard(favoriteCount: Int, onAdd: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(localizedText("Favorites"), style = MaterialTheme.typography.titleMedium)
                Text(localizedText("$favoriteCount favorite contact(s) ready for quick calling."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(localizedText("Add"))
            }
        }
    }
}

@Composable
private fun FavoritesPickerDialog(
    contacts: List<ContactSummary>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("Add favorites")) },
        text = {
            if (contacts.isEmpty()) {
                Text(localizedText("All contacts are already in favorites."))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(contacts, key = { it.id }) { contact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onToggle(contact.id) }).padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = contact.id in selected, onCheckedChange = { onToggle(contact.id) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                contact.allPhoneNumbers().firstOrNull()?.value?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = selected.isNotEmpty()) { Text(localizedText("Add to favorites")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("Cancel")) } },
    )
}

@Composable
private fun ContactModernCard(
    contact: ContactSummary,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    compact: Boolean,
    showPhoto: Boolean,
) {
    val context = LocalContext.current
    val arabicUi = isArabicUi()
    val padding = if (compact) 12.dp else 16.dp
    val avatarSize = if (compact) 44.dp else 52.dp
    val avatarBitmapState by rememberBitmapFromUri(context = context, uri = contact.photoUri, maxSizePx = 192)
    val avatarBitmap = avatarBitmapState
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                contact.isBlocked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                    if (selectionMode) {
                        Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
                        Spacer(Modifier.width(8.dp))
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(avatarSize)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (showPhoto && avatarBitmap != null) {
                                Image(bitmap = avatarBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text(contact.displayName.take(1).uppercase(), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(contact.displayName, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (contact.isFavorite) Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        val contactPhones = contact.allPhoneNumbers()
                        Text(contactPhones.firstOrNull()?.value ?: "No phone number", style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
                        if (contactPhones.size > 1) {
                            Text("+${contactPhones.size - 1} more number(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            contact.folderName?.let { AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { Icon(Icons.Default.Folder, null) }) }
                            if (contact.isBlocked) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Blocked", color = MaterialTheme.colorScheme.onErrorContainer) },
                                    leadingIcon = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer) },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onCall, enabled = contact.allPhoneNumbers().isNotEmpty()) { Icon(Icons.Default.Phone, contentDescription = "Call") }
            }
            if (contact.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    contact.tags.take(8).forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                }
            }
        }
    }
}

@Composable
private fun CallLogGroupCard(group: CallLogGroup, onDial: () -> Unit, onOpenDetails: (() -> Unit)?, onSendSms: (() -> Unit)? = null) {
    val missedColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF7A7A) else Color(0xFFB3261E)
    val isMissed = group.lastType == "Missed"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpenDetails != null) Modifier.clickable(onClick = onOpenDetails) else Modifier),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (isMissed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.displayName, style = MaterialTheme.typography.titleMedium)
                    if (isMissed) AssistChip(onClick = {}, label = { Text("Missed", color = missedColor) })
                }
                Text(
                    text = "${group.lastType} • ${formatTime(group.lastTimestamp)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMissed) missedColor else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${group.totalCalls} call(s) • ${group.number.ifBlank { "No number" }}${if (group.totalDurationSeconds > 0) " • ${group.totalDurationSeconds}s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMissed) missedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // SMS quick action (shown for all entries with a number)
            if (onSendSms != null && group.number.isNotBlank()) {
                IconButton(onClick = onSendSms) {
                    Icon(
                        Icons.Default.Message,
                        contentDescription = "Send SMS",
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            IconButton(onClick = onDial, enabled = group.number.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Filled.CallMade,
                    contentDescription = "Dial",
                    tint = if (isMissed) missedColor else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle)
        }
    }
}

@Composable
private fun FullScreenContactEditor(state: ContactEditorState, onStateChange: (ContactEditorState) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onStateChange(state.copy(photoUri = uri?.toString().orEmpty()))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.id == null) "Add contact" else "Edit contact", style = MaterialTheme.typography.headlineMedium)
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(onClick = onConfirm) { Text("Save") }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Main details", style = MaterialTheme.typography.titleMedium)
                        ReliableOutlinedTextField(value = state.displayName, onValueChange = { onStateChange(state.copy(displayName = it)) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        ReliableOutlinedTextField(value = state.phone, onValueChange = { onStateChange(state.copy(phone = it)) }, label = { Text("Primary phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        AdditionalPhoneEntriesEditor(
                            entries = state.additionalPhoneEntries,
                            onEntriesChange = { onStateChange(state.copy(additionalPhoneEntries = it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ReliableOutlinedTextField(value = state.folderName, onValueChange = { onStateChange(state.copy(folderName = it)) }, label = { Text("Folder") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        ReliableOutlinedTextField(value = state.tags, onValueChange = { onStateChange(state.copy(tags = it)) }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.isFavorite, onCheckedChange = { onStateChange(state.copy(isFavorite = it)) })
                            Text("Favorite")
                        }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Contact photo", style = MaterialTheme.typography.titleMedium)
                        Text(if (state.photoUri.isBlank()) "No photo selected" else "Photo attached")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { photoPicker.launch("image/*") }) { Text(if (state.photoUri.isBlank()) "Add photo" else "Change photo") }
                            if (state.photoUri.isNotBlank()) {
                                TextButton(onClick = { onStateChange(state.copy(photoUri = "")) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialPadPanel(
    number: String,
    contacts: List<ContactSummary>,
    settings: AppLockSettings,
    modifier: Modifier = Modifier,
    onNumberChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onSaveContact: (String) -> Unit,
) {
    val keys = listOf(
        "1" to "", "2" to "ABC", "3" to "DEF",
        "4" to "GHI", "5" to "JKL", "6" to "MNO",
        "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
        "*" to "", "0" to "+", "#" to "",
    )
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val formattedNumber = remember(number, settings.dialPadAutoFormat) {
        if (settings.dialPadAutoFormat) formatDialPadNumber(number) else number
    }
    val suggestions = remember(number, contacts, settings.dialPadShowT9Suggestions) {
        if (!settings.dialPadShowT9Suggestions) emptyList() else computeDialPadSuggestions(number, contacts)
    }
    val arabicUi = isArabicUi()
    val stateLabel = remember(number, suggestions, arabicUi) {
        when {
            number.isBlank() -> localizeText("Idle", arabicUi)
            suggestions.isNotEmpty() -> localizeText("Matched contact", arabicUi)
            else -> localizeText("Typing", arabicUi)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 10.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stateLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                val clip = clipboardManager.getText()?.text.orEmpty()
                                if (clip.isNotBlank()) onNumberChange(number + clip.filter { it.isDigit() || it == '+' || it == '*' || it == '#' })
                            }) { Text(localizedText("Paste")) }
                            TextButton(onClick = { if (number.isNotBlank()) onSaveContact(number) }, enabled = number.isNotBlank()) { Text(localizedText("Save")) }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (formattedNumber.isBlank()) localizeText("Enter number", arabicUi) else formattedNumber,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (number.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .pointerInput(number, settings.dialPadLongPressBackspaceClears, settings.dialPadBackspaceLongPressDurationMs) {
                                        detectTapGestures(
                                            onPress = {
                                                if (number.isEmpty()) return@detectTapGestures
                                                var cleared = false
                                                coroutineScope {
                                                    val holdJob = launch {
                                                        delay(settings.dialPadBackspaceLongPressDurationMs.toLong())
                                                        if (settings.dialPadLongPressBackspaceClears && number.isNotEmpty()) {
                                                            cleared = true
                                                            if (settings.hapticFeedbackEnabled) {
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            }
                                                            onNumberChange("")
                                                        }
                                                    }
                                                    val released = tryAwaitRelease()
                                                    holdJob.cancel()
                                                    if (released && !cleared && number.isNotEmpty()) {
                                                        onNumberChange(number.dropLast(1))
                                                    }
                                                }
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Backspace,
                                    contentDescription = localizedText("Delete digit"),
                                    tint = if (number.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                    if (suggestions.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(suggestions.take(4), key = { it.id }) { contact ->
                                DialPadSuggestionChip(
                                    contact = contact,
                                    onSelect = { onNumberChange(contact.allPhoneNumbers().firstOrNull()?.value.orEmpty()) },
                                )
                            }
                        }
                    }
                    if (settings.dialPadLongPressBackspaceClears) {
                        Text(
                            text = localizedText("Hold delete for ${settings.dialPadBackspaceLongPressDurationMs} ms to clear the whole number."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                keys.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEach { (digit, letters) ->
                            Box(
                                modifier = Modifier
                                    .size(width = 92.dp, height = 72.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (settings.hapticFeedbackEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onNumberChange(number + digit)
                                        },
                                        onLongClick = {
                                            if (settings.hapticFeedbackEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            when (digit) {
                                                "0" -> onNumberChange(number + "+")
                                                else -> onNumberChange(number + digit)
                                            }
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(digit, style = MaterialTheme.typography.headlineMedium)
                                    if (settings.dialPadShowLetters && letters.isNotBlank()) {
                                        Text(letters, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (digit == "0") {
                                        Spacer(Modifier.height(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(44.dp))
                Surface(shape = CircleShape, color = Color(0xFF16A34A)) {
                    IconButton(onClick = onCall, modifier = Modifier.size(74.dp), enabled = number.isNotBlank()) {
                        Icon(Icons.Default.Phone, contentDescription = localizedText("Call"), tint = Color.White)
                    }
                }
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.width(20.dp).height(4.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        ) {}
                    }
                }
            }
        }
    }
}


@Composable
private fun DialPadSuggestionChip(contact: ContactSummary, onSelect: () -> Unit) {
    val phone = contact.allPhoneNumbers().firstOrNull()?.value.orEmpty()
    val folderLabel = contact.folderNames.firstOrNull() ?: contact.folderName
    AssistChip(
        onClick = onSelect,
        label = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(contact.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                Text(phone, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
        trailingIcon = if (!folderLabel.isNullOrBlank()) {
            { Text(folderLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        } else null,
    )
}

private fun computeDialPadSuggestions(number: String, contacts: List<ContactSummary>): List<ContactSummary> {
    val digits = number.filter(Char::isDigit)
    if (digits.isBlank()) return emptyList()
    return contacts.filter { contact ->
        contact.allPhoneNumbers().any { phone -> phone.value.filter(Char::isDigit).contains(digits) } ||
            contact.displayName.toT9Digits().startsWith(digits)
    }.sortedWith(compareByDescending<ContactSummary> { it.isFavorite }.thenBy { it.displayName.lowercase() }).take(3)
}

private fun String.toT9Digits(): String = uppercase().mapNotNull { char ->
    when (char) {
        in 'A'..'C' -> '2'
        in 'D'..'F' -> '3'
        in 'G'..'I' -> '4'
        in 'J'..'L' -> '5'
        in 'M'..'O' -> '6'
        in 'P'..'S' -> '7'
        in 'T'..'V' -> '8'
        in 'W'..'Z' -> '9'
        else -> null
    }
}.joinToString("")

private fun formatDialPadNumber(number: String): String {
    if (number.isBlank()) return ""
    val prefix = if (number.startsWith('+')) "+" else ""
    val digits = number.removePrefix("+")
    return prefix + digits.chunked(3).joinToString(" ")
}

@Composable
private fun QrCodeDialog(payload: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val arabicUi = isArabicUi()
    var expanded by remember { mutableStateOf(false) }
    val bitmap = remember(payload) { generateQrBitmap(payload, if (expanded) 1200 else 720) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("Share as QR")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = localizedText("QR code"),
                        modifier = Modifier.size(if (expanded) 300.dp else 220.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(12.dp),
                    )
                }
                Text(localizedText("Scan to import contact data quickly."))
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val ok = bitmap?.let { shareBitmap(context, it, payload) } ?: false
                    if (!ok) Toast.makeText(context, localizeText("Unable to share QR", arabicUi), Toast.LENGTH_SHORT).show()
                }) { Text(localizedText("Share")) }
                TextButton(onClick = {
                    val ok = bitmap?.let { saveBitmapToGallery(context, it) } ?: false
                    Toast.makeText(context, localizeText(if (ok) "QR saved to gallery" else "Unable to save QR", arabicUi), Toast.LENGTH_SHORT).show()
                }) { Text(localizedText("Save")) }
                TextButton(onClick = { expanded = !expanded }) { Text(localizedText(if (expanded) "Collapse" else "Expand")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("Close")) } },
    )
}

private fun generateQrBitmap(payload: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}.getOrNull()

private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap): Boolean {
    return runCatching {
        val resolver = context.contentResolver
        val name = "opencontacts_qr_${System.currentTimeMillis()}.png"
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenContacts")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } ?: return false
        true
    }.getOrDefault(false)
}

private fun shareBitmap(context: android.content.Context, bitmap: Bitmap, fallbackText: String): Boolean {
    return runCatching {
        val file = File(context.cacheDir, "qr_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val uri = FileProvider.getUriForFile(context, "com.opencontacts.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, fallbackText)
            clipData = ClipData.newRawUri("QR", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share QR"))
        true
    }.recoverCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, fallbackText)
        }, "Share contact as text"))
        true
    }.getOrDefault(false)
}

@Composable
private fun DrawerFolderCarouselTile(
    folder: com.opencontacts.core.model.FolderSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val arabicUi = isArabicUi()
    val bitmap by rememberBitmapFromUri(context, folder.imageUri, maxSizePx = 720)
    val accent = drawerFolderAccent(folder.colorToken)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 138.dp, height = 126.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        tonalElevation = if (selected) 6.dp else 1.dp,
        shadowElevation = if (selected) 6.dp else 1.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (selected) 0.22f else 0.32f)),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if (bitmap == null) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.16f),
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = folder.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (bitmap != null) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (folder.usageCount == 1) "1 contact" else "${folder.usageCount} contacts",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bitmap != null) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun drawerFolderAccent(token: String): Color = when (token.lowercase()) {
    "green" -> Color(0xFF16A34A)
    "emerald" -> Color(0xFF059669)
    "teal" -> Color(0xFF0F766E)
    "cyan" -> Color(0xFF0891B2)
    "sky" -> Color(0xFF0284C7)
    "violet" -> Color(0xFF7C3AED)
    "purple" -> Color(0xFF9333EA)
    "pink" -> Color(0xFFDB2777)
    "amber" -> Color(0xFFD97706)
    "orange" -> Color(0xFFEA580C)
    "red" -> Color(0xFFDC2626)
    "rose" -> Color(0xFFE11D48)
    "slate" -> Color(0xFF475569)
    else -> Color(0xFF2563EB)
}

@Composable
private fun ClassificationPickerDialog(
    title: String,
    value: String,
    existingNames: List<String>,
    label: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true)
                if (existingNames.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        existingNames.forEach { name ->
                            AssistChip(onClick = { onValueChange(name) }, label = { Text(name) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("Cancel")) } },
    )
}

@Composable
private fun rememberBitmapFromUri(
    context: android.content.Context,
    uri: String?,
    maxSizePx: Int,
) = produceState<Bitmap?>(initialValue = null, key1 = context, key2 = uri, key3 = maxSizePx) {
    value = withContext(Dispatchers.IO) {
        loadBitmapFromUri(context = context, uri = uri, maxSizePx = maxSizePx)
    }
}

private fun loadBitmapFromUri(
    context: android.content.Context,
    uri: String?,
    maxSizePx: Int,
): Bitmap? {
    if (uri.isNullOrBlank()) return null
    return runCatching {
        val file = File(uri)
        if (file.exists()) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
            val sampleSize = generateSequence(1) { it * 2 }
                .takeWhile { largestSide / it > maxSizePx }
                .lastOrNull() ?: 1
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } else {
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
            val sampleSize = generateSequence(1) { it * 2 }
                .takeWhile { largestSide / it > maxSizePx }
                .lastOrNull() ?: 1
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        }
    }.getOrNull()
}

private fun escapeVCardValue(value: String): String = value
    .replace("\\", "\\\\")
    .replace(";", "\\;")
    .replace(",", "\\,")
    .replace("\n", "\\n")

private fun ContactSummary.toVCardPayload(): String = buildString {
    append("BEGIN:VCARD\r\n")
    append("VERSION:3.0\r\n")
    append("FN:").append(escapeVCardValue(displayName)).append("\r\n")
    allPhoneNumbers().forEach { phone ->
        append("TEL;TYPE=")
            .append(escapeVCardValue(phone.type.ifBlank { "CELL" }.uppercase()))
            .append(":")
            .append(escapeVCardValue(phone.value))
            .append("\r\n")
    }
    val noteParts = buildList {
        if (tags.isNotEmpty()) add("Tags=${tags.joinToString(",")}")
        folderName?.takeIf(String::isNotBlank)?.let { add("Folder=$it") }
    }
    if (noteParts.isNotEmpty()) append("NOTE:").append(escapeVCardValue(noteParts.joinToString(" | "))).append("\r\n")
    folderName?.takeIf(String::isNotBlank)?.let { append("ORG:").append(escapeVCardValue(it)).append("\r\n") }
    append("END:VCARD\r\n")
}

private fun formatTime(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
