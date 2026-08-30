@file:OptIn(ExperimentalMaterial3Api::class)

package com.pgoorts.tripplanner.ui.event

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pgoorts.tripplanner.data.local.entity.EventCategory
import com.pgoorts.tripplanner.data.local.entity.EventEntity
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.NoteType
import com.pgoorts.tripplanner.data.local.entity.classifyNoteUrl
import com.pgoorts.tripplanner.pkpass.PkpassContent
import com.pgoorts.tripplanner.pkpass.PkpassParser
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.local.entity.TripRole
import com.pgoorts.tripplanner.ui.components.ConfirmDeleteDialog
import com.pgoorts.tripplanner.ui.components.DatePickerField
import com.pgoorts.tripplanner.ui.components.TimePickerField
import com.pgoorts.tripplanner.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun OpenedEventScreen(
    eventId: String,
    onBack: () -> Unit,
    onNoteClick: (String) -> Unit,
    onReminderClick: (String) -> Unit,
    viewModel: OpenedEventViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val canEdit = uiState.currentUserRole != TripRole.VIEWER

    // Form states
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EventCategory.OTHER) }
    var location by remember { mutableStateOf("") }
    var timezone by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var flightNumber by remember { mutableStateOf("") }
    var departureAirportCode by remember { mutableStateOf("") }
    var arrivalAirportCode by remember { mutableStateOf("") }
    var bookingNumber by remember { mutableStateOf("") }

    // Dialog states
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var pendingDeleteNote by remember { mutableStateOf<NoteEntity?>(null) }
    var pendingDeleteReminder by remember { mutableStateOf<ReminderEntity?>(null) }

    var isDropdownExpanded by remember { mutableStateOf(false) }
    var isTimezoneExpanded by remember { mutableStateOf(false) }
    var timezoneSearchQuery by remember { mutableStateOf("") }

    val commonTimezones = remember {
        ZoneId.getAvailableZoneIds().sorted()
    }
    val filteredTimezones = remember(timezoneSearchQuery) {
        if (timezoneSearchQuery.isBlank()) {
            commonTimezones.take(20)
        } else {
            commonTimezones.filter { it.contains(timezoneSearchQuery, ignoreCase = true) }.take(20)
        }
    }

    // Initialize form states when event is loaded
    LaunchedEffect(uiState.event) {
        uiState.event?.let { event ->
            title = event.title
            category = event.category
            location = event.location ?: ""
            timezone = event.startTimezone
            startDate = event.startDate
            endDate = event.endDate
            startTime = event.startTime ?: ""
            endTime = event.endTime ?: ""
            description = event.description ?: ""
            flightNumber = event.flightNumber ?: ""
            departureAirportCode = event.departureAirportCode ?: ""
            arrivalAirportCode = event.arrivalAirportCode ?: ""
            bookingNumber = event.bookingNumber ?: ""
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Edit Event", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (canEdit) {
                        IconButton(onClick = {
                            val error = validateEventForm(title, startDate, endDate, startTime, endTime)
                            if (error != null) {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.updateEvent(
                                    title = title.trim(),
                                    category = category,
                                    location = location.trim().takeIf { it.isNotBlank() },
                                    timezone = timezone.trim(),
                                    startDate = startDate.trim(),
                                    startTime = startTime.trim().takeIf { it.isNotBlank() },
                                    endDate = endDate.trim(),
                                    endTime = endTime.trim().takeIf { it.isNotBlank() },
                                    description = description.trim().takeIf { it.isNotBlank() },
                                    flightNumber = flightNumber.trim().takeIf { it.isNotBlank() },
                                    departureAirportCode = departureAirportCode.trim().takeIf { it.isNotBlank() },
                                    arrivalAirportCode = arrivalAirportCode.trim().takeIf { it.isNotBlank() },
                                    bookingNumber = bookingNumber.trim().takeIf { it.isNotBlank() }
                                )
                                Toast.makeText(context, "Saved changes", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save", tint = Teal300)
                        }
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
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal300)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Form Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Title
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title", color = Grey500) },
                            singleLine = true,
                            enabled = canEdit,
                            colors = tripTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Category Dropdown
                        ExposedDropdownMenuBox(
                            expanded = isDropdownExpanded && canEdit,
                            onExpandedChange = { if (canEdit) isDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = category.name.lowercase().replaceFirstChar { it.uppercase() },
                                onValueChange = {},
                                readOnly = true,
                                enabled = canEdit,
                                label = { Text("Category", color = Grey500) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded && canEdit) },
                                colors = tripTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false },
                                modifier = Modifier.background(Navy700)
                            ) {
                                EventCategory.entries.forEach { cat ->
                                    val (catColor, catIcon) = categoryVisuals(cat)
                                    DropdownMenuItem(
                                        text = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }, color = White) },
                                        onClick = {
                                            category = cat
                                            isDropdownExpanded = false
                                        },
                                        leadingIcon = {
                                            Icon(catIcon, contentDescription = null, tint = catColor, modifier = Modifier.size(18.dp))
                                        }
                                    )
                                }
                            }
                        }

                        // Location with Map Quick Link
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                label = { Text("Location", color = Grey500) },
                                singleLine = true,
                                enabled = canEdit,
                                colors = tripTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (location.isNotBlank()) {
                                        try {
                                            val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(location)}")
                                            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                            context.startActivity(mapIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open map", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Enter a location first", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = location.isNotBlank(),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (location.isNotBlank()) Teal300.copy(alpha = 0.15f) else Navy700)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Map,
                                    contentDescription = "Open Map",
                                    tint = if (location.isNotBlank()) Teal300 else Grey500
                                )
                            }
                        }

                        // Timezone Picker
                        ExposedDropdownMenuBox(
                            expanded = isTimezoneExpanded && canEdit,
                            onExpandedChange = { if (canEdit) isTimezoneExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = timezone,
                                onValueChange = {
                                    timezone = it
                                    timezoneSearchQuery = it
                                },
                                enabled = canEdit,
                                label = { Text("Timezone", color = Grey500) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTimezoneExpanded && canEdit) },
                                colors = tripTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = isTimezoneExpanded,
                                onDismissRequest = { isTimezoneExpanded = false },
                                modifier = Modifier.background(Navy700)
                            ) {
                                // Search bar inside menu to filter
                                OutlinedTextField(
                                    value = timezoneSearchQuery,
                                    onValueChange = { timezoneSearchQuery = it },
                                    placeholder = { Text("Search timezone...", color = Grey500) },
                                    singleLine = true,
                                    colors = tripTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )
                                filteredTimezones.forEach { zone ->
                                    DropdownMenuItem(
                                        text = { Text(zone, color = White) },
                                        onClick = {
                                            timezone = zone
                                            isTimezoneExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Dates
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DatePickerField(
                                label = "Start Date",
                                value = startDate,
                                onValueChange = { startDate = it },
                                enabled = canEdit,
                                colors = tripTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            DatePickerField(
                                label = "End Date",
                                value = endDate,
                                onValueChange = { endDate = it },
                                enabled = canEdit,
                                colors = tripTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Times
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TimePickerField(
                                label = "Start Time",
                                value = startTime,
                                onValueChange = { startTime = it },
                                enabled = canEdit,
                                allowClear = true,
                                colors = tripTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            TimePickerField(
                                label = "End Time",
                                value = endTime,
                                onValueChange = { endTime = it },
                                enabled = canEdit,
                                allowClear = true,
                                colors = tripTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Description
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description", color = Grey500) },
                            colors = tripTextFieldColors(),
                            enabled = canEdit,
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (category == EventCategory.FLIGHT) {
                            OutlinedTextField(
                                value = flightNumber,
                                onValueChange = { flightNumber = it },
                                label = { Text("Flight number", color = Grey500) },
                                singleLine = true,
                                enabled = canEdit,
                                colors = tripTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = departureAirportCode,
                                    onValueChange = { departureAirportCode = it },
                                    label = { Text("Departure airport", color = Grey500) },
                                    singleLine = true,
                                    enabled = canEdit,
                                    colors = tripTextFieldColors(),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = arrivalAirportCode,
                                    onValueChange = { arrivalAirportCode = it },
                                    label = { Text("Arrival airport", color = Grey500) },
                                    singleLine = true,
                                    enabled = canEdit,
                                    colors = tripTextFieldColors(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else if (category == EventCategory.LODGING) {
                            OutlinedTextField(
                                value = bookingNumber,
                                onValueChange = { bookingNumber = it },
                                label = { Text("Booking number", color = Grey500) },
                                singleLine = true,
                                enabled = canEdit,
                                colors = tripTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Section Headers: Notes
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.titleMedium,
                            color = White,
                            fontWeight = FontWeight.Bold
                        )
                        if (canEdit) {
                            TextButton(onClick = { showAddNoteDialog = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Teal300, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Note", color = Teal300, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (uiState.notes.isEmpty()) {
                    item {
                        Text("No notes associated with this event.", style = MaterialTheme.typography.bodySmall, color = Grey500)
                    }
                } else {
                    items(uiState.notes, key = { it.id }) { note ->
                        NoteRowItem(note = note, onClick = { onNoteClick(note.id) }, onDelete = { pendingDeleteNote = note }, canEdit = canEdit)
                    }
                }

                // Section Headers: Reminders
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reminders",
                            style = MaterialTheme.typography.titleMedium,
                            color = White,
                            fontWeight = FontWeight.Bold
                        )
                        if (canEdit) {
                            TextButton(onClick = { showAddReminderDialog = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Teal300, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Reminder", color = Teal300, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (uiState.reminders.isEmpty()) {
                    item {
                        Text("No reminders scheduled for this event.", style = MaterialTheme.typography.bodySmall, color = Grey500)
                    }
                } else {
                    items(uiState.reminders, key = { it.id }) { reminder ->
                        ReminderRowItem(
                            reminder = reminder,
                            onClick = { onReminderClick(reminder.id) },
                            onDelete = { pendingDeleteReminder = reminder },
                            canEdit = canEdit
                        )
                    }
                }
            }
        }
    }

    if (showAddNoteDialog) {
        AddNoteDialog(
            tripId = uiState.event?.tripId.orEmpty(),
            onDismiss = { showAddNoteDialog = false },
            onConfirm = { title, type, content, localAttachmentPath, id ->
                viewModel.addNote(title, type, content, localAttachmentPath, id)
                showAddNoteDialog = false
            }
        )
    }

    if (showAddReminderDialog) {
        AddReminderDialog(
            onDismiss = { showAddReminderDialog = false },
            onConfirm = { text, date, time ->
                viewModel.addReminder(text, date, time)
                showAddReminderDialog = false
            }
        )
    }

    pendingDeleteNote?.let { note ->
        ConfirmDeleteDialog(
            title = "Delete note?",
            message = "\"${note.title}\" will be permanently removed from this trip. This can't be undone.",
            onConfirm = { viewModel.deleteNote(note); pendingDeleteNote = null },
            onDismiss = { pendingDeleteNote = null }
        )
    }

    pendingDeleteReminder?.let { reminder ->
        ConfirmDeleteDialog(
            title = "Delete reminder?",
            message = "\"${reminder.text}\" will be permanently removed from this trip. This can't be undone.",
            onConfirm = { viewModel.deleteReminder(reminder); pendingDeleteReminder = null },
            onDismiss = { pendingDeleteReminder = null }
        )
    }
}

@Composable
private fun NoteRowItem(note: NoteEntity, onClick: () -> Unit, onDelete: () -> Unit, canEdit: Boolean = true) {
    val (noteColor, noteIcon) = noteTypeVisuals(note.type)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Navy700),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(noteColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(noteIcon, contentDescription = null, tint = noteColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(note.title, style = MaterialTheme.typography.bodyMedium, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    note.type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = Grey500
                )
            }
            if (canEdit) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Grey700, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ReminderRowItem(reminder: ReminderEntity, onClick: () -> Unit, onDelete: () -> Unit, canEdit: Boolean = true) {
    val isPast = try { LocalDate.parse(reminder.date).isBefore(LocalDate.now()) } catch (e: Exception) { false }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Navy700),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPast) Grey700.copy(alpha = 0.3f) else WarningAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = if (isPast) Grey500 else WarningAmber,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPast) Grey500 else White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isPast) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    "${reminder.date} at ${reminder.time}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Grey500
                )
            }
            if (canEdit) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Grey700, modifier = Modifier.size(18.dp))
                }
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

private fun categoryVisuals(category: EventCategory): Pair<Color, ImageVector> = when (category) {
    EventCategory.FLIGHT     -> Pair(FlightBlue, Icons.Filled.Flight)
    EventCategory.LODGING    -> Pair(LodgingPurple, Icons.Filled.Hotel)
    EventCategory.RESTAURANT -> Pair(RestaurantOrange, Icons.Filled.Restaurant)
    EventCategory.TRANSIT    -> Pair(TransitGrey, Icons.Filled.DirectionsBus)
    EventCategory.ACTIVITY   -> Pair(ActivityTeal, Icons.Filled.DirectionsWalk)
    EventCategory.ATTRACTION -> Pair(AttractionYellow, Icons.Filled.PhotoCamera)
    EventCategory.OTHER      -> Pair(OtherGrey, Icons.Filled.Category)
}

private fun noteTypeVisuals(type: NoteType): Pair<Color, ImageVector> = when (type) {
    NoteType.TEXT_BLOCK   -> Pair(Grey300, Icons.Filled.Article)
    NoteType.CHECKLIST    -> Pair(Teal300, Icons.Filled.Checklist)
    NoteType.WEB_URL      -> Pair(FlightBlue, Icons.Filled.Link)
    NoteType.GOOGLE_DOC   -> Pair(ActivityTeal, Icons.Filled.Description)
    NoteType.GOOGLE_DRIVE -> Pair(LodgingPurple, Icons.Filled.Cloud)
    NoteType.PKPASS       -> Pair(Teal300, Icons.Filled.CreditCard)
    NoteType.FILE         -> Pair(Teal200, Icons.Filled.InsertDriveFile)
}

private fun validateEventForm(
    title: String,
    startDate: String,
    endDate: String,
    startTime: String,
    endTime: String
): String? {
    if (title.isBlank()) return "Title cannot be blank"
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val start = try { LocalDate.parse(startDate.trim(), fmt) } catch (e: DateTimeParseException) { return "Start date must be YYYY-MM-DD" }
    val end = try { LocalDate.parse(endDate.trim(), fmt) } catch (e: DateTimeParseException) { return "End date must be YYYY-MM-DD" }
    if (end.isBefore(start)) return "End date must be after start date"
    if (startTime.isNotBlank() && !startTime.trim().matches(Regex("\\d{2}:\\d{2}"))) return "Start time must be HH:MM"
    if (endTime.isNotBlank() && !endTime.trim().matches(Regex("\\d{2}:\\d{2}"))) return "End time must be HH:MM"
    return null
}

private enum class NoteKind(val label: String) {
    TEXT_BLOCK("Text Block"),
    CHECKLIST("Checklist"),
    LINK("Link (paste a URL)"),
    PASS("Pass (.pkpass)")
}

// Dialog components locally simplified or imported
@Composable
private fun AddNoteDialog(
    tripId: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, type: NoteType, content: String, localAttachmentPath: String?, id: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noteId = remember { java.util.UUID.randomUUID().toString() }

    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(NoteKind.TEXT_BLOCK) }
    var kindExpanded by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    var pkpassContent by remember { mutableStateOf<PkpassContent?>(null) }
    var pkpassLocalPath by remember { mutableStateOf<String?>(null) }
    var pkpassFileName by remember { mutableStateOf<String?>(null) }
    var isParsingPkpass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickPkpassLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isParsingPkpass = true
        error = null
        scope.launch {
            val originalFileName = PkpassParser.queryDisplayName(context, uri)
            val storagePath = PkpassParser.buildStoragePath(tripId, noteId)
            val parsed = withContext(Dispatchers.IO) {
                try {
                    PkpassParser.parse(context, uri, storagePath, originalFileName)
                } catch (e: Exception) {
                    null
                }
            }
            if (parsed == null) {
                isParsingPkpass = false
                error = "Couldn't read this .pkpass file"
                return@launch
            }
            val localPath = withContext(Dispatchers.IO) {
                PkpassParser.copyToLocalStorage(context, uri, noteId)
            }
            pkpassContent = parsed
            pkpassLocalPath = localPath
            pkpassFileName = originalFileName
            if (title.isBlank()) title = parsed.description.ifBlank { originalFileName }
            isParsingPkpass = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = { Text("New Note", style = MaterialTheme.typography.titleLarge, color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("Title", color = Grey500) },
                    colors = tripTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(expanded = kindExpanded, onExpandedChange = { kindExpanded = it }) {
                    OutlinedTextField(
                        value = kind.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type", color = Grey500) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindExpanded) },
                        colors = tripTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = kindExpanded,
                        onDismissRequest = { kindExpanded = false },
                        modifier = Modifier.background(Navy700)
                    ) {
                        NoteKind.entries.forEach { k ->
                            DropdownMenuItem(
                                text = { Text(k.label, color = White) },
                                onClick = { kind = k; kindExpanded = false; error = null },
                                leadingIcon = { Icon(noteKindIcon(k), contentDescription = null, tint = Teal300, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }

                when (kind) {
                    NoteKind.TEXT_BLOCK, NoteKind.CHECKLIST -> {}

                    NoteKind.LINK -> {
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it; error = null },
                            label = { Text("URL", color = Grey500) },
                            singleLine = true,
                            colors = tripTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (urlText.isNotBlank()) {
                            Text(
                                "Detected automatically as: ${classifyNoteUrl(urlText).name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Grey500
                            )
                        }
                    }

                    NoteKind.PASS -> {
                        OutlinedButton(
                            onClick = { pickPkpassLauncher.launch("*/*") },
                            enabled = !isParsingPkpass,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal300),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    isParsingPkpass -> "Reading pass..."
                                    pkpassFileName != null -> pkpassFileName!!
                                    else -> "Choose .pkpass file"
                                }
                            )
                        }
                    }
                }

                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) { error = "Please enter a title"; return@Button }
                    when (kind) {
                        NoteKind.TEXT_BLOCK -> onConfirm(title.trim(), NoteType.TEXT_BLOCK, "", null, noteId)
                        NoteKind.CHECKLIST -> onConfirm(title.trim(), NoteType.CHECKLIST, "", null, noteId)
                        NoteKind.LINK -> {
                            if (urlText.isBlank()) { error = "Please enter a URL"; return@Button }
                            onConfirm(title.trim(), classifyNoteUrl(urlText), urlText.trim(), null, noteId)
                        }
                        NoteKind.PASS -> {
                            val content = pkpassContent
                            val localPath = pkpassLocalPath
                            if (content == null || localPath == null) { error = "Please choose a .pkpass file"; return@Button }
                            onConfirm(title.trim(), NoteType.PKPASS, Json.encodeToString(content), localPath, noteId)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Navy950)
            ) { Text("Add", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Grey300) } }
    )
}

private fun noteKindIcon(kind: NoteKind): ImageVector = when (kind) {
    NoteKind.TEXT_BLOCK -> Icons.Filled.Article
    NoteKind.CHECKLIST -> Icons.Filled.Checklist
    NoteKind.LINK -> Icons.Filled.Link
    NoteKind.PASS -> Icons.Filled.CreditCard
}

@Composable
private fun AddReminderDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = { Text("New Reminder", style = MaterialTheme.typography.titleLarge, color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("Reminder text", color = Grey500) },
                    colors = tripTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(
                        label = "Date",
                        value = date,
                        onValueChange = { date = it; error = null },
                        colors = tripTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    TimePickerField(
                        label = "Time",
                        value = time,
                        onValueChange = { time = it; error = null },
                        allowClear = false,
                        colors = tripTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val err = validateReminderInput(text, date, time)
                    if (err != null) { error = err; return@Button }
                    onConfirm(text.trim(), date.trim(), time.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Navy950)
            ) { Text("Add", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Grey300) } }
    )
}

private fun validateReminderInput(text: String, date: String, time: String): String? {
    if (text.isBlank()) return "Please enter reminder text"
    try { LocalDate.parse(date.trim(), DateTimeFormatter.ISO_LOCAL_DATE) } catch (e: Exception) { return "Date must be YYYY-MM-DD" }
    if (!time.trim().matches(Regex("\\d{2}:\\d{2}"))) return "Time must be HH:MM"
    return null
}
