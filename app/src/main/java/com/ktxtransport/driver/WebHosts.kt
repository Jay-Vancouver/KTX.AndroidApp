package com.ktxtransport.driver

import android.net.Uri

/**
 * Which hosts the app treats as its own; everything else opens in the external browser.
 * Besides withktx.com this follows the driver site address in ServerConfig, so an address set by
 * the administrator keeps working (in-app navigation, bridge, tracking URL).
 */
object WebHosts {

    val startUrl: String
        get() = ServerConfig.startUrl

    private val defaultStartHost: String? = Uri.parse(ServerConfig.defaultStartUrl).host?.lowercase()

    private fun startHost(): String? = Uri.parse(startUrl).host?.lowercase()

    /** withktx.com and its subdomains (driver., pod., www.), plus the built-in and the configured start host. */
    fun isAppHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase()
        return h == "withktx.com" || h.endsWith(".withktx.com") || h == defaultStartHost || h == startHost()
    }

    fun isAppUrl(uri: Uri?): Boolean =
        uri != null && isPageScheme(uri.scheme) && isAppHost(uri.host)

    /**
     * Pages allowed to use the KtxAndroidApp bridge: the driver site only.
     * driver.withktx.com redirects to www.withktx.com/driver/, so both count; a configured start
     * address counts on its own host under its own path (e.g. /driver/).
     */
    fun isBridgeUrl(url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path.orEmpty()
        if (uri.scheme == "https" &&
            (host == "driver.withktx.com" || (host == "www.withktx.com" && underPath(path, "/driver")))
        ) {
            return true
        }
        if (ServerConfig.startUrlOverride == null || !isPageScheme(uri.scheme)) return false
        val start = Uri.parse(startUrl)
        return host == start.host?.lowercase() && uri.port == start.port &&
            underPath(path, start.path.orEmpty().trimEnd('/'))
    }

    /**
     * Hosts a tracking URL may point at: our hosts, or another host in the configured site's domain
     * (the site moved to example.com → gps.example.com is fine).
     */
    fun isTrackingHost(host: String?): Boolean {
        if (isAppHost(host)) return true
        val h = host?.lowercase() ?: return false
        val domain = siteDomain(startHost()) ?: return false
        return h == domain || h.endsWith(".$domain")
    }

    /** "tms.ktxtransport.com" → "ktxtransport.com"; null for IP addresses and single labels. */
    private fun siteDomain(host: String?): String? {
        if (host == null || host.all { it.isDigit() || it == '.' || it == ':' }) return null
        val labels = host.split('.')
        return if (labels.size >= 2) labels.takeLast(2).joinToString(".") else null
    }

    private fun underPath(path: String, prefix: String): Boolean =
        prefix.isEmpty() || path == prefix || path.startsWith("$prefix/")

    private fun isPageScheme(scheme: String?): Boolean =
        scheme == "https" || (BuildConfig.DEBUG && scheme == "http")
}
