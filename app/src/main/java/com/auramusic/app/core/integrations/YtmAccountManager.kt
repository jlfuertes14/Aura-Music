package com.auramusic.app.core.integrations

import java.security.MessageDigest

object YtmAccountManager {
    const val DEFAULT_ORIGIN = "https://music.youtube.com"

    @Volatile
    private var rawCookie: String = ""

    fun setCookie(cookie: String?) {
        rawCookie = cookie?.trim().orEmpty()
    }

    fun getCookie(): String = rawCookie

    fun isSignedIn(): Boolean = rawCookie.isNotBlank()

    fun extractSapisid(cookieHeader: String): String? {
        if (cookieHeader.isBlank()) return null
        val pairs = cookieHeader.split(";")
        var fallback3p: String? = null
        for (segment in pairs) {
            val trimmed = segment.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx <= 0) continue
            val name = trimmed.substring(0, eqIdx).trim()
            val value = trimmed.substring(eqIdx + 1).trim()
            if (value.isBlank()) continue
            if (name == "SAPISID") return value
            if (name == "__Secure-3PAPISID" && fallback3p == null) {
                fallback3p = value
            }
        }
        return fallback3p
    }

    /**
     * Computes Google InnerTube `SAPISIDHASH`: `"SAPISIDHASH <timestampSec>_<sha1Hex>"`
     * where `<sha1Hex>` is lowercase SHA-1 of `"$timestampSec $sapisid $origin"`.
     */
    fun computeSapisidHash(
        sapisid: String,
        timestampSec: Long,
        origin: String = DEFAULT_ORIGIN
    ): String {
        val input = "$timestampSec $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray(Charsets.UTF_8))
        val sha1Hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestampSec}_$sha1Hex"
    }

    fun buildAuthHeader(
        origin: String = DEFAULT_ORIGIN,
        timestampSec: Long = System.currentTimeMillis() / 1000L
    ): String? {
        if (!isSignedIn()) return null
        val sapisid = extractSapisid(rawCookie) ?: return null
        return computeSapisidHash(sapisid, timestampSec, origin)
    }
}
