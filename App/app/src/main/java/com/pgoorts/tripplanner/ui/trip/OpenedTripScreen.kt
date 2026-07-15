@file:OptIn(ExperimentalMaterial3Api::class)

package com.pgoorts.tripplanner.ui.trip

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pgoorts.tripplanner.data.local.entity.EventCategory
import com.pgoorts.tripplanner.data.local.entity.EventEntity
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.NoteType
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.local.entity.TripEntity
import com.pgoorts.tripplanner.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

private val TabLabels = listOf("Itinerary", "Notes", "Reminders")

@Composable
fun OpenedTripScreen(
    tripId: String,
    onBack: () -> Unit,
    onEventClick: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onReminderClick: (String) -> Unit,
    viewModel: OpenedTripViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    var showAddEventDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TripTopBar(trip = uiState.trip, onBack = onBack)
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> FloatingActionButton(
                    onClick = { showAddEventDialog = true },
                    containerColor = Teal300,
                    contentColor = Navy950,
                    shape = CircleShape,
                    modifier = Modifier.size(60.dp)
                ) { Icon(Icons.Filled.Add, contentDescription = "Add Event", modifier = Modifier.size(28.dp)) }

                1 -> FloatingActionButton(
                    onClick = { showAddNoteDialog = true },
                    containerColor = Teal300,
                    contentColor = Navy950,
                    shape = CircleShape,
                    modifier = Modifier.size(60.dp)
                ) { Icon(Icons.Filled.Add, contentDescription = "Add Note", modifier = Modifier.size(28.dp)) }

                2 -> FloatingActionButton(
                    onClick = { showAddReminderDialog = true },
                    containerColor = Teal300,
                    contentColor = Navy950,
                    shape = CircleShape,
                    modifier = Modifier.size(60.dp)
                ) { Icon(Icons.Filled.Add, contentDescription = "Add Reminder", modifier = Modifier.size(28.dp)) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TripTabRow(selectedTab = selectedTab, onTabSelected = { selectedTab = it })

            when (selectedTab) {
                0 -> ItineraryTab(
                    itineraryDays = uiState.itineraryDays,
                    onEventClick = onEventClick,
                    onDeleteEvent = { viewModel.deleteEvent(it) }
                )
                1 -> NotesTab(
                    notes = uiState.notes,
                    onNoteClick = onNoteClick,
                    onDeleteNote = { viewModel.deleteNote(it) }
                )
                2 -> RemindersTab(
                    reminders = uiState.reminders,
                    onReminderClick = onReminderClick,
                    onDeleteReminder = { viewModel.deleteReminder(it) }
                )
            }
        }
    }

    if (showAddEventDialog) {
        AddEventDialog(
            onDismiss = { showAddEventDialog = false },
            onConfirm = { title, category, startDate, endDate, startTime, endTime ->
                viewModel.createEvent(
                    title = title, category = category,
                    startDate = startDate, endDate = endDate,
                    startTime = startTime, endTime = endTime
                )
                showAddEventDialog = false
            }
        )
    }

    if (showAddNoteDialog) {
        AddNoteDialog(
            onDismiss = { showAddNoteDialog = false },
            onConfirm = { title, type ->
                viewModel.createNote(title = title, type = type)
                showAddNoteDialog = false
            }
        )
    }

    if (showAddReminderDialog) {
        AddReminderDialog(
            onDismiss = { showAddReminderDialog = false },
            onConfirm = { text, date, time ->
                viewModel.createReminder(text = text, date = date, time = time)
                showAddReminderDialog = false
            }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Top bar
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripTopBar(trip: TripEntity?, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(Navy800, Navy900)))
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Grey300)
                }
                Text(
                    text = trip?.destination ?: "Trip",
                    style = MaterialTheme.typography.titleLarge,
                    color = White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Share, contentDescription = "Export", tint = Grey500)
                }
            }

            if (trip != null) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, tint = Teal300, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatDateRange(trip.startDate, trip.endDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = Grey300
                    )
                    Spacer(Modifier.width(12.dp))
                    val duration = tripDurationDays(trip.startDate, trip.endDate)
                    if (duration > 0) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Teal300.copy(alpha = 0.15f)) {
                            Text(
                                text = "$duration days",
                                style = MaterialTheme.typography.labelSmall,
                                color = Teal200,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Tab row
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripTabRow(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Navy900)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabLabels.forEachIndexed { index, label ->
            val isSelected = selectedTab == index
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) Teal300 else Navy700,
                animationSpec = tween(200), label = "tab_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Navy950 else Grey300,
                animationSpec = tween(200), label = "tab_text"
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onTabSelected(index) },
                shape = RoundedCornerShape(20.dp),
                color = bgColor
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    maxLines = 1
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Itinerary tab
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ItineraryTab(
    itineraryDays: List<ItineraryDay>,
    onEventClick: (String) -> Unit,
    onDeleteEvent: (EventEntity) -> Unit
) {
    if (itineraryDays.isEmpty() || itineraryDays.all { it.events.isEmpty() }) {
        EmptyTabState(icon = Icons.Filled.CalendarMonth, message = "No events yet", hint = "Tap + to add your first itinerary event")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp, top = 4.dp)
    ) {
        itineraryDays.forEach { day ->
            item(key = "header_${day.date}") {
                DayHeader(date = day.date)
            }
            if (day.events.isEmpty()) {
                item(key = "empty_${day.date}") {
                    Text(
                        text = "No events",
                        style = MaterialTheme.typography.bodySmall,
                        color = Grey700,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                }
            } else {
                items(day.events, key = { "${day.date}_${it.event.id}" }) { itEvent ->
                    EventCard(
                        itineraryEvent = itEvent,
                        onClick = { onEventClick(itEvent.event.id) },
                        onDelete = { onDeleteEvent(itEvent.event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val today = LocalDate.now()
    val isToday = date.isEqual(today)
    val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = if (isToday) Teal300 else Grey700, shape = CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = date.format(fmt),
            style = MaterialTheme.typography.titleSmall,
            color = if (isToday) Teal300 else Grey300,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold
        )
        if (isToday) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = Teal300.copy(alpha = 0.15f)) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal300,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Divider(modifier = Modifier.width(80.dp), color = Grey700.copy(alpha = 0.4f))
    }
}

@Composable
private fun EventCard(
    itineraryEvent: ItineraryEvent,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val event = itineraryEvent.event
    val (categoryColor, categoryIcon) = categoryVisuals(event.category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(categoryColor.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, categoryColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = categoryIcon, contentDescription = null, tint = categoryColor, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.width(10.dp))

        Card(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Navy700),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        itineraryEvent.multiDayLabel?.let { label ->
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(20.dp), color = categoryColor.copy(alpha = 0.18f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = categoryColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    if (event.startTime != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = Grey500, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                            val timeText = if (event.endTime != null) "${event.startTime} → ${event.endTime}" else event.startTime
                            Text(text = timeText, style = MaterialTheme.typography.bodySmall, color = Grey500)
                        }
                    } else {
                        Text(text = "All day", style = MaterialTheme.typography.bodySmall, color = Grey500)
                    }

                    if (!event.location.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Grey500, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(text = event.location, style = MaterialTheme.typography.bodySmall, color = Grey500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = categoryColor.copy(alpha = 0.12f)) {
                        Text(
                            text = event.category.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete event", tint = Grey700, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Notes tab
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotesTab(
    notes: List<NoteEntity>,
    onNoteClick: (String) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit
) {
    if (notes.isEmpty()) {
        EmptyTabState(icon = Icons.Filled.Note, message = "No notes yet", hint = "Tap + to add a note for this trip")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(notes, key = { it.id }) { note ->
            NoteCard(note = note, onClick = { onNoteClick(note.id) }, onDelete = { onDeleteNote(note) })
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val (noteColor, noteIcon) = noteTypeVisuals(note.type)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Navy700),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(noteColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = noteIcon, contentDescription = null, tint = noteColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = note.type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = Grey500
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete note", tint = Grey700, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Reminders tab
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun RemindersTab(
    reminders: List<ReminderEntity>,
    onReminderClick: (String) -> Unit,
    onDeleteReminder: (ReminderEntity) -> Unit
) {
    if (reminders.isEmpty()) {
        EmptyTabState(icon = Icons.Filled.Notifications, message = "No reminders yet", hint = "Tap + to set a reminder for this trip")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(reminders, key = { it.id }) { reminder ->
            ReminderCard(reminder = reminder, onClick = { onReminderClick(reminder.id) }, onDelete = { onDeleteReminder(reminder) })
        }
    }
}

@Composable
private fun ReminderCard(reminder: ReminderEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val isPast = try { LocalDate.parse(reminder.date).isBefore(LocalDate.now()) } catch (e: Exception) { false }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Navy700),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isPast) Grey700.copy(alpha = 0.3f) else WarningAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = if (isPast) Grey500 else WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPast) Grey500 else White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isPast) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Grey500, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${formatDate(reminder.date)} at ${reminder.time}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Grey500
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete reminder", tint = Grey700, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Empty state
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyTabState(icon: ImageVector, message: String, hint: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Grey700, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.titleMedium, color = Grey300)
        Spacer(Modifier.height(6.dp))
        Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = Grey500)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Add Event Dialog
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, EventCategory, String, String, String?, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EventCategory.OTHER) }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = { Text("New Event", style = MaterialTheme.typography.titleLarge, color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TripTextField(value = title, onValueChange = { title = it; error = null }, label = "Title")

                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                    OutlinedTextField(
                        value = category.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category", color = Grey500) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        colors = tripTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.background(Navy700)
                    ) {
                        EventCategory.entries.forEach { cat ->
                            val (catColor, catIcon) = categoryVisuals(cat)
                            DropdownMenuItem(
                                text = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }, color = White) },
                                onClick = { category = cat; categoryExpanded = false },
                                leadingIcon = { Icon(catIcon, contentDescription = null, tint = catColor, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TripTextField(value = startDate, onValueChange = { startDate = it; error = null }, label = "Start Date", hint = "YYYY-MM-DD", modifier = Modifier.weight(1f))
                    TripTextField(value = endDate, onValueChange = { endDate = it; error = null }, label = "End Date", hint = "YYYY-MM-DD", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TripTextField(value = startTime, onValueChange = { startTime = it; error = null }, label = "Start Time", hint = "HH:MM (opt)", modifier = Modifier.weight(1f))
                    TripTextField(value = endTime, onValueChange = { endTime = it; error = null }, label = "End Time", hint = "HH:MM (opt)", modifier = Modifier.weight(1f))
                }

                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val err = validateEventInput(title, startDate, endDate, startTime, endTime)
                    if (err != null) { error = err; return@Button }
                    onConfirm(
                        title.trim(), category,
                        startDate.trim(), endDate.trim(),
                        startTime.trim().takeIf { it.isNotBlank() },
                        endTime.trim().takeIf { it.isNotBlank() }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Navy950)
            ) { Text("Add", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Grey300) } }
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Add Note Dialog
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddNoteDialog(onDismiss: () -> Unit, onConfirm: (String, NoteType) -> Unit) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(NoteType.TEXT_BLOCK) }
    var typeExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = { Text("New Note", style = MaterialTheme.typography.titleLarge, color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TripTextField(value = title, onValueChange = { title = it; error = null }, label = "Title")

                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type", color = Grey500) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        colors = tripTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                        modifier = Modifier.background(Navy700)
                    ) {
                        NoteType.entries.forEach { nt ->
                            val (ntColor, ntIcon) = noteTypeVisuals(nt)
                            DropdownMenuItem(
                                text = { Text(nt.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, color = White) },
                                onClick = { type = nt; typeExpanded = false },
                                leadingIcon = { Icon(ntIcon, contentDescription = null, tint = ntColor, modifier = Modifier.size(18.dp)) }
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
                    onConfirm(title.trim(), type)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Navy950)
            ) { Text("Add", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Grey300) } }
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Add Reminder Dialog
// ──────────────────────────────────────────────────────────────────────────────

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
                TripTextField(value = text, onValueChange = { text = it; error = null }, label = "Reminder text")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TripTextField(value = date, onValueChange = { date = it; error = null }, label = "Date", hint = "YYYY-MM-DD", modifier = Modifier.weight(1f))
                    TripTextField(value = time, onValueChange = { time = it; error = null }, label = "Time", hint = "HH:MM", modifier = Modifier.weight(1f))
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

// ──────────────────────────────────────────────────────────────────────────────
// Shared helpers & utility composables
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String = "",
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Grey500) },
        placeholder = if (hint.isNotBlank()) ({ Text(hint, color = Grey700) }) else null,
        singleLine = true,
        colors = tripTextFieldColors(),
        modifier = modifier
    )
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
}

private fun formatDateRange(startDate: String, endDate: String): String = try {
    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    "${LocalDate.parse(startDate).format(fmt)} – ${LocalDate.parse(endDate).format(fmt)}"
} catch (e: Exception) { "$startDate – $endDate" }

private fun formatDate(date: String): String = try {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
} catch (e: Exception) { date }

private fun tripDurationDays(startDate: String, endDate: String): Int = try {
    (LocalDate.parse(startDate).until(LocalDate.parse(endDate), ChronoUnit.DAYS) + 1).toInt()
} catch (e: Exception) { 0 }

private fun validateEventInput(title: String, startDate: String, endDate: String, startTime: String, endTime: String): String? {
    if (title.isBlank()) return "Please enter a title"
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val start = try { LocalDate.parse(startDate.trim(), fmt) } catch (e: DateTimeParseException) { return "Start date must be YYYY-MM-DD" }
    val end   = try { LocalDate.parse(endDate.trim(),   fmt) } catch (e: DateTimeParseException) { return "End date must be YYYY-MM-DD" }
    if (end.isBefore(start)) return "End date must be after start date"
    if (startTime.isNotBlank() && !startTime.trim().matches(Regex("\\d{2}:\\d{2}"))) return "Start time must be HH:MM"
    if (endTime.isNotBlank()   && !endTime.trim().matches(Regex("\\d{2}:\\d{2}")))   return "End time must be HH:MM"
    return null
}

private fun validateReminderInput(text: String, date: String, time: String): String? {
    if (text.isBlank()) return "Please enter reminder text"
    try { LocalDate.parse(date.trim(), DateTimeFormatter.ISO_LOCAL_DATE) } catch (e: Exception) { return "Date must be YYYY-MM-DD" }
    if (!time.trim().matches(Regex("\\d{2}:\\d{2}"))) return "Time must be HH:MM"
    return null
}
