package com.auramusic.app.domain.model

import androidx.compose.ui.graphics.Color

enum class NowPlayingStyle(val label: String) {
    SPOTIFY("Spotify Encore"),
    LIQUID_GLASS("Apple Music")
}

enum class PlaybackMode(val label: String) {
    NORMAL("Normal"),
    SHUFFLE("Shuffle All"),
    REPEAT_ALL("Repeat All"),
    REPEAT_ONE("Repeat One")
}

data class AuraPalette(
    val primary: Color = Color(0xFF8B5CF6),
    val secondary: Color = Color(0xFFEC4899),
    val backgroundTop: Color = Color(0xFF1E1436),
    val backgroundBottom: Color = Color(0xFF0A0B0E),
    val surfaceGlass: Color = Color(0x26FFFFFF)
)

data class AuraTrack(
    val id: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "YouTube Music",
    val durationSeconds: Int = 210,
    val artworkUrl: String = "",
    val source: String = "youtube",
    val genre: String = "Pop",
    val bitrateKbps: Int = 160,
    val codec: String = "AAC",
    val loudnessDb: Float = 0f,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val isExplicit: Boolean = false,
    val hasLyricsBadge: Boolean = true,
    /** YouTube InnerTube's MUSIC_VIDEO_TYPE_* value when the response provides it. */
    val musicVideoType: String? = null,
    /** Approximate count from the InnerTube search subtitle, when available. */
    val viewCount: Long? = null,
    /** Whether this track is an episode of a podcast show */
    val isPodcast: Boolean = false
) {
    val highResArtworkUrl: String
        get() = toHighResArtwork(artworkUrl, videoId)
}

val AuraTrack.isPendingResolution: Boolean
    get() = videoId.isBlank() || videoId.startsWith("spotify_pending") || source == "spotify"

/**
 * Normalizes artwork URLs from YouTube Music, Google User Content, and YouTube CDN
 * to fetch reliable high-definition images with zero 404 black screens.
 */
fun toHighResArtwork(url: String, videoId: String = ""): String {
    val cleanUrl = if (url.startsWith("//")) "https:$url" else url.trim()
    if (cleanUrl.isBlank() && videoId.isNotBlank() && !videoId.startsWith("spotify_pending")) {
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }
    if (cleanUrl.isBlank()) return ""

    return when {
        // Google User Content / Ggpht (YouTube Music standard square CDN)
        cleanUrl.contains("googleusercontent.com") || cleanUrl.contains("ggpht.com") -> {
            // Strip any low-res signed query parameters (?sqp=...)
            val clean = cleanUrl.substringBefore("?")
            if (clean.contains("=")) {
                // Replaces =s60, =s120, =s540, =w60-h60, =w120-h120 with official YTM 544x544 square album art
                clean.replace(Regex("=.*$"), "=w544-h544-l90-rj")
            } else {
                "$clean=w544-h544-l90-rj"
            }
        }
        // Spotify CDN (already native crisp 640x640)
        cleanUrl.contains("i.scdn.co") -> cleanUrl
        // YouTube video thumbnails (img.youtube.com/vi/ or i.ytimg.com/vi/)
        (cleanUrl.contains("youtube.com") || cleanUrl.contains("ytimg.com")) && cleanUrl.contains("/vi/") -> {
            // Keep hqdefault.jpg as it is guaranteed to exist on YouTube servers for 100% of videos
            // (hq720.jpg causes 404 and black screen for videos that were not uploaded in HD)
            cleanUrl.substringBefore("?")
        }
        // YouTube studio square playlist/podcast thumbnails (pl_c) or channel avatars require their signed tokens
        cleanUrl.contains("youtube.com") || cleanUrl.contains("ytimg.com") -> cleanUrl
        else -> cleanUrl
    }
}

data class LyricWord(
    val word: String,
    val startTimeSec: Float,
    val endTimeSec: Float
)

data class LyricLine(
    val timeSec: Float,
    val endTimeSec: Float,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val romanizedText: String? = null
)

