package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TelemetryMonoFontFamily
import com.example.weather.ProactiveRouteWeatherAlert
import com.example.weather.ProactiveWeatherHazardType
import com.example.weather.WeatherAdvisorySeverity

@Composable
fun ProactiveWeatherShieldCard(
    proactiveWeatherPushEnabled: Boolean,
    hasActiveRoute: Boolean,
    isEffectiveOffline: Boolean,
    isScanning: Boolean,
    proactiveAlert: ProactiveRouteWeatherAlert?,
    onToggleProactivePush: (Boolean) -> Unit,
    onScanRouteWeatherNow: () -> Unit,
    onApplySuggestedStop: (ProactiveRouteWeatherAlert) -> Unit,
    onApplySuggestedDetour: (ProactiveRouteWeatherAlert) -> Unit,
    onBroadcastToPack: (String) -> Unit,
    onDismissAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("proactive_weather_shield_card")
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (proactiveWeatherPushEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (proactiveWeatherPushEnabled) {
                                Icons.Default.NotificationsActive
                            } else {
                                Icons.Default.Shield
                            },
                            contentDescription = null,
                            tint = if (proactiveWeatherPushEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Proactive Weather Push Shield",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                !proactiveWeatherPushEnabled -> "Background storm alerts paused"
                                isEffectiveOffline -> "Paused while offline — resume signal to monitor ahead"
                                hasActiveRoute -> "Scanning planned corridor every 5m for rain, gusts & storms"
                                else -> "Plan a route to start background weather & detour monitoring"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = proactiveWeatherPushEnabled,
                    onCheckedChange = onToggleProactivePush,
                    modifier = Modifier.testTag("proactive_weather_push_switch")
                )
            }

            if (hasActiveRoute) {
                OutlinedButton(
                    onClick = onScanRouteWeatherNow,
                    enabled = !isScanning && !isEffectiveOffline,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("scan_route_weather_button")
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning Corridor Forecasts...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Route Weather Ahead")
                    }
                }
            }

            if (proactiveAlert != null) {
                ProactiveWeatherAlertBanner(
                    alert = proactiveAlert,
                    onApplyStop = { onApplySuggestedStop(proactiveAlert) },
                    onApplyDetour = { onApplySuggestedDetour(proactiveAlert) },
                    onAlertPack = {
                        onBroadcastToPack("${proactiveAlert.hazardType.label}: ${proactiveAlert.headline}")
                    },
                    onDismiss = onDismissAlert
                )
            } else if (hasActiveRoute && proactiveWeatherPushEnabled && !isScanning && !isEffectiveOffline) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Corridor ahead currently clear of severe rain & high-wind gusts. You'll get a push alert if conditions deteriorate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProactiveWeatherAlertBanner(
    alert: ProactiveRouteWeatherAlert,
    onApplyStop: () -> Unit,
    onApplyDetour: () -> Unit,
    onAlertPack: () -> Unit,
    onDismiss: () -> Unit
) {
    val isCritical = alert.severity == WeatherAdvisorySeverity.CRITICAL
    val accent = if (isCritical) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val hazardIcon = when (alert.hazardType) {
        ProactiveWeatherHazardType.THUNDERSTORM -> Icons.Default.Thunderstorm
        ProactiveWeatherHazardType.HIGH_WINDS -> Icons.Default.Air
        ProactiveWeatherHazardType.ICE_RISK -> Icons.Default.AcUnit
        ProactiveWeatherHazardType.HEAVY_RAIN,
        ProactiveWeatherHazardType.MIXED_STORM -> Icons.Default.WaterDrop
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("proactive_weather_alert_banner")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = hazardIcon,
                        contentDescription = alert.hazardType.label,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${if (isCritical) "URGENT" else "ADVISORY"} • ${alert.hazardType.label}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent
                        )
                        Text(
                            text = "In ~${alert.etaMinutes} min (${String.format("%.1f", alert.distanceAheadKm)} km ahead) • ${alert.checkpointLabel}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dismiss_weather_advisory_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss weather alert",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = alert.headline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = String.format(
                    "%.0f°C • Wind %.0f km/h (gusts %.0f km/h) • Rain %d%% (%.1f mm/h)",
                    alert.tempCelsius,
                    alert.windKmh,
                    alert.gustKmh,
                    alert.rainProbPercent,
                    alert.precipitationMm
                ),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TelemetryMonoFontFamily,
                color = accent
            )

            alert.suggestedStop?.let { stop ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (stop.isCoveredCafe) Icons.Default.LocalCafe else Icons.Default.LocalGasStation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Suggested Stop: ${stop.name}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stop.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Route Change: ${alert.suggestedRouteChange.title}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = alert.suggestedRouteChange.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (alert.suggestedStop != null) {
                    Button(
                        onClick = onApplyStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("weather_suggested_stop_button")
                    ) {
                        Text(
                            text = "Route to Stop",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Button(
                    onClick = onApplyDetour,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("weather_suggested_detour_button")
                ) {
                    Text(
                        text = "Detour Route",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onAlertPack,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Notify Pack Riders")
                }
            }
        }
    }
}
