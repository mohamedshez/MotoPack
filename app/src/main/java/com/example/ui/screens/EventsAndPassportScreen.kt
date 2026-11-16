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
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BikerBrewCatalog
import com.example.data.model.BikerEvent
import com.example.data.model.BikerLocation
import com.example.data.model.PassportStamp
import com.example.location.LiveGpsFix
import com.example.ui.theme.TelemetryMonoFontFamily
import com.google.android.gms.maps.model.LatLng

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventsAndPassportScreen(
    currentUserId: String,
    myGpsFix: LiveGpsFix,
    passportStamps: List<PassportStamp>,
    personalEvents: List<BikerEvent>,
    packEvents: List<BikerEvent>,
    onStampLocation: (location: BikerLocation, brewNote: String) -> Unit,
    onCreateEvent: (
        title: String,
        description: String,
        eventType: String,
        locationName: String,
        brewSpecialty: String,
        scheduledLabel: String,
        lat: Double,
        lng: Double,
        isPassportEvent: Boolean,
        locationId: String
    ) -> Unit,
    onToggleRsvp: (BikerEvent) -> Unit,
    onRouteToDestination: (routeName: String, destinationLabel: String, coords: LatLng) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }
    var checkInTarget by remember { mutableStateOf<BikerLocation?>(null) }
    var showCreateEventDialog by remember { mutableStateOf(false) }

    val stampedLocationIds = remember(passportStamps) {
        passportStamps.map { it.locationId }.toSet()
    }
    val rankInfo = remember(passportStamps.size) {
        BikerBrewCatalog.computePassportRank(passportStamps.size)
    }

    val combinedEvents = remember(personalEvents, packEvents) {
        val personalById = personalEvents.associateBy { it.id }
        val curatedMerged = BikerBrewCatalog.curatedCommunityEvents.map { curated ->
            personalById[curated.id] ?: curated
        }
        val extraPersonal = personalEvents.filter { p ->
            BikerBrewCatalog.curatedCommunityEvents.none { it.id == p.id }
        }
        (packEvents + extraPersonal + curatedMerged).distinctBy { it.id }
    }

    val filteredEvents = remember(combinedEvents, selectedFilter) {
        when (selectedFilter) {
            "BIKER_BREW" -> combinedEvents.filter { it.eventType == "BIKER_BREW" || it.isPassportEvent }
            "BIKE_NIGHT" -> combinedEvents.filter { it.eventType == "BIKE_NIGHT" }
            "RIDE_OUT" -> combinedEvents.filter { it.eventType == "RIDE_OUT" || it.eventType == "SCENIC_MEET" }
            else -> combinedEvents
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Events & Brew",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${filteredEvents.size} events • ${stampedLocationIds.size}/${BikerBrewCatalog.passportStops.size} passport stamps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Button(
                    onClick = { showCreateEventDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("create_biker_event_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Host Event")
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag("events_tab_events")
                ) {
                    Text("Events (${filteredEvents.size})", maxLines = 1)
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("events_tab_passport")
                ) {
                    Text("Brew Passport", maxLines = 1)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedTab == 1) {
                // Biker & Brew Passport Feature Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("biker_brew_passport_card")
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
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalCafe,
                                    contentDescription = "Biker and Brew Passport",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = BikerBrewCatalog.PASSPORT_SEASON_TITLE,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${rankInfo.title} • ${rankInfo.badgeTag}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${stampedLocationIds.size}/${BikerBrewCatalog.passportStops.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = TelemetryMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = {
                            (stampedLocationIds.size.toFloat() / BikerBrewCatalog.passportStops.size.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = rankInfo.rewardPerk,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 6 Interactive Passport Stamp Tiles
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BikerBrewCatalog.passportStops.forEach { stop ->
                            val isStamped = stop.id in stampedLocationIds
                            Surface(
                                onClick = { checkInTarget = stop },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isStamped) {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isStamped) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .testTag("passport_stamp_${stop.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isStamped) Icons.Default.Verified else Icons.Default.QrCode2,
                                        contentDescription = if (isStamped) "Stamped" else "Check in",
                                        tint = if (isStamped) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "${stop.passportStampCode} • ${stop.name.substringBefore(" ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isStamped) "Stamped" else "Tap to stamp",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isStamped) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Biker-Friendly Locations Vertical List
        item {
            Text("Biker-Friendly Cafés & Hangouts", style = MaterialTheme.typography.titleMedium)
        }
        items(BikerBrewCatalog.curatedLocations, key = { it.id }) { loc ->
            val distKm = BikerBrewCatalog.distanceKmTo(myGpsFix.lat, myGpsFix.lng, loc.lat, loc.lng)
            val isStamped = loc.id in stampedLocationIds
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("biker_location_card_${loc.id}")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = if (loc.isPassportStop) "${loc.passportStampCode} STOP" else loc.category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = String.format("%.1f km", distKm),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = TelemetryMonoFontFamily,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = loc.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${loc.cityRegion} • ${loc.brewSpecialty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = loc.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        loc.amenities.take(3).forEach { amenity ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = amenity,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onRouteToDestination(loc.name, loc.name, LatLng(loc.lat, loc.lng))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("route_location_${loc.id}")
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Route")
                        }
                        Button(
                            onClick = { checkInTarget = loc },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("checkin_location_${loc.id}")
                        ) {
                            Icon(
                                if (isStamped) Icons.Default.CheckCircle else Icons.Default.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isStamped) "Stamped" else "Stamp")
                        }
                    }
                }
            }
        }
            } else {
        // Compact Passport Summary Strip + Biker Events Filter Chips
        item {
            Surface(
                onClick = { selectedTab = 1 },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("events_compact_passport_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalCafe,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Brew Passport: ${stampedLocationIds.size}/${BikerBrewCatalog.passportStops.size} stamps • ${rankInfo.title}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val filters = listOf(
                    "ALL" to "All Events",
                    "BIKER_BREW" to "Biker & Brew",
                    "BIKE_NIGHT" to "Bike Nights",
                    "RIDE_OUT" to "Ride-Outs"
                )
                items(filters) { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        items(filteredEvents, key = { it.id }) { event ->
            val isGoing = currentUserId in event.attendeeUids
            val distKm = BikerBrewCatalog.distanceKmTo(myGpsFix.lat, myGpsFix.lng, event.lat, event.lng)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (event.isPassportEvent) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("biker_event_card_${event.id}")
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = event.eventType.replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (event.isPassportEvent) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "PASSPORT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${event.attendeeUids.size} Going • ${String.format("%.0f km", distKm)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = TelemetryMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = event.scheduledLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = event.locationName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (event.brewSpecialty.isNotBlank()) {
                        Text(
                            text = "Featured Brew: ${event.brewSpecialty}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (event.description.isNotBlank()) {
                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onToggleRsvp(event) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("rsvp_event_${event.id}")
                        ) {
                            Icon(
                                if (isGoing) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isGoing) "Going" else "Join Event")
                        }
                        OutlinedButton(
                            onClick = {
                                onRouteToDestination(event.title, event.locationName, LatLng(event.lat, event.lng))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Route Here")
                        }
                    }
                }
            }
        }
            }
        }
    }

    checkInTarget?.let { stop ->
        var brewNote by remember { mutableStateOf("Ordered ${stop.brewSpecialty}") }
        val distKm = BikerBrewCatalog.distanceKmTo(myGpsFix.lat, myGpsFix.lng, stop.lat, stop.lng)
        val withinGpsRange = distKm <= BikerBrewCatalog.CHECK_IN_RADIUS_KM
        AlertDialog(
            onDismissRequest = { checkInTarget = null },
            title = { Text("Stamp ${stop.passportStampCode} • ${stop.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (withinGpsRange) {
                            "GPS Verified! You are within ${String.format("%.2f", distKm)} km of this Biker & Brew stop."
                        } else {
                            "Distance to stop: ${String.format("%.1f", distKm)} km. Check-in will record your visit note to your Passport."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (withinGpsRange) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = brewNote,
                        onValueChange = { brewNote = it },
                        label = { Text("Brew & Ride Note") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStampLocation(stop, brewNote)
                        checkInTarget = null
                    },
                    modifier = Modifier.testTag("confirm_stamp_passport_button")
                ) {
                    Text("Stamp Passport")
                }
            },
            dismissButton = {
                TextButton(onClick = { checkInTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateEventDialog) {
        var title by remember { mutableStateOf("Sunday Roast & Ridge Run") }
        var desc by remember { mutableStateOf("Convoy meetup for espresso, route briefing, and scenic ridge cruise.") }
        var selectedType by remember { mutableStateOf("BIKER_BREW") }
        var selectedLoc by remember { mutableStateOf(BikerBrewCatalog.curatedLocations.first()) }
        var schedule by remember { mutableStateOf("Sunday • 09:00 AM") }
        var isPassport by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateEventDialog = false },
            title = { Text("Host Biker & Brew Event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("BIKER_BREW", "BIKE_NIGHT", "RIDE_OUT", "SCENIC_MEET")) { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.replace("_", " ")) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Event Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = schedule,
                        onValueChange = { schedule = it },
                        label = { Text("Date & Time") },
                        singleLine = true
                    )
                    Text("Select Host Café / Hangout", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BikerBrewCatalog.curatedLocations) { loc ->
                            FilterChip(
                                selected = selectedLoc.id == loc.id,
                                onClick = { selectedLoc = loc },
                                label = { Text(loc.name.substringBefore(" ")) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Meetup Notes") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateEvent(
                            title,
                            desc,
                            selectedType,
                            selectedLoc.name,
                            selectedLoc.brewSpecialty,
                            schedule,
                            selectedLoc.lat,
                            selectedLoc.lng,
                            isPassport,
                            selectedLoc.id
                        )
                        showCreateEventDialog = false
                    },
                    modifier = Modifier.testTag("confirm_create_event_button")
                ) {
                    Text("Publish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateEventDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
