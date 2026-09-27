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
 * First-run guide: location "always", notifications, no battery restriction, and letting the
 * browser install app updates. "Allow" walks through whatever is missing; each row can also be
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
            if (!state.updateSettingsOpened) openUpdateSettings()
        }
    }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        state = SetupState(this)

        val list = findViewById<ViewGroup>(R.id.items)
        val browser = Browser.label(this) ?: getString(R.string.setup_browser_fallback)
        items = listOf(
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
            Item(
                addRow(list, R.string.setup_updates_title, getString(R.string.setup_updates_desc, browser)),
                isDone = { state.updateSettingsOpened },
                action = ::openUpdateSettings,
            ),
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

    /** The browser downloads the update APK, so it is the one that needs "install unknown apps". */
    private fun openUpdateSettings() {
        state.updateSettingsOpened = true
        val pkg = Browser.packageName(this)
        val intent = if (pkg != null) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$pkg"))
        } else {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        }
        launchSettings(intent)
    }

    private fun launchSettings(intent: Intent) {
        try {
            settingsLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            refresh()
        }
    }
}
