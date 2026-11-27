package com.auramusic.app.ui.widget

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.auramusic.app.MainActivity

/**
 * Lightweight invisible trampoline used by Aura home-screen widgets to open Now Playing
 * cleanly without triggering Android 12+ / HyperOS widget expand-and-collapse morph animations.
 */
class AuraWidgetLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoPlay = intent?.getBooleanExtra(MainActivity.EXTRA_WIDGET_AUTO_PLAY, false) == true
        val targetIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_FROM_WIDGET
            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
            putExtra(MainActivity.EXTRA_WIDGET_AUTO_PLAY, autoPlay)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(targetIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        finish()
    }
}
