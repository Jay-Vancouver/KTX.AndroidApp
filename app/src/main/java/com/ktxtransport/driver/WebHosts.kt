package com.ktxtransport.driver

import android.net.Uri

/** Which hosts the app treats as its own; everything else opens in the external browser. */
object WebHosts {

    val startUrl: String = BuildConfig.START_URL

    private val startHost: String? = Uri.parse(startUrl).host

    /** withktx.com and its subdomains (driver., pod., www.), plus the start URL host. */
    fun isAppHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase()
        return h == "withktx.com" || h.endsWith(".withktx.com") || h == startHost
    }

    fun isAppUrl(uri: Uri?): Boolean =
        uri != null && uri.scheme == "https" && isAppHost(uri.host)
}
