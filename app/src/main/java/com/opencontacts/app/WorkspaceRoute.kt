package com.opencontacts.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.ui.ReliableOutlinedTextField
import com.opencontacts.core.crypto.AppLockRepository
import com.opencontacts.core.crypto.AppLockSettings
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.allPhoneNumbers
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.TagSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val classificationColorPalette = listOf("blue", "green", "emerald", "teal", "cyan", "sky", "violet", "purple", "pink", "rose", "amber", "orange", "red", "slate")
private val folderColorPalette = classificationColorPalette
private val tagColorPalette = classificationColorPalette
private val folderIconPalette = listOf("folder", "work", "home", "star", "archive", "group")

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun WorkspaceRoute(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var folderEditor by remember { mutableStateOf<FolderEditorState?>(null) }
    var tagEditor by remember { mutableStateOf<TagEditorState?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedContactIds by remember { mutableStateOf(setOf<String>()) }
    var addContactsDialog by remember { mutableStateOf(false) }
    var pendingDeleteFolder by remember { mutableStateOf<FolderSummary?>(null) }
    var pendingDeleteTag by remember { mutableStateOf<TagSummary?>(null) }
    var tagSearchQuery by remember { mutableStateOf("") }
    var debouncedTagSearchQuery by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(tagSearchQuery) {
        delay(250)
        debouncedTagSearchQuery = tagSearchQuery
    }

    val activeFolder = folders.firstOrNull { it.name == selectedFolder }
    val activeTag = tags.firstOrNull { it.name == selectedTag }
    val filteredContacts = remember(contacts, selectedTag, selectedFolder) {
        when {
            selectedTag != null -> contacts.filter { selectedTag in it.tags }
            selectedFolder != null -> contacts.filter {
                selectedFolder == it.folderName || it.folderNames.any { folder -> folder.equals(selectedFolder, ignoreCase = true) }
            }
            else -> emptyList()
        }
    }
    val activeContainerTitle = activeFolder?.name ?: activeTag?.name
    val selectionMode = selectedContactIds.isNotEmpty()

    val displayedFolders = remember(folders, settings.hideEmptyFoldersAndTags) {
        if (settings.hideEmptyFoldersAndTags) folders.filter { it.usageCount > 0 } else folders
    }
    val displayedTags = remember(tags, settings.hideEmptyFoldersAndTags, settings.groupTagSortOrder, debouncedTagSearchQuery) {
        val source = if (settings.hideEmptyFoldersAndTags) tags.filter { it.usageCount > 0 } else tags
        source.filter { debouncedTagSearchQuery.isBlank() || it.name.contains(debouncedTagSearchQuery.trim(), ignoreCase = true) }
            .sortedWith(
                when (settings.groupTagSortOrder.uppercase()) {
                    "ALPHABETICAL" -> compareBy<TagSummary> { it.name.lowercase() }
                    "RECENT" -> compareByDescending<TagSummary> { it.usageCount }.thenByDescending { it.name.length }
                    else -> compareByDescending<TagSummary> { it.usageCount }.thenBy { it.name.lowercase() }
                }
            )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WorkspaceHeader(
                title = activeContainerTitle ?: "Groups, folders & tags",
                subtitle = when {
                    activeFolder != null -> "${filteredContacts.size} contact(s) inside this folder"
                    activeTag != null -> "${filteredContacts.size} contact(s) with this tag"
                    else -> "Keep folders visual, tags lightweight, and organization fast."
                },
                accent = activeFolder?.colorToken ?: activeTag?.colorToken ?: "blue",
                onBack = if (activeContainerTitle == null) onBack else {
                    {
                        selectedFolder = null
                        selectedTag = null
                        selectedContactIds = emptySet()
                    }
                },
                onEdit = {
                    when {
                        activeFolder != null -> folderEditor = activeFolder.toEditorState()
                        activeTag != null -> tagEditor = TagEditorState(originalName = activeTag.name, name = activeTag.name, colorToken = activeTag.colorToken)
                    }
                },
                onDelete = {
                    activeFolder?.let { pendingDeleteFolder = it }
                    activeTag?.let { pendingDeleteTag = it }
                },
                canEdit = activeContainerTitle != null,
            )

            if (activeContainerTitle == null) {
                FolderLane(
                    folders = displayedFolders,
                    selectedFolder = selectedFolder,
                    onSelect = {
                        selectedFolder = it
                        selectedTag = null
                        selectedContactIds = emptySet()
                    },
                    onAdd = { folderEditor = FolderEditorState() },
                    onEdit = { folderEditor = it.toEditorState() },
                )

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Label, null)
                                Text("Tags", style = MaterialTheme.typography.titleLarge)
                            }
                            IconButton(onClick = { tagEditor = TagEditorState() }) { Icon(Icons.Default.Add, contentDescription = "Add tag") }
                        }
                        ReliableOutlinedTextField(
                            value = tagSearchQuery,
                            onValueChange = { tagSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search tags") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            displayedTags.take(24).forEach { tag ->
                                FilterChip(
                                    selected = tag.name == selectedTag,
                                    onClick = {
                                        selectedTag = tag.name
                                        selectedFolder = null
                                        selectedContactIds = emptySet()
                                    },
                                    label = { Text(if (tag.usageCount > 0) "${tag.name} · ${tag.usageCount}" else tag.name) },
                                    leadingIcon = { Icon(Icons.Default.Label, null, tint = accentColor(tag.colorToken)) },
                                )
                            }
                        }
                    }
                }
            } else {
                if (selectionMode) {
                    WorkspaceSelectionBar(
                        count = selectedContactIds.size,
                        onAssign = { addContactsDialog = true },
                        onClear = { selectedContactIds = emptySet() },
                        onRemove = {
                            if (selectedFolder != null) viewModel.removeFolderFromContacts(selectedContactIds, selectedFolder!!)
                            if (selectedTag != null) viewModel.removeTagFromContacts(selectedContactIds, selectedTag!!)
                            selectedContactIds = emptySet()
                        },
                    )
                }

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selectedFolder != null) "Folder contents" else "Tagged contacts", style = MaterialTheme.typography.titleLarge)
                            Row {
                                IconButton(onClick = { addContactsDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add contacts") }
                                if (selectedContactIds.isNotEmpty()) {
                                    TextButton(onClick = { selectedContactIds = emptySet() }) { Text("Clear") }
                                }
                            }
                        }
                        if (filteredContacts.isEmpty()) {
                            Text(
                                if (selectedFolder != null) "No contacts are linked to this folder yet." else "No contacts are linked to this tag yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                items(filteredContacts, key = { it.id }) { contact ->
                                    ContactMiniCard(
                                        contact = contact,
                                        selected = contact.id in selectedContactIds,
                                        selectionMode = selectionMode,
                                        onOpen = {
                                            if (selectionMode) {
                                                selectedContactIds = if (contact.id in selectedContactIds) selectedContactIds - contact.id else selectedContactIds + contact.id
                                            } else {
                                                onOpenDetails(contact.id)
                                            }
                                        },
                                        onLongPress = {
                                            selectedContactIds = if (contact.id in selectedContactIds) selectedContactIds - contact.id else selectedContactIds + contact.id
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    folderEditor?.let { state ->
        val context = LocalContext.current
        val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val persisted = uri?.let { persistFolderCover(context, it) }
            folderEditor = state.copy(imageUri = persisted)
        }
        FolderEditorDialog(
            state = state,
            onDismiss = { folderEditor = null },
            onStateChange = { folderEditor = it },
            onPickPhoto = { photoPicker.launch("image/*") },
            onConfirm = {
                viewModel.saveFolder(folderEditor!!.toSummary())
                if (state.originalName != null && state.originalName != state.name.trim()) {
                    viewModel.renameFolder(state.originalName, state.name.trim())
                }
                folderEditor = null
            },
        )
    }

    tagEditor?.let { state ->
        TagEditorDialog(
            state = state,
            onDismiss = { tagEditor = null },
            onStateChange = { tagEditor = it },
            onConfirm = {
                val cleanName = state.name.trim().removePrefix("#")
                if (cleanName.isNotBlank()) {
                    if (state.originalName != null && state.originalName != cleanName) {
                        viewModel.renameTag(state.originalName.orEmpty(), cleanName, state.colorToken)
                    } else {
                        viewModel.saveTag(TagSummary(name = cleanName, colorToken = state.colorToken))
                    }
                }
                tagEditor = null
            },
        )
    }

    pendingDeleteFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingDeleteFolder = null },
            title = { Text("Delete folder") },
            text = { Text("This deletes the folder only. Contacts inside it will stay محفوظة وسيتم فقط فك الربط من هذا الفولدر دون حذف أي جهة اتصال.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folder.name)
                    if (selectedFolder == folder.name) {
                        selectedFolder = null
                        selectedContactIds = emptySet()
                    }
                    pendingDeleteFolder = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteFolder = null }) { Text("Cancel") } },
        )
    }

    pendingDeleteTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("Delete tag") },
            text = { Text("This removes the tag classification from linked contacts and deletes the tag itself. Contacts will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(tag.name)
                    if (selectedTag == tag.name) {
                        selectedTag = null
                        selectedContactIds = emptySet()
                    }
                    pendingDeleteTag = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteTag = null }) { Text("Cancel") } },
        )
    }

    if (addContactsDialog && activeContainerTitle != null) {
        AddContactsDialog(
            title = if (selectedFolder != null) "Add contacts to folder" else "Add contacts to tag",
            contacts = contacts,
            alreadyIncludedIds = filteredContacts.map { it.id }.toSet(),
            onDismiss = { addContactsDialog = false },
            onConfirm = { ids ->
                if (selectedFolder != null) viewModel.assignFolderToContacts(ids, selectedFolder!!)
                if (selectedTag != null) viewModel.assignTagToContacts(ids, selectedTag!!)
                selectedContactIds = emptySet()
                addContactsDialog = false
            },
        )
    }
}

