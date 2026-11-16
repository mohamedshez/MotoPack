package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ai.ScenicRoutePreferences
import com.example.ai.ScenicRouteSuggesterService
import com.example.ai.ScenicRouteUiState
import com.example.auth.onGoogleSignInClicked
import com.example.data.local.GuestSessionState
import com.example.data.local.MapAreaDownloadProgress
import com.example.data.local.OfflineMapAreaEntity
import com.example.data.local.OfflineMapAreaManager
import com.example.data.model.BikerBrewCatalog
import com.example.data.model.BikerEvent
import com.example.data.model.GarageBike
import com.example.data.model.MaintenanceReminderItem
import com.example.data.model.MaintenanceTaskType
import com.example.data.model.PassportStamp
import com.example.data.model.RideTrip
import com.example.data.model.ServiceHistoryLog
import com.example.data.remote.ComputedMotoRoute
import com.example.data.remote.MotoRouteAndWeatherRepository
import com.example.data.remote.OpenWeatherRepository
import com.example.data.remote.RiderLiveWeatherAlert
import com.example.export.TripReportExporter
import com.example.location.LiveGpsFix
import com.example.ui.theme.TelemetryMonoFontFamily
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun GuestCountdownBanner(
    remainingMs: Long,
    onSignInNow: () -> Unit
) {
    val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val isLow = mins < 5L
    val accent = if (isLow) MaterialTheme.colorScheme.error else Color(0xFFFFB300)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("guest_countdown_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = String.format("Guest Preview • %02d:%02d left", mins, secs),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = TelemetryMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(
                        text = "Sign in with Google to unlock cloud sync, pack radar & unlimited access",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(
                onClick = onSignInNow,
                modifier = Modifier.testTag("guest_upgrade_signin_button")
            ) {
                Text("Google Sign-In", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GuestExpiredLockOverlay(
    onGoogleSignInSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(24.dp)
            .testTag("guest_expired_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.guest_expired_title),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.guest_expired_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Your temporary 30-minute guest preview has expired. MotoPack now requires signing in with Google to continue using Navigation, AI Scenic Routes, Garage & Encrypted Pack features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onGoogleSignInSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = { isLoading = false }
                        )
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("guest_lock_google_signin_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.sign_in_with_google),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GuestMotoPackShell(
    guestState: GuestSessionState,
    onExitGuestToSignIn: () -> Unit,
    onGoogleSignInSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val routeRepo = remember { MotoRouteAndWeatherRepository() }
    val weatherRepo = remember { OpenWeatherRepository() }
    val scenicService = remember { ScenicRouteSuggesterService() }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(guestState.firstStartedAtMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val remainingMs = guestState.remainingMs(nowMs)
    if (guestState.isExpired(nowMs) || remainingMs <= 0L) {
        GuestExpiredLockOverlay(onGoogleSignInSuccess = onGoogleSignInSuccess)
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showGuestOfflineMaps by rememberSaveable { mutableStateOf(false) }
    val guestUid = "guest_preview"
    val myGpsFix = remember {
        LiveGpsFix(
            lat = 53.3811,
            lng = -1.4701,
            speedKmh = 0.0,
            headingDeg = 0f,
            altitudeM = 185.0,
            timestampMs = System.currentTimeMillis()
        )
    }

    var plannedRoute by remember { mutableStateOf<ComputedMotoRoute?>(null) }
    var isPlanning by remember { mutableStateOf(false) }
    var liveWeather by remember { mutableStateOf<RiderLiveWeatherAlert?>(null) }
    var scenicPrefs by remember { mutableStateOf(ScenicRoutePreferences()) }
    var scenicState by remember { mutableStateOf<ScenicRouteUiState>(ScenicRouteUiState.Idle) }
    var scenicJob by remember { mutableStateOf<Job?>(null) }
    var guestOfflineAreas by remember { mutableStateOf<List<OfflineMapAreaEntity>>(emptyList()) }
    var activeGuestOfflineArea by remember { mutableStateOf<OfflineMapAreaEntity?>(null) }

    var guestStamps by remember { mutableStateOf<List<PassportStamp>>(emptyList()) }
    var guestEvents by remember { mutableStateOf<List<BikerEvent>>(emptyList()) }

    val initialBike = remember {
        GarageBike(
            id = "guest_bike_1",
            userId = guestUid,
            name = "Demo Tracer 9",
            makeModel = "Yamaha Tracer 9 GT",
            year = 2024,
            currentOdometerKm = 11250.0,
            engineCc = 890,
            tirePsiFront = 36,
            tirePsiRear = 42,
            isPrimary = true
        )
    }
    var guestBikes by remember { mutableStateOf(listOf(initialBike)) }
    var guestReminders by remember {
        mutableStateOf(
            com.example.data.model.GarageMaintenanceCalculator.starterRemindersForBike(
                guestUid,
                initialBike.id,
                initialBike.name,
                initialBike.currentOdometerKm
            )
        )
    }
    var guestLogs by remember { mutableStateOf<List<ServiceHistoryLog>>(emptyList()) }

    val sampleGuestTrip = remember {
        RideTrip(
            id = "guest_demo_trip",
            userId = guestUid,
            title = "Peak District Guest Preview Loop",
            distanceKm = 68.4,
            elevationGainM = 620.0,
            elevationChangeNetM = 285.0,
            durationMin = 74L,
            avgSpeedKmh = 55.5,
            startLat = 53.3811,
            startLng = -1.4701,
            endLat = 53.4339,
            endLng = -1.8732,
            weatherSummary = "Clear afternoon • Moderate ridge breeze",
            weatherSeverity = "CLEAR",
            maxWindGustKmh = 24.0,
            batteryEfficiencyUsed = false,
            batteryPercentUsed = 12
        )
    }
    var showGuestTripSummary by remember { mutableStateOf(false) }
    val pdfCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = TripReportExporter.writeTripReportPdfToUri(
                    context = context,
                    destinationUri = uri,
                    trip = sampleGuestTrip,
                    elevationSamples = buildEstimatedTripElevationSamples(sampleGuestTrip),
                    riderName = "Guest Rider",
                    bikeModel = guestBikes.firstOrNull()?.makeModel ?: "Demo Tracer 9"
                )
                Toast.makeText(
                    context,
                    if (res.isSuccess) "PDF trip report saved!" else "Failed to write PDF file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(if (selectedTab == 0) Icons.Filled.Route else Icons.Outlined.Route, contentDescription = "Routes") },
                    label = { Text("Routes") },
                    modifier = Modifier.testTag("guest_nav_route")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(if (selectedTab == 1) Icons.Filled.Event else Icons.Outlined.Event, contentDescription = "Events") },
                    label = { Text("Events") },
                    modifier = Modifier.testTag("guest_nav_events")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(if (selectedTab == 2) Icons.Filled.Build else Icons.Outlined.Build, contentDescription = "Garage") },
                    label = { Text("Garage") },
                    modifier = Modifier.testTag("guest_nav_garage")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(if (selectedTab == 3) Icons.Filled.Map else Icons.Outlined.Map, contentDescription = "Summary") },
                    label = { Text("Summary") },
                    modifier = Modifier.testTag("guest_nav_summary")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            GuestCountdownBanner(
                remainingMs = remainingMs,
                onSignInNow = onExitGuestToSignIn
            )

            Box(modifier = Modifier.weight(1f)) {
                if (showGuestOfflineMaps) {
                    OfflineMapsScreen(
                        offlineMapAreas = guestOfflineAreas,
                        activeOfflineArea = activeGuestOfflineArea,
                        plannedRoute = plannedRoute,
                        myGpsFix = myGpsFix,
                        mapDownloadProgress = MapAreaDownloadProgress(),
                        isEffectiveOffline = false,
                        onSearchPlaces = { q ->
                            routeRepo.searchPlacesForOfflineArea(
                                query = q,
                                biasCenter = LatLng(myGpsFix.lat, myGpsFix.lng),
                                context = context
                            )
                        },
                        onDownloadArea = { title, center, radiusKm, _ ->
                            val est = OfflineMapAreaManager.estimateAreaDownload(
                                center = center,
                                routePoints = emptyList(),
                                radiusKm = radiusKm
                            )
                            val latDelta = radiusKm / 111.0
                            val lngDelta = radiusKm / 70.0
                            val previewArea = OfflineMapAreaEntity(
                                areaId = "guest_area_${System.currentTimeMillis()}",
                                packId = "guest",
                                title = title,
                                centerLat = center.latitude,
                                centerLng = center.longitude,
                                minLat = center.latitude - latDelta,
                                maxLat = center.latitude + latDelta,
                                minLng = center.longitude - lngDelta,
                                maxLng = center.longitude + lngDelta,
                                radiusKm = radiusKm,
                                zoomRangeLabel = est.zoomRangeLabel,
                                tileCount = est.tileCount,
                                storageBytes = est.estimatedBytes,
                                routePolyline = plannedRoute?.encodedPolyline ?: "",
                                navStepCount = plannedRoute?.navigationSteps?.size ?: 0,
                                fuelStopCount = plannedRoute?.fuelStations?.size ?: 0,
                                elevationSampleCount = plannedRoute?.elevationSamples?.size ?: 0
                            )
                            guestOfflineAreas = listOf(previewArea) + guestOfflineAreas
                            activeGuestOfflineArea = previewArea
                            Toast.makeText(context, "Cached $title (${est.tileCount} tiles) in guest preview", Toast.LENGTH_SHORT).show()
                        },
                        onSelectOfflineArea = { activeGuestOfflineArea = it },
                        onDeleteOfflineArea = { target ->
                            guestOfflineAreas = guestOfflineAreas.filterNot { it.areaId == target.areaId }
                            if (activeGuestOfflineArea?.areaId == target.areaId) {
                                activeGuestOfflineArea = null
                            }
                        },
                        onBack = { showGuestOfflineMaps = false }
                    )
                } else {
                when (selectedTab) {
                    0 -> RoutePlannerScreen(
                        myGpsFix = myGpsFix,
                        plannedRoute = plannedRoute,
                        isPlanning = isPlanning,
                        savedOfflineRoutes = emptyList(),
                        offlineMapAreas = guestOfflineAreas,
                        activeOfflineArea = activeGuestOfflineArea,
                        isEffectiveOffline = false,
                        scenicPreferences = scenicPrefs,
                        scenicRouteState = scenicState,
                        onUpdateScenicPreferences = { scenicPrefs = it },
                        onOpenOfflineMaps = { showGuestOfflineMaps = true },
                        onSuggestScenicRoutes = {
                            scenicJob?.cancel()
                            scenicJob = scope.launch {
                                scenicState = ScenicRouteUiState.Loading
                                try {
                                    val wx = liveWeather ?: withTimeoutOrNull(2_000L) {
                                        weatherRepo.fetchRiderWeatherAlert(myGpsFix.lat, myGpsFix.lng)
                                    }?.also { liveWeather = it }
                                    val brewSummary = BikerBrewCatalog.passportStops.take(4).joinToString("; ") { it.name }
                                    val bundle = scenicService.suggestScenicRoutes(
                                        myGpsFix = myGpsFix,
                                        weather = wx,
                                        trafficDelayMin = plannedRoute?.trafficDelayMin ?: 0,
                                        preferences = scenicPrefs,
                                        bikeModel = guestBikes.firstOrNull()?.makeModel ?: "Adventure Tourer",
                                        nearbyBrewStopsSummary = brewSummary
                                    )
                                    scenicState = ScenicRouteUiState.Success(bundle)
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (t: Throwable) {
                                    scenicState = ScenicRouteUiState.Error(ScenicRouteSuggesterService.formatErrorMessage(t))
                                }
                            }
                        },
                        onCancelScenicRoutes = {
                            scenicJob?.cancel()
                            scenicJob = null
                            if (scenicState is ScenicRouteUiState.Loading) {
                                scenicState = ScenicRouteUiState.Idle
                            }
                        },
                        onPlanRoute = { name, label, coords ->
                            scope.launch {
                                isPlanning = true
                                plannedRoute = routeRepo.planMotorcycleRoute(
                                    routeName = name,
                                    origin = LatLng(myGpsFix.lat, myGpsFix.lng),
                                    destinationLabel = label,
                                    destinationLatLng = coords
                                )
                                isPlanning = false
                            }
                        },
                        onSaveRouteOffline = {
                            Toast.makeText(context, "Sign in with Google to save unlimited routes", Toast.LENGTH_SHORT).show()
                        },
                        onLoadOfflineRoute = {},
                        onBroadcastWeatherAlert = {
                            Toast.makeText(context, "Sign in with Google to broadcast alerts to a pack", Toast.LENGTH_SHORT).show()
                        }
                    )

                    1 -> EventsAndPassportScreen(
                        currentUserId = guestUid,
                        myGpsFix = myGpsFix,
                        passportStamps = guestStamps,
                        personalEvents = guestEvents,
                        packEvents = emptyList(),
                        onStampLocation = { loc, note ->
                            val existing = guestStamps.filterNot { it.locationId == loc.id }
                            guestStamps = existing + PassportStamp(
                                id = loc.id,
                                userId = guestUid,
                                locationId = loc.id,
                                locationName = loc.name,
                                stampCode = loc.passportStampCode,
                                brewNote = note,
                                lat = loc.lat,
                                lng = loc.lng,
                                verifiedGps = true
                            )
                        },
                        onCreateEvent = { title, desc, type, locName, brew, sched, lat, lng, isPass, locId ->
                            guestEvents = listOf(
                                BikerEvent(
                                    id = "g_evt_${System.currentTimeMillis()}",
                                    packId = "personal",
                                    creatorId = guestUid,
                                    creatorName = "Guest Rider",
                                    title = title,
                                    description = desc,
                                    eventType = type,
                                    locationId = locId,
                                    locationName = locName,
                                    brewSpecialty = brew,
                                    scheduledLabel = sched,
                                    lat = lat,
                                    lng = lng,
                                    attendeeUids = listOf(guestUid),
                                    isPassportEvent = isPass
                                )
                            ) + guestEvents
                        },
                        onToggleRsvp = { evt ->
                            Toast.makeText(context, "Preview RSVP toggled for ${evt.title}", Toast.LENGTH_SHORT).show()
                        },
                        onRouteToDestination = { name, label, coords ->
                            selectedTab = 0
                            scope.launch {
                                isPlanning = true
                                plannedRoute = routeRepo.planMotorcycleRoute(
                                    routeName = name,
                                    origin = LatLng(myGpsFix.lat, myGpsFix.lng),
                                    destinationLabel = label,
                                    destinationLatLng = coords
                                )
                                isPlanning = false
                            }
                        }
                    )

                    2 -> GarageScreen(
                        bikes = guestBikes,
                        reminders = guestReminders,
                        serviceLogs = guestLogs,
                        defaultBikeName = "Demo Tracer 9",
                        onAddBike = { name, model, year, odo, cc, psiF, psiR ->
                            val newBike = GarageBike(
                                id = "g_bike_${System.currentTimeMillis()}",
                                userId = guestUid,
                                name = name,
                                makeModel = model,
                                year = year,
                                currentOdometerKm = odo,
                                engineCc = cc,
                                tirePsiFront = psiF,
                                tirePsiRear = psiR,
                                isPrimary = false
                            )
                            guestBikes = guestBikes + newBike
                            guestReminders = guestReminders + com.example.data.model.GarageMaintenanceCalculator.starterRemindersForBike(
                                guestUid,
                                newBike.id,
                                newBike.name,
                                odo
                            )
                        },
                        onUpdateOdometer = { bikeId, km ->
                            guestBikes = guestBikes.map { if (it.id == bikeId) it.copy(currentOdometerKm = km) else it }
                        },
                        onAddReminder = { bike, type, title, intervalKm, notes ->
                            guestReminders = guestReminders + MaintenanceReminderItem(
                                id = "g_rem_${System.currentTimeMillis()}",
                                userId = guestUid,
                                bikeId = bike.id,
                                bikeName = bike.name,
                                taskType = type.code,
                                title = title,
                                notes = notes,
                                intervalKm = intervalKm,
                                lastServiceOdometerKm = bike.currentOdometerKm,
                                nextDueOdometerKm = bike.currentOdometerKm + intervalKm
                            )
                        },
                        onLogServiceDone = { bike, rem, type, title, odo, cost, shop, notes ->
                            guestLogs = listOf(
                                ServiceHistoryLog(
                                    id = "g_log_${System.currentTimeMillis()}",
                                    userId = guestUid,
                                    bikeId = bike.id,
                                    bikeName = bike.name,
                                    taskType = type.code,
                                    title = title,
                                    odometerKm = odo,
                                    costAmount = cost,
                                    shopOrDiy = shop,
                                    notes = notes
                                )
                            ) + guestLogs
                            if (rem != null) {
                                guestReminders = guestReminders.map {
                                    if (it.id == rem.id) {
                                        it.copy(lastServiceOdometerKm = odo, nextDueOdometerKm = odo + it.intervalKm)
                                    } else it
                                }
                            }
                        },
                        onDeleteReminder = { id -> guestReminders = guestReminders.filterNot { it.id == id } },
                        onDeleteServiceLog = { id -> guestLogs = guestLogs.filterNot { it.id == id } }
                    )

                    3 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Trip Report & PDF Preview", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Preview exporting a formatted PDF Trip Report & sharing to social media. Note: Live Pack Radar, Cloud Backup, and PTT Voice require Google Sign-In.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showGuestTripSummary = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("guest_open_trip_summary_button")
                        ) {
                            Text("Open Sample Trip Summary & Export PDF")
                        }
                        Button(
                            onClick = onExitGuestToSignIn,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Sign in with Google for Full Features")
                        }
                    }
                }
                }
            }
        }

        if (showGuestTripSummary) {
            val elev = remember { buildEstimatedTripElevationSamples(sampleGuestTrip) }
            TripSummaryDashboardSheet(
                trip = sampleGuestTrip,
                elevationSamples = elev,
                riderName = "Guest Rider",
                bikeModel = guestBikes.firstOrNull()?.makeModel ?: "Demo Tracer 9",
                isBatteryEfficiencyEnabled = false,
                onToggleBatteryEfficiency = {},
                onSavePdf = { tripToExport, _ ->
                    pdfCreateLauncher.launch(TripReportExporter.buildSuggestedPdfFileName(tripToExport))
                },
                onSharePdf = { tripToExport, samples ->
                    val bikeName = guestBikes.firstOrNull()?.makeModel ?: "Demo Tracer 9"
                    scope.launch {
                        TripReportExporter.createShareablePdfFile(
                            context = context,
                            trip = tripToExport,
                            elevationSamples = samples,
                            riderName = "Guest Rider",
                            bikeModel = bikeName
                        ).onSuccess { file ->
                            TripReportExporter.launchShareTripReportChooser(
                                context = context,
                                pdfFile = file,
                                trip = tripToExport,
                                riderName = "Guest Rider",
                                bikeModel = bikeName
                            )
                        }.onFailure {
                            Toast.makeText(context, "Failed to generate PDF share file", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { showGuestTripSummary = false }
            )
        }
    }
}
