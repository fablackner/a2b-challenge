package io.github.fablackner.a2b.data

import android.content.Context
import android.util.Log
import io.github.fablackner.a2b.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The online track directory: a Supabase table (see `supabase/schema.sql`)
 * that lets everyone see every published track on the map.
 *
 * Talks to the table's REST endpoint directly, so no extra HTTP or JSON
 * libraries are needed. Everything fails soft: without internet (or without
 * SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY at build time) the app simply works
 * with the tracks it knows locally.
 */
object OnlineTracks {

    private const val TAG = "OnlineTracks"
    private const val TIMEOUT_MS = 10_000

    /** All published tracks, or null if the directory could not be reached. */
    suspend fun fetchAll(context: Context): List<Track>? = withContext(Dispatchers.IO) {
        val config = Config.from(context) ?: return@withContext null
        try {
            val (code, body) = request(config, "GET", query = "?select=*")
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Fetching tracks failed ($code): $body")
                return@withContext null
            }
            val rows = JSONArray(body)
            (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.toTrackOrNull() }
        } catch (e: Exception) {
            Log.w(TAG, "Fetching tracks failed", e)
            null
        }
    }

    /**
     * Publishes [track]. Returns false if the upload failed; [sync] retries it
     * on the next app start. Publishing an already published track is a no-op.
     */
    suspend fun publish(context: Context, track: Track): Boolean = withContext(Dispatchers.IO) {
        val config = Config.from(context) ?: return@withContext true
        try {
            val (code, body) = request(config, "POST", body = track.toJson().toString())
            when (code) {
                HttpURLConnection.HTTP_CREATED, HttpURLConnection.HTTP_CONFLICT -> true
                else -> {
                    Log.w(TAG, "Publishing track ${track.stamp} failed ($code): $body")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Publishing track ${track.stamp} failed", e)
            false
        }
    }

    /**
     * Fetches the published tracks and uploads any of our own [localTracks]
     * that are missing online (e.g. created while offline).
     */
    suspend fun sync(context: Context, localTracks: List<Track>): List<Track>? {
        val online = fetchAll(context) ?: return null
        val onlineStamps = online.mapTo(HashSet()) { it.stamp }
        localTracks
            .filter { it.createdByMe && it.stamp !in onlineStamps }
            .forEach { publish(context, it) }
        return online
    }

    private fun request(config: Config, method: String, query: String = "", body: String? = null): Pair<Int, String> {
        val connection = URL(config.tracksEndpoint + query).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("apikey", config.key)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Prefer", "return=minimal")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code < HttpURLConnection.HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    private fun Track.toJson(): JSONObject = JSONObject()
        .put("stamp", stamp)
        .put("name", name)
        .put("description", description)
        .put("horizontal_meters", horizontalMeters)
        .put("vertical_meters", verticalMeters)
        .put("start_lat", startLat)
        .put("start_lng", startLng)
        .put("finish_lat", finishLat)
        .put("finish_lng", finishLng)

    private fun JSONObject.toTrackOrNull(): Track? = try {
        Track(
            stamp = getInt("stamp"),
            name = getString("name"),
            description = optString("description"),
            horizontalMeters = getInt("horizontal_meters"),
            verticalMeters = getInt("vertical_meters"),
            startLat = getDouble("start_lat"),
            startLng = getDouble("start_lng"),
            finishLat = getDouble("finish_lat"),
            finishLng = getDouble("finish_lng"),
            createdByMe = false,
        )
    } catch (e: Exception) {
        Log.w(TAG, "Skipping malformed track row: $this", e)
        null
    }

    private class Config(val tracksEndpoint: String, val key: String) {
        companion object {
            fun from(context: Context): Config? {
                val url = context.getString(R.string.supabase_url).trimEnd('/')
                val key = context.getString(R.string.supabase_publishable_key)
                if (url.isBlank() || key.isBlank()) return null
                return Config("$url/rest/v1/tracks", key)
            }
        }
    }
}
