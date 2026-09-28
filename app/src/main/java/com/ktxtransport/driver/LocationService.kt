package com.ktxtransport.driver

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

/**
 * Sends the phone's position while the driver carries a load: a high-accuracy fix every
 * `interval`, and at least one report every `heartbeat` even when no new fix arrives (so the
 * server can tell "parked" from "phone off"). Both come from startTracking (TrackingState);
 * the defaults, 60 s and 5 min, match the Traccar Client setup (interval=60, heartbeat=300).
 */
class LocationService : Service() {

    private lateinit var state: TrackingState
    private val handler = Handler(Looper.getMainLooper())

    private var fused: FusedLocationProviderClient? = null
    private var locationManager: LocationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var updatesStarted = false

    // Cadence in use; re-read from TrackingState whenever startTracking is called again.
    private var intervalMs = TrackingState.DEFAULT_INTERVAL_SEC * 1000L
    private var heartbeatMs = TrackingState.DEFAULT_HEARTBEAT_SEC * 1000L

    private var lastFix: Location? = null
    private var lastQueuedAt = 0L // SystemClock.elapsedRealtime() of the last queued report

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onFix(it) }
        }
    }

    private val managerListener = LocationListener { onFix(it) }

    private val heartbeat = object : Runnable {
        override fun run() {
            val fix = lastFix
            if (fix != null && SystemClock.elapsedRealtime() - lastQueuedAt >= heartbeatMs) {
                queue(fix, System.currentTimeMillis())
            } else {
                FixUploader.flush(this@LocationService) // retry anything left from a failed send
            }
            handler.postDelayed(this, CHECK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        state = TrackingState(this)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go foreground within seconds of startForegroundService, even when about to stop.
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!state.tracking || state.phone == null || state.url == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!updatesStarted) startUpdates() else applyCadence()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        fused?.removeLocationUpdates(fusedCallback)
        locationManager?.removeUpdates(managerListener)
        networkCallback?.let {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        FixUploader.flush(this) // deliver whatever is still queued
        isRunning = false
        super.onDestroy()
    }

    private fun goForeground(): Boolean {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.tracking_channel))
                .setShowBadge(false)
                .build()
        )
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(getString(R.string.tracking_notification))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
            true
        } catch (e: SecurityException) {
            // No location permission (or not "always" when started from the background).
            Log.w(TAG, "cannot start location service: ${e.message}")
            false
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: started from the background.
            Log.w(TAG, "cannot start location service: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun startUpdates() {
        if (!AppPermissions.hasLocation(this)) {
            stopSelf()
            return
        }
        updatesStarted = true
        intervalMs = state.intervalSec * 1000L
        heartbeatMs = state.heartbeatSec * 1000L

        // Keep the CPU awake between fixes so the cadence and the heartbeat hold with the screen off.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KtxDriver:tracking")
            .apply { acquire() }

        val playServices = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        if (playServices) {
            fused = LocationServices.getFusedLocationProviderClient(this)
        } else {
            locationManager = getSystemService(LocationManager::class.java)
        }
        requestUpdates()

        // First report right away rather than after the first interval.
        fused?.let { client ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                if (loc != null) onFix(loc) else client.lastLocation.addOnSuccessListener { it?.let(::onFix) }
            }
        }
        locationManager?.let { lm ->
            providers(lm).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let(::onFix)
        }

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = FixUploader.flush(this@LocationService)
        }.also { getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(it) }

        handler.postDelayed(heartbeat, CHECK_MS)
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        fused?.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs)
                .setWaitForAccurateLocation(false)
                .build(),
            fusedCallback,
            Looper.getMainLooper(),
        )
        locationManager?.let { lm ->
            for (provider in providers(lm)) {
                lm.requestLocationUpdates(provider, intervalMs, 0f, managerListener, Looper.getMainLooper())
            }
        }
    }

    private fun providers(lm: LocationManager): List<String> =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.allProviders.contains(it) }

    /** startTracking called again while running: take a changed interval / heartbeat right away. */
    private fun applyCadence() {
        val newInterval = state.intervalSec * 1000L
        heartbeatMs = state.heartbeatSec * 1000L
        if (newInterval == intervalMs) return
        intervalMs = newInterval
        fused?.removeLocationUpdates(fusedCallback)
        locationManager?.removeUpdates(managerListener)
        requestUpdates()
    }

    private fun onFix(location: Location) {
        val first = lastQueuedAt == 0L
        // GPS and network providers (fallback path) both report; keep one report per interval.
        if (!first && SystemClock.elapsedRealtime() - lastQueuedAt < intervalMs * 5 / 6) {
            val prev = lastFix
            if (prev == null || location.accuracy <= prev.accuracy) lastFix = location
            return
        }
        lastFix = location
        queue(location, location.time)
    }

    private fun queue(location: Location, timeMs: Long) {
        val phone = state.phone ?: return
        val url = state.url ?: return
        lastQueuedAt = SystemClock.elapsedRealtime()
        FixUploader.enqueue(this, url, osmAndBody(phone, location, timeMs))
    }

    private fun osmAndBody(phone: String, location: Location, timeMs: Long): String {
        val builder = Uri.Builder()
            .appendQueryParameter("id", phone)
            .appendQueryParameter("lat", fmt(location.latitude, 6))
            .appendQueryParameter("lon", fmt(location.longitude, 6))
            .appendQueryParameter("timestamp", (timeMs / 1000).toString())
            .appendQueryParameter("speed", fmt(location.speed * MPS_TO_KNOTS, 1))
            .appendQueryParameter("bearing", fmt(location.bearing.toDouble(), 1))
            .appendQueryParameter("altitude", fmt(location.altitude, 1))
            .appendQueryParameter("accuracy", fmt(location.accuracy.toDouble(), 1))
        batteryPercent()?.let { builder.appendQueryParameter("batt", it.toString()) }
        return builder.build().encodedQuery.orEmpty()
    }

    private fun batteryPercent(): Int? =
        getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }

    private fun fmt(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1

        private const val CHECK_MS = 60_000L // heartbeat check; the shortest heartbeat allowed is 60 s
        private const val MPS_TO_KNOTS = 1.943844

        /** Whether the service is alive in this process (for status()). */
        @Volatile
        var isRunning = false
            private set

        /**
         * Saves the settings as tracking-on and starts the service if location is allowed
         * (otherwise it starts from resumeIfTracking once it is). False when phone or url is unusable.
         */
        fun start(
            context: Context,
            phone: String?,
            url: String?,
            cadence: TrackingState.Cadence = TrackingState.Cadence.DEFAULT,
        ): Boolean {
            val digits = TrackingState.normalizePhone(phone) ?: return false
            if (!TrackingState.isUsableUrl(url)) return false
            TrackingState(context).apply {
                this.phone = digits
                this.url = url!!.trim()
                intervalSec = cadence.intervalSec
                heartbeatSec = cadence.heartbeatSec
                tracking = true
            }
            resumeIfTracking(context)
            return true
        }

        fun stop(context: Context) {
            TrackingState(context).tracking = false
            context.stopService(Intent(context, LocationService::class.java))
        }

        /** After a reboot, an app update, a permission grant or opening the app: resume if tracking is on. */
        fun resumeIfTracking(context: Context) {
            if (!TrackingState(context).tracking || !AppPermissions.hasLocation(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "cannot resume tracking: ${e.message}")
            }
        }
    }
}
