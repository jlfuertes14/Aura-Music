package com.auramusic.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.auramusic.app.R
import com.auramusic.app.core.update.AuraReleaseInfo
import com.auramusic.app.core.update.UpdateDownloadProgress
import com.auramusic.app.core.update.UpdateState
import com.auramusic.app.ui.theme.LocalAuraColors
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val FluidEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Full-screen in-app update experience matching the Yomite design architecture:
 * - Adaptive Obsidian Dark / Apple Light canvas background
 * - Top action bar with Close 'X' and GitHub icon
 * - Centered hero header with circular mascot logo, app title, subtitle, and version text
 * - Scrollable overlapping content sheet that slides over and conceals the hero header
 * - Transparent floating sticky bottom bar with "Later" and "Install Update" pill actions
 * - Single rounded container with two-toned animated fill when downloading
 */
@Composable
fun AuraUpdateModal(
    visible: Boolean,
    release: AuraReleaseInfo?,
    currentVersion: String,
    updateState: UpdateState,
    downloadProgress: UpdateDownloadProgress,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onSnooze: (String) -> Unit,
    onUpdateNow: (AuraReleaseInfo) -> Unit,
    onInstallNow: (AuraReleaseInfo) -> Unit
) {
    BackHandler(enabled = visible && updateState != UpdateState.DOWNLOADING) {
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible && release != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 350, easing = FluidEasing)
        ) + fadeIn(animationSpec = tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 300, easing = FluidEasing)
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier.zIndex(100f)
    ) {
        if (release == null) return@AnimatedVisibility

        val auraColors = LocalAuraColors.current
        val accentTint = auraReadableAccent(auraColors.accent, auraColors.isDark)
        val canvasBg = auraColors.groupedBackground
        val sheetSurfaceBg = auraColors.surface
        val context = LocalContext.current
        val formattedDate = rememberFormattedDate(release.publishedAt)
        val scrollState = rememberScrollState()

        val density = LocalDensity.current
        var heroHeaderHeightDp by remember { mutableStateOf(340.dp) }

        // Parallax and alpha fade calculation for hero header as user scrolls the overlapping card
        val scrollOffset = scrollState.value
        val heroHeaderHeightPx = with(density) { heroHeaderHeightDp.toPx() }
        val collapseProgress = (scrollOffset / (heroHeaderHeightPx * 0.70f).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val headerTranslationY = -(scrollOffset * 0.38f)
        val headerAlpha = (1f - collapseProgress).coerceIn(0f, 1f)

        ForceWhiteSystemBarsEffect()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(canvasBg)
        ) {
            // 1. Hero Header Layer (Positioned behind the scrolling sheet, fades/translates on scroll)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        val measuredDp = with(density) { size.height.toDp() }
                        if (measuredDp > 200.dp) {
                            heroHeaderHeightDp = measuredDp
                        }
                    }
                    .statusBarsPadding()
                    .padding(top = 52.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
                    .graphicsLayer {
                        translationY = headerTranslationY
                        alpha = headerAlpha
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.aura_app_icon_round),
                    contentDescription = "Aura App Icon",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Aura",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentTint
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "New version available",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = auraColors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Better music. Smoother experience.",
                    fontSize = 13.5.sp,
                    color = auraColors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").ifBlank { "1.5.2" }
                val cleanRemote = release.version.trim().removePrefix("v").removePrefix("V").ifBlank { "1.5.3" }

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "v$cleanCurrent",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = auraColors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "→",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = auraColors.textMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "v$cleanRemote",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentTint
                    )
                    if (formattedDate.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            fontSize = 13.sp,
                            color = auraColors.textMuted
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formattedDate,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = auraColors.textSecondary
                        )
                    }
                }
            }

            // 2. Scrollable Overlapping Content Card Layer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(heroHeaderHeightDp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 750.dp),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = sheetSurfaceBg,
                    shadowElevation = if (auraColors.isDark) 16.dp else 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = "Version v${release.version}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = auraColors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "WHAT'S NEW",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = auraColors.textMuted,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        MarkdownChangelogView(
                            content = release.releaseNotes,
                            accentTint = accentTint,
                            textPrimary = auraColors.textPrimary,
                            textSecondary = auraColors.textSecondary,
                            dividerColor = auraColors.divider
                        )

                        Spacer(modifier = Modifier.height(140.dp))
                    }
                }
            }

            // 3. Pinned Top Action Bar (Close 'X' & GitHub Octocat Logo)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                canvasBg.copy(alpha = 0.90f),
                                canvasBg.copy(alpha = 0.60f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        enabled = updateState != UpdateState.DOWNLOADING,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = auraColors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val targetUrl = release.htmlUrl.ifBlank { "https://github.com/jlfuertes14/Aura-Music" }
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            context.startActivity(browserIntent)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "View on GitHub",
                            tint = auraColors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 4. Sticky Floating Bottom Action Bar with Gradient Transparency
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                sheetSurfaceBg.copy(alpha = 0.65f),
                                sheetSurfaceBg.copy(alpha = 0.95f),
                                sheetSurfaceBg
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (updateState == UpdateState.ERROR && errorMessage != null) {
                        val errorColor = if (auraColors.isDark) Color(0xFFFF6961) else Color(0xFFD92D20)
                        Text(
                            text = errorMessage,
                            color = errorColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    when (updateState) {
                        UpdateState.DOWNLOADING -> {
                            val animatedProgress by animateFloatAsState(
                                targetValue = downloadProgress.fraction.coerceIn(0f, 1f),
                                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                                label = "DownloadProgressFill"
                            )

                            val clampedProgress = animatedProgress.coerceIn(0f, 1f)
                            val percent = (clampedProgress * 100).toInt()
                            val downloadedMb = downloadProgress.writtenBytes / (1024f * 1024f)
                            val totalMb = downloadProgress.totalBytes / (1024f * 1024f)
                            val progressLabel = if (totalMb > 0f) {
                                val downloadedStr = String.format(Locale.US, "%.1f", downloadedMb)
                                val totalStr = String.format(Locale.US, "%.1f", totalMb)
                                "Downloading $percent% ($downloadedStr / $totalStr MB)"
                            } else {
                                "Downloading $percent%"
                            }

                            val isBrightAccent = auraColors.accent.luminance() > 0.45f
                            val trackBg = if (isBrightAccent) Color(0xFFEAECEF) else Color(0xFF252731)
                            val labelColor = if (isBrightAccent) Color(0xFF0E1014) else Color.White

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(trackBg),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (clampedProgress > 0.001f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = clampedProgress)
                                            .background(auraColors.accent)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = progressLabel,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = labelColor
                                    )
                                }
                            }
                        }

                        UpdateState.READY -> {
                            Button(
                                onClick = { onInstallNow(release) },
                                elevation = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(percent = 50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = auraColors.accent,
                                    contentColor = auraColors.onAccent
                                )
                            ) {
                                Text(
                                    text = "Install Update",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        UpdateState.IDLE, UpdateState.ERROR -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        onSnooze(release.version)
                                        onDismiss()
                                    },
                                    elevation = null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(percent = 50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = auraColors.circleButtonBg,
                                        contentColor = auraColors.textPrimary
                                    )
                                ) {
                                    Text(
                                        text = "Later",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = { onUpdateNow(release) },
                                    elevation = null,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(percent = 50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = auraColors.accent,
                                        contentColor = auraColors.onAccent
                                    )
                                ) {
                                    Text(
                                        text = if (updateState == UpdateState.ERROR) "Retry Update" else "Install Update",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-signal Markdown Changelog Renderer built specifically for release-docs.
 * Parses headers, bullet points, numbered lists, dividers, and body paragraphs.
 */
@Composable
private fun MarkdownChangelogView(
    content: String,
    accentTint: Color,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color
) {
    val lines = remember(content) { content.replace("\r\n", "\n").split("\n") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { rawLine ->
            val trimmed = rawLine.trim()
            when {
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                trimmed.startsWith("# ") -> {
                    val title = trimmed.removePrefix("# ").trim()
                    if (!title.startsWith("Aura", ignoreCase = true) && !title.startsWith("Version", ignoreCase = true)) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }
                }

                trimmed.startsWith("Aura v", ignoreCase = true) || trimmed.startsWith("Version v", ignoreCase = true) -> {
                    // Suppress redundant version headers that might not have a leading '#'
                }

                trimmed.startsWith("## ") -> {
                    val section = trimmed.removePrefix("## ").trim()
                    Text(
                        text = section,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                trimmed.startsWith("### ") -> {
                    val subSection = trimmed.removePrefix("### ").trim()
                    Text(
                        text = subSection,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentTint,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val rawBullet = trimmed.substring(2).trim()
                    val cleanBullet = rawBullet.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(accentTint)
                        )
                        Text(
                            text = cleanBullet,
                            fontSize = 13.5.sp,
                            color = textPrimary.copy(alpha = 0.86f),
                            lineHeight = 20.sp
                        )
                    }
                }

                trimmed.startsWith("---") -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(0.5.dp)
                            .background(dividerColor)
                    )
                }

                else -> {
                    val cleanParagraph = trimmed.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                    Text(
                        text = cleanParagraph,
                        fontSize = 13.5.sp,
                        color = textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

private fun rememberFormattedDate(isoString: String): String {
    if (isoString.isBlank()) return ""
    return try {
        val clean = isoString.substringBefore("Z").substringBefore("+")
        val pattern = if (clean.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSS" else "yyyy-MM-dd'T'HH:mm:ss"
        val parser = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(clean) ?: return isoString
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
        formatter.format(date)
    } catch (_: Exception) {
        isoString
    }
}
