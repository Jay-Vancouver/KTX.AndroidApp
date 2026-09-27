package com.ktxtransport.driver

import android.content.Context
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Posts queued positions in order on one background thread (OsmAnd protocol, form body).
 * The server stores a repeated (id, timestamp) once, so re-sending after an unclear failure is safe.
 */
object FixUploader {

    private const val TAG = "FixUploader"
    private const val TIMEOUT_MS = 15_000

    private val executor = Executors.newSingleThreadExecutor()

    fun enqueue(context: Context, url: String, body: String) {
        val app = context.applicationContext
        executor.execute {
            FixQueue.get(app).add(url, body)
            flushNow(app)
        }
    }

    fun flush(context: Context) {
        val app = context.applicationContext
        executor.execute { flushNow(app) }
    }

    /** Sends until the queue is empty or a send fails (then waits for the next trigger). */
    private fun flushNow(context: Context) {
        val queue = FixQueue.get(context)
        while (true) {
            val batch = queue.oldest(50)
            if (batch.isEmpty()) return
            for (fix in batch) {
                when (post(fix.url, fix.body)) {
                    Result.SENT -> {
                        queue.remove(fix.id)
                        TrackingState(context).lastSentAt = System.currentTimeMillis()
                    }
                    Result.REJECTED -> queue.remove(fix.id)
                    Result.RETRY -> return
                }
            }
        }
    }

    private enum class Result { SENT, REJECTED, RETRY }

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
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            when {
                code in 200..299 -> Result.SENT
                // 4xx other than timeout / rate limit: the server will never take this one.
                code in 400..499 && code != 408 && code != 429 -> {
                    Log.w(TAG, "server rejected a fix with HTTP $code; dropped")
                    Result.REJECTED
                }
                else -> Result.RETRY
            }
        } catch (e: IOException) {
            Result.RETRY
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "unusable url $url; dropped")
            Result.REJECTED
        } finally {
            conn?.disconnect()
        }
    }
}
