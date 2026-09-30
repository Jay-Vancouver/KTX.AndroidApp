package com.ktxtransport.driver

import android.app.Activity
import androidx.core.view.WindowCompat

/**
 * From targetSdk 35 Android draws apps edge-to-edge: the status and navigation bars are
 * transparent over the app, which paints them itself (the layouts' fitsSystemWindows padding
 * shows the root background there). The bar icons must then contrast with that background.
 */
object SystemBars {

    /** [lightBackground] = the root behind the bars is light, so the icons are dark. */
    fun apply(activity: Activity, lightBackground: Boolean) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}
