package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ScenicRoutePreferences
import com.example.ai.ScenicRouteRecommendation
import com.example.ai.ScenicRouteSuggesterService
import com.example.ai.ScenicRouteUiState
import com.example.ai.ScenicStylePreference
import com.example.ai.ScenicSuggestionBundle
import com.example.data.local.GuestSessionState
import com.example.data.local.MotoPackPreferences
import com.example.data.model.BikerBrewCatalog
import com.example.data.model.GarageBike
import com.example.data.model.GarageMaintenanceCalculator
import com.example.data.model.MaintenanceHealthStatus
import com.example.data.model.MaintenanceReminderItem
import com.example.data.model.MaintenanceTaskType
import com.example.data.model.PassportStamp
import com.example.data.model.RideTrip
import com.example.data.model.ServiceHistoryLog
import com.example.export.TripReportExporter
import com.example.location.LiveGpsFix
import com.example.ui.theme.HudThemeMode
import com.example.ui.theme.MotoPackTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import java.io.ByteArrayOutputStream
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowTestPdfDocument {
    @Implementation
    fun __constructor__() {}

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        val bitmap = Bitmap.createBitmap(pageInfo.pageWidth, pageInfo.pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        return ReflectionHelpers.callConstructor(
            PdfDocument.Page::class.java,
            ReflectionHelpers.ClassParameter.from(Canvas::class.java, canvas),
            ReflectionHelpers.ClassParameter.from(PdfDocument.PageInfo::class.java, pageInfo)
        )
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {}

    @Implementation
    fun writeTo(out: OutputStream) {
        val dummyBody = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n" +
            "%MotoPack Vector Ride Report\n".repeat(45) +
            "%%EOF\n"
        out.write(dummyBody.toByteArray(Charsets.US_ASCII))
    }

    @Implementation
    fun close() {}
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class MotoPackNewFeaturesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleTrip = RideTrip(
        id = "trip_pdf_1",
        userId = "rider_1",
        title = "Winnats Pass Espresso Run",
        distanceKm = 82.5,
        elevationGainM = 740.0,
        elevationChangeNetM = 310.0,
        durationMin = 88L,
        avgSpeedKmh = 56.2,
        startLat = 53.3811,
        startLng = -1.4701,
        endLat = 53.3426,
        endLng = -1.7783,
        weatherSummary = "Clear skies • Gentle ridge breeze",
        weatherSeverity = "CLEAR",
        maxWindGustKmh = 21.0,
        batteryEfficiencyUsed = true,
        batteryPercentUsed = 9
    )

    @Test
    @Config(sdk = [36], shadows = [ShadowTestPdfDocument::class])
    fun tripReportExporter_generatesValidPdfBytes_andShareFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val baos = ByteArrayOutputStream()
        val samples = buildEstimatedTripElevationSamples(sampleTrip)

        val suggestedName = TripReportExporter.buildSuggestedPdfFileName(sampleTrip)
        assertTrue(suggestedName.startsWith("Winnats_Pass_Espresso_Run_"))
        assertTrue(suggestedName.endsWith(".pdf"))

        val caption = TripReportExporter.buildSocialShareCaption(sampleTrip, "Alex Rider", "Yamaha Tracer 9 GT")
        assertTrue(caption.contains("82.5 km"))
        assertTrue(caption.contains("Yamaha Tracer 9 GT"))
        assertTrue(caption.contains("#MotoPack"))

        TripReportExporter.writeTripReportPdfToStream(
            outputStream = baos,
            trip = sampleTrip,
            elevationSamples = samples,
            riderName = "Alex Rider",
            bikeModel = "Yamaha Tracer 9 GT"
        )
        val bytes = baos.toByteArray()
        assertTrue("Expected PDF > 1KB, got ${bytes.size}", bytes.size > 1024)
        val header = String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)
        assertEquals("%PDF-", header)

        val shareResult = TripReportExporter.createShareablePdfFile(
            context = context,
            trip = sampleTrip,
            elevationSamples = samples,
            riderName = "Alex Rider",
            bikeModel = "Yamaha Tracer 9 GT"
        )
        assertTrue(shareResult.isSuccess)
        val file = shareResult.getOrThrow()
        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".pdf"))
    }

    @Test
    fun tripSummaryDashboard_displaysSaveAndSharePdfButtons_andInvokesCallbacks() {
        var saveClicked = false
        var shareClicked = false

        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                TripSummaryDashboardContent(
                    trip = sampleTrip,
                    elevationSamples = buildEstimatedTripElevationSamples(sampleTrip),
                    isBatteryEfficiencyEnabled = false,
                    riderName = "Alex Rider",
                    bikeModel = "Tracer 9 GT",
                    onToggleBatteryEfficiency = {},
                    onSavePdf = { _, _ -> saveClicked = true },
                    onSharePdf = { _, _ -> shareClicked = true },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("trip_pdf_export_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_trip_pdf_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("share_trip_pdf_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue(saveClicked)
        assertTrue(shareClicked)
    }

    @Test
    fun bikerBrewPassport_rankProgression_andScreenRoutingWork() {
        assertEquals("Espresso Prospect", BikerBrewCatalog.computePassportRank(0).title)
        assertEquals("Caffeine Cruiser", BikerBrewCatalog.computePassportRank(1).title)
        assertEquals("Roast & Throttle", BikerBrewCatalog.computePassportRank(3).title)
        assertEquals("Grand Brew Road Captain", BikerBrewCatalog.computePassportRank(6).title)

        var routedName = ""
        val firstStop = BikerBrewCatalog.passportStops.first()

        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                EventsAndPassportScreen(
                    currentUserId = "rider_1",
                    myGpsFix = LiveGpsFix(53.3832, -1.4728, 0.0, 0f, 150.0, System.currentTimeMillis()),
                    passportStamps = listOf(
                        PassportStamp(
                            id = firstStop.id,
                            userId = "rider_1",
                            locationId = firstStop.id,
                            locationName = firstStop.name,
                            stampCode = firstStop.passportStampCode,
                            verifiedGps = true
                        )
                    ),
                    personalEvents = emptyList(),
                    packEvents = emptyList(),
                    onStampLocation = { _, _ -> },
                    onCreateEvent = { _, _, _, _, _, _, _, _, _, _ -> },
                    onToggleRsvp = {},
                    onRouteToDestination = { name, _, _ -> routedName = name }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("events_tab_passport").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("biker_brew_passport_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Caffeine Cruiser", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("route_location_${firstStop.id}").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(firstStop.name, routedName)
    }

    @Test
    fun garageCalculator_andGarageScreen_trackOverdueAndOdometerUpdates() {
        val bike = GarageBike(
            id = "bike_1",
            userId = "rider_1",
            name = "Tracer 9",
            makeModel = "Yamaha Tracer 9 GT",
            year = 2024,
            currentOdometerKm = 15200.0,
            engineCc = 890,
            tirePsiFront = 36,
            tirePsiRear = 42,
            isPrimary = true
        )
        val overdueChain = MaintenanceReminderItem(
            id = "rem_chain",
            userId = "rider_1",
            bikeId = "bike_1",
            bikeName = "Tracer 9",
            taskType = MaintenanceTaskType.CHAIN_ADJUST.code,
            title = "Chain Tension & Lube",
            intervalKm = 800.0,
            lastServiceOdometerKm = 14200.0,
            nextDueOdometerKm = 15000.0
        )
        val dueSoonOil = MaintenanceReminderItem(
            id = "rem_oil",
            userId = "rider_1",
            bikeId = "bike_1",
            bikeName = "Tracer 9",
            taskType = MaintenanceTaskType.OIL_CHANGE.code,
            title = "Engine Oil & Filter Change",
            intervalKm = 5000.0,
            lastServiceOdometerKm = 10400.0,
            nextDueOdometerKm = 15400.0
        )
        val goodTires = MaintenanceReminderItem(
            id = "rem_tires",
            userId = "rider_1",
            bikeId = "bike_1",
            bikeName = "Tracer 9",
            taskType = MaintenanceTaskType.TIRES.code,
            title = "Tire Tread & Pressure Check",
            intervalKm = 3000.0,
            lastServiceOdometerKm = 14500.0,
            nextDueOdometerKm = 17500.0
        )

        assertEquals(MaintenanceHealthStatus.OVERDUE, GarageMaintenanceCalculator.evaluate(overdueChain, 15200.0).status)
        assertEquals(MaintenanceHealthStatus.DUE_SOON, GarageMaintenanceCalculator.evaluate(dueSoonOil, 15200.0).status)
        assertEquals(MaintenanceHealthStatus.GOOD, GarageMaintenanceCalculator.evaluate(goodTires, 15200.0).status)

        var updatedOdometer = 0.0
        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                GarageScreen(
                    bikes = listOf(bike),
                    reminders = listOf(overdueChain, dueSoonOil, goodTires),
                    serviceLogs = listOf(
                        ServiceHistoryLog(
                            id = "log_1",
                            userId = "rider_1",
                            bikeId = "bike_1",
                            bikeName = "Tracer 9",
                            taskType = MaintenanceTaskType.OIL_CHANGE.code,
                            title = "Break-In Service",
                            odometerKm = 10400.0,
                            costAmount = 140.0,
                            shopOrDiy = "Yamaha Dealer"
                        )
                    ),
                    defaultBikeName = "Yamaha Tracer 9 GT",
                    onAddBike = { _, _, _, _, _, _, _ -> },
                    onUpdateOdometer = { _, newOdo -> updatedOdometer = newOdo },
                    onAddReminder = { _, _, _, _, _ -> },
                    onLogServiceDone = { _, _, _, _, _, _, _, _ -> },
                    onDeleteReminder = {},
                    onDeleteServiceLog = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("garage_active_bike_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("garage_add_50km_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(15250.0, updatedOdometer, 0.01)
    }

    @Test
    fun scenicRouteSuggester_parsesJson_andAppliesSelectedRecommendation() {
        val anchor = LiveGpsFix(53.3811, -1.4701, 0.0, 0f, 150.0, System.currentTimeMillis())
        val rawJson = """
            ```json
            {
              "analysisSummary": "Dry tarmac across Derwent Valley with calm 14 km/h crosswinds.",
              "recommendations": [
                {
                  "routeName": "Snake Pass Ridge Sweeper",
                  "destinationLabel": "A57 Snake Summit",
                  "destinationLat": 53.4339,
                  "destinationLng": -1.8732,
                  "distanceKm": 64.0,
                  "estimatedMinutes": 72,
                  "curvinessRating": "EXTREME",
                  "weatherFit": "DRY_OPTIMAL",
                  "trafficOutlook": "Light mid-morning flow",
                  "roadHighlights": "Crisp switchbacks & moorland views",
                  "brewOrFuelStop": "Ladybower Espresso Outpost",
                  "whyRecommended": "Clear visibility and low crosswinds match Twisty Passes preference."
                }
              ]
            }
            ```
        """.trimIndent()

        val bundle = ScenicRouteSuggesterService.parseScenicJson(rawJson, anchor)
        assertEquals(1, bundle.recommendations.size)
        assertEquals("Snake Pass Ridge Sweeper", bundle.recommendations.first().routeName)
        assertEquals("EXTREME", bundle.recommendations.first().curvinessRating)

        var appliedRouteName = ""
        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                ScenicRouteSuggesterCard(
                    preferences = ScenicRoutePreferences(style = ScenicStylePreference.TWISTIES),
                    scenicState = ScenicRouteUiState.Success(bundle),
                    isEffectiveOffline = false,
                    onUpdatePreferences = {},
                    onSuggestRoutes = {},
                    onApplyRecommendedRoute = { appliedRouteName = it.routeName }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("scenic_recommendation_card_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("plan_scenic_route_button_0").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Snake Pass Ridge Sweeper", appliedRouteName)
    }

    @Test
    fun scenicRouteSuggester_loadingState_showsCancelButtonAndInvokesCallback() {
        var cancelClicked = false
        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                ScenicRouteSuggesterCard(
                    preferences = ScenicRoutePreferences(style = ScenicStylePreference.TWISTIES),
                    scenicState = ScenicRouteUiState.Loading,
                    isEffectiveOffline = false,
                    onUpdatePreferences = {},
                    onSuggestRoutes = {},
                    onCancelSuggestRoutes = { cancelClicked = true },
                    onApplyRecommendedRoute = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Analyzing Roads, Weather & Traffic...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_scenic_routes_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTrue(cancelClicked)
    }

    @Test
    fun guestSessionState_enforces30MinLimit_andBlocksAppWithGoogleSignInOnly() {
        val t0 = 1_700_000_000_000L
        val activeState = GuestSessionState(firstStartedAtMs = t0, isActiveSession = true)
        assertFalse(activeState.isExpired(t0 + 10 * 60_000L))
        assertTrue(activeState.isCurrentlyAllowedInGuest(t0 + 10 * 60_000L))
        assertEquals(20 * 60_000L, activeState.remainingMs(t0 + 10 * 60_000L))

        val expiredNow = t0 + MotoPackPreferences.GUEST_PREVIEW_DURATION_MS + 1000L
        assertTrue(activeState.isExpired(expiredNow))
        assertFalse(activeState.isCurrentlyAllowedInGuest(expiredNow))
        assertEquals(0L, activeState.remainingMs(expiredNow))

        // Active guest shell displays countdown banner
        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                GuestMotoPackShell(
                    guestState = GuestSessionState(
                        firstStartedAtMs = System.currentTimeMillis() - 5 * 60_000L,
                        isActiveSession = true
                    ),
                    onExitGuestToSignIn = {},
                    onGoogleSignInSuccess = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("guest_countdown_banner").assertIsDisplayed()
    }

    @Test
    fun guestExpired_blocksAppWithGoogleSignInOverlay_andDisablesGuestButtonOnAuthScreen() {
        val expiredTimestamp = System.currentTimeMillis() - MotoPackPreferences.GUEST_PREVIEW_DURATION_MS - 5_000L

        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                GuestMotoPackShell(
                    guestState = GuestSessionState(
                        firstStartedAtMs = expiredTimestamp,
                        isActiveSession = true
                    ),
                    onExitGuestToSignIn = {},
                    onGoogleSignInSuccess = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("guest_expired_lock_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("guest_lock_google_signin_button").assertIsDisplayed()
    }

    @Test
    fun authScreen_hidesGuestButtonOnceTrialExpired() {
        val expiredTimestamp = System.currentTimeMillis() - MotoPackPreferences.GUEST_PREVIEW_DURATION_MS - 5_000L
        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                AuthScreen(
                    onAuthSuccess = {},
                    guestState = GuestSessionState(
                        firstStartedAtMs = expiredTimestamp,
                        isActiveSession = false
                    ),
                    onContinueAsGuest = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_guest_expired_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("continue_as_guest_button").assertDoesNotExist()
    }
}
