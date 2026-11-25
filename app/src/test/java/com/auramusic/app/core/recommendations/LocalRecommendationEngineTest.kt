package com.auramusic.app.core.recommendations

import com.auramusic.app.domain.model.AuraTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRecommendationEngineTest {

    private fun track(
        videoId: String,
        title: String = "Track $videoId",
        artist: String = "Artist $videoId",
        album: String = "Album $videoId",
        genre: String = "Pop"
    ) = AuraTrack(
        id = "yt_$videoId",
        videoId = videoId,
        title = title,
        artist = artist,
        album = album,
        genre = genre
    )

    @Test
    fun rankTracks_emptyCandidatesReturnsEmptyList() {
        val ranked = LocalRecommendationEngine.rankTracks(
            candidates = emptyList(),
            favorites = listOf(track("fav1")),
            recentlyPlayed = listOf(track("rec1")),
            interactionScores = mapOf("fav1" to 5f)
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun rankTracks_boostsFavoritesAndMatchingTaste() {
        val fav = track("fav", title = "Favorite Song", artist = "Daft Punk", album = "Discovery", genre = "Electronic")
        val sameArtist = track("sameArtist", title = "Another Track", artist = "Daft Punk", album = "Homework", genre = "Electronic")
        val unrelated = track("other", title = "Random Song", artist = "Unrelated Artist", album = "Other", genre = "Classical")

        val ranked = LocalRecommendationEngine.rankTracks(
            candidates = listOf(unrelated, sameArtist, fav),
            favorites = listOf(fav),
            recentlyPlayed = emptyList(),
            interactionScores = emptyMap()
        )

        assertEquals(listOf("fav", "sameArtist", "other"), ranked.map { it.videoId })
    }

    @Test
    fun rankTracks_boostsCompletedPlaysAndPenalizesSkips() {
        val completed = track("completed", artist = "Artist A", genre = "Indie")
        val neutral = track("neutral", artist = "Artist B", genre = "Rock")
        val skipped = track("skipped", artist = "Artist C", genre = "Jazz")

        val ranked = LocalRecommendationEngine.rankTracks(
            candidates = listOf(skipped, neutral, completed),
            favorites = emptyList(),
            recentlyPlayed = emptyList(),
            interactionScores = mapOf(
                "completed" to 6.5f,
                "skipped" to -6.5f
            )
        )

        assertEquals(listOf("completed", "neutral", "skipped"), ranked.map { it.videoId })
    }

    @Test
    fun rankTracks_penalizesTopFiveRecentlyPlayedRelativeToUnplayedSameTasteTrack() {
        val recent = track("recent1", title = "Old Favorite", artist = "Shared Artist", album = "Shared Album", genre = "Synthpop")
        val freshSameTaste = track("fresh1", title = "Fresh Track", artist = "Shared Artist", album = "Shared Album", genre = "Synthpop")

        val ranked = LocalRecommendationEngine.rankTracks(
            candidates = listOf(recent, freshSameTaste),
            favorites = emptyList(),
            recentlyPlayed = listOf(recent),
            interactionScores = emptyMap()
        )

        assertEquals(listOf("fresh1", "recent1"), ranked.map { it.videoId })
    }

    @Test
    fun rankTracks_enforcesLeadArtistDiversityCapOfTwoWhenOtherCandidatesExist() {
        val fav = track("a1", title = "Hit One", artist = "Dominant Artist", genre = "Pop")
        val a2 = track("a2", title = "Hit Two", artist = "Dominant Artist", genre = "Pop")
        val a3 = track("a3", title = "Hit Three", artist = "Dominant Artist feat. Guest", genre = "Pop")
        val a4 = track("a4", title = "Hit Four", artist = "Dominant Artist", genre = "Pop")
        val b1 = track("b1", title = "Song B", artist = "Artist B", genre = "Rock")
        val c1 = track("c1", title = "Song C", artist = "Artist C", genre = "Jazz")
        val d1 = track("d1", title = "Song D", artist = "Artist D", genre = "Electronic")

        val ranked = LocalRecommendationEngine.rankTracks(
            candidates = listOf(fav, a2, a3, a4, b1, c1, d1),
            favorites = listOf(fav),
            recentlyPlayed = emptyList(),
            interactionScores = mapOf("a2" to 5f, "a3" to 4f, "a4" to 3f),
            limit = 5
        )

        assertEquals(5, ranked.size)
        val dominantCount = ranked.count { it.artist.startsWith("Dominant Artist") }
        assertEquals(2, dominantCount)
        assertTrue(ranked.any { it.videoId == "b1" })
        assertTrue(ranked.any { it.videoId == "c1" })
        assertTrue(ranked.any { it.videoId == "d1" })
    }

    @Test
    fun rankTracks_fallsBackToFillLimitWhenFewerDiverseArtistsExist() {
        val candidates = listOf(
            track("1", title = "One", artist = "Solo Artist"),
            track("2", title = "Two", artist = "Solo Artist"),
            track("3", title = "Three", artist = "Solo Artist"),
            track("4", title = "Four", artist = "Solo Artist")
        )

        val ranked = LocalRecommendationEngine.rankTracks(
            candidates = candidates,
            favorites = emptyList(),
            recentlyPlayed = emptyList(),
            interactionScores = emptyMap(),
            limit = 4
        )

        assertEquals(4, ranked.size)
        assertEquals(listOf("1", "2", "3", "4"), ranked.map { it.videoId })
    }

    @Test
    fun rankTracks_deduplicatesNormalizedSongs() {
        val clean = track("v1", title = "Midnight City", artist = "M83")
        val officialVideo = track("v2", title = "Midnight City (Official Music Video)", artist = "M83")
        val lyrics = track("v3", title = "Midnight City [Lyrics]", artist = "M83")
        val featuring = track("v4", title = "Midnight City (feat. Guest)", artist = "M83 & Someone")
        val otherSong = track("v5", title = "Wait", artist = "M83")

        val ranked = LocalRecommendationEngine.rankTracks(
            candidates = listOf(clean, officialVideo, lyrics, featuring, otherSong),
            favorites = emptyList(),
            recentlyPlayed = emptyList(),
            interactionScores = emptyMap(),
            limit = 10
        )

        assertEquals(2, ranked.size)
        assertEquals("v1", ranked[0].videoId)
        assertEquals("v5", ranked[1].videoId)
    }

    @Test
    fun rankTracks_favorOfficialVideosPrefersOfficialOverCoverAndNightcore() {
        val cover = track("cov", title = "Starlight (Cover)", artist = "Band")
        val nightcore = track("nc", title = "Starlight (Nightcore)", artist = "DJ")
        val official = track("off", title = "Starlight (Official Music Video)", artist = "Original")

        val ranked = LocalRecommendationEngine.rankTracks(
            candidates = listOf(cover, nightcore, official),
            favorites = emptyList(),
            recentlyPlayed = emptyList(),
            interactionScores = emptyMap(),
            favorOfficialVideos = true
        )

        assertEquals("off", ranked.first().videoId)
    }

    @Test
    fun songIdentityKey_normalizesCommonDecorationsAndFallsBackWhenBlank() {
        val t1 = track("id1", title = "Blinding Lights", artist = "The Weeknd")
        val t2 = track("id2", title = "Blinding Lights (Official Audio) [4K]", artist = "The Weeknd feat. Rosalía")
        assertEquals(
            LocalRecommendationEngine.songIdentityKey(t1),
            LocalRecommendationEngine.songIdentityKey(t2)
        )

        val fallback = AuraTrack(id = "local_9", videoId = "vid_9", title = "!!!", artist = "")
        assertEquals("vid_9", LocalRecommendationEngine.songIdentityKey(fallback))

        val idFallback = AuraTrack(id = "local_10", videoId = "", title = "", artist = "")
        assertEquals("local_10", LocalRecommendationEngine.songIdentityKey(idFallback))
    }

    @Test
    fun categoryMatches_matchesBySplitArtistsOrNormalizedGenre() {
        val seed = listOf(
            track("s1", artist = "Taylor Swift feat. Bon Iver", genre = "Indie Folk")
        )
        val (artists, genres) = LocalRecommendationEngine.buildCategoryFilterContext(seed)

        assertTrue(
            LocalRecommendationEngine.categoryMatches(
                track("m1", artist = "Bon Iver", genre = "Alternative"),
                artists,
                genres
            )
        )
        assertTrue(
            LocalRecommendationEngine.categoryMatches(
                track("m2", artist = "Fleet Foxes", genre = "Indie-Folk"),
                seed
            )
        )
        assertFalse(
            LocalRecommendationEngine.categoryMatches(
                track("m3", artist = "Metallica", genre = "Heavy Metal"),
                seed
            )
        )
        assertFalse(LocalRecommendationEngine.categoryMatches(track("m4"), emptyList()))
    }
}
