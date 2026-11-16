package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.SmartHudUiState
import com.example.data.remote.MotoRouteAndWeatherRepository
import com.example.location.LiveGpsFix
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.TelemetryMonoFontFamily
import com.example.ui.viewmodel.HudNavigationUiState
import com.example.ui.viewmodel.HudSmartAdvisory
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

fun formatHudDistance(meters: Int): String {
    val safe = meters.coerceAtLeast(0)
    return when {
        safe < 1000 -> "${((safe + 5) / 10) * 10} m"
        safe < 10_000 -> String.format(Locale.US, "%.1f km", safe / 1000.0)
        else -> String.format(Locale.US, "%.0f km", safe / 1000.0)
    }
}

fun maneuverIcon(maneuver: String): ImageVector {
    val m = maneuver.uppercase(Locale.US)
    return when {
        m.contains("SHARP_LEFT") -> Icons.Default.TurnSharpLeft
        m.contains("SHARP_RIGHT") -> Icons.Default.TurnSharpRight
        m.contains("SLIGHT_LEFT") -> Icons.Default.TurnSlightLeft
        m.contains("SLIGHT_RIGHT") -> Icons.Default.TurnSlightRight
        m.contains("UTURN_LEFT") -> Icons.Default.UTurnLeft
        m.contains("UTURN_RIGHT") -> Icons.Default.UTurnRight
        m.contains("ROUNDABOUT") -> Icons.Default.RoundaboutRight
        m.contains("FORK_LEFT") || m.contains("KEEP_LEFT") || m.contains("RAMP_LEFT") -> Icons.Default.ForkLeft
        m.contains("FORK_RIGHT") || m.contains("KEEP_RIGHT") || m.contains("RAMP_RIGHT") -> Icons.Default.ForkRight
        m.contains("MERGE") -> Icons.Default.Merge
        m.contains("LEFT") -> Icons.Default.TurnLeft
        m.contains("RIGHT") -> Icons.Default.TurnRight
        m.contains("ARRIVE") || m.contains("DESTINATION") -> Icons.Default.Flag
        else -> Icons.Default.Straight
    }
}

fun maneuverAccentColor(maneuver: String, scheme: ColorScheme): Color {
    val m = maneuver.uppercase(Locale.US)
    return when {
        m.contains("SHARP") || m.contains("UTURN") -> scheme.error
        m.contains("ARRIVE") -> scheme.secondary
        m.contains("LEFT") || m.contains("RIGHT") || m.contains("ROUNDABOUT") -> scheme.primary
        else -> scheme.tertiary
    }
}

@Composable
fun RiderHudManeuverHeroCard(
    hudNav: HudNavigationUiState,
    onStepDelta: (Int) -> Unit,
    onResetAutoStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val accent = maneuverAccentColor(hudNav.currentManeuver, scheme)
    val animatedProgress by animateFloatAsState(
        targetValue = hudNav.stepProgressFraction.coerceIn(0.05f, 1f),
        label = "hudTurnProgress"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surface,
        border = BorderStroke(2.5.dp, accent),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("hud_maneuver_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(94.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = maneuverIcon(hudNav.currentManeuver),
                        contentDescription = hudNav.currentInstruction,
                        tint = Color.Black.takeIf { accent == scheme.primary || accent == scheme.secondary }
                            ?: Color.White,
                        modifier = Modifier.size(62.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NEXT TURN",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = formatHudDistance(hudNav.distanceToNextTurnMeters),
                        fontFamily = TelemetryMonoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 52.sp,
                        lineHeight = 56.sp,
                        color = scheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.testTag("hud_next_turn_distance")
                    )
                    Text(
                        text = "STEP ${hudNav.currentStepIndex + 1} / ${hudNav.totalSteps}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                color = accent,
                trackColor = scheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )

            Text(
                text = hudNav.currentInstruction,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("hud_next_turn_instruction")
            )

            // "THEN" subsequent turn + glove-friendly step scrub controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!hudNav.thenInstruction.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = maneuverIcon(hudNav.thenManeuver ?: "STRAIGHT"),
                                contentDescription = "Next turn after this",
                                tint = scheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "THEN ${hudNav.thenDistanceMeters?.let { formatHudDistance(it) + " • " }.orEmpty()}${hudNav.thenInstruction}",
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Final leg to destination",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onStepDelta(-1) },
                        enabled = hudNav.currentStepIndex > 0,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceVariant)
                            .testTag("hud_prev_step_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous turn step")
                    }
                    IconButton(
                        onClick = onResetAutoStep,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceVariant)
                            .testTag("hud_gps_auto_step_button")
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Lock to GPS turn")
                    }
                    IconButton(
                        onClick = { onStepDelta(1) },
                        enabled = hudNav.currentStepIndex < hudNav.totalSteps - 1,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceVariant)
                            .testTag("hud_next_step_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next turn step")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RiderHudFreeRideCard(
    myGpsFix: LiveGpsFix,
    hasPackRiders: Boolean,
    isPlanningRoute: Boolean,
    onQuickPlanPreset: (String, String, LatLng) -> Unit,
    onRouteToPackLeader: () -> Unit,
    onOpenRoutePlanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val cardinal = MotoRouteAndWeatherRepository.compassCardinal(myGpsFix.headingDeg.toDouble())

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surface,
        border = BorderStroke(2.dp, scheme.primary),
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("hud_free_ride_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Compass heading $cardinal",
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(52.dp)
                    )
                }
                Column {
                    Text(
                        text = "FREE RIDE COCKPIT",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary
                    )
                    Text(
                        text = "$cardinal • ${myGpsFix.headingDeg.toInt()}°",
                        fontFamily = TelemetryMonoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 42.sp,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "Tap a quick route below for large turn-by-turn guidance",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onQuickPlanPreset(
                            "Snake Pass Twisties",
                            "Snake Pass, Peak District",
                            LatLng(53.4339, -1.8732)
                        )
                    },
                    enabled = !isPlanningRoute,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("hud_quick_route_snake_pass")
                ) {
                    if (isPlanningRoute) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = scheme.onPrimary
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Snake Pass", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onQuickPlanPreset(
                            "Cat & Fiddle Run",
                            "Cat and Fiddle, Buxton",
                            LatLng(53.2435, -1.9964)
                        )
                    },
                    enabled = !isPlanningRoute,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cat & Fiddle", fontWeight = FontWeight.Bold)
                }

                if (hasPackRiders) {
                    OutlinedButton(
                        onClick = onRouteToPackLeader,
                        enabled = !isPlanningRoute,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Join Pack", fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = onOpenRoutePlanner,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Custom Route")
                }
            }
        }
    }
}

