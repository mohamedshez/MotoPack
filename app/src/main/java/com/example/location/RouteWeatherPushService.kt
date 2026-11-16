package com.example.location

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.remote.MotoRouteAndWeatherRepository
import com.example.weather.ProactiveRouteWeatherEngine
import com.example.weather.RouteWeatherActionBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Proactive Foreground DataSync Service that monitors weather forecasts along the active planned
 * motorcycle corridor and pushes actionable advisories (suggesting safe stops or bypass detours)
 * before the rider enters adverse rain, thunderstorm, ice, or high-crosswind cells.
 */
class RouteWeatherPushService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val routeWeatherRepository by lazy { MotoRouteAndWeatherRepository() }
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            monitorJob?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val routeName = intent?.getStringExtra(EXTRA_ROUTE_NAME)
            ?: RouteWeatherActionBus.monitorSnapshot.value.plannedRoute?.routeName
            ?: "Active Route"
        val ecoMode = intent?.getBooleanExtra(
            EXTRA_ECO_MODE,
            RouteWeatherActionBus.monitorSnapshot.value.ecoMode
        ) ?: false

        MotoPackNotificationHelper.ensureChannels(this)
        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                action = MotoPackNotificationHelper.ACTION_WEATHER_OPEN_ROUTE
                setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, RouteWeatherPushService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusNotification = NotificationCompat.Builder(
            this,
            MotoPackNotificationHelper.CHANNEL_RIDE_SERVICE
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Route Weather Shield • $routeName")
            .setContentText(
                if (ecoMode) "Scanning storm & crosswind corridor every 10 min (Eco)"
                else "Monitoring rain & crosswind hazards ahead every 5 min"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, "Pause Alerts", pauseIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MotoPackNotificationHelper.WEATHER_SHIELD_SERVICE_NOTIFICATION_ID,
                statusNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(
                MotoPackNotificationHelper.WEATHER_SHIELD_SERVICE_NOTIFICATION_ID,
                statusNotification
            )
        }

        startMonitorLoop(
            pollIntervalMs = if (ecoMode) ECO_SCAN_INTERVAL_MS else STANDARD_SCAN_INTERVAL_MS,
            immediateScan = intent?.action == ACTION_SCAN_NOW
        )
        return START_STICKY
    }

    private fun startMonitorLoop(pollIntervalMs: Long, immediateScan: Boolean) {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            if (!immediateScan) {
                // Delay initial background poll slightly so immediate ViewModel route scan runs first
                delay(45_000L)
            }
            while (isActive) {
                runSingleCorridorCheck()
                delay(pollIntervalMs)
            }
        }
    }

    private suspend fun runSingleCorridorCheck() {
        val snapshot = RouteWeatherActionBus.monitorSnapshot.value
        val route = snapshot.plannedRoute ?: return
        if (!snapshot.proactivePushEnabled || snapshot.isOffline) return

        val alert = ProactiveRouteWeatherEngine.analyzeRoute(
            route = route,
            currentFix = snapshot.currentFix,
            routeRepository = routeWeatherRepository
        )
        RouteWeatherActionBus.recordServiceAlert(alert)

        if (alert != null && RouteWeatherActionBus.tryClaimPushSlot(alert)) {
            MotoPackNotificationHelper.showRouteWeatherPushNotification(
                context = this@RouteWeatherPushService,
                alert = alert
            )
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.action.START_ROUTE_WEATHER_PUSH"
        const val ACTION_SCAN_NOW = "com.example.action.SCAN_ROUTE_WEATHER_NOW"
        const val ACTION_STOP = "com.example.action.STOP_ROUTE_WEATHER_PUSH"
        const val EXTRA_ROUTE_NAME = "extra_weather_route_name"
        const val EXTRA_ECO_MODE = "extra_weather_eco_mode"

        private const val STANDARD_SCAN_INTERVAL_MS = 5L * 60L * 1000L
        private const val ECO_SCAN_INTERVAL_MS = 10L * 60L * 1000L

        fun startOrUpdate(
            context: Context,
            routeName: String,
            ecoMode: Boolean,
            scanNow: Boolean = false
        ) {
            val intent = Intent(context, RouteWeatherPushService::class.java).apply {
                action = if (scanNow) ACTION_SCAN_NOW else ACTION_START
                putExtra(EXTRA_ROUTE_NAME, routeName)
                putExtra(EXTRA_ECO_MODE, ecoMode)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RouteWeatherPushService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.startService(intent)
            }
        }
    }
}
