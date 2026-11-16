package com.example.location

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class DeviceBatteryState(
    val percent: Int = 100,
    val isCharging: Boolean = false
)

data class LiveGpsFix(
    val lat: Double,
    val lng: Double,
    val speedKmh: Double,
    val headingDeg: Float,
    val altitudeM: Double,
    val timestampMs: Long
)

class DeviceTelemetryMonitor(private val context: Context) {

    fun observeBatteryState(): Flow<DeviceBatteryState> = callbackFlow {
        fun readBattery(intent: Intent?): DeviceBatteryState {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 85
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            return DeviceBatteryState(percent = pct, isCharging = charging)
        }

        val initialIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        trySend(readBattery(initialIntent))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(readBattery(intent))
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()

    fun observeNetworkConnected(): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun checkCurrent(): Boolean {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        trySend(checkCurrent())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(checkCurrent())
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    @SuppressLint("MissingPermission")
    fun observeLocationFixes(ecoMode: Boolean): Flow<LiveGpsFix> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)

        val intervalMs = if (ecoMode) 20_000L else 5_000L
        val priority = if (ecoMode) {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        } else {
            Priority.PRIORITY_HIGH_ACCURACY
        }

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(if (ecoMode) 10_000L else 3_000L)
            .setMinUpdateDistanceMeters(if (ecoMode) 15f else 5f)
            .setMaxUpdateDelayMillis(if (ecoMode) 30_000L else 8_000L)
            .build()

        // Emit last known location immediately, and actively request a single current fix if available
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                trySend(
                    LiveGpsFix(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        speedKmh = (loc.speed * 3.6).coerceAtLeast(0.0),
                        headingDeg = loc.bearing,
                        altitudeM = loc.altitude,
                        timestampMs = loc.time
                    )
                )
            }
        }
        client.getCurrentLocation(priority, null).addOnSuccessListener { loc ->
            if (loc != null) {
                trySend(
                    LiveGpsFix(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        speedKmh = (loc.speed * 3.6).coerceAtLeast(0.0),
                        headingDeg = loc.bearing,
                        altitudeM = loc.altitude,
                        timestampMs = loc.time
                    )
                )
            }
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    LiveGpsFix(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        speedKmh = (loc.speed * 3.6).coerceAtLeast(0.0),
                        headingDeg = loc.bearing,
                        altitudeM = loc.altitude,
                        timestampMs = loc.time
                    )
                )
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
