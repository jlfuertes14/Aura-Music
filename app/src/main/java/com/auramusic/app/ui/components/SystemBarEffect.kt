package com.auramusic.app.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.auramusic.app.ui.theme.LocalIsDarkTheme

private fun applySystemBarAppearance(win: Window, isDarkTheme: Boolean) {
    WindowCompat.getInsetsController(win, win.decorView).apply {
        // Light status/nav bar flags mean DARK icons on a LIGHT background.
        isAppearanceLightStatusBars = !isDarkTheme
        isAppearanceLightNavigationBars = !isDarkTheme
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        win.isStatusBarContrastEnforced = false
        win.isNavigationBarContrastEnforced = false
    }
}

private fun resolveActivityWindow(view: View): Window? {
    var context = view.context
    while (context is ContextWrapper) {
        if (context is Activity) {
            return context.window
        }
        context = context.baseContext
    }
    return null
}

private fun resolveDialogWindow(view: View): Window? {
    var parent = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) {
            return parent.window
        }
        parent = parent.parent
    }
    return null
}

/**
 * Syncs system status bar and navigation bar icon contrast with [isDarkTheme]
 * (defaults to [LocalIsDarkTheme.current]):
 * - Dark theme -> light (white) system bar icons.
 * - Light theme -> dark system bar icons.
 */
@Composable
fun SystemBarEffect(
    isDarkTheme: Boolean = LocalIsDarkTheme.current
) {
    val view = LocalView.current

    DisposableEffect(view, isDarkTheme) {
        val dialogWindow = resolveDialogWindow(view)
        val activityWindow = resolveActivityWindow(view)

        dialogWindow?.let { applySystemBarAppearance(it, isDarkTheme) }
        activityWindow?.let { applySystemBarAppearance(it, isDarkTheme) }

        onDispose {
            activityWindow?.let { applySystemBarAppearance(it, isDarkTheme) }
        }
    }
}

/**
 * Ensures dark modals and bottom sheets keep bright white system bar icons while open,
 * and restores the host Activity window's contrast to [LocalIsDarkTheme.current] on dispose.
 */
@Composable
fun ForceWhiteSystemBarsEffect() {
    val view = LocalView.current
    val appIsDark = LocalIsDarkTheme.current

    DisposableEffect(view, appIsDark) {
        val dialogWindow = resolveDialogWindow(view)
        val activityWindow = resolveActivityWindow(view)

        dialogWindow?.let { applySystemBarAppearance(it, isDarkTheme = true) }
        activityWindow?.let { applySystemBarAppearance(it, isDarkTheme = true) }

        onDispose {
            activityWindow?.let { applySystemBarAppearance(it, isDarkTheme = appIsDark) }
        }
    }
}
