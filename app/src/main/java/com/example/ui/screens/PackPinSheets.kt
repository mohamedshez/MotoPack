package com.example.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PackMapPin
import com.example.data.remote.MotoRouteAndWeatherRepository
import com.example.location.LiveGpsFix
import com.example.ui.theme.TelemetryMonoFontFamily
import com.google.android.gms.maps.model.LatLng

fun pinTypeLabel(pinType: String): String = when (pinType) {
    "GAS_STOP" -> "Planned Gas Stop"
    "REST_BREAK" -> "Rest Break"
    "HAZARD_WARNING" -> "Road Hazard Warning"
    "EMERGENCY_SOS" -> "Emergency SOS Beacon"
    else -> "Pack Pin"
}

fun pinTypeIcon(pinType: String): ImageVector = when (pinType) {
    "GAS_STOP" -> Icons.Default.LocalGasStation
    "REST_BREAK" -> Icons.Default.LocalCafe
    "EMERGENCY_SOS" -> Icons.Default.Sos
    else -> Icons.Default.Warning
}

fun pinTypeColor(pinType: String, severity: String): Color = when {
    pinType == "EMERGENCY_SOS" -> Color(0xFFFF1744)
    pinType == "HAZARD_WARNING" && severity == "DANGER" -> Color(0xFFFF4D4F)
    pinType == "HAZARD_WARNING" -> Color(0xFFFF7A00)
    pinType == "GAS_STOP" -> Color(0xFFFFB300)
    else -> Color(0xFF2EE59D)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatePackPinDialog(
    targetLatLng: LatLng,
    onDismiss: () -> Unit,
    onCreatePin: (
        pinType: String,
        title: String,
        description: String,
        lat: Double,
        lng: Double,
        severity: String
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf("GAS_STOP") }
    var selectedSeverity by remember { mutableStateOf("INFO") }
    var title by remember { mutableStateOf("Planned Fuel Stop") }
    var description by remember { mutableStateOf("") }

    val presets = remember(selectedType) {
        when (selectedType) {
            "GAS_STOP" -> listOf("Planned Fuel Stop", "High-Octane Station", "Reserve Range Check")
            "REST_BREAK" -> listOf("Summit Cafe Break", "Scenic Viewpoint Stop", "Hydration & Stretch")
            else -> listOf("Loose Gravel on Bend", "Wet Leaves / Mud", "Standing Water / Ice", "Roadworks / Debris")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drop Interactive Pack Pin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = formatCoordinates(targetLatLng.latitude, targetLatLng.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TelemetryMonoFontFamily,
                    color = MaterialTheme.colorScheme.tertiary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "GAS_STOP" to "Gas Stop",
                        "REST_BREAK" to "Rest Break",
                        "HAZARD_WARNING" to "Road Hazard"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                selectedSeverity = if (type == "HAZARD_WARNING") "CAUTION" else "INFO"
                                title = when (type) {
                                    "GAS_STOP" -> "Planned Fuel Stop"
                                    "REST_BREAK" -> "Summit Rest Break"
                                    else -> "Loose Gravel on Bend"
                                }
                            },
                            label = { Text(label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = pinTypeIcon(type),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                if (selectedType == "HAZARD_WARNING") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedSeverity == "CAUTION",
                            onClick = { selectedSeverity = "CAUTION" },
                            label = { Text("Caution") }
                        )
                        FilterChip(
                            selected = selectedSeverity == "DANGER",
                            onClick = { selectedSeverity = "DANGER" },
                            label = { Text("High Danger") }
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = title == preset,
                            onClick = { title = preset },
                            label = { Text(preset) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Pin Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pack_pin_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details for Pack (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreatePin(
                            selectedType,
                            title,
                            description,
                            targetLatLng.latitude,
                            targetLatLng.longitude,
                            selectedSeverity
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("confirm_create_pack_pin_button")
            ) {
                Text("Drop Pin")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackPinDetailBottomSheet(
    pin: PackMapPin,
    currentUserId: String,
    myGpsFix: LiveGpsFix,
    onConfirmPin: (PackMapPin) -> Unit,
    onResolvePin: (PackMapPin) -> Unit,
    onDeletePin: (PackMapPin) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = pinTypeColor(pin.pinType, pin.severity)
    val distanceKm = MotoRouteAndWeatherRepository.haversineKm(myGpsFix.lat, myGpsFix.lng, pin.lat, pin.lng)
    val alreadyConfirmed = pin.confirmedByUids.contains(currentUserId)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .testTag("pack_pin_detail_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = pinTypeIcon(pin.pinType),
                            contentDescription = pinTypeLabel(pin.pinType),
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "${pinTypeLabel(pin.pinType).uppercase()} • ${pin.severity}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pin.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close pin details")
                }
            }

            if (pin.description.isNotBlank()) {
                Text(
                    text = pin.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reported by ${pin.creatorName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatCoordinates(pin.lat, pin.lng),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = TelemetryMonoFontFamily,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format("%.1f km away", distanceKm),
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = TelemetryMonoFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${pin.confirmedByUids.size} confirmed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onConfirmPin(pin) },
                    enabled = !alreadyConfirmed,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("confirm_pin_button")
                ) {
                    Icon(
                        imageVector = if (alreadyConfirmed) Icons.Default.CheckCircle else Icons.Default.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (alreadyConfirmed) "Confirmed" else "Confirm")
                }

                OutlinedButton(
                    onClick = {
                        onResolvePin(pin)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("resolve_pin_button")
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mark Cleared")
                }
            }

            if (pin.creatorId == currentUserId) {
                TextButton(
                    onClick = {
                        onDeletePin(pin)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Pin")
                }
            }
        }
    }
}
