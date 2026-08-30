@file:OptIn(ExperimentalMaterial3Api::class)

package com.pgoorts.tripplanner.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pgoorts.tripplanner.auth.GoogleAuthClient
import com.pgoorts.tripplanner.data.local.entity.PackingTemplateEntity
import com.pgoorts.tripplanner.ui.components.ConfirmDeleteDialog
import com.pgoorts.tripplanner.ui.components.TimezonePickerDialog
import com.pgoorts.tripplanner.ui.theme.*
import kotlinx.coroutines.launch

private val SyncIntervalPresets = listOf(5, 15, 30, 60)

@Composable
fun SettingsScreen(
    googleAuthClient: GoogleAuthClient,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val innerCircle by viewModel.innerCircle.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val defaultTimezone by viewModel.defaultTimezone.collectAsState()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsState()

    var showAddContactDialog by remember { mutableStateOf(false) }
    var showAddTemplateDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<PackingTemplateEntity?>(null) }
    var showTimezonePicker by remember { mutableStateOf(false) }
    var pendingDeleteContact by remember { mutableStateOf<String?>(null) }
    var pendingDeleteTemplate by remember { mutableStateOf<PackingTemplateEntity?>(null) }

    Scaffold(
        containerColor = Navy900,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            googleAuthClient.signOut()
                            onSignOut()
                        }
                    }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sign Out", tint = ErrorRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy900,
                    titleContentColor = White,
                    navigationIconContentColor = Grey300
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // --- User Info Card ---
            item {
                Spacer(Modifier.height(12.dp))
                UserInfoCard(viewModel)
                Spacer(Modifier.height(20.dp))
            }

            // --- Preferences Section ---
            item {
                Text(
                    "Preferences",
                    style = MaterialTheme.typography.titleSmall.copy(color = Teal300, fontWeight = FontWeight.Bold)
                )
                Divider(color = Navy700, thickness = 1.dp)
                Spacer(Modifier.height(8.dp))
                PreferencesCard(
                    defaultTimezone = defaultTimezone,
                    syncIntervalMinutes = syncIntervalMinutes,
                    onDefaultTimezoneClick = { showTimezonePicker = true },
                    onSyncIntervalSelected = { viewModel.setSyncIntervalMinutes(it) }
                )
                Spacer(Modifier.height(20.dp))
            }

            // --- Inner Circle Section ---
            item {
                SectionHeader(title = "Favorite Contacts (Inner Circle)", onAdd = { showAddContactDialog = true })
                Spacer(Modifier.height(8.dp))
            }
            if (innerCircle.isEmpty()) {
                item {
                    EmptyHint("No favorite contacts yet. Add email addresses for quick trip sharing.")
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                items(innerCircle, key = { it }) { email ->
                    ContactRow(email = email, onDelete = { pendingDeleteContact = email })
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            // --- Packing Templates Section ---
            item {
                SectionHeader(title = "Packing List Templates", onAdd = { showAddTemplateDialog = true })
                Spacer(Modifier.height(8.dp))
            }
            if (templates.isEmpty()) {
                item {
                    EmptyHint("No packing templates yet. Create reusable checklists like \"Plane Items\" or \"Medication\".")
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                items(templates, key = { it.id }) { template ->
                    TemplateRow(
                        template = template,
                        items = viewModel.decodeItems(template.items),
                        onEdit = { editingTemplate = template },
                        onDelete = { pendingDeleteTemplate = template }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // --- Timezone Picker Dialog ---
    if (showTimezonePicker) {
        TimezonePickerDialog(
            onDismiss = { showTimezonePicker = false },
            onSelect = { zone ->
                viewModel.setDefaultTimezone(zone)
                showTimezonePicker = false
            }
        )
    }

    // --- Add Contact Dialog ---
    if (showAddContactDialog) {
        AddContactDialog(
            onDismiss = { showAddContactDialog = false },
            onAdd = { email ->
                if (email.isNotBlank() && !innerCircle.contains(email.trim())) {
                    viewModel.saveInnerCircle(innerCircle + email.trim())
                }
                showAddContactDialog = false
            }
        )
    }

    // --- Add Template Dialog ---
    if (showAddTemplateDialog) {
        AddEditTemplateDialog(
            initialTitle = "",
            initialItems = emptyList(),
            onDismiss = { showAddTemplateDialog = false },
            onSave = { title, items ->
                viewModel.createTemplate(title, items)
                showAddTemplateDialog = false
            }
        )
    }

    // --- Edit Template Dialog ---
    editingTemplate?.let { tmpl ->
        AddEditTemplateDialog(
            initialTitle = tmpl.title,
            initialItems = viewModel.decodeItems(tmpl.items),
            onDismiss = { editingTemplate = null },
            onSave = { title, items ->
                viewModel.updateTemplate(tmpl, title, items)
                editingTemplate = null
            }
        )
    }

    // --- Delete confirmations ---
    pendingDeleteContact?.let { email ->
        ConfirmDeleteDialog(
            title = "Remove contact?",
            message = "\"$email\" will be removed from your Inner Circle.",
            onConfirm = {
                val updated = innerCircle.toMutableList().also { it.remove(email) }
                viewModel.saveInnerCircle(updated)
                pendingDeleteContact = null
            },
            onDismiss = { pendingDeleteContact = null }
        )
    }
    pendingDeleteTemplate?.let { template ->
        ConfirmDeleteDialog(
            title = "Delete template?",
            message = "\"${template.title}\" will be permanently deleted. This can't be undone.",
            onConfirm = { viewModel.deleteTemplate(template); pendingDeleteTemplate = null },
            onDismiss = { pendingDeleteTemplate = null }
        )
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun UserInfoCard(viewModel: SettingsViewModel) {
    val name = viewModel.userSessionManager.userDisplayName ?: "Unknown User"
    val email = viewModel.userSessionManager.userEmail ?: ""
    val photoUrl = viewModel.userSessionManager.userPhotoUrl

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Navy700)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Profile photo",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Navy600)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Teal300),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = White, fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            Column {
                Text(name, style = MaterialTheme.typography.titleMedium.copy(color = White, fontWeight = FontWeight.Bold))
                Text(email, style = MaterialTheme.typography.bodySmall.copy(color = Grey300))
            }
        }
    }
}

@Composable
private fun PreferencesCard(
    defaultTimezone: String?,
    syncIntervalMinutes: Int,
    onDefaultTimezoneClick: () -> Unit,
    onSyncIntervalSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Navy700)
    ) {
        Column {
            // --- Default timezone row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDefaultTimezoneClick)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PreferenceIcon(Icons.Filled.Public)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Default timezone", style = MaterialTheme.typography.bodyMedium.copy(color = Grey100, fontWeight = FontWeight.Medium))
                    Text("Used for new trips and events", style = MaterialTheme.typography.bodySmall.copy(color = Grey500))
                }
                Text(
                    text = defaultTimezone ?: "Not set",
                    style = MaterialTheme.typography.bodySmall.copy(color = Grey300),
                    maxLines = 1
                )
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Grey500, modifier = Modifier.size(18.dp))
            }
            Divider(color = Navy900, thickness = 1.dp, modifier = Modifier.padding(start = 14.dp))

            // --- Sync interval row ---
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreferenceIcon(Icons.Filled.Sync)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync interval", style = MaterialTheme.typography.bodyMedium.copy(color = Grey100, fontWeight = FontWeight.Medium))
                        Text("How often the app syncs in the background", style = MaterialTheme.typography.bodySmall.copy(color = Grey500))
                    }
                }
                Row(
                    modifier = Modifier.padding(start = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SyncIntervalPresets.forEach { minutes ->
                        SyncIntervalChip(
                            minutes = minutes,
                            selected = minutes == syncIntervalMinutes,
                            onClick = { onSyncIntervalSelected(minutes) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Teal300.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Teal300, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SyncIntervalChip(minutes: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Teal300 else Navy900,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Grey700)
    ) {
        Text(
            text = "$minutes min",
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (selected) Navy950 else Grey300,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(color = Teal300, fontWeight = FontWeight.Bold)
        )
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Add", tint = Teal300, modifier = Modifier.size(20.dp))
        }
    }
    Divider(color = Navy700, thickness = 1.dp)
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(color = Grey500),
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ContactRow(email: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Grey500, modifier = Modifier.size(18.dp))
            Text(email, style = MaterialTheme.typography.bodyMedium.copy(color = Grey100))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = ErrorRed, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TemplateRow(
    template: PackingTemplateEntity,
    items: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Navy700)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(template.title, style = MaterialTheme.typography.bodyMedium.copy(color = White, fontWeight = FontWeight.SemiBold))
                Text(
                    "${items.size} item${if (items.size != 1) "s" else ""}: ${items.take(3).joinToString(", ")}${if (items.size > 3) "…" else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Grey500)
                )
            }
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Teal300, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = { Text("Add Favorite Contact", color = White) },
        text = {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address", color = Grey500) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Grey100,
                    unfocusedTextColor = Grey100,
                    focusedBorderColor = Teal300,
                    unfocusedBorderColor = Grey700,
                    focusedLabelColor = Teal300,
                    cursorColor = Teal300
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(email) }) {
                Text("Add", color = Teal300)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Grey500) }
        }
    )
}

@Composable
private fun AddEditTemplateDialog(
    initialTitle: String,
    initialItems: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var itemsText by remember { mutableStateOf(initialItems.joinToString("\n")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = { Text(if (initialTitle.isEmpty()) "New Template" else "Edit Template", color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Template name", color = Grey500) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors()
                )
                OutlinedTextField(
                    value = itemsText,
                    onValueChange = { itemsText = it },
                    label = { Text("Items (one per line)", color = Grey500) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 220.dp),
                    maxLines = 10,
                    colors = dialogTextFieldColors()
                )
                Text("Enter one item per line", style = MaterialTheme.typography.bodySmall.copy(color = Grey700))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val items = itemsText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                onSave(title.trim(), items)
            }) {
                Text("Save", color = Teal300, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Grey500) }
        }
    )
}

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Grey100,
    unfocusedTextColor = Grey100,
    focusedBorderColor = Teal300,
    unfocusedBorderColor = Grey700,
    focusedLabelColor = Teal300,
    cursorColor = Teal300
)
