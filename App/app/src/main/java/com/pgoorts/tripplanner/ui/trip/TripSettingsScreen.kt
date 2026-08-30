@file:OptIn(ExperimentalMaterial3Api::class)

package com.pgoorts.tripplanner.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pgoorts.tripplanner.ui.components.DatePickerField
import com.pgoorts.tripplanner.ui.components.TimezonePickerDialog
import com.pgoorts.tripplanner.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Trip Settings, expanded from a Phase 3 dialog into a full screen per description_detail.txt §8
 * (Bug 7): editable trip dates, the existing default-timezone override, and (Block 5) a cover-photo
 * override — matching designmockups/TripSettingsDates.png.
 */
@Composable
fun TripSettingsScreen(
    onBack: () -> Unit,
    viewModel: OpenedTripViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val globalDefaultTimezone by viewModel.globalDefaultTimezone.collectAsState()
    val trip = uiState.trip

    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf<String?>(null) }
    var showTimezonePicker by remember { mutableStateOf(false) }

    LaunchedEffect(trip?.id) {
        trip?.let {
            startDate = it.startDate
            endDate = it.endDate
        }
    }

    fun trySaveDates(newStart: String, newEnd: String) {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val start = try { LocalDate.parse(newStart.trim(), fmt) } catch (e: DateTimeParseException) { null }
        val end = try { LocalDate.parse(newEnd.trim(), fmt) } catch (e: DateTimeParseException) { null }
        dateError = when {
            start == null || end == null -> null // still typing/picking; not an error yet
            end.isBefore(start) -> "End date must be after start date"
            else -> null
        }
        if (start != null && end != null && !end.isBefore(start)) {
            viewModel.updateTripDates(newStart.trim(), newEnd.trim())
        }
    }

    val override = trip?.defaultTimezone

    Scaffold(
        containerColor = Navy900,
        topBar = {
            TopAppBar(
                title = { Text("Trip Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                if (trip != null) {
                    Text(
                        text = "${trip.destination.uppercase()} · ${formatTripDateRange(trip.startDate, trip.endDate)}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Grey500)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            item {
                Text(
                    "Trip dates",
                    style = MaterialTheme.typography.titleSmall.copy(color = Teal300, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(
                        label = "Start date",
                        value = startDate,
                        onValueChange = { startDate = it; trySaveDates(it, endDate) },
                        colors = tripSettingsFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    DatePickerField(
                        label = "End date",
                        value = endDate,
                        onValueChange = { endDate = it; trySaveDates(startDate, it) },
                        colors = tripSettingsFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (dateError != null) {
                    Text(dateError!!, style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                    Text(
                        "Narrowing these dates won't delete or hide events — ones that now fall outside will be flagged invalid.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Grey500)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Text(
                    "This trip's default timezone",
                    style = MaterialTheme.typography.titleSmall.copy(color = Teal300, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(6.dp))
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
                if (override != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Clear override",
                        style = MaterialTheme.typography.labelMedium.copy(color = Teal300, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.clickable { viewModel.setTripDefaultTimezone(null) }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Text(
                    "Cover photo",
                    style = MaterialTheme.typography.titleSmall.copy(color = Teal300, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Coming soon.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Grey500)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showTimezonePicker) {
        TimezonePickerDialog(
            onDismiss = { showTimezonePicker = false },
            onSelect = { zone -> viewModel.setTripDefaultTimezone(zone); showTimezonePicker = false }
        )
    }
}

private fun formatTripDateRange(startDate: String, endDate: String): String = try {
    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    "${LocalDate.parse(startDate).format(fmt)} – ${LocalDate.parse(endDate).format(fmt)}"
} catch (e: Exception) {
    "$startDate – $endDate"
}

@Composable
private fun tripSettingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Grey100,
    unfocusedTextColor = Grey100,
    focusedBorderColor = Teal300,
    unfocusedBorderColor = Grey700,
    focusedLabelColor = Teal300,
    cursorColor = Teal300
)
