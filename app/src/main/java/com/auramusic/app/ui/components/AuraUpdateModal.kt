package com.auramusic.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.R
import com.auramusic.app.core.update.AuraReleaseInfo
import com.auramusic.app.core.update.UpdateDownloadProgress
import com.auramusic.app.core.update.UpdateState
import com.auramusic.app.ui.theme.AuraNeonLime
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val FluidEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val DarkCanvasBg = Color(0xFF000000)
private val CardSurfaceBg = Color(0xFF16181F)
private val AuraLime = AuraNeonLime // Official AURA Neon Lime (#D4F644)
private val SecondaryText = Color(0xFF9E9E9E)
private val LightButtonBg = Color(0xFFF0F1F4)
private val DarkButtonText = Color(0xFF111215)

/**
 * Full-screen in-app update experience matching the Yomite design architecture:
 * - Immersive dark canvas background
 * - Top action bar with Close 'X' and GitHub icon
 * - Centered hero header with circular mascot logo, app title, subtitle, and version text
 * - Description container starts right at the version change text below the tagline
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
                .background(DarkCanvasBg)
        ) {
            // 1. Hero Header Layer (Positioned behind the scrolling sheet, fades/translates on scroll)
            // onSizeChanged placed BEFORE statusBarsPadding & padding so measured size accurately includes insets
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
                // Circled App Icon from assets/icon.png directly matching APK round icon
                Image(
                    painter = painterResource(id = R.drawable.aura_app_icon_round),
                    contentDescription = "Aura App Icon",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // App Brand Name in official AURA Neon Lime
                Text(
                    text = "Aura",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuraLime
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Large Headline
                Text(
                    text = "New version available",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle / Tagline
                Text(
                    text = "Better music. Smoother experience.",
                    fontSize = 13.5.sp,
                    color = SecondaryText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Version Diff Text Row (matching Yomite minimal inline design: no pill border/background)
                val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").ifBlank { "1.5.0" }
                val cleanRemote = release.version.trim().removePrefix("v").removePrefix("V").ifBlank { "1.5.1" }

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "v$cleanCurrent",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFA0A5B5)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "→",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7E8292)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "v$cleanRemote",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AuraLime
                    )
                    if (formattedDate.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            fontSize = 13.sp,
                            color = Color(0xFF5A5E6D)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formattedDate,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA0A5B5)
                        )
                    }
                }
            }

            // 2. Scrollable Overlapping Content Card Layer
            // Dynamic spacer ensures the sheet starts cleanly right below the version change text
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(heroHeaderHeightDp))

                // Overlapping Sheet with Rounded Top Corners
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 750.dp),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = CardSurfaceBg,
                    shadowElevation = 16.dp
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
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "WHAT'S NEW",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7A7D87),
                            letterSpacing = 1.2.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Full Markdown Changelog parsed from release-docs
                        MarkdownChangelogView(content = release.releaseNotes)

                        // Generous bottom spacer so text scrolls cleanly above sticky floating bottom bar
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
                                Color(0xE6000000),
                                Color(0x99000000),
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
                            tint = Color.White,
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
                            tint = Color.White,
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
                                Color(0x99000000),
                                Color(0xF2000000),
                                Color.Black
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
                    // Error Notification Bar
                    if (updateState == UpdateState.ERROR && errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Bottom Action Area
                    when (updateState) {
                        UpdateState.DOWNLOADING -> {
                            // Single rounded border container with two-toned progress animation fill
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
                                val downloadedStr = String.format(java.util.Locale.US, "%.1f", downloadedMb)
                                val totalStr = String.format(java.util.Locale.US, "%.1f", totalMb)
                                "Downloading $percent% ($downloadedStr / $totalStr MB)"
                            } else {
                                "Downloading $percent%"
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(LightButtonBg),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // Two-toned fill progress layer
                                // Note: Outer container is already clipped to percent = 50 (capsule pill).
                                // We do NOT apply a secondary RoundedCornerShape(percent = 50) on this inner box,
                                // preventing IllegalArgumentException if width is near zero or fractional.
                                if (clampedProgress > 0.001f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = clampedProgress)
                                            .background(AuraLime)
                                    )
                                }

                                // Centered label with progress text - color black not white
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = progressLabel,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        UpdateState.READY -> {
                            // Single full-width "Install Update" button
                            Button(
                                onClick = { onInstallNow(release) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(percent = 50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AuraLime,
                                    contentColor = DarkButtonText
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
                            // Two Sticky Pill Buttons: "Later" and "Install Update"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // "Later" Pill Button (Light / White Background with Dark Text)
                                Button(
                                    onClick = {
                                        onSnooze(release.version)
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(percent = 50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LightButtonBg,
                                        contentColor = DarkButtonText
                                    )
                                ) {
                                    Text(
                                        text = "Later",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // "Install Update" / "Retry Update" Pill Button (AURA Neon Lime with Dark Text)
                                Button(
                                    onClick = { onUpdateNow(release) },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(percent = 50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AuraLime,
                                        contentColor = DarkButtonText
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
private fun MarkdownChangelogView(content: String) {
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
                    // Filter out redundant version titles since the modal sheet already prominently displays "Version vX.X" at the top
                    if (!title.startsWith("Aura", ignoreCase = true) && !title.startsWith("Version", ignoreCase = true)) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
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
                        color = Color.White,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                trimmed.startsWith("### ") -> {
                    val subSection = trimmed.removePrefix("### ").trim()
                    Text(
                        text = subSection,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AuraLime,
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
                                .padding(top = 6.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(AuraLime)
                        )
                        Text(
                            text = cleanBullet,
                            fontSize = 13.5.sp,
                            color = Color(0xFFD6D8E0),
                            lineHeight = 20.sp
                        )
                    }
                }

                trimmed.startsWith("---") -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(1.dp)
                            .background(Color(0xFF262933))
                    )
                }

                else -> {
                    val cleanParagraph = trimmed.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                    Text(
                        text = cleanParagraph,
                        fontSize = 13.5.sp,
                        color = Color(0xFFA6ABB8),
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
