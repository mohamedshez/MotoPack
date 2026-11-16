package com.example.weather

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MotoPackPreferences
import com.example.data.remote.ComputedMotoRoute
import com.example.data.remote.ElevationSample
import com.example.data.remote.FuelStationStop
import com.example.location.LiveGpsFix
import com.example.location.MotoPackNotificationHelper
import com.example.ui.screens.ProactiveWeatherShieldCard
import com.example.ui.theme.MotoPackTheme
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class ProactiveRouteWeatherTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildSampleRoute(): ComputedMotoRoute {
        val pts = listOf(
            LatLng(53.3811, -1.4701), // Sheffield start
            LatLng(53.3790, -1.5800),
            LatLng(53.3775, -1.7014), // Near Ladybower
            LatLng(53.4050, -1.7900),
            LatLng(53.4339, -1.8732), // Snake Pass Summit (storm sector)
            LatLng(53.4480, -1.9400)
        )
        return ComputedMotoRoute(
            routeName = "Snake Pass Twisties",
            originLabel = "53.381, -1.470",
            destinationLabel = "Snake Pass, Peak District",
            points = pts,
            encodedPolyline = "mock_poly",
            distanceKm = 46.0,
            durationMin = 54,
            trafficDelayMin = 0,
            elevationSamples = listOf(
                ElevationSample(0.0, 120.0, 53.3811, -1.4701),
                ElevationSample(23.0, 310.0, 53.3775, -1.7014),
                ElevationSample(46.0, 512.0, 53.4480, -1.9400)
            ),
            maxElevationM = 512.0,
            totalAscentM = 392.0,
            fuelStations = listOf(
                FuelStationStop(
                    id = "fuel_bamford",
                    name = "Bamford Valley Fuel",
                    address = "A57 Corridor",
                    lat = 53.3780,
                    lng = -1.6850,
                    distanceAlongKm = 15.0,
                    isOpenNow = true
                )
            ),
            weatherWarnings = emptyList(),
            isLiveGoogleData = true
        )
    }

    @Test
    fun `evaluateSample detects storm, heavy rain, high winds, ice, and clear conditions`() {
        val clear = ProactiveRouteWeatherEngine.evaluateSample(
            CorridorWeatherSample(18.0, 12.0, 19.0, 10, 0.0, 1)
        )
        assertNull(clear)

        val heavyRain = ProactiveRouteWeatherEngine.evaluateSample(
            CorridorWeatherSample(11.0, 18.0, 26.0, 90, 6.2, 65)
        )
        assertNotNull(heavyRain)
        assertEquals(ProactiveWeatherHazardType.HEAVY_RAIN, heavyRain?.hazardType)
        assertEquals(WeatherAdvisorySeverity.CRITICAL, heavyRain?.severity)

        val highWinds = ProactiveRouteWeatherEngine.evaluateSample(
            CorridorWeatherSample(14.0, 39.0, 58.0, 20, 0.0, 3)
        )
        assertNotNull(highWinds)
        assertEquals(ProactiveWeatherHazardType.HIGH_WINDS, highWinds?.hazardType)
        assertEquals(WeatherAdvisorySeverity.CRITICAL, highWinds?.severity)

        val storm = ProactiveRouteWeatherEngine.evaluateSample(
            CorridorWeatherSample(15.0, 30.0, 52.0, 95, 8.0, 95)
        )
        assertEquals(ProactiveWeatherHazardType.THUNDERSTORM, storm?.hazardType)
        assertEquals(WeatherAdvisorySeverity.CRITICAL, storm?.severity)

        val ice = ProactiveRouteWeatherEngine.evaluateSample(
            CorridorWeatherSample(1.5, 10.0, 16.0, 50, 0.4, 51)
        )
        assertEquals(ProactiveWeatherHazardType.ICE_RISK, ice?.hazardType)
        assertEquals(WeatherAdvisorySeverity.CRITICAL, ice?.severity)
    }

    @Test
    fun `analyzeRouteWithFetcher recommends pre-hazard safe stop and bypass detour`() = runBlocking {
        val route = buildSampleRoute()
        val fix = LiveGpsFix(53.3811, -1.4701, 45.0, 270f, 120.0, 1000L)

        val alert = ProactiveRouteWeatherEngine.analyzeRouteWithFetcher(
            route = route,
            currentFix = fix
        ) { lat, _, _ ->
            // Clear early along route, severe crosswind + heavy rain near Snake Pass summit (north of 53.40)
            if (lat >= 53.40) {
                CorridorWeatherSample(
                    tempCelsius = 9.0,
                    windKmh = 36.0,
                    gustKmh = 56.0,
                    rainProbPercent = 88,
                    precipitationMm = 5.4,
                    wmoCode = 65
                )
            } else {
                CorridorWeatherSample(
                    tempCelsius = 14.0,
                    windKmh = 12.0,
                    gustKmh = 18.0,
                    rainProbPercent = 15,
                    precipitationMm = 0.0,
                    wmoCode = 2
                )
            }
        }

        assertNotNull(alert)
        val nonNull = alert!!
        assertEquals(WeatherAdvisorySeverity.CRITICAL, nonNull.severity)
        assertTrue(nonNull.distanceAheadKm > 5.0)
        assertTrue(nonNull.etaMinutes > 5)

        val stop = nonNull.suggestedStop
        assertNotNull("Expected safe stop before hazard zone", stop)
        assertTrue(stop!!.distanceAheadKm <= nonNull.distanceAheadKm + 2.0)

        val detour = nonNull.suggestedRouteChange
        assertTrue(detour.title.contains("Bypass") || detour.title.contains("Alternate") || detour.title.contains("Route"))
        assertTrue(detour.estimatedExtraMinutes >= 5)
    }

    @Test
    fun `analyzeRouteWithFetcher checks all 4 corridor points concurrently`() = runBlocking {
        val route = buildSampleRoute()
        val fix = LiveGpsFix(53.3811, -1.4701, 45.0, 270f, 120.0, 1000L)
        val inFlight = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val totalCalls = AtomicInteger(0)

        withContext(Dispatchers.Default) {
            ProactiveRouteWeatherEngine.analyzeRouteWithFetcher(
                route = route,
                currentFix = fix
            ) { _, _, _ ->
                totalCalls.incrementAndGet()
                val active = inFlight.incrementAndGet()
                maxConcurrent.updateAndGet { prev -> maxOf(prev, active) }
                delay(120L)
                inFlight.decrementAndGet()
                CorridorWeatherSample(15.0, 12.0, 18.0, 10, 0.0, 1)
            }
        }

        assertEquals(4, totalCalls.get())
        assertTrue("Expected concurrent checkpoint fetches >= 2, got ${maxConcurrent.get()}", maxConcurrent.get() >= 2)
    }

    @Test
    fun `shouldPushNotification enforces cooldown and allows critical escalation`() {
        val advisory = ProactiveRouteWeatherAlert(
            alertId = "a1",
            routeName = "Test",
            checkpointLabel = "Mid",
            hazardLat = 53.4,
            hazardLng = -1.8,
            distanceAheadKm = 18.0,
            etaMinutes = 20,
            hazardType = ProactiveWeatherHazardType.HIGH_WINDS,
            severity = WeatherAdvisorySeverity.ADVISORY,
            headline = "Moderate crosswinds",
            recommendationSummary = "Stop or detour",
            tempCelsius = 12.0,
            windKmh = 25.0,
            gustKmh = 38.0,
            rainProbPercent = 20,
            precipitationMm = 0.0,
            suggestedStop = null,
            suggestedRouteChange = SuggestedRouteChange("Bypass (+9 min)", "Valley detour", "Bypass", 53.35, -1.82, 9),
            signatureKey = "Test|HIGH_WINDS|ADVISORY"
        )
        val escalated = advisory.copy(
            severity = WeatherAdvisorySeverity.CRITICAL,
            signatureKey = "Test|HIGH_WINDS|CRITICAL"
        )

        val t0 = 1_000_000L
        assertTrue(
            ProactiveRouteWeatherEngine.shouldPushNotification(
                newAlert = advisory,
                lastPushedSignature = null,
                lastPushedAtMs = 0L,
                nowMs = t0
            )
        )

        // Same signature within cooldown -> suppress
        assertFalse(
            ProactiveRouteWeatherEngine.shouldPushNotification(
                newAlert = advisory,
                lastPushedSignature = advisory.signatureKey,
                lastPushedAtMs = t0,
                nowMs = t0 + 120_000L
            )
        )

        // Escalated to CRITICAL within cooldown -> allow immediate push
        assertTrue(
            ProactiveRouteWeatherEngine.shouldPushNotification(
                newAlert = escalated,
                lastPushedSignature = advisory.signatureKey,
                lastPushedAtMs = t0,
                nowMs = t0 + 30_000L
            )
        )

        // Same signature after cooldown -> allow push
        assertTrue(
            ProactiveRouteWeatherEngine.shouldPushNotification(
                newAlert = advisory,
                lastPushedSignature = advisory.signatureKey,
                lastPushedAtMs = t0,
                nowMs = t0 + ProactiveRouteWeatherEngine.DEFAULT_COOLDOWN_MS + 1000L
            )
        )
    }

    @Test
    fun `showRouteWeatherPushNotification creates high-priority notification with Stop and Detour actions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as Application
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val alert = ProactiveRouteWeatherAlert(
            alertId = "wx_1",
            routeName = "Snake Pass Twisties",
            checkpointLabel = "High Ridge / Approach",
            hazardLat = 53.4339,
            hazardLng = -1.8732,
            distanceAheadKm = 24.5,
            etaMinutes = 26,
            hazardType = ProactiveWeatherHazardType.HEAVY_RAIN,
            severity = WeatherAdvisorySeverity.CRITICAL,
            headline = "Heavy downpour expected (92% rain • 6.5 mm/h)",
            recommendationSummary = "Pull in at Ladybower Espresso (~14.2 km) or switch to Dry-Line Valley Alternate (+8 min).",
            tempCelsius = 8.5,
            windKmh = 28.0,
            gustKmh = 41.0,
            rainProbPercent = 92,
            precipitationMm = 6.5,
            suggestedStop = SuggestedWeatherStop(
                id = "brew_ladybower",
                name = "Ladybower Espresso",
                subtitle = "Biker & Brew Shelter",
                lat = 53.3775,
                lng = -1.7014,
                distanceAheadKm = 14.2,
                etaMinutes = 15,
                isCoveredCafe = true
            ),
            suggestedRouteChange = SuggestedRouteChange(
                title = "Dry-Line Valley Alternate (+8 min)",
                summary = "Avoid exposed moorland storm cell",
                destinationLabel = "Valley Alternate",
                detourLat = 53.3900,
                detourLng = -1.9100,
                estimatedExtraMinutes = 8
            ),
            signatureKey = "Snake Pass Twisties|HEAVY_RAIN|CRITICAL"
        )

        val posted = MotoPackNotificationHelper.showRouteWeatherPushNotification(context, alert)
        assertTrue(posted)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(MotoPackNotificationHelper.CHANNEL_ROUTE_WEATHER_PUSH)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)

        val shadowNm = Shadows.shadowOf(nm)
        val activeNotif: Notification? = shadowNm.getNotification(MotoPackNotificationHelper.ROUTE_WEATHER_NOTIFICATION_ID)
        assertNotNull(activeNotif)
        val extras = activeNotif!!.extras
        assertTrue(extras.getString(Notification.EXTRA_TITLE).orEmpty().contains("Heavy Rain"))
        assertEquals(2, activeNotif.actions.size)
        assertTrue(activeNotif.actions[0].title.toString().contains("Stop:"))
        assertEquals("Detour Route", activeNotif.actions[1].title.toString())

        val stopPiIntent: Intent = Shadows.shadowOf(activeNotif.actions[0].actionIntent).savedIntent
        assertEquals(MotoPackNotificationHelper.ACTION_WEATHER_APPLY_STOP, stopPiIntent.action)
        assertEquals("Ladybower Espresso", stopPiIntent.getStringExtra(MotoPackNotificationHelper.EXTRA_STOP_NAME))

        val detourPiIntent: Intent = Shadows.shadowOf(activeNotif.actions[1].actionIntent).savedIntent
        assertEquals(MotoPackNotificationHelper.ACTION_WEATHER_APPLY_DETOUR, detourPiIntent.action)
        assertEquals("Dry-Line Valley Alternate (+8 min)", detourPiIntent.getStringExtra(MotoPackNotificationHelper.EXTRA_DETOUR_TITLE))
    }

    @Test
    fun `ProactiveWeatherShieldCard renders hazard banner and invokes stop, detour, and toggle callbacks`() {
        var toggleCalls = mutableListOf<Boolean>()
        var scanClicked = false
        var stopApplied = false
        var detourApplied = false

        val alert = ProactiveRouteWeatherAlert(
            alertId = "wx_ui_1",
            routeName = "Cat & Fiddle Run",
            checkpointLabel = "Summit Ridge",
            hazardLat = 53.2435,
            hazardLng = -1.9964,
            distanceAheadKm = 19.4,
            etaMinutes = 22,
            hazardType = ProactiveWeatherHazardType.HIGH_WINDS,
            severity = WeatherAdvisorySeverity.CRITICAL,
            headline = "Severe crosswind gusts (62 km/h)",
            recommendationSummary = "Stop at Copper Kettle or detour via sheltered valley.",
            tempCelsius = 10.0,
            windKmh = 42.0,
            gustKmh = 62.0,
            rainProbPercent = 30,
            precipitationMm = 0.0,
            suggestedStop = SuggestedWeatherStop(
                id = "stop_1",
                name = "Torque & Tonic Roastery",
                subtitle = "Biker & Brew Shelter (~11.0 km)",
                lat = 53.2584,
                lng = -1.9125,
                distanceAheadKm = 11.0,
                etaMinutes = 12,
                isCoveredCafe = true
            ),
            suggestedRouteChange = SuggestedRouteChange(
                title = "Sheltered Valley Bypass (+9 min)",
                summary = "Lower elevation valley road with tree cover",
                destinationLabel = "Valley Bypass",
                detourLat = 53.2100,
                detourLng = -1.9400,
                estimatedExtraMinutes = 9
            ),
            signatureKey = "Cat & Fiddle Run|HIGH_WINDS|CRITICAL"
        )

        composeTestRule.setContent {
            MotoPackTheme {
                ProactiveWeatherShieldCard(
                    proactiveWeatherPushEnabled = true,
                    hasActiveRoute = true,
                    isEffectiveOffline = false,
                    isScanning = false,
                    proactiveAlert = alert,
                    onToggleProactivePush = { toggleCalls.add(it) },
                    onScanRouteWeatherNow = { scanClicked = true },
                    onApplySuggestedStop = { stopApplied = true },
                    onApplySuggestedDetour = { detourApplied = true },
                    onBroadcastToPack = {},
                    onDismissAlert = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("proactive_weather_shield_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("proactive_weather_alert_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Suggested Stop: Torque & Tonic Roastery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Route Change: Sheltered Valley Bypass (+9 min)").assertIsDisplayed()

        composeTestRule.onNodeWithTag("scan_route_weather_button").performClick()
        assertTrue(scanClicked)

        composeTestRule.onNodeWithTag("weather_suggested_stop_button").performClick()
        assertTrue(stopApplied)

        composeTestRule.onNodeWithTag("weather_suggested_detour_button").performClick()
        assertTrue(detourApplied)

        composeTestRule.onNodeWithTag("proactive_weather_push_switch").performClick()
        assertEquals(listOf(false), toggleCalls)
    }

    @Test
    fun `MotoPackPreferences persists proactiveWeatherPushEnabled toggle`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = MotoPackPreferences(context)

        prefs.setProactiveWeatherPushEnabled(false)
        assertFalse(prefs.settingsFlow.first().proactiveWeatherPushEnabled)

        prefs.setProactiveWeatherPushEnabled(true)
        assertTrue(prefs.settingsFlow.first().proactiveWeatherPushEnabled)
    }
}
