package com.example.ui.screens

import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MapAreaDownloadProgress
import com.example.data.local.OfflineMapAreaEntity
import com.example.data.local.OfflineMapAreaManager
import com.example.data.remote.ComputedMotoRoute
import com.example.data.remote.ElevationSample
import com.example.data.remote.PlaceSearchSuggestion
import com.example.location.LiveGpsFix
import com.example.ui.theme.TelemetryMonoFontFamily
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Locale

private enum class NewAreaTargetMode {
    SEARCHED_PLACE,
    ACTIVE_ROUTE,
    MY_GPS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    offlineMapAreas: List<OfflineMapAreaEntity>,
    activeOfflineArea: OfflineMapAreaEntity?,
    plannedRoute: ComputedMotoRoute?,
    myGpsFix: LiveGpsFix,
    mapDownloadProgress: MapAreaDownloadProgress,
    isEffectiveOffline: Boolean,
    onSearchPlaces: suspend (String) -> List<PlaceSearchSuggestion>,
    onDownloadArea: (title: String, center: LatLng, radiusKm: Double, useActiveRoute: Boolean) -> Unit,
    onSelectOfflineArea: (OfflineMapAreaEntity?) -> Unit,
    onDeleteOfflineArea: (OfflineMapAreaEntity) -> Unit,
    onBack: () -> Unit,
    deviceAvailableBytesOverride: Long? = null
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val json = remember { Json { ignoreUnknownKeys = true } }
    var filterQuery by rememberSaveable { mutableStateOf("") }
    var showDownloadSheet by rememberSaveable { mutableStateOf(false) }
    var pendingSheetSearchQuery by rememberSaveable { mutableStateOf("") }

