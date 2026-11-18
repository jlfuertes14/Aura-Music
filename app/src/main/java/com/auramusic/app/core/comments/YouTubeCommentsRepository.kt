package com.auramusic.app.core.comments

import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.domain.model.AuraCommentSort
import com.auramusic.app.domain.model.AuraCommentsPage
import com.auramusic.app.domain.model.AuraYouTubeComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject

data class AuraCommentRepliesPage(
    val replies: List<AuraYouTubeComment>,
    val continuation: String?
)

/** Read-only InnerTube comments flow: watch-page comments tab token, sort menus, replies, and continuation. */
object YouTubeCommentsRepository {
    private const val ENDPOINT = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    private const val COMMENT_ACTION_ENDPOINT = "https://music.youtube.com/youtubei/v1/comment/perform_comment_action?prettyPrint=false"
    private const val CLIENT_VERSION = "1.20250312.01.00"

    private val translationCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val nonEnglishIndicators = setOf(
        // Tagalog / Filipino
        "ang", "mga", "niya", "namin", "kayo", "sila", "ito", "iyan", "iyon", "kasi", "pero", "para",
        "lang", "naman", "talaga", "sobrang", "ganda", "sarap", "mahal", "ikaw", "ako", "tayo", "kami",
        "siya", "yung", "walang", "meron", "grabe", "lupet", "nakakaiyak", "kanta", "kantang", "boses",
        "naalala", "palagi", "hanggang", "ngayon", "kahit", "bakit", "paano", "sana", "lahat", "puso",
        // Spanish / Portuguese
        "que", "por", "con", "una", "este", "esta", "como", "muy", "más", "canción", "hermosa", "mejor",
        "música", "vida", "corazón", "siempre", "cuando", "todo", "nada", "não", "muito", "linda", "essa",
        "você", "também", "estou", "mejor", "recuerdo", "escuchar", "temazo", "gracias",
        // Indonesian / Malay
        "yang", "dan", "ini", "itu", "dari", "untuk", "dengan", "bisa", "banget", "lagu", "enak", "bagus",
        "suka", "dengar", "selalu", "hati", "banget", "keren",
        // French / German / Italian / Turkish
        "c'est", "très", "avec", "dans", "chanson", "toujours", "magnifique", "écoute",
        "ich", "nicht", "das", "und", "lied", "schön", "immer",
        "molto", "questa", "canzone", "sempre", "bella",
        "çok", "güzel", "şarkı", "harika"
    )

    fun getCachedTranslation(commentId: String): String? = translationCache[commentId]

    fun isTranslatableToEnglish(text: String, translateToken: String? = null): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false
        if (!translateToken.isNullOrBlank()) return true

        // Check for any non-ASCII alphabetic character (CJK, Hangul, Kana, Cyrillic, Arabic, Thai, Devanagari, accented Latin, etc.)
        var asciiLetters = 0
        var nonAsciiLetters = 0
        for (ch in clean) {
            if (ch.isLetter()) {
                if (ch.code <= 127) asciiLetters++ else nonAsciiLetters++
            }
        }
        if (nonAsciiLetters > 0) return true
        if (asciiLetters < 3) return false

