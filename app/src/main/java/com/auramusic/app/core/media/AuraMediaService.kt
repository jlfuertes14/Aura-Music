package com.auramusic.app.core.media

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.auramusic.app.AuraApplication
import com.auramusic.app.MainActivity
import com.auramusic.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@OptIn(UnstableApi::class)
class AuraMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var artworkJob: Job? = null
    private var currentArtwork: Bitmap? = null
    private var lastArtworkUrl: String? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.auramusic.app.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.auramusic.app.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.auramusic.app.ACTION_NEXT"
        const val ACTION_UPDATE = "com.auramusic.app.ACTION_UPDATE"
    }

    override fun onCreate() {
        super.onCreate()

        AuraPlayerManager.initialize(applicationContext)
        val player = AuraPlayerManager.exoPlayer ?: return

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val session = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val availableCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(availableCommands)
                        .build()
                }
            })
            .build()

        mediaSession = session
        addSession(session)

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                postNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                postNotification()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                AuraPlayerManager.currentTrack.value?.let { track ->
                    updateArtwork(track.highResArtworkUrl)
                }
                postNotification()
            }
        }
        player.addListener(playerListener!!)

        AuraPlayerManager.currentTrack.value?.let { track ->
            updateArtwork(track.highResArtworkUrl)
        }
        postNotification(startInForegroundRequired = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> AuraPlayerManager.togglePlayPause()
            ACTION_PREVIOUS -> AuraPlayerManager.skipToPrevious()
            ACTION_NEXT -> AuraPlayerManager.skipToNext()
            ACTION_UPDATE -> {
                AuraPlayerManager.currentTrack.value?.let { track ->
                    updateArtwork(track.highResArtworkUrl)
                }
            }
        }
        super.onStartCommand(intent, flags, startId)
        postNotification()
        return START_NOT_STICKY
    }

    private var isForegroundService = false

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        postNotification(startInForegroundRequired = startInForegroundRequired)
    }

    private fun postNotification(startInForegroundRequired: Boolean = false) {
        val notification = buildNotification() ?: return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isPlaying = AuraPlayerManager.isPlaying.value
        val hasTrack = AuraPlayerManager.currentTrack.value != null

        val shouldBeForeground = startInForegroundRequired || isPlaying || hasTrack

        try {
            if (shouldBeForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundService = true
            } else {
                if (isForegroundService) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    isForegroundService = false
                }
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun buildNotification(): Notification? {
        val session = mediaSession ?: return null
        val currentTrack = AuraPlayerManager.currentTrack.value
        val isPlaying = AuraPlayerManager.isPlaying.value

        val title = currentTrack?.title?.ifBlank { "Aura" } ?: "Aura"
        val artist = currentTrack?.artist?.ifBlank { "Playing" } ?: "Playing"
        val album = currentTrack?.album ?: ""

        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(session)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(this, AuraApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.aura_app_icon_round)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setLargeIcon(currentArtwork)
            .setContentIntent(contentPendingIntent)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(isPlaying)
            .setShowWhen(false)

        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "Previous",
                buildActionPendingIntent(ACTION_PREVIOUS)
            )
        )

        builder.addAction(
            NotificationCompat.Action(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                buildActionPendingIntent(ACTION_PLAY_PAUSE)
            )
        )

        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "Next",
                buildActionPendingIntent(ACTION_NEXT)
            )
        )

        return builder.build()
    }

    private fun buildActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, AuraMediaService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateArtwork(artworkUrl: String?) {
        if (artworkUrl.isNullOrBlank() || artworkUrl == lastArtworkUrl) return
        lastArtworkUrl = artworkUrl
        artworkJob?.cancel()
        artworkJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val url = URL(artworkUrl)
                val connection = url.openConnection()
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                val bitmap = connection.getInputStream().use {
                    BitmapFactory.decodeStream(it)
                }
                if (isActive && bitmap != null) {
                    withContext(Dispatchers.Main) {
                        currentArtwork = bitmap
                        postNotification()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (AuraPlayerManager.killServiceOnExit.value) {
            player?.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        artworkJob?.cancel()
        val player = mediaSession?.player
        playerListener?.let { player?.removeListener(it) }
        mediaSession?.run {
            removeSession(this)
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
