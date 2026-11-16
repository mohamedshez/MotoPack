package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ai.AiBriefingUiState
import com.example.ai.AiCopilotUiState
import com.example.audio.PttPlaybackUiState
import com.example.audio.PttRecordingUiState
import com.example.data.local.MapAreaDownloadProgress
import com.example.data.local.OfflineMapAreaEntity
import com.example.data.model.BikerBrewCatalog
import com.example.data.model.DecryptedPackMessage
import com.example.data.model.DecryptedRiderState
import com.example.data.model.MapChallenge
import com.example.data.model.PackGalleryPhoto
import com.example.data.model.PackMapPin
import com.example.data.model.RidePack
import com.example.data.model.RideTrip
import com.example.data.remote.ComputedMotoRoute
import com.example.data.remote.RiderLiveWeatherAlert
import com.example.location.LiveGpsFix
import com.example.mesh.MeshVpnStatus
import com.example.mesh.P2pMeshUiState
import com.example.ui.theme.TelemetryMonoFontFamily
import com.example.ui.viewmodel.IncomingSosAlert
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.TileProvider
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val NIGHT_HUD_MAP_STYLE = """
[
  {"elementType":"geometry","stylers":[{"color":"#121821"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#8fa3ba"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#0c1017"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#243040"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#33445a"}]},
  {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#1b2430"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0a131f"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#13221f"}]}
]
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapRadarScreen(
    currentUserId: String,
    activePack: RidePack?,
    riders: List<DecryptedRiderState>,
    selectedRider: DecryptedRiderState?,
    myGpsFix: LiveGpsFix,
    hasLocationPermission: Boolean,
    hasRealGpsFix: Boolean = false,
    plannedRoute: ComputedMotoRoute?,
    challenges: List<MapChallenge>,
    userTrips: List<RideTrip>,
    packMarkers: List<PackMapPin> = emptyList(),
    messages: List<DecryptedPackMessage> = emptyList(),
    packPhotos: List<PackGalleryPhoto> = emptyList(),
    offlineMapAreas: List<OfflineMapAreaEntity> = emptyList(),
    activeOfflineArea: OfflineMapAreaEntity? = null,
    mapDownloadProgress: MapAreaDownloadProgress = MapAreaDownloadProgress(),
    offlineTileProvider: TileProvider? = null,
    isEffectiveOffline: Boolean,
    pendingBreadcrumbCount: Int,
    ecoRideMode: Boolean,
    isRideTrackingActive: Boolean,
    sessionDistanceKm: Double,
    liveWeatherAlert: RiderLiveWeatherAlert? = null,
    isRefreshingWeather: Boolean = false,
    aiBriefingState: AiBriefingUiState = AiBriefingUiState.Idle,
    aiCopilotState: AiCopilotUiState = AiCopilotUiState.Idle,
    p2pMeshState: P2pMeshUiState = P2pMeshUiState(),
    meshVpnStatus: MeshVpnStatus = MeshVpnStatus(),
    onSelectRider: (DecryptedRiderState?) -> Unit,
    onRingRider: (DecryptedRiderState) -> Unit,
    onMessageRider: (DecryptedRiderState) -> Unit,
    onToggleRideTracking: (Boolean) -> Unit,
    onToggleEcoMode: (Boolean) -> Unit,
    onNavigateToPackSetup: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRefreshWeather: () -> Unit = {},
    onBroadcastWeatherAlert: (String) -> Unit = {},
    onRunAiBriefing: () -> Unit = {},
    onAskAiCopilot: (String) -> Unit = {},
    onCreatePackPin: (pinType: String, title: String, description: String, lat: Double, lng: Double, severity: String) -> Unit = { _, _, _, _, _, _ -> },
    onConfirmPackPin: (PackMapPin) -> Unit = {},
    onResolvePackPin: (PackMapPin) -> Unit = {},
    onDeletePackPin: (PackMapPin) -> Unit = {},
    onSendGroupChat: (String) -> Unit = {},
    onUploadGalleryPhoto: (Uri, String, String) -> Unit = { _, _, _ -> },
    onDeleteGalleryPhoto: (PackGalleryPhoto) -> Unit = {},
    onSelectOfflineArea: (OfflineMapAreaEntity?) -> Unit = {},
    onDownloadArea: (title: String, center: LatLng, radiusKm: Double, useActiveRoute: Boolean) -> Unit = { _, _, _, _ -> },
    onDeleteOfflineArea: (OfflineMapAreaEntity) -> Unit = {},
    onOpenOfflineMaps: () -> Unit = {},
    isSosActive: Boolean = false,
    sosLastBroadcastMs: Long? = null,
    myBatteryPercent: Int = 100,
    incomingSosAlerts: List<IncomingSosAlert> = emptyList(),
    onTriggerSos: (String) -> Unit = {},
    onStandDownSos: () -> Unit = {},
    onAcknowledgeSos: (String) -> Unit = {},
    onOpenRiderHud: () -> Unit = {},
    pttRecordingState: PttRecordingUiState = PttRecordingUiState(),
    pttPlaybackState: PttPlaybackUiState = PttPlaybackUiState(),
    pttAutoPlayEnabled: Boolean = true,
    pendingVoiceCount: Int = 0,
    onStartPttRecording: () -> Unit = {},
    onStopAndSendPttRecording: () -> Unit = {},
    onCancelPttRecording: () -> Unit = {},
    onTogglePlayVoice: (DecryptedPackMessage) -> Unit = {},
    onTogglePttAutoPlay: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkHud = MaterialTheme.colorScheme.background.luminance() < 0.3f

    var showStatsSheetFor by remember { mutableStateOf<DecryptedRiderState?>(null) }
    var showAiCopilotSheet by remember { mutableStateOf(false) }
    var showSosSheet by remember { mutableStateOf(false) }
    var showPinFilters by rememberSaveable { mutableStateOf(false) }
    var createPinLatLng by remember { mutableStateOf<LatLng?>(null) }
    var selectedPackPinId by remember { mutableStateOf<String?>(null) }
    var pinTypeFilter by rememberSaveable { mutableStateOf("ALL") }
    var showPackCommsSheet by remember { mutableStateOf(false) }
    var packCommsInitialTab by remember { mutableIntStateOf(0) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var hasCenteredOnUserLocation by rememberSaveable { mutableStateOf(false) }

    val selectedPackPin = remember(selectedPackPinId, packMarkers) {
        selectedPackPinId?.let { id -> packMarkers.firstOrNull { it.id == id } }
    }
    val visiblePackPins = remember(packMarkers, pinTypeFilter) {
        if (pinTypeFilter == "ALL") packMarkers else packMarkers.filter { it.pinType == pinTypeFilter }
    }
    val groupMessagesCount = remember(messages) { messages.count { it.recipientId.isBlank() } }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(myGpsFix.lat, myGpsFix.lng), 14f)
    }

    LaunchedEffect(isMapLoaded, hasLocationPermission, hasRealGpsFix, myGpsFix.lat, myGpsFix.lng) {
        if (isMapLoaded && hasLocationPermission && hasRealGpsFix && !hasCenteredOnUserLocation) {
            hasCenteredOnUserLocation = true
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(myGpsFix.lat, myGpsFix.lng), 15f)
            )
        }
    }

    val routePoints = remember(plannedRoute, activePack?.activeRoutePolyline) {
        when {
            plannedRoute != null && plannedRoute.points.isNotEmpty() -> plannedRoute.points
            !activePack?.activeRoutePolyline.isNullOrBlank() -> {
                runCatching { PolyUtil.decode(activePack!!.activeRoutePolyline) }.getOrDefault(emptyList())
            }
            else -> emptyList()
        }
    }

    val mapProperties = remember(isDarkHud, hasLocationPermission, isEffectiveOffline) {
        MapProperties(
            isTrafficEnabled = !isEffectiveOffline,
            isMyLocationEnabled = hasLocationPermission,
            mapType = if (isDarkHud) MapType.NORMAL else MapType.TERRAIN,
            mapStyleOptions = if (isDarkHud) MapStyleOptions(NIGHT_HUD_MAP_STYLE) else null
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false
            ),
            onMapLoaded = { isMapLoaded = true },
            onMapClick = {
                onSelectRider(null)
                selectedPackPinId = null
            },
            onMapLongClick = { tappedLatLng ->
                createPinLatLng = tappedLatLng
            }
        ) {
            if ((isEffectiveOffline || activeOfflineArea != null) && offlineTileProvider != null) {
                TileOverlay(
                    tileProvider = offlineTileProvider,
                    fadeIn = true,
                    zIndex = -1f
                )
            }
            if (routePoints.size >= 2) {
                Polyline(
                    points = routePoints,
                    color = if (isDarkHud) Color(0xFF0A2E36) else Color(0xFF0F294A),
                    width = 18f
                )
                Polyline(
                    points = routePoints,
                    color = if (isDarkHud) Color(0xFF2EE59D) else Color(0xFF1D4ED8),
                    width = 11f
                )
            }

            // OpenWeatherMap rain / high-wind hazard radius & pin on map
            if (liveWeatherAlert != null && (liveWeatherAlert.hasRainAlert || liveWeatherAlert.hasHighWindAlert || liveWeatherAlert.severity == "WARNING")) {
                val hazardColor = if (liveWeatherAlert.severity == "WARNING") Color(0xFFFF5252) else Color(0xFFFFB300)
                val center = LatLng(liveWeatherAlert.lat, liveWeatherAlert.lng)
                Circle(
                    center = center,
                    radius = 4500.0,
                    fillColor = hazardColor.copy(alpha = 0.14f),
                    strokeColor = hazardColor.copy(alpha = 0.65f),
                    strokeWidth = 3f
                )
                val wxPos = LatLng(liveWeatherAlert.lat + 0.014, liveWeatherAlert.lng + 0.014)
                key("wx_hazard") {
                    val wxState = rememberUpdatedMarkerState(position = wxPos)
                    MarkerComposable(
                        keys = arrayOf<Any>(liveWeatherAlert.alertHeadline, liveWeatherAlert.severity),
                        state = wxState,
                        title = liveWeatherAlert.conditionTitle,
                        snippet = liveWeatherAlert.alertHeadline
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = hazardColor,
                            border = BorderStroke(2.dp, Color.White),
                            shadowElevation = 5.dp
                        ) {
                            Icon(
                                imageVector = when {
                                    liveWeatherAlert.hasRainAlert && liveWeatherAlert.hasHighWindAlert -> Icons.Default.Thunderstorm
                                    liveWeatherAlert.hasRainAlert -> Icons.Default.WaterDrop
                                    else -> Icons.Default.Air
                                },
                                contentDescription = liveWeatherAlert.alertHeadline,
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }
            }

            // Fuel stations along route
            plannedRoute?.fuelStations?.forEach { station ->
                key("fuel_${station.id}") {
                    val fuelPos = LatLng(station.lat, station.lng)
                    val markerState = rememberUpdatedMarkerState(position = fuelPos)
                    MarkerComposable(
                        keys = arrayOf<Any>(station.id, isDarkHud),
                        state = markerState,
                        title = station.name,
                        snippet = String.format("+%.1f km along route", station.distanceAlongKm)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFB300),
                            border = BorderStroke(2.dp, Color.White),
                            shadowElevation = 4.dp
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = station.name,
                                tint = Color(0xFF1A1200),
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }
            }

            // Active map challenge checkpoints
            challenges.filter { it.status == "active" && (it.checkpointLat != 0.0 || it.checkpointLng != 0.0) }
                .forEach { ch ->
                    key("ch_${ch.id}") {
                        val pos = LatLng(ch.checkpointLat, ch.checkpointLng)
                        val markerState = rememberUpdatedMarkerState(position = pos)
                        MarkerComposable(
                            keys = arrayOf<Any>(ch.id, ch.currentValue),
                            state = markerState,
                            title = ch.title,
                            snippet = "${ch.currentValue.toInt()}/${ch.targetValue.toInt()} ${ch.unit}"
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF7C3AED),
                                border = BorderStroke(2.dp, Color.White),
                                shadowElevation = 4.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = ch.title,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                    }
                }

            // Curated Biker & Brew coffee stops & hangout venues
            BikerBrewCatalog.curatedLocations.forEach { spot ->
                key("brew_${spot.id}") {
                    val pos = LatLng(spot.lat, spot.lng)
                    val markerState = rememberUpdatedMarkerState(position = pos)
                    MarkerComposable(
                        keys = arrayOf<Any>(spot.id, isDarkHud),
                        state = markerState,
                        title = "${spot.passportStampCode} • ${spot.name}",
                        snippet = spot.brewSpecialty
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF6D4C41),
                            border = BorderStroke(2.dp, Color.White),
                            shadowElevation = 4.dp
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalCafe,
                                contentDescription = spot.name,
                                tint = Color(0xFFFFE0B2),
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }
            }

            // Interactive Pack Pins (Gas Stops, Rest Breaks, Road Hazard Warnings, Emergency SOS Beacons)
            visiblePackPins.forEach { pin ->
                key("pack_pin_${pin.id}") {
                    val pinPos = LatLng(pin.lat, pin.lng)
                    val pinMarkerState = rememberUpdatedMarkerState(position = pinPos)
                    val accent = pinTypeColor(pin.pinType, pin.severity)
                    val isPinSelected = selectedPackPinId == pin.id

                    if (pin.pinType == "EMERGENCY_SOS") {
                        Circle(
                            center = pinPos,
                            radius = 350.0,
                            fillColor = Color(0xFFFF1744).copy(alpha = 0.18f),
                            strokeColor = Color(0xFFFF1744).copy(alpha = 0.85f),
                            strokeWidth = 4f
                        )
                    }

                    MarkerComposable(
                        keys = arrayOf<Any>(
                            pin.id,
                            pin.pinType,
                            pin.severity,
                            pin.confirmedByUids.size,
                            isPinSelected
                        ),
                        state = pinMarkerState,
                        onClick = {
                            onSelectRider(null)
                            selectedPackPinId = pin.id
                            true
                        }
                    ) {
                        PackPinMapMarker(
                            pin = pin,
                            accent = accent,
                            isSelected = isPinSelected
                        )
                    }
                }
            }

            // Rider avatar pins (circular photo/initials with pointer ring & distance badge)
            riders.forEach { rider ->
                key("rider_${rider.userId}") {
                    val pos = LatLng(rider.lat, rider.lng)
                    val markerState = rememberUpdatedMarkerState(position = pos)
                    val isSelected = selectedRider?.userId == rider.userId
                    val isMe = rider.userId == currentUserId

                    MarkerComposable(
                        keys = arrayOf<Any>(
                            rider.userId,
                            rider.distanceFromMeKm.toInt(),
                            rider.batteryPercent,
                            isSelected,
                            rider.isOnline
                        ),
                        state = markerState,
                        onClick = {
                            onSelectRider(rider)
                            true
                        }
                    ) {
                        RiderAvatarMapMarker(
                            rider = rider,
                            isMe = isMe,
                            isSelected = isSelected
                        )
                    }
                }
            }
        }

        // Top Floating Status HUD Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToPackSetup() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted Pack",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = activePack?.name ?: "No Pack Active — Tap to Join",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val meshSuffix = buildString {
                                if (p2pMeshState.isActive) {
                                    append(" • P2P Mesh (${p2pMeshState.activeSocketPeers.size} peers)")
                                }
                                if (meshVpnStatus.isActive) {
                                    append(" • VPN ${meshVpnStatus.virtualIp}")
                                }
                            }
                            Text(
                                text = when {
                                    isEffectiveOffline -> "Offline Cache • $pendingBreadcrumbCount queued$meshSuffix"
                                    ecoRideMode -> "${riders.size} riders • Battery Eco (20s GPS)$meshSuffix"
                                    else -> "${riders.size} riders • AES-256 E2E$meshSuffix"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    p2pMeshState.isActive || meshVpnStatus.isActive -> MaterialTheme.colorScheme.secondary
                                    isEffectiveOffline -> MaterialTheme.colorScheme.primary
                                    ecoRideMode -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Battery Efficiency Mode toggle pill
                        Surface(
                            onClick = { onToggleEcoMode(!ecoRideMode) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (ecoRideMode) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.testTag("eco_mode_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Battery Efficiency Mode",
                                    tint = if (ecoRideMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (ecoRideMode) "BATT ECO" else "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ecoRideMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Start / End Ride tracking pill
                        Surface(
                            onClick = { onToggleRideTracking(!isRideTrackingActive) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isRideTrackingActive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.testTag("toggle_ride_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isRideTrackingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isRideTrackingActive) "End Ride" else "Start Ride",
                                    tint = if (isRideTrackingActive) Color.White else MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isRideTrackingActive) {
                                        String.format("%.1f km", sessionDistanceKm)
                                    } else {
                                        "Ride"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRideTrackingActive) Color.White else MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Incoming Pack Member Emergency SOS Banner
            val activeIncomingSos = incomingSosAlerts.firstOrNull()
            if (activeIncomingSos != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFB71C1C).copy(alpha = 0.96f),
                    border = BorderStroke(2.dp, Color(0xFFFF8A80)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("incoming_sos_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sos,
                                contentDescription = "Rider SOS Alert",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SOS: ${activeIncomingSos.senderName}${
                                    activeIncomingSos.distanceKm?.let { String.format(" (%.1f km away)", it) } ?: ""
                                }",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeIncomingSos.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (activeIncomingSos.lat != null && activeIncomingSos.lng != null) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(activeIncomingSos.lat, activeIncomingSos.lng),
                                                16f
                                            )
                                        )
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.ShareLocation, contentDescription = "Locate SOS rider", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Locate", fontWeight = FontWeight.Bold)
                            }
                        }
                        TextButton(
                            onClick = { onAcknowledgeSos(activeIncomingSos.pingId) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFCDD2)),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Ack")
                        }
                    }
                }
            }

            // Location permission banner if not granted yet
            if (!hasLocationPermission) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Enable GPS to broadcast live location to your pack",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRequestLocationPermission) {
                            Text("Enable")
                        }
                    }
                }
            }

            // Quick-select horizontal rider strip
            if (riders.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(riders, key = { it.userId }) { rider ->
                        val isSel = selectedRider?.userId == rider.userId
                        Surface(
                            onClick = {
                                onSelectRider(rider)
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(rider.lat, rider.lng), 13.5f)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (rider.userId == currentUserId) "You" else rider.displayName.substringBefore(" "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (rider.userId == currentUserId) "${rider.batteryPercent}%" else String.format("%.1f km", rider.distanceFromMeKm),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Compact Map Layer & Pin Filter Strip
            if (packMarkers.isNotEmpty() || activeOfflineArea != null || (isEffectiveOffline && offlineMapAreas.isNotEmpty())) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    if (activeOfflineArea != null || (isEffectiveOffline && offlineMapAreas.isNotEmpty())) {
                        item(key = "offline_tiles_badge") {
                            val labelArea = activeOfflineArea?.title ?: offlineMapAreas.firstOrNull()?.title ?: "Cached Sector"
                            Surface(
                                onClick = onOpenOfflineMaps,
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = "Offline map tiles active",
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Offline: $labelArea",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (activeOfflineArea != null) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear active offline overlay",
                                            tint = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { onSelectOfflineArea(null) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (packMarkers.isNotEmpty()) {
                        item(key = "pin_filter_toggle") {
                            FilterChip(
                                selected = showPinFilters || pinTypeFilter != "ALL",
                                onClick = { showPinFilters = !showPinFilters },
                                label = {
                                    Text(
                                        text = if (pinTypeFilter == "ALL") "Pins (${packMarkers.size})" else "$pinTypeFilter",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                        if (showPinFilters) {
                            val filterItems = listOf(
                                "ALL" to "All (${packMarkers.size})",
                                "GAS_STOP" to "Gas (${packMarkers.count { it.pinType == "GAS_STOP" }})",
                                "REST_BREAK" to "Rest (${packMarkers.count { it.pinType == "REST_BREAK" }})",
                                "HAZARD_WARNING" to "Hazards (${packMarkers.count { it.pinType == "HAZARD_WARNING" }})"
                            )
                            items(filterItems, key = { it.first }) { (typeKey, label) ->
                                FilterChip(
                                    selected = pinTypeFilter == typeKey,
                                    onClick = { pinTypeFilter = typeKey },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("pin_filter_$typeKey")
                                )
                            }
                        }
                    }
                }
            }

            // Real-time OpenWeatherMap Rain / High-Wind Alert Overlay
            liveWeatherAlert?.let { wx ->
                val hasHazard = wx.hasRainAlert || wx.hasHighWindAlert || wx.severity != "CLEAR"
                val accent = when (wx.severity) {
                    "WARNING" -> MaterialTheme.colorScheme.error
                    "ADVISORY" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
                if (hasHazard) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.7f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("map_weather_alert_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        wx.hasRainAlert && wx.hasHighWindAlert -> Icons.Default.Thunderstorm
                                        wx.hasRainAlert -> Icons.Default.WaterDrop
                                        wx.hasHighWindAlert -> Icons.Default.Air
                                        else -> Icons.Default.Warning
                                    },
                                    contentDescription = wx.severity,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = wx.alertHeadline,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = String.format(
                                        "%.0f°C • Wind %.0f km/h (gusts %.0f) • %s",
                                        wx.tempCelsius,
                                        wx.windSpeedKmh,
                                        wx.windGustKmh,
                                        wx.providerLabel
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (activePack != null) {
                                TextButton(
                                    onClick = { onBroadcastWeatherAlert("${wx.locationName}: ${wx.alertHeadline}") },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Alert Pack")
                                }
                            }
                            IconButton(
                                onClick = onRefreshWeather,
                                enabled = !isRefreshingWeather,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh weather",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        onClick = onRefreshWeather,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        shadowElevation = 3.dp,
                        modifier = Modifier.testTag("map_weather_clear_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = "Weather status",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = String.format(
                                    "%s • %.0f°C • Wind %.0f km/h",
                                    wx.locationName,
                                    wx.tempCelsius,
                                    wx.windSpeedKmh
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Bottom-Right HUD Controls (Offline Maps + Drop Pin + Pack Chat/Gallery + AI Copilot + Recenter GPS FAB)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = if (activePack == null) 144.dp else 20.dp
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            BadgedBox(
                badge = {
                    if (offlineMapAreas.isNotEmpty()) {
                        Badge {
                            Text(offlineMapAreas.size.toString())
                        }
                    }
                }
            ) {
                SmallFloatingActionButton(
                    onClick = onOpenOfflineMaps,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("map_download_area_fab")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "Open Offline Maps manager")
                }
            }

            SmallFloatingActionButton(
                onClick = {
                    createPinLatLng = LatLng(myGpsFix.lat, myGpsFix.lng)
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag("drop_pack_pin_fab")
            ) {
                Icon(Icons.Default.AddLocationAlt, contentDescription = "Drop pack pin")
            }

            BadgedBox(
                badge = {
                    val totalItems = groupMessagesCount + packPhotos.size
                    if (totalItems > 0) {
                        Badge {
                            Text(if (totalItems > 99) "99+" else totalItems.toString())
                        }
                    }
                }
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        packCommsInitialTab = 0
                        showPackCommsSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag("map_pack_chat_fab")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Open Pack Group Chat & Gallery")
                }
            }

            SmallFloatingActionButton(
                onClick = { showAiCopilotSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("ai_copilot_fab")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Open MotoPack AI Copilot")
            }

            SmallFloatingActionButton(
                onClick = {
                    if (!hasLocationPermission) {
                        onRequestLocationPermission()
                    } else if (isMapLoaded) {
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(LatLng(myGpsFix.lat, myGpsFix.lng), 15f)
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on my GPS location")
            }
        }

        // Live Push-to-Talk TX Waveform Banner floating cleanly above bottom controls
        if (pttRecordingState.isRecording) {
            PttLiveRecordingBanner(
                recordingState = pttRecordingState,
                onStopAndSend = onStopAndSendPttRecording,
                onCancel = onCancelPttRecording,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 84.dp)
                    .widthIn(max = 520.dp)
            )
        }

        // Bottom-Left Prominent Emergency SOS + Rider HUD + Quick PTT Buttons (accessible >= 48.dp targets)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 16.dp,
                    bottom = if (activePack == null && selectedRider == null) 144.dp else 20.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { showSosSheet = true },
                shape = RoundedCornerShape(26.dp),
                color = if (isSosActive) Color(0xFFFF1744) else Color(0xFFD50000),
                border = BorderStroke(
                    width = if (isSosActive) 2.5.dp else 1.5.dp,
                    color = if (isSosActive) Color.White else Color(0xFFFF8A80)
                ),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .height(52.dp)
                    .testTag("emergency_sos_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sos,
                        contentDescription = if (isSosActive) "Emergency SOS Active" else "Trigger Emergency SOS",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (isSosActive) "SOS ACTIVE" else "SOS",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Surface(
                onClick = onOpenRiderHud,
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .height(52.dp)
                    .testTag("open_rider_hud_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Open Simplified Rider HUD",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "HUD",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (activePack != null) {
                PttHoldOrTapButton(
                    isRecording = pttRecordingState.isRecording,
                    compactLabel = true,
                    testTag = "map_quick_ptt_button",
                    onStartRecording = onStartPttRecording,
                    onStopAndSendRecording = onStopAndSendPttRecording
                )
            }
        }

        // Compact Empty Pack CTA Pill when user has not joined a pack yet
        if (activePack == null && selectedRider == null) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No Riding Pack Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Create or join a pack for live radar & comms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onNavigateToPackSetup,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("open_pack_setup_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Open Pack")
                    }
                }
            }
        }

    }

    // Rider Avatar Quick-Action Bottom-Sheet Overlay (Ring, Message, View Profile Stats + Live ETA & Battery)
    selectedRider?.let { rider ->
        RiderAvatarBottomSheet(
            rider = rider,
            isSelf = rider.userId == currentUserId,
            onDismiss = { onSelectRider(null) },
            onRingClick = { onRingRider(rider) },
            onMessageClick = {
                onSelectRider(null)
                onMessageRider(rider)
            },
            onViewProfileStatsClick = {
                onSelectRider(null)
                showStatsSheetFor = rider
            }
        )
    }

    if (showAiCopilotSheet) {
        AiCopilotBottomSheet(
            weatherAlert = liveWeatherAlert,
            briefingState = aiBriefingState,
            copilotState = aiCopilotState,
            onRunBriefing = onRunAiBriefing,
            onAskQuestion = onAskAiCopilot,
            onDismiss = { showAiCopilotSheet = false }
        )
    }

    // Rider Full Telemetry Stats & Trip History Sheet
    showStatsSheetFor?.let { rider ->
        ModalBottomSheet(
            onDismissRequest = { showStatsSheetFor = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            RiderStatsSheetContent(
                rider = rider,
                trips = if (rider.userId == currentUserId) userTrips else emptyList(),
                onCallPhone = { phone ->
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    context.startActivity(dialIntent)
                },
                onClose = { showStatsSheetFor = null }
            )
        }
    }

    createPinLatLng?.let { target ->
        CreatePackPinDialog(
            targetLatLng = target,
            onDismiss = { createPinLatLng = null },
            onCreatePin = onCreatePackPin
        )
    }

    selectedPackPin?.let { pin ->
        PackPinDetailBottomSheet(
            pin = pin,
            currentUserId = currentUserId,
            myGpsFix = myGpsFix,
            onConfirmPin = onConfirmPackPin,
            onResolvePin = onResolvePackPin,
            onDeletePin = onDeletePackPin,
            onDismiss = { selectedPackPinId = null }
        )
    }

    if (showPackCommsSheet) {
        PackCommsAndGalleryBottomSheet(
            activePack = activePack,
            currentUserId = currentUserId,
            messages = messages,
            galleryPhotos = packPhotos,
            initialTabIndex = packCommsInitialTab,
            pttRecordingState = pttRecordingState,
            pttPlaybackState = pttPlaybackState,
            pttAutoPlayEnabled = pttAutoPlayEnabled,
            pendingVoiceCount = pendingVoiceCount,
            onSendMessage = onSendGroupChat,
            onStartPttRecording = onStartPttRecording,
            onStopAndSendPttRecording = onStopAndSendPttRecording,
            onCancelPttRecording = onCancelPttRecording,
            onTogglePlayVoice = onTogglePlayVoice,
            onTogglePttAutoPlay = onTogglePttAutoPlay,
            onUploadPhoto = onUploadGalleryPhoto,
            onDeletePhoto = onDeleteGalleryPhoto,
            onNavigateToPackSetup = onNavigateToPackSetup,
            onDismiss = { showPackCommsSheet = false }
        )
    }

    if (showSosSheet) {
        EmergencySosBottomSheet(
            isSosActive = isSosActive,
            sosLastBroadcastMs = sosLastBroadcastMs,
            activePack = activePack,
            currentUserId = currentUserId,
            riders = riders,
            myGpsFix = myGpsFix,
            batteryPercent = myBatteryPercent,
            isEffectiveOffline = isEffectiveOffline,
            onTriggerSos = onTriggerSos,
            onStandDownSos = onStandDownSos,
            onDismiss = { showSosSheet = false }
        )
    }

    }

@Composable
private fun PackPinMapMarker(
    pin: PackMapPin,
    accent: Color,
    isSelected: Boolean
) {
    val iconTint = if (pin.pinType == "EMERGENCY_SOS") Color.White else Color(0xFF0E141B)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = accent,
            border = BorderStroke(if (isSelected) 3.dp else 2.dp, Color.White),
            shadowElevation = if (isSelected) 8.dp else 4.dp
        ) {
            Icon(
                imageVector = pinTypeIcon(pin.pinType),
                contentDescription = "${pinTypeLabel(pin.pinType)}: ${pin.title}",
                tint = iconTint,
                modifier = Modifier
                    .padding(if (isSelected) 8.dp else 6.dp)
                    .size(if (isSelected) 20.dp else 16.dp)
            )
        }
        Spacer(modifier = Modifier.width(3.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.9f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.7f))
        ) {
            Text(
                text = if (pin.confirmedByUids.size > 1) {
                    "${pin.title.take(12)} (${pin.confirmedByUids.size})"
                } else {
                    pin.title.take(14)
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun RiderAvatarMapMarker(
    rider: DecryptedRiderState,
    isMe: Boolean,
    isSelected: Boolean
) {
    val ringColor = when {
        isSelected -> Color(0xFFFFB300)
        isMe -> Color(0xFF2EE59D)
        !rider.isOnline -> Color(0xFF64748B)
        else -> Color.White
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(if (isSelected) 50.dp else 42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(3.dp, ringColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (rider.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = rider.photoUrl,
                        contentDescription = rider.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = rider.displayName.take(2).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.9f),
            border = BorderStroke(1.dp, ringColor.copy(alpha = 0.6f))
        ) {
            Text(
                text = if (isMe) "YOU" else String.format("%.1f km", rider.distanceFromMeKm),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun RiderStatsSheetContent(
    rider: DecryptedRiderState,
    trips: List<RideTrip>,
    onCallPhone: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "${rider.displayName} • Live Telemetry",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "${rider.bikeModel} • ${formatCoordinates(rider.lat, rider.lng)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMiniBox("Speed", String.format("%.0f km/h", rider.speedKmh), Modifier.weight(1f))
            StatMiniBox("Altitude", String.format("%.0f m", rider.altitudeM), Modifier.weight(1f))
            StatMiniBox("Battery", "${rider.batteryPercent}%", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMiniBox("Shared Dist", String.format("%.1f km", rider.totalDistanceKm), Modifier.weight(1f))
            StatMiniBox("Elevation", String.format("%.0f m", rider.elevationClimbedM), Modifier.weight(1f))
            StatMiniBox("Separation", String.format("%.1f km", rider.distanceFromMeKm), Modifier.weight(1f))
        }

        if (rider.phoneNumber.isNotBlank()) {
            Button(
                onClick = { onCallPhone(rider.phoneNumber) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Phone, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Call Rider (${rider.phoneNumber})")
            }
        }

        if (trips.isNotEmpty()) {
            Text(
                text = "Recent Trip History",
                style = MaterialTheme.typography.titleMedium
            )
            trips.take(4).forEach { trip ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(trip.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                String.format("%.1f km • +%.0f m climb", trip.distanceKm, trip.elevationGainM),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${trip.durationMin}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.End)
                .padding(bottom = 16.dp)
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun StatMiniBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

fun formatCoordinates(lat: Double, lng: Double): String {
    val latDir = if (lat >= 0) "N" else "S"
    val lngDir = if (lng >= 0) "E" else "W"
    return String.format("%.4f° %s, %.4f° %s", abs(lat), latDir, abs(lng), lngDir)
}

fun formatRelativeTime(timestampMs: Long): String {
    val diffSec = ((System.currentTimeMillis() - timestampMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 30L -> "A moment ago"
        diffSec < 60L -> "${diffSec}s ago"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        else -> "${diffSec / 3600L}h ago"
    }
}
