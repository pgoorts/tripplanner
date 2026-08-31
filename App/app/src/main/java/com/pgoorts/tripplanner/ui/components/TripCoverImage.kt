package com.pgoorts.tripplanner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.google.firebase.storage.FirebaseStorage
import com.pgoorts.tripplanner.data.local.entity.TripEntity
import com.pgoorts.tripplanner.photo.CoverIllustration
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Resolves and renders a trip's cover photo the same way everywhere it's shown (Home cards, Trip
 * Settings, Add Trip preview) — per Bug 6/description_detail.txt §7:
 * 1. A freshly staged local file, if one hasn't synced yet — shown immediately, no network
 *    round-trip needed (covers both the auto-fetch and manual-override paths right after they run).
 * 2. Else the synced Storage object, resolved to a download URL and loaded via Coil.
 * 3. Else the offline illustration fallback — always available, no network required.
 */
@Composable
fun TripCoverImage(
    trip: TripEntity,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val localPath = trip.localCoverPhotoPath
    val storagePath = trip.coverPhotoStoragePath
    when {
        localPath != null -> AsyncImage(
            model = File(localPath),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
        )
        storagePath != null -> {
            val downloadUrl = rememberStorageDownloadUrl(storagePath)
            if (downloadUrl != null) {
                AsyncImage(
                    model = downloadUrl,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = modifier
                )
            } else {
                CoverIllustration(destination = trip.destination, modifier = modifier)
            }
        }
        else -> CoverIllustration(destination = trip.destination, modifier = modifier)
    }
}

@Composable
private fun rememberStorageDownloadUrl(storagePath: String): String? {
    var url by remember(storagePath) { mutableStateOf<String?>(null) }
    LaunchedEffect(storagePath) {
        url = try {
            FirebaseStorage.getInstance().reference.child(storagePath).downloadUrl.await().toString()
        } catch (e: Exception) {
            null
        }
    }
    return url
}
