package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.ScenicRoutePreferences
import com.example.ai.ScenicRouteUiState
import com.example.data.local.MapAreaDownloadProgress
import com.example.data.local.OfflineMapAreaEntity
import com.example.data.local.OfflineRouteEntity
import com.example.data.remote.ComputedMotoRoute
import com.example.location.LiveGpsFix
import com.example.weather.ProactiveRouteWeatherAlert
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileProvider
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

@Composable
fun RoutePlannerScreen(
    myGpsFix: LiveGpsFix,
    plannedRoute: ComputedMotoRoute?,
    isPlanning: Boolean,
    savedOfflineRoutes: List<OfflineRouteEntity>,
    offlineMapAreas: List<OfflineMapAreaEntity> = emptyList(),
    activeOfflineArea: OfflineMapAreaEntity? = null,
    mapDownloadProgress: MapAreaDownloadProgress = MapAreaDownloadProgress(),
    offlineTileProvider: TileProvider? = null,
    isEffectiveOffline: Boolean,
    isP2pMeshActive: Boolean = false,
    isMeshVpnActive: Boolean = false,
    meshVirtualIp: String = "",
    onPlanRoute: (routeName: String, destinationLabel: String, destLatLng: LatLng?) -> Unit,
    onSaveRouteOffline: () -> Unit,
    onLoadOfflineRoute: (OfflineRouteEntity) -> Unit,
    onBroadcastWeatherAlert: (String) -> Unit,
    onDownloadRouteMapArea: () -> Unit = {},
    onDownloadRadiusMapArea: (String, Double) -> Unit = { _, _ -> },
    onDownloadArea: (title: String, center: LatLng, radiusKm: Double, useActiveRoute: Boolean) -> Unit = { _, _, _, _ -> },
    onSelectOfflineMapArea: (OfflineMapAreaEntity?) -> Unit = {},
    onDeleteOfflineMapArea: (OfflineMapAreaEntity) -> Unit = {},
    onOpenOfflineMaps: () -> Unit = {},
    onOpenRiderHud: () -> Unit = {},
    scenicPreferences: ScenicRoutePreferences = ScenicRoutePreferences(),
    scenicRouteState: ScenicRouteUiState = ScenicRouteUiState.Idle,
    onUpdateScenicPreferences: (ScenicRoutePreferences) -> Unit = {},
    onSuggestScenicRoutes: () -> Unit = {},
    onCancelScenicRoutes: () -> Unit = {},
    proactiveWeatherPushEnabled: Boolean = true,
    isScanningRouteWeather: Boolean = false,
    proactiveWeatherAlert: ProactiveRouteWeatherAlert? = null,
    onToggleProactiveWeatherPush: (Boolean) -> Unit = {},
    onScanRouteWeatherNow: () -> Unit = {},
    onApplyWeatherSuggestedStop: (ProactiveRouteWeatherAlert) -> Unit = {},
    onApplyWeatherSuggestedDetour: (ProactiveRouteWeatherAlert) -> Unit = {},
    onDismissProactiveWeatherAlert: () -> Unit = {}
) {
    var destinationInput by remember { mutableStateOf("Snake Pass Summit, Peak District") }
    var selectedSectionTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Plan, 1 = AI Scenic, 2 = Weather
    var selectedRouteDetailTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Elevation, 1 = Fuel, 2 = Weather
    val scope = rememberCoroutineScope()
    val isDarkHud = MaterialTheme.colorScheme.background.luminance() < 0.3f

    val presets = remember {
        listOf(
            Triple("Snake Pass Twisties", "Snake Pass, Peak District", LatLng(53.4339, -1.8732)),
            Triple("Cat & Fiddle Run", "Cat and Fiddle, Buxton", LatLng(53.2435, -1.9964)),
            Triple("Matlock Bath Loop", "Matlock Bath, Derbyshire", LatLng(53.1225, -1.5594))
        )
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(myGpsFix.lat, myGpsFix.lng), 10.5f)
    }

    LaunchedEffect(plannedRoute?.points) {
        val mid = plannedRoute?.points?.let { if (it.isNotEmpty()) it[it.size / 2] else null }
        if (mid != null) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(mid, 10.5f))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Compact Top Header + Offline Maps Shortcut + 3-Tab Switcher
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Routes",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                OutlinedButton(
                    onClick = onOpenOfflineMaps,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("route_open_offline_maps_button")
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Offline (${offlineMapAreas.size})")
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(
                    Triple(0, "Plan", "route_tab_plan"),
                    Triple(1, "AI Scenic", "route_tab_scenic"),
                    Triple(
                        2,
                        if (proactiveWeatherAlert != null) "Weather !" else "Weather",
                        "route_tab_weather"
                    )
                )
                tabs.forEach { (idx, label, tag) ->
                    SegmentedButton(
                        selected = selectedSectionTab == idx,
                        onClick = { selectedSectionTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(idx, tabs.size),
                        modifier = Modifier.testTag(tag)
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        when (selectedSectionTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Destination Search & Presets
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = destinationInput,
                                    onValueChange = { destinationInput = it },
                                    label = { Text("Destination or Mountain Pass") },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("route_destination_input")
                                )
                                Button(
                                    onClick = {
                                        if (destinationInput.isNotBlank()) {
                                            onPlanRoute(destinationInput, destinationInput, null)
                                        }
                                    },
                                    enabled = !isPlanning && !isEffectiveOffline,
                                    modifier = Modifier
                                        .height(56.dp)
                                        .testTag("plan_route_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    if (isPlanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(Icons.Default.Route, contentDescription = "Plan route")
                                    }
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(presets) { (name, label, coords) ->
                                    FilterChip(
                                        selected = plannedRoute?.routeName == name,
                                        onClick = {
                                            destinationInput = label
                                            onPlanRoute(name, label, coords)
                                        },
                                        enabled = !isPlanning,
                                        label = { Text(name) }
                                    )
                                }
                            }
                        }
                    }

                    // Clean Route Preview Map
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                        ) {
                            GoogleMap(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                                cameraPositionState = cameraPositionState,
                                properties = MapProperties(
                                    isTrafficEnabled = !isEffectiveOffline,
                                    mapType = if (isDarkHud) MapType.NORMAL else MapType.TERRAIN
                                ),
                                uiSettings = MapUiSettings(
                                    zoomControlsEnabled = false,
                                    mapToolbarEnabled = false
                                )
                            ) {
                                if ((isEffectiveOffline || activeOfflineArea != null) && offlineTileProvider != null) {
                                    TileOverlay(
                                        tileProvider = offlineTileProvider,
                                        fadeIn = true,
                                        zIndex = -1f
                                    )
                                }
                                val pts = plannedRoute?.points.orEmpty()
                                if (pts.size >= 2) {
                                    Polyline(
                                        points = pts,
                                        color = MaterialTheme.colorScheme.primary,
                                        width = 12f
                                    )
                                }
                                plannedRoute?.fuelStations?.forEach { station ->
                                    key("rp_fuel_${station.id}") {
                                        val pos = LatLng(station.lat, station.lng)
                                        val state = rememberUpdatedMarkerState(position = pos)
                                        MarkerComposable(
                                            keys = arrayOf(station.id),
                                            state = state,
                                            title = station.name
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFFFFB300),
                                                border = BorderStroke(1.5.dp, Color.White)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocalGasStation,
                                                    contentDescription = station.name,
                                                    tint = Color.Black,
                                                    modifier = Modifier
                                                        .padding(4.dp)
                                                        .size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Active Route Summary with Tabbed Elevation / Fuel / Weather (no stacked vertical walls)
                    if (plannedRoute != null) {
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = plannedRoute.routeName,
                                                style = MaterialTheme.typography.titleLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = String.format(
                                                    "%.1f km • %d min%s",
                                                    plannedRoute.distanceKm,
                                                    plannedRoute.durationMin,
                                                    if (plannedRoute.trafficDelayMin > 0) " (+${plannedRoute.trafficDelayMin}m)" else " • Clear"
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            IconButton(
                                                onClick = onSaveRouteOffline,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("save_offline_button")
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = "Save route offline")
                                            }
                                            IconButton(
                                                onClick = onDownloadRouteMapArea,
                                                enabled = !mapDownloadProgress.isDownloading && !isEffectiveOffline,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("download_route_area_button")
                                            ) {
                                                Icon(Icons.Default.OfflinePin, contentDescription = "Cache route corridor tiles")
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = onOpenRiderHud,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("route_planner_open_hud_button")
                                    ) {
                                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Launch Bike-Mount Rider HUD", fontWeight = FontWeight.Bold)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = selectedRouteDetailTab == 0,
                                            onClick = { selectedRouteDetailTab = 0 },
                                            label = { Text("Elevation") }
                                        )
                                        FilterChip(
                                            selected = selectedRouteDetailTab == 1,
                                            onClick = { selectedRouteDetailTab = 1 },
                                            label = { Text("Fuel (${plannedRoute.fuelStations.size})") }
                                        )
                                        FilterChip(
                                            selected = selectedRouteDetailTab == 2,
                                            onClick = { selectedRouteDetailTab = 2 },
                                            label = { Text("Weather (${plannedRoute.weatherWarnings.size})") }
                                        )
                                    }

                                    when (selectedRouteDetailTab) {
                                        0 -> ElevationGraphCard(
                                            title = "Terrain Elevation & Grade",
                                            samples = plannedRoute.elevationSamples,
                                            sourceLabel = if (plannedRoute.isLiveGoogleData) "Elevation API" else "Cached Terrain",
                                            fuelStations = plannedRoute.fuelStations,
                                            testTagPrefix = "route_elevation_graph"
                                        )

                                        1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            plannedRoute.fuelStations.forEach { station ->
                                                Surface(
                                                    onClick = {
                                                        scope.launch {
                                                            cameraPositionState.animate(
                                                                CameraUpdateFactory.newLatLngZoom(LatLng(station.lat, station.lng), 14f)
                                                            )
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(station.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(station.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                        Text(
                                                            text = String.format("+%.1f km", station.distanceAlongKm),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        2 -> RouteWeatherCheckpointList(
                                            warnings = plannedRoute.weatherWarnings,
                                            onBroadcastWeatherAlert = onBroadcastWeatherAlert
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Select a Pass or Enter Destination", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Switch to AI Scenic for road ideas, or Offline to manage downloaded map areas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (savedOfflineRoutes.isNotEmpty()) {
                        item {
                            Text("Saved Offline Routes (${savedOfflineRoutes.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        items(savedOfflineRoutes, key = { it.routeId }) { saved ->
                            Surface(
                                onClick = { onLoadOfflineRoute(saved) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(saved.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            String.format("%.1f km • %d min", saved.distanceKm, saved.durationMin),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    OutlinedButton(onClick = { onLoadOfflineRoute(saved) }) {
                                        Text("Load")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ScenicRouteSuggesterCard(
                            preferences = scenicPreferences,
                            scenicState = scenicRouteState,
                            isEffectiveOffline = isEffectiveOffline,
                            onUpdatePreferences = onUpdateScenicPreferences,
                            onSuggestRoutes = onSuggestScenicRoutes,
                            onCancelSuggestRoutes = onCancelScenicRoutes,
                            onApplyRecommendedRoute = { rec ->
                                destinationInput = rec.destinationLabel
                                onPlanRoute(
                                    rec.routeName,
                                    rec.destinationLabel,
                                    LatLng(rec.destinationLat, rec.destinationLng)
                                )
                                selectedSectionTab = 0
                            }
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ProactiveWeatherShieldCard(
                            proactiveWeatherPushEnabled = proactiveWeatherPushEnabled,
                            hasActiveRoute = plannedRoute != null,
                            isEffectiveOffline = isEffectiveOffline,
                            isScanning = isScanningRouteWeather,
                            proactiveAlert = proactiveWeatherAlert,
                            onToggleProactivePush = onToggleProactiveWeatherPush,
                            onScanRouteWeatherNow = onScanRouteWeatherNow,
                            onApplySuggestedStop = {
                                onApplyWeatherSuggestedStop(it)
                                selectedSectionTab = 0
                            },
                            onApplySuggestedDetour = {
                                onApplyWeatherSuggestedDetour(it)
                                selectedSectionTab = 0
                            },
                            onBroadcastToPack = onBroadcastWeatherAlert,
                            onDismissAlert = onDismissProactiveWeatherAlert
                        )
                    }
                    if (plannedRoute != null && plannedRoute.weatherWarnings.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Route Corridor Radar", style = MaterialTheme.typography.titleMedium)
                                RouteWeatherCheckpointList(
                                    warnings = plannedRoute.weatherWarnings,
                                    onBroadcastWeatherAlert = onBroadcastWeatherAlert
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteWeatherCheckpointList(
    warnings: List<com.example.data.remote.RouteWeatherWarning>,
    onBroadcastWeatherAlert: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        warnings.forEach { wx ->
            val isHazard = wx.severity == "WARNING" || wx.severity == "ADVISORY"
            val accent = when (wx.severity) {
                "WARNING" -> MaterialTheme.colorScheme.error
                "ADVISORY" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.secondary
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (wx.severity) {
                            "WARNING" -> Icons.Default.Warning
                            "ADVISORY" -> Icons.Default.Air
                            else -> Icons.Default.WbSunny
                        },
                        contentDescription = wx.severity,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${wx.locationLabel} • ${wx.tempCelsius.toInt()}°C",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = wx.headline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Wind ${wx.windKmh.toInt()} km/h (gusts ${wx.gustKmh.toInt()}) • Rain ${wx.rainProbPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                    }
                    if (isHazard) {
                        TextButton(onClick = { onBroadcastWeatherAlert("${wx.locationLabel}: ${wx.headline}") }) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Alert Pack", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
