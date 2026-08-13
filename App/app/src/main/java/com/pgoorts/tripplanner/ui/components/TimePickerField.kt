package com.pgoorts.tripplanner.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A read-only text field that opens a Material3 time picker dialog when tapped.
 *
 * The [value] and [onValueChange] contract stays a plain "HH:MM" (24-hour) string, so this is a
 * drop-in replacement for a free-text time [OutlinedTextField]. Material3 doesn't ship a
 * TimePickerDialog, so a plain [AlertDialog] wraps the [TimePicker].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    allowClear: Boolean = false,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("HH:MM") },
            trailingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
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
        val (initialHour, initialMinute) = value.trim().let { raw ->
            if (raw.isBlank()) {
                null
            } else {
                try {
                    LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm"))
                } catch (e: Exception) {
                    null
                }
            }
        }?.let { it.hour to it.minute } ?: (9 to 0)

        val state = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange("%02d:%02d".format(state.hour, state.minute))
                        showDialog = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (allowClear) {
                        TextButton(
                            onClick = {
                                onValueChange("")
                                showDialog = false
                            }
                        ) { Text("Clear") }
                    }
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            },
            text = {
                TimePicker(state = state)
            }
        )
    }
}
