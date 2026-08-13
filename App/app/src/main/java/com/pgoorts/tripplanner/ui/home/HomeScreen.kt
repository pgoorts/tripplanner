package com.pgoorts.tripplanner.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pgoorts.tripplanner.data.local.entity.TripEntity
import com.pgoorts.tripplanner.ui.components.DatePickerField
import com.pgoorts.tripplanner.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun HomeScreen(
    onTripClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Teal300,
                contentColor = Navy950,
                shape = CircleShape,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Trip",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header
            item {
                HomeHeader(onProfileClick = onProfileClick)
            }

            // Currently active trips
            if (uiState.currentTrips.isNotEmpty()) {
                item {
                    SectionHeader(title = "Current Trips")
                }
                items(uiState.currentTrips, key = { it.id }) { trip ->
                    LargeTripCard(
                        trip = trip,
                        onClick = { onTripClick(trip.id) },
                        onDelete = { viewModel.deleteTrip(trip) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Upcoming trips
            if (uiState.upcomingTrips.isNotEmpty()) {
                item {
                    SectionHeader(title = "Upcoming Trips")
                }
                items(uiState.upcomingTrips, key = { it.id }) { trip ->
                    LargeTripCard(
                        trip = trip,
                        onClick = { onTripClick(trip.id) },
                        onDelete = { viewModel.deleteTrip(trip) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Past trips
            if (uiState.pastTrips.isNotEmpty()) {
                item {
                    SectionHeader(title = "Past Trips")
                }
                items(uiState.pastTrips, key = { it.id }) { trip ->
                    SmallTripCard(
                        trip = trip,
                        onClick = { onTripClick(trip.id) },
                        onDelete = { viewModel.deleteTrip(trip) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Empty state
            if (!uiState.isLoading &&
                uiState.currentTrips.isEmpty() &&
                uiState.upcomingTrips.isEmpty() &&
                uiState.pastTrips.isEmpty()
            ) {
                item {
                    EmptyState(onAddClick = { showAddDialog = true })
                }
            }
        }
    }

    if (showAddDialog) {
        AddTripDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { destination, startDate, endDate ->
                viewModel.createTrip(destination, startDate, endDate)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun HomeHeader(onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Public,
            contentDescription = null,
            tint = Teal300,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "TripPlanner",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Navy700)
                .clickable { onProfileClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Profile",
                tint = Teal300,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LargeTripCard(
    trip: TripEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateRange = formatDateRange(trip.startDate, trip.endDate)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Navy700),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Destination photo placeholder — gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Navy600, Navy800)
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Flight,
                    contentDescription = null,
                    tint = Grey700,
                    modifier = Modifier
                        .size(80.dp)
                        .align(Alignment.Center)
                )
            }

            // Bottom gradient scrim for text legibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC080E1A))
                        )
                    )
            )

            // Text overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = trip.destination,
                    style = MaterialTheme.typography.titleLarge,
                    color = White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dateRange,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grey300
                )
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete trip",
                    tint = Grey300.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallTripCard(
    trip: TripEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateRange = formatDateRange(trip.startDate, trip.endDate)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(80.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Navy700),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Small thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(listOf(Navy600, Navy800))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Flight,
                    contentDescription = null,
                    tint = Grey700,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.destination,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = Grey500,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = dateRange,
                        style = MaterialTheme.typography.bodySmall,
                        color = Grey500
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete trip",
                    tint = Grey700,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun EmptyState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(
            imageVector = Icons.Filled.Flight,
            contentDescription = null,
            tint = Grey700,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No trips yet",
            style = MaterialTheme.typography.titleMedium,
            color = Grey300
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap the + button to plan your first adventure",
            style = MaterialTheme.typography.bodyMedium,
            color = Grey500
        )
    }
}

@Composable
private fun AddTripDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var destination by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = {
            Text(
                text = "New Trip",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it; errorMessage = null },
                    label = { Text("Destination", color = Grey500) },
                    placeholder = { Text("e.g. Rome, Italy", color = Grey700) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Grey100,
                        unfocusedTextColor = Grey100,
                        focusedBorderColor = Teal300,
                        unfocusedBorderColor = Grey700,
                        focusedLabelColor = Teal300,
                        cursorColor = Teal300
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Start Date",
                    value = startDate,
                    onValueChange = { startDate = it; errorMessage = null },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Grey100,
                        unfocusedTextColor = Grey100,
                        focusedBorderColor = Teal300,
                        unfocusedBorderColor = Grey700,
                        focusedLabelColor = Teal300,
                        cursorColor = Teal300
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "End Date",
                    value = endDate,
                    onValueChange = { endDate = it; errorMessage = null },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Grey100,
                        unfocusedTextColor = Grey100,
                        focusedBorderColor = Teal300,
                        unfocusedBorderColor = Grey700,
                        focusedLabelColor = Teal300,
                        cursorColor = Teal300
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val error = validateTripInput(destination, startDate, endDate)
                    if (error != null) {
                        errorMessage = error
                    } else {
                        onConfirm(destination.trim(), startDate.trim(), endDate.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal300,
                    contentColor = Navy950
                )
            ) {
                Text("Add Trip", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Grey300)
            }
        }
    )
}

private fun validateTripInput(destination: String, startDate: String, endDate: String): String? {
    if (destination.isBlank()) return "Please enter a destination"
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val start = try { LocalDate.parse(startDate.trim(), fmt) } catch (e: DateTimeParseException) {
        return "Start date must be YYYY-MM-DD"
    }
    val end = try { LocalDate.parse(endDate.trim(), fmt) } catch (e: DateTimeParseException) {
        return "End date must be YYYY-MM-DD"
    }
    if (end.isBefore(start)) return "End date must be after start date"
    return null
}

private fun formatDateRange(startDate: String, endDate: String): String {
    return try {
        val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
        val start = LocalDate.parse(startDate).format(fmt)
        val end = LocalDate.parse(endDate).format(fmt)
        "$start – $end"
    } catch (e: Exception) {
        "$startDate – $endDate"
    }
}
