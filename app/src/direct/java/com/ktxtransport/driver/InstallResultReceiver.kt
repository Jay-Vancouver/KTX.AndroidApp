package com.ktxtransport.driver

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast
import androidx.core.content.IntentCompat

/** PackageInstaller results for an in-app update (UpdateInstaller.install). */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android's "Update this app?" screen.
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "no install confirmation screen")
                }
            }
            // On success this process is replaced; BootReceiver (MY_PACKAGE_REPLACED) resumes tracking.
            PackageInstaller.STATUS_SUCCESS -> Unit
            // The driver tapped Cancel.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                Log.w(TAG, "update failed: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private companion object {
        const val TAG = "InstallResultReceiver"
    }
}
