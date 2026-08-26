package com.pgoorts.tripplanner.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.pgoorts.tripplanner.ui.theme.ErrorRed
import com.pgoorts.tripplanner.ui.theme.Grey300
import com.pgoorts.tripplanner.ui.theme.Navy800
import com.pgoorts.tripplanner.ui.theme.White

/** Reusable "are you sure" dialog shown before any destructive delete action. */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = White, fontWeight = FontWeight.Bold) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = Grey300) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = White)
            ) { Text("Delete", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Grey300) } }
    )
}