        // Check for common non-English Latin-script words
        val tokens = clean.lowercase()
            .split(Regex("[^\\p{L}']+"))
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return false
        val matches = tokens.count { it in nonEnglishIndicators }
        return matches >= 2 || (tokens.size <= 5 && matches >= 1)
    }

    suspend fun translateToEnglish(
        commentId: String,
        text: String,
        translateToken: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        translationCache[commentId]?.let { return@withContext it }

        // 1. Try InnerTube perform_comment_action if an explicit action token was provided
        if (!translateToken.isNullOrBlank()) {
            runCatching {
                val payload = JSONObject()
                    .put("context", JSONObject().put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", CLIENT_VERSION)
                        put("hl", "en")
                        put("gl", "US")
                    }))
                    .put("actions", JSONArray().put(translateToken))
                val request = Request.Builder().url(COMMENT_ACTION_ENDPOINT)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                SimpMusicExtractorEngine.httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val root = JSONObject(response.body?.string() ?: "{}")
                        val translated = findObjects(root, "translatedContent")
                            .firstNotNullOfOrNull { it.optString("content").takeIf(String::isNotBlank) }
                            ?: findObjects(root, "commentEntityPayload")
                                .firstNotNullOfOrNull {
                                    it.optJSONObject("translatedContent")?.optString("content")?.takeIf(String::isNotBlank)
                                }
                        if (!translated.isNullOrBlank()) {
                            translationCache[commentId] = translated
                            return@withContext translated
                        }
                    }
                }
            }
        }

        // 2. Google Translate endpoint used by YouTube/Chrome for instant auto-detected English translation
        runCatching {
            val encoded = java.net.URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:131.0) Gecko/131.0 Firefox/131.0")
                .build()
            SimpMusicExtractorEngine.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val outer = JSONArray(body)
                val sentences = outer.optJSONArray(0) ?: return@runCatching null
                val builder = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val seg = sentences.optJSONArray(i) ?: continue
                    val part = seg.optString(0)
                    if (part.isNotEmpty() && part != "null") {
                        builder.append(part)
                    }
                }
                val result = builder.toString().trim()
                if (result.isNotBlank()) {
                    translationCache[commentId] = result
                    return@withContext result
                }
            }
        }
        null
    }

    suspend fun firstPage(videoId: String): AuraCommentsPage = withContext(Dispatchers.IO) {
        require(videoId.isNotBlank()) { "A video id is required to load comments" }
        val watch = nextRequest(JSONObject().put("videoId", videoId))
        val token = findCommentsTabToken(watch)
            ?: return@withContext AuraCommentsPage(null, emptyList(), commentsDisabled = true)
        parsePage(nextRequest(JSONObject().put("continuation", token)))
    }

    suspend fun nextPage(continuation: String): AuraCommentsPage = withContext(Dispatchers.IO) {
        parsePage(nextRequest(JSONObject().put("continuation", continuation)))
    }

    suspend fun loadReplies(continuation: String): AuraCommentRepliesPage = withContext(Dispatchers.IO) {
        val page = parsePage(nextRequest(JSONObject().put("continuation", continuation)))
        AuraCommentRepliesPage(replies = page.comments, continuation = page.continuation)
    }

    private fun nextRequest(extra: JSONObject): JSONObject {
        val payload = JSONObject().put("context", JSONObject().put("client", JSONObject().apply {
            put("clientName", "WEB_REMIX")
            put("clientVersion", CLIENT_VERSION)
            put("hl", "en")
            put("gl", "US")
        }))
        extra.keys().forEach { key -> payload.put(key, extra.get(key)) }
        val request = Request.Builder().url(ENDPOINT)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return SimpMusicExtractorEngine.httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "YouTube comments request failed (${response.code})" }
            JSONObject(response.body?.string() ?: "{}")
        }
    }

    private fun findCommentsTabToken(root: JSONObject): String? {
        val watchRenderer = findObjects(root, "watchNextTabbedResultsRenderer").firstOrNull() ?: return null
        val tabs = watchRenderer.optJSONArray("tabs") ?: return null
        for (index in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(index)?.optJSONObject("tabRenderer") ?: continue
            val section = tab.optJSONObject("content")?.optJSONObject("sectionListRenderer") ?: continue
            val continuations = section.optJSONArray("continuations") ?: continue
            for (i in 0 until continuations.length()) {
                val reload = continuations.optJSONObject(i)?.optJSONObject("reloadContinuationData") ?: continue
                reload.optString("continuation").takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return null
    }

    private fun parsePage(root: JSONObject): AuraCommentsPage {
        val entities = mutableMapOf<String, JSONObject>()
        findObjects(root, "entityBatchUpdate").forEach { batch ->
            val mutations = batch.optJSONArray("mutations") ?: return@forEach
            for (i in 0 until mutations.length()) {
                val mutation = mutations.optJSONObject(i) ?: continue
                val key = mutation.optString("entityKey")
                val payload = mutation.optJSONObject("payload")?.optJSONObject("commentEntityPayload")
                if (key.isNotBlank() && payload != null) entities[key] = payload
            }
        }

        val endpointItems = extractTopLevelContinuationItems(root)
        val threads = if (endpointItems.isNotEmpty()) {
            endpointItems.mapNotNull { it.optJSONObject("commentThreadRenderer") }
        } else {
            findObjects(root, "commentThreadRenderer")
        }

        val comments = mutableListOf<AuraYouTubeComment>()
        threads.forEach { thread ->
            parseCommentThread(thread, entities)?.let(comments::add)
        }
        if (comments.isEmpty()) {
            findObjects(root, "commentRenderer").mapNotNullTo(comments) { it.toAuraComment() }
        }

        val header = findObjects(root, "commentsHeaderRenderer").firstOrNull()
        val count = header?.optJSONObject("commentsCount")?.readText()
            ?: header?.optJSONObject("countText")?.readText()

        var topSortToken: String? = null
        var newestSortToken: String? = null
        var activeSort = AuraCommentSort.TOP
        val sortItems = header?.optJSONObject("sortMenu")
            ?.optJSONObject("sortFilterSubMenuRenderer")
            ?.optJSONArray("subMenuItems")
        if (sortItems != null) {
            for (i in 0 until sortItems.length()) {
                val item = sortItems.optJSONObject(i) ?: continue
                val title = item.optString("title")
                val token = item.optJSONObject("continuation")
                    ?.optJSONObject("reloadContinuationData")
                    ?.optString("continuation")
                    ?.takeIf(String::isNotBlank)
                val selected = item.optBoolean("selected", false)
                when {
                    title.contains("Newest", ignoreCase = true) -> {
                        newestSortToken = token
                        if (selected) activeSort = AuraCommentSort.NEWEST
                    }
                    title.contains("Top", ignoreCase = true) -> {
                        topSortToken = token
                        if (selected) activeSort = AuraCommentSort.TOP
                    }
                }
            }
        }

        val continuation = if (endpointItems.isNotEmpty()) {
            endpointItems.firstNotNullOfOrNull { item ->
                item.optJSONObject("continuationItemRenderer")?.extractContinuationToken()
            }
        } else {
            findObjects(root, "continuationItemRenderer").firstNotNullOfOrNull { it.extractContinuationToken() }
        }

        return AuraCommentsPage(
            countText = count,
            comments = comments.distinctBy { it.id },
            continuation = continuation,
            activeSort = activeSort,
            topSortToken = topSortToken,
            newestSortToken = newestSortToken
        )
    }

    private fun extractTopLevelContinuationItems(root: JSONObject): List<JSONObject> {
        val endpoints = root.optJSONArray("onResponseReceivedEndpoints") ?: return emptyList()
        val items = mutableListOf<JSONObject>()
        for (i in 0 until endpoints.length()) {
            val endpoint = endpoints.optJSONObject(i) ?: continue
            val arr = endpoint.optJSONObject("reloadContinuationItemsCommand")?.optJSONArray("continuationItems")
                ?: endpoint.optJSONObject("appendContinuationItemsAction")?.optJSONArray("continuationItems")
                ?: continue
            for (j in 0 until arr.length()) {
                arr.optJSONObject(j)?.let(items::add)
            }
        }
        return items
    }

    private fun parseCommentThread(
        thread: JSONObject,
        entities: Map<String, JSONObject>
    ): AuraYouTubeComment? {
        val baseComment = parseCommentFromThread(thread, entities) ?: return null
        val repliesRenderer = thread.optJSONObject("replies")?.optJSONObject("commentRepliesRenderer")
            ?: return baseComment

        val viewRepliesText = repliesRenderer.optJSONObject("viewReplies")
            ?.optJSONObject("buttonRenderer")
            ?.optJSONObject("text")
            ?.readText()
            .orEmpty()

        val repliesContinuation = extractRepliesContinuation(repliesRenderer.optJSONArray("subThreads"))
            ?: extractRepliesContinuation(repliesRenderer.optJSONArray("contents"))

        val inlineReplies = mutableListOf<AuraYouTubeComment>()
        val contents = repliesRenderer.optJSONArray("contents")
        if (contents != null) {
            for (i in 0 until contents.length()) {
                contents.optJSONObject(i)?.optJSONObject("commentRenderer")?.toAuraComment()?.let(inlineReplies::add)
            }
        }

        val resolvedReplyCountText = viewRepliesText.ifBlank {
            baseComment.replyCountText.takeIf { it.isNotBlank() && it != "0" }
                ?.let { if (it == "1") "1 reply" else "$it replies" }
                .orEmpty()
        }

        return baseComment.copy(
            replyCountText = resolvedReplyCountText,
            repliesContinuation = repliesContinuation,
            replies = inlineReplies
        )
    }

    private fun parseCommentFromThread(
        thread: JSONObject,
        entities: Map<String, JSONObject>
    ): AuraYouTubeComment? {
        val viewModel = thread.optJSONObject("commentViewModel")?.optJSONObject("commentViewModel")
        val commentKey = viewModel?.optString("commentKey").orEmpty()
        val payload = entities[commentKey]
        if (payload != null) {
            val properties = payload.optJSONObject("properties")
            val author = payload.optJSONObject("author")
            val toolbar = payload.optJSONObject("toolbar")
            val id = viewModel?.optString("commentId").orEmpty().ifBlank { properties?.optString("commentId").orEmpty() }
            if (id.isBlank()) return null
            val translateToken = findObjects(payload, "performCommentActionEndpoint")
                .firstNotNullOfOrNull { it.optString("action").takeIf(String::isNotBlank) }
                ?: viewModel?.let { vm ->
                    findObjects(vm, "performCommentActionEndpoint")
                        .firstNotNullOfOrNull { it.optString("action").takeIf(String::isNotBlank) }
                }
            return AuraYouTubeComment(
                id = id,
                author = author?.optString("displayName").orEmpty().ifBlank { "YouTube user" },
                avatarUrl = author?.optString("avatarThumbnailUrl")?.takeIf(String::isNotBlank),
                text = properties?.optJSONObject("content")?.optString("content").orEmpty(),
                publishedTime = properties?.optString("publishedTime").orEmpty(),
                likeCount = toolbar?.optString("likeCountNotliked").orEmpty(),
                replyCountText = toolbar?.optString("replyCount").orEmpty(),
                translateActionToken = translateToken
            )
        }
        // Older InnerTube renderer is still used for some tracks and client responses.
        return thread.optJSONObject("comment")?.optJSONObject("commentRenderer")?.toAuraComment()
    }

    private fun extractRepliesContinuation(array: JSONArray?): String? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            val renderer = array.optJSONObject(i)?.optJSONObject("continuationItemRenderer") ?: continue
            renderer.extractContinuationToken()?.let { return it }
        }
        return null
    }

    private fun JSONObject.extractContinuationToken(): String? =
        optJSONObject("continuationEndpoint")
            ?.optJSONObject("continuationCommand")
            ?.optString("token")
            ?.takeIf(String::isNotBlank)
            ?: optJSONObject("button")
                ?.optJSONObject("buttonRenderer")
                ?.optJSONObject("command")
                ?.optJSONObject("continuationCommand")
                ?.optString("token")
                ?.takeIf(String::isNotBlank)

    private fun JSONObject.toAuraComment(): AuraYouTubeComment? {
        val id = optString("commentId").ifBlank { return null }
        val author = optJSONObject("authorText")?.readText().orEmpty().ifBlank { "YouTube user" }
        val text = optJSONObject("contentText")?.readText().orEmpty()
        val avatar = optJSONObject("authorThumbnail")?.optJSONArray("thumbnails")?.lastOrNullObject()?.optString("url")
        val translateToken = optJSONObject("translateButton")
            ?.let { findObjects(it, "performCommentActionEndpoint").firstNotNullOfOrNull { ep -> ep.optString("action").takeIf(String::isNotBlank) } }
        return AuraYouTubeComment(
            id = id,
            author = author,
            avatarUrl = avatar?.takeIf(String::isNotBlank),
            text = text,
            publishedTime = optJSONObject("publishedTimeText")?.readText().orEmpty(),
            likeCount = optJSONObject("voteCount")?.readText().orEmpty(),
            translateActionToken = translateToken
        )
    }

    private fun JSONObject?.readText(): String? {
        if (this == null) return null
        optString("simpleText").takeIf(String::isNotBlank)?.let { return it }
        return optJSONArray("runs")?.let { runs ->
            (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
        }
    }

    private fun JSONArray.lastOrNullObject(): JSONObject? = optJSONObject(length() - 1)

    private fun findObjects(node: JSONObject, key: String): List<JSONObject> = buildList {
        node.keys().forEach { name ->
            val value = node.opt(name)
            when (value) {
                is JSONObject -> {
                    if (name == key) add(value)
                    addAll(findObjects(value, key))
                }
                is JSONArray -> for (i in 0 until value.length()) value.optJSONObject(i)?.let { addAll(findObjects(it, key)) }
            }
        }
    }
}
