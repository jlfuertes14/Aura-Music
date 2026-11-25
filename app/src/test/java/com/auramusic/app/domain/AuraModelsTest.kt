package com.auramusic.app.domain

import com.auramusic.app.domain.model.ArtistAlbum
import com.auramusic.app.domain.model.ArtistCarouselItem
import com.auramusic.app.domain.model.AuraChartArtistItem
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.ChartSupportedCountries
import com.auramusic.app.domain.model.EqualizerPreset
import com.auramusic.app.domain.model.SIMPMUSIC_ALL_EQ_PRESETS
import com.auramusic.app.domain.model.SponsorSegment
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.domain.model.YtmTopArtist
import com.auramusic.app.domain.model.findMatchingPreset
import com.auramusic.app.domain.model.isPendingResolution
import com.auramusic.app.domain.model.toHighResArtwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraModelsTest {

    @Test
    fun toHighResArtwork_upgradesGoogleUserContentS60() {
        val input = "https://lh3.googleusercontent.com/abc123=s60"
        assertEquals(
            "https://lh3.googleusercontent.com/abc123=w544-h544-l90-rj",
            toHighResArtwork(input)
        )
    }

    @Test
    fun toHighResArtwork_upgradesGoogleUserContentW120H120AndStripsQuery() {
        val input = "https://lh3.googleusercontent.com/xyz789=w120-h120-l90-rj?sqp=signed_token"
        assertEquals(
            "https://lh3.googleusercontent.com/xyz789=w544-h544-l90-rj",
            toHighResArtwork(input)
        )
    }

    @Test
    fun toHighResArtwork_appendsHighResParamsToGgphtWithoutEquals() {
        val input = "https://yt3.ggpht.com/channel_avatar?sqp=lowres"
        assertEquals(
            "https://yt3.ggpht.com/channel_avatar=w544-h544-l90-rj",
            toHighResArtwork(input)
        )
    }

    @Test
    fun toHighResArtwork_preservesSpotifyCdnUrl() {
        val spotifyUrl = "https://i.scdn.co/image/ab67616d0000b273a1b2c3d4e5f6"
        assertEquals(spotifyUrl, toHighResArtwork(spotifyUrl))
    }

    @Test
    fun toHighResArtwork_stripsQueryFromYouTubeViThumbnails() {
        val ytimg = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=-oaymwEcCNACELwBSFXyq4qpAw4IARUAAIhCGAFwAcABBg=="
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", toHighResArtwork(ytimg))

        val imgYoutube = "https://img.youtube.com/vi/abc123/hqdefault.jpg?foo=bar"
        assertEquals("https://img.youtube.com/vi/abc123/hqdefault.jpg", toHighResArtwork(imgYoutube))
    }

    @Test
    fun toHighResArtwork_preservesSignedNonViYouTubeUrl() {
        val signedPlaylistCover = "https://i.ytimg.com/pl_c/PL12345/studio_square_thumbnail.jpg?sqp=CIS43bsG"
        assertEquals(signedPlaylistCover, toHighResArtwork(signedPlaylistCover))
    }

    @Test
    fun toHighResArtwork_blankUrlFallsBackToHqDefaultWhenVideoIdValid() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            toHighResArtwork("", "dQw4w9WgXcQ")
        )
        assertEquals(
            "https://i.ytimg.com/vi/xyz987/hqdefault.jpg",
            toHighResArtwork("   ", "xyz987")
        )
    }

    @Test
    fun toHighResArtwork_blankUrlReturnsEmptyWhenVideoIdBlankOrSpotifyPending() {
        assertEquals("", toHighResArtwork("", ""))
        assertEquals("", toHighResArtwork("  ", "spotify_pending_123"))
    }

    @Test
    fun toHighResArtwork_normalizesProtocolRelativePrefix() {
        assertEquals(
            "https://lh3.googleusercontent.com/cover=w544-h544-l90-rj",
            toHighResArtwork("//lh3.googleusercontent.com/cover=s60")
        )
        assertEquals(
            "https://i.scdn.co/image/12345",
            toHighResArtwork("//i.scdn.co/image/12345")
        )
    }

    @Test
    fun auraTrack_isPendingResolutionAndHighResArtworkUrl() {
        val pendingBlank = AuraTrack(id = "1", videoId = "", title = "A", artist = "B")
        val pendingWhitespace = AuraTrack(id = "2", videoId = "   ", title = "A", artist = "B")
        val pendingSpotify = AuraTrack(id = "3", videoId = "spotify_pending_99", title = "A", artist = "B")
        val resolved = AuraTrack(
            id = "4",
            videoId = "vid42",
            title = "Song",
            artist = "Artist",
            artworkUrl = "https://lh3.googleusercontent.com/art=w120-h120"
        )

        assertTrue(pendingBlank.isPendingResolution)
        assertTrue(pendingWhitespace.isPendingResolution)
        assertTrue(pendingSpotify.isPendingResolution)
        assertFalse(resolved.isPendingResolution)
        assertEquals("https://lh3.googleusercontent.com/art=w544-h544-l90-rj", resolved.highResArtworkUrl)
    }

    @Test
    fun modelArtworkHelpers_delegateToHighResArtwork() {
        val topArtist = YtmTopArtist("a1", "Artist", "1M", "https://lh3.googleusercontent.com/a=s60")
        val playlist = YtmCommunityPlaylist("p1", "Mix", "Author", "20", "//lh3.googleusercontent.com/p=s120")
        val album = ArtistAlbum("al1", "Album", "2024", "https://i.scdn.co/image/album")
        val carousel = ArtistCarouselItem("c1", "Item", artworkUrl = "https://i.ytimg.com/vi/vid1/hqdefault.jpg?sqp=x")
        val chartArtist = AuraChartArtistItem("ca1", "1", "Star", "2M", "https://yt3.ggpht.com/star=s88")

        assertEquals("https://lh3.googleusercontent.com/a=w544-h544-l90-rj", topArtist.highResAvatarUrl)
        assertEquals("https://lh3.googleusercontent.com/p=w544-h544-l90-rj", playlist.highResArtworkUrl)
        assertEquals("https://i.scdn.co/image/album", album.highResArtworkUrl)
        assertEquals("https://i.ytimg.com/vi/vid1/hqdefault.jpg", carousel.highResArtworkUrl)
        assertEquals("https://yt3.ggpht.com/star=w544-h544-l90-rj", chartArtist.highResAvatarUrl)
    }

    @Test
    fun equalizerPreset_preampDbNegatesMaxPositiveGainOrZero() {
        val positivePeak = EqualizerPreset(
            id = "boost",
            name = "Boost",
            subtitle = "",
            bandsDb = listOf(-2f, 0f, 3.5f, 6f, 1f, 0f, 0f, 0f, 0f, 0f)
        )
        val allZero = EqualizerPreset(
            id = "flat",
            name = "Flat",
            subtitle = "",
            bandsDb = List(10) { 0f }
        )
        val allNegative = EqualizerPreset(
            id = "cut",
            name = "Cut",
            subtitle = "",
            bandsDb = listOf(-6f, -5f, -4f, -2f, -1f, 0f, 0f, 0f, 0f, 0f)
        )
        val empty = EqualizerPreset(
            id = "empty",
            name = "Empty",
            subtitle = "",
            bandsDb = emptyList()
        )

        assertEquals(-6f, positivePeak.preampDb, 0.001f)
        assertEquals(0f, allZero.preampDb, 0.001f)
        assertEquals(0f, allNegative.preampDb, 0.001f)
        assertEquals(0f, empty.preampDb, 0.001f)
    }

    @Test
    fun findMatchingPreset_matchesExactWithinToleranceAndRejectsMismatch() {
        SIMPMUSIC_ALL_EQ_PRESETS.forEach { preset ->
            assertEquals(10, preset.bandsDb.size)
            val matched = findMatchingPreset(preset.bandsDb)
            assertNotNull("Expected preset ${preset.id} to match itself", matched)
            assertEquals(preset.id, matched?.id)
        }

        val jazz = SIMPMUSIC_ALL_EQ_PRESETS.first { it.id == "jazz" }
        val nearJazz = jazz.bandsDb.toMutableList().apply { this[0] = this[0] + 0.04f }
        assertEquals("jazz", findMatchingPreset(nearJazz)?.id)

        val outsideTolerance = jazz.bandsDb.toMutableList().apply { this[0] = this[0] + 0.06f }
        assertNull(findMatchingPreset(outsideTolerance))

        assertEquals("flat", findMatchingPreset(emptyList())?.id)
    }

    @Test
    fun sponsorSegment_displayLabelMapsAllCategories() {
        assertEquals("Sponsor", SponsorSegment("1", 0f, 5f, "sponsor").displayLabel)
        assertEquals("Self-Promotion", SponsorSegment("2", 0f, 5f, "selfpromo").displayLabel)
        assertEquals("Interaction Reminder", SponsorSegment("3", 0f, 5f, "interaction").displayLabel)
        assertEquals("Intermission / Intro", SponsorSegment("4", 0f, 5f, "intro").displayLabel)
        assertEquals("Endcards / Credits", SponsorSegment("5", 0f, 5f, "outro").displayLabel)
        assertEquals("Recap / Preview", SponsorSegment("6", 0f, 5f, "preview").displayLabel)
        assertEquals("Non-Music Section", SponsorSegment("7", 0f, 5f, "music_offtopic").displayLabel)
        assertEquals("Filler", SponsorSegment("8", 0f, 5f, "filler").displayLabel)
    }

    @Test
    fun chartSupportedCountries_mapsCodesAndNamesWithUsFallback() {
        assertEquals(ChartSupportedCountries.items.size, ChartSupportedCountries.itemsData.size)
        assertEquals("Global", ChartSupportedCountries.getCountryName("ZZ"))
        assertEquals("Japan", ChartSupportedCountries.getCountryName("JP"))
        assertEquals("United States", ChartSupportedCountries.getCountryName("UNKNOWN"))

        assertEquals("ZZ", ChartSupportedCountries.getCountryCode("Global"))
        assertEquals("KR", ChartSupportedCountries.getCountryCode("South Korea"))
        assertEquals("US", ChartSupportedCountries.getCountryCode("Atlantis"))
    }
}
