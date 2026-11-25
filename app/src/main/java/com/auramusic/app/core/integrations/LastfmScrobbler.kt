package com.auramusic.app.core.integrations

import android.util.Log
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.domain.model.AuraTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest

object LastfmScrobbler {
    private const val TAG = "LastfmScrobbler"
    private const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"

    /**
     * Computes Last.fm `api_sig`: parameters sorted lexicographically by key (excluding
     * `format` and `callback`), concatenated as `<key><value>`, followed by [secret],
     * then hashed as lowercase MD5 hex.
     */
    fun computeApiSig(params: Map<String, String>, secret: String): String {
        val canonical = StringBuilder()
        params.entries
            .filter { it.key != "format" && it.key != "callback" }
            .sortedBy { it.key }
            .forEach { (key, value) ->
                canonical.append(key).append(value)
            }
        canonical.append(secret)
        val digest = MessageDigest.getInstance("MD5")
            .digest(canonical.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun updateNowPlaying(
        track: AuraTrack,
        sessionKey: String,
        apiKey: String,
        apiSecret: String
    ): Boolean {
        if (sessionKey.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) return false
        if (track.title.isBlank() || track.artist.isBlank()) return false

        val params = linkedMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to track.artist,
            "track" to track.title,
            "api_key" to apiKey,
            "sk" to sessionKey
        )
        if (track.album.isNotBlank() && !track.album.equals("YouTube Music", ignoreCase = true)) {
            params["album"] = track.album
        }
        if (track.durationSeconds > 0) {
            params["duration"] = track.durationSeconds.toString()
        }
        return postSigned(params, apiSecret)
    }

    suspend fun scrobble(
        track: AuraTrack,
        timestampSec: Long,
        sessionKey: String,
        apiKey: String,
        apiSecret: String
    ): Boolean {
        if (sessionKey.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) return false
        if (track.title.isBlank() || track.artist.isBlank() || timestampSec <= 0L) return false

        val params = linkedMapOf(
            "method" to "track.scrobble",
            "artist" to track.artist,
            "track" to track.title,
            "timestamp" to timestampSec.toString(),
            "api_key" to apiKey,
            "sk" to sessionKey
        )
        if (track.album.isNotBlank() && !track.album.equals("YouTube Music", ignoreCase = true)) {
            params["album"] = track.album
        }
        if (track.durationSeconds > 0) {
            params["duration"] = track.durationSeconds.toString()
        }
        return postSigned(params, apiSecret)
    }

    private suspend fun postSigned(
        params: Map<String, String>,
        apiSecret: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiSig = computeApiSig(params, apiSecret)
            val bodyBuilder = FormBody.Builder()
            params.forEach { (key, value) -> bodyBuilder.add(key, value) }
            bodyBuilder.add("api_sig", apiSig)
            bodyBuilder.add("format", "json")

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(bodyBuilder.build())
                .build()

            SimpMusicExtractorEngine.httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                resp.isSuccessful && !body.contains("\"error\"")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Last.fm request failed: ${e.message}")
            false
        }
    }
}
