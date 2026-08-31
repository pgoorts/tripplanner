package com.pgoorts.tripplanner.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pgoorts.tripplanner.ui.theme.Grey700
import com.pgoorts.tripplanner.ui.theme.Navy600
import com.pgoorts.tripplanner.ui.theme.Navy800

/**
 * Offline/no-photo cover fallback (Bug 6): a small fixed set of gradient+icon combinations chosen
 * by keyword-substring match against the trip's destination, generic as the last resort — per
 * techstack.txt §6. Never touches the network, so it always has something to show.
 */
@Composable
fun CoverIllustration(destination: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Brush.linearGradient(colors = listOf(Navy600, Navy800))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = illustrationIconFor(destination),
            contentDescription = null,
            tint = Grey700,
            modifier = Modifier.size(64.dp)
        )
    }
}

private val KNOWN_CITY_KEYWORDS = listOf(
    "paris", "london", "tokyo", "new york", "rome", "berlin", "madrid", "lisbon", "amsterdam",
    "bangkok", "dubai", "singapore", "sydney", "cairo", "beijing", "moscow", "toronto", "chicago"
)

private fun illustrationIconFor(destination: String): ImageVector {
    val d = destination.lowercase()
    return when {
        d.contains("beach") || d.contains("island") -> Icons.Filled.BeachAccess
        d.contains("mountain") || d.contains("hik") -> Icons.Filled.Terrain
        d.contains("city") || KNOWN_CITY_KEYWORDS.any { d.contains(it) } -> Icons.Filled.LocationCity
        else -> Icons.Filled.Flight
    }
}
