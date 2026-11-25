package com.auramusic.app.core.integrations

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationsCryptoTest {

    @After
    fun tearDown() {
        YtmAccountManager.setCookie("")
    }

    @Test
    fun deepLinkParser_parsesStandardYouTubeWatchUrl() {
        assertEquals(
            AuraDeepLink.Track("dQw4w9WgXcQ"),
            DeepLinkParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            AuraDeepLink.Track("dQw4w9WgXcQ"),
            DeepLinkParser.parse("https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=share")
        )
    }

    @Test
    fun deepLinkParser_parsesYouTubeMusicWatchAndPlaylistUrls() {
        assertEquals(
            AuraDeepLink.Track("abc123XYZ"),
            DeepLinkParser.parse("https://music.youtube.com/watch?v=abc123XYZ&si=shareToken")
        )
        assertEquals(
            AuraDeepLink.Playlist("PL1234567890abcdef"),
            DeepLinkParser.parse("https://music.youtube.com/playlist?list=PL1234567890abcdef")
        )
        assertEquals(
            AuraDeepLink.Playlist("OLAK5uy_example123"),
            DeepLinkParser.parse("https://music.youtube.com/watch?list=OLAK5uy_example123")
        )
    }

    @Test
    fun deepLinkParser_parsesShortLinksAndShorts() {
        assertEquals(
            AuraDeepLink.Track("kJQP7kiw5Fk"),
            DeepLinkParser.parse("https://youtu.be/kJQP7kiw5Fk?t=42")
        )
        assertEquals(
            AuraDeepLink.Track("sH0rTs_99"),
            DeepLinkParser.parse("https://youtube.com/shorts/sH0rTs_99?feature=shared")
        )
        assertEquals(
            AuraDeepLink.Track("sH0rTs_99"),
            DeepLinkParser.parse("https://www.youtube.com/shorts/sH0rTs_99")
        )
    }

    @Test
    fun deepLinkParser_extractsUrlFromSharedTextAndSearchUrls() {
        val shared = "Check out this track on YouTube Music: https://music.youtube.com/watch?v=xyz987_123!"
        assertEquals(AuraDeepLink.Track("xyz987_123"), DeepLinkParser.parse(shared))

        assertEquals(
            AuraDeepLink.Search("lofi chill beats"),
            DeepLinkParser.parse("https://music.youtube.com/search?q=lofi%20chill%20beats")
        )
        assertEquals(
            AuraDeepLink.Search("ambient piano"),
            DeepLinkParser.parse("https://www.youtube.com/results?search_query=ambient+piano")
        )
    }

    @Test
    fun deepLinkParser_returnsNullForBlankOrUnsupportedInput() {
        assertNull(DeepLinkParser.parse(null))
        assertNull(DeepLinkParser.parse(""))
        assertNull(DeepLinkParser.parse("   "))
        assertNull(DeepLinkParser.parse("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(DeepLinkParser.parse("just plain text without links"))
    }

    @Test
    fun lastfmScrobbler_computeApiSigMatchesSortedMd5Vector() {
        val params = linkedMapOf(
            "track" to "Creep",
            "artist" to "Radiohead",
            "method" to "track.scrobble",
            "timestamp" to "1700000000",
            "api_key" to "my_api_key",
            "sk" to "session_123",
            "format" to "json"
        )
        val sig = LastfmScrobbler.computeApiSig(params, "my_secret")
        assertEquals(32, sig.length)
        assertEquals("fe17defea8e2c6bdbd6d17fff623d048", sig)
    }

    @Test
    fun lastfmScrobbler_computeApiSigIsOrderIndependent() {
        val paramsA = linkedMapOf("b" to "2", "a" to "1", "c" to "3")
        val paramsB = linkedMapOf("c" to "3", "a" to "1", "b" to "2")
        assertEquals(
            LastfmScrobbler.computeApiSig(paramsA, "secret"),
            LastfmScrobbler.computeApiSig(paramsB, "secret")
        )
    }

    @Test
    fun ytmAccountManager_computeSapisidHashAndCookieExtraction() {
        val header = YtmAccountManager.computeSapisidHash(
            sapisid = "abcdef123456",
            timestampSec = 1700000000L,
            origin = "https://music.youtube.com"
        )
        assertEquals("SAPISIDHASH 1700000000_863cb324d2c0dff515af7f2ebcce9e797fc48406", header)

        assertFalse(YtmAccountManager.isSignedIn())
        assertNull(YtmAccountManager.buildAuthHeader())

        YtmAccountManager.setCookie("CONSENT=YES+1; SAPISID=abcdef123456; SID=xyz")
        assertTrue(YtmAccountManager.isSignedIn())
        assertEquals("abcdef123456", YtmAccountManager.extractSapisid(YtmAccountManager.getCookie()))
        assertEquals(
            "SAPISIDHASH 1700000000_863cb324d2c0dff515af7f2ebcce9e797fc48406",
            YtmAccountManager.buildAuthHeader(timestampSec = 1700000000L)
        )

        YtmAccountManager.setCookie("  ")
        assertFalse(YtmAccountManager.isSignedIn())
        assertEquals("", YtmAccountManager.getCookie())
    }
}
