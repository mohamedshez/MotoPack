package com.example.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOutFromMotoPack
import com.example.data.local.GuestSessionState
import com.example.data.local.MotoPackDatabase
import com.example.data.local.MotoPackPreferences
import com.example.data.local.OfflineMapAreaManager
import com.example.data.remote.MotoRouteAndWeatherRepository
import com.example.data.repository.MotoPackRepository
import com.example.export.TripReportExporter
import com.example.location.DeviceTelemetryMonitor
import com.example.location.MotoPackNotificationHelper
import com.example.location.RideTrackingService
import com.example.location.RouteWeatherPushService
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChallengesScreen
import com.example.ui.screens.EventsAndPassportScreen
import com.example.ui.screens.GarageScreen
import com.example.ui.screens.GuestExpiredLockOverlay
import com.example.ui.screens.GuestMotoPackShell
import com.example.ui.screens.MapRadarScreen
import com.example.ui.screens.OfflineMapsScreen
import com.example.ui.screens.PackHubScreen
import com.example.ui.screens.RiderHudScreen
import com.example.ui.screens.RoutePlannerScreen
import com.example.ui.screens.TripSummaryDashboardSheet
import com.example.ui.theme.MotoPackTheme
import com.example.ui.viewmodel.AlertEvent
import com.example.ui.viewmodel.MotoPackViewModel
import com.example.weather.RouteWeatherActionBus
import com.example.weather.RouteWeatherNotificationAction
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable
data object MapRoute

@Serializable
data object OfflineMapsRoute

@Serializable
data object RoutePlannerRoute

@Serializable
data object EventsRoute

@Serializable
data object GarageRoute

@Serializable
data object PackRoute

@Serializable
data object ChallengesRoute

@Serializable
data object RiderHudRoute

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopDestinations = listOf(
    TopLevelDestination(
        route = MapRoute,
        labelRes = R.string.tab_map,
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map,
        testTag = "nav_map"
    ),
    TopLevelDestination(
        route = RoutePlannerRoute,
        labelRes = R.string.tab_route,
        selectedIcon = Icons.Filled.Route,
        unselectedIcon = Icons.Outlined.Route,
        testTag = "nav_route"
    ),
    TopLevelDestination(
        route = EventsRoute,
        labelRes = R.string.tab_events,
        selectedIcon = Icons.Filled.Event,
        unselectedIcon = Icons.Outlined.Event,
        testTag = "nav_events"
    ),
    TopLevelDestination(
        route = GarageRoute,
        labelRes = R.string.tab_garage,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_garage"
    ),
    TopLevelDestination(
        route = PackRoute,
        labelRes = R.string.tab_pack,
        selectedIcon = Icons.Filled.Group,
        unselectedIcon = Icons.Outlined.Group,
        testTag = "nav_pack"
    )
)

