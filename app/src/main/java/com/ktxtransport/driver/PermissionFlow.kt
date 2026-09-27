package com.ktxtransport.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Asks, in order, for what tracking needs: location, location "always", notifications
 * (the tracking notification), and the battery-optimization exemption. Each step is
 * skipped when already granted; a refusal moves on to the next step.
 * Must be created before the activity is started (it registers result launchers).
 */
class PermissionFlow(
    private val activity: AppCompatActivity,
    private val onFinished: () -> Unit,
) {
    private var running = false

    private val locationLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { next() }

    private val singleLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { next() }

    private val settingsLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { next() }

    // Steps already shown in this run, so a refused one is not asked again in a loop.
    private val asked = mutableSetOf<String>()

    fun start() {
        if (running) return
        running = true
        asked.clear()
        next()
    }

    @SuppressLint("BatteryLife", "InlinedApi")
    private fun next() {
        val ctx = activity
        when {
            !AppPermissions.hasLocation(ctx) && asked.add(STEP_LOCATION) ->
                locationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                AppPermissions.hasLocation(ctx) && !AppPermissions.hasBackgroundLocation(ctx) &&
                asked.add(STEP_BACKGROUND) ->
                // Android 11+ answers this by opening the app's location settings ("Allow all the time").
                singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            !AppPermissions.hasNotifications(ctx) && asked.add(STEP_NOTIFICATIONS) ->
                singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            !AppPermissions.isBatteryUnrestricted(ctx) && asked.add(STEP_BATTERY) -> {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${ctx.packageName}"))
                try {
                    settingsLauncher.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    next()
                }
            }

            else -> {
                running = false
                onFinished()
            }
        }
    }

    private companion object {
        const val STEP_LOCATION = "location"
        const val STEP_BACKGROUND = "background"
        const val STEP_NOTIFICATIONS = "notifications"
        const val STEP_BATTERY = "battery"
    }
}
