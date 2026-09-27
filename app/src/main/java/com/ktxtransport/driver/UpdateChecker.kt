package com.ktxtransport.driver

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Reads version.json ({"version":"1.0.3","apk":"https://.../ktx-driver-1.0.3.apk","notes":"..."})
 * and reports a newer version. Checked when the app comes to the foreground, at most every
 * 12 hours per process (drivers leave the app open in the background for days).
 */
object UpdateChecker {

    data class Update(val version: String, val apkUrl: String, val notes: String)

    private const val TAG = "UpdateChecker"
    private const val TIMEOUT_MS = 10_000
    private const val MIN_INTERVAL_MS = 12 * 60 * 60 * 1000L

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var lastCheckAt = 0L // SystemClock.elapsedRealtime(); 0 = not yet in this process

    /** Debug builds only: a local version.json for testing (set with the debug_version_url extra). */
    var urlOverride: String? = null
        set(value) {
            field = value
            lastCheckAt = 0L
        }

    fun checkIfDue(onUpdate: (Update) -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (lastCheckAt != 0L && now - lastCheckAt < MIN_INTERVAL_MS) return
        lastCheckAt = now
        val url = urlOverride ?: BuildConfig.VERSION_URL
        executor.execute {
            val update = fetch(url) ?: return@execute
            main.post { onUpdate(update) }
        }
    }

    private fun fetch(url: String): Update? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("User-Agent", "KTXDriverApp/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.optString("version").trim()
            val apk = json.optString("apk").trim()
            val notes = json.optString("notes").trim()
            if (!isNewer(version, BuildConfig.VERSION_NAME) || !isUsableApkUrl(apk)) return null
            Update(version, apk, notes)
        } catch (e: IOException) {
            null // offline or server down: try again next time
        } catch (e: JSONException) {
            Log.w(TAG, "version.json is not valid JSON")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun isUsableApkUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.startsWith("https://") || (BuildConfig.DEBUG && u.startsWith("http://"))
    }

    /** "1.0.10" > "1.0.9"; non-numeric tails ("1.0.3-beta") compare by their leading digits. */
    fun isNewer(remote: String, current: String): Boolean {
        val r = parts(remote)
        val c = parts(current)
        if (r.isEmpty()) return false
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            .takeIf { version.isNotBlank() }.orEmpty()
}
