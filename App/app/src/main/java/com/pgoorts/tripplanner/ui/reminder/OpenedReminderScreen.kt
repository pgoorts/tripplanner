@file:OptIn(ExperimentalMaterial3Api::class)

package com.pgoorts.tripplanner.ui.reminder

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pgoorts.tripplanner.data.local.entity.TripRole
import com.pgoorts.tripplanner.ui.components.DatePickerField
import com.pgoorts.tripplanner.ui.components.TimePickerField
import com.pgoorts.tripplanner.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun OpenedReminderScreen(
    reminderId: String,
    onBack: () -> Unit,
    viewModel: OpenedReminderViewModel = hiltViewModel()
) {
    val reminder by viewModel.reminderFlow.collectAsState()
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val canEdit = currentUserRole != TripRole.VIEWER
    val context = LocalContext.current

    var text by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }

    LaunchedEffect(reminder) {
        reminder?.let {
            text = it.text
            date = it.date
            time = it.time
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Edit Reminder", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (canEdit) {
                        IconButton(onClick = {
                            val error = validateReminderForm(text, date, time)
                            if (error != null) {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.updateReminder(
                                    text = text.trim(),
                                    date = date.trim(),
                                    time = time.trim()
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
        if (reminder == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal300)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Reminder Description", color = Grey500) },
                    enabled = canEdit,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = tripTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(
                        label = "Date",
                        value = date,
                        onValueChange = { date = it },
                        enabled = canEdit,
                        colors = tripTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    TimePickerField(
                        label = "Time",
                        value = time,
                        onValueChange = { time = it },
                        enabled = canEdit,
                        allowClear = false,
                        colors = tripTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
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

private fun validateReminderForm(text: String, date: String, time: String): String? {
    if (text.isBlank()) return "Please enter reminder text"
    try {
        LocalDate.parse(date.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (e: DateTimeParseException) {
        return "Date must be YYYY-MM-DD"
    }
    if (!time.trim().matches(Regex("\\d{2}:\\d{2}"))) {
        return "Time must be HH:MM"
    }
    return null
}
