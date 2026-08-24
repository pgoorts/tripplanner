@file:OptIn(ExperimentalMaterial3Api::class)

package com.pgoorts.tripplanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pgoorts.tripplanner.ui.theme.Grey100
import com.pgoorts.tripplanner.ui.theme.Grey500
import com.pgoorts.tripplanner.ui.theme.Grey700
import com.pgoorts.tripplanner.ui.theme.Navy800
import com.pgoorts.tripplanner.ui.theme.Teal300
import com.pgoorts.tripplanner.ui.theme.White
import java.time.ZoneId

/** Modal, search-filtered picker over every IANA timezone ID. */
@Composable
fun TimezonePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val allZones = remember { ZoneId.getAvailableZoneIds().sorted() }
    val filteredZones = remember(query) {
        if (query.isBlank()) allZones else allZones.filter { it.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = { Text("Select Timezone", style = MaterialTheme.typography.titleLarge, color = White) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search timezone...", color = Grey500) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Grey100,
                        unfocusedTextColor = Grey100,
                        focusedBorderColor = Teal300,
                        unfocusedBorderColor = Grey700,
                        cursorColor = Teal300
                    )
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filteredZones, key = { it }) { zone ->
                        Text(
                            text = zone,
                            color = Grey100,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(zone) }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Grey500) } }
    )
}
