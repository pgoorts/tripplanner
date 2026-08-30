@file:OptIn(ExperimentalMaterial3Api::class)

package com.pgoorts.tripplanner.ui.trip

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pgoorts.tripplanner.data.local.entity.ChecklistItem
import com.pgoorts.tripplanner.data.local.entity.EventCategory
import com.pgoorts.tripplanner.data.local.entity.EventEntity
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.NoteType
import com.pgoorts.tripplanner.data.local.entity.classifyNoteUrl
import com.pgoorts.tripplanner.pkpass.PkpassContent
import com.pgoorts.tripplanner.pkpass.PkpassParser
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.local.entity.TripEntity
import com.pgoorts.tripplanner.data.local.entity.TripRole
import com.pgoorts.tripplanner.ui.components.ConfirmDeleteDialog
import com.pgoorts.tripplanner.ui.components.DatePickerField
import com.pgoorts.tripplanner.ui.components.TimePickerField
import com.pgoorts.tripplanner.ui.components.TimezonePickerDialog
import com.pgoorts.tripplanner.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    val innerCircle by viewModel.innerCircle.collectAsState()
    val globalDefaultTimezone by viewModel.globalDefaultTimezone.collectAsState()
    val lastSuccessfulSyncAt by viewModel.lastSuccessfulSyncAt.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var showAddEventDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showTripSettingsDialog by remember { mutableStateOf(false) }

    val isViewer = uiState.currentUserRole == TripRole.VIEWER
    val canEdit = !isViewer

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TripTopBar(
                trip = uiState.trip,
                onBack = onBack,
                canShare = canEdit,
                onShareClick = { showShareDialog = true },
                onExportClick = {
                    val exportText = buildTripExportText(uiState)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, exportText)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Trip"))
                },
                onTripSettingsClick = { showTripSettingsDialog = true }
            )
        },
        floatingActionButton = {
            if (canEdit) {
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SyncStatusBar(
                lastSuccessfulSyncAt = lastSuccessfulSyncAt,
                status = syncStatus,
                onSyncClick = { viewModel.triggerManualSync() }
            )
            TripTabRow(selectedTab = selectedTab, onTabSelected = { selectedTab = it })

            when (selectedTab) {
                0 -> ItineraryTab(
                    itineraryDays = uiState.itineraryDays,
                    onEventClick = onEventClick,
                    onDeleteEvent = { viewModel.deleteEvent(it) },
                    canEdit = canEdit
                )
                1 -> NotesTab(
                    notes = uiState.notes,
                    onNoteClick = onNoteClick,
                    onDeleteNote = { viewModel.deleteNote(it) },
                    canEdit = canEdit
                )
                2 -> RemindersTab(
                    reminders = uiState.reminders,
                    onReminderClick = onReminderClick,
                    onDeleteReminder = { viewModel.deleteReminder(it) },
                    canEdit = canEdit
                )
            }
        }
    }

    if (showAddEventDialog) {
        AddEventDialog(
            initialTimezone = uiState.trip?.defaultTimezone ?: globalDefaultTimezone ?: "UTC",
            onDismiss = { showAddEventDialog = false },
            onConfirm = { title, category, startDate, endDate, startTime, endTime, timezone,
                          flightNumber, departureAirportCode, arrivalAirportCode, bookingNumber ->
                viewModel.createEvent(
                    title = title, category = category,
                    startDate = startDate, endDate = endDate,
                    startTime = startTime, endTime = endTime,
                    timezone = timezone,
                    flightNumber = flightNumber,
                    departureAirportCode = departureAirportCode,
                    arrivalAirportCode = arrivalAirportCode,
                    bookingNumber = bookingNumber
                )
                showAddEventDialog = false
            }
        )
    }

    if (showTripSettingsDialog) {
        TripSettingsDialog(
            trip = uiState.trip,
            globalDefaultTimezone = globalDefaultTimezone,
            onDismiss = { showTripSettingsDialog = false },
            onSetTimezone = { viewModel.setTripDefaultTimezone(it) },
            onClearOverride = { viewModel.setTripDefaultTimezone(null) }
        )
    }

    if (showAddNoteDialog) {
        AddNoteDialog(
            tripId = tripId,
            onDismiss = { showAddNoteDialog = false },
            onConfirm = { title, type, content, localAttachmentPath, id ->
                viewModel.createNote(title = title, type = type, content = content, localAttachmentPath = localAttachmentPath, id = id)
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

    if (showShareDialog) {
        ShareTripDialog(
            innerCircle = innerCircle,
            onDismiss = { showShareDialog = false },
            onConfirm = { email, role ->
                viewModel.shareTrip(email, role)
                showShareDialog = false
                Toast.makeText(context, "Shared with $email as ${role.name.lowercase().replace('_', ' ')}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Top bar
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripTopBar(
    trip: TripEntity?,
    onBack: () -> Unit,
    canShare: Boolean,
    onShareClick: () -> Unit,
    onExportClick: () -> Unit,
    onTripSettingsClick: () -> Unit
) {
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
                if (canShare) {
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Share Trip", tint = Grey300)
                    }
                }
                IconButton(onClick = onExportClick) {
                    Icon(Icons.Filled.IosShare, contentDescription = "Export", tint = Grey300)
                }
                IconButton(onClick = onTripSettingsClick) {
                    Icon(Icons.Filled.Settings, contentDescription = "Trip Settings", tint = Grey300)
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
// Sync status bar
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SyncStatusBar(
    lastSuccessfulSyncAt: Long?,
    status: SyncBarStatus,
    onSyncClick: () -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    val isFailed = status == SyncBarStatus.FAILED
    val statusText = when (status) {
        SyncBarStatus.IN_PROGRESS -> "Syncing…"
        SyncBarStatus.FAILED -> "Sync failed — check your connection"
        SyncBarStatus.IDLE -> formatLastSyncText(lastSuccessfulSyncAt, now)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Navy800)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.Sync,
            contentDescription = null,
            tint = if (isFailed) ErrorRed else Teal300,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFailed) ErrorRed else Grey300,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onSyncClick,
            enabled = status != SyncBarStatus.IN_PROGRESS,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                Icons.Filled.Sync,
                contentDescription = "Sync now",
                tint = Teal300,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private fun formatLastSyncText(epochMillis: Long?, now: Long): String {
    if (epochMillis == null) return "Not synced yet"
    val delta = now - epochMillis
    if (delta < DateUtils.MINUTE_IN_MILLIS) return "Synced just now"
    val relative = DateUtils.getRelativeTimeSpanString(epochMillis, now, DateUtils.MINUTE_IN_MILLIS)
    return "Synced $relative"
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
    onDeleteEvent: (EventEntity) -> Unit,
    canEdit: Boolean
) {
    var pendingDelete by remember { mutableStateOf<EventEntity?>(null) }

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
                        onDelete = { pendingDelete = itEvent.event },
                        canEdit = canEdit
                    )
                }
            }
        }
    }

    pendingDelete?.let { event ->
        ConfirmDeleteDialog(
            title = "Delete event?",
            message = "\"${event.title}\" will be permanently removed from this trip. This can't be undone.",
            onConfirm = { onDeleteEvent(event); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
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
    onDelete: () -> Unit,
    canEdit: Boolean = true
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
                val isFlight = event.category == EventCategory.FLIGHT
                val flightBadge = if (isFlight) {
                    if (itineraryEvent.isFirstDay && !itineraryEvent.isLastDay) "Departing"
                    else if (itineraryEvent.isLastDay && !itineraryEvent.isFirstDay) "Arriving"
                    else null
                } else null

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
                        (flightBadge ?: itineraryEvent.multiDayLabel)?.let { label ->
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

                    val flightInfo = if (isFlight) {
                        val number = event.flightNumber
                        when {
                            itineraryEvent.isFirstDay && !itineraryEvent.isLastDay ->
                                listOfNotNull(number, event.departureAirportCode).takeIf { it.isNotEmpty() }
                                    ?.joinToString(" · ")
                            itineraryEvent.isLastDay && !itineraryEvent.isFirstDay ->
                                listOfNotNull(number, event.arrivalAirportCode).takeIf { it.isNotEmpty() }
                                    ?.joinToString(" · ")
                            else -> {
                                val codes = listOfNotNull(event.departureAirportCode, event.arrivalAirportCode)
                                    .takeIf { it.isNotEmpty() }?.joinToString(" → ")
                                listOfNotNull(number, codes).takeIf { it.isNotEmpty() }?.joinToString(" · ")
                            }
                        }
                    } else null

                    if (!flightInfo.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Flight, contentDescription = null, tint = Grey500, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(text = flightInfo, style = MaterialTheme.typography.bodySmall, color = Grey500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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

                if (canEdit) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete event", tint = Grey700, modifier = Modifier.size(16.dp))
                    }
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
    onDeleteNote: (NoteEntity) -> Unit,
    canEdit: Boolean
) {
    var pendingDelete by remember { mutableStateOf<NoteEntity?>(null) }

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
            NoteCard(note = note, onClick = { onNoteClick(note.id) }, onDelete = { pendingDelete = note }, canEdit = canEdit)
        }
    }

    pendingDelete?.let { note ->
        ConfirmDeleteDialog(
            title = "Delete note?",
            message = "\"${note.title}\" will be permanently removed from this trip. This can't be undone.",
            onConfirm = { onDeleteNote(note); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun NoteCard(note: NoteEntity, onClick: () -> Unit, onDelete: () -> Unit, canEdit: Boolean = true) {
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
            if (canEdit) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete note", tint = Grey700, modifier = Modifier.size(16.dp))
                }
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
    onDeleteReminder: (ReminderEntity) -> Unit,
    canEdit: Boolean
) {
    var pendingDelete by remember { mutableStateOf<ReminderEntity?>(null) }

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
            ReminderCard(reminder = reminder, onClick = { onReminderClick(reminder.id) }, onDelete = { pendingDelete = reminder }, canEdit = canEdit)
        }
    }

    pendingDelete?.let { reminder ->
        ConfirmDeleteDialog(
            title = "Delete reminder?",
            message = "\"${reminder.text}\" will be permanently removed from this trip. This can't be undone.",
            onConfirm = { onDeleteReminder(reminder); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun ReminderCard(reminder: ReminderEntity, onClick: () -> Unit, onDelete: () -> Unit, canEdit: Boolean = true) {
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
            if (canEdit) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete reminder", tint = Grey700, modifier = Modifier.size(16.dp))
                }
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
    initialTimezone: String,
    onDismiss: () -> Unit,
    onConfirm: (String, EventCategory, String, String, String?, String?, String, String?, String?, String?, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EventCategory.OTHER) }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var timezone by remember { mutableStateOf(initialTimezone) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showTimezonePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var flightNumber by remember { mutableStateOf("") }
    var departureAirportCode by remember { mutableStateOf("") }
    var arrivalAirportCode by remember { mutableStateOf("") }
    var bookingNumber by remember { mutableStateOf("") }

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

                val dateLabels = when (category) {
                    EventCategory.FLIGHT -> "Departure Date" to "Arrival Date"
                    EventCategory.LODGING -> "Check-in Date" to "Check-out Date"
                    else -> "Start Date" to "End Date"
                }
                val timeLabels = when (category) {
                    EventCategory.FLIGHT -> "Departure Time" to "Arrival Time"
                    else -> "Start Time" to "End Time"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(label = dateLabels.first, value = startDate, onValueChange = { startDate = it; error = null }, colors = tripTextFieldColors(), modifier = Modifier.weight(1f))
                    DatePickerField(label = dateLabels.second, value = endDate, onValueChange = { endDate = it; error = null }, colors = tripTextFieldColors(), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePickerField(label = timeLabels.first, value = startTime, onValueChange = { startTime = it; error = null }, allowClear = true, colors = tripTextFieldColors(), modifier = Modifier.weight(1f))
                    TimePickerField(label = timeLabels.second, value = endTime, onValueChange = { endTime = it; error = null }, allowClear = true, colors = tripTextFieldColors(), modifier = Modifier.weight(1f))
                }

                OutlinedTextField(
                    value = timezone,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Timezone", color = Grey500) },
                    trailingIcon = {
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Grey500)
                    },
                    colors = tripTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimezonePicker = true }
                )

                if (category == EventCategory.FLIGHT) {
                    Text("Flight details", style = MaterialTheme.typography.labelMedium, color = Grey500)
                    TripTextField(value = flightNumber, onValueChange = { flightNumber = it }, label = "Flight number · optional")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TripTextField(value = departureAirportCode, onValueChange = { departureAirportCode = it }, label = "Departure airport · optional", modifier = Modifier.weight(1f))
                        TripTextField(value = arrivalAirportCode, onValueChange = { arrivalAirportCode = it }, label = "Arrival airport · optional", modifier = Modifier.weight(1f))
                    }
                } else if (category == EventCategory.LODGING) {
                    TripTextField(value = bookingNumber, onValueChange = { bookingNumber = it }, label = "Booking number · optional")
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
                        endTime.trim().takeIf { it.isNotBlank() },
                        timezone.trim(),
                        flightNumber.trim().takeIf { it.isNotBlank() },
                        departureAirportCode.trim().takeIf { it.isNotBlank() },
                        arrivalAirportCode.trim().takeIf { it.isNotBlank() },
                        bookingNumber.trim().takeIf { it.isNotBlank() }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Navy950)
            ) { Text("Add", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Grey300) } }
    )

    if (showTimezonePicker) {
        TimezonePickerDialog(
            onDismiss = { showTimezonePicker = false },
            onSelect = { zone -> timezone = zone; showTimezonePicker = false }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Add Note Dialog
// ──────────────────────────────────────────────────────────────────────────────

private enum class NoteKind(val label: String) {
    TEXT_BLOCK("Text Block"),
    CHECKLIST("Checklist"),
    LINK("Link (paste a URL)"),
    PASS("Pass (.pkpass)")
}

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
                TripTextField(value = title, onValueChange = { title = it; error = null }, label = "Title")

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
                    DatePickerField(label = "Date", value = date, onValueChange = { date = it; error = null }, colors = tripTextFieldColors(), modifier = Modifier.weight(1f))
                    TimePickerField(label = "Time", value = time, onValueChange = { time = it; error = null }, allowClear = false, colors = tripTextFieldColors(), modifier = Modifier.weight(1f))
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
// Share Trip Dialog
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShareTripDialog(
    innerCircle: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, TripRole) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(TripRole.VIEWER) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = { Text("Share Trip", style = MaterialTheme.typography.titleLarge, color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TripTextField(value = email, onValueChange = { email = it; error = null }, label = "Email address")

                if (innerCircle.isNotEmpty()) {
                    Text("From Inner Circle", style = MaterialTheme.typography.labelMedium, color = Grey500)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        innerCircle.forEach { contact ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Teal300.copy(alpha = 0.15f),
                                modifier = Modifier.clickable { email = contact; error = null }
                            ) {
                                Text(
                                    text = contact,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Teal200,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Text("Role", style = MaterialTheme.typography.labelMedium, color = Grey500)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleChip(
                        label = "Viewer",
                        selected = role == TripRole.VIEWER,
                        onClick = { role = TripRole.VIEWER },
                        modifier = Modifier.weight(1f)
                    )
                    RoleChip(
                        label = "Co-owner",
                        selected = role == TripRole.CO_OWNER,
                        onClick = { role = TripRole.CO_OWNER },
                        modifier = Modifier.weight(1f)
                    )
                }

                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = email.trim()
                    if (trimmed.isBlank() || !trimmed.contains("@")) {
                        error = "Please enter a valid email address"
                        return@Button
                    }
                    onConfirm(trimmed, role)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Navy950)
            ) { Text("Share", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Grey300) } }
    )
}

@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Teal300 else Navy700,
        border = if (!selected) BorderStroke(1.dp, Grey700) else null
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Navy950 else Grey300,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Trip Settings Dialog
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TripSettingsDialog(
    trip: TripEntity?,
    globalDefaultTimezone: String?,
    onDismiss: () -> Unit,
    onSetTimezone: (String) -> Unit,
    onClearOverride: () -> Unit
) {
    var showTimezonePicker by remember { mutableStateOf(false) }
    val override = trip?.defaultTimezone

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = { Text("Trip Settings", style = MaterialTheme.typography.titleLarge, color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This trip's default timezone",
                    style = MaterialTheme.typography.titleSmall.copy(color = Teal300, fontWeight = FontWeight.Bold)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimezonePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Navy700)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Teal300.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = null, tint = Teal300, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Overrides your global default for events in this trip",
                                style = MaterialTheme.typography.bodySmall.copy(color = Grey500)
                            )
                            Text(
                                override ?: (globalDefaultTimezone?.let { "Using global default: $it" } ?: "Not set"),
                                style = MaterialTheme.typography.bodyMedium.copy(color = White, fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Grey500, modifier = Modifier.size(18.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = Grey500, modifier = Modifier.size(14.dp))
                    Text(
                        "Falls back to your global default timezone (Settings → Preferences) when this is unset.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Grey500)
                    )
                }
                if (override != null) {
                    Text(
                        "Clear override",
                        style = MaterialTheme.typography.labelMedium.copy(color = Teal300, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.clickable { onClearOverride() }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = Grey300) } }
    )

    if (showTimezonePicker) {
        TimezonePickerDialog(
            onDismiss = { showTimezonePicker = false },
            onSelect = { zone -> onSetTimezone(zone); showTimezonePicker = false }
        )
    }
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
    NoteType.PKPASS       -> Pair(Teal300, Icons.Filled.CreditCard)
    NoteType.FILE         -> Pair(Teal200, Icons.Filled.InsertDriveFile)
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

// ──────────────────────────────────────────────────────────────────────────────
// Trip export
// ──────────────────────────────────────────────────────────────────────────────

private fun buildTripExportText(state: OpenedTripUiState): String {
    val trip = state.trip
    return buildString {
        appendLine(trip?.destination ?: "Trip")
        if (trip != null) {
            appendLine(formatDateRange(trip.startDate, trip.endDate))
        }
        appendLine()

        appendLine("ITINERARY")
        appendLine("---------")
        if (state.itineraryDays.all { it.events.isEmpty() }) {
            appendLine("No events.")
        } else {
            state.itineraryDays.forEach { day ->
                if (day.events.isEmpty()) return@forEach
                appendLine(day.date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")))
                day.events.forEach { itEvent ->
                    val event = itEvent.event
                    val timeLabel = if (event.startTime != null) {
                        if (event.endTime != null) "${event.startTime} - ${event.endTime}" else event.startTime
                    } else "All day"
                    val dayLabel = itEvent.multiDayLabel?.let { " ($it)" } ?: ""
                    append("  - $timeLabel: ${event.title}$dayLabel")
                    if (!event.location.isNullOrBlank()) append(" @ ${event.location}")
                    appendLine()
                }
            }
        }
        appendLine()

        appendLine("NOTES")
        appendLine("-----")
        if (state.notes.isEmpty()) {
            appendLine("No notes.")
        } else {
            state.notes.forEach { note -> append(formatNoteForExport(note)) }
        }
        appendLine()

        appendLine("REMINDERS")
        appendLine("---------")
        if (state.reminders.isEmpty()) {
            appendLine("No reminders.")
        } else {
            state.reminders.forEach { reminder ->
                appendLine("  - ${formatDate(reminder.date)} at ${reminder.time}: ${reminder.text}")
            }
        }
    }
}

private fun formatNoteForExport(note: NoteEntity): String = buildString {
    append("- ${note.title}")
    if (note.type == NoteType.CHECKLIST) {
        appendLine()
        val items = try {
            Json.decodeFromString<List<ChecklistItem>>(note.content)
        } catch (e: Exception) {
            emptyList()
        }
        items.forEach { item -> appendLine("    [${if (item.isChecked) "x" else " "}] ${item.text}") }
    } else {
        if (note.content.isNotBlank()) append(": ${note.content}")
        appendLine()
    }
}
