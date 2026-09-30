package com.ktxtransport.driver

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * First-run guide: location "always", notifications, no battery restriction, and letting this
 * app install its own updates. "Allow" walks through whatever is missing; each row can also be
 * tapped on its own. Shown at every launch until everything is done, then never again.
 */
class SetupActivity : AppCompatActivity() {

    private class Item(
        val row: View,
        val isDone: () -> Boolean,
        val action: () -> Unit,
    )

    private lateinit var state: SetupState
    private lateinit var items: List<Item>
    private lateinit var allowButton: Button
    private lateinit var laterButton: Button

    private var allowAllRunning = false

    private val flow = PermissionFlow(this) {
        refresh()
        if (allowAllRunning) {
            allowAllRunning = false
            if (UpdateUi.NEEDS_INSTALL_PERMISSION && !packageManager.canRequestPackageInstalls()) openUpdateSettings()
        }
    }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        SystemBars.apply(this, lightBackground = true)
        state = SetupState(this)

        val list = findViewById<ViewGroup>(R.id.items)
        items = listOfNotNull(
            Item(
                addRow(list, R.string.setup_location_title, getString(R.string.setup_location_desc)),
                isDone = { AppPermissions.hasBackgroundLocation(this) },
                action = ::askLocation,
            ),
            Item(
                addRow(list, R.string.setup_notifications_title, getString(R.string.setup_notifications_desc)),
                isDone = { AppPermissions.hasNotifications(this) },
                action = ::askNotifications,
            ),
            Item(
                addRow(list, R.string.setup_battery_title, getString(R.string.setup_battery_desc)),
                isDone = { AppPermissions.isBatteryUnrestricted(this) },
                action = { flow.start(setOf(PermissionFlow.Step.BATTERY)) },
            ),
            // direct flavor only: the app installs its own updates (Google Play updates the play build)
            if (UpdateUi.NEEDS_INSTALL_PERMISSION) {
                Item(
                    addRow(list, R.string.setup_updates_title, getString(R.string.setup_updates_desc)),
                    isDone = { packageManager.canRequestPackageInstalls() },
                    action = ::openUpdateSettings,
                )
            } else {
                null
            },
        )

        allowButton = findViewById(R.id.allowButton)
        allowButton.setOnClickListener {
            if (items.all { it.isDone() }) {
                finish()
            } else {
                allowAll()
            }
        }
        laterButton = findViewById(R.id.laterButton)
        laterButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun addRow(parent: ViewGroup, titleRes: Int, desc: String): View {
        val row = layoutInflater.inflate(R.layout.item_setup, parent, false)
        row.findViewById<TextView>(R.id.title).setText(titleRes)
        row.findViewById<TextView>(R.id.description).text = desc
        parent.addView(row)
        return row
    }

    private fun refresh() {
        for (item in items) {
            val done = item.isDone()
            val status = item.row.findViewById<TextView>(R.id.status)
            status.text = if (done) "✓" else "!"
            status.setTextColor(ContextCompat.getColor(this, if (done) R.color.setup_done else R.color.ktx_red))
            item.row.isClickable = !done
            item.row.setOnClickListener(if (done) null else View.OnClickListener { item.action() })
        }
        val allDone = items.all { it.isDone() }
        if (allDone) state.completed = true
        allowButton.setText(if (allDone) R.string.setup_start else R.string.setup_allow)
        laterButton.visibility = if (allDone) View.GONE else View.VISIBLE
    }

    private fun allowAll() {
        allowAllRunning = true
        if (needsLocationSettings()) {
            // Permanently refused: the dialog cannot come back, only the settings page.
            allowAllRunning = false
            openAppSettings()
            return
        }
        if (!AppPermissions.hasLocation(this)) state.locationAsked = true
        if (!AppPermissions.hasNotifications(this)) state.notificationsAsked = true
        flow.start()
    }

    private fun askLocation() {
        if (needsLocationSettings()) {
            openAppSettings()
        } else {
            state.locationAsked = true
            flow.start(setOf(PermissionFlow.Step.LOCATION, PermissionFlow.Step.BACKGROUND_LOCATION))
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state.notificationsAsked &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            launchSettings(intent)
        } else {
            state.notificationsAsked = true
            flow.start(setOf(PermissionFlow.Step.NOTIFICATIONS))
        }
    }

    /** Asked before and refused with "don't ask again": the system dialog will not appear. */
    private fun needsLocationSettings(): Boolean =
        !AppPermissions.hasLocation(this) && state.locationAsked &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun openAppSettings() {
        launchSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    /** "Install unknown apps" for this app: updates are downloaded and installed in the app. */
    private fun openUpdateSettings() {
        launchSettings(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
    }

    private fun launchSettings(intent: Intent) {
        try {
            settingsLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            refresh()
        }
    }
}
