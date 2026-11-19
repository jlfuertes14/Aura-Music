package com.auramusic.app.core.sponsorblock

import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.domain.model.ReturnYouTubeDislikeData
import com.auramusic.app.domain.model.SponsorSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object SponsorBlockRepository {
    val DEFAULT_CATEGORIES = setOf(
        "sponsor", "selfpromo", "interaction", "intro", "outro", "music_offtopic"
    )
    private val segmentCache = ConcurrentHashMap<String, List<SponsorSegment>>()
    private val rydCache = ConcurrentHashMap<String, ReturnYouTubeDislikeData>()

    fun clearSegmentCache() {
        segmentCache.clear()
    }

    suspend fun getSponsorSegments(
        videoId: String,
        categories: Set<String> = DEFAULT_CATEGORIES
    ): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || categories.isEmpty()) return@withContext emptyList()
        val sortedCats = categories.sorted()
        val cacheKey = "${videoId}_${sortedCats.joinToString(",")}"
        segmentCache[cacheKey]?.let { return@withContext it }

        try {
            val encodedCategories = sortedCats.joinToString(",") { "%22$it%22" }
            val categoriesParam = "%5B$encodedCategories%5D"
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$categoriesParam"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    segmentCache[cacheKey] = emptyList()
                    return@withContext emptyList()
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val list = mutableListOf<SponsorSegment>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val segArr = obj.optJSONArray("segment") ?: continue
                    if (segArr.length() < 2) continue
                    val start = segArr.optDouble(0, 0.0).toFloat()
                    val end = segArr.optDouble(1, 0.0).toFloat()
                    if (end > start + 0.5f) {
                        list.add(
                            SponsorSegment(
                                uuid = obj.optString("UUID", "seg_$i"),
                                startTimeSec = start,
                                endTimeSec = end,
                                category = obj.optString("category", "music_offtopic")
                            )
                        )
                    }
                }
                segmentCache[cacheKey] = list
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getReturnYouTubeDislike(videoId: String): ReturnYouTubeDislikeData? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        rydCache[videoId]?.let { return@withContext it }

        try {
            val req = Request.Builder()
                .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
                .header("Accept", "application/json")
                .build()

            SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val obj = JSONObject(resp.body?.string() ?: return@withContext null)
                val data = ReturnYouTubeDislikeData(
                    likes = obj.optLong("likes", 0L),
                    dislikes = obj.optLong("dislikes", 0L),
                    viewCount = obj.optLong("viewCount", 0L),
                    rating = obj.optDouble("rating", 5.0).toFloat()
                )
                rydCache[videoId] = data
                data
            }
        } catch (e: Exception) {
            null
        }
    }
}