    val availableDeviceBytes = remember(deviceAvailableBytesOverride) {
        deviceAvailableBytesOverride ?: runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        }.getOrDefault(0L)
    }

    val totalStorageBytes = remember(offlineMapAreas) {
        offlineMapAreas.sumOf { it.storageBytes }
    }
    val totalTiles = remember(offlineMapAreas) {
        offlineMapAreas.sumOf { it.tileCount }
    }
    val totalNavTurns = remember(offlineMapAreas) {
        offlineMapAreas.sumOf { it.navStepCount }
    }

    val filteredAreas = remember(offlineMapAreas, filterQuery) {
        val q = filterQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) offlineMapAreas
        else offlineMapAreas.filter {
            it.title.lowercase(Locale.ROOT).contains(q) ||
                it.zoomRangeLabel.lowercase(Locale.ROOT).contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("offline_maps_screen")
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("offline_maps_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Maps",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Offline Maps",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Downloaded map regions & storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isEffectiveOffline || activeOfflineArea != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (isEffectiveOffline) "Offline ON" else "Overlay ON",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Storage Usage Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_maps_storage_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = "Offline Maps Storage",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Storage Usage",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${offlineMapAreas.size} regions • $totalTiles tiles • $totalNavTurns turns",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatStorageBytes(totalStorageBytes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = TelemetryMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("offline_storage_used_text")
                                )
                                if (availableDeviceBytes > 0L) {
                                    Text(
                                        text = "${formatStorageBytes(availableDeviceBytes)} free",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = TelemetryMonoFontFamily,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        val capDenom = (totalStorageBytes + availableDeviceBytes).coerceAtLeast(50L * 1024L * 1024L)
                        val barFraction = if (totalStorageBytes <= 0L) 0f
                        else (totalStorageBytes.toFloat() / capDenom.toFloat()).coerceIn(0.03f, 1f)
                        LinearProgressIndicator(
                            progress = { barFraction },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            // Clear 'Download New Area' Primary Action Button
            item {
                Button(
                    onClick = {
                        pendingSheetSearchQuery = filterQuery.trim()
                        showDownloadSheet = true
                    },
                    enabled = !isEffectiveOffline,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("download_new_area_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEffectiveOffline) "Go Online to Download New Area" else "Download New Area",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (mapDownloadProgress.isDownloading) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Downloading ${mapDownloadProgress.title}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            LinearProgressIndicator(
                                progress = { mapDownloadProgress.fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${mapDownloadProgress.completedTiles}/${mapDownloadProgress.totalTiles} map tiles + turns cached",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Search Downloaded Regions (plus 1-tap jump to search & download)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = { Text("Search downloaded regions or places...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search regions") },
                        trailingIcon = {
                            if (filterQuery.isNotEmpty()) {
                                IconButton(onClick = { filterQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (filterQuery.isNotBlank() && filteredAreas.isEmpty() && !isEffectiveOffline) {
                                    pendingSheetSearchQuery = filterQuery.trim()
                                    showDownloadSheet = true
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offline_maps_search_input")
                    )

                    if (filterQuery.isNotBlank() && !isEffectiveOffline) {
                        OutlinedButton(
                            onClick = {
                                pendingSheetSearchQuery = filterQuery.trim()
                                showDownloadSheet = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("search_to_download_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Search & Download \"${filterQuery.trim()}\"")
                        }
                    }
                }
            }

            // Downloaded Regions List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloaded Regions (${filteredAreas.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (activeOfflineArea != null) {
                        FilterChip(
                            selected = true,
                            onClick = { onSelectOfflineArea(null) },
                            label = { Text("Active: ${activeOfflineArea.title.take(16)}") },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Clear active area", modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }

            if (offlineMapAreas.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offline_maps_empty_state")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No Offline Map Regions Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Search for any mountain pass, town, or riding corridor to cache map tiles, turn-by-turn steps, fuel stops, and elevation for zero-signal rides.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (filteredAreas.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No downloaded region matches \"$filterQuery\". Tap \"Search & Download\" above to download it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(filteredAreas, key = { it.areaId }) { area ->
                    val isActive = activeOfflineArea?.areaId == area.areaId
                    val elevSamples = remember(area.elevationPointsJson) {
                        runCatching {
                            json.decodeFromString<List<ElevationSample>>(area.elevationPointsJson)
                        }.getOrDefault(emptyList())
                    }

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = if (isActive) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offline_region_card_${area.areaId}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = area.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${area.radiusKm.toInt()} km radius • ${area.tileCount} tiles • ${area.zoomRangeLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = formatStorageBytes(area.storageBytes),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = TelemetryMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (elevSamples.size >= 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompactElevationSparkline(
                                        samples = elevSamples,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                    )
                                    Text(
                                        text = String.format("Max %.0fm • +%.0fm", area.maxElevationM, area.elevationGainM),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = TelemetryMonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${area.navStepCount} turns • ${area.fuelStopCount} fuel stops",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = isActive,
                                        onClick = { onSelectOfflineArea(if (isActive) null else area) },
                                        label = { Text(if (isActive) "Active" else "Use Offline") },
                                        leadingIcon = if (isActive) {
                                            {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        } else null,
                                        modifier = Modifier.testTag("activate_region_${area.areaId}")
                                    )
                                    IconButton(
                                        onClick = { onDeleteOfflineArea(area) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("delete_region_${area.areaId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete ${area.title}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDownloadSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDownloadSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DownloadNewAreaSearchSheetContent(
                initialSearchQuery = pendingSheetSearchQuery,
                plannedRoute = plannedRoute,
                myGpsFix = myGpsFix,
                mapDownloadProgress = mapDownloadProgress,
                isEffectiveOffline = isEffectiveOffline,
                onSearchPlaces = onSearchPlaces,
                onStartDownload = { title, center, radiusKm, useActiveRoute ->
                    onDownloadArea(title, center, radiusKm, useActiveRoute)
                    showDownloadSheet = false
                },
                onDismiss = { showDownloadSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadNewAreaSearchSheetContent(
    initialSearchQuery: String = "",
    plannedRoute: ComputedMotoRoute?,
    myGpsFix: LiveGpsFix,
    mapDownloadProgress: MapAreaDownloadProgress,
    isEffectiveOffline: Boolean,
    onSearchPlaces: suspend (String) -> List<PlaceSearchSuggestion>,
    onStartDownload: (title: String, center: LatLng, radiusKm: Double, useActiveRoute: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PlaceSearchSuggestion>>(emptyList()) }
    var selectedSuggestion by remember { mutableStateOf<PlaceSearchSuggestion?>(null) }
    var targetMode by remember {
        mutableStateOf(
            if (plannedRoute != null && initialSearchQuery.isBlank()) NewAreaTargetMode.ACTIVE_ROUTE
            else NewAreaTargetMode.SEARCHED_PLACE
        )
    }
    var selectedRadiusKm by remember { mutableDoubleStateOf(10.0) }
    var areaTitle by remember {
        mutableStateOf(
            if (initialSearchQuery.isNotBlank()) initialSearchQuery
            else plannedRoute?.routeName ?: "Peak District Sector"
        )
    }

    LaunchedEffect(Unit) {
        isSearching = true
        searchResults = runCatching { onSearchPlaces(searchQuery) }.getOrDefault(emptyList())
        if (selectedSuggestion == null && initialSearchQuery.isNotBlank() && searchResults.isNotEmpty()) {
            selectedSuggestion = searchResults.first()
            areaTitle = searchResults.first().name
        }
        isSearching = false
    }

    fun triggerSearch(q: String) {
        scope.launch {
            isSearching = true
            searchResults = runCatching { onSearchPlaces(q) }.getOrDefault(emptyList())
            isSearching = false
        }
    }

    val resolvedCenter = when (targetMode) {
        NewAreaTargetMode.ACTIVE_ROUTE -> plannedRoute?.points?.firstOrNull() ?: LatLng(myGpsFix.lat, myGpsFix.lng)
        NewAreaTargetMode.MY_GPS -> LatLng(myGpsFix.lat, myGpsFix.lng)
        NewAreaTargetMode.SEARCHED_PLACE -> selectedSuggestion?.let { LatLng(it.lat, it.lng) }
            ?: LatLng(myGpsFix.lat, myGpsFix.lng)
    }
    val useActiveRoute = targetMode == NewAreaTargetMode.ACTIVE_ROUTE && plannedRoute != null
    val estimate = remember(resolvedCenter, selectedRadiusKm, useActiveRoute, plannedRoute) {
        OfflineMapAreaManager.estimateAreaDownload(
            center = resolvedCenter,
            routePoints = if (useActiveRoute) plannedRoute?.points.orEmpty() else emptyList(),
            radiusKm = selectedRadiusKm
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .testTag("download_new_area_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Download New Area", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Search any pass, town, or café to cache offline tiles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Download New Area")
            }
        }

        // Location Search Input + Search Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    triggerSearch(it)
                },
                label = { Text("Search city, mountain pass, or region") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { triggerSearch(searchQuery) }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("offline_area_search_input")
            )
            Button(
                onClick = { triggerSearch(searchQuery) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("offline_area_search_button")
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Find")
                }
            }
        }

        // Source Mode Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (plannedRoute != null) {
                FilterChip(
                    selected = targetMode == NewAreaTargetMode.ACTIVE_ROUTE,
                    onClick = {
                        targetMode = NewAreaTargetMode.ACTIVE_ROUTE
                        areaTitle = "${plannedRoute.routeName} Corridor"
                    },
                    label = { Text("Active Route") },
                    leadingIcon = { Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
            FilterChip(
                selected = targetMode == NewAreaTargetMode.MY_GPS,
                onClick = {
                    targetMode = NewAreaTargetMode.MY_GPS
                    areaTitle = "Current GPS Sector"
                },
                label = { Text("My GPS") },
                leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        // Search Results / Riding Regions List
        if (searchResults.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("offline_area_search_results")
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "Popular Riding Regions" else "Search Results (${searchResults.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                searchResults.take(4).forEachIndexed { idx, place ->
                    val isSelected = targetMode == NewAreaTargetMode.SEARCHED_PLACE &&
                        selectedSuggestion?.name == place.name &&
                        selectedSuggestion?.lat == place.lat
                    Surface(
                        onClick = {
                            selectedSuggestion = place
                            targetMode = NewAreaTargetMode.SEARCHED_PLACE
                            areaTitle = place.name
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offline_search_result_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(place.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = place.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Radius & Custom Area Name
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Radius:", style = MaterialTheme.typography.labelMedium)
            listOf(5.0, 10.0, 20.0).forEach { km ->
                FilterChip(
                    selected = selectedRadiusKm == km,
                    onClick = { selectedRadiusKm = km },
                    label = { Text("${km.toInt()} km") },
                    modifier = Modifier.testTag("download_radius_${km.toInt()}")
                )
            }
        }

        OutlinedTextField(
            value = areaTitle,
            onValueChange = { areaTitle = it },
            label = { Text("Region Name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("download_area_name_input")
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Estimated Download", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = String.format(
                        Locale.US,
                        "~%d tiles • %.1f MB (%s)",
                        estimate.tileCount,
                        estimate.estimatedBytes / (1024.0 * 1024.0),
                        estimate.zoomRangeLabel
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = TelemetryMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Button(
            onClick = {
                onStartDownload(
                    areaTitle.trim().ifBlank { "Offline Region" },
                    resolvedCenter,
                    selectedRadiusKm,
                    useActiveRoute
                )
            },
            enabled = !mapDownloadProgress.isDownloading && !isEffectiveOffline,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("confirm_download_area_button")
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isEffectiveOffline) "Go Online to Download" else "Start Download",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