data class LyricsData(
    val videoId: String,
    val plainLyrics: String = "",
    val syncedLines: List<LyricLine> = emptyList(),
    val source: String = "SimpMusic RichSync",
    val hasRomanization: Boolean = false
)

data class SponsorSegment(
    val uuid: String,
    val startTimeSec: Float,
    val endTimeSec: Float,
    val category: String
) {
    val displayLabel: String
        get() = when (category) {
            "sponsor" -> "Sponsor"
            "selfpromo" -> "Self-Promotion"
            "interaction" -> "Interaction Reminder"
            "intro" -> "Intermission / Intro"
            "outro" -> "Endcards / Credits"
            "preview" -> "Recap / Preview"
            "music_offtopic" -> "Non-Music Section"
            else -> category.replaceFirstChar { it.uppercase() }
        }
}

data class ReturnYouTubeDislikeData(
    val likes: Long = 0L,
    val dislikes: Long = 0L,
    val viewCount: Long = 0L,
    val rating: Float = 5.0f
)

data class EqualizerPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val bandsDb: List<Float>, // 10 bands: 31Hz..16kHz (-12f..+12f)
    val preampGain: Float = 1.0f
) {
    val preampDb: Float
        get() {
            val loudest = bandsDb.maxOrNull() ?: 0f
            return if (loudest > 0f) -loudest else 0f
        }
}

val EQUALIZER_BAND_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

