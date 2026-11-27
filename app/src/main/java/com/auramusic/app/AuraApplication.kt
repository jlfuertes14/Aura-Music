package com.auramusic.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.stringPreferencesKey
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.data.prefs.AuraPreferencesStore
import com.auramusic.app.di.auraAppModule
import com.auramusic.app.domain.model.AppThemeMode
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class AuraApplication : Application() {
    companion object {
        const val CHANNEL_ID = "aura_music_playback"

        /**
         * Maps Aura's [AppThemeMode] to [AppCompatDelegate]'s global night mode so framework
         * windows, dialogs, and system resources follow System / Dark / Light mode consistently.
         */
        fun applyThemeMode(mode: AppThemeMode) {
            val nightMode = when (mode) {
                AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@AuraApplication)
                modules(auraAppModule)
            }
        }
        createNotificationChannel()
        com.auramusic.app.core.notifications.AuraNotificationManager.createChannels(this)
        SimpMusicExtractorEngine.initExtractors()
        val storage: AuraStorageRepository = GlobalContext.get().get()
        applyThemeMode(resolvePersistedThemeMode(storage))
        SimpMusicExtractorEngine.setStorageRepository(storage)
        AuraPlayerManager.initialize(this)
    }

    private fun resolvePersistedThemeMode(storage: AuraStorageRepository): AppThemeMode {
        val fromRepo = runCatching {
            storage.javaClass.getMethod("loadThemeMode").invoke(storage) as? AppThemeMode
        }.getOrNull()
        if (fromRepo != null) return fromRepo

        val raw = runCatching {
            AuraPreferencesStore.getInstance(this)
                .getString(stringPreferencesKey("setting_app_theme_mode"), null)
        }.getOrNull()
        return AppThemeMode.entries.firstOrNull { it.name == raw } ?: AppThemeMode.DARK
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
