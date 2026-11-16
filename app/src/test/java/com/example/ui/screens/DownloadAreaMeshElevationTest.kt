package com.example.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.example.data.crypto.PackCrypto
import com.example.data.local.MapAreaDownloadProgress
import com.example.data.local.OfflineMapAreaEntity
import com.example.data.local.OfflineMapAreaManager
import com.example.data.local.RiderSettings
import com.example.data.model.RidePack
import com.example.data.model.RideTrip
import com.example.data.remote.ElevationSample
import com.example.data.remote.FuelStationStop
import com.example.location.LiveGpsFix
import com.example.mesh.MeshIncomingEvent
import com.example.mesh.MeshMessageBody
import com.example.mesh.MeshTelemetryBody
import com.example.mesh.MeshVpnStatus
import com.example.mesh.MeshWireEnvelope
import com.example.mesh.MotoPackMeshVpnService
import com.example.mesh.P2pMeshUiState
import com.example.mesh.PackP2pMeshManager
import com.example.ui.theme.HudThemeMode
import com.example.ui.theme.MotoPackTheme
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class DownloadAreaMeshElevationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun offlineMapAreaEstimate_scalesWithRadiusAndCapsAtMaxTiles() {
        val center = LatLng(53.4339, -1.8732)
        val est5 = OfflineMapAreaManager.estimateAreaDownload(center, emptyList(), 5.0)
        val est10 = OfflineMapAreaManager.estimateAreaDownload(center, emptyList(), 10.0)
        val est20 = OfflineMapAreaManager.estimateAreaDownload(center, emptyList(), 20.0)

        assertTrue(est5.tileCount in 9..OfflineMapAreaManager.DEFAULT_MAX_TILES)
        assertTrue(est10.tileCount >= est5.tileCount)
        assertTrue(est20.tileCount >= est10.tileCount)
        assertTrue(est20.tileCount <= OfflineMapAreaManager.DEFAULT_MAX_TILES)
        assertEquals("Z11–Z14", est10.zoomRangeLabel)
        assertTrue(est10.estimatedBytes > est5.estimatedBytes || est10.tileCount == est5.tileCount)
    }

    @Test
    fun virtualMeshIp_isDeterministicAndWithinMeshSubnet() {
        val ipA1 = MotoPackMeshVpnService.computeVirtualMeshIp("pack_peak", "rider_alice")
        val ipA2 = MotoPackMeshVpnService.computeVirtualMeshIp("pack_peak", "rider_alice")
        val ipB = MotoPackMeshVpnService.computeVirtualMeshIp("pack_peak", "rider_bob")

        assertEquals(ipA1, ipA2)
        assertTrue(ipA1.startsWith("10.66."))
        assertTrue(ipB.startsWith("10.66."))
        assertTrue(ipA1 != ipB)
        val octets = ipA1.split(".").map { it.toInt() }
        assertEquals(4, octets.size)
        assertTrue(octets[2] in 2..251)
        assertTrue(octets[3] in 2..251)
    }

    @Test
    fun p2pMeshManager_decryptsValidEnvelopeDeduplicatesReplayAndRejectsWrongKey() = runBlocking {
        val mesh = PackP2pMeshManager(context = null)
        try {
            mesh.startMesh(
                packId = "pack_snake",
                inviteCode = "SNAKE9",
                encryptionSalt = "salt_xyz",
                userId = "rider_me"
            )

            val validKey = PackCrypto.derivePackKey("SNAKE9", "salt_xyz")
            val telemetryBody = MeshTelemetryBody(
                userId = "rider_peer",
                displayName = "Alex Tracer",
                bikeModel = "Yamaha Tracer 9",
                lat = 53.434,
                lng = -1.874,
                speedKmh = 68.0,
                altitudeM = 512.0,
                batteryPercent = 79
            )
            val validEnvelope = MeshWireEnvelope(
                packetId = "pkt_001",
                originUserId = "rider_peer",
                packId = "pack_snake",
                kind = PackP2pMeshManager.KIND_TELEMETRY,
                ttl = 1,
                encryptedPayload = PackCrypto.encryptString(json.encodeToString(telemetryBody), validKey)
            )
            val wireJson = json.encodeToString(validEnvelope)

            var receivedEvent: MeshIncomingEvent? = null
            val collectJob = launch {
                receivedEvent = mesh.incomingEvents.first()
            }
            kotlinx.coroutines.yield()

            assertTrue(mesh.processIncomingWireJsonForTest(wireJson, "192.168.49.15"))
            collectJob.join()
            assertTrue(receivedEvent is MeshIncomingEvent.Telemetry)
            assertEquals("Alex Tracer", (receivedEvent as MeshIncomingEvent.Telemetry).body.displayName)

            // Replayed packetId must be ignored
            assertFalse(mesh.processIncomingWireJsonForTest(wireJson, "192.168.49.15"))

            // Wrong pack key must fail decryption
            val wrongKey = PackCrypto.derivePackKey("WRONG1", "salt_xyz")
            val msgBody = MeshMessageBody(
                messageId = "msg_01",
                senderId = "rider_peer",
                senderName = "Alex",
                text = "Gravel on bend"
            )
            val badEnvelope = MeshWireEnvelope(
                packetId = "pkt_002",
                originUserId = "rider_peer",
                packId = "pack_snake",
                kind = PackP2pMeshManager.KIND_CHAT,
                ttl = 1,
                encryptedPayload = PackCrypto.encryptString(json.encodeToString(msgBody), wrongKey)
            )
            assertFalse(mesh.processIncomingWireJsonForTest(json.encodeToString(badEnvelope), "192.168.49.15"))
        } finally {
            mesh.release()
        }
    }

    @Test
    fun elevationMetrics_calculatesClimbDescentSummitAndGrade() {
        val samples = listOf(
            ElevationSample(distanceKm = 0.0, elevationM = 120.0, lat = 53.38, lng = -1.80),
            ElevationSample(distanceKm = 1.0, elevationM = 220.0, lat = 53.39, lng = -1.81), // +10% grade
            ElevationSample(distanceKm = 2.0, elevationM = 180.0, lat = 53.40, lng = -1.82), // -4% grade
            ElevationSample(distanceKm = 3.0, elevationM = 340.0, lat = 53.41, lng = -1.83)  // +16% grade
        )
        val metrics = computeElevationMetrics(samples)
        assertEquals(120.0, metrics.minElevationM, 0.1)
        assertEquals(340.0, metrics.maxElevationM, 0.1)
        assertEquals(260.0, metrics.totalAscentM, 0.1)
        assertEquals(40.0, metrics.totalDescentM, 0.1)
        assertEquals(16.0, metrics.maxGradePercent, 0.2)
        assertEquals(3, metrics.peakIndex)
    }

    @Test
    fun elevationGraphCard_tapUpdatesScrubInspectTelemetry() {
        val samples = listOf(
            ElevationSample(distanceKm = 0.0, elevationM = 150.0, lat = 53.35, lng = -1.75),
            ElevationSample(distanceKm = 5.0, elevationM = 480.0, lat = 53.37, lng = -1.70),
            ElevationSample(distanceKm = 10.0, elevationM = 260.0, lat = 53.40, lng = -1.65)
        )
        val fuelStops = listOf(
            FuelStationStop(
                id = "f1",
                name = "Ladybower Fuel",
                address = "A57 Snake Rd, Bamford",
                lat = 53.37,
                lng = -1.70,
                distanceAlongKm = 5.0
            )
        )

        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.DAYLIGHT) {
                ElevationGraphCard(
                    title = "Snake Pass Terrain",
                    samples = samples,
                    sourceLabel = "Live Terrain",
                    fuelStations = fuelStops
                )
            }
        }

        composeTestRule.onNodeWithTag("elevation_graph_card").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("480m").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("+330m").assertIsDisplayed()
        composeTestRule.onNodeWithText("Drag or tap profile", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("elevation_graph_canvas").performTouchInput {
            click(Offset(width * 0.5f, height * 0.5f))
        }

        composeTestRule.onNodeWithText("grade", substring = true).assertIsDisplayed()
    }

    @Test
    fun downloadAreaSheet_displaysEstimateAndTriggersDownloadCallback() {
        var capturedTitle = ""
        var capturedRadius = 0.0

        val cachedArea = OfflineMapAreaEntity(
            areaId = "area_1",
            packId = "pack_1",
            title = "Snake Pass Sector",
            centerLat = 53.43,
            centerLng = -1.87,
            minLat = 53.38,
            maxLat = 53.48,
            minLng = -1.95,
            maxLng = -1.78,
            radiusKm = 10.0,
            zoomRangeLabel = "Z11–Z14",
            tileCount = 48,
            storageBytes = 780_000L,
            routePolyline = "",
            navStepCount = 6,
            fuelStopCount = 2,
            elevationSampleCount = 12,
            maxElevationM = 512.0,
            elevationGainM = 340.0,
            elevationPointsJson = json.encodeToString(
                listOf(
                    ElevationSample(0.0, 180.0, 53.40, -1.90),
                    ElevationSample(5.0, 512.0, 53.43, -1.87),
                    ElevationSample(10.0, 290.0, 53.46, -1.84)
                )
            )
        )

        composeTestRule.setContent {
            MotoPackTheme {
                DownloadAreaSheetContent(
                    plannedRoute = null,
                    mapCenterLatLng = LatLng(53.4339, -1.8732),
                    myGpsFix = LiveGpsFix(
                        lat = 53.3811,
                        lng = -1.4701,
                        speedKmh = 45.0,
                        headingDeg = 90f,
                        altitudeM = 180.0,
                        timestampMs = 1_700_000_000_000L
                    ),
                    offlineMapAreas = listOf(cachedArea),
                    activeOfflineArea = null,
                    mapDownloadProgress = MapAreaDownloadProgress(),
                    isEffectiveOffline = false,
                    isP2pMeshActive = true,
                    isMeshVpnActive = true,
                    meshVirtualIp = "10.66.12.44",
                    onDownloadArea = { title, _, radius, _ ->
                        capturedTitle = title
                        capturedRadius = radius
                    },
                    onSelectOfflineArea = {},
                    onDeleteOfflineArea = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("download_area_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("P2P Mesh ON", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("VPN 10.66.12.44", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("download_radius_20").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("download_source_gps").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("download_area_name_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_download_area_button").performScrollTo().performClick()

        assertEquals("Canyon Dead-Zone Sector", capturedTitle)
        assertEquals(20.0, capturedRadius, 0.01)
        composeTestRule.onNodeWithText("Snake Pass Sector").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tripSummaryDashboard_rendersInteractiveElevationGraph() {
        val trip = RideTrip(
            id = "trip_42",
            title = "Cat & Fiddle Summit Loop",
            distanceKm = 42.5,
            elevationGainM = 620.0,
            elevationChangeNetM = 180.0,
            durationMin = 55L,
            avgSpeedKmh = 46.4
        )
        composeTestRule.setContent {
            MotoPackTheme {
                TripSummaryDashboardContent(
                    trip = trip,
                    elevationSamples = buildEstimatedTripElevationSamples(trip),
                    isBatteryEfficiencyEnabled = false,
                    onToggleBatteryEfficiency = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("trip_elevation_graph_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Ride Elevation Profile").assertIsDisplayed()
    }

    @Test
    fun packHubScreen_togglesP2pMeshAndMeshVpnAndLinksWifiDirectPeer() {
        var p2pToggled = false
        var vpnToggled = false
        var linkedDeviceAddr = ""

        composeTestRule.setContent {
            MotoPackTheme {
                PackHubScreen(
                    currentUserId = "uid_me",
                    userProfile = null,
                    userPacks = emptyList(),
                    activePack = RidePack(id = "pack_1", name = "Peak Pack", inviteCode = "PEAK99"),
                    riders = emptyList(),
                    messages = emptyList(),
                    directMessageTarget = null,
                    settings = RiderSettings(p2pMeshEnabled = true, meshVpnEnabled = false),
                    isEffectiveOffline = true,
                    pendingBreadcrumbsCount = 4,
                    p2pMeshState = P2pMeshUiState(
                        isActive = true,
                        localTcpPort = 47821,
                        virtualMeshIp = "10.66.40.12",
                        wifiDirectPeers = listOf(
                            com.example.mesh.DiscoveredP2pPeer(
                                deviceName = "Sam BMW GS",
                                deviceAddress = "aa:bb:cc:dd:ee:01",
                                statusLabel = "Available"
                            )
                        ),
                        transportSummary = "Wi-Fi Direct + Mesh UDP/TCP active on :47821"
                    ),
                    meshVpnStatus = MeshVpnStatus(isActive = true, virtualIp = "10.66.40.12", packetsRouted = 14L),
                    onCreatePack = { _, _, _ -> },
                    onJoinPack = {},
                    onSwitchPack = {},
                    onSelectMessageTarget = {},
                    onSendMessage = { _, _ -> },
                    onRingRider = {},
                    onUpdateProfile = { _, _, _ -> },
                    onSetHudMode = {},
                    onSetEcoMode = {},
                    onSetManualOffline = {},
                    onSetP2pMesh = { p2pToggled = !p2pToggled },
                    onSetMeshVpn = { vpnToggled = it },
                    onConnectWifiDirectPeer = { linkedDeviceAddr = it },
                    onAddManualMeshPeer = {},
                    onSetSeparationKm = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("pack_tab_settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("p2p_mesh_switch").performScrollTo().performClick()
        assertTrue(p2pToggled)

        composeTestRule.onNodeWithTag("mesh_vpn_switch").performScrollTo().performClick()
        assertTrue(vpnToggled)

        composeTestRule.onNodeWithText("TUN UP").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_mesh_peer_ip_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Link").performScrollTo().performClick()
        assertEquals("aa:bb:cc:dd:ee:01", linkedDeviceAddr)
    }
}
