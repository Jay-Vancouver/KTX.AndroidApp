package com.ktxtransport.driver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * In-app updates for the site-distributed APK (direct flavor): UpdateChecker finds a newer
 * version, UpdateInstaller downloads and installs it. The play flavor has a no-op UpdateUi,
 * because Google Play apps may only be updated by Play.
 * Create it while the activity is being constructed (it registers a result launcher).
 */
class UpdateUi(private val activity: AppCompatActivity) {

    private var updateDialog: AlertDialog? = null
    private var pendingUpdate: UpdateChecker.Update? = null

    /** "Install unknown apps" for this app itself, asked the first time an update is installed. */
    private val installPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val update = pendingUpdate ?: return@registerForActivityResult
            pendingUpdate = null
            if (activity.packageManager.canRequestPackageInstalls()) {
                downloadUpdate(update)
            } else {
                showNotice(R.string.update_need_permission)
            }
        }

    fun checkIfDue() = UpdateChecker.checkIfDue(::showUpdate)

    /** Not forced: "Later" closes it until the next check. */
    private fun showUpdate(update: UpdateChecker.Update) {
        if (activity.isFinishing || activity.isDestroyed || updateDialog?.isShowing == true) return
        val message = update.notes.ifEmpty { activity.getString(R.string.update_message) }
        updateDialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_title, update.version))
            .setMessage(message)
            .setPositiveButton(R.string.update_now) { _, _ -> startUpdate(update) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun startUpdate(update: UpdateChecker.Update) {
        if (activity.packageManager.canRequestPackageInstalls()) {
            downloadUpdate(update)
            return
        }
        pendingUpdate = update
        try {
            installPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
        } catch (_: ActivityNotFoundException) {
            pendingUpdate = null
            showNotice(R.string.update_need_permission)
        }
    }

    private fun downloadUpdate(update: UpdateChecker.Update) {
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val padding = (24 * activity.resources.displayMetrics.density).toInt()
        val content = FrameLayout(activity).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(bar)
        }
        var cancelled = false
        var cancel: (() -> Unit)? = null
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_downloading, update.version))
            .setView(content)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                cancelled = true
                cancel?.invoke()
            }
            .show()

        cancel = UpdateInstaller.download(activity, update.apkUrl,
            onProgress = { percent ->
                if (percent >= 0) {
                    bar.isIndeterminate = false
                    bar.progress = percent
                }
            },
            onDone = { file ->
                if (activity.isDestroyed) return@download
                dialog.dismiss()
                when {
                    cancelled -> file?.delete()
                    file == null -> showNotice(R.string.update_failed)
                    !UpdateInstaller.isValidUpdate(activity, file) -> {
                        file.delete()
                        showNotice(R.string.update_invalid)
                    }
                    !UpdateInstaller.install(activity, file) -> showNotice(R.string.update_install_failed)
                    // else: Android's install confirmation opens via InstallResultReceiver
                }
            },
        )
    }

    private fun showNotice(messageRes: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setMessage(messageRes)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        /** The first-run guide asks for "install unknown apps" (this app installs its own updates). */
        const val NEEDS_INSTALL_PERMISSION = true
    }
}
