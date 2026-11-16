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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DecryptedRiderState
import com.example.data.model.MapChallenge
import com.example.data.model.RidePack
import com.example.data.model.RideTrip
import com.example.data.model.UserProfile

private data class MilestoneBadgeDef(
    val id: String,
    val title: String,
    val requirementLabel: String
)

@Composable
fun ChallengesScreen(
    activePack: RidePack?,
    userProfile: UserProfile?,
    riders: List<DecryptedRiderState>,
    challenges: List<MapChallenge>,
    userTrips: List<RideTrip>,
    onCreateChallenge: (title: String, desc: String, type: String, target: Double, unit: String) -> Unit,
    onIncrementChallenge: (MapChallenge, Double) -> Unit,
    onSelectTripForSummary: (RideTrip) -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val milestones = remember {
        listOf(
            MilestoneBadgeDef("100km", "100 km Rookie", "100+ km total"),
            MilestoneBadgeDef("500km", "500 km Carver", "500+ km total"),
            MilestoneBadgeDef("1000km", "1000 km Iron", "1000+ km total"),
            MilestoneBadgeDef("500m_climb", "Summit Seeker", "500m elevation"),
            MilestoneBadgeDef("5_rides", "Pack Regular", "5 logged rides")
        )
    }

    val totalSharedKm = (userProfile?.totalDistanceKm ?: 0.0) + riders.sumOf { it.totalDistanceKm }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pack Challenges & Milestones", style = MaterialTheme.typography.headlineMedium)
                if (activePack != null) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("create_challenge_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }
        }

        // Pack Aggregate Stats Banner
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = String.format("%.1f km Pack Distance", totalSharedKm),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${riders.size} Active Riders • ${userProfile?.ridesCompleted ?: 0} Your Logged Rides",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Active Map-Based Challenges
        item {
            Text("Map-Based Pack Challenges", style = MaterialTheme.typography.titleMedium)
        }

        if (activePack == null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Join or create a Pack in the Pack tab to launch map-based elevation, convoy, and sprint challenges.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else if (challenges.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No Active Challenges", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Create a Summit Climb, Convoy Cohesion, or Sprint challenge for your riders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { showCreateDialog = true }) {
                            Text("Launch First Challenge")
                        }
                    }
                }
            }
        } else {
            items(challenges, key = { it.id }) { ch ->
                val progress = (ch.currentValue / ch.targetValue.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                val completed = ch.status == "completed"
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (ch.challengeType) {
                                            "ELEVATION" -> Icons.Default.Terrain
                                            "SPRINT" -> Icons.Default.Speed
                                            else -> Icons.Default.Flag
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(ch.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${ch.currentValue.toInt()} / ${ch.targetValue.toInt()} ${ch.unit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (completed) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Challenge Completed",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val step = (ch.targetValue * 0.25).coerceAtLeast(1.0)
                                        onIncrementChallenge(ch, step)
                                    },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Log +25%")
                                }
                            }
                        }

                        if (ch.description.isNotBlank()) {
                            Text(
                                text = ch.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Travel Distance Milestone Medals
        item {
            Text("Travel Distance Milestones", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(milestones, key = { it.id }) { badge ->
                    val unlocked = userProfile?.earnedMilestones?.contains(badge.id) == true
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (unlocked) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.width(136.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MilitaryTech,
                                contentDescription = badge.title,
                                tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = badge.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (unlocked) "Unlocked" else badge.requirementLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (unlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Featured Latest Trip Summary & Clickable Trip History
        if (userTrips.isNotEmpty()) {
            val latestTrip = userTrips.first()
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LATEST TRIP SUMMARY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${latestTrip.durationMin} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = latestTrip.title.ifBlank { "MotoPack Ride" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = String.format(
                                "%.1f km • Avg %.0f km/h • %+.0f m elev (+%.0f m climb)",
                                latestTrip.distanceKm,
                                latestTrip.avgSpeedKmh,
                                latestTrip.elevationChangeNetM,
                                latestTrip.elevationGainM
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (latestTrip.weatherSummary.isNotBlank()) {
                            Text(
                                text = "Weather (${latestTrip.weatherSeverity}): ${latestTrip.weatherSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        OutlinedButton(
                            onClick = { onSelectTripForSummary(latestTrip) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_latest_trip_summary_button")
                        ) {
                            Text("Open Trip Summary Dashboard")
                        }
                    }
                }
            }

            item {
                Text("Your Logged Trip History", style = MaterialTheme.typography.titleMedium)
            }
            items(userTrips, key = { it.id }) { trip ->
                Surface(
                    onClick = { onSelectTripForSummary(trip) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trip_history_row_${trip.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(trip.title.ifBlank { "MotoPack Ride" }, style = MaterialTheme.typography.titleSmall)
                            Text(
                                String.format(
                                    "%.1f km • Avg %.0f km/h • %+.0fm elev",
                                    trip.distanceKm,
                                    trip.avgSpeedKmh,
                                    trip.elevationChangeNetM
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (trip.weatherSummary.isNotBlank()) {
                                Text(
                                    text = "${trip.weatherSeverity}: ${trip.weatherSummary}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Text(
                            "${trip.durationMin}m",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("Peak 500 Elevation Climb") }
        var desc by remember { mutableStateOf("Climb 500m elevation across the ridge with the pack") }
        var selectedType by remember { mutableStateOf("ELEVATION") }
        var targetStr by remember { mutableStateOf("500") }

        val unit = when (selectedType) {
            "ELEVATION" -> "m"
            "SPRINT" -> "km"
            "CONVOY" -> "riders"
            else -> "pts"
        }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Launch Map Challenge") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ELEVATION", "SPRINT", "CHECKPOINT").forEach { t ->
                            FilterChip(
                                selected = selectedType == t,
                                onClick = { selectedType = t },
                                label = { Text(t.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Challenge Title") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                    OutlinedTextField(
                        value = targetStr,
                        onValueChange = { targetStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Target ($unit)") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = targetStr.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0
                    onCreateChallenge(title, desc, selectedType, target, unit)
                    showCreateDialog = false
                }) { Text("Launch") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}
