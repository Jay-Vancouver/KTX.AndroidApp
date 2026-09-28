package com.ktxtransport.driver

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** The phone's web browser: page downloads must go there, not back into this app. */
object Browser {

    private const val CHROME = "com.android.chrome"

    /**
     * Opens an http(s) URL in the browser. Plain ACTION_VIEW could land back in this app when the
     * URL is on driver.withktx.com (App Links), so the browser package is named explicitly; with
     * no browser found, a chooser that leaves this app out.
     */
    fun open(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        packageName(context)?.let { pkg ->
            try {
                context.startActivity(Intent(intent).setPackage(pkg))
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        val chooser = Intent.createChooser(intent, null)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Default browser package; Chrome when there is no default (chooser) or none resolves. */
    fun packageName(context: Context): String? {
        val pm = context.packageManager
        // A neutral address: driver.withktx.com links resolve to this app itself.
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
        val pkg = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (pkg != null && pkg != "android" && isInstalled(pm, pkg)) return pkg
        return CHROME.takeIf { isInstalled(pm, it) }
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        try {
            pm.getApplicationInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