val SIMPMUSIC_ALL_EQ_PRESETS: List<EqualizerPreset> = listOf(
    EqualizerPreset("flat", "Flat", "Reference neutral curve", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqualizerPreset("acoustic", "Acoustic", "Warm acoustic tone", listOf(4f, 4f, 3f, 1f, 1.5f, 1.5f, 3f, 3.5f, 3f, 1.5f)),
    EqualizerPreset("bass_booster", "Bass booster", "Deep low-end impact", listOf(6f, 5.5f, 4.5f, 2.5f, 0.5f, 0f, 0f, 0f, 0f, 0f)),
    EqualizerPreset("bass_reducer", "Bass reducer", "Clean attenuated lows", listOf(-6f, -5.5f, -4.5f, -2.5f, -0.5f, 0f, 0f, 0f, 0f, 0f)),
    EqualizerPreset("classical", "Classical", "Orchestral clarity", listOf(4.5f, 3.5f, 3f, 2.5f, -1.5f, -1.5f, 0f, 2f, 3f, 3.5f)),
    EqualizerPreset("dance", "Dance", "Club kick and airy top", listOf(5f, 6f, 3.5f, 0f, 2f, 3f, 4f, 4f, 3f, 0f)),
    EqualizerPreset("deep", "Deep", "Atmospheric sub presence", listOf(6f, 5f, 3f, 1.5f, 3f, 2f, 0.5f, -2.5f, -4f, -5f)),
    EqualizerPreset("electronic", "Electronic", "Punchy synth brilliance", listOf(5f, 4.5f, 1.5f, 0f, -1.5f, 2f, 1f, 1.5f, 4.5f, 5f)),
    EqualizerPreset("hip_hop", "HipHop", "Heavy 808s and crisp snares", listOf(6f, 5f, 1.5f, 3f, -1f, -1f, 1.5f, -1f, 2f, 3f)),
    EqualizerPreset("jazz", "Jazz", "Upright bass and brass tone", listOf(4f, 3f, 1.5f, 2f, -1.5f, -1.5f, 0f, 1.5f, 3f, 4f)),
    EqualizerPreset("latin", "Latin", "Percussive presence", listOf(5f, 3f, 0f, 0f, -1.5f, -1.5f, -1.5f, 0f, 3f, 5f)),
    EqualizerPreset("loudness", "Loudness", "Low/high frequency boost", listOf(6f, 5f, 0f, 0f, -2f, 0f, -1f, -5f, 5f, 1f)),
    EqualizerPreset("lounge", "Lounge", "Relaxed chilled vibes", listOf(-3f, -1.5f, -0.5f, 1.5f, 4f, 2.5f, 0f, -1.5f, 2f, 1f)),
    EqualizerPreset("piano", "Piano", "Keys resonance and warmth", listOf(3f, 2f, 0f, 2.5f, 3f, 1f, 2f, 4f, 3f, 3f)),
    EqualizerPreset("pop", "Pop", "Crisp vocal and snappy beat", listOf(-1.5f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -1.5f)),
    EqualizerPreset("rnb", "RnB", "Warm sub bass and silky sheen", listOf(5.5f, 6f, 4.5f, 1f, -2.5f, -1f, 2.5f, 3f, 3.5f, 4f)),
    EqualizerPreset("rock", "Rock", "Biting guitars and tight drums", listOf(5f, 4f, 3f, 1.5f, -0.5f, -1f, 0.5f, 3f, 4f, 4.5f)),
    EqualizerPreset("small_speakers", "Small speakers", "Earbuds and portable speaker boost", listOf(6f, 5f, 3.5f, 2f, 0f, -0.5f, 0f, 1.5f, 2.5f, 2f)),
    EqualizerPreset("spoken_word", "Spoken word", "Vocal clarity and podcasts", listOf(-4f, -3.5f, -2f, 0f, 3f, 4.5f, 5f, 4f, 2f, -1f)),
    EqualizerPreset("treble_booster", "Treble booster", "Airy harmonic brilliance", listOf(0f, 0f, 0f, 0f, 0f, 0f, 2.5f, 4.5f, 5.5f, 6f)),
    EqualizerPreset("treble_reducer", "Treble reducer", "Gentle non-fatiguing high cut", listOf(0f, 0f, 0f, 0f, 0f, 0f, -2.5f, -4.5f, -5.5f, -6f)),
    EqualizerPreset("vocal_booster", "Vocal booster", "Lead vocal articulation", listOf(-2.5f, -3f, -2.5f, 1.5f, 4f, 4f, 3.5f, 2f, 0f, -1.5f))
)

fun findMatchingPreset(bands: List<Float>): EqualizerPreset? {
    return SIMPMUSIC_ALL_EQ_PRESETS.firstOrNull { preset ->
        preset.bandsDb.indices.all { i -> kotlin.math.abs(preset.bandsDb[i] - bands.getOrElse(i) { 0f }) < 0.05f }
    }
}

val SIMPMUSIC_EQ_PRESETS = SIMPMUSIC_ALL_EQ_PRESETS

data class MoodCategory(
    val title: String,
    val query: String,
    val accentHex: Long
)

object YtmMoodBrowseParams {
    const val ENERGIZE = "ggM8SgQIBxABSgQIBRABSgQICRADSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
    const val RELAX = "ggM8SgQIBxADSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
    const val FEEL_GOOD = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBADSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
    const val PARTY = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhADSgQIAxABSgQIBhAB"
    const val COMMUTE = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxADSgQIBhAB"
    const val WORKOUT = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBADSgQIDhABSgQIAxABSgQIBhAB"
    const val ROMANCE = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRADSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
    const val SAD = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChADSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
    const val FOCUS = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAD"
    const val SLEEP = "ggM8SgQIBxABSgQIBRADSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"

    val ALL_MOODS = listOf(
        "Energize",
        "Relax",
        "Feel good",
        "Party",
        "Commute",
        "Workout",
        "Romance",
        "Sad",
        "Focus",
        "Sleep"
    )

    val PARAMS_MAP = mapOf(
        "Energize" to ENERGIZE,
        "Relax" to RELAX,
        "Feel good" to FEEL_GOOD,
        "Party" to PARTY,
        "Commute" to COMMUTE,
        "Workout" to WORKOUT,
        "Romance" to ROMANCE,
        "Sad" to SAD,
        "Focus" to FOCUS,
        "Sleep" to SLEEP
    )

    val DEFAULT_BACKGROUNDS = mapOf(
        "Energize" to "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1400&auto=format&fit=crop&q=80",
        "Relax" to "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1400&auto=format&fit=crop&q=80",
        "Feel good" to "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1400&auto=format&fit=crop&q=80",
        "Party" to "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1400&auto=format&fit=crop&q=80",
        "Commute" to "https://images.unsplash.com/photo-1519501025264-65ba15a82390?w=1400&auto=format&fit=crop&q=80",
        "Workout" to "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=1400&auto=format&fit=crop&q=80",
        "Romance" to "https://images.unsplash.com/photo-1507400492013-162706c8c05e?w=1400&auto=format&fit=crop&q=80",
        "Sad" to "https://images.unsplash.com/photo-1515694346937-94d85e41e6f0?w=1400&auto=format&fit=crop&q=80",
        "Focus" to "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1400&auto=format&fit=crop&q=80",
        "Sleep" to "https://images.unsplash.com/photo-1505118380757-91f5f5632de0?w=1400&auto=format&fit=crop&q=80"
    )
}

enum class YtmMoodItemType {
    TRACK,
    PLAYLIST,
    ALBUM,
    VIDEO
}

data class YtmMoodItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String = "",
    val type: YtmMoodItemType = YtmMoodItemType.TRACK,
    val track: AuraTrack? = null,
    val playlist: YtmCommunityPlaylist? = null
)