@Composable
private fun WorkspaceHeader(
    title: String,
    subtitle: String,
    accent: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canEdit: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = accentColor(accent).copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accentColor(accent).copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            if (canEdit) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}

@Composable
private fun FolderLane(
    folders: List<FolderSummary>,
    selectedFolder: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (FolderSummary) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null)
                    Text("Folders", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Visual organization",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                items(folders, key = { it.name }) { folder ->
                    FolderTile(
                        folder = folder,
                        selected = folder.name == selectedFolder,
                        onClick = { onSelect(folder.name) },
                        onLongPress = { onEdit(folder) },
                    )
                }
                item("add-folder") {
                    AddFolderTile(onClick = onAdd)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(
    folder: FolderSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val bitmap by rememberFolderBitmap(folder.imageUri)
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "folderSelectionScale",
    )
    val overlayAlpha = if (bitmap != null) 0.34f else 0.14f
    val accent = accentColor(folder.colorToken)
    Surface(
        modifier = Modifier
            .size(width = 164.dp, height = 148.dp)
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(28.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        shadowElevation = if (selected) 10.dp else 2.dp,
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
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.08f),
                                    accent.copy(alpha = overlayAlpha),
                                    Color.Black.copy(alpha = 0.42f),
                                ),
                            ),
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.20f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                                ),
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = if (bitmap != null) 0.22f else 0.12f),
                        modifier = Modifier.size(60.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (bitmap == null) {
                                Icon(folderIcon(folder.iconToken), contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    if (folder.isPinned) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.18f)) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                tint = if (bitmap != null) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(7.dp).size(16.dp),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (bitmap != null) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (folder.usageCount == 1) "1 contact" else "${folder.usageCount} contacts",
                            style = MaterialTheme.typography.labelLarge.copy(
                                shadow = Shadow(
                                    color = accent.copy(alpha = 0.72f),
                                    blurRadius = 14f,
                                ),
                            ),
                            color = lerp(accent, Color.White, if (bitmap != null) 0.42f else 0.18f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        folder.description?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (bitmap != null) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFolderTile(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(width = 164.dp, height = 148.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("New folder", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun WorkspaceSelectionBar(
    count: Int,
    onAssign: () -> Unit,
    onRemove: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$count selected", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAssign) { Text("Assign") }
                TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun ContactMiniCard(contact: ContactSummary, selected: Boolean, selectionMode: Boolean, onOpen: () -> Unit, onLongPress: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Box(
                    modifier = Modifier.size(22.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(contact.primaryPhone ?: "No phone", style = MaterialTheme.typography.bodyMedium)
                if (contact.tags.isNotEmpty()) Text(contact.tags.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddContactsDialog(
    title: String,
    contacts: List<ContactSummary>,
    alreadyIncludedIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    val availableContacts = remember(contacts, alreadyIncludedIds, query) {
        val needle = query.trim()
        contacts
            .filterNot { it.id in alreadyIncludedIds }
            .filter {
                needle.isBlank() ||
                    it.displayName.contains(needle, ignoreCase = true) ||
                    (it.primaryPhone?.contains(needle, ignoreCase = true) == true)
            }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReliableOutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search name or number") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableContacts, key = { it.id }) { contact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                selected = if (contact.id in selected) selected - contact.id else selected + contact.id
                            },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(20.dp).clip(CircleShape).background(if (contact.id in selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            )
                            Column {
                                Text(contact.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(contact.primaryPhone ?: "No phone", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FolderEditorDialog(
    state: FolderEditorState,
    onDismiss: () -> Unit,
    onStateChange: (FolderEditorState) -> Unit,
    onPickPhoto: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.originalName == null) "New folder" else "Edit folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReliableOutlinedTextField(value = state.name, onValueChange = { onStateChange(state.copy(name = it)) }, label = { Text("Folder name") }, singleLine = true)
                ReliableOutlinedTextField(value = state.description.orEmpty(), onValueChange = { onStateChange(state.copy(description = it)) }, label = { Text("Short description") }, maxLines = 2)
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    folderColorPalette.forEach { color ->
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = accentColor(color),
                            border = BorderStroke(2.dp, if (state.colorToken == color) MaterialTheme.colorScheme.onSurface else Color.Transparent),
                            onClick = { onStateChange(state.copy(colorToken = color)) },
                        ) {}
                    }
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    folderIconPalette.forEach { icon ->
                        AssistChip(
                            onClick = { onStateChange(state.copy(iconToken = icon)) },
                            label = { Text(icon) },
                            leadingIcon = { Icon(folderIcon(icon), null) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPickPhoto) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.imageUri.isNullOrBlank()) "Pick image" else "Change image")
                    }
                    if (!state.imageUri.isNullOrBlank()) {
                        TextButton(onClick = { onStateChange(state.copy(imageUri = null)) }) { Text("Remove") }
                    }
                }
                FilterChip(selected = state.isPinned, onClick = { onStateChange(state.copy(isPinned = !state.isPinned)) }, label = { Text("Pinned") }, leadingIcon = { Icon(Icons.Default.PushPin, null) })
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = state.name.trim().isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TagEditorDialog(
    state: TagEditorState,
    onDismiss: () -> Unit,
    onStateChange: (TagEditorState) -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.originalName == null) "New tag" else "Edit tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReliableOutlinedTextField(value = state.name, onValueChange = { onStateChange(state.copy(name = it)) }, label = { Text("Tag name") }, singleLine = true)
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tagColorPalette.forEach { color ->
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = accentColor(color),
                            border = BorderStroke(2.dp, if (state.colorToken == color) MaterialTheme.colorScheme.onSurface else Color.Transparent),
                            onClick = { onStateChange(state.copy(colorToken = color)) },
                        ) {}
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = state.name.trim().isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun accentColor(token: String): Color = when (token.lowercase()) {
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

private fun folderIcon(token: String) = when (token.lowercase()) {
    "star" -> Icons.Default.PushPin
    "group" -> Icons.Default.Label
    else -> Icons.Default.Folder
}

private data class FolderEditorState(
    val originalName: String? = null,
    val name: String = "",
    val iconToken: String = "folder",
    val colorToken: String = "blue",
    val imageUri: String? = null,
    val description: String? = null,
    val isPinned: Boolean = false,
) {
    fun toSummary(): FolderSummary = FolderSummary(
        name = name.trim(),
        iconToken = iconToken,
        colorToken = colorToken,
        imageUri = imageUri,
        description = description?.trim()?.takeIf { it.isNotBlank() },
        isPinned = isPinned,
    )
}

private data class TagEditorState(
    val originalName: String? = null,
    val name: String = "",
    val colorToken: String = "blue",
)

private fun FolderSummary.toEditorState() = FolderEditorState(
    originalName = name,
    name = name,
    iconToken = iconToken,
    colorToken = colorToken,
    imageUri = imageUri,
    description = description,
    isPinned = isPinned,
)


@Composable
private fun rememberFolderBitmap(uri: String?): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, key1 = context, key2 = uri) {
        value = withContext(Dispatchers.IO) {
            if (uri.isNullOrBlank()) null else runCatching {
                val file = File(uri)
                when {
                    file.exists() -> BitmapFactory.decodeFile(file.absolutePath)
                    else -> context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        }
    }
}

private fun persistFolderCover(context: android.content.Context, uri: Uri): String? = runCatching {
    val directory = File(context.filesDir, "folder_covers").apply { mkdirs() }
    val target = File(directory, "folder_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(target).use { output -> input.copyTo(output) }
    }
    target.absolutePath
}.getOrNull()

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val vaultSessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
    appLockRepository: AppLockRepository,
) : ViewModel() {
    val settings = appLockRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockSettings.DEFAULT)

    val tags = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeTags(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeFolders(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contacts = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeContacts(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveTag(tag: TagSummary) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.upsertTag(vaultId, tag) }
    }

    fun saveFolder(folder: FolderSummary) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.upsertFolder(vaultId, folder) }
    }

    fun deleteTag(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val affectedContacts = contacts.value.filter { name in it.tags }
        viewModelScope.launch {
            affectedContacts.forEach { current ->
                contactRepository.saveContactDraft(
                    vaultId,
                    ContactDraft(
                        id = current.id,
                        displayName = current.displayName,
                        primaryPhone = current.primaryPhone, phoneNumbers = current.allPhoneNumbers(), tags = current.tags.filterNot { it == name },
                        isFavorite = current.isFavorite,
                        folderName = current.folderName,
                        folderNames = current.folderNames,
                        photoUri = current.photoUri,
                        isBlocked = current.isBlocked,
                        blockMode = current.blockMode,
                        socialLinks = current.socialLinks,
                    ),
                )
            }
            contactRepository.deleteTag(vaultId, name)
        }
    }

    fun deleteFolder(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val affectedContacts = contacts.value.filter { it.folderName == name || it.folderNames.any { folder -> folder.equals(name, ignoreCase = true) } }
        viewModelScope.launch {
            affectedContacts.forEach { current ->
                contactRepository.saveContactDraft(
                    vaultId,
                    ContactDraft(
                        id = current.id,
                        displayName = current.displayName,
                        primaryPhone = current.primaryPhone, phoneNumbers = current.allPhoneNumbers(), tags = current.tags,
                        isFavorite = current.isFavorite,
                        folderName = current.folderNames.filterNot { it.equals(name, ignoreCase = true) }.firstOrNull(),
                        folderNames = current.folderNames.filterNot { it.equals(name, ignoreCase = true) },
                        photoUri = current.photoUri,
                        isBlocked = current.isBlocked,
                        blockMode = current.blockMode,
                        socialLinks = current.socialLinks,
                    ),
                )
            }
            contactRepository.deleteFolder(vaultId, name)
        }
    }

    fun renameTag(oldName: String, newName: String, colorToken: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            if (oldName != newName) {
                contactRepository.upsertTag(vaultId, TagSummary(name = newName, colorToken = colorToken))
                contacts.value.filter { oldName in it.tags }.forEach { current ->
                    contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, phoneNumbers = current.allPhoneNumbers(), tags = current.tags.map { if (it == oldName) newName else it }, isFavorite = current.isFavorite, folderName = current.folderName, folderNames = current.folderNames, photoUri = current.photoUri, isBlocked = current.isBlocked, blockMode = current.blockMode, socialLinks = current.socialLinks))
                }
                contactRepository.deleteTag(vaultId, oldName)
            } else {
                contactRepository.upsertTag(vaultId, TagSummary(name = newName, colorToken = colorToken))
            }
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            if (oldName != newName) {
                val oldFolder = folders.value.firstOrNull { it.name == oldName }
                val existingTarget = folders.value.firstOrNull { it.name == newName }
                val mergedFolder = when {
                    oldFolder != null && existingTarget != null -> existingTarget.copy(
                        usageCount = maxOf(existingTarget.usageCount, oldFolder.usageCount),
                    )
                    oldFolder != null -> oldFolder.copy(name = newName)
                    existingTarget != null -> existingTarget
                    else -> FolderSummary(name = newName)
                }
                contactRepository.upsertFolder(vaultId, mergedFolder)
                contacts.value.filter { it.folderName == oldName || it.folderNames.any { folder -> folder.equals(oldName, ignoreCase = true) } }.forEach { current ->
                    val updatedFolders = current.folderNames.ifEmpty { listOfNotNull(current.folderName) }.map { if (it.equals(oldName, ignoreCase = true)) newName else it }.distinct()
                    contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, phoneNumbers = current.allPhoneNumbers(), tags = current.tags, isFavorite = current.isFavorite, folderName = updatedFolders.firstOrNull(), folderNames = updatedFolders, photoUri = current.photoUri, isBlocked = current.isBlocked, blockMode = current.blockMode, socialLinks = current.socialLinks))
                }
                contactRepository.deleteFolder(vaultId, oldName)
            }
        }
    }

    fun removeTagFromContacts(contactIds: Set<String>, tag: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, phoneNumbers = current.allPhoneNumbers(), tags = current.tags.filterNot { it == tag }, isFavorite = current.isFavorite, folderName = current.folderName, folderNames = current.folderNames, photoUri = current.photoUri, isBlocked = current.isBlocked, blockMode = current.blockMode, socialLinks = current.socialLinks))
            }
        }
    }

    fun removeFolderFromContacts(contactIds: Set<String>, folderName: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                val remainingFolders = current.folderNames.ifEmpty { listOfNotNull(current.folderName) }.filterNot { it.equals(folderName, ignoreCase = true) }
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, phoneNumbers = current.allPhoneNumbers(), tags = current.tags, isFavorite = current.isFavorite, folderName = remainingFolders.firstOrNull(), folderNames = remainingFolders, photoUri = current.photoUri, isBlocked = current.isBlocked, blockMode = current.blockMode, socialLinks = current.socialLinks))
            }
        }
    }

    fun assignFolderToContacts(contactIds: Set<String>, folder: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val cleanFolder = folder.trim().ifBlank { return }
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            val existingFolder = folders.value.firstOrNull { it.name == cleanFolder } ?: FolderSummary(name = cleanFolder)
            contactRepository.upsertFolder(vaultId, existingFolder)
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                val mergedFolders = (current.folderNames.ifEmpty { listOfNotNull(current.folderName) } + cleanFolder)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, phoneNumbers = current.allPhoneNumbers(), tags = current.tags, isFavorite = current.isFavorite, folderName = mergedFolders.firstOrNull(), folderNames = mergedFolders, photoUri = current.photoUri, isBlocked = current.isBlocked, blockMode = current.blockMode, socialLinks = current.socialLinks))
            }
        }
    }

    fun assignTagToContacts(contactIds: Set<String>, tag: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val cleanTag = tag.trim().removePrefix("#").ifBlank { return }
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            val existingTag = tags.value.firstOrNull { it.name.equals(cleanTag, ignoreCase = true) }
            contactRepository.upsertTag(vaultId, TagSummary(name = cleanTag, colorToken = existingTag?.colorToken ?: "blue"))
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, phoneNumbers = current.allPhoneNumbers(), tags = (current.tags.filterNot { it.equals(cleanTag, ignoreCase = true) } + cleanTag).distinct(), isFavorite = current.isFavorite, folderName = current.folderName, folderNames = current.folderNames, photoUri = current.photoUri, isBlocked = current.isBlocked, blockMode = current.blockMode, socialLinks = current.socialLinks))
            }
        }
    }
}
