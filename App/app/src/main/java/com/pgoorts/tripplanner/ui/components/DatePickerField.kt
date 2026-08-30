package com.pgoorts.tripplanner.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Parses an ISO ("YYYY-MM-DD") date string to UTC start-of-day epoch millis, or null if invalid. */
fun isoDateToEpochMillis(date: String): Long? = try {
    LocalDate.parse(date.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
} catch (e: Exception) {
    null
}

/**
 * A read-only text field that opens a Material3 date picker dialog when tapped.
 *
 * The [value] and [onValueChange] contract stays a plain ISO ("YYYY-MM-DD") string, so this is a
 * drop-in replacement for a free-text date [OutlinedTextField].
 *
 * [defaultMillis], per description_detail.txt §2 (Bug 1), only ever applies when [value] is blank
 * — a trip-scoped Add dialog passes its trip's start date so the picker opens pre-scrolled there
 * instead of today's date; it's never a hard bound, and editing an existing value is unaffected
 * since [value] itself always wins when non-blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    defaultMillis: Long? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("YYYY-MM-DD") },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            supportingText = supportingText,
            colors = colors,
            modifier = Modifier.fillMaxWidth()
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures { showDialog = true }
                    }
            )
        }
    }

    if (showDialog) {
        val initialMillis = value.trim().takeIf { it.isNotBlank() }?.let { isoDateToEpochMillis(it) }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis ?: defaultMillis)

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onValueChange(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        }
                        showDialog = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