data class YtmMoodShelf(
    val title: String,
    val subtitle: String? = null,
    val is4RowGrid: Boolean = false,
    val items: List<YtmMoodItem> = emptyList()
)

data class MoodBrowseResult(
    val backgroundUrl: String?,
    val tracks: List<AuraTrack>,
    val albums: List<AuraTrack> = emptyList(),
    val shelves: List<YtmMoodShelf> = emptyList()
)

data class YtmChartSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<AuraTrack>
)

data class AuraPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val coverUrl: String,
    val tracks: List<AuraTrack>,
    val isPodcast: Boolean = false
)

data class ListeningStats(
    val totalMinutes: Int = 0,
    val tracksPlayed: Int = 0,
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val topGenres: List<Pair<String, Int>> = emptyList(),
    val topTracks: List<Pair<AuraTrack, Int>> = emptyList(),
    val peakHourLabel: String = "10:00 PM"
)

data class YtmTopArtist(
    val id: String,
    val name: String,
    val subscribers: String,
    val avatarUrl: String
) {
    val highResAvatarUrl: String
        get() = toHighResArtwork(avatarUrl)
}

data class YtmCommunityPlaylist(
    val id: String,
    val title: String,
    val author: String,
    val trackCount: String = "",
    val artworkUrl: String = ""
) {
    val highResArtworkUrl: String
        get() = toHighResArtwork(artworkUrl)
}

data class ArtistSong(
    val rank: Int,
    val track: AuraTrack,
    val playCount: String = ""
)

data class ArtistAlbum(
    val id: String,
    val title: String,
    val year: String = "",
    val artworkUrl: String = "",
    val type: String = "Album"
) {
    val highResArtworkUrl: String
        get() = toHighResArtwork(artworkUrl)
}

data class ArtistCarouselItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String = "",
    val endpointType: String = "album", // "album", "video", "playlist", "artist"
    val videoId: String = "",
    val browseId: String = ""
) {
    val highResArtworkUrl: String
        get() = toHighResArtwork(artworkUrl)
}

data class ArtistCarousel(
    val title: String,
    val items: List<ArtistCarouselItem>
)

data class ArtistDetail(
    val id: String,
    val name: String,
    val subscribers: String,
    val bannerUrl: String,
    val topSongs: List<ArtistSong>,
    val albums: List<ArtistAlbum> = emptyList(),
    val carousels: List<ArtistCarousel> = emptyList(),
    val description: String = "",
    val motion: ArtistMotion? = null,
    val motionLoading: Boolean = false
)

/** YouTube video owner and engagement details used below the now-playing lyrics. */
data class AuraVideoInfo(
    val videoId: String,
    val author: String = "",
    val authorId: String? = null,
    val authorThumbnail: String? = null,
    val description: String = "",
    val subscribers: String = "",
    val uploadDate: String = "",
    val viewCount: Long = 0L,
    val likes: Long = 0L,
    val dislikes: Long = 0L
)

data class ArtistMotion(
    val squareVideoUrl: String? = null,
    val squarePreviewUrl: String? = null,
    val wideVideoUrl: String? = null,
    val widePreviewUrl: String? = null
)

