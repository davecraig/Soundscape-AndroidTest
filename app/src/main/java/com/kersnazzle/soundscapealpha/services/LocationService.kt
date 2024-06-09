package com.kersnazzle.soundscapealpha.services

import android.annotation.SuppressLint
import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location

import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kersnazzle.soundscapealpha.R

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Simple foreground service that shows a notification to the user and provides location updates.
 */
class LocationService : Service() {
    private val binder = LocalBinder()

    private val coroutineScope = CoroutineScope(Job())
    // core service
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    // secondary service
    private var timerJob: Job? = null

    // Flow to return Location objects
    private val _locationFlow = MutableStateFlow<Location?>(null)
    var locationFlow: StateFlow<Location?> = _locationFlow

    // Binder to allow local clients to Bind to our service
    // Need to check that this stops another naughty app hooking into our Location Service?
    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        startAsForegroundService()
        startLocationUpdates()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        Toast.makeText(this, "Foreground Service created", Toast.LENGTH_SHORT).show()

        // Set up the location updates using the FusedLocationProviderClient but doesn't start them
        setupLocationUpdates()
        // Start secondary service
        startServiceRunningTicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        fusedLocationClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()
        coroutineScope.coroutineContext.cancelChildren()

        Toast.makeText(this, "Foreground Service destroyed", Toast.LENGTH_SHORT).show()
    }

    /**
     * Promotes the service to a foreground service, showing a notification to the user.
     *
     * This needs to be called within 10 seconds of starting the service or the system will throw an exception.
     */
    private fun startAsForegroundService() {

        // promote service to foreground service
        // FOREGROUND_SERVICE_TYPE_LOCATION needs to be in AndroidManifest.xml
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            getNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    /**
     * Stops the foreground service and removes the notification.
     * Can be called from inside or outside the service.
     */
    fun stopForegroundService() {
        stopSelf()
    }

    /**
     * Sets up the location updates using the FusedLocationProviderClient, but doesn't actually start them.
     * To start the location updates, call [startLocationUpdates].
     */
    private fun setupLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    _locationFlow.value = location
                }
            }
        }
    }

    /**
     * Starts the location updates using the FusedLocationProviderClient.
     * Suppressing IDE warning with annotation. Will check for this in UI.
     *  TODO: Add permission checks and observe for permission changes by user
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        /*fusedLocationClient.requestLocationUpdates(
            LocationRequest.Builder(
                LOCATION_UPDATES_INTERVAL_MS
            ).build(), locationCallback, Looper.getMainLooper()
        )*/
        fusedLocationClient.requestLocationUpdates(
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_UPDATES_INTERVAL_MS
            ).apply {
                setMinUpdateDistanceMeters(1f)
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                setWaitForAccurateLocation(true)
            }.build(),
            locationCallback,
            Looper.getMainLooper(),
        )
    }

    /**
     * Starts a ticker that shows a toast every [TICKER_PERIOD_SECONDS] seconds to indicate that the service is still running.
     */
    private fun startServiceRunningTicker() {
        timerJob?.cancel()
        timerJob = coroutineScope.launch {
            tickerFlow()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@LocationService,
                            "Foreground Service still running.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    private fun tickerFlow(
        period: Duration = TICKER_PERIOD_SECONDS,
        initialDelay: Duration = TICKER_PERIOD_SECONDS
    ) = flow {
        delay(initialDelay)
        while (true) {
            emit(Unit)
            delay(period)
        }
    }

    private fun getNotification(): Notification {
        createServiceNotificationChannel()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)

        return builder.build()
    }

    private fun createServiceNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "LocationService"
        // Check for GPS every n seconds
        private val LOCATION_UPDATES_INTERVAL_MS = 1.seconds.inWholeMilliseconds
        // Secondary "service" every 5 seconds
        private val TICKER_PERIOD_SECONDS = 10.seconds

        private const val CHANNEL_ID = "LocationService_channel_01"
        private const val NOTIFICATION_CHANNEL_NAME = "SoundscapeAlpha_LocationService"
        private const val NOTIFICATION_ID = 1000000
    }
}