@file:OptIn(ExperimentalMaterial3Api::class)

package com.pgoorts.tripplanner.ui.note

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.pgoorts.tripplanner.data.local.entity.DrivePickerContent
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.NoteType
import com.pgoorts.tripplanner.data.local.entity.TripRole
import com.pgoorts.tripplanner.files.FileNoteContent
import com.pgoorts.tripplanner.files.FileNoteHelper
import com.pgoorts.tripplanner.pkpass.PkpassContent
import com.pgoorts.tripplanner.ui.components.ConfirmDeleteDialog
import com.pgoorts.tripplanner.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

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
    var pendingDeleteItemIndex by remember { mutableStateOf<Int?>(null) }

    // Raise screen brightness while a Pkpass is open (easier to scan), restore on leaving.
    DisposableEffect(uiState.note?.type) {
        val window = context.findActivity()?.window
        val isPkpass = uiState.note?.type == NoteType.PKPASS
        if (isPkpass && window != null) {
            window.attributes = window.attributes.apply { screenBrightness = 1f }
        }
        onDispose {
            if (isPkpass && window != null) {
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

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
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
                if (note.type != NoteType.PKPASS) {
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
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
                                                IconButton(onClick = { pendingDeleteItemIndex = index }) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Grey700, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    NoteType.WEB_URL, NoteType.GOOGLE_DOC -> {
                        LinkNoteContent(
                            note = note,
                            textContent = textContent,
                            onTextChange = { textContent = it; viewModel.updateTextContent(it) },
                            canEdit = canEdit
                        )
                    }

                    NoteType.GOOGLE_DRIVE -> {
                        // A picker-based Drive note (Block 6) stores a small JSON object instead
                        // of a plain URL string — try that shape first, falling back to today's
                        // plain-URL rendering for a pasted Drive share link (datastructure.txt §3).
                        val drivePicked = remember(note.content) {
                            try {
                                Json.decodeFromString<DrivePickerContent>(note.content)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (drivePicked != null) {
                            DrivePickerNoteContent(drivePicked)
                        } else {
                            LinkNoteContent(
                                note = note,
                                textContent = textContent,
                                onTextChange = { textContent = it; viewModel.updateTextContent(it) },
                                canEdit = canEdit
                            )
                        }
                    }

                    NoteType.PKPASS -> {
                        val parsedContent = remember(note.content) {
                            try {
                                Json.decodeFromString<PkpassContent>(note.content)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (parsedContent != null) {
                            PkpassCard(parsedContent)
                        } else {
                            Text("Unable to load this pass.", color = Grey500)
                        }
                    }

                    NoteType.FILE -> {
                        val fileContent = remember(note.content) { FileNoteHelper.parseContent(note.content) }
                        if (fileContent != null) {
                            FileNoteCard(noteId = note.id, fileContent = fileContent)
                        } else {
                            Text("Unable to load this file.", color = Grey500)
                        }
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

    pendingDeleteItemIndex?.let { index ->
        val itemText = uiState.checklistItems.getOrNull(index)?.text.orEmpty()
        ConfirmDeleteDialog(
            title = "Delete item?",
            message = "\"$itemText\" will be removed from this checklist.",
            onConfirm = { viewModel.removeChecklistItem(index); pendingDeleteItemIndex = null },
            onDismiss = { pendingDeleteItemIndex = null }
        )
    }
}

/** WEB_URL/GOOGLE_DOC's plain-URL rendering, also GOOGLE_DRIVE's fallback for a pasted share link. */
@Composable
private fun LinkNoteContent(
    note: NoteEntity,
    textContent: String,
    onTextChange: (String) -> Unit,
    canEdit: Boolean
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = textContent,
        onValueChange = onTextChange,
        readOnly = !canEdit,
        label = { Text("Link URL", color = Grey500) },
        singleLine = true,
        colors = tripTextFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )

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

/**
 * A Drive file picked via the system picker (Block 6) — launches its `content://` URI directly.
 * That grant only exists on the originating device/account, so a `SecurityException` (or any
 * other launch failure, e.g. a fresh install with no cached grant) falls back to an explanatory
 * card instead of crashing — matching designmockups/FileDriveNote.png.
 */
@Composable
private fun DrivePickerNoteContent(drive: DrivePickerContent) {
    val context = LocalContext.current
    var unavailable by remember(drive.uri) { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(20.dp), color = LodgingPurple.copy(alpha = 0.15f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Filled.Cloud, contentDescription = null, tint = LodgingPurple, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Google Drive", style = MaterialTheme.typography.labelMedium, color = LodgingPurple, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(drive.displayName, style = MaterialTheme.typography.titleLarge, color = White, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))

    if (!unavailable) {
        Card(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(drive.uri))
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    unavailable = true
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
                Icon(Icons.Filled.Cloud, contentDescription = null, tint = LodgingPurple, modifier = Modifier.size(24.dp))
                Text("Open", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open", tint = Teal300)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Grey700, RoundedCornerShape(14.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Grey500, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text("Not available on this device", style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "This file was picked directly from Drive on another device. Ask them to share a Drive link instead.",
                style = MaterialTheme.typography.bodySmall,
                color = Grey500,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** A generic `FILE` note (Block 7) — downloads the Storage object on demand, then opens it via `FileProvider`. */
@Composable
private fun FileNoteCard(noteId: String, fileContent: FileNoteContent) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Navy700),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Teal200.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = Teal200, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        fileContent.originalFileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(fileContent.mimeType, style = MaterialTheme.typography.bodySmall, color = Grey500)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    isDownloading = true
                    error = null
                    scope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) {
                                FileNoteHelper.ensureDownloaded(context, noteId, fileContent)
                            }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, fileContent.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            error = "Couldn't open this file"
                        } finally {
                            isDownloading = false
                        }
                    }
                },
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Navy950),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isDownloading) "Downloading..." else "Open", fontWeight = FontWeight.SemiBold)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = ErrorRed)
            }
        }
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
    NoteType.FILE         -> Pair(Teal200, Icons.Filled.InsertDriveFile)
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
private fun PkpassCard(content: PkpassContent) {
    val barcodeBitmap = remember(content.barcodeMessage, content.barcodeFormat) {
        renderPkpassBarcode(content.barcodeMessage, content.barcodeFormat)
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                content.organizationName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Grey300,
                letterSpacing = 1.sp
            )
            Text(
                content.description.ifBlank { content.passType },
                style = MaterialTheme.typography.titleLarge,
                color = White,
                fontWeight = FontWeight.Bold
            )

            if (content.fields.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                content.fields.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { field ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(field.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Grey300)
                                Text(field.value, style = MaterialTheme.typography.bodyMedium, color = White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            Divider(color = Grey700, modifier = Modifier.padding(vertical = 8.dp))

            Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    if (barcodeBitmap != null) {
                        Image(
                            bitmap = barcodeBitmap.asImageBitmap(),
                            contentDescription = "Pass barcode",
                            modifier = Modifier.size(180.dp)
                        )
                    } else {
                        Text("Unable to render barcode", color = Navy900)
                    }
                    if (content.serialNumber.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(content.serialNumber, style = MaterialTheme.typography.labelSmall, color = Grey700, letterSpacing = 2.sp)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Grey500, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Screen brightness increased for scanning", style = MaterialTheme.typography.labelSmall, color = Grey500)
    }
}

private fun renderPkpassBarcode(message: String, formatName: String): Bitmap? {
    if (message.isBlank()) return null
    val format = when (formatName) {
        "PDF417" -> BarcodeFormat.PDF_417
        "AZTEC" -> BarcodeFormat.AZTEC
        "CODE128" -> BarcodeFormat.CODE_128
        else -> BarcodeFormat.QR_CODE
    }
    return try {
        val matrix = MultiFormatWriter().encode(message, format, 480, 480)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
