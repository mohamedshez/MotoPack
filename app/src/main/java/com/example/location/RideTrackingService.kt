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

class RideTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val packName = intent?.getStringExtra(EXTRA_PACK_NAME) ?: "Active Pack"
        val ecoMode = intent?.getBooleanExtra(EXTRA_ECO_MODE, false) ?: false

        MotoPackNotificationHelper.ensureChannels(this)
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RideTrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MotoPackNotificationHelper.CHANNEL_RIDE_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MotoPack Ride Active • $packName")
            .setContentText(
                if (ecoMode) "Battery-Saver GPS pulse (20s) • Encrypted E2E"
                else "Live Convoy Radar (5s) • Encrypted E2E"
            )
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "End Ride", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MotoPackNotificationHelper.RIDE_SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(MotoPackNotificationHelper.RIDE_SERVICE_NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    companion object {
        const val ACTION_START = "com.example.action.START_RIDE"
        const val ACTION_STOP = "com.example.action.STOP_RIDE"
        const val EXTRA_PACK_NAME = "extra_pack_name"
        const val EXTRA_ECO_MODE = "extra_eco_mode"

        fun start(context: Context, packName: String, ecoMode: Boolean) {
            val intent = Intent(context, RideTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PACK_NAME, packName)
                putExtra(EXTRA_ECO_MODE, ecoMode)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
