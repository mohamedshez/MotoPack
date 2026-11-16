package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ai.configureAppCheck
import com.example.location.MotoPackNotificationHelper
import com.example.ui.MotoPackRoot
import com.example.weather.RouteWeatherActionBus
import com.example.weather.RouteWeatherNotificationAction

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    dispatchWeatherNotificationIntent(intent)
    enableEdgeToEdge()
    setContent {
      MotoPackRoot()
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    dispatchWeatherNotificationIntent(intent)
  }

  private fun dispatchWeatherNotificationIntent(intent: Intent?) {
    val action = intent?.action ?: return
    when (action) {
      MotoPackNotificationHelper.ACTION_WEATHER_APPLY_STOP -> {
        val name = intent.getStringExtra(MotoPackNotificationHelper.EXTRA_STOP_NAME) ?: "Safe Shelter Stop"
        val lat = intent.getDoubleExtra(MotoPackNotificationHelper.EXTRA_STOP_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(MotoPackNotificationHelper.EXTRA_STOP_LNG, Double.NaN)
        if (!lat.isNaN() && !lng.isNaN()) {
          RouteWeatherActionBus.emitAction(
            RouteWeatherNotificationAction.ApplySuggestedStop(name, lat, lng)
          )
        } else {
          RouteWeatherActionBus.emitAction(RouteWeatherNotificationAction.OpenRoutePlanner)
        }
        intent.action = null
      }
      MotoPackNotificationHelper.ACTION_WEATHER_APPLY_DETOUR -> {
        val title = intent.getStringExtra(MotoPackNotificationHelper.EXTRA_DETOUR_TITLE) ?: "Weather Detour"
        val label = intent.getStringExtra(MotoPackNotificationHelper.EXTRA_DETOUR_LABEL) ?: "Sheltered Detour"
        val lat = intent.getDoubleExtra(MotoPackNotificationHelper.EXTRA_DETOUR_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(MotoPackNotificationHelper.EXTRA_DETOUR_LNG, Double.NaN)
        if (!lat.isNaN() && !lng.isNaN()) {
          RouteWeatherActionBus.emitAction(
            RouteWeatherNotificationAction.ApplyRouteDetour(title, label, lat, lng)
          )
        } else {
          RouteWeatherActionBus.emitAction(RouteWeatherNotificationAction.OpenRoutePlanner)
        }
        intent.action = null
      }
      MotoPackNotificationHelper.ACTION_WEATHER_OPEN_ROUTE -> {
        RouteWeatherActionBus.emitAction(RouteWeatherNotificationAction.OpenRoutePlanner)
        intent.action = null
      }
    }
  }
}

