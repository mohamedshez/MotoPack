package com.example.weather

import com.example.data.remote.ComputedMotoRoute
import com.example.location.LiveGpsFix
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RouteWeatherNotificationAction {
    data class ApplySuggestedStop(
        val stopName: String,
        val lat: Double,
        val lng: Double
    ) : RouteWeatherNotificationAction

    data class ApplyRouteDetour(
        val routeTitle: String,
        val destinationLabel: String,
        val lat: Double,
        val lng: Double
    ) : RouteWeatherNotificationAction

    data object OpenRoutePlanner : RouteWeatherNotificationAction
}

data class RouteWeatherMonitorSnapshot(
    val plannedRoute: ComputedMotoRoute? = null,
    val currentFix: LiveGpsFix = LiveGpsFix(53.3811, -1.4701, 0.0, 0f, 120.0, 0L),
    val proactivePushEnabled: Boolean = true,
    val isOffline: Boolean = false,
    val ecoMode: Boolean = false
)

object RouteWeatherActionBus {
    private val _actions = MutableSharedFlow<RouteWeatherNotificationAction>(
        replay = 0,
        extraBufferCapacity = 8
    )
    val actions: SharedFlow<RouteWeatherNotificationAction> = _actions.asSharedFlow()

    private val _monitorSnapshot = MutableStateFlow(RouteWeatherMonitorSnapshot())
    val monitorSnapshot: StateFlow<RouteWeatherMonitorSnapshot> = _monitorSnapshot.asStateFlow()

    private val _latestServiceAlert = MutableStateFlow<ProactiveRouteWeatherAlert?>(null)
    val latestServiceAlert: StateFlow<ProactiveRouteWeatherAlert?> = _latestServiceAlert.asStateFlow()

    @Volatile
    private var lastPushedSignature: String? = null

    @Volatile
    private var lastPushedAtMs: Long = 0L

    fun emitAction(action: RouteWeatherNotificationAction) {
        _actions.tryEmit(action)
    }

    fun updateMonitorSnapshot(snapshot: RouteWeatherMonitorSnapshot) {
        _monitorSnapshot.value = snapshot
    }

    fun recordServiceAlert(alert: ProactiveRouteWeatherAlert?) {
        _latestServiceAlert.value = alert
    }

    fun tryClaimPushSlot(
        alert: ProactiveRouteWeatherAlert,
        force: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val shouldPush = force || ProactiveRouteWeatherEngine.shouldPushNotification(
            newAlert = alert,
            lastPushedSignature = lastPushedSignature,
            lastPushedAtMs = lastPushedAtMs,
            nowMs = nowMs
        )
        if (shouldPush) {
            lastPushedSignature = alert.signatureKey
            lastPushedAtMs = nowMs
        }
        return shouldPush
    }

    fun resetPushDeduplication() {
        lastPushedSignature = null
        lastPushedAtMs = 0L
        _latestServiceAlert.value = null
    }
}
