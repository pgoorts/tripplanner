package com.pgoorts.tripplanner.photo

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import java.security.MessageDigest

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

    suspend fun fetchCoverPhoto(context: Context, destination: String): CoverPhotoResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "fetchCoverPhoto(\"$destination\") starting")
        fetchFromPlaces(context, destination)?.let { return@withContext CoverPhotoResult(it, CoverPhotoSource.AUTO_PLACES) }
        fetchFromUnsplash(destination)?.let { return@withContext CoverPhotoResult(it, CoverPhotoSource.AUTO_UNSPLASH) }
        Log.w(TAG, "fetchCoverPhoto(\"$destination\") found nothing from either source")
        null
    }

    /**
     * The Places API key is restricted to this app's package + signing cert in Cloud Console.
     * Direct REST calls (as opposed to the Places SDK) must prove that identity themselves via
     * these headers, or Google rejects the request with API_KEY_ANDROID_APP_BLOCKED.
     */
    private fun androidAppHeaders(context: Context): Pair<String, String>? {
        val packageName = context.packageName
        val sha1 = signingCertSha1(context) ?: return null
        return packageName to sha1
    }

    private fun signingCertSha1(context: Context): String? {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = info.signingInfo ?: return null
                if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                info.signatures
            }
            val cert = signatures?.firstOrNull() ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(cert.toByteArray())
            digest.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not compute signing cert SHA-1 for Android key restriction", e)
            null
        }
    }

    private fun fetchFromPlaces(context: Context, destination: String): ByteArray? {
        val apiKey = BuildConfig.PLACES_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "Places skipped: PLACES_API_KEY is blank (not set in local.properties at build time)")
            return null
        }
        val (androidPackage, androidCert) = androidAppHeaders(context) ?: run {
            Log.w(TAG, "Places skipped: could not resolve package/signing cert for X-Android-* headers")
            return null
        }
        return try {
            val body = """{"textQuery":"${destination.replace("\"", "\\\"")}"}"""
                .toRequestBody(jsonMediaType)
            val searchRequest = Request.Builder()
                .url("https://places.googleapis.com/v1/places:searchText")
                .addHeader("X-Goog-Api-Key", apiKey)
                .addHeader("X-Goog-FieldMask", "places.photos")
                .addHeader("X-Android-Package", androidPackage)
                .addHeader("X-Android-Cert", androidCert)
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
                .addHeader("X-Android-Package", androidPackage)
                .addHeader("X-Android-Cert", androidCert)
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
