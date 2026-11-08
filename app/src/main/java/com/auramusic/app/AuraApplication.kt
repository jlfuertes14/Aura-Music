package com.auramusic.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.core.media.AuraPlayerManager

class AuraApplication : Application() {
    companion object {
        const val CHANNEL_ID = "aura_music_playback"
    }

    override fun onCreate() {
        super.onCreate()
        // Enforce dark/night mode globally across all framework windows and dialogs
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        createNotificationChannel()
        com.auramusic.app.core.notifications.AuraNotificationManager.createChannels(this)
        SimpMusicExtractorEngine.initExtractors()
        val storage = com.auramusic.app.data.AuraStorageRepository(this)
        SimpMusicExtractorEngine.setStorageRepository(storage)
        AuraPlayerManager.initialize(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aura Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows now playing music notification with controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
