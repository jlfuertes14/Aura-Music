package com.auramusic.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.auramusic.app.MainActivity
import com.auramusic.app.R
import com.auramusic.app.core.media.AuraPlayerManager

class AuraQuickPlayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val track = AuraPlayerManager.currentTrack.value
        val isPlaying = AuraPlayerManager.isPlaying.value
        val artwork = AuraWidgetSupport.loadWidgetArtworkBitmap(context, track)

        provideContent {
            QuickPlayContent(
                trackTitle = track?.title?.ifBlank { "Aura Music" } ?: "Aura Music",
                artistName = track?.artist?.ifBlank { "Ready to stream" } ?: "Ready to stream",
                isPlaying = isPlaying,
                artwork = artwork
            )
        }
    }

    @Composable
    private fun QuickPlayContent(
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        artwork: Bitmap?
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(AuraWidgetSupport.CardBg))
                .cornerRadius(24.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(72.dp)
                    .cornerRadius(16.dp)
                    .background(ColorProvider(AuraWidgetSupport.CardSurface))
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                val artProvider = if (artwork != null) {
                    ImageProvider(artwork)
                } else {
                    ImageProvider(R.drawable.aura_app_icon_round)
                }
                Image(
                    provider = artProvider,
                    contentDescription = "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(72.dp)
                        .cornerRadius(16.dp)
                )
            }

            Spacer(modifier = GlanceModifier.width(12.dp))

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = trackTitle,
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(AuraWidgetSupport.TextPrimary),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = artistName,
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(AuraWidgetSupport.TextSecondary),
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(12.dp)
                            .background(ColorProvider(AuraWidgetSupport.PlayButtonBg))
                            .clickable(actionStartActivity<MainActivity>())
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Open",
                            style = TextStyle(
                                color = ColorProvider(AuraWidgetSupport.AccentLime),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(10.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    TransportIconButton(
                        iconRes = android.R.drawable.ic_media_previous,
                        contentDescription = "Previous track",
                        tint = AuraWidgetSupport.TextPrimary,
                        backgroundColor = AuraWidgetSupport.CardSurface,
                        onClick = actionRunCallback<WidgetPreviousAction>()
                    )
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    TransportIconButton(
                        iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = AuraWidgetSupport.CardBg,
                        backgroundColor = AuraWidgetSupport.AccentLime,
                        onClick = actionRunCallback<WidgetPlayPauseAction>()
                    )
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    TransportIconButton(
                        iconRes = android.R.drawable.ic_media_next,
                        contentDescription = "Next track",
                        tint = AuraWidgetSupport.TextPrimary,
                        backgroundColor = AuraWidgetSupport.CardSurface,
                        onClick = actionRunCallback<WidgetNextAction>()
                    )
                }
            }
        }
    }
}

class AuraQuickPlayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuraQuickPlayWidget()
}
