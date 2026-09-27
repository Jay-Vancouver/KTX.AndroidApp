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

    /** Wall-clock time (epoch ms) the server last accepted a position; 0 = never. */
    var lastSentAt: Long
        get() = prefs.getLong(KEY_LAST_SENT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SENT, value).apply()

    companion object {
        private const val KEY_TRACKING = "tracking"
        private const val KEY_PHONE = "phone"
        private const val KEY_URL = "url"
        private const val KEY_LAST_SENT = "last_sent_at"

        /** "(604) 555-1234", "+1 604 555 1234" -> "6045551234"; null unless it is 10 digits. */
        fun normalizePhone(raw: String?): String? {
            var digits = raw.orEmpty().filter { it.isDigit() }
            if (digits.length == 11 && digits.startsWith("1")) digits = digits.substring(1)
            return digits.takeIf { it.length == 10 }
        }

        /** https only; debug builds also accept http so a local test receiver can be used. */
        fun isUsableUrl(url: String?): Boolean {
            val u = url.orEmpty().trim().lowercase()
            return u.startsWith("https://") || (BuildConfig.DEBUG && u.startsWith("http://"))
        }
    }
}
