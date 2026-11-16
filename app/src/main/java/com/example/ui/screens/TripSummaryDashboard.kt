package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.RideTrip
import com.example.data.remote.ElevationSample
import com.example.ui.theme.TelemetryMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSummaryDashboardSheet(
    trip: RideTrip,
    elevationSamples: List<ElevationSample> = emptyList(),
    isBatteryEfficiencyEnabled: Boolean,
    riderName: String = "",
    bikeModel: String = "",
    onToggleBatteryEfficiency: (Boolean) -> Unit,
    onSavePdf: ((RideTrip, List<ElevationSample>) -> Unit)? = null,
    onSharePdf: ((RideTrip, List<ElevationSample>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        TripSummaryDashboardContent(
            trip = trip,
            elevationSamples = elevationSamples,
            isBatteryEfficiencyEnabled = isBatteryEfficiencyEnabled,
            riderName = riderName,
            bikeModel = bikeModel,
            onToggleBatteryEfficiency = onToggleBatteryEfficiency,
            onSavePdf = onSavePdf,
            onSharePdf = onSharePdf,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun TripSummaryDashboardContent(
    trip: RideTrip,
    elevationSamples: List<ElevationSample> = emptyList(),
    isBatteryEfficiencyEnabled: Boolean,
    riderName: String = "",
    bikeModel: String = "",
    onToggleBatteryEfficiency: (Boolean) -> Unit,
    onSavePdf: ((RideTrip, List<ElevationSample>) -> Unit)? = null,
    onSharePdf: ((RideTrip, List<ElevationSample>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val weatherAccent = when (trip.weatherSeverity.uppercase()) {
        "WARNING" -> MaterialTheme.colorScheme.error
        "ADVISORY" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val weatherIcon = when {
        trip.weatherSeverity.equals("WARNING", ignoreCase = true) &&
            trip.weatherSummary.contains("Storm", ignoreCase = true) -> Icons.Default.Thunderstorm
        trip.weatherSummary.contains("Rain", ignoreCase = true) ||
            trip.weatherSummary.contains("Drizzle", ignoreCase = true) -> Icons.Default.WaterDrop
        trip.weatherSummary.contains("Wind", ignoreCase = true) ||
            trip.maxWindGustKmh >= 42.0 -> Icons.Default.Air
        trip.weatherSeverity.equals("WARNING", ignoreCase = true) -> Icons.Default.Warning
        else -> Icons.Default.WbSunny
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .testTag("trip_summary_dashboard"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed Ride",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "TRIP SUMMARY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${trip.durationMin} min",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = TelemetryMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = trip.title.ifBlank { "MotoPack Ride" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close Trip Summary")
            }
        }

        // 2x2 Telemetry Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TripMetricTile(
                icon = Icons.Default.Route,
                label = "Distance Covered",
                value = String.format("%.1f km", trip.distanceKm),
                subtitle = "GPS Tracked",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("trip_metric_distance")
            )
            TripMetricTile(
                icon = Icons.Default.Speed,
                label = "Average Speed",
                value = String.format("%.0f km/h", trip.avgSpeedKmh),
                subtitle = "Moving Pace",
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("trip_metric_avg_speed")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TripMetricTile(
                icon = Icons.Default.Terrain,
                label = "Elevation Change",
                value = String.format("%+.0f m", trip.elevationChangeNetM),
                subtitle = String.format("+%.0f m total climb", trip.elevationGainM),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("trip_metric_elevation")
            )
            TripMetricTile(
                icon = Icons.Default.Bolt,
                label = "Power Profile",
                value = if (trip.batteryEfficiencyUsed) "Eco 20s" else "Live 5s",
                subtitle = if (trip.batteryPercentUsed > 0) {
                    "-${trip.batteryPercentUsed}% battery used"
                } else {
                    "Minimal drain"
                },
                accent = if (trip.batteryEfficiencyUsed) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("trip_metric_power")
            )
        }

        // Interactive Ride Elevation & Grade Graph
        val resolvedElevation = if (elevationSamples.size >= 2) {
            elevationSamples
        } else {
            buildEstimatedTripElevationSamples(trip)
        }
        val elevBadge = if (elevationSamples.size >= 2) "GPS Tracked" else "Telemetry Est."
        ElevationGraphCard(
            title = "Ride Elevation Profile",
            samples = resolvedElevation,
            sourceLabel = elevBadge,
            testTagPrefix = "trip_elevation_graph"
        )

        // Weather Conditions Encountered Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, weatherAccent.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("trip_weather_summary_card")
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(weatherAccent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = weatherIcon,
                        contentDescription = trip.weatherSeverity,
                        tint = weatherAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Weather Conditions Encountered",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = weatherAccent.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = trip.weatherSeverity.ifBlank { "CLEAR" },
                                style = MaterialTheme.typography.labelSmall,
                                color = weatherAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = trip.weatherSummary.ifBlank { "Clear riding conditions" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (trip.maxWindGustKmh > 0.0) {
                            String.format("Peak wind gusts encountered: %.0f km/h", trip.maxWindGustKmh)
                        } else {
                            "Calm to moderate wind profile"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Battery Efficiency Mode Toggle Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (isBatteryEfficiencyEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Battery Efficiency Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Uses 20s GPS polling, OLED Night HUD & 10 km weather checks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isBatteryEfficiencyEnabled,
                    onCheckedChange = onToggleBatteryEfficiency,
                    modifier = Modifier.testTag("trip_summary_battery_toggle")
                )
            }
        }

        if (onSavePdf != null || onSharePdf != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trip_pdf_export_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Export & Share Ride Report",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Formatted PDF with telemetry, elevation grade graph & weather summary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onSavePdf != null) {
                            OutlinedButton(
                                onClick = { onSavePdf(trip, resolvedElevation) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("save_trip_pdf_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = stringResource(R.string.action_save_pdf),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_save_pdf))
                            }
                        }
                        if (onSharePdf != null) {
                            Button(
                                onClick = { onSharePdf(trip, resolvedElevation) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_trip_pdf_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.action_share_report),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_share_report))
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("close_trip_summary_button")
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun TripMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = TelemetryMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
