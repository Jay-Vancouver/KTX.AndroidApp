package com.ktxtransport.driver

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * App status and the server address, opened by swiping left→right across the top of the main
 * screen. Status is open to everyone; changing the address needs the admin PIN (KTX_ADMIN_PIN).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var statusList: LinearLayout
    private lateinit var serverText: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val flow = PermissionFlow(this) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        SystemBars.apply(this, lightBackground = true)
        statusList = findViewById(R.id.statusList)
        serverText = findViewById(R.id.serverUrl)
        findViewById<Button>(R.id.permissionsButton).setOnClickListener { flow.start() }
        findViewById<Button>(R.id.changeServerButton).setOnClickListener { askPin() }
        findViewById<Button>(R.id.closeButton).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val tracking = TrackingState(this)
        val lastSent = tracking.lastSentAt
        val rows = listOf(
            R.string.settings_tracking to getString(if (LocationService.isRunning) R.string.settings_on else R.string.settings_off),
            R.string.settings_last_sent to
                if (lastSent > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(lastSent))
                else getString(R.string.settings_never),
            R.string.settings_location to getString(
                when (AppPermissions.locationLevel(this)) {
                    "always" -> R.string.settings_perm_always
                    "whileInUse" -> R.string.settings_perm_while_in_use
                    else -> R.string.settings_perm_denied
                }
            ),
            R.string.settings_notifications to getString(
                if (AppPermissions.hasNotifications(this)) R.string.settings_allowed else R.string.settings_not_allowed
            ),
            R.string.settings_battery to getString(
                if (AppPermissions.isBatteryUnrestricted(this)) R.string.settings_battery_unrestricted
                else R.string.settings_battery_restricted
            ),
            R.string.settings_cadence to getString(R.string.settings_cadence_value, tracking.intervalSec, tracking.heartbeatSec),
            R.string.settings_version to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
        statusList.removeAllViews()
        for ((label, value) in rows) statusList.addView(row(getString(label), value))

        val custom = ServerConfig.startUrlOverride != null
        serverText.text = getString(
            R.string.settings_server_value, ServerConfig.startUrl,
            getString(if (custom) R.string.settings_server_custom else R.string.settings_server_default),
        )
    }

    private fun row(label: String, value: String): LinearLayout {
        val pad = dp(6)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, pad, 0, pad)
            addView(TextView(this@SettingsActivity).apply {
                text = label
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = value
                textSize = 15f
                gravity = Gravity.END
                setTextColor(getColor(R.color.ktx_blue))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    // --- Server address (admin only) ---

    private fun askPin() {
        val input = EditText(this).apply {
            // Single line first: setSingleLine replaces the transformation, which would unmask the PIN.
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_pin_title)
            .setView(padded(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (sha256(input.text.toString().trim()) == BuildConfig.ADMIN_PIN_SHA256) {
                    editServer(ServerConfig.startUrl)
                } else {
                    Toast.makeText(this, R.string.settings_pin_wrong, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editServer(current: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            setText(current)
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_server)
            .setView(padded(input))
            .setPositiveButton(R.string.settings_save, null) // set below so an invalid entry keeps the dialog open
            .setNeutralButton(R.string.settings_reset_default) { _, _ -> apply(null) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = input.text.toString().trim()
            if (!ServerConfig.isValidStartUrl(url)) {
                input.error = getString(R.string.settings_server_invalid)
                return@setOnClickListener
            }
            dialog.dismiss()
            checkThenSave(url)
        }
    }

    /** Saves when the address answers; otherwise asks first (the server may be down right now). */
    private fun checkThenSave(url: String) {
        val checking = AlertDialog.Builder(this)
            .setMessage(R.string.settings_server_checking)
            .setCancelable(false)
            .show()
        executor.execute {
            val reachable = isReachable(url)
            main.post {
                if (isFinishing || isDestroyed) return@post
                checking.dismiss()
                if (reachable) {
                    apply(url)
                } else {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.settings_server_unreachable, url))
                        .setPositiveButton(R.string.settings_save_anyway) { _, _ -> apply(url) }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> editServer(url) }
                        .show()
                }
            }
        }
    }

    private fun isReachable(url: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "KTXDriverApp/${BuildConfig.VERSION_NAME}")
            }
            conn.responseCode in 200..499
        } catch (_: IOException) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** [url] null = back to the built-in address. MainActivity reloads when it sees the change. */
    private fun apply(url: String?) {
        ServerConfig.startUrlOverride = url?.takeIf { it != ServerConfig.defaultStartUrl }
        UpdateChecker.reset()
        Toast.makeText(this, R.string.settings_server_saved, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun padded(view: android.view.View) = FrameLayout(this).apply {
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(view)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
