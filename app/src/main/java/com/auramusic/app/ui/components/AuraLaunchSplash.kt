package com.auramusic.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.auramusic.app.R
import com.auramusic.app.ui.theme.LocalAuraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clean, flat launcher splash animation centered on the large circular app icon.
 * Initially, the circle is flat and empty with the iconic #353A4D fill.
 * The Aura Bunny mascot peeks inside the circle with organic spring physics,
 * settles into its exact app icon position, and smoothly transitions into the app.
 */
@Composable
fun AuraLaunchSplash(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val auraColors = LocalAuraColors.current
    val splashBackground = if (auraColors.isDark) Color(0xFF0D0E13) else auraColors.background
    // 0f = mascot tucked below circle out of view; 1f = settled in exact icon position
    val peekProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Small initial moment so the user sees the clean empty flat circle (120ms)
        delay(120)

        // 1. Fluid peeking entrance: mascot pops up inside the circle with organic spring bounce
        launch {
            peekProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }

        // 2. Settle briefly for delight, then seamlessly proceed
        delay(900)
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(splashBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // Flat, borderless large circular icon badge (200.dp)
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(Color(0xFF353A4D)),
            contentAlignment = Alignment.Center
        ) {
            // The Bunny Mascot peeking inside the circle
            // At progress = 0: tucked below/left outside the circle clip
            // At progress = 1: perfectly in rest position matching Candidate A1 app icon
            Image(
                painter = painterResource(id = R.drawable.aura_bunny_mascot),
                contentDescription = "Aura Bunny Mascot",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = peekProgress.value
                        translationX = (1f - progress) * -130.dp.toPx()
                        translationY = (1f - progress) * 165.dp.toPx()
                        rotationZ = (1f - progress) * -12f
                        scaleX = 0.90f + 0.10f * progress
                        scaleY = 0.90f + 0.10f * progress
                        alpha = (progress * 1.5f).coerceIn(0f, 1f)
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}
