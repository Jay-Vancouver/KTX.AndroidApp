package com.ktxtransport.driver

import android.content.Context

/** Tracking settings and progress, kept in SharedPreferences so they survive restarts and reboots. */
class TrackingState(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("tracking", Context.MODE_PRIVATE)

    /** Whether the web page asked for tracking (startTracking) and has not stopped it since. */
    var tracking: Boolean
        get() = prefs.getBoolean(KEY_TRACKING, false)
        set(value) = prefs.edit().putBoolean(KEY_TRACKING, value).apply()

    /** Driver phone, 10 digits: the `id` field the server matches pickups by. */
    var phone: String?
        get() = prefs.getString(KEY_PHONE, null)
        set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

    /** Where positions are posted; given by the web page, never hard-coded. */
    var url: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    /** Seconds between reports while tracking. */
    var intervalSec: Int
        get() = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SEC)
        set(value) = prefs.edit().putInt(KEY_INTERVAL, value).apply()

    /** At least one report this often (seconds) even when no new fix arrives. */
    var heartbeatSec: Int
        get() = prefs.getInt(KEY_HEARTBEAT, DEFAULT_HEARTBEAT_SEC)
        set(value) = prefs.edit().putInt(KEY_HEARTBEAT, value).apply()

    /** Wall-clock time (epoch ms) the server last accepted a position; 0 = never. */
    var lastSentAt: Long
        get() = prefs.getLong(KEY_LAST_SENT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SENT, value).apply()

    /** Report cadence set by the web page through startTracking's options. */
    data class Cadence(val intervalSec: Int, val heartbeatSec: Int) {
        companion object {
            val DEFAULT = Cadence(DEFAULT_INTERVAL_SEC, DEFAULT_HEARTBEAT_SEC)
        }
    }

    companion object {
        private const val KEY_TRACKING = "tracking"
        private const val KEY_PHONE = "phone"
        private const val KEY_URL = "url"
        private const val KEY_LAST_SENT = "last_sent_at"
        private const val KEY_INTERVAL = "interval_sec"
        private const val KEY_HEARTBEAT = "heartbeat_sec"

        // Defaults match the Traccar Client setup (interval=60, heartbeat=300).
        const val DEFAULT_INTERVAL_SEC = 60
        const val DEFAULT_HEARTBEAT_SEC = 300
        private val INTERVAL_RANGE = 10..600
        private val HEARTBEAT_RANGE = 60..3600

        /**
         * startTracking options, a JSON string such as `{"interval":30,"heartbeat":300}` (seconds).
         * Null/blank → defaults; a missing or non-numeric key → its default; out of range → clamped;
         * heartbeat is never shorter than interval. Null result = not valid JSON.
         */
        fun parseCadence(options: String?): Cadence? {
            if (options.isNullOrBlank() || options == "undefined" || options == "null") return Cadence.DEFAULT
            val json = try {
                org.json.JSONObject(options)
            } catch (_: org.json.JSONException) {
                return null
            }
            val interval = json.optInt("interval", DEFAULT_INTERVAL_SEC).coerceIn(INTERVAL_RANGE)
            val heartbeat = json.optInt("heartbeat", DEFAULT_HEARTBEAT_SEC).coerceIn(HEARTBEAT_RANGE)
            return Cadence(interval, maxOf(heartbeat, interval))
        }

        /** "(604) 555-1234", "+1 604 555 1234" -> "6045551234"; null unless it is 10 digits. */
        fun normalizePhone(raw: String?): String? {
            var digits = raw.orEmpty().filter { it.isDigit() }
            if (digits.length == 11 && digits.startsWith("1")) digits = digits.substring(1)
            return digits.takeIf { it.length == 10 }
        }

        /**
         * https on a withktx.com host (www.withktx.com/gps today, gps.withktx.com later) or in the
         * domain of an administrator-set site address (WebHosts.isTrackingHost).
         * Debug builds also accept http://127.0.0.1 / localhost for a local test receiver.
         */
        fun isUsableUrl(url: String?): Boolean {
            val uri = android.net.Uri.parse(url.orEmpty().trim())
            val host = uri.host?.lowercase()
            return when (uri.scheme?.lowercase()) {
                "https" -> WebHosts.isTrackingHost(host)
                "http" -> BuildConfig.DEBUG && (host == "127.0.0.1" || host == "localhost")
                else -> false
            }
        }
    }
}
