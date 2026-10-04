package com.ktxtransport.driver

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Posts queued positions in order on one background thread (OsmAnd protocol, form body).
 * The server stores a repeated (id, timestamp) once, so re-sending after an unclear failure is safe.
 *
 * Watchdog (2026-10-03): on a real phone the sender thread once blocked inside a request and
 * nothing was sent for two hours until the app was restarted. Every new request now checks how
 * long the current send has been running; past [STUCK_MS] it drops that connection and starts a
 * fresh sender thread. The old thread, if it ever wakes up, sees it was replaced and stops.
 */
object FixUploader {

    private const val TAG = "FixUploader"
    private const val TIMEOUT_MS = 15_000
    private const val STUCK_MS = 60_000L

    private val lock = Any()
    private var executor: ExecutorService = newExecutor()
    private var generation = 0 // bumped when a stuck sender is replaced

    @Volatile private var busySince = 0L // elapsedRealtime when the running flush started; 0 = idle
    @Volatile private var activeConn: HttpURLConnection? = null

    private fun newExecutor(): ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "FixUploader").apply { isDaemon = true } }

    fun enqueue(context: Context, url: String, body: String) {
        val app = context.applicationContext
        submit(app) { gen ->
            FixQueue.get(app).add(url, body)
            flushNow(app, gen)
        }
    }

    fun flush(context: Context) {
        val app = context.applicationContext
        submit(app) { gen -> flushNow(app, gen) }
    }

    private fun submit(context: Context, task: (Int) -> Unit) {
        synchronized(lock) {
            val since = busySince
            if (since != 0L && SystemClock.elapsedRealtime() - since > STUCK_MS) {
                val secs = (SystemClock.elapsedRealtime() - since) / 1000
                Log.w(TAG, "send stuck for ${secs}s; dropping it and starting a new sender")
                TrackingState(context).recordSendError("stuck ${secs}s, sender restarted")
                activeConn?.disconnect()
                executor.shutdownNow()
                executor = newExecutor()
                generation++
                busySince = 0L
            }
            val gen = generation
            executor.execute {
                busySince = SystemClock.elapsedRealtime()
                try {
                    task(gen)
                } finally {
                    synchronized(lock) { if (gen == generation) busySince = 0L }
                }
            }
        }
    }

    private fun isCurrent(gen: Int): Boolean = synchronized(lock) { gen == generation }

    /** Sends until the queue is empty or a send fails (then waits for the next trigger). */
    private fun flushNow(context: Context, gen: Int) {
        val queue = FixQueue.get(context)
        val state = TrackingState(context)
        while (isCurrent(gen)) {
            val batch = queue.oldest(50)
            if (batch.isEmpty()) return
            for (fix in batch) {
                if (!isCurrent(gen)) return // replaced by the watchdog while this thread was blocked
                when (val result = post(fix.url, fix.body)) {
                    is Result.Sent -> {
                        queue.remove(fix.id)
                        state.lastSentAt = System.currentTimeMillis()
                        state.recordSendError(null)
                    }
                    is Result.Rejected -> {
                        queue.remove(fix.id)
                        Log.w(TAG, "server rejected a fix (${result.reason}); dropped")
                        state.recordSendError(result.reason)
                    }
                    is Result.Retry -> {
                        Log.w(TAG, "send failed (${result.reason}); ${queue.count()} waiting, will retry")
                        state.recordSendError(result.reason)
                        return
                    }
                }
            }
        }
    }

    private sealed class Result {
        object Sent : Result()
        class Rejected(val reason: String) : Result()
        class Retry(val reason: String) : Result()
    }

    private fun post(url: String, body: String): Result {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "KTXDriverApp/${BuildConfig.VERSION_NAME}")
            }
            activeConn = conn
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            when {
                code in 200..299 -> Result.Sent
                // 4xx other than timeout / rate limit: the server will never take this one.
                code in 400..499 && code != 408 && code != 429 -> Result.Rejected("HTTP $code")
                else -> Result.Retry("HTTP $code") // 3xx (e.g. a redirect), 408, 429, 5xx
            }
        } catch (e: java.net.MalformedURLException) {
            Result.Rejected("bad url $url")
        } catch (e: IOException) {
            Result.Retry("${e.javaClass.simpleName}: ${e.message}")
        } catch (e: IllegalArgumentException) {
            Result.Rejected("bad url $url")
        } finally {
            activeConn = null
            conn?.disconnect()
        }
    }
}
