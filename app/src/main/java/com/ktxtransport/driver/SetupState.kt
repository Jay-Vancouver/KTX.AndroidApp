package com.ktxtransport.driver

import android.content.Context

/** First-run guide progress. Once completed the guide is never shown again. */
class SetupState(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("setup", Context.MODE_PRIVATE)

    var completed: Boolean
        get() = prefs.getBoolean("completed", false)
        set(value) = prefs.edit().putBoolean("completed", value).apply()

    /** The system dialog was shown once; after that a refusal may be permanent (settings only). */
    var locationAsked: Boolean
        get() = prefs.getBoolean("location_asked", false)
        set(value) = prefs.edit().putBoolean("location_asked", value).apply()

    var notificationsAsked: Boolean
        get() = prefs.getBoolean("notifications_asked", false)
        set(value) = prefs.edit().putBoolean("notifications_asked", value).apply()
}