data class AuraYouTubeComment(
    val id: String,
    val author: String,
    val avatarUrl: String?,
    val text: String,
    val publishedTime: String,
    val likeCount: String
)

data class AuraCommentsPage(
    val countText: String?,
    val comments: List<AuraYouTubeComment>,
    val continuation: String? = null,
    val commentsDisabled: Boolean = false
)

data class PlaylistTrackItem(
    val rank: Int,
    val track: AuraTrack,
    val viewCount: String = "",
    val description: String = ""
)

data class PlaylistDetail(
    val id: String,
    val title: String,
    val author: String,
    val trackCount: String,
    val bannerUrl: String,
    val tracks: List<PlaylistTrackItem>,
    val totalViews: String = "",
    val description: String = "",
    val authorAvatarUrl: String = "",
    val isPodcast: Boolean = false
)

data class ResolvedVideoStream(
    val videoId: String,
    val streamUrl: String,
    val quality: String = "720p",
    val durationSeconds: Int = 0,
    val isLive: Boolean = false,
    val audioUrl: String? = null
)

object ChartSupportedCountries {
    val items = arrayOf(
        "US", "ZZ", "AR", "AU", "AT", "BE", "BO", "BR", "CA", "CL", "CO", "CR", "CZ", "DK", "DO", "EC",
        "EG", "SV", "EE", "FI", "FR", "DE", "GT", "HN", "HK", "HU", "IS", "IN", "ID", "IE", "IL", "IT",
        "JP", "KE", "LU", "MY", "MX", "NL", "NZ", "NI", "NG", "NO", "PA", "PY", "PE", "PH", "PL", "PT",
        "RO", "RU", "SA", "RS", "SG", "ZA", "KR", "ES", "SE", "CH", "TW", "TZ", "TH", "TR", "UG", "UA",
        "AE", "GB", "UY", "VN", "ZW"
    )
    val itemsData = arrayOf(
        "United States", "Global", "Argentina", "Australia", "Austria", "Belgium", "Bolivia", "Brazil", "Canada",
        "Chile", "Colombia", "Costa Rica", "Czech Republic", "Denmark", "Dominican Republic", "Ecuador", "Egypt",
        "El Salvador", "Estonia", "Finland", "France", "Germany", "Guatemala", "Honduras", "Hong Kong", "Hungary",
        "Iceland", "India", "Indonesia", "Ireland", "Israel", "Italy", "Japan", "Kenya", "Luxembourg", "Malaysia",
        "Mexico", "Netherlands", "New Zealand", "Nicaragua", "Nigeria", "Norway", "Panama", "Paraguay", "Peru",
        "Philippines", "Poland", "Portugal", "Romania", "Russia", "Saudi Arabia", "Serbia", "Singapore", "South Africa",
        "South Korea", "Spain", "Sweden", "Switzerland", "Taiwan", "Tanzania", "Thailand", "Turkey", "Uganda",
        "Ukraine", "United Arab Emirates", "United Kingdom", "Uruguay", "Vietnam", "Zimbabwe"
    )

    fun getCountryName(code: String): String {
        val idx = items.indexOf(code)
        return if (idx >= 0) itemsData[idx] else "United States"
    }

    fun getCountryCode(name: String): String {
        val idx = itemsData.indexOf(name)
        return if (idx >= 0) items[idx] else "US"
    }
}

data class AuraChart(
    val countryCode: String,
    val playlists: List<AuraChartPlaylistSection>,
    val topArtists: List<AuraChartArtistItem>,
    val podcasts: AuraChartPodcastSection? = null
)

data class AuraChartPlaylistSection(
    val title: String,
    val items: List<YtmCommunityPlaylist>
)

data class AuraChartArtistItem(
    val id: String,
    val rank: String,
    val name: String,
    val subscribers: String,
    val avatarUrl: String
) {
    val highResAvatarUrl: String
        get() = toHighResArtwork(avatarUrl)
}

data class AuraChartPodcastSection(
    val title: String,
    val shows: List<AuraChartArtistItem>
)



