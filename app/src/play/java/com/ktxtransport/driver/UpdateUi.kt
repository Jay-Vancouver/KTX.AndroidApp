package com.ktxtransport.driver

import androidx.appcompat.app.AppCompatActivity

/**
 * Google Play build: Play updates the app, and Play policy forbids an app updating itself,
 * so there is no update check, download or install here (see the direct flavor's UpdateUi).
 */
@Suppress("UNUSED_PARAMETER")
class UpdateUi(activity: AppCompatActivity) {

    fun checkIfDue() = Unit

    companion object {
        /** No "install unknown apps" step in the first-run guide. */
        const val NEEDS_INSTALL_PERMISSION = false
    }
}
