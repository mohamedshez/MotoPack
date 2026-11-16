package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MapAreaDownloadProgress
import com.example.data.local.OfflineAreaEstimate
import com.example.data.local.OfflineMapAreaEntity
import com.example.data.local.OfflineMapAreaManager
import com.example.data.remote.ComputedMotoRoute
import com.example.data.remote.ElevationSample
import com.example.location.LiveGpsFix
import com.example.ui.theme.TelemetryMonoFontFamily
import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.json.Json

enum class DownloadAreaSource {
    PLANNED_ROUTE,
    MAP_CENTER,
    MY_GPS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadAreaBottomSheet(
    plannedRoute: ComputedMotoRoute?,
    mapCenterLatLng: LatLng,
    myGpsFix: LiveGpsFix,
    offlineMapAreas: List<OfflineMapAreaEntity>,
    activeOfflineArea: OfflineMapAreaEntity?,
    mapDownloadProgress: MapAreaDownloadProgress,
    isEffectiveOffline: Boolean,
    isP2pMeshActive: Boolean,
    isMeshVpnActive: Boolean,
    meshVirtualIp: String,
    onDownloadArea: (title: String, center: LatLng, radiusKm: Double, useActiveRoute: Boolean) -> Unit,
    onSelectOfflineArea: (OfflineMapAreaEntity?) -> Unit,
    onDeleteOfflineArea: (OfflineMapAreaEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        DownloadAreaSheetContent(
            plannedRoute = plannedRoute,
            mapCenterLatLng = mapCenterLatLng,
            myGpsFix = myGpsFix,
            offlineMapAreas = offlineMapAreas,
            activeOfflineArea = activeOfflineArea,
            mapDownloadProgress = mapDownloadProgress,
            isEffectiveOffline = isEffectiveOffline,
            isP2pMeshActive = isP2pMeshActive,
            isMeshVpnActive = isMeshVpnActive,
            meshVirtualIp = meshVirtualIp,
            onDownloadArea = onDownloadArea,
            onSelectOfflineArea = onSelectOfflineArea,
            onDeleteOfflineArea = onDeleteOfflineArea,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadAreaSheetContent(
    plannedRoute: ComputedMotoRoute?,
    mapCenterLatLng: LatLng,
    myGpsFix: LiveGpsFix,
    offlineMapAreas: List<OfflineMapAreaEntity>,
    activeOfflineArea: OfflineMapAreaEntity?,
    mapDownloadProgress: MapAreaDownloadProgress,
    isEffectiveOffline: Boolean,
    isP2pMeshActive: Boolean,
    isMeshVpnActive: Boolean,
    meshVirtualIp: String,
    onDownloadArea: (title: String, center: LatLng, radiusKm: Double, useActiveRoute: Boolean) -> Unit,
    onSelectOfflineArea: (OfflineMapAreaEntity?) -> Unit,
    onDeleteOfflineArea: (OfflineMapAreaEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSource by remember(plannedRoute != null) {
        mutableStateOf(
            if (plannedRoute != null) DownloadAreaSource.PLANNED_ROUTE else DownloadAreaSource.MAP_CENTER
        )
    }
    var selectedRadiusKm by remember { mutableDoubleStateOf(10.0) }
    var areaTitle by remember(plannedRoute?.routeName) {
        mutableStateOf(
            plannedRoute?.routeName?.let { "$it Offline Pack" } ?: "Canyon Dead-Zone Sector"
        )
    }

    val resolvedCenter = when (selectedSource) {
        DownloadAreaSource.PLANNED_ROUTE -> plannedRoute?.points?.firstOrNull() ?: mapCenterLatLng
        DownloadAreaSource.MAP_CENTER -> mapCenterLatLng
        DownloadAreaSource.MY_GPS -> LatLng(myGpsFix.lat, myGpsFix.lng)
    }
    val useActiveRoute = selectedSource == DownloadAreaSource.PLANNED_ROUTE && plannedRoute != null
    val estimate: OfflineAreaEstimate = remember(resolvedCenter, selectedRadiusKm, useActiveRoute, plannedRoute) {
        OfflineMapAreaManager.estimateAreaDownload(
            center = resolvedCenter,
            routePoints = if (useActiveRoute) plannedRoute?.points.orEmpty() else emptyList(),
            radiusKm = selectedRadiusKm
        )
    }
    val json = remember { Json { ignoreUnknownKeys = true } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .testTag("download_area_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Column {
                    Text("Download Area for Dead Zones", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Caches map tiles + turn-by-turn nav + elevation & fuel stops",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close Download Area sheet")
            }
        }

        // Offline / P2P Mesh / VPN Status Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = if (isP2pMeshActive || isMeshVpnActive) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = buildString {
                        append(if (isEffectiveOffline) "Dead-Zone Offline Active" else "Online — Ready to Cache")
                        if (isP2pMeshActive) append(" • P2P Mesh ON")
                        if (isMeshVpnActive) append(" • VPN $meshVirtualIp")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text("Target Coverage", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (plannedRoute != null) {
                FilterChip(
                    selected = selectedSource == DownloadAreaSource.PLANNED_ROUTE,
                    onClick = { selectedSource = DownloadAreaSource.PLANNED_ROUTE },
                    label = { Text("Planned Route Corridor") },
                    leadingIcon = { Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("download_source_route")
                )
            }
            FilterChip(
                selected = selectedSource == DownloadAreaSource.MAP_CENTER,
                onClick = { selectedSource = DownloadAreaSource.MAP_CENTER },
                label = { Text("Map View Area") },
                leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("download_source_map")
            )
            FilterChip(
                selected = selectedSource == DownloadAreaSource.MY_GPS,
                onClick = { selectedSource = DownloadAreaSource.MY_GPS },
                label = { Text("My GPS") },
                leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("download_source_gps")
            )
        }

        Text("Coverage Radius", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            label = { Text("Offline Area Name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("download_area_name_input")
        )

        // Package Estimate & Included Data Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("download_area_estimate_card")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Offline Package Estimate",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(
                            "~%d tiles • %.1f MB (%s)",
                            estimate.tileCount,
                            estimate.estimatedBytes / (1024.0 * 1024.0),
                            estimate.zoomRangeLabel
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = TelemetryMonoFontFamily,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IncludedDataChip(Icons.Default.Layers, "OSM Map Tiles")
                    IncludedDataChip(Icons.Default.Navigation, "Turn-by-Turn Steps")
                    IncludedDataChip(Icons.Default.Terrain, "Elevation Graph")
                    IncludedDataChip(Icons.Default.LocalGasStation, "Fuel Stops")
                }
            }
        }

        if (mapDownloadProgress.isDownloading) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { mapDownloadProgress.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Downloading ${mapDownloadProgress.title}: ${mapDownloadProgress.completedTiles}/${mapDownloadProgress.totalTiles} tiles + nav data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Button(
            onClick = {
                onDownloadArea(
                    areaTitle.trim().ifBlank { "Offline Sector" },
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
                text = if (isEffectiveOffline) {
                    "Go Online to Download New Area"
                } else {
                    "Download Map Tiles & Nav Data"
                },
                fontWeight = FontWeight.Bold
            )
        }

        if (offlineMapAreas.isNotEmpty()) {
            Text(
                text = "Cached Offline Areas (${offlineMapAreas.size})",
                style = MaterialTheme.typography.titleSmall
            )
            offlineMapAreas.forEach { area ->
                val isActive = activeOfflineArea?.areaId == area.areaId
                val cachedSamples = remember(area.elevationPointsJson) {
                    runCatching {
                        json.decodeFromString<List<ElevationSample>>(area.elevationPointsJson)
                    }.getOrDefault(emptyList())
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isActive) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (isActive) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = area.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = String.format(
                                        "%d tiles • %.0f KB • %.0f km • %d turns • %d fuel",
                                        area.tileCount,
                                        (area.storageBytes / 1024.0).coerceAtLeast(1.0),
                                        area.radiusKm,
                                        area.navStepCount,
                                        area.fuelStopCount
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = isActive,
                                    onClick = { onSelectOfflineArea(if (isActive) null else area) },
                                    label = { Text(if (isActive) "Active" else "Use Offline") },
                                    leadingIcon = if (isActive) {
                                        {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null
                                )
                                IconButton(onClick = { onDeleteOfflineArea(area) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete offline area ${area.title}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        if (cachedSamples.size >= 2) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CompactElevationSparkline(
                                    samples = cachedSamples,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                )
                                Text(
                                    text = String.format("Summit %.0fm • +%.0fm", area.maxElevationM, area.elevationGainM),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = TelemetryMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun IncludedDataChip(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
