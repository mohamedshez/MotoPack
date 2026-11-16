package com.example.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.weather.ProactiveRouteWeatherAlert
import com.example.weather.WeatherAdvisorySeverity

object MotoPackNotificationHelper {
    const val CHANNEL_URGENT_ALERTS = "motopack_urgent_alerts"
    const val CHANNEL_RIDE_SERVICE = "motopack_ride_service"
    const val CHANNEL_ROUTE_WEATHER_PUSH = "motopack_route_weather_push"
    const val RIDE_SERVICE_NOTIFICATION_ID = 4101
    const val WEATHER_SHIELD_SERVICE_NOTIFICATION_ID = 4102
    const val ROUTE_WEATHER_NOTIFICATION_ID = 4205

    const val ACTION_WEATHER_APPLY_STOP = "com.example.action.WEATHER_APPLY_STOP"
    const val ACTION_WEATHER_APPLY_DETOUR = "com.example.action.WEATHER_APPLY_DETOUR"
    const val ACTION_WEATHER_OPEN_ROUTE = "com.example.action.WEATHER_OPEN_ROUTE"

    const val EXTRA_STOP_NAME = "extra_weather_stop_name"
    const val EXTRA_STOP_LAT = "extra_weather_stop_lat"
    const val EXTRA_STOP_LNG = "extra_weather_stop_lng"
    const val EXTRA_DETOUR_TITLE = "extra_weather_detour_title"
    const val EXTRA_DETOUR_LABEL = "extra_weather_detour_label"
    const val EXTRA_DETOUR_LAT = "extra_weather_detour_lat"
    const val EXTRA_DETOUR_LNG = "extra_weather_detour_lng"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val urgentChannel = NotificationChannel(
                CHANNEL_URGENT_ALERTS,
                "Pack Urgent Pings & Safety Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent rider rings, convoy separation, low battery, and weather warnings"
                enableVibration(true)
            }
            val weatherChannel = NotificationChannel(
                CHANNEL_ROUTE_WEATHER_PUSH,
                context.getString(R.string.proactive_weather_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Proactive storm, heavy rain, and high crosswind alerts along your active planned route with stop and detour actions"
                enableVibration(true)
            }
            val serviceChannel = NotificationChannel(
                CHANNEL_RIDE_SERVICE,
                "Active Convoy & Weather Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent battery-efficient GPS telemetry & route weather shield during rides"
            }
            manager.createNotificationChannel(urgentChannel)
            manager.createNotificationChannel(weatherChannel)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun buildRouteWeatherNotification(
        context: Context,
        alert: ProactiveRouteWeatherAlert
    ): Notification {
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_WEATHER_OPEN_ROUTE
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            ROUTE_WEATHER_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val severityTag = if (alert.severity == WeatherAdvisorySeverity.CRITICAL) "URGENT" else "Advisory"
        val title = "$severityTag: ${alert.hazardType.label} Ahead (~${alert.etaMinutes} min)"
        val shortSummary = "${alert.checkpointLabel} • ${alert.headline}"
        val stopLine = alert.suggestedStop?.let {
            "\nSuggested Stop: ${it.name} (~${String.format("%.1f", it.distanceAheadKm)} km)"
        }.orEmpty()
        val detourLine = "\nRoute Option: ${alert.suggestedRouteChange.title}"
        val bigBody = "$shortSummary$stopLine$detourLine"

        val builder = NotificationCompat.Builder(context, CHANNEL_ROUTE_WEATHER_PUSH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(alert.recommendationSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)

        alert.suggestedStop?.let { stop ->
            val stopIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_WEATHER_APPLY_STOP
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_STOP_NAME, stop.name)
                putExtra(EXTRA_STOP_LAT, stop.lat)
                putExtra(EXTRA_STOP_LNG, stop.lng)
            }
            val stopPendingIntent = PendingIntent.getActivity(
                context,
                ROUTE_WEATHER_NOTIFICATION_ID + 1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val actionLabel = "Stop: ${stop.name.take(18)}"
            builder.addAction(0, actionLabel, stopPendingIntent)
        }

        val detour = alert.suggestedRouteChange
        val detourIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_WEATHER_APPLY_DETOUR
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DETOUR_TITLE, detour.title)
            putExtra(EXTRA_DETOUR_LABEL, detour.destinationLabel)
            putExtra(EXTRA_DETOUR_LAT, detour.detourLat)
            putExtra(EXTRA_DETOUR_LNG, detour.detourLng)
        }
        val detourPendingIntent = PendingIntent.getActivity(
            context,
            ROUTE_WEATHER_NOTIFICATION_ID + 2,
            detourIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "Detour Route", detourPendingIntent)

        return builder.build()
    }

    fun showRouteWeatherPushNotification(
        context: Context,
        alert: ProactiveRouteWeatherAlert
    ): Boolean {
        ensureChannels(context)
        if (alert.severity == WeatherAdvisorySeverity.CRITICAL) {
            triggerUrgentHaptic(context)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        val notification = buildRouteWeatherNotification(context, alert)
        return runCatching {
            NotificationManagerCompat.from(context).notify(ROUTE_WEATHER_NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun showUrgentAlertNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        vibrateUrgently: Boolean = true
    ) {
        ensureChannels(context)
        if (vibrateUrgently) {
            triggerUrgentHaptic(context)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_URGENT_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun triggerUrgentHaptic(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 220, 120, 220, 120, 320), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 120, 220), -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(300L)
                }
            }
        }
    }
}
