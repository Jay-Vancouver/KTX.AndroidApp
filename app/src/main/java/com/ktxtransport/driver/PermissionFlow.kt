package com.ktxtransport.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Asks, in order, for what tracking needs: location, location "always", notifications
 * (the tracking notification), and the battery-optimization exemption. Each step is
 * skipped when already granted or not in the requested set; a refusal moves on to the next step.
 *
 * Before the location dialogs it shows a disclosure of background location collection
 * (required by Google Play before requesting location; shown in both builds).
 * Must be created before the activity is started (it registers result launchers).
 */
class PermissionFlow(
    private val activity: AppCompatActivity,
    private val onFinished: () -> Unit,
) {
    enum class Step { LOCATION, BACKGROUND_LOCATION, NOTIFICATIONS, BATTERY }

    private var running = false
    private var steps: Set<Step> = emptySet()
    private var disclosureAccepted = false

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
        disclosureAccepted = false
        next()
    }

    private fun wants(step: Step): Boolean = step in steps && step !in asked && asked.add(step)

    private fun needsLocation(): Boolean = !AppPermissions.hasLocation(activity)

    private fun needsBackground(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            AppPermissions.hasLocation(activity) && !AppPermissions.hasBackgroundLocation(activity)

    @SuppressLint("BatteryLife", "InlinedApi")
    private fun next() {
        val ctx = activity
        when {
            // The disclosure comes first, once per run, whenever a location dialog is about to appear.
            !disclosureAccepted &&
                ((needsLocation() && Step.LOCATION in steps && Step.LOCATION !in asked) ||
                    (needsBackground() && Step.BACKGROUND_LOCATION in steps && Step.BACKGROUND_LOCATION !in asked)) ->
                showLocationDisclosure()

            needsLocation() && wants(Step.LOCATION) ->
                locationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )

            needsBackground() && wants(Step.BACKGROUND_LOCATION) ->
                // Android 11+ answers this by opening the app's location settings ("Allow all the time").
                singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            !AppPermissions.hasNotifications(ctx) && wants(Step.NOTIFICATIONS) ->
                singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            !AppPermissions.isBatteryUnrestricted(ctx) && wants(Step.BATTERY) ->
                if (BuildConfig.DIRECT_BATTERY_REQUEST) requestBatteryExemption() else showBatterySettingsGuide()

            else -> {
                running = false
                onFinished()
            }
        }
    }

    private fun showLocationDisclosure() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.disclosure_title)
            .setMessage(R.string.disclosure_message)
            .setCancelable(false)
            .setPositiveButton(R.string.disclosure_accept) { _, _ ->
                disclosureAccepted = true
                next()
            }
            .setNegativeButton(R.string.disclosure_decline) { _, _ ->
                // Declined: no location dialogs in this run.
                asked.add(Step.LOCATION)
                asked.add(Step.BACKGROUND_LOCATION)
                next()
            }
            .show()
    }

    /** direct flavor: the system "stop optimizing battery usage?" dialog. */
    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${activity.packageName}"))
        launchSettings(intent)
    }

    /** play flavor: no direct request; the app's settings page, where Battery → Unrestricted is. */
    private fun showBatterySettingsGuide() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.battery_guide_title)
            .setMessage(R.string.battery_guide_message)
            .setCancelable(false)
            .setPositiveButton(R.string.battery_guide_open) { _, _ ->
                launchSettings(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> next() }
            .show()
    }

    private fun launchSettings(intent: Intent) {
        try {
            settingsLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            next()
        }
    }
}
