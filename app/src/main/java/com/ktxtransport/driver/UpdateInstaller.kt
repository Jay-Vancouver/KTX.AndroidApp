package com.ktxtransport.driver

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-app update: download the APK, check it is a newer build of this app signed with the same
 * key, and hand it to PackageInstaller. Android still shows its install confirmation (apps outside
 * Google Play cannot update silently the first time); InstallResultReceiver starts that screen.
 */
object UpdateInstaller {

    private const val TAG = "UpdateInstaller"
    private const val TIMEOUT_MS = 20_000

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * Downloads [url] into the cache. [onProgress] gets 0..100, or -1 when the size is unknown;
     * [onDone] gets the file, or null on failure or cancel. Returns a function that cancels.
     */
    fun download(context: Context, url: String, onProgress: (Int) -> Unit, onDone: (File?) -> Unit): () -> Unit {
        val cancelled = AtomicBoolean(false)
        val file = File(File(context.cacheDir, "update").apply { mkdirs() }, "update.apk")
        executor.execute {
            val ok = fetch(url, file, cancelled) { percent -> main.post { onProgress(percent) } }
            val result = if (ok && !cancelled.get()) file else null
            if (result == null) file.delete()
            main.post { onDone(result) }
        }
        return { cancelled.set(true) }
    }

    private fun fetch(url: String, file: File, cancelled: AtomicBoolean, progress: (Int) -> Unit): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "KTXDriverApp/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode != 200) return false
            val total = conn.contentLengthLong
            var done = 0L
            var lastPercent = -2
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled.get()) return false
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            progress(percent)
                        }
                    }
                }
            }
            total <= 0 || done == total
        } catch (e: IOException) {
            Log.w(TAG, "download failed: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** Same package, same signing key, higher versionCode than what is installed. */
    fun isValidUpdate(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        val installed = pm.getPackageInfo(context.packageName, flags)
        return archive.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed) &&
            signers(archive).isNotEmpty() && signers(archive) == signers(installed)
    }

    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
    }

    /** Starts a PackageInstaller session for [apk]; the result arrives in InstallResultReceiver. */
    fun install(context: Context, apk: File): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Android 12+: no confirmation once this app is the installer of record (after the first in-app update).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        return try {
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                // Mutable: the installer adds the status extras to this intent.
                val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val callback = PendingIntent.getBroadcast(
                    context, sessionId,
                    Intent(context, InstallResultReceiver::class.java),
                    mutable or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                session.commit(callback.intentSender)
            }
            true
        } catch (e: IOException) {
            Log.w(TAG, "install session failed: ${e.message}")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "install not allowed: ${e.message}")
            false
        } finally {
            apk.delete() // copied into the session
        }
    }
}
