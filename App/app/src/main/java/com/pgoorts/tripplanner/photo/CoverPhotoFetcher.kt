package com.pgoorts.tripplanner.photo

import com.pgoorts.tripplanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** One of the values TripEntity.coverPhotoSource can hold, per datastructure.txt §2. */
object CoverPhotoSource {
    const val AUTO_PLACES = "AUTO_PLACES"
    const val AUTO_UNSPLASH = "AUTO_UNSPLASH"
    const val USER = "USER"
}

data class CoverPhotoResult(val bytes: ByteArray, val source: String)

/**
 * Fetches a representative destination photo per description_detail.txt §7/techstack.txt §6:
 * Google Places API (New) first, Unsplash Search API as a fallback. Returns null (never throws)
 * when both fail, a key is unconfigured, or there's no usable result — callers fall back to
 * [CoverIllustration] in that case.
 */
object CoverPhotoFetcher {

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun fetchCoverPhoto(destination: String): CoverPhotoResult? = withContext(Dispatchers.IO) {
        fetchFromPlaces(destination)?.let { return@withContext CoverPhotoResult(it, CoverPhotoSource.AUTO_PLACES) }
        fetchFromUnsplash(destination)?.let { return@withContext CoverPhotoResult(it, CoverPhotoSource.AUTO_UNSPLASH) }
        null
    }

    private fun fetchFromPlaces(destination: String): ByteArray? {
        val apiKey = BuildConfig.PLACES_API_KEY
        if (apiKey.isBlank()) return null
        return try {
            val body = """{"textQuery":"${destination.replace("\"", "\\\"")}"}"""
                .toRequestBody(jsonMediaType)
            val searchRequest = Request.Builder()
                .url("https://places.googleapis.com/v1/places:searchText")
                .addHeader("X-Goog-Api-Key", apiKey)
                .addHeader("X-Goog-FieldMask", "places.photos")
                .post(body)
                .build()

            val photoName = client.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = Json.parseToJsonElement(response.body?.string() ?: return null).jsonObject
                val places = json["places"]?.jsonArray ?: return null
                places.firstNotNullOfOrNull { place ->
                    (place.jsonObject["photos"] as? JsonArray)?.firstOrNull()
                        ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                }
            } ?: return null

            val mediaRequest = Request.Builder()
                .url("https://places.googleapis.com/v1/$photoName/media?maxWidthPx=800&key=$apiKey")
                .build()
            client.newCall(mediaRequest).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun fetchFromUnsplash(destination: String): ByteArray? {
        val accessKey = BuildConfig.UNSPLASH_ACCESS_KEY
        if (accessKey.isBlank()) return null
        return try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.unsplash.com")
                .addPathSegments("search/photos")
                .addQueryParameter("per_page", "1")
                .addQueryParameter("query", destination)
                .build()
            val searchRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Client-ID $accessKey")
                .build()

            val photoUrl = client.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = Json.parseToJsonElement(response.body?.string() ?: return null).jsonObject
                val results = json["results"]?.jsonArray ?: return null
                results.firstOrNull()?.jsonObject?.get("urls")?.jsonObject?.get("regular")?.jsonPrimitive?.contentOrNull
            } ?: return null

            val imageRequest = Request.Builder().url(photoUrl).build()
            client.newCall(imageRequest).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
