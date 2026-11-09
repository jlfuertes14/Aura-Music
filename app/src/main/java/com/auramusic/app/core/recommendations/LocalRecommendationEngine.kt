package com.auramusic.app.core.recommendations

import com.auramusic.app.domain.model.AuraTrack

/** Lightweight per-device ranking. It needs no account, model download, or recommendation server. */
object LocalRecommendationEngine {
    fun rankTracks(
        candidates: List<AuraTrack>,
        favorites: List<AuraTrack>,
        recentlyPlayed: List<AuraTrack>,
        interactionScores: Map<String, Float>,
        limit: Int = 40,
        favorOfficialVideos: Boolean = false
    ): List<AuraTrack> {
        if (candidates.isEmpty()) return emptyList()

        val artistWeights = mutableMapOf<String, Float>()
        val genreWeights = mutableMapOf<String, Float>()
        val albumWeights = mutableMapOf<String, Float>()
        favorites.forEach { addTaste(it, 5f, artistWeights, genreWeights, albumWeights) }
        recentlyPlayed.forEachIndexed { index, track ->
            val recencyWeight = (2.8f - index * 0.075f).coerceAtLeast(0.45f)
            val engagementWeight = ((interactionScores[track.videoId] ?: 0f) * 0.22f).coerceIn(-1.4f, 1.8f)
            addTaste(
                track,
                (recencyWeight + engagementWeight).coerceAtLeast(0.15f),
                artistWeights,
                genreWeights,
                albumWeights
            )
        }
        val likedIds = favorites.mapTo(mutableSetOf()) { it.videoId }
        val recentIds = recentlyPlayed.take(5).mapTo(mutableSetOf()) { it.videoId }

        val scored = candidates
            .distinctBy { songIdentityKey(it) }
            .mapIndexed { index, track ->
                var score = index * -0.001f
                splitArtists(track.artist).forEach { artist -> score += artistWeights[artist] ?: 0f }
                score += (genreWeights[normalize(track.genre)] ?: 0f) * 0.65f
                score += (albumWeights[normalize(track.album)] ?: 0f) * 0.35f
                score += interactionScores[track.videoId] ?: 0f
                if (track.videoId in likedIds) score += 12f
                if (track.videoId in recentIds) score -= 1.4f
                if (favorOfficialVideos) score += videoQualityScore(track.title)
                ScoredTrack(track, score)
            }.sortedByDescending { it.score }

        // Keep the shelf from becoming a row of near-identical artists, and never duplicate songs.
        val selected = mutableListOf<ScoredTrack>()
        val selectedKeys = mutableSetOf<String>()
        val artistCounts = mutableMapOf<String, Int>()
        scored.forEach { item ->
            if (selected.size >= limit) return@forEach
            val songKey = songIdentityKey(item.track)
            if (songKey in selectedKeys) return@forEach

            val leadArtist = splitArtists(item.track.artist).firstOrNull().orEmpty()
            if ((artistCounts[leadArtist] ?: 0) < 2) {
                selected += item
                selectedKeys += songKey
                artistCounts[leadArtist] = (artistCounts[leadArtist] ?: 0) + 1
            }
        }
        if (selected.size < limit) {
            scored.forEach { item ->
                if (selected.size >= limit) return@forEach
                val songKey = songIdentityKey(item.track)
                if (songKey !in selectedKeys) {
                    selected += item
                    selectedKeys += songKey
                }
            }
        }
        return selected.take(limit).map { it.track }
    }

    fun songIdentityKey(track: AuraTrack): String {
        val leadArtist = splitArtists(track.artist).firstOrNull().orEmpty()
        val cleanTitle = track.title.lowercase()
            .replace(Regex("(?i)\\s*[\\[\\(]?(?:official\\s+)?(?:music\\s+)?(?:video|audio|visualizer|lyric video|lyrics|hd|4k|remastered)[\\]\\)]?"), "")
            .replace(Regex("(?i)\\s*[\\[\\(](?:feat\\.?|ft\\.?|with)\\s+[^\\)\\]]+[\\]\\)]"), "")
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
            .trim()
        return if (leadArtist.isNotBlank() && cleanTitle.isNotBlank()) {
            "${leadArtist}_${cleanTitle}"
        } else {
            track.videoId.ifBlank { track.id }
        }
    }

    fun buildCategoryFilterContext(categoryCandidates: List<AuraTrack>): Pair<Set<String>, Set<String>> {
        if (categoryCandidates.isEmpty()) return emptySet<String>() to emptySet<String>()
        val relatedArtists = categoryCandidates.flatMap { splitArtists(it.artist) }.toSet()
        val relatedGenres = categoryCandidates.map { normalize(it.genre) }.filter(String::isNotBlank).toSet()
        return relatedArtists to relatedGenres
    }

    fun categoryMatches(track: AuraTrack, relatedArtists: Set<String>, relatedGenres: Set<String>): Boolean {
        if (relatedArtists.isEmpty() && relatedGenres.isEmpty()) return false
        return splitArtists(track.artist).any { it in relatedArtists } || normalize(track.genre) in relatedGenres
    }

    fun categoryMatches(track: AuraTrack, categoryCandidates: List<AuraTrack>): Boolean {
        if (categoryCandidates.isEmpty()) return false
        val (relatedArtists, relatedGenres) = buildCategoryFilterContext(categoryCandidates)
        return categoryMatches(track, relatedArtists, relatedGenres)
    }

    private fun addTaste(
        track: AuraTrack,
        weight: Float,
        artistWeights: MutableMap<String, Float>,
        genreWeights: MutableMap<String, Float>,
        albumWeights: MutableMap<String, Float>
    ) {
        splitArtists(track.artist).forEach { artist -> artistWeights[artist] = (artistWeights[artist] ?: 0f) + weight }
        normalize(track.genre).takeIf(String::isNotBlank)?.let { genre ->
            genreWeights[genre] = (genreWeights[genre] ?: 0f) + weight
        }
        normalize(track.album).takeIf { it.isNotBlank() && it != "youtube music" }?.let { album ->
            albumWeights[album] = (albumWeights[album] ?: 0f) + weight
        }
    }

    private fun videoQualityScore(title: String): Float {
        val value = title.lowercase()
        var score = if ("official" in value) 1.8f else 0f
        if ("official music video" in value || "official video" in value) score += 2.4f
        if (listOf("cover", "karaoke", "reaction", "nightcore", "slowed", "lyrics", "fanmade").any { it in value }) {
            score -= 6f
        }
        return score
    }

    private fun splitArtists(value: String): List<String> = value
        .lowercase()
        .split(Regex("\\s*(?:,|;|/|&|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bx\\b|\\bwith\\b)\\s*"))
        .map(::normalize)
        .filter(String::isNotBlank)

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private data class ScoredTrack(val track: AuraTrack, val score: Float)
}
