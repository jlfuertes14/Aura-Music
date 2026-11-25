package com.auramusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auramusic.app.core.comments.YouTubeCommentsRepository
import com.auramusic.app.domain.model.AuraCommentSort
import com.auramusic.app.domain.model.AuraCommentsPage
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.AuraYouTubeComment
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraCommentsBottomSheet(
    track: AuraTrack,
    page: AuraCommentsPage?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSelectSort: (AuraCommentSort) -> Unit = {},
    onToggleReplies: (String) -> Unit = {},
    onLoadMoreReplies: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val auraColors = LocalAuraColors.current
    val accentTint = auraReadableAccent(auraColors.accent, auraColors.isDark)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = auraGlassSheetColor(auraColors.sheetBackground),
        scrimColor = auraColors.scrim,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(auraColors.dragHandle)
            )
        }
    ) {
        ForceWhiteSystemBarsEffect()
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.78f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Forum,
                    contentDescription = null,
                    tint = auraColors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        "Comments",
                        color = auraColors.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = listOfNotNull(track.title, page?.countText).joinToString(" · "),
                        color = auraColors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (page != null && !page.commentsDisabled && (page.topSortToken != null || page.newestSortToken != null)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AuraCommentSort.entries.forEach { sort ->
                        val isSelected = page.activeSort == sort
                        val hasToken = when (sort) {
                            AuraCommentSort.TOP -> page.topSortToken != null
                            AuraCommentSort.NEWEST -> page.newestSortToken != null
                        }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) auraColors.accent else auraColors.pillInactiveBg,
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(enabled = hasToken && !isLoading) {
                                    onSelectSort(sort)
                                }
                        ) {
                            Text(
                                text = sort.label,
                                color = if (isSelected) {
                                    auraColors.onAccent
                                } else {
                                    auraColors.pillInactiveText.copy(alpha = if (hasToken) 1f else 0.45f)
                                },
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = auraColors.divider,
                thickness = 0.5.dp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading && (page == null || page.comments.isEmpty()) -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = accentTint,
                        strokeWidth = 2.dp
                    )
                    error != null && page == null -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(error, color = auraColors.textSecondary)
                        TextButton(onClick = onRetry) { Text("Retry", color = accentTint) }
                    }
                    page?.commentsDisabled == true || (page != null && page.comments.isEmpty()) -> Text(
                        if (page?.commentsDisabled == true) "Comments are turned off for this video." else "No comments to show yet.",
                        color = auraColors.textSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    page != null -> LazyColumn(Modifier.fillMaxWidth()) {
                        items(page.comments, key = { it.id }) { comment ->
                            CommentThreadItem(
                                comment = comment,
                                onToggleReplies = { onToggleReplies(comment.id) },
                                onLoadMoreReplies = { onLoadMoreReplies(comment.id) }
                            )
                        }
                        if (page.continuation != null) item {
                            TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        Modifier.size(16.dp),
                                        color = accentTint,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Load more comments", color = accentTint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentThreadItem(
    comment: AuraYouTubeComment,
    onToggleReplies: () -> Unit,
    onLoadMoreReplies: () -> Unit
) {
    val auraColors = LocalAuraColors.current
    val accentTint = auraReadableAccent(auraColors.accent, auraColors.isDark)
    val hasReplies = comment.replyCountText.isNotBlank() ||
        comment.repliesContinuation != null ||
        comment.replies.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        CommentBodyRow(
            comment = comment,
            avatarSize = 38.dp
        )

        if (hasReplies) {
            Row(
                modifier = Modifier
                    .padding(start = 46.dp, top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !comment.isLoadingReplies) { onToggleReplies() }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (comment.isRepliesExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (comment.isRepliesExpanded) "Hide replies" else "Show replies",
                    tint = accentTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (comment.isRepliesExpanded) "Hide replies" else comment.replyCountText.ifBlank { "View replies" },
                    color = accentTint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (comment.isRepliesExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                comment.replies.forEach { reply ->
                    CommentBodyRow(
                        comment = reply,
                        avatarSize = 26.dp
                    )
                }

                if (comment.isLoadingReplies) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = auraColors.textSecondary,
                            strokeWidth = 2.dp
                        )
                    }
                } else if (comment.repliesNextContinuation != null) {
                    Text(
                        text = "Show more replies",
                        color = accentTint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onLoadMoreReplies() }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentBodyRow(
    comment: AuraYouTubeComment,
    avatarSize: Dp
) {
    val auraColors = LocalAuraColors.current
    val scope = rememberCoroutineScope()
    var translatedText by remember(comment.id) {
        mutableStateOf(YouTubeCommentsRepository.getCachedTranslation(comment.id))
    }
    var isShowingTranslated by rememberSaveable(comment.id) {
        mutableStateOf(false)
    }
    var isTranslating by remember(comment.id) {
        mutableStateOf(false)
    }
    val canTranslate = remember(comment.id, comment.text, comment.translateActionToken) {
        YouTubeCommentsRepository.isTranslatableToEnglish(comment.text, comment.translateActionToken)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (!comment.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = comment.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
            )
        } else {
            Box(
                Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(auraColors.circleButtonBg)
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author,
                    color = auraColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (comment.publishedTime.isNotBlank()) {
                    Text(
                        "  ·  ${comment.publishedTime}",
                        color = auraColors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val displayedText = if (isShowingTranslated && !translatedText.isNullOrBlank()) {
                translatedText!!
            } else {
                comment.text
            }
            Text(
                text = displayedText,
                color = auraColors.textPrimary.copy(alpha = 0.90f),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            if (canTranslate || !translatedText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isTranslating) {
                            if (isShowingTranslated) {
                                isShowingTranslated = false
                            } else if (!translatedText.isNullOrBlank()) {
                                isShowingTranslated = true
                            } else {
                                isTranslating = true
                                scope.launch {
                                    val result = YouTubeCommentsRepository.translateToEnglish(
                                        commentId = comment.id,
                                        text = comment.text,
                                        translateToken = comment.translateActionToken
                                    )
                                    isTranslating = false
                                    if (!result.isNullOrBlank()) {
                                        translatedText = result
                                        isShowingTranslated = true
                                    }
                                }
                            }
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            color = auraColors.textSecondary,
                            strokeWidth = 1.5.dp
                        )
                        Text(
                            text = "Translating…",
                            color = auraColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = if (isShowingTranslated && !translatedText.isNullOrBlank()) {
                                "See original (Translated by Google)"
                            } else {
                                "Translate to English"
                            },
                            color = auraColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (comment.likeCount.isNotBlank() && comment.likeCount != "0") {
                Spacer(Modifier.height(5.dp))
                Text("♡  ${comment.likeCount}", color = auraColors.textSecondary, fontSize = 11.sp)
            }
        }
    }
}
