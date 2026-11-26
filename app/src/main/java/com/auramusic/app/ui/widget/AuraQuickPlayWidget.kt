package com.auramusic.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.auramusic.app.R

class AuraQuickPlayWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        AuraWidgetSupport.hydrateInitialSnapshot(context)

        provideContent {
            currentState<Preferences>()
            val snapshot by AuraWidgetSupport.snapshotFlow.collectAsState()
            QuickPlayContent(snapshot = snapshot)
        }
    }

    @Composable
    private fun QuickPlayContent(snapshot: WidgetSnapshot) {
        val context = LocalContext.current
        val openPlayerAction = AuraWidgetSupport.openPlayerActivityAction(context)
        val palette = snapshot.palette
        val widgetSize = LocalSize.current
        // Extra top & bottom breathing room while keeping cover art flush-aligned with title & controls
        val horizontalPad = 16.dp
        val verticalPad = 24.dp
        val contentHeight = (widgetSize.height - (verticalPad * 2)).coerceIn(84.dp, 100.dp)
        val artWidth = (contentHeight * 1.04f).coerceAtMost((widgetSize.width * 0.36f).coerceAtLeast(88.dp))

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ColorProvider(palette.widgetBg))
                .cornerRadius(24.dp)
                .clickable(openPlayerAction)
                .padding(horizontal = horizontalPad, vertical = verticalPad),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            WidgetArtworkBox(
                artwork = snapshot.artwork,
                width = artWidth,
                height = contentHeight,
                cornerRadius = 15.dp,
                placeholderBg = palette.artSurfaceBg,
                onClick = openPlayerAction
            )

            Spacer(modifier = GlanceModifier.width(16.dp))

            // Exact same height as cover art so Title/Artist aligns flush with top
            // and Transport Controls align flush with bottom of cover image
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(contentHeight)
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(openPlayerAction),
                    verticalAlignment = Alignment.Vertical.Top
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = snapshot.trackTitle,
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Text(
                            text = snapshot.artistName,
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.78f)),
                                fontSize = 13.5.sp
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(10.dp))

                    // Actual Aura Music round app logo (no white tint)
                    Image(
                        provider = ImageProvider(R.drawable.aura_app_icon_round),
                        contentDescription = "Aura Music",
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier
                            .size(34.dp)
                            .cornerRadius(17.dp)
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                SpotifyTransportRow(
                    isPlaying = snapshot.isPlaying,
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
