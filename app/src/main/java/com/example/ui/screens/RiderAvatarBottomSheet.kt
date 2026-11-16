package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.DecryptedRiderState
import com.example.ui.theme.TelemetryMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderAvatarBottomSheet(
    rider: DecryptedRiderState,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onRingClick: () -> Unit,
    onMessageClick: () -> Unit,
    onViewProfileStatsClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        RiderAvatarBottomSheetContent(
            rider = rider,
            isSelf = isSelf,
            onDismiss = onDismiss,
            onRingClick = onRingClick,
            onMessageClick = onMessageClick,
            onViewProfileStatsClick = onViewProfileStatsClick
        )
    }
}

@Composable
fun RiderAvatarBottomSheetContent(
    rider: DecryptedRiderState,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onRingClick: () -> Unit,
    onMessageClick: () -> Unit,
    onViewProfileStatsClick: () -> Unit
) {
    val battColor = when {
        rider.batteryPercent <= 15 -> Color(0xFFFF5252)
        rider.batteryPercent <= 35 -> Color(0xFFFFB300)
        else -> Color(0xFF2EE59D)
    }
    val statusColor = when {
        isSelf -> MaterialTheme.colorScheme.secondary
        rider.isOnline -> Color(0xFF2EE59D)
        else -> MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .testTag("rider_avatar_bottom_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Rider Identity & Live Status Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, statusColor, RoundedCornerShape(18.dp)),
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
                    @Suppress("DEPRECATION")
                    Icon(
                        imageVector = Icons.Default.DirectionsBike,
                        contentDescription = rider.displayName,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isSelf) "${rider.displayName} (You)" else rider.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = if (rider.isOnline) "LIVE" else "CACHED",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${rider.bikeModel.ifBlank { "Motorcycle" }} • ${formatRelativeTime(rider.lastUpdatedMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = formatCoordinates(rider.lat, rider.lng),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TelemetryMonoFontFamily,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close rider bottom sheet"
                )
            }
        }

        // Real-Time ETA + Battery Info Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Real-time ETA / Separation Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("rider_sheet_eta_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelf) Icons.Default.Speed else Icons.Default.Schedule,
                            contentDescription = if (isSelf) "Live Speed" else "Real-time ETA",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isSelf) "LIVE SPEED" else "REAL-TIME ETA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (isSelf) {
                            String.format("%.0f km/h", rider.speedKmh)
                        } else {
                            rider.trafficEtaMinutes?.let { "$it min" } ?: "Calculating..."
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = TelemetryMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isSelf) {
                                String.format("Alt %.0f m", rider.altitudeM)
                            } else {
                                "${String.format("%.1f km", rider.distanceFromMeKm)} • ${rider.trafficConditionLabel}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Real-time Battery Info Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, battColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("rider_sheet_battery_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                rider.isCharging -> Icons.Default.BatteryChargingFull
                                rider.batteryPercent <= 15 -> Icons.Default.BatteryAlert
                                else -> Icons.Default.BatteryStd
                            },
                            contentDescription = "Rider battery",
                            tint = battColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "BATTERY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${rider.batteryPercent}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = TelemetryMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = battColor
                    )
                    LinearProgressIndicator(
                        progress = { (rider.batteryPercent / 100f).coerceIn(0f, 1f) },
                        color = battColor,
                        trackColor = battColor.copy(alpha = 0.16f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = when {
                            rider.isCharging -> "Charging on bike"
                            rider.batteryPercent <= 15 -> "Critical low battery"
                            else -> String.format("%.0f km/h live pace", rider.speedKmh)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Quick-Action Buttons: Ring, Message, View Profile Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRingClick,
                enabled = !isSelf,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ring_rider_button")
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ring", maxLines = 1)
            }

            FilledTonalButton(
                onClick = onMessageClick,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("message_rider_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Message", maxLines = 1)
            }
        }

        OutlinedButton(
            onClick = onViewProfileStatsClick,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("view_profile_stats_button")
        ) {
            Icon(
                imageVector = Icons.Default.QueryStats,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("View Profile Stats", maxLines = 1)
        }
    }
}
