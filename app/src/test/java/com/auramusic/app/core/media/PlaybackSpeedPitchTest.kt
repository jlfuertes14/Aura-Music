package com.auramusic.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedPitchTest {

    @Test
    fun semitonesToPitchFactor_zeroOneOctaveUpAndDown() {
        assertEquals(1.0f, semitonesToPitchFactor(0), 0.0001f)
        assertEquals(2.0f, semitonesToPitchFactor(12), 0.0001f)
        assertEquals(0.5f, semitonesToPitchFactor(-12), 0.0001f)
    }

    @Test
    fun semitonesToPitchFactor_clampsBeyondOctaveBounds() {
        assertEquals(2.0f, semitonesToPitchFactor(24), 0.0001f)
        assertEquals(0.5f, semitonesToPitchFactor(-24), 0.0001f)
        assertTrue(semitonesToPitchFactor(7) in 1.49f..1.51f) // perfect fifth ~1.4983
    }

    @Test
    fun clampPlaybackSpeed_enforcesRangeAndNonFiniteFallback() {
        assertEquals(0.25f, clampPlaybackSpeed(0.05f), 0.0001f)
        assertEquals(0.25f, clampPlaybackSpeed(-2.0f), 0.0001f)
        assertEquals(2.0f, clampPlaybackSpeed(3.5f), 0.0001f)
        assertEquals(1.25f, clampPlaybackSpeed(1.25f), 0.0001f)
        assertEquals(1.0f, clampPlaybackSpeed(Float.NaN), 0.0001f)
        assertEquals(1.0f, clampPlaybackSpeed(Float.POSITIVE_INFINITY), 0.0001f)
    }

    @Test
    fun browseTreeIds_roundTripPlaylistAndTrackIds() {
        assertEquals("aura_root", AuraBrowseTree.ROOT_ID)
        assertTrue(AuraBrowseTree.isKnownNodeId(AuraBrowseTree.LIKED_ID))
        assertTrue(AuraBrowseTree.isKnownNodeId(AuraBrowseTree.DOWNLOADED_ID))
        assertTrue(AuraBrowseTree.isKnownNodeId(AuraBrowseTree.RECENT_ID))
        assertTrue(AuraBrowseTree.isKnownNodeId(AuraBrowseTree.PLAYLISTS_ID))

        val playlistNode = AuraBrowseTree.playlistNodeId("pl_favorites_99")
        assertEquals("aura_playlist:pl_favorites_99", playlistNode)
        assertEquals("pl_favorites_99", AuraBrowseTree.extractPlaylistId(playlistNode))
        assertTrue(AuraBrowseTree.isKnownNodeId(playlistNode))
        assertNull(AuraBrowseTree.extractPlaylistId("aura_playlist:"))
        assertNull(AuraBrowseTree.extractPlaylistId("aura_liked"))

        val trackBrowseId = AuraBrowseTree.trackBrowseId(playlistNode, "dQw4w9WgXcQ")
        assertEquals("aura_playlist:pl_favorites_99/dQw4w9WgXcQ", trackBrowseId)
        val parsed = AuraBrowseTree.parseTrackBrowseId(trackBrowseId)
        assertEquals(playlistNode, parsed.first)
        assertEquals("dQw4w9WgXcQ", parsed.second)

        val searchTrackId = AuraBrowseTree.trackBrowseId(AuraBrowseTree.SEARCH_ID, "abc_123")
        val parsedSearch = AuraBrowseTree.parseTrackBrowseId(searchTrackId)
        assertEquals(AuraBrowseTree.SEARCH_ID, parsedSearch.first)
        assertEquals("abc_123", parsedSearch.second)

        val plainParsed = AuraBrowseTree.parseTrackBrowseId("dQw4w9WgXcQ")
        assertNull(plainParsed.first)
        assertEquals("dQw4w9WgXcQ", plainParsed.second)
    }

    @Test
    fun browseTreePaginate_slicesSafely() {
        val items = (0 until 10).toList()
        assertEquals(listOf(0, 1, 2, 3), AuraBrowseTree.paginate(items, page = 0, pageSize = 4))
        assertEquals(listOf(4, 5, 6, 7), AuraBrowseTree.paginate(items, page = 1, pageSize = 4))
        assertEquals(listOf(8, 9), AuraBrowseTree.paginate(items, page = 2, pageSize = 4))
        assertEquals(emptyList<Int>(), AuraBrowseTree.paginate(items, page = 3, pageSize = 4))
        assertEquals(emptyList<Int>(), AuraBrowseTree.paginate(items, page = 2, pageSize = Int.MAX_VALUE))
        assertEquals(items, AuraBrowseTree.paginate(items, page = -1, pageSize = 4))
    }

    @Test
    fun extractVideoIdFromCacheKey_parsesAudioOfflineAndVideoKeys() {
        assertEquals("dQw4w9WgXcQ", extractVideoIdFromCacheKey("audio_dQw4w9WgXcQ_Auto"))
        assertEquals("ab_cd123456", extractVideoIdFromCacheKey("audio_ab_cd123456_High Quality"))
        assertEquals("xyz98765432", extractVideoIdFromCacheKey("offline_xyz98765432"))
        assertEquals("dQw4w9WgXcQ", extractVideoIdFromCacheKey("video_audio_dQw4w9WgXcQ_Auto"))
        assertEquals("dQw4w9WgXcQ", extractVideoIdFromCacheKey("video_dQw4w9WgXcQ_1080p_Auto"))
    }
}