@Composable
fun RiderHudTelemetryGrid(
    myGpsFix: LiveGpsFix,
    hudNav: HudNavigationUiState,
    batteryPercent: Int,
    ecoRideMode: Boolean,
    onToggleEcoMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Primary Speedometer Tile
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = scheme.surface,
            border = BorderStroke(1.5.dp, scheme.outline),
            modifier = Modifier.weight(1.15f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "SPEED",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${myGpsFix.speedKmh.toInt()}",
                        fontFamily = TelemetryMonoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 46.sp,
                        lineHeight = 48.sp,
                        color = scheme.secondary,
                        modifier = Modifier.testTag("hud_speed_value")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terrain,
                        contentDescription = null,
                        tint = scheme.tertiary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Alt ${myGpsFix.altitudeM.toInt()} m",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }

        // Route ETA + Battery Eco Tile
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = scheme.surface,
            border = BorderStroke(1.5.dp, scheme.outline),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (hudNav.hasActiveRoute) "REMAINING" else "COCKPIT",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    text = if (hudNav.hasActiveRoute) {
                        String.format(Locale.US, "%.1f km", hudNav.remainingRouteKm)
                    } else {
                        "Standby"
                    },
                    fontFamily = TelemetryMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = scheme.onSurface
                )
                Text(
                    text = if (hudNav.hasActiveRoute) {
                        "ETA ${hudNav.remainingEtaMin} min"
                    } else {
                        "Live GPS Active"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary
                )

                Surface(
                    onClick = { onToggleEcoMode(!ecoRideMode) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (ecoRideMode) scheme.secondary.copy(alpha = 0.2f) else scheme.surfaceVariant,
                    modifier = Modifier.testTag("hud_battery_eco_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Toggle Battery Eco",
                            tint = if (batteryPercent <= 20) scheme.error else scheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (ecoRideMode) "ECO • $batteryPercent%" else "BATT $batteryPercent%",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RiderHudSmartCard(
    hudNav: HudNavigationUiState,
    smartHudState: SmartHudUiState,
    isEffectiveOffline: Boolean,
    onRunSmartScan: () -> Unit,
    onSpeakCallout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val topAdvisory: HudSmartAdvisory? = hudNav.smartAdvisories.firstOrNull()

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface,
        border = BorderStroke(
            width = 1.5.dp,
            color = when {
                topAdvisory?.severity == "CRITICAL" -> scheme.error
                smartHudState is SmartHudUiState.Success && smartHudState.callout.priority == "CRITICAL" -> scheme.error
                topAdvisory != null -> scheme.primary
                else -> scheme.outline
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hud_smart_card")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "SMART RIDER INTEL",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary
                    )
                }

                Button(
                    onClick = onRunSmartScan,
                    enabled = smartHudState !is SmartHudUiState.Loading,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("hud_ai_scan_button")
                ) {
                    if (smartHudState is SmartHudUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = scheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Scan")
                }
            }

            if (topAdvisory != null) {
                val advAccent = if (topAdvisory.severity == "CRITICAL") scheme.error else scheme.primary
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = advAccent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, advAccent.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = topAdvisory.tag,
                            tint = advAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${topAdvisory.tag} • ${topAdvisory.title}",
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = topAdvisory.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!hudNav.nextFuelStopLabel.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = null,
                        tint = scheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Next fuel: ${hudNav.nextFuelStopLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.secondary
                    )
                }
            }

            when (smartHudState) {
                is SmartHudUiState.Idle -> {
                    Text(
                        text = if (isEffectiveOffline) {
                            "Offline dead-zone: turn corner guard & cached alerts are monitoring your ride."
                        } else {
                            "Tap AI Scan for a live pace, grip & convoy safety callout spoken to your helmet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                is SmartHudUiState.Loading -> {
                    Text(
                        text = "Analyzing turn speed, tarmac weather & convoy telemetry…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.primary
                    )
                }
                is SmartHudUiState.Success -> {
                    val c = smartHudState.callout
                    val badgeColor = when (c.priority) {
                        "CRITICAL" -> scheme.error
                        "CAUTION" -> scheme.primary
                        else -> scheme.secondary
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = badgeColor
                                ) {
                                    Text(
                                        text = c.paceAction,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Black.takeIf { badgeColor != scheme.error } ?: Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                TextButton(
                                    onClick = { onSpeakCallout(c.spokenTip) },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak tip", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Speak")
                                }
                            }
                            Text(
                                text = c.headline,
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = c.spokenTip,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is SmartHudUiState.Error -> {
                    Text(
                        text = smartHudState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