@Composable
fun MotoPackRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val preferences = remember(app) { MotoPackPreferences(app) }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    val guestState by preferences.guestSessionFlow
        .collectAsStateWithLifecycle(initialValue = GuestSessionState())

    LaunchedEffect(Unit) {
        MotoPackNotificationHelper.ensureChannels(context)
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    val user = currentUser
    if (user == null) {
        MotoPackTheme {
            when {
                guestState.isExpired() -> {
                    GuestExpiredLockOverlay(
                        onGoogleSignInSuccess = {
                            scope.launch { preferences.exitGuestPreviewToSignIn() }
                        }
                    )
                }
                guestState.isCurrentlyAllowedInGuest() -> {
                    GuestMotoPackShell(
                        guestState = guestState,
                        onExitGuestToSignIn = {
                            scope.launch { preferences.exitGuestPreviewToSignIn() }
                        },
                        onGoogleSignInSuccess = {
                            scope.launch { preferences.exitGuestPreviewToSignIn() }
                        }
                    )
                }
                else -> {
                    AuthScreen(
                        onAuthSuccess = {
                            scope.launch { preferences.exitGuestPreviewToSignIn() }
                        },
                        guestState = guestState,
                        onContinueAsGuest = {
                            scope.launch {
                                preferences.startOrResumeGuestSession()
                            }
                        }
                    )
                }
            }
        }
    } else {
        key(user.uid) {
            val vm: MotoPackViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val db = FirebaseFirestore.getInstance(
                            app.getString(R.string.firestore_database_id)
                        )
                        val offlineDao = MotoPackDatabase.getInstance(app).offlineDao()
                        MotoPackViewModel(
                            repository = MotoPackRepository(db),
                            offlineDao = offlineDao,
                            routeRepository = MotoRouteAndWeatherRepository(),
                            preferences = preferences,
                            telemetryMonitor = DeviceTelemetryMonitor(app),
                            currentUserId = user.uid,
                            initialDisplayName = user.displayName ?: "Moto Rider",
                            initialPhotoUrl = user.photoUrl?.toString() ?: "",
                            mapAreaManager = OfflineMapAreaManager(app),
                            appContext = app
                        )
                    }
                }
            )
            AuthenticatedMotoPackShell(
                viewModel = vm,
                onSignOut = {
                    if (vm.isRideTrackingActive.value) {
                        vm.toggleRideTracking(false)
                        RideTrackingService.stop(context)
                    }
                    signOutFromMotoPack(credentialManager, scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedMotoPackShell(
    viewModel: MotoPackViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val batteryState by viewModel.batteryState.collectAsStateWithLifecycle()
    val isEffectiveOffline by viewModel.isEffectiveOffline.collectAsStateWithLifecycle()
    val pendingBreadcrumbCount by viewModel.pendingBreadcrumbCount.collectAsStateWithLifecycle()
    val hasLocationPermission by viewModel.hasLocationPermission.collectAsStateWithLifecycle()
    val hasRealGpsFix by viewModel.hasRealGpsFix.collectAsStateWithLifecycle()
    val isRideTrackingActive by viewModel.isRideTrackingActive.collectAsStateWithLifecycle()
    val myGpsFix by viewModel.myGpsFix.collectAsStateWithLifecycle()
    val sessionDistanceKm by viewModel.sessionRideDistanceKm.collectAsStateWithLifecycle()

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val userTrips by viewModel.userTrips.collectAsStateWithLifecycle()
    val userPacks by viewModel.userPacks.collectAsStateWithLifecycle()
    val activePack by viewModel.activePack.collectAsStateWithLifecycle()
    val riders by viewModel.riders.collectAsStateWithLifecycle()
    val selectedRider by viewModel.selectedRider.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val challenges by viewModel.challenges.collectAsStateWithLifecycle()
    val plannedRoute by viewModel.plannedRoute.collectAsStateWithLifecycle()
    val isPlanningRoute by viewModel.isPlanningRoute.collectAsStateWithLifecycle()
    val savedOfflineRoutes by viewModel.savedOfflineRoutes.collectAsStateWithLifecycle()
    val directMessageTarget by viewModel.directMessageTarget.collectAsStateWithLifecycle()
    val liveWeatherAlert by viewModel.liveWeatherAlert.collectAsStateWithLifecycle()
    val isRefreshingWeather by viewModel.isRefreshingWeather.collectAsStateWithLifecycle()
    val aiBriefingState by viewModel.aiBriefingState.collectAsStateWithLifecycle()
    val aiCopilotState by viewModel.aiCopilotState.collectAsStateWithLifecycle()
    val activeTripSummary by viewModel.activeTripSummary.collectAsStateWithLifecycle()
    val packMarkers by viewModel.packMarkers.collectAsStateWithLifecycle()
    val packPhotos by viewModel.packPhotos.collectAsStateWithLifecycle()
    val offlineMapAreas by viewModel.offlineMapAreas.collectAsStateWithLifecycle()
    val mapDownloadProgress by viewModel.mapDownloadProgress.collectAsStateWithLifecycle()
    val activeOfflineArea by viewModel.activeOfflineArea.collectAsStateWithLifecycle()
    val isSosActive by viewModel.isSosActive.collectAsStateWithLifecycle()
    val sosLastBroadcastMs by viewModel.sosLastBroadcastMs.collectAsStateWithLifecycle()
    val incomingSosAlerts by viewModel.incomingSosAlerts.collectAsStateWithLifecycle()
    val hudNavigationState by viewModel.hudNavigationState.collectAsStateWithLifecycle()
    val smartHudState by viewModel.smartHudState.collectAsStateWithLifecycle()
    val hudVoiceEnabled by viewModel.hudVoiceEnabled.collectAsStateWithLifecycle()
    val pttRecordingState by viewModel.pttRecordingState.collectAsStateWithLifecycle()
    val pttPlaybackState by viewModel.pttPlaybackState.collectAsStateWithLifecycle()
    val pendingVoiceCount by viewModel.pendingVoiceCount.collectAsStateWithLifecycle()
    val activeTripElevationSamples by viewModel.activeTripElevationSamples.collectAsStateWithLifecycle()
    val p2pMeshState by viewModel.p2pMeshState.collectAsStateWithLifecycle()
    val meshVpnStatus by viewModel.meshVpnStatus.collectAsStateWithLifecycle()
    val passportStamps by viewModel.passportStamps.collectAsStateWithLifecycle()
    val personalEvents by viewModel.personalEvents.collectAsStateWithLifecycle()
    val packEvents by viewModel.packEvents.collectAsStateWithLifecycle()
    val garageBikes by viewModel.garageBikes.collectAsStateWithLifecycle()
    val maintenanceItems by viewModel.maintenanceItems.collectAsStateWithLifecycle()
    val serviceLogs by viewModel.serviceLogs.collectAsStateWithLifecycle()
    val scenicPreferences by viewModel.scenicPreferences.collectAsStateWithLifecycle()
    val scenicRouteState by viewModel.scenicRouteState.collectAsStateWithLifecycle()
    val proactiveWeatherAlert by viewModel.proactiveWeatherAlert.collectAsStateWithLifecycle()
    val isScanningRouteWeather by viewModel.isScanningRouteWeather.collectAsStateWithLifecycle()
    val uiScope = rememberCoroutineScope()

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val trip = activeTripSummary
        if (uri != null && trip != null) {
            uiScope.launch {
                val res = TripReportExporter.writeTripReportPdfToUri(
                    context = context,
                    destinationUri = uri,
                    trip = trip,
                    elevationSamples = activeTripElevationSamples,
                    riderName = userProfile?.displayName ?: "MotoPack Rider",
                    bikeModel = garageBikes.firstOrNull { it.isPrimary }?.makeModel
                        ?: userProfile?.bikeModel
                        ?: "Motorcycle"
                )
                snackbarHostState.showSnackbar(
                    if (res.isSuccess) "PDF trip report saved!" else "Could not write PDF report"
                )
            }
        }
    }

    val offlineTileProvider = remember(activeOfflineArea, offlineMapAreas) {
        val ids = activeOfflineArea?.let { listOf(it.areaId) }
            ?: offlineMapAreas.map { it.areaId }
        viewModel.buildOfflineTileProvider(ids)
    }

    val p2pPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setP2pMeshEnabled(true)
        } else {
            viewModel.setP2pMeshEnabled(false)
            uiScope.launch {
                snackbarHostState.showSnackbar("Nearby Wi-Fi permission is needed for Wi-Fi Direct peer discovery")
            }
        }
    }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.setMeshVpnEnabled(true)
        } else {
            viewModel.setMeshVpnEnabled(false)
            uiScope.launch {
                snackbarHostState.showSnackbar("Mesh VPN tunnel permission was cancelled")
            }
        }
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(fineGranted || coarseGranted)
    }

    LaunchedEffect(Unit) {
        val fineOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onLocationPermissionResult(fineOk || coarseOk)

        val missing = requiredPermissions.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.alertEvents.collect { event ->
            when (event) {
                is AlertEvent.UrgentNotification -> {
                    MotoPackNotificationHelper.showUrgentAlertNotification(
                        context = context,
                        notificationId = event.id,
                        title = event.title,
                        body = event.body,
                        vibrateUrgently = true
                    )
                    snackbarHostState.showSnackbar("${event.title}: ${event.body}")
                }
                is AlertEvent.RouteWeatherPush -> {
                    MotoPackNotificationHelper.showRouteWeatherPushNotification(
                        context = context,
                        alert = event.alert
                    )
                    snackbarHostState.showSnackbar(
                        "${event.alert.hazardType.label} ahead (~${event.alert.etaMinutes}m): ${event.alert.recommendationSummary}"
                    )
                }
                is AlertEvent.StatusBanner -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    LaunchedEffect(isRideTrackingActive, hasLocationPermission, activePack?.name, settings.ecoRideMode) {
        if (isRideTrackingActive && hasLocationPermission) {
            RideTrackingService.start(
                context = context,
                packName = activePack?.name ?: "Solo Convoy",
                ecoMode = settings.ecoRideMode
            )
        } else {
            RideTrackingService.stop(context)
        }
    }

    LaunchedEffect(
        plannedRoute?.routeName,
        settings.proactiveWeatherPushEnabled,
        isEffectiveOffline,
        settings.ecoRideMode
    ) {
        val route = plannedRoute
        if (route != null && settings.proactiveWeatherPushEnabled && !isEffectiveOffline) {
            RouteWeatherPushService.startOrUpdate(
                context = context,
                routeName = route.routeName,
                ecoMode = settings.ecoRideMode,
                scanNow = false
            )
        } else {
            RouteWeatherPushService.stop(context)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isInRiderHud = currentDestination?.hasRoute(RiderHudRoute::class) == true
    val isInOfflineMaps = currentDestination?.hasRoute(OfflineMapsRoute::class) == true
    val hideChrome = isInRiderHud || isInOfflineMaps

    fun navigateTopLevel(destRoute: Any) {
        navController.navigate(destRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(Unit) {
        RouteWeatherActionBus.actions.collect { action ->
            if (action is RouteWeatherNotificationAction.OpenRoutePlanner ||
                action is RouteWeatherNotificationAction.ApplySuggestedStop ||
                action is RouteWeatherNotificationAction.ApplyRouteDetour
            ) {
                navigateTopLevel(RoutePlannerRoute)
            }
        }
    }

    MotoPackTheme(
        hudThemeMode = settings.hudThemeMode,
        isLowBattery = batteryState.percent <= 20,
        isBatteryEfficiencyMode = settings.ecoRideMode
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail && !hideChrome) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            TopDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.hasRoute(dest.route::class)
                                } == true
                                val label = stringResource(dest.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail && !hideChrome) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            TopDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.hasRoute(dest.route::class)
                                } == true
                                val label = stringResource(dest.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = MapRoute,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable<MapRoute> {
                                MapRadarScreen(
                                    currentUserId = viewModel.currentUserId,
                                    activePack = activePack,
                                    riders = riders,
                                    selectedRider = selectedRider,
                                    myGpsFix = myGpsFix,
                                    hasLocationPermission = hasLocationPermission,
                                    hasRealGpsFix = hasRealGpsFix,
                                    plannedRoute = plannedRoute,
                                    challenges = challenges,
                                    userTrips = userTrips,
                                    packMarkers = packMarkers,
                                    messages = messages,
                                    packPhotos = packPhotos,
                                    offlineMapAreas = offlineMapAreas,
                                    activeOfflineArea = activeOfflineArea,
                                    mapDownloadProgress = mapDownloadProgress,
                                    offlineTileProvider = offlineTileProvider,
                                    isEffectiveOffline = isEffectiveOffline,
                                    pendingBreadcrumbCount = pendingBreadcrumbCount,
                                    ecoRideMode = settings.ecoRideMode,
                                    isRideTrackingActive = isRideTrackingActive,
                                    sessionDistanceKm = sessionDistanceKm,
                                    liveWeatherAlert = liveWeatherAlert,
                                    isRefreshingWeather = isRefreshingWeather,
                                    aiBriefingState = aiBriefingState,
                                    aiCopilotState = aiCopilotState,
                                    p2pMeshState = p2pMeshState,
                                    meshVpnStatus = meshVpnStatus,
                                    onSelectRider = viewModel::selectRider,
                                    onRingRider = viewModel::ringRider,
                                    onMessageRider = { rider ->
                                        viewModel.setDirectMessageTarget(
                                            if (rider.userId == viewModel.currentUserId) null else rider
                                        )
                                        navigateTopLevel(PackRoute)
                                    },
                                    onToggleRideTracking = { active ->
                                        if (active && !hasLocationPermission) {
                                            permissionLauncher.launch(requiredPermissions)
                                        }
                                        viewModel.toggleRideTracking(active)
                                    },
                                    onToggleEcoMode = viewModel::setEcoRideMode,
                                    onNavigateToPackSetup = { navigateTopLevel(PackRoute) },
                                    onRequestLocationPermission = {
                                        permissionLauncher.launch(requiredPermissions)
                                    },
                                    onRefreshWeather = viewModel::refreshWeatherNow,
                                    onBroadcastWeatherAlert = viewModel::broadcastWeatherHazardAlert,
                                    onRunAiBriefing = viewModel::runAiSafetyBriefing,
                                    onAskAiCopilot = viewModel::askAiCopilot,
                                    onCreatePackPin = viewModel::createPackMarker,
                                    onConfirmPackPin = viewModel::confirmPackMarker,
                                    onResolvePackPin = viewModel::resolvePackMarker,
                                    onDeletePackPin = viewModel::deletePackMarker,
                                    onSendGroupChat = { text -> viewModel.sendMessage(text, "") },
                                    onUploadGalleryPhoto = viewModel::postPackGalleryPhoto,
                                    onDeleteGalleryPhoto = viewModel::deletePackGalleryPhoto,
                                    onSelectOfflineArea = viewModel::selectOfflineMapArea,
                                    onDownloadArea = viewModel::downloadArea,
                                    onDeleteOfflineArea = viewModel::deleteOfflineMapArea,
                                    onOpenOfflineMaps = {
                                        navController.navigate(OfflineMapsRoute) {
                                            launchSingleTop = true
                                        }
                                    },
                                    isSosActive = isSosActive,
                                    sosLastBroadcastMs = sosLastBroadcastMs,
                                    myBatteryPercent = batteryState.percent,
                                    incomingSosAlerts = incomingSosAlerts,
                                    onTriggerSos = viewModel::triggerEmergencySos,
                                    onStandDownSos = viewModel::standDownEmergencySos,
                                    onAcknowledgeSos = viewModel::acknowledgePing,
                                    onOpenRiderHud = {
                                        navController.navigate(RiderHudRoute) {
                                            launchSingleTop = true
                                        }
                                    },
                                    pttRecordingState = pttRecordingState,
                                    pttPlaybackState = pttPlaybackState,
                                    pttAutoPlayEnabled = settings.pttAutoPlay,
                                    pendingVoiceCount = pendingVoiceCount,
                                    onStartPttRecording = { viewModel.startPttRecording("") },
                                    onStopAndSendPttRecording = { viewModel.stopAndSendPttRecording("") },
                                    onCancelPttRecording = viewModel::cancelPttRecording,
                                    onTogglePlayVoice = viewModel::togglePlayVoiceMessage,
                                    onTogglePttAutoPlay = viewModel::setPttAutoPlay
                                )
                            }

                            composable<RoutePlannerRoute> {
                                RoutePlannerScreen(
                                    myGpsFix = myGpsFix,
                                    plannedRoute = plannedRoute,
                                    isPlanning = isPlanningRoute,
                                    savedOfflineRoutes = savedOfflineRoutes,
                                    offlineMapAreas = offlineMapAreas,
                                    activeOfflineArea = activeOfflineArea,
                                    mapDownloadProgress = mapDownloadProgress,
                                    offlineTileProvider = offlineTileProvider,
                                    isEffectiveOffline = isEffectiveOffline,
                                    isP2pMeshActive = p2pMeshState.isActive,
                                    isMeshVpnActive = meshVpnStatus.isActive,
                                    meshVirtualIp = meshVpnStatus.virtualIp,
                                    onPlanRoute = viewModel::planRoute,
                                    onSaveRouteOffline = viewModel::savePlannedRouteOffline,
                                    onLoadOfflineRoute = viewModel::loadOfflineRoute,
                                    onBroadcastWeatherAlert = viewModel::broadcastWeatherHazardAlert,
                                    onDownloadRouteMapArea = viewModel::downloadRouteMapArea,
                                    onDownloadRadiusMapArea = viewModel::downloadRadiusMapArea,
                                    onDownloadArea = viewModel::downloadArea,
                                    onSelectOfflineMapArea = viewModel::selectOfflineMapArea,
                                    onDeleteOfflineMapArea = viewModel::deleteOfflineMapArea,
                                    onOpenOfflineMaps = {
                                        navController.navigate(OfflineMapsRoute) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenRiderHud = {
                                        navController.navigate(RiderHudRoute) {
                                            launchSingleTop = true
                                        }
                                    },
                                    scenicPreferences = scenicPreferences,
                                    scenicRouteState = scenicRouteState,
                                    onUpdateScenicPreferences = viewModel::updateScenicPreferences,
                                    onSuggestScenicRoutes = viewModel::suggestAiScenicRoutes,
                                    onCancelScenicRoutes = viewModel::cancelScenicRouteSuggestion,
                                    proactiveWeatherPushEnabled = settings.proactiveWeatherPushEnabled,
                                    isScanningRouteWeather = isScanningRouteWeather,
                                    proactiveWeatherAlert = proactiveWeatherAlert,
                                    onToggleProactiveWeatherPush = viewModel::setProactiveWeatherPushEnabled,
                                    onScanRouteWeatherNow = { viewModel.scanPlannedRouteWeatherNow(forcePushAlert = true) },
                                    onApplyWeatherSuggestedStop = viewModel::applyWeatherSuggestedStop,
                                    onApplyWeatherSuggestedDetour = viewModel::applyWeatherSuggestedDetour,
                                    onDismissProactiveWeatherAlert = viewModel::dismissProactiveWeatherAlert
                                )
                            }

                            composable<OfflineMapsRoute> {
                                OfflineMapsScreen(
                                    offlineMapAreas = offlineMapAreas,
                                    activeOfflineArea = activeOfflineArea,
                                    plannedRoute = plannedRoute,
                                    myGpsFix = myGpsFix,
                                    mapDownloadProgress = mapDownloadProgress,
                                    isEffectiveOffline = isEffectiveOffline,
                                    onSearchPlaces = viewModel::searchOfflineRegions,
                                    onDownloadArea = viewModel::downloadArea,
                                    onSelectOfflineArea = viewModel::selectOfflineMapArea,
                                    onDeleteOfflineArea = viewModel::deleteOfflineMapArea,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<EventsRoute> {
                                EventsAndPassportScreen(
                                    currentUserId = viewModel.currentUserId,
                                    myGpsFix = myGpsFix,
                                    passportStamps = passportStamps,
                                    personalEvents = personalEvents,
                                    packEvents = packEvents,
                                    onStampLocation = viewModel::stampPassportStop,
                                    onCreateEvent = viewModel::createBikerEvent,
                                    onToggleRsvp = viewModel::toggleEventRsvp,
                                    onRouteToDestination = { name, label, coords ->
                                        viewModel.planRoute(name, label, coords)
                                        navigateTopLevel(RoutePlannerRoute)
                                    }
                                )
                            }

                            composable<GarageRoute> {
                                GarageScreen(
                                    bikes = garageBikes,
                                    reminders = maintenanceItems,
                                    serviceLogs = serviceLogs,
                                    defaultBikeName = userProfile?.bikeModel ?: "Tracer 9",
                                    onAddBike = viewModel::addGarageBike,
                                    onUpdateOdometer = viewModel::updateBikeOdometer,
                                    onAddReminder = viewModel::addMaintenanceReminder,
                                    onLogServiceDone = viewModel::completeServiceAndLogHistory,
                                    onDeleteReminder = viewModel::deleteMaintenanceReminder,
                                    onDeleteServiceLog = viewModel::deleteServiceLog
                                )
                            }

                            composable<RiderHudRoute> {
                                RiderHudScreen(
                                    currentUserId = viewModel.currentUserId,
                                    activePack = activePack,
                                    riders = riders,
                                    myGpsFix = myGpsFix,
                                    batteryPercent = batteryState.percent,
                                    ecoRideMode = settings.ecoRideMode,
                                    hudThemeMode = settings.hudThemeMode,
                                    isEffectiveOffline = isEffectiveOffline,
                                    isPlanningRoute = isPlanningRoute,
                                    hudNav = hudNavigationState,
                                    smartHudState = smartHudState,
                                    hudVoiceEnabled = hudVoiceEnabled,
                                    isSosActive = isSosActive,
                                    sosLastBroadcastMs = sosLastBroadcastMs,
                                    pttRecordingState = pttRecordingState,
                                    onStartPttRecording = { viewModel.startPttRecording("") },
                                    onStopAndSendPttRecording = { viewModel.stopAndSendPttRecording("") },
                                    onCancelPttRecording = viewModel::cancelPttRecording,
                                    onToggleVoice = viewModel::toggleHudVoice,
                                    onToggleEcoMode = viewModel::setEcoRideMode,
                                    onSetHudThemeMode = viewModel::setHudThemeMode,
                                    onStepDelta = viewModel::advanceHudStep,
                                    onResetAutoStep = viewModel::resetHudStepAuto,
                                    onQuickPlanPreset = viewModel::planRoute,
                                    onRouteToPackLeader = viewModel::navigateToPackLeader,
                                    onOpenRoutePlanner = {
                                        navController.popBackStack()
                                        navigateTopLevel(RoutePlannerRoute)
                                    },
                                    onRunSmartScan = viewModel::refreshSmartHudCallout,
                                    onTriggerSos = viewModel::triggerEmergencySos,
                                    onStandDownSos = viewModel::standDownEmergencySos,
                                    onExitHud = { navController.popBackStack() }
                                )
                            }

                            composable<PackRoute> {
                                PackHubScreen(
                                    currentUserId = viewModel.currentUserId,
                                    userProfile = userProfile,
                                    userPacks = userPacks,
                                    activePack = activePack,
                                    riders = riders,
                                    messages = messages,
                                    packPhotos = packPhotos,
                                    directMessageTarget = directMessageTarget,
                                    settings = settings,
                                    isEffectiveOffline = isEffectiveOffline,
                                    pendingBreadcrumbsCount = pendingBreadcrumbCount,
                                    pttRecordingState = pttRecordingState,
                                    pttPlaybackState = pttPlaybackState,
                                    pendingVoiceCount = pendingVoiceCount,
                                    p2pMeshState = p2pMeshState,
                                    meshVpnStatus = meshVpnStatus,
                                    onCreatePack = viewModel::createPack,
                                    onJoinPack = viewModel::joinPack,
                                    onSwitchPack = viewModel::switchPack,
                                    onSelectMessageTarget = viewModel::setDirectMessageTarget,
                                    onSendMessage = viewModel::sendMessage,
                                    onStartPttRecording = viewModel::startPttRecording,
                                    onStopAndSendPttRecording = viewModel::stopAndSendPttRecording,
                                    onCancelPttRecording = viewModel::cancelPttRecording,
                                    onTogglePlayVoice = viewModel::togglePlayVoiceMessage,
                                    onSetPttAutoPlay = viewModel::setPttAutoPlay,
                                    onRingRider = viewModel::ringRider,
                                    onUpdateProfile = viewModel::updateRiderProfile,
                                    onSetHudMode = viewModel::setHudThemeMode,
                                    onSetEcoMode = viewModel::setEcoRideMode,
                                    onSetManualOffline = viewModel::setManualOfflineMode,
                                    onSetP2pMesh = { enabled ->
                                        if (enabled && !viewModel.hasRequiredP2pPermissions()) {
                                            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                Manifest.permission.NEARBY_WIFI_DEVICES
                                            } else {
                                                Manifest.permission.ACCESS_FINE_LOCATION
                                            }
                                            p2pPermissionLauncher.launch(perm)
                                        } else {
                                            viewModel.setP2pMeshEnabled(enabled)
                                        }
                                    },
                                    onSetMeshVpn = { enabled ->
                                        if (enabled) {
                                            val consentIntent = viewModel.prepareMeshVpnConsentIntent()
                                            if (consentIntent != null) {
                                                vpnConsentLauncher.launch(consentIntent)
                                            } else {
                                                viewModel.setMeshVpnEnabled(true)
                                            }
                                        } else {
                                            viewModel.setMeshVpnEnabled(false)
                                        }
                                    },
                                    onConnectWifiDirectPeer = viewModel::connectWifiDirectPeer,
                                    onAddManualMeshPeer = { ip -> viewModel.addManualMeshPeer(ip) },
                                    onSetSeparationKm = viewModel::setSeparationAlertKm,
                                    onUploadGalleryPhoto = viewModel::postPackGalleryPhoto,
                                    onDeleteGalleryPhoto = viewModel::deletePackGalleryPhoto,
                                    onOpenChallengesAndReports = {
                                        navController.navigate(ChallengesRoute) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onSignOut = onSignOut
                                )
                            }

                            composable<ChallengesRoute> {
                                ChallengesScreen(
                                    activePack = activePack,
                                    userProfile = userProfile,
                                    riders = riders,
                                    challenges = challenges,
                                    userTrips = userTrips,
                                    onCreateChallenge = viewModel::createChallenge,
                                    onIncrementChallenge = viewModel::incrementChallengeProgress,
                                    onSelectTripForSummary = viewModel::selectTripForSummary
                                )
                            }
                        }
                    }
                }
            }

            activeTripSummary?.let { trip ->
                TripSummaryDashboardSheet(
                    trip = trip,
                    elevationSamples = activeTripElevationSamples,
                    riderName = userProfile?.displayName ?: "MotoPack Rider",
                    bikeModel = garageBikes.firstOrNull { it.isPrimary }?.makeModel
                        ?: userProfile?.bikeModel
                        ?: "Motorcycle",
                    isBatteryEfficiencyEnabled = settings.ecoRideMode,
                    onToggleBatteryEfficiency = viewModel::setEcoRideMode,
                    onSavePdf = { tripToExport, _ ->
                        savePdfLauncher.launch(TripReportExporter.buildSuggestedPdfFileName(tripToExport))
                    },
                    onSharePdf = { tripToExport, samples ->
                        val resolvedRiderName = userProfile?.displayName ?: "MotoPack Rider"
                        val resolvedBikeModel = garageBikes.firstOrNull { it.isPrimary }?.makeModel
                            ?: userProfile?.bikeModel
                            ?: "Motorcycle"
                        uiScope.launch {
                            TripReportExporter.createShareablePdfFile(
                                context = context,
                                trip = tripToExport,
                                elevationSamples = samples,
                                riderName = resolvedRiderName,
                                bikeModel = resolvedBikeModel
                            ).onSuccess { file ->
                                TripReportExporter.launchShareTripReportChooser(
                                    context = context,
                                    pdfFile = file,
                                    trip = tripToExport,
                                    riderName = resolvedRiderName,
                                    bikeModel = resolvedBikeModel
                                )
                            }.onFailure {
                                snackbarHostState.showSnackbar("Failed to generate shareable PDF")
                            }
                        }
                    },
                    onDismiss = viewModel::dismissTripSummary
                )
            }
        }
    }
}
