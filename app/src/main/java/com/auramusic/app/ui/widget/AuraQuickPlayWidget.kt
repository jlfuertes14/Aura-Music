package com.auramusic.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import com.auramusic.app.R
import com.auramusic.app.core.media.AuraPlayerManager

class AuraQuickPlayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val track = AuraWidgetSupport.resolveDisplayTrack(context)
        val isPlaying = AuraPlayerManager.isPlaying.value
        val artwork = AuraWidgetSupport.loadWidgetArtworkBitmap(context, track)
        val palette = AuraWidgetSupport.extractWidgetPalette(context, track, artwork)

        provideContent {
            QuickPlayContent(
                trackTitle = track?.title?.ifBlank { "Aura Music" } ?: "Aura Music",
                artistName = track?.artist?.ifBlank { "Tap to listen" } ?: "Tap to listen",
                isPlaying = isPlaying,
                artwork = artwork,
                palette = palette
            )
        }
    }

    @Composable
    private fun QuickPlayContent(
        trackTitle: String,
        artistName: String,
        isPlaying: Boolean,
        artwork: Bitmap?,
        palette: WidgetThemePalette
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(palette.widgetBg))
                .cornerRadius(24.dp)
                .clickable(actionRunCallback<WidgetOpenPlayerAction>())
                .padding(14.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            WidgetArtworkBox(
                artwork = artwork,
                size = 88.dp,
                cornerRadius = 14.dp,
                placeholderBg = palette.artSurfaceBg
            )

            Spacer(modifier = GlanceModifier.width(14.dp))

            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionRunCallback<WidgetOpenPlayerAction>()),
                    verticalAlignment = Alignment.Vertical.Top
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = trackTitle,
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(palette.fgPrimary),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = artistName,
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(palette.fgSecondary),
                                fontSize = 13.sp
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    Image(
                        provider = ImageProvider(R.drawable.ic_aura_notification),
                        contentDescription = "Aura",
                        colorFilter = ColorFilter.tint(ColorProvider(palette.fgPrimary)),
                        modifier = GlanceModifier.size(22.dp)
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                SpotifyTransportRow(
                    isPlaying = isPlaying,
                    palette = palette,
                    compact = false
                )
            }
        }
    }
}

class AuraQuickPlayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuraQuickPlayWidget()
}
