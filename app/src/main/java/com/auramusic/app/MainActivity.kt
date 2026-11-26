package com.auramusic.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.auramusic.app.core.integrations.DeepLinkParser
import com.auramusic.app.core.media.AuraMediaService
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.core.notifications.AuraNotificationManager
import com.auramusic.app.ui.AuraViewModel
import com.auramusic.app.ui.components.AuraLaunchSplash
import com.auramusic.app.ui.screens.AuraMainScreen
import com.auramusic.app.ui.theme.AuraMusicTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "com.auramusic.app.EXTRA_OPEN_NOW_PLAYING"
        const val EXTRA_WIDGET_AUTO_PLAY = "com.auramusic.app.EXTRA_WIDGET_AUTO_PLAY"
    }

    private val auraViewModel: AuraViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && auraViewModel.releaseNotificationsEnabled.value) {
            AuraNotificationManager.setupFcmSubscription(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make Android status bar header transparent edge-to-edge (contrast synced by SystemBarEffect)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // Initialize FCM subscription only when release notifications are enabled
        if (auraViewModel.releaseNotificationsEnabled.value) {
            AuraNotificationManager.setupFcmSubscription(applicationContext)
        }

        // Request POST_NOTIFICATIONS runtime permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val openedFromUpdate = isUpdateNotificationIntent(intent)
        val openedFromWidget = intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true

        // Check if opened from an update notification, widget launch, or deep link / share intent
        handleIncomingIntent(intent)

        try {
            val serviceIntent = Intent(this, AuraMediaService::class.java)
            startService(serviceIntent)
        } catch (_: Exception) {
        }

        setContent {
            val liquidGlassEnabled by auraViewModel.liquidGlassEnabled.collectAsState()
            val lyricsStyle by auraViewModel.lyricsStyle.collectAsState()
            val themeMode by auraViewModel.themeMode.collectAsState()
            val themeColorSource by auraViewModel.themeColorSource.collectAsState()
            val customAccentArgb by auraViewModel.customAccentArgb.collectAsState()

            LaunchedEffect(themeMode) {
                AuraApplication.applyThemeMode(themeMode)
            }

            AuraMusicTheme(
                liquidGlassEnabled = liquidGlassEnabled,
                lyricsStyle = lyricsStyle,
                themeMode = themeMode,
                colorSource = themeColorSource,
                customAccentArgb = customAccentArgb,
            ) {
                var isSplashActive by rememberSaveable { mutableStateOf(!openedFromUpdate && !openedFromWidget) }

                Box(modifier = Modifier.fillMaxSize()) {
                    AuraMainScreen(viewModel = auraViewModel)

                    AnimatedVisibility(
                        visible = isSplashActive,
                        enter = fadeIn(),
                        exit = fadeOut(tween(380, easing = FastOutSlowInEasing))
                    ) {
                        AuraLaunchSplash(
                            onDismiss = { isSplashActive = false }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AuraPlayerManager.isVideoMode.value &&
            AuraPlayerManager.isPlaying.value
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
    }

    private fun isUpdateNotificationIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return intent.getBooleanExtra(AuraNotificationManager.EXTRA_OPEN_UPDATE, false) ||
            intent.action == "com.auramusic.app.OPEN_UPDATE" ||
            intent.getStringExtra("type") == "release" ||
            intent.getStringExtra("type") == "app-update" ||
            intent.hasExtra("update_version")
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) {
            val autoPlay = intent.getBooleanExtra(EXTRA_WIDGET_AUTO_PLAY, true)
            intent.removeExtra(EXTRA_OPEN_NOW_PLAYING)
            intent.removeExtra(EXTRA_WIDGET_AUTO_PLAY)
            auraViewModel.openNowPlayingFromWidget(autoPlay = autoPlay)
        }

        if (intent?.getBooleanExtra("test_update", false) == true) {
            auraViewModel.triggerTestUpdateModal()
        } else if (isUpdateNotificationIntent(intent)) {
            auraViewModel.checkForUpdates(force = true)
        }

        val rawLink: String? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> if (intent.type?.startsWith("text/") == true) {
                intent.getStringExtra(Intent.EXTRA_TEXT)
            } else {
                null
            }
            else -> null
        }
        if (!rawLink.isNullOrBlank()) {
            DeepLinkParser.parse(rawLink)?.let { deepLink ->
                auraViewModel.handleDeepLink(deepLink)
            }
        }
    }
}
