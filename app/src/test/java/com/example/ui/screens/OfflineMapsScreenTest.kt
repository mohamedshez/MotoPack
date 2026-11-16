package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.data.local.MapAreaDownloadProgress
import com.example.data.local.OfflineMapAreaEntity
import com.example.data.remote.ElevationSample
import com.example.data.remote.PlaceSearchSuggestion
import com.example.location.LiveGpsFix
import com.example.ui.theme.HudThemeMode
import com.example.ui.theme.MotoPackTheme
import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class OfflineMapsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val json = Json { ignoreUnknownKeys = true }

    private val gpsFix = LiveGpsFix(
        lat = 53.3811,
        lng = -1.4701,
        speedKmh = 0.0,
        headingDeg = 0f,
        altitudeM = 185.0,
        timestampMs = 1_700_000_000_000L
    )

    private fun sampleArea(
        id: String,
        title: String,
        bytes: Long,
        tiles: Int
    ): OfflineMapAreaEntity {
        val samples = listOf(
            ElevationSample(0.0, 180.0, 53.40, -1.90),
            ElevationSample(5.0, 512.0, 53.43, -1.87),
            ElevationSample(10.0, 290.0, 53.46, -1.84)
        )
        return OfflineMapAreaEntity(
            areaId = id,
            packId = "pack_1",
            title = title,
            centerLat = 53.43,
            centerLng = -1.87,
            minLat = 53.38,
            maxLat = 53.48,
            minLng = -1.95,
            maxLng = -1.78,
            radiusKm = 10.0,
            zoomRangeLabel = "Z11–Z14",
            tileCount = tiles,
            storageBytes = bytes,
            routePolyline = "",
            navStepCount = 8,
            fuelStopCount = 2,
            elevationSampleCount = samples.size,
            maxElevationM = 512.0,
            elevationGainM = 332.0,
            elevationPointsJson = json.encodeToString(samples)
        )
    }

    @Test
    fun offlineMapsScreen_displaysStorageUsage_downloadedRegions_andSearchFilter() {
        val snakePass = sampleArea("area_snake", "Snake Pass Ridge", 2_500_000L, 120)
        val catFiddle = sampleArea("area_cat", "Cat & Fiddle Loop", 1_500_000L, 80)
        var activatedArea: OfflineMapAreaEntity? = null
        var deletedArea: OfflineMapAreaEntity? = null

        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                OfflineMapsScreen(
                    offlineMapAreas = listOf(snakePass, catFiddle),
                    activeOfflineArea = snakePass,
                    plannedRoute = null,
                    myGpsFix = gpsFix,
                    mapDownloadProgress = MapAreaDownloadProgress(),
                    isEffectiveOffline = false,
                    onSearchPlaces = { emptyList() },
                    onDownloadArea = { _, _, _, _ -> },
                    onSelectOfflineArea = { activatedArea = it },
                    onDeleteOfflineArea = { deletedArea = it },
                    onBack = {},
                    deviceAvailableBytesOverride = 2L * 1024L * 1024L * 1024L
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("offline_maps_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_maps_storage_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_storage_used_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 regions • 200 tiles • 16 turns", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("2.00 GB free", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("download_new_area_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("offline_region_card_area_snake").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_region_card_area_cat").assertIsDisplayed()

        // Filter downloaded regions using the search input
        composeTestRule.onNodeWithTag("offline_maps_search_input").performTextInput("Cat")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("offline_region_card_area_cat").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_region_card_area_snake").assertDoesNotExist()

        composeTestRule.onNodeWithTag("activate_region_area_cat").performClick()
        composeTestRule.waitForIdle()
        assertEquals("area_cat", activatedArea?.areaId)

        composeTestRule.onNodeWithTag("delete_region_area_cat").performClick()
        composeTestRule.waitForIdle()
        assertEquals("area_cat", deletedArea?.areaId)
    }

    @Test
    fun offlineMapsScreen_downloadNewAreaButton_opensSearchAndDownloadsSelectedPlace() {
        var downloadedTitle = ""
        var downloadedCenter: LatLng? = null
        var downloadedRadius = 0.0

        val catalogPlaces = listOf(
            PlaceSearchSuggestion("Winnats Pass", "Castleton Limestone Gorge", 53.3426, -1.7783, true),
            PlaceSearchSuggestion("Kirkstone Pass", "Lake District Highest Road", 54.4628, -2.9234, true)
        )

        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                OfflineMapsScreen(
                    offlineMapAreas = emptyList(),
                    activeOfflineArea = null,
                    plannedRoute = null,
                    myGpsFix = gpsFix,
                    mapDownloadProgress = MapAreaDownloadProgress(),
                    isEffectiveOffline = false,
                    onSearchPlaces = { q ->
                        if (q.isBlank()) catalogPlaces
                        else catalogPlaces.filter { it.name.contains(q, ignoreCase = true) }
                    },
                    onDownloadArea = { title, center, radius, _ ->
                        downloadedTitle = title
                        downloadedCenter = center
                        downloadedRadius = radius
                    },
                    onSelectOfflineArea = {},
                    onDeleteOfflineArea = {},
                    onBack = {},
                    deviceAvailableBytesOverride = 1_500_000_000L
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("offline_maps_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("download_new_area_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("download_new_area_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_area_search_input").performTextClearance()
        composeTestRule.onNodeWithTag("offline_area_search_input").performTextInput("Kirkstone")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("offline_search_result_0").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("download_radius_20").performClick()
        composeTestRule.onNodeWithTag("confirm_download_area_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Kirkstone Pass", downloadedTitle)
        assertEquals(20.0, downloadedRadius, 0.01)
        assertEquals(54.4628, downloadedCenter?.latitude ?: 0.0, 0.001)
    }

    @Test
    fun routePlannerScreen_switchesCleanlyBetweenTabsAndOpensOfflineMaps() {
        var offlineMapsOpened = false
        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                RoutePlannerScreen(
                    myGpsFix = gpsFix,
                    plannedRoute = null,
                    isPlanning = false,
                    savedOfflineRoutes = emptyList(),
                    isEffectiveOffline = false,
                    onPlanRoute = { _, _, _ -> },
                    onSaveRouteOffline = {},
                    onLoadOfflineRoute = {},
                    onBroadcastWeatherAlert = {},
                    onOpenOfflineMaps = { offlineMapsOpened = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("route_open_offline_maps_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(offlineMapsOpened)

        composeTestRule.onNodeWithTag("route_tab_scenic").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("scenic_route_suggester_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("route_tab_weather").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("proactive_weather_shield_card").assertIsDisplayed()
    }
}
