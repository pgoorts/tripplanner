package com.pgoorts.tripplanner.photo

import android.util.Log
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

    private const val TAG = "CoverPhotoFetcher"

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun fetchCoverPhoto(destination: String): CoverPhotoResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "fetchCoverPhoto(\"$destination\") starting")
        fetchFromPlaces(destination)?.let { return@withContext CoverPhotoResult(it, CoverPhotoSource.AUTO_PLACES) }
        fetchFromUnsplash(destination)?.let { return@withContext CoverPhotoResult(it, CoverPhotoSource.AUTO_UNSPLASH) }
        Log.w(TAG, "fetchCoverPhoto(\"$destination\") found nothing from either source")
        null
    }

    private fun fetchFromPlaces(destination: String): ByteArray? {
        val apiKey = BuildConfig.PLACES_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "Places skipped: PLACES_API_KEY is blank (not set in local.properties at build time)")
            return null
        }
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
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Places searchText failed: HTTP ${response.code} — $responseBody")
                    return null
                }
                val json = Json.parseToJsonElement(responseBody ?: run {
                    Log.w(TAG, "Places searchText returned an empty body")
                    return null
                }).jsonObject
                val places = json["places"]?.jsonArray
                if (places == null) {
                    Log.w(TAG, "Places searchText returned no \"places\" for \"$destination\": $responseBody")
                    return null
                }
                places.firstNotNullOfOrNull { place ->
                    (place.jsonObject["photos"] as? JsonArray)?.firstOrNull()
                        ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                }
            } ?: run {
                Log.w(TAG, "Places searchText returned no place with a photo for \"$destination\"")
                return null
            }

            val mediaRequest = Request.Builder()
                .url("https://places.googleapis.com/v1/$photoName/media?maxWidthPx=800&key=$apiKey")
                .build()
            client.newCall(mediaRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Places photo media fetch failed: HTTP ${response.code} — ${response.body?.string()}")
                    null
                } else {
                    Log.d(TAG, "Places photo fetched successfully for \"$destination\"")
                    response.body?.bytes()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Places fetch threw for \"$destination\"", e)
            null
        }
    }

    private fun fetchFromUnsplash(destination: String): ByteArray? {
        val accessKey = BuildConfig.UNSPLASH_ACCESS_KEY
        if (accessKey.isBlank()) {
            Log.w(TAG, "Unsplash skipped: UNSPLASH_ACCESS_KEY is blank (not set in local.properties at build time)")
            return null
        }
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
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Unsplash search failed: HTTP ${response.code} — $responseBody")
                    return null
                }
                val json = Json.parseToJsonElement(responseBody ?: run {
                    Log.w(TAG, "Unsplash search returned an empty body")
                    return null
                }).jsonObject
                val results = json["results"]?.jsonArray
                if (results == null) {
                    Log.w(TAG, "Unsplash search returned no \"results\" for \"$destination\": $responseBody")
                    return null
                }
                results.firstOrNull()?.jsonObject?.get("urls")?.jsonObject?.get("regular")?.jsonPrimitive?.contentOrNull
            } ?: run {
                Log.w(TAG, "Unsplash search returned no usable photo URL for \"$destination\"")
                return null
            }

            val imageRequest = Request.Builder().url(photoUrl).build()
            client.newCall(imageRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Unsplash image download failed: HTTP ${response.code}")
                    null
                } else {
                    Log.d(TAG, "Unsplash photo fetched successfully for \"$destination\"")
                    response.body?.bytes()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unsplash fetch threw for \"$destination\"", e)
            null
        }
    }
}
