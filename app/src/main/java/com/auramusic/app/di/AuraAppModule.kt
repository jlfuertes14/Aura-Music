package com.auramusic.app.di

import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.data.db.AuraDatabase
import com.auramusic.app.data.prefs.AuraPreferencesStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val auraAppModule = module {
    single { AuraDatabase.getInstance(androidContext()) }
    single { get<AuraDatabase>().favoriteTrackDao() }
    single { get<AuraDatabase>().downloadTrackDao() }
    single { get<AuraDatabase>().playlistDao() }
    single { get<AuraDatabase>().recentTrackDao() }
    single { get<AuraDatabase>().playbackEventDao() }
    single { get<AuraDatabase>().offlineLyricsDao() }
    single { get<AuraDatabase>().offlinePaletteDao() }
    single { get<AuraDatabase>().savedQueueDao() }
    single { get<AuraDatabase>().lyricsOffsetDao() }
    single { get<AuraDatabase>().searchHistoryDao() }
    single { get<AuraDatabase>().smartDownloadDao() }
    single { AuraPreferencesStore.getInstance(androidContext()) }
    single { AuraStorageRepository(androidContext(), get(), get()) }
}
