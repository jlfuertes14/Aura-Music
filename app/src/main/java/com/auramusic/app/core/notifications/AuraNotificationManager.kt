package com.auramusic.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.auramusic.app.MainActivity
import com.auramusic.app.R
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AuraNotificationManager {
    private const val TAG = "AuraNotificationMgr"

    const val CHANNEL_UPDATES_ID = "aura_music_updates"
    const val CHANNEL_UPDATES_NAME = "Aura App Updates & Releases"
    const val NOTIFICATION_UPDATE_ID = 2002

    const val EXTRA_OPEN_UPDATE = "extra_open_update"
    const val TOPIC_RELEASES = "releases"

    private const val PREFS_NAME = "aura_push_preferences"
    private const val KEY_FCM_TOKEN = "fcm_push_token"
    private const val KEY_DEVICE_ID = "device_installation_id"
    private const val KEY_IS_SUBSCRIBED = "is_subscribed_to_topic"

    // Optional: Set your Supabase or Render subscription pool URL here or via environment
    // E.g. "https://your-project.supabase.co/rest/v1/aura_release_subscriptions"
    // Or Render: "https://your-render-service.onrender.com/api/releases/subscribe"
    var customSubscriptionPoolUrl: String? = null
    var supabaseAnonKey: String? = null

    private val registrationMutex = Mutex()
    private var lastSyncedTimeMs = 0L
    private var lastSyncedToken: String? = null

    init {
        val sbUrl = com.auramusic.app.BuildConfig.SUPABASE_PUBLIC_URL.trim().trimEnd('/')
        val sbKey = com.auramusic.app.BuildConfig.SUPABASE_ANON_KEY.trim()
        if (sbUrl.isNotBlank() && sbKey.isNotBlank()) {
            customSubscriptionPoolUrl = "$sbUrl/rest/v1/aura_release_subscriptions?on_conflict=device_id"
            supabaseAnonKey = sbKey
            Log.i(TAG, "Configured Supabase subscription pool: $sbUrl")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (_: Exception) { null }

            deviceId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                androidId
            } else {
                java.util.UUID.randomUUID().toString()
            }
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // App Updates & Announcements Channel
            val updateChannel = NotificationChannel(
                CHANNEL_UPDATES_ID,
                CHANNEL_UPDATES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when new updates or features are released"
                enableLights(true)
                lightColor = 0xFFD4F644.toInt() // Aura Neon Lime
                enableVibration(true)
                setShowBadge(true)
            }

            manager.createNotificationChannel(updateChannel)
        }
    }

    /**
     * Initializes FCM registration once notifications are enabled.
     * Subscribes the device to the 'releases' topic for serverless broadcasts,
     * and optionally posts the token to a backend subscription pool.
     */
    fun setupFcmSubscription(context: Context, backendPoolUrl: String? = null) {
        createChannels(context)

        val poolUrl = backendPoolUrl ?: customSubscriptionPoolUrl

        // Immediately sync cached FCM token to Supabase pool if already available
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedToken = prefs.getString(KEY_FCM_TOKEN, null)
        if (!cachedToken.isNullOrBlank() && !poolUrl.isNullOrBlank()) {
            scope.launch {
                registerWithSubscriptionPool(context, cachedToken, poolUrl)
            }
        }

        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result ?: return@addOnCompleteListener
                Log.d(TAG, "Current FCM Token acquired: $token")
                onNewFcmToken(context, token, backendPoolUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase messaging not yet initialized or google-services.json not present: ${e.message}")
        }
    }

    fun onNewFcmToken(context: Context, token: String, backendPoolUrl: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

        // 1. Subscribe to Native FCM Topic for Zero-Server broadcasts
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_RELEASES)
                .addOnSuccessListener {
                    prefs.edit().putBoolean(KEY_IS_SUBSCRIBED, true).apply()
                    Log.i(TAG, "Successfully subscribed to FCM topic: $TOPIC_RELEASES")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to subscribe to FCM topic: $TOPIC_RELEASES", e)
                }
        } catch (e: Exception) {
            Log.w(TAG, "FCM Topic subscription error: ${e.message}")
        }

        // 2. Optional: Pool into Render/Supabase subscription database if URL provided
        val poolUrl = backendPoolUrl ?: customSubscriptionPoolUrl
        if (!poolUrl.isNullOrBlank()) {
            scope.launch {
                registerWithSubscriptionPool(context, token, poolUrl)
            }
        }
    }

    /**
     * Registers this device's token in a custom backend or Supabase PostgREST table.
     * Matches the Supabase schema: { device_id, push_token, platform: 'android', channel: 'stable', app_version, enabled, last_seen_at }
     * Protected by Mutex and 60-second debounce to prevent concurrent/duplicate requests.
     */
    private suspend fun registerWithSubscriptionPool(context: Context, token: String, poolUrl: String) {
        if (!registrationMutex.tryLock()) {
            Log.d(TAG, "Registration already in flight, skipping duplicate call.")
            return
        }
        try {
            val now = System.currentTimeMillis()
            if (token == lastSyncedToken && (now - lastSyncedTimeMs) < 60_000L) {
                Log.d(TAG, "Device already synced within last 60 seconds, skipping duplicate.")
                return
            }

            val deviceId = getOrCreateDeviceId(context)
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.5.2"
            } catch (_: Exception) { "1.5.2" }

            val payload = JSONObject().apply {
                put("device_id", deviceId)
                put("push_token", token)
                put("platform", "android")
                put("channel", "stable")
                put("app_version", appVersion)
                put("enabled", true)
                put("last_seen_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date()))
            }

            val requestBuilder = Request.Builder()
                .url(poolUrl)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))

            // If pointing directly to Supabase PostgREST, attach API key and resolution headers
            val key = supabaseAnonKey
            if (!key.isNullOrBlank()) {
                requestBuilder.header("apikey", key)
                requestBuilder.header("Authorization", "Bearer $key")
                requestBuilder.header("Prefer", "resolution=merge-duplicates,return=representation")
            }

            SimpMusicExtractorEngine.httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    lastSyncedTimeMs = now
                    lastSyncedToken = token
                    Log.i(TAG, "Device registered with Supabase subscription pool! HTTP ${response.code} response: $body")
                } else {
                    Log.e(TAG, "Subscription pool returned HTTP ${response.code} error: $body")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register with custom subscription pool: ${e.message}", e)
        } finally {
            registrationMutex.unlock()
        }
    }

    /**
     * Posts a local Android notification for a new app update that, when tapped,
     * opens MainActivity and immediately displays the update modal.
     */
    fun showUpdateNotification(
        context: Context,
        title: String,
        message: String,
        version: String
    ) {
        createChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.auramusic.app.OPEN_UPDATE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE, true)
            putExtra("type", "release")
            putExtra("update_version", version)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_UPDATE_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val circleLogoBitmap = createCircularLogoBitmap(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES_ID)
            .setSmallIcon(R.drawable.ic_aura_notification)
            .apply {
                if (circleLogoBitmap != null) {
                    setLargeIcon(circleLogoBitmap)
                }
            }
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFFD4F644.toInt()) // Aura Neon Lime
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_UPDATE_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted: ${e.message}")
        }
    }

    /**
     * Renders R.drawable.aura_app_icon_round (assets/icon.png) into a crisp circular Bitmap
     * for the right-side notification thumbnail (setLargeIcon) without BigPicture expansion.
     */
    private fun createCircularLogoBitmap(context: Context): Bitmap? {
        return try {
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.aura_app_icon_round)
                ?: BitmapFactory.decodeResource(context.resources, R.drawable.aura_logo)
                ?: return null
            val size = 256
            val scaled = Bitmap.createScaledBitmap(raw, size, size, true)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val radius = size / 2f
            canvas.drawCircle(radius, radius, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            output
        } catch (_: Exception) {
            null
        }
    }
}
