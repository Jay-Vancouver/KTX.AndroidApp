package com.ktxtransport.driver

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * The driver site address. Normally the built-in START_URL; an administrator can override it in
 * SettingsActivity (behind the admin PIN) when that server cannot be reached. The update check
 * follows the same host (/app/version.json); the tracking URL still comes from startTracking.
 * Initialised by KtxApp so the location service can read it without an activity.
 */
object ServerConfig {

    private const val KEY_START_URL = "start_url"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("server", Context.MODE_PRIVATE)
    }

    val defaultStartUrl: String = BuildConfig.START_URL

    /** Administrator's address, or null for the built-in one. */
    var startUrlOverride: String?
        get() = prefs.getString(KEY_START_URL, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_START_URL) else putString(KEY_START_URL, value.trim())
        }.apply()

    val startUrl: String
        get() = startUrlOverride ?: defaultStartUrl

    val versionUrl: String
        get() = startUrlOverride?.let { origin(it) + "/app/version.json" } ?: BuildConfig.VERSION_URL

    /** https with a host; debug builds also accept http (local test servers). */
    fun isValidStartUrl(url: String?): Boolean {
        val uri = Uri.parse(url.orEmpty().trim())
        val scheme = uri.scheme?.lowercase()
        return !uri.host.isNullOrEmpty() && (scheme == "https" || (BuildConfig.DEBUG && scheme == "http"))
    }

    /** "https://host[:port]" of [url]. */
    fun origin(url: String): String {
        val uri = Uri.parse(url)
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port"
    }
}
