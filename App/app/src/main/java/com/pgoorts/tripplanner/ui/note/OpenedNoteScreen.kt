@file:OptIn(ExperimentalMaterial3Api::class)

package com.pgoorts.tripplanner.ui.note

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.NoteType
import com.pgoorts.tripplanner.data.local.entity.TripRole
import com.pgoorts.tripplanner.ui.theme.*

@Composable
fun OpenedNoteScreen(
    noteId: String,
    onBack: () -> Unit,
    viewModel: OpenedNoteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val canEdit = uiState.currentUserRole != TripRole.VIEWER

    var noteTitle by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }
    var newChecklistItemText by remember { mutableStateOf("") }
    var showTemplateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.note) {
        uiState.note?.let { note ->
            noteTitle = note.title
            if (note.type != NoteType.CHECKLIST) {
                textContent = note.content
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = noteTitle,
                        onValueChange = {
                            noteTitle = it
                            viewModel.updateNoteTitle(it)
                        },
                        readOnly = !canEdit,
                        placeholder = { Text("Note Title", color = Grey500) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = White,
                            unfocusedTextColor = White
                        ),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy900,
                    navigationIconContentColor = Grey300
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal300)
            }
        } else {
            val note = uiState.note ?: return@Scaffold
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Note Type Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Teal300.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = note.type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = Teal200,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                when (note.type) {
                    NoteType.TEXT_BLOCK -> {
                        OutlinedTextField(
                            value = textContent,
                            onValueChange = {
                                textContent = it
                                viewModel.updateTextContent(it)
                            },
                            readOnly = !canEdit,
                            placeholder = { Text("Start typing your note here...", color = Grey500) },
                            colors = tripTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }

                    NoteType.CHECKLIST -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Checklist Items", style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.Bold)
                            if (canEdit) {
                                TextButton(onClick = { showTemplateDialog = true }) {
                                    Icon(Icons.Filled.Merge, contentDescription = null, tint = Teal300, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Merge Template", color = Teal300, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Add item row
                        if (canEdit) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newChecklistItemText,
                                    onValueChange = { newChecklistItemText = it },
                                    placeholder = { Text("Add new checklist item...", color = Grey500) },
                                    colors = tripTextFieldColors(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (newChecklistItemText.isNotBlank()) {
                                            viewModel.addChecklistItem(newChecklistItemText)
                                            newChecklistItemText = ""
                                        }
                                    },
                                    enabled = newChecklistItemText.isNotBlank(),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (newChecklistItemText.isNotBlank()) Teal300 else Navy700)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Add Item",
                                        tint = if (newChecklistItemText.isNotBlank()) Navy950 else Grey500
                                    )
                                }
                            }
                        }

                        // Checklist items list
                        if (uiState.checklistItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No items in checklist. Add some or merge a template!", color = Grey500)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(uiState.checklistItems) { index, item ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Navy700),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = item.isChecked,
                                                onCheckedChange = { viewModel.toggleChecklistItem(index) },
                                                enabled = canEdit,
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Teal300,
                                                    checkmarkColor = Navy950
                                                )
                                            )
                                            Text(
                                                text = item.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (item.isChecked) Grey500 else White,
                                                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(start = 8.dp)
                                            )
                                            if (canEdit) {
                                                IconButton(onClick = { viewModel.removeChecklistItem(index) }) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Grey700, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    NoteType.WEB_URL, NoteType.GOOGLE_DOC, NoteType.GOOGLE_DRIVE -> {
                        // URL Link input
                        OutlinedTextField(
                            value = textContent,
                            onValueChange = {
                                textContent = it
                                viewModel.updateTextContent(it)
                            },
                            readOnly = !canEdit,
                            label = { Text("Link URL", color = Grey500) },
                            singleLine = true,
                            colors = tripTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Launch/Preview Card
                        Spacer(Modifier.height(8.dp))
                        Card(
                            onClick = {
                                if (textContent.isNotBlank()) {
                                    var formattedUrl = textContent.trim()
                                    if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                                        formattedUrl = "https://$formattedUrl"
                                    }
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Invalid link or no browser found", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter a URL first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = Navy700),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val (color, icon) = getNoteTypeVisuals(note.type)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(color.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (textContent.isBlank()) "No link entered" else "Open External Link",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = White
                                    )
                                    if (textContent.isNotBlank()) {
                                        Text(
                                            text = textContent,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Grey500,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(Icons.Filled.OpenInNew, contentDescription = "Open Link", tint = Teal300)
                            }
                        }
                    }

                    NoteType.PKPASS -> {
                        // Wallet-style pass rendering (barcode + pass fields) lands in Phase 3 Block 5.
                        Text("Pass rendering coming soon", color = Grey500)
                    }
                }
            }
        }
    }

    if (showTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            containerColor = Navy800,
            title = { Text("Merge Packing Template", color = White) },
            text = {
                if (uiState.templates.isEmpty()) {
                    Text("No templates found. Create packing templates in your profile screen.", color = Grey500)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.templates.size) { index ->
                            val template = uiState.templates[index]
                            Card(
                                onClick = {
                                    viewModel.mergeTemplate(template)
                                    showTemplateDialog = false
                                },
                                colors = CardDefaults.cardColors(containerColor = Navy700),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(template.title, style = MaterialTheme.typography.bodyMedium, color = White, fontWeight = FontWeight.SemiBold)
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Grey500)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTemplateDialog = false }) {
                    Text("Cancel", color = Grey300)
                }
            }
        )
    }
}

@Composable
private fun tripTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Grey100,
    unfocusedTextColor = Grey100,
    focusedBorderColor = Teal300,
    unfocusedBorderColor = Grey700,
    focusedLabelColor = Teal300,
    cursorColor = Teal300
)

private fun getNoteTypeVisuals(type: NoteType): Pair<Color, ImageVector> = when (type) {
    NoteType.TEXT_BLOCK   -> Pair(Grey300, Icons.Filled.Article)
    NoteType.CHECKLIST    -> Pair(Teal300, Icons.Filled.Checklist)
    NoteType.WEB_URL      -> Pair(FlightBlue, Icons.Filled.Link)
    NoteType.GOOGLE_DOC   -> Pair(ActivityTeal, Icons.Filled.Description)
    NoteType.GOOGLE_DRIVE -> Pair(LodgingPurple, Icons.Filled.Cloud)
    NoteType.PKPASS       -> Pair(Teal300, Icons.Filled.CreditCard)
}
