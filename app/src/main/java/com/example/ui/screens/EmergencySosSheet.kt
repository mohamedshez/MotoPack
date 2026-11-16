package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DecryptedRiderState
import com.example.data.model.RidePack
import com.example.location.LiveGpsFix
import com.example.ui.theme.TelemetryMonoFontFamily
import kotlinx.coroutines.delay
import java.util.Locale

private fun defaultEmergencyNumberForLocale(): String {
    return when (Locale.getDefault().country.uppercase(Locale.US)) {
        "US", "CA", "MX" -> "911"
        "GB" -> "999"
        "AU" -> "000"
        else -> "112"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmergencySosBottomSheet(
    isSosActive: Boolean,
    sosLastBroadcastMs: Long?,
    activePack: RidePack?,
    currentUserId: String,
    riders: List<DecryptedRiderState>,
    myGpsFix: LiveGpsFix,
    batteryPercent: Int,
    isEffectiveOffline: Boolean,
    onTriggerSos: (reason: String) -> Unit,
    onStandDownSos: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var emergencyNumber by remember { mutableStateOf(defaultEmergencyNumberForLocale()) }
    var icePhoneNumber by remember { mutableStateOf("") }
    var selectedReason by remember { mutableStateOf("Crash / Rider Down") }
    var countdownRunning by remember { mutableStateOf(false) }
    var countdownSec by remember { mutableIntStateOf(5) }
    var copiedNotice by remember { mutableStateOf(false) }

    val nearbyPackRiders = remember(riders, currentUserId) {
        riders.filter { it.userId != currentUserId }.sortedBy { it.distanceFromMeKm }
    }

    val decimalCoords = remember(myGpsFix.lat, myGpsFix.lng) {
        String.format(Locale.US, "%.5f, %.5f", myGpsFix.lat, myGpsFix.lng)
    }
    val mapsUrl = remember(myGpsFix.lat, myGpsFix.lng) {
        String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", myGpsFix.lat, myGpsFix.lng)
    }
    val sosMessageBody = remember(selectedReason, decimalCoords, mapsUrl, myGpsFix.altitudeM, batteryPercent) {
        "EMERGENCY SOS ($selectedReason)! My live GPS location: $decimalCoords (Alt ${myGpsFix.altitudeM.toInt()}m, Battery $batteryPercent%). Map: $mapsUrl"
    }

    LaunchedEffect(countdownRunning, isSosActive) {
        if (countdownRunning && !isSosActive) {
            for (sec in 5 downTo 1) {
                countdownSec = sec
                delay(1000L)
            }
            countdownRunning = false
            onTriggerSos(selectedReason)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .testTag("emergency_sos_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF1744).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sos,
                            contentDescription = "Emergency SOS",
                            tint = Color(0xFFFF1744),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isSosActive) "SOS Beacon Active" else "Emergency SOS",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isSosActive) Color(0xFFFF4D4F) else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isSosActive) {
                                "Sharing live GPS with pack & ready for emergency dispatch"
                            } else {
                                "Broadcast exact coordinates to nearby pack & emergency services"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close SOS panel")
                }
            }

            // Live Precise GPS Telemetry Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (isSosActive) Color(0xFFFF1744) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShareLocation,
                                contentDescription = null,
                                tint = Color(0xFFFF4D4F),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Precise Live GPS",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        TextButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("MotoPack SOS GPS", "$decimalCoords ($mapsUrl)"))
                                copiedNotice = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (copiedNotice) "Copied" else "Copy GPS")
                        }
                    }

                    Text(
                        text = decimalCoords,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = TelemetryMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${formatCoordinates(myGpsFix.lat, myGpsFix.lng)} • Alt ${myGpsFix.altitudeM.toInt()} m • Batt $batteryPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isSosActive && sosLastBroadcastMs != null) {
                        Text(
                            text = if (isEffectiveOffline) {
                                "Offline cellular fallback: use Call or SMS buttons below"
                            } else {
                                "High-accuracy GPS active • Broadcast ${formatRelativeTime(sosLastBroadcastMs)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF4D4F)
                        )
                    }
                }
            }

            // Emergency Number & Situation Selection
            Text("Emergency Services Number", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("112" to "112 (EU/Intl)", "911" to "911 (US/CA)", "999" to "999 (UK)").forEach { (num, label) ->
                    FilterChip(
                        selected = emergencyNumber == num,
                        onClick = { emergencyNumber = num },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = icePhoneNumber,
                onValueChange = { icePhoneNumber = it },
                label = { Text("Optional Emergency Contact (ICE) Phone for SMS") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sos_ice_phone_input")
            )

            if (!isSosActive) {
                Text("Emergency Situation", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "Crash / Rider Down",
                        "Medical Emergency",
                        "Mechanical Breakdown",
                        "Stranded / Off-Road"
                    ).forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            label = { Text(reason) }
                        )
                    }
                }

                // Trigger SOS Section
                if (countdownRunning) {
                    Button(
                        onClick = {
                            countdownRunning = false
                            onTriggerSos(selectedReason)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD50000),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("sos_confirm_trigger_button")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sending SOS in ${countdownSec}s — Tap to Send Now", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { countdownRunning = false },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Cancel Countdown")
                    }
                } else {
                    Button(
                        onClick = { onTriggerSos(selectedReason) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD50000),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("sos_confirm_trigger_button")
                    ) {
                        Icon(Icons.Default.Sos, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SEND EMERGENCY SOS TO PACK", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            countdownSec = 5
                            countdownRunning = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Start 5-Second Hands-Free Countdown")
                    }
                }
            }

            // Emergency Services Dispatch Controls (always available, highlighted once SOS active)
            Text(
                text = "Dispatch Emergency Services",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber"))
                        runCatching { context.startActivity(dialIntent) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSosActive) Color(0xFFD50000) else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("sos_call_services_button")
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call $emergencyNumber", maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        val smsDest = icePhoneNumber.trim().ifBlank { emergencyNumber }
                        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$smsDest")).apply {
                            putExtra("sms_body", sosMessageBody)
                        }
                        runCatching { context.startActivity(smsIntent) }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("sos_sms_location_button")
                ) {
                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SMS GPS", maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "MotoPack Emergency SOS Location")
                        putExtra(Intent.EXTRA_TEXT, sosMessageBody)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(shareIntent, "Share Live SOS Location"))
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sos_share_location_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Live Coordinates & Map Link")
            }

            // Nearby Pack Members Status
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (activePack == null) {
                            "No active riding pack — join a pack in Pack Hub to broadcast SOS to nearby riders."
                        } else if (nearbyPackRiders.isEmpty()) {
                            "Pack '${activePack.name}' connected — awaiting other riders' telemetry."
                        } else {
                            "Nearby Pack Riders (${nearbyPackRiders.size} in ${activePack.name})"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    nearbyPackRiders.take(3).forEach { rider ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = rider.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f km • %d%% batt", rider.distanceFromMeKm, rider.batteryPercent),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            if (isSosActive) {
                OutlinedButton(
                    onClick = {
                        onStandDownSos()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sos_cancel_beacon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mark Safe & Stand Down SOS", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
