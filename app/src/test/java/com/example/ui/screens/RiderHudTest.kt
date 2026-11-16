package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ai.SmartHudCallout
import com.example.ai.SmartHudUiState
import com.example.data.remote.MotoRouteAndWeatherRepository
import com.example.location.LiveGpsFix
import com.example.ui.theme.HudThemeMode
import com.example.ui.theme.MotoPackTheme
import com.example.ui.viewmodel.HudNavigationUiState
import com.example.ui.viewmodel.HudSmartAdvisory
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class RiderHudTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun formatHudDistance_formatsMetersAndKilometersCleanly() {
        assertEquals("340 m", formatHudDistance(340))
        assertEquals("1.4 km", formatHudDistance(1420))
        assertEquals("14 km", formatHudDistance(14300))
    }

    @Test
    fun geometricNavigationSteps_detectsTurnsAndArrival() {
        val points = listOf(
            LatLng(53.3800, -1.4700),
            LatLng(53.3900, -1.4700), // Heading North (~0 deg)
            LatLng(53.3900, -1.4500), // Turn East (~90 deg -> TURN_RIGHT)
            LatLng(53.4000, -1.4500)  // Final leg North -> ARRIVE
        )
        val steps = MotoRouteAndWeatherRepository.buildGeometricNavigationSteps(points, "Snake Pass")
        assertTrue(steps.size >= 2)
        assertTrue(steps.first().maneuver.contains("RIGHT"))
        assertEquals("ARRIVE", steps.last().maneuver)
        assertTrue(steps.all { it.distanceMeters > 0 })
    }

    @Test
    fun riderHudScreen_displaysLargeTurnDistanceInstructionAndSmartIntel() {
        var aiScanCount = 0
        val navState = HudNavigationUiState(
            hasActiveRoute = true,
            routeName = "Snake Pass Twisties",
            currentStepIndex = 0,
            totalSteps = 4,
            currentInstruction = "Turn left onto A57 Snake Pass",
            currentManeuver = "TURN_LEFT",
            distanceToNextTurnMeters = 350,
            stepProgressFraction = 0.65f,
            thenInstruction = "Bear right at Ladybower",
            thenManeuver = "TURN_SLIGHT_RIGHT",
            thenDistanceMeters = 1200,
            remainingRouteKm = 24.5,
            remainingEtaMin = 28,
            nextFuelStopLabel = "Bamford Petrol (+6.2 km)",
            smartAdvisories = listOf(
                HudSmartAdvisory(
                    severity = "CRITICAL",
                    title = "Ease speed for turn in 350m",
                    detail = "Approaching Turn left onto A57 Snake Pass at 78 km/h",
                    tag = "CORNER GUARD"
                )
            )
        )

        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                RiderHudScreen(
                    currentUserId = "uid_1",
                    activePack = null,
                    riders = emptyList(),
                    myGpsFix = LiveGpsFix(
                        lat = 53.3811,
                        lng = -1.4701,
                        speedKmh = 78.4,
                        headingDeg = 315f,
                        altitudeM = 340.0,
                        timestampMs = 1_700_000_000_000L
                    ),
                    batteryPercent = 82,
                    ecoRideMode = false,
                    hudThemeMode = HudThemeMode.NIGHT_HUD,
                    isEffectiveOffline = false,
                    isPlanningRoute = false,
                    hudNav = navState,
                    smartHudState = SmartHudUiState.Success(
                        SmartHudCallout(
                            priority = "CAUTION",
                            headline = "Wet camber on summit hairpins",
                            spokenTip = "Ease throttle before A57 left turn and check mirror spacing.",
                            paceAction = "EASE THROTTLE"
                        )
                    ),
                    hudVoiceEnabled = false,
                    isSosActive = false,
                    sosLastBroadcastMs = null,
                    onToggleVoice = {},
                    onToggleEcoMode = {},
                    onSetHudThemeMode = {},
                    onStepDelta = {},
                    onResetAutoStep = {},
                    onQuickPlanPreset = { _, _, _ -> },
                    onRouteToPackLeader = {},
                    onOpenRoutePlanner = {},
                    onRunSmartScan = { aiScanCount++ },
                    onTriggerSos = {},
                    onStandDownSos = {},
                    onExitHud = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("hud_next_turn_distance").performScrollTo().assertIsDisplayed().assertTextContains("350 m")
        composeTestRule.onNodeWithTag("hud_next_turn_instruction").performScrollTo().assertIsDisplayed().assertTextContains("Turn left onto A57 Snake Pass")
        composeTestRule.onNodeWithTag("hud_speed_value").performScrollTo().assertIsDisplayed().assertTextContains("78")
        composeTestRule.onNodeWithTag("hud_smart_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("EASE THROTTLE").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("hud_ai_scan_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, aiScanCount)
    }

    @Test
    fun riderHudScreen_freeRideModeShowsQuickRouteLaunch() {
        var quickLaunchedName: String? = null
        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.DAYLIGHT) {
                RiderHudScreen(
                    currentUserId = "uid_1",
                    activePack = null,
                    riders = emptyList(),
                    myGpsFix = LiveGpsFix(53.3811, -1.4701, 0.0, 45f, 120.0, 1_700_000_000_000L),
                    batteryPercent = 90,
                    ecoRideMode = false,
                    hudThemeMode = HudThemeMode.DAYLIGHT,
                    isEffectiveOffline = false,
                    isPlanningRoute = false,
                    hudNav = HudNavigationUiState(hasActiveRoute = false),
                    smartHudState = SmartHudUiState.Idle,
                    hudVoiceEnabled = false,
                    isSosActive = false,
                    sosLastBroadcastMs = null,
                    onToggleVoice = {},
                    onToggleEcoMode = {},
                    onSetHudThemeMode = {},
                    onStepDelta = {},
                    onResetAutoStep = {},
                    onQuickPlanPreset = { name, _, _ -> quickLaunchedName = name },
                    onRouteToPackLeader = {},
                    onOpenRoutePlanner = {},
                    onRunSmartScan = {},
                    onTriggerSos = {},
                    onStandDownSos = {},
                    onExitHud = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("hud_free_ride_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_quick_route_snake_pass").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Snake Pass Twisties", quickLaunchedName)
    }
}
