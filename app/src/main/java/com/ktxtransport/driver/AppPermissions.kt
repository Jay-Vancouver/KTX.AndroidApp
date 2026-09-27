package com.ktxtransport.driver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** What the location service needs, as the web page sees it in status(). */
object AppPermissions {

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(context: Context): Boolean =
        has(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            has(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Android 10+ separates "while in use" from "always"; before that any grant is "always". */
    fun hasBackgroundLocation(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            has(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            hasLocation(context)
        }

    /** "always" | "whileInUse" | "denied" */
    fun locationLevel(context: Context): String = when {
        !hasLocation(context) -> "denied"
        hasBackgroundLocation(context) -> "always"
        else -> "whileInUse"
    }

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            has(context, Manifest.permission.POST_NOTIFICATIONS)

    fun isBatteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
}
