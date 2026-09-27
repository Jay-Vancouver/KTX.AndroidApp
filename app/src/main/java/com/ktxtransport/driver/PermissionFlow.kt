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
 * skipped when already granted or not in the requested set; a refusal moves on to the next step.
 * Must be created before the activity is started (it registers result launchers).
 */
class PermissionFlow(
    private val activity: AppCompatActivity,
    private val onFinished: () -> Unit,
) {
    enum class Step { LOCATION, BACKGROUND_LOCATION, NOTIFICATIONS, BATTERY }

    private var running = false
    private var steps: Set<Step> = emptySet()

    private val locationLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { next() }

    private val singleLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { next() }

    private val settingsLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { next() }

    // Steps already shown in this run, so a refused one is not asked again in a loop.
    private val asked = mutableSetOf<Step>()

    fun start(steps: Set<Step> = Step.entries.toSet()) {
        if (running) return
        running = true
        this.steps = steps
        asked.clear()
        next()
    }

    private fun wants(step: Step): Boolean = step in steps && step !in asked && asked.add(step)

    @SuppressLint("BatteryLife", "InlinedApi")
    private fun next() {
        val ctx = activity
        when {
            !AppPermissions.hasLocation(ctx) && wants(Step.LOCATION) ->
                locationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                AppPermissions.hasLocation(ctx) && !AppPermissions.hasBackgroundLocation(ctx) &&
                wants(Step.BACKGROUND_LOCATION) ->
                // Android 11+ answers this by opening the app's location settings ("Allow all the time").
                singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            !AppPermissions.hasNotifications(ctx) && wants(Step.NOTIFICATIONS) ->
                singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            !AppPermissions.isBatteryUnrestricted(ctx) && wants(Step.BATTERY) -> {
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
}
