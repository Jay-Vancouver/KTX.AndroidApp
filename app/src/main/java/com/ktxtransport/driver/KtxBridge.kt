package com.ktxtransport.driver

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * `window.KtxAndroidApp` for the driver web pages. The names and shapes are shared with the
 * TMS server (driver pages); change both sides together.
 *
 * Every call is ignored unless the main frame is the driver site (WebHosts.isBridgeUrl).
 * Methods run on the WebView's JavaBridge thread, not the UI thread.
 */
class KtxBridge(private val activity: MainActivity) {

    /** Starts sending positions for this driver at the default cadence (60 s, heartbeat 300 s). */
    @JavascriptInterface
    fun startTracking(phone: String?, url: String?): Boolean = startTracking(phone, url, null)

    /**
     * Starts sending positions for this driver. [options] is a JSON **string** (the bridge cannot take
     * a JS object): `JSON.stringify({interval: 30, heartbeat: 300})`, seconds; interval 10..600,
     * heartbeat 60..3600, missing keys use the defaults. Calling again while tracking applies the new
     * values. False when refused or the arguments are unusable (bad phone, url or JSON).
     */
    @JavascriptInterface
    fun startTracking(phone: String?, url: String?, options: String?): Boolean {
        if (!activity.bridgeAllowed) return false
        val cadence = TrackingState.parseCadence(options) ?: return false
        if (!LocationService.start(activity, phone, url, cadence)) return false
        // Tracking is saved as on; it begins as soon as location is allowed.
        if (!AppPermissions.hasLocation(activity)) activity.runOnUiThread { activity.startPermissionFlow() }
        return true
    }

    @JavascriptInterface
    fun stopTracking() {
        if (!activity.bridgeAllowed) return
        LocationService.stop(activity)
    }

    /**
     * JSON: {"tracking": bool, "lastSentAt": epoch ms | null,
     *        "permission": "always" | "whileInUse" | "denied", "battery": "unrestricted" | "restricted",
     *        "interval": seconds, "heartbeat": seconds}
     * `tracking` is whether the service is actually running now.
     */
    @JavascriptInterface
    fun status(): String {
        if (!activity.bridgeAllowed) return "{}"
        return statusJson(activity)
    }

    /** Walks the driver through location "always", notifications and battery exemption. */
    @JavascriptInterface
    fun requestPermissions() {
        if (!activity.bridgeAllowed) return
        activity.runOnUiThread { activity.startPermissionFlow() }
    }

    @JavascriptInterface
    fun version(): String {
        if (!activity.bridgeAllowed) return ""
        return BuildConfig.VERSION_NAME
    }

    companion object {
        const val NAME = "KtxAndroidApp"

        /** Fired on window after permissions or tracking may have changed; detail = status(). */
        const val STATUS_EVENT = "ktxappstatus"

        fun statusJson(activity: MainActivity): String {
            val state = TrackingState(activity)
            val lastSent = state.lastSentAt
            return JSONObject()
                .put("tracking", LocationService.isRunning)
                .put("lastSentAt", if (lastSent > 0) lastSent else JSONObject.NULL)
                .put("permission", AppPermissions.locationLevel(activity))
                .put("battery", if (AppPermissions.isBatteryUnrestricted(activity)) "unrestricted" else "restricted")
                .put("interval", state.intervalSec)
                .put("heartbeat", state.heartbeatSec)
                .toString()
        }
    }
}
