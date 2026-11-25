package com.auramusic.app.core.integrations

import android.util.Log
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.domain.model.AuraTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

object DiscordRpcManager {
    private const val TAG = "DiscordRpcManager"
    private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
    private const val MIN_PRESENCE_INTERVAL_MS = 4_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null

    @Volatile
    private var activeToken: String = ""

    @Volatile
    private var isIdentified: Boolean = false

    @Volatile
    private var lastSequence: Int? = null

    @Volatile
    private var lastSentAtMs: Long = 0L

    @Volatile
    private var lastSentTrackId: String? = null

    @Volatile
    private var lastSentPlaying: Boolean? = null

    private var pendingPresencePayload: String? = null

    fun connect(token: String) {
        val clean = token.trim()
        if (clean.isBlank()) {
            disconnect()
            return
        }
        synchronized(stateLock) {
            if (clean == activeToken && webSocket != null) return
            disconnectLocked()
            activeToken = clean
            openSocketLocked(clean)
        }
    }

    fun updatePresence(
        track: AuraTrack?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        if (activeToken.isBlank()) return
        if (track == null) {
            clearPresence()
            return
        }

        val now = System.currentTimeMillis()
        val trackChanged = track.videoId != lastSentTrackId || isPlaying != lastSentPlaying
        if (!trackChanged && now - lastSentAtMs < MIN_PRESENCE_INTERVAL_MS) {
            return
        }

        val payload = buildPresencePayload(track, isPlaying, positionMs, durationMs, now)
        synchronized(stateLock) {
            if (activeToken.isBlank()) return
            if (webSocket == null) {
                openSocketLocked(activeToken)
            }
            if (!isIdentified || webSocket == null) {
                pendingPresencePayload = payload
                return
            }
            if (sendFrameLocked(payload)) {
                lastSentAtMs = now
                lastSentTrackId = track.videoId
                lastSentPlaying = isPlaying
                pendingPresencePayload = null
            }
        }
    }

    fun clearPresence() {
        if (activeToken.isBlank()) return
        val payload = JSONObject().apply {
            put("op", 3)
            put("d", JSONObject().apply {
                put("since", JSONObject.NULL)
                put("activities", JSONArray())
                put("status", "online")
                put("afk", false)
            })
        }.toString()

        synchronized(stateLock) {
            lastSentTrackId = null
            lastSentPlaying = null
            if (!isIdentified || webSocket == null) {
                pendingPresencePayload = payload
                return
            }
            sendFrameLocked(payload)
            lastSentAtMs = System.currentTimeMillis()
        }
    }

    fun disconnect() {
        synchronized(stateLock) {
            disconnectLocked()
        }
    }

    private fun disconnectLocked() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        runCatching { webSocket?.close(1000, "Disconnected") }
        webSocket = null
        activeToken = ""
        isIdentified = false
        lastSequence = null
        lastSentAtMs = 0L
        lastSentTrackId = null
        lastSentPlaying = null
        pendingPresencePayload = null
    }

    private fun openSocketLocked(token: String) {
        val request = Request.Builder().url(GATEWAY_URL).build()
        isIdentified = false
        webSocket = SimpMusicExtractorEngine.httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleGatewayMessage(webSocket, token, text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Discord Gateway failure: ${t.message}")
                    synchronized(stateLock) {
                        if (this@DiscordRpcManager.webSocket === webSocket) {
                            heartbeatJob?.cancel()
                            heartbeatJob = null
                            this@DiscordRpcManager.webSocket = null
                            isIdentified = false
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    synchronized(stateLock) {
                        if (this@DiscordRpcManager.webSocket === webSocket) {
                            heartbeatJob?.cancel()
                            heartbeatJob = null
                            this@DiscordRpcManager.webSocket = null
                            isIdentified = false
                        }
                    }
                }
            }
        )
    }

    private fun handleGatewayMessage(socket: WebSocket, token: String, rawText: String) {
        runCatching {
            val json = JSONObject(rawText)
            if (!json.isNull("s")) {
                lastSequence = json.optInt("s")
            }
            when (json.optInt("op", -1)) {
                10 -> {
                    val intervalMs = json.optJSONObject("d")
                        ?.optLong("heartbeat_interval", 41_250L)
                        ?.coerceAtLeast(5_000L)
                        ?: 41_250L
                    startHeartbeat(socket, intervalMs)
                    sendIdentify(socket, token)
                }
                1 -> sendHeartbeat(socket)
                7, 9 -> {
                    synchronized(stateLock) {
                        if (this@DiscordRpcManager.webSocket === socket) {
                            isIdentified = false
                            heartbeatJob?.cancel()
                            socket.close(4000, "Session reset")
                            this@DiscordRpcManager.webSocket = null
                        }
                    }
                }
            }
        }
    }

    private fun startHeartbeat(socket: WebSocket, intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                if (!sendHeartbeat(socket)) break
            }
        }
    }

    private fun sendHeartbeat(socket: WebSocket): Boolean {
        val payload = JSONObject().apply {
            put("op", 1)
            put("d", lastSequence ?: JSONObject.NULL)
        }.toString()
        return runCatching { socket.send(payload) }.getOrDefault(false)
    }

    private fun sendIdentify(socket: WebSocket, token: String) {
        val identify = JSONObject().apply {
            put("op", 2)
            put("d", JSONObject().apply {
                put("token", token)
                put("intents", 0)
                put("properties", JSONObject().apply {
                    put("os", "android")
                    put("browser", "Aura Music")
                    put("device", "Aura")
                })
                put("presence", JSONObject().apply {
                    put("status", "online")
                    put("afk", false)
                    put("activities", JSONArray())
                })
            })
        }.toString()

        synchronized(stateLock) {
            if (this@DiscordRpcManager.webSocket !== socket) return
            if (runCatching { socket.send(identify) }.getOrDefault(false)) {
                isIdentified = true
                pendingPresencePayload?.let { queued ->
                    if (sendFrameLocked(queued)) {
                        lastSentAtMs = System.currentTimeMillis()
                        pendingPresencePayload = null
                    }
                }
            }
        }
    }

    private fun sendFrameLocked(payload: String): Boolean {
        val ws = webSocket ?: return false
        return runCatching { ws.send(payload) }.getOrDefault(false)
    }

    private fun buildPresencePayload(
        track: AuraTrack,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        nowMs: Long
    ): String {
        val activity = JSONObject().apply {
            put("name", "Aura Music")
            put("type", 2) // Listening
            put("details", track.title.ifBlank { "Aura Music" }.take(128))
            put("state", if (isPlaying) track.artist.ifBlank { "Listening" }.take(128) else "Paused • ${track.artist}".take(128))
            if (isPlaying && durationMs > 0L) {
                val safePos = positionMs.coerceIn(0L, durationMs)
                val startMs = (nowMs - safePos).coerceAtLeast(0L)
                val endMs = startMs + durationMs
                put("timestamps", JSONObject().apply {
                    put("start", startMs)
                    put("end", endMs)
                })
            }
        }

        return JSONObject().apply {
            put("op", 3)
            put("d", JSONObject().apply {
                put("since", JSONObject.NULL)
                put("activities", JSONArray().put(activity))
                put("status", "online")
                put("afk", false)
            })
        }.toString()
    }
}
