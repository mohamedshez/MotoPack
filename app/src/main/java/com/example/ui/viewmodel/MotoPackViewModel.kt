package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiBriefingUiState
import com.example.ai.AiCopilotUiState
import com.example.ai.MotoAiCopilotService
import com.example.ai.ScenicRoutePreferences
import com.example.ai.ScenicRouteSuggesterService
import com.example.ai.ScenicRouteUiState
import com.example.ai.SmartHudUiState
import com.example.audio.PttPlaybackUiState
import com.example.audio.PttRecordingUiState
import com.example.audio.PttVoiceEngine
import com.example.data.crypto.PackCrypto
import com.example.data.crypto.RiderTelemetryPayload
import com.example.data.local.CachedPackPinEntity
import com.example.data.local.CachedRiderEntity
import com.example.data.local.CachedTripElevationEntity
import com.example.data.local.MapAreaDownloadProgress
import com.example.data.local.MotoPackPreferences
import com.example.data.local.OfflineDao
import com.example.data.local.OfflineMapAreaEntity
import com.example.data.local.OfflineMapAreaManager
import com.example.data.local.OfflineRouteEntity
import com.example.data.local.QueuedBreadcrumbEntity
import com.example.data.local.QueuedVoiceMessageEntity
import com.example.data.local.RiderSettings
import com.example.data.model.BikerEvent
import com.example.data.model.BikerLocation
import com.example.data.model.DecryptedPackMessage
import com.example.data.model.DecryptedRiderState
import com.example.data.model.GarageBike
import com.example.data.model.MaintenanceReminderItem
import com.example.data.model.MaintenanceTaskType
import com.example.data.model.MapChallenge
import com.example.data.model.PackGalleryPhoto
import com.example.data.model.PackMapPin
import com.example.data.model.PassportStamp
import com.example.data.model.RidePack
import com.example.data.model.RideTrip
import com.example.data.model.ServiceHistoryLog
import com.example.data.model.UrgentPing
import com.example.data.model.UserProfile
import com.example.data.remote.ComputedMotoRoute
import com.example.data.remote.ElevationSample
import com.example.data.remote.FuelStationStop
import com.example.data.remote.MotoRouteAndWeatherRepository
import com.example.data.remote.OpenWeatherRepository
import com.example.data.remote.RiderLiveWeatherAlert
import com.example.data.remote.RouteNavigationStep
import com.example.data.remote.RouteWeatherWarning
import com.example.data.repository.BikerEventsRepository
import com.example.data.repository.GarageRepository
import com.example.data.repository.MotoPackRepository
import com.example.location.DeviceBatteryState
import com.example.location.DeviceTelemetryMonitor
import com.example.location.LiveGpsFix
import com.example.mesh.MeshIncomingEvent
import com.example.mesh.MeshMessageBody
import com.example.mesh.MeshTelemetryBody
import com.example.mesh.MeshVpnStatus
import com.example.mesh.MotoPackMeshVpnService
import com.example.mesh.P2pMeshUiState
import com.example.mesh.PackP2pMeshManager
import com.example.ui.screens.buildEstimatedTripElevationSamples
import com.example.ui.theme.HudThemeMode
import com.example.weather.ProactiveRouteWeatherAlert
import com.example.weather.ProactiveRouteWeatherEngine
import com.example.weather.RouteWeatherActionBus
import com.example.weather.RouteWeatherMonitorSnapshot
import com.example.weather.RouteWeatherNotificationAction
import com.example.weather.WeatherAdvisorySeverity
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.maps.android.PolyUtil
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

sealed interface AlertEvent {
    data class UrgentNotification(val title: String, val body: String, val id: Int) : AlertEvent
    data class RouteWeatherPush(val alert: ProactiveRouteWeatherAlert) : AlertEvent
    data class StatusBanner(val message: String, val isError: Boolean = false) : AlertEvent
}

data class IncomingSosAlert(
    val pingId: String,
    val senderId: String,
    val senderName: String,
    val details: String,
    val lat: Double?,
    val lng: Double?,
    val distanceKm: Double?,
    val timestampMs: Long
)

data class HudSmartAdvisory(
    val severity: String, // "CRITICAL", "CAUTION", "INFO"
    val title: String,
    val detail: String,
    val tag: String
)

data class HudNavigationUiState(
    val hasActiveRoute: Boolean = false,
    val routeName: String = "",
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val currentInstruction: String = "No active route selected",
    val currentManeuver: String = "STRAIGHT",
    val distanceToNextTurnMeters: Int = 0,
    val stepProgressFraction: Float = 0f,
    val thenInstruction: String? = null,
    val thenManeuver: String? = null,
    val thenDistanceMeters: Int? = null,
    val remainingRouteKm: Double = 0.0,
    val remainingEtaMin: Int = 0,
    val nextFuelStopLabel: String? = null,
    val smartAdvisories: List<HudSmartAdvisory> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class MotoPackViewModel(
    private val repository: MotoPackRepository,
    private val offlineDao: OfflineDao,
    private val routeRepository: MotoRouteAndWeatherRepository,
    private val preferences: MotoPackPreferences,
    private val telemetryMonitor: DeviceTelemetryMonitor,
    val currentUserId: String,
    initialDisplayName: String,
    initialPhotoUrl: String,
    private val openWeatherRepository: OpenWeatherRepository = OpenWeatherRepository(),
    private val aiCopilotService: MotoAiCopilotService = MotoAiCopilotService(),
    private val mapAreaManager: OfflineMapAreaManager? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val pttVoiceEngine = PttVoiceEngine(appContext)
    private val p2pMeshManager = PackP2pMeshManager(appContext)

    val pttRecordingState: StateFlow<PttRecordingUiState> = pttVoiceEngine.recordingState
    val pttPlaybackState: StateFlow<PttPlaybackUiState> = pttVoiceEngine.playbackState
    val p2pMeshState: StateFlow<P2pMeshUiState> = p2pMeshManager.uiState
    val meshVpnStatus: StateFlow<MeshVpnStatus> = MotoPackMeshVpnService.status

    private val _alertEvents = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 16)
    val alertEvents: SharedFlow<AlertEvent> = _alertEvents.asSharedFlow()

    val settings: StateFlow<RiderSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), RiderSettings())

    val batteryState: StateFlow<DeviceBatteryState> = telemetryMonitor.observeBatteryState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DeviceBatteryState())

    val networkConnected: StateFlow<Boolean> = telemetryMonitor.observeNetworkConnected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val isEffectiveOffline: StateFlow<Boolean> = combine(networkConnected, settings) { online, s ->
        !online || s.manualOfflineMode
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val pendingBreadcrumbCount: StateFlow<Int> = offlineDao.observePendingBreadcrumbCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val pendingVoiceCount: StateFlow<Int> = offlineDao.observePendingVoiceCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val _hasLocationPermission = MutableStateFlow(false)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _isRideTrackingActive = MutableStateFlow(false)
    val isRideTrackingActive: StateFlow<Boolean> = _isRideTrackingActive.asStateFlow()

    private val _hasRealGpsFix = MutableStateFlow(false)
    val hasRealGpsFix: StateFlow<Boolean> = _hasRealGpsFix.asStateFlow()

    private val _myGpsFix = MutableStateFlow(
        LiveGpsFix(
            lat = 53.3811,
            lng = -1.4701,
            speedKmh = 0.0,
            headingDeg = 0f,
            altitudeM = 120.0,
            timestampMs = System.currentTimeMillis()
        )
    )
    val myGpsFix: StateFlow<LiveGpsFix> = _myGpsFix.asStateFlow()

    private val _sessionRideDistanceKm = MutableStateFlow(0.0)
    val sessionRideDistanceKm: StateFlow<Double> = _sessionRideDistanceKm.asStateFlow()

    private val _sessionElevationGainM = MutableStateFlow(0.0)
    val sessionElevationGainM: StateFlow<Double> = _sessionElevationGainM.asStateFlow()

    private val _sessionElevationSamples = MutableStateFlow<List<ElevationSample>>(emptyList())
    private val _activeTripElevationSamples = MutableStateFlow<List<ElevationSample>>(emptyList())
    val activeTripElevationSamples: StateFlow<List<ElevationSample>> = _activeTripElevationSamples.asStateFlow()

    private val _meshRidersMap = MutableStateFlow<Map<String, DecryptedRiderState>>(emptyMap())
    private val _meshMessages = MutableStateFlow<List<DecryptedPackMessage>>(emptyList())

    private val _rideStartTimeMs = MutableStateFlow<Long?>(null)

    private val _activeTripSummary = MutableStateFlow<RideTrip?>(null)
    val activeTripSummary: StateFlow<RideTrip?> = _activeTripSummary.asStateFlow()

    private var rideStartAltitudeM: Double? = null
    private var lastSampledElevationDistKm: Double = -1.0
    private var rideStartBatteryPercent: Int = 100
    private val sessionWeatherEncounters = LinkedHashSet<String>()
    private var sessionMaxWindGustKmh: Double = 0.0
    private var sessionWorstWeatherSeverity: String = "CLEAR"

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { err ->
            logStreamError("observeUserProfile", err)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val userTrips: StateFlow<List<RideTrip>> = repository.observeUserTrips(currentUserId)
        .catch { err ->
            logStreamError("observeUserTrips", err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userPacks: StateFlow<List<RidePack>> = repository.observeUserPacks(currentUserId)
        .catch { err ->
            logStreamError("observeUserPacks", err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activePack: StateFlow<RidePack?> = combine(userPacks, userProfile) { packs, profile ->
        val preferredId = profile?.activePackId.orEmpty()
        packs.firstOrNull { it.id == preferredId } ?: packs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val features = RiderGarageEventsFeatureDelegate(
        scope = viewModelScope,
        currentUserId = currentUserId,
        eventsRepository = BikerEventsRepository(repository.firestoreDb, repository.firebaseAuth),
        garageRepository = GarageRepository(repository.firestoreDb, repository.firebaseAuth),
        scenicService = ScenicRouteSuggesterService(),
        weatherRepository = openWeatherRepository,
        activePackFlow = activePack,
        emitBanner = { msg, err -> _alertEvents.tryEmit(AlertEvent.StatusBanner(msg, err)) }
    )

    val passportStamps: StateFlow<List<PassportStamp>> = features.passportStamps
    val personalEvents: StateFlow<List<BikerEvent>> = features.personalEvents
    val packEvents: StateFlow<List<BikerEvent>> = features.packEvents
    val garageBikes: StateFlow<List<GarageBike>> = features.garageBikes
    val maintenanceItems: StateFlow<List<MaintenanceReminderItem>> = features.maintenanceItems
    val serviceLogs: StateFlow<List<ServiceHistoryLog>> = features.serviceLogs
    val scenicPreferences: StateFlow<ScenicRoutePreferences> = features.scenicPreferences
    val scenicRouteState: StateFlow<ScenicRouteUiState> = features.scenicRouteState

    val savedOfflineRoutes: StateFlow<List<OfflineRouteEntity>> = activePack.flatMapLatest { pack ->
        if (pack == null) flowOf(emptyList()) else offlineDao.observeRoutesForPack(pack.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val rawPackMembers = activePack.flatMapLatest { pack ->
        if (pack == null) {
            flowOf(emptyList())
        } else {
            repository.observePackMembers(pack.id, currentUserId).catch { err ->
                logStreamError("observePackMembers", err)
                emit(emptyList())
            }
        }
    }

    private val cachedPackRiders = activePack.flatMapLatest { pack ->
        if (pack == null) flowOf(emptyList()) else offlineDao.observeCachedRiders(pack.id)
    }

    private val cloudAndCachedRiders = combine(
        activePack,
        rawPackMembers,
        cachedPackRiders,
        myGpsFix,
        isEffectiveOffline
    ) { pack, liveDocs, cachedDocs, myFix, offline ->
        if (pack == null) return@combine emptyList()
        val key = PackCrypto.derivePackKey(pack.inviteCode, pack.encryptionSalt)

        val liveDecrypted = liveDocs.mapNotNull { doc ->
            val payload = PackCrypto.decryptTelemetry(doc.encryptedTelemetry, key)
                ?: return@mapNotNull null
            val distKm = MotoRouteAndWeatherRepository.haversineKm(
                myFix.lat,
                myFix.lng,
                payload.lat,
                payload.lng
            )
            DecryptedRiderState(
                userId = doc.userId,
                displayName = doc.displayName,
                photoUrl = doc.photoUrl,
                bikeModel = doc.bikeModel,
                phoneNumber = doc.phoneNumber,
                lat = payload.lat,
                lng = payload.lng,
                speedKmh = payload.speedKmh,
                headingDeg = payload.headingDeg,
                altitudeM = payload.altitudeM,
                batteryPercent = doc.batteryPercent.toInt(),
                isCharging = doc.isCharging,
                totalDistanceKm = doc.totalDistanceKm,
                elevationClimbedM = doc.elevationClimbedM,
                isOnline = doc.isOnline && !offline,
                isOfflineQueued = payload.isOfflineQueued,
                lastUpdatedMs = doc.updatedAt?.toDate()?.time ?: payload.timestampMs,
                distanceFromMeKm = distKm
            )
        }

        if (liveDecrypted.isNotEmpty() && !offline) {
            viewModelScope.launch {
                offlineDao.upsertCachedRiders(
                    liveDecrypted.map {
                        CachedRiderEntity(
                            userId = it.userId,
                            packId = pack.id,
                            displayName = it.displayName,
                            photoUrl = it.photoUrl,
                            bikeModel = it.bikeModel,
                            phoneNumber = it.phoneNumber,
                            lat = it.lat,
                            lng = it.lng,
                            speedKmh = it.speedKmh,
                            altitudeM = it.altitudeM,
                            batteryPercent = it.batteryPercent,
                            isCharging = it.isCharging,
                            totalDistanceKm = it.totalDistanceKm,
                            elevationClimbedM = it.elevationClimbedM,
                            lastSeenMs = it.lastUpdatedMs
                        )
                    }
                )
            }
            liveDecrypted
        } else {
            cachedDocs.map { c ->
                val distKm = MotoRouteAndWeatherRepository.haversineKm(myFix.lat, myFix.lng, c.lat, c.lng)
                DecryptedRiderState(
                    userId = c.userId,
                    displayName = c.displayName,
                    photoUrl = c.photoUrl,
                    bikeModel = c.bikeModel,
                    phoneNumber = c.phoneNumber,
                    lat = c.lat,
                    lng = c.lng,
                    speedKmh = c.speedKmh,
                    headingDeg = 0f,
                    altitudeM = c.altitudeM,
                    batteryPercent = c.batteryPercent,
                    isCharging = c.isCharging,
                    totalDistanceKm = c.totalDistanceKm,
                    elevationClimbedM = c.elevationClimbedM,
                    isOnline = false,
                    isOfflineQueued = true,
                    lastUpdatedMs = c.lastSeenMs,
                    distanceFromMeKm = distKm
                )
            }
        }
    }

    val riders: StateFlow<List<DecryptedRiderState>> = combine(
        cloudAndCachedRiders,
        _meshRidersMap,
        myGpsFix
    ) { baseRiders, meshPeers, myFix ->
        if (meshPeers.isEmpty()) return@combine baseRiders
        val merged = baseRiders.associateBy { it.userId }.toMutableMap()
        val now = System.currentTimeMillis()
        meshPeers.values.forEach { peer ->
            val existing = merged[peer.userId]
            val peerAgeMs = now - peer.lastUpdatedMs
            if (peerAgeMs <= 120_000L && (existing == null || !existing.isOnline || peer.lastUpdatedMs > existing.lastUpdatedMs)) {
                val distKm = MotoRouteAndWeatherRepository.haversineKm(myFix.lat, myFix.lng, peer.lat, peer.lng)
                merged[peer.userId] = peer.copy(distanceFromMeKm = distKm, isOnline = true)
            }
        }
        merged.values.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val cloudMessages = activePack.flatMapLatest { pack ->
        if (pack == null) {
            flowOf(emptyList())
        } else {
            val key = PackCrypto.derivePackKey(pack.inviteCode, pack.encryptionSalt)
            repository.observePackMessages(pack.id, currentUserId)
                .map { docs ->
                    docs.map { doc ->
                        val decryptedAudio = if (doc.messageType == "voice" && doc.encryptedAudio.isNotBlank()) {
                            PackCrypto.decryptString(doc.encryptedAudio, key).orEmpty()
                        } else {
                            ""
                        }
                        DecryptedPackMessage(
                            id = doc.id,
                            senderId = doc.senderId,
                            senderName = doc.senderName,
                            recipientId = doc.recipientId,
                            text = PackCrypto.decryptString(doc.encryptedBody, key) ?: "[Encrypted message]",
                            messageType = doc.messageType,
                            timestampMs = doc.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                            audioBase64 = decryptedAudio,
                            audioCodec = doc.audioCodec,
                            audioDurationMs = doc.audioDurationMs,
                            audioSizeBytes = doc.audioSizeBytes,
                            audioPeaks = if (doc.messageType == "voice") PttVoiceEngine.parsePeaksCsv(doc.audioPeaks) else emptyList()
                        )
                    }
                }
                .catch { err ->
                    logStreamError("observePackMessages", err)
                    emit(emptyList())
                }
        }
    }

    val messages: StateFlow<List<DecryptedPackMessage>> = combine(
        cloudMessages,
        _meshMessages
    ) { cloud, mesh ->
        if (mesh.isEmpty()) return@combine cloud
        val existingIds = cloud.mapTo(HashSet()) { it.id }
        (cloud + mesh.filter { it.id !in existingIds }).sortedBy { it.timestampMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val urgentPings: StateFlow<List<UrgentPing>> = activePack.flatMapLatest { pack ->
        if (pack == null) {
            flowOf(emptyList())
        } else {
            repository.observeUrgentPings(pack.id, currentUserId).catch { err ->
                logStreamError("observeUrgentPings", err)
                emit(emptyList())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val incomingSosAlerts: StateFlow<List<IncomingSosAlert>> = combine(
        urgentPings,
        activePack,
        myGpsFix
    ) { pings, pack, myFix ->
        if (pack == null) return@combine emptyList()
        val key = PackCrypto.derivePackKey(pack.inviteCode, pack.encryptionSalt)
        pings.filter {
            it.alertType == "EMERGENCY_SOS" &&
                !it.acknowledged &&
                it.senderId != currentUserId
        }.map { ping ->
            val decrypted = if (ping.encryptedNote.isNotBlank()) {
                PackCrypto.decryptString(ping.encryptedNote, key).orEmpty()
            } else {
                ""
            }
            val parsed = parseSosPayload(decrypted)
            val distKm = if (parsed.lat != null && parsed.lng != null) {
                MotoRouteAndWeatherRepository.haversineKm(myFix.lat, myFix.lng, parsed.lat, parsed.lng)
            } else {
                null
            }
            IncomingSosAlert(
                pingId = ping.id,
                senderId = ping.senderId,
                senderName = ping.senderName,
                details = parsed.details.ifBlank { "Emergency SOS triggered — check rider location" },
                lat = parsed.lat,
                lng = parsed.lng,
                distanceKm = distKm,
                timestampMs = ping.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val challenges: StateFlow<List<MapChallenge>> = activePack.flatMapLatest { pack ->
        if (pack == null) {
            flowOf(emptyList())
        } else {
            repository.observeChallenges(pack.id, currentUserId).catch { err ->
                logStreamError("observeChallenges", err)
                emit(emptyList())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val rawPackMarkers = activePack.flatMapLatest { pack ->
        if (pack == null) {
            flowOf(emptyList())
        } else {
            repository.observePackMarkers(pack.id, currentUserId).catch { err ->
                logStreamError("observePackMarkers", err)
                emit(emptyList())
            }
        }
    }

    private val cachedPackMarkers = activePack.flatMapLatest { pack ->
        if (pack == null) flowOf(emptyList()) else offlineDao.observeCachedPackPins(pack.id)
    }

    val packMarkers: StateFlow<List<PackMapPin>> = combine(
        activePack,
        rawPackMarkers,
        cachedPackMarkers,
        isEffectiveOffline
    ) { pack, livePins, cachedPins, offline ->
        if (pack == null) return@combine emptyList()
        if (!offline && livePins.isNotEmpty()) {
            viewModelScope.launch {
                offlineDao.upsertCachedPackPins(
                    livePins.map { p ->
                        CachedPackPinEntity(
                            pinId = p.id,
                            packId = pack.id,
                            creatorId = p.creatorId,
                            creatorName = p.creatorName,
                            pinType = p.pinType,
                            title = p.title,
                            description = p.description,
                            lat = p.lat,
                            lng = p.lng,
                            severity = p.severity,
                            confirmedCount = p.confirmedByUids.size,
                            status = p.status,
                            updatedAtMs = p.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    }
                )
            }
            livePins
        } else if (offline) {
            cachedPins.map { c ->
                PackMapPin(
                    id = c.pinId,
                    packId = c.packId,
                    creatorId = c.creatorId,
                    creatorName = c.creatorName,
                    pinType = c.pinType,
                    title = c.title,
                    description = c.description,
                    lat = c.lat,
                    lng = c.lng,
                    severity = c.severity,
                    confirmedByUids = emptyList(),
                    status = c.status,
                    memberUids = listOf(currentUserId)
                )
            }
        } else {
            livePins
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val packPhotos: StateFlow<List<PackGalleryPhoto>> = activePack.flatMapLatest { pack ->
        if (pack == null) {
            flowOf(emptyList())
        } else {
            repository.observePackGalleryPhotos(pack.id, currentUserId).catch { err ->
                logStreamError("observePackGalleryPhotos", err)
                emit(emptyList())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val offlineMapAreas: StateFlow<List<OfflineMapAreaEntity>> = offlineDao.observeOfflineMapAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _mapDownloadProgress = MutableStateFlow(MapAreaDownloadProgress())
    val mapDownloadProgress: StateFlow<MapAreaDownloadProgress> = _mapDownloadProgress.asStateFlow()

    private val _activeOfflineArea = MutableStateFlow<OfflineMapAreaEntity?>(null)
    val activeOfflineArea: StateFlow<OfflineMapAreaEntity?> = _activeOfflineArea.asStateFlow()

    fun buildOfflineTileProvider(areaIds: List<String>): TileProvider? {
        if (areaIds.isEmpty()) return null
        return mapAreaManager?.createOfflineTileProvider(areaIds)
    }

    private val _selectedRiderId = MutableStateFlow<String?>(null)
    val selectedRiderId: StateFlow<String?> = _selectedRiderId.asStateFlow()

    private val _selectedRiderEta = MutableStateFlow<Pair<String, com.example.data.remote.TrafficEtaResult>?>(null)

    val selectedRider: StateFlow<DecryptedRiderState?> = combine(
        riders,
        _selectedRiderId,
        _selectedRiderEta
    ) { list, id, etaPair ->
        val found = list.firstOrNull { it.userId == id } ?: return@combine null
        if (etaPair != null && etaPair.first == found.userId) {
            found.copy(
                trafficEtaMinutes = etaPair.second.etaMinutes,
                trafficConditionLabel = etaPair.second.trafficLabel
            )
        } else {
            found
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _plannedRoute = MutableStateFlow<ComputedMotoRoute?>(null)
    val plannedRoute: StateFlow<ComputedMotoRoute?> = _plannedRoute.asStateFlow()

    private val _isPlanningRoute = MutableStateFlow(false)
    val isPlanningRoute: StateFlow<Boolean> = _isPlanningRoute.asStateFlow()

    private val _directMessageTarget = MutableStateFlow<DecryptedRiderState?>(null)
    val directMessageTarget: StateFlow<DecryptedRiderState?> = _directMessageTarget.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _liveWeatherAlert = MutableStateFlow<RiderLiveWeatherAlert?>(null)
    val liveWeatherAlert: StateFlow<RiderLiveWeatherAlert?> = _liveWeatherAlert.asStateFlow()

    private val _isRefreshingWeather = MutableStateFlow(false)
    val isRefreshingWeather: StateFlow<Boolean> = _isRefreshingWeather.asStateFlow()

    private val _proactiveWeatherAlert = MutableStateFlow<ProactiveRouteWeatherAlert?>(null)
    val proactiveWeatherAlert: StateFlow<ProactiveRouteWeatherAlert?> = _proactiveWeatherAlert.asStateFlow()

    private val _isScanningRouteWeather = MutableStateFlow(false)
    val isScanningRouteWeather: StateFlow<Boolean> = _isScanningRouteWeather.asStateFlow()

    private val _lastRouteWeatherScanMs = MutableStateFlow<Long?>(null)
    val lastRouteWeatherScanMs: StateFlow<Long?> = _lastRouteWeatherScanMs.asStateFlow()

    private val _aiBriefingState = MutableStateFlow<AiBriefingUiState>(AiBriefingUiState.Idle)
    val aiBriefingState: StateFlow<AiBriefingUiState> = _aiBriefingState.asStateFlow()

    private val _aiCopilotState = MutableStateFlow<AiCopilotUiState>(AiCopilotUiState.Idle)
    val aiCopilotState: StateFlow<AiCopilotUiState> = _aiCopilotState.asStateFlow()

    private val _smartHudState = MutableStateFlow<SmartHudUiState>(SmartHudUiState.Idle)
    val smartHudState: StateFlow<SmartHudUiState> = _smartHudState.asStateFlow()

    private val _hudVoiceEnabled = MutableStateFlow(true)
    val hudVoiceEnabled: StateFlow<Boolean> = _hudVoiceEnabled.asStateFlow()

    private val _hudStepOverrideIndex = MutableStateFlow<Int?>(null)

    private val hudRouteFlow = combine(_plannedRoute, myGpsFix, _hudStepOverrideIndex) { route, fix, overrideIdx ->
        Triple(route, fix, overrideIdx)
    }

    private data class HudSafetySnapshot(
        val riders: List<DecryptedRiderState>,
        val weather: RiderLiveWeatherAlert?,
        val routeWeatherAlert: ProactiveRouteWeatherAlert?,
        val pins: List<PackMapPin>,
        val battery: DeviceBatteryState,
        val settings: RiderSettings
    )

    private val hudSafetyFlow = combine(
        riders,
        combine(liveWeatherAlert, proactiveWeatherAlert) { wx, routeWx -> wx to routeWx },
        packMarkers,
        batteryState,
        settings
    ) { rList, (wx, routeWx), pins, batt, s ->
        HudSafetySnapshot(rList, wx, routeWx, pins, batt, s)
    }

    val hudNavigationState: StateFlow<HudNavigationUiState> = combine(
        hudRouteFlow,
        hudSafetyFlow
    ) { (route, fix, overrideIdx), safety ->
        buildHudNavigationUiState(route, fix, overrideIdx, safety)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), HudNavigationUiState())

    private val _isSosActive = MutableStateFlow(false)
    val isSosActive: StateFlow<Boolean> = _isSosActive.asStateFlow()

    private val _sosLastBroadcastMs = MutableStateFlow<Long?>(null)
    val sosLastBroadcastMs: StateFlow<Long?> = _sosLastBroadcastMs.asStateFlow()

    private var activeSosPinId: String? = null
    private var lastWeatherCoords: Pair<Double, Double>? = null
    private var weatherFetchedWithRealFix = false
    private var lastAlertedWeatherHeadline: String? = null

    private val notifiedPingIds = mutableSetOf<String>()
    private val alertedSeparationUids = mutableSetOf<String>()
    private val alertedLowBatteryUids = mutableSetOf<String>()
    private val seenVoiceMessageIds = mutableSetOf<String>()
    private var voiceFeedHydrated = false
    private var activePttRecipientId: String = ""

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(initialDisplayName, initialPhotoUrl)
        }
        observeLocationAndSync()
        observeSafetyAndPingAlerts()
        observeReconnectBreadcrumbFlush()
        observeLiveWeatherForRider()
        observeIncomingAutoPlayVoice()
        observeP2pMeshLifecycleAndPackets()
        observeDeadZoneAutoActivation()
        observeProactiveRouteWeather()
        observeWeatherNotificationActions()
    }

    fun onLocationPermissionResult(granted: Boolean) {
        _hasLocationPermission.value = granted
    }

    private fun observeLocationAndSync() {
        viewModelScope.launch {
            combine(
                _hasLocationPermission,
                settings.map { it.ecoRideMode }.distinctUntilChanged(),
                batteryState.map { it.percent <= 20 }.distinctUntilChanged(),
                _isSosActive
            ) { granted, ecoSetting, lowBatt, sosActive ->
                granted to (!sosActive && (ecoSetting || lowBatt))
            }.distinctUntilChanged().flatMapLatest { (granted, useEco) ->
                if (!granted) flowOf(null) else telemetryMonitor.observeLocationFixes(useEco)
            }.collect { fix ->
                if (fix == null) return@collect
                val prev = _myGpsFix.value
                if (_isRideTrackingActive.value) {
                    if (rideStartAltitudeM == null) {
                        rideStartAltitudeM = fix.altitudeM
                    }
                    val deltaKm = MotoRouteAndWeatherRepository.haversineKm(prev.lat, prev.lng, fix.lat, fix.lng)
                    if (deltaKm in 0.005..5.0) {
                        _sessionRideDistanceKm.value += deltaKm
                    }
                    val deltaElev = (fix.altitudeM - prev.altitudeM).coerceAtLeast(0.0)
                    if (deltaElev in 0.5..250.0) {
                        _sessionElevationGainM.value += deltaElev
                    }
                    val distNow = _sessionRideDistanceKm.value
                    if (lastSampledElevationDistKm < 0.0 ||
                        (distNow - lastSampledElevationDistKm) >= 0.05 ||
                        _sessionElevationSamples.value.size < 2
                    ) {
                        lastSampledElevationDistKm = distNow
                        _sessionElevationSamples.value = _sessionElevationSamples.value + ElevationSample(
                            distanceKm = distNow,
                            elevationM = fix.altitudeM,
                            lat = fix.lat,
                            lng = fix.lng
                        )
                    }
                }
                _myGpsFix.value = fix
                _hasRealGpsFix.value = true
                pushOrQueueTelemetry(fix)
            }
        }
    }

    private suspend fun pushOrQueueTelemetry(fix: LiveGpsFix) {
        val pack = activePack.value ?: return
        val profile = userProfile.value
        val batt = batteryState.value
        val offline = isEffectiveOffline.value

        if (settings.value.p2pMeshEnabled && p2pMeshState.value.isActive) {
            p2pMeshManager.broadcastTelemetry(
                MeshTelemetryBody(
                    userId = currentUserId,
                    displayName = profile?.displayName ?: "Rider",
                    photoUrl = profile?.photoUrl ?: "",
                    bikeModel = profile?.bikeModel ?: "Adventure Tourer",
                    phoneNumber = profile?.phoneNumber ?: "",
                    lat = fix.lat,
                    lng = fix.lng,
                    speedKmh = fix.speedKmh,
                    headingDeg = fix.headingDeg,
                    altitudeM = fix.altitudeM,
                    batteryPercent = batt.percent,
                    isCharging = batt.isCharging,
                    totalDistanceKm = (profile?.totalDistanceKm ?: 0.0) + _sessionRideDistanceKm.value,
                    elevationClimbedM = (profile?.totalElevationM ?: 0.0) + _sessionElevationGainM.value,
                    meshVirtualIp = meshVpnStatus.value.virtualIp.ifBlank {
                        MotoPackMeshVpnService.computeVirtualMeshIp(pack.id, currentUserId)
                    },
                    timestampMs = fix.timestampMs
                )
            )
        }

        if (offline) {
            offlineDao.enqueueBreadcrumb(
                QueuedBreadcrumbEntity(
                    packId = pack.id,
                    userId = currentUserId,
                    lat = fix.lat,
                    lng = fix.lng,
                    speedKmh = fix.speedKmh,
                    headingDeg = fix.headingDeg,
                    altitudeM = fix.altitudeM,
                    batteryPercent = batt.percent,
                    isCharging = batt.isCharging,
                    recordedAtMs = fix.timestampMs
                )
            )
        } else {
            repository.publishTelemetry(
                pack = pack,
                displayName = profile?.displayName ?: "Rider",
                photoUrl = profile?.photoUrl ?: "",
                bikeModel = profile?.bikeModel ?: "Adventure Tourer",
                phoneNumber = profile?.phoneNumber ?: "",
                payload = RiderTelemetryPayload(
                    lat = fix.lat,
                    lng = fix.lng,
                    speedKmh = fix.speedKmh,
                    headingDeg = fix.headingDeg,
                    altitudeM = fix.altitudeM,
                    isOfflineQueued = false,
                    timestampMs = fix.timestampMs
                ),
                batteryPercent = batt.percent,
                isCharging = batt.isCharging,
                totalDistanceKm = (profile?.totalDistanceKm ?: 0.0) + _sessionRideDistanceKm.value,
                elevationClimbedM = (profile?.totalElevationM ?: 0.0) + _sessionElevationGainM.value,
                isOnline = true
            )
        }
    }

    private fun observeReconnectBreadcrumbFlush() {
        viewModelScope.launch {
            isEffectiveOffline.collect { offline ->
                if (!offline) {
                    val pending = offlineDao.getPendingBreadcrumbs()
                    val pack = activePack.value
                    val profile = userProfile.value
                    if (pending.isNotEmpty() && pack != null) {
                        val latest = pending.last()
                        val res = repository.publishTelemetry(
                            pack = pack,
                            displayName = profile?.displayName ?: "Rider",
                            photoUrl = profile?.photoUrl ?: "",
                            bikeModel = profile?.bikeModel ?: "Adventure Tourer",
                            phoneNumber = profile?.phoneNumber ?: "",
                            payload = RiderTelemetryPayload(
                                lat = latest.lat,
                                lng = latest.lng,
                                speedKmh = latest.speedKmh,
                                headingDeg = latest.headingDeg,
                                altitudeM = latest.altitudeM,
                                isOfflineQueued = true,
                                timestampMs = latest.recordedAtMs
                            ),
                            batteryPercent = latest.batteryPercent,
                            isCharging = latest.isCharging,
                            totalDistanceKm = (profile?.totalDistanceKm ?: 0.0) + _sessionRideDistanceKm.value,
                            elevationClimbedM = (profile?.totalElevationM ?: 0.0) + _sessionElevationGainM.value,
                            isOnline = true
                        )
                        if (res.isSuccess) {
                            offlineDao.deleteBreadcrumbsByIds(pending.map { it.id })
                            _alertEvents.tryEmit(
                                AlertEvent.StatusBanner("Synced ${pending.size} queued offline GPS points")
                            )
                        }
                    }

                    val pendingVoices = offlineDao.getPendingVoiceMessages()
                    if (pendingVoices.isNotEmpty() && pack != null) {
                        val syncedIds = mutableListOf<Long>()
                        pendingVoices.filter { it.packId == pack.id }.forEach { voice ->
                            val sendRes = repository.sendPackVoiceMessage(
                                pack = pack,
                                senderName = voice.senderName,
                                audioBase64 = voice.audioBase64,
                                audioCodec = voice.audioCodec,
                                audioDurationMs = voice.audioDurationMs,
                                audioSizeBytes = voice.audioSizeBytes,
                                audioPeaks = voice.audioPeaks,
                                recipientId = voice.recipientId
                            )
                            if (sendRes.isSuccess) {
                                syncedIds.add(voice.id)
                            }
                        }
                        if (syncedIds.isNotEmpty()) {
                            offlineDao.deleteVoiceMessagesByIds(syncedIds)
                            _alertEvents.tryEmit(
                                AlertEvent.StatusBanner("Sent ${syncedIds.size} queued Push-to-Talk voice clip(s)")
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeIncomingAutoPlayVoice() {
        viewModelScope.launch {
            messages.collect { list ->
                val voiceMsgs = list.filter { it.messageType == "voice" && it.audioBase64.isNotBlank() }
                if (!voiceFeedHydrated) {
                    voiceMsgs.forEach { seenVoiceMessageIds.add(it.id) }
                    if (list.isNotEmpty()) {
                        voiceFeedHydrated = true
                    }
                    return@collect
                }
                val now = System.currentTimeMillis()
                val newestFresh = voiceMsgs.lastOrNull { msg ->
                    seenVoiceMessageIds.add(msg.id) &&
                        msg.senderId != currentUserId &&
                        (now - msg.timestampMs) <= 25_000L &&
                        (msg.recipientId.isBlank() || msg.recipientId == currentUserId)
                }
                if (newestFresh != null &&
                    settings.value.pttAutoPlay &&
                    !pttVoiceEngine.recordingState.value.isRecording &&
                    pttVoiceEngine.playbackState.value.playingMessageId == null
                ) {
                    pttVoiceEngine.togglePlayback(
                        messageId = newestFresh.id,
                        audioBase64 = newestFresh.audioBase64,
                        codec = newestFresh.audioCodec,
                        fallbackDurationMs = newestFresh.audioDurationMs
                    )
                }
            }
        }
    }

    private fun observeSafetyAndPingAlerts() {
        viewModelScope.launch {
            urgentPings.collect { pings ->
                pings.filter {
                    !it.acknowledged &&
                        it.senderId != currentUserId &&
                        (it.targetUserId == currentUserId || it.targetUserId == "ALL") &&
                        notifiedPingIds.add(it.id)
                }.forEach { ping ->
                    val title = when (ping.alertType) {
                        "EMERGENCY_SOS" -> "EMERGENCY SOS from ${ping.senderName}"
                        "RING_URGENT" -> "Urgent Ring from ${ping.senderName}"
                        "WEATHER_HAZARD" -> "Weather Warning from ${ping.senderName}"
                        "SEPARATION" -> "Convoy Separation Alert"
                        else -> "Pack Alert from ${ping.senderName}"
                    }
                    val pack = activePack.value
                    val decryptedNote = if (pack != null && ping.encryptedNote.isNotBlank()) {
                        val key = PackCrypto.derivePackKey(pack.inviteCode, pack.encryptionSalt)
                        PackCrypto.decryptString(ping.encryptedNote, key)
                    } else {
                        null
                    }
                    val body = when {
                        decryptedNote == null -> "Check MotoPack live radar immediately."
                        ping.alertType == "EMERGENCY_SOS" -> parseSosPayload(decryptedNote).details
                            .ifBlank { "Emergency SOS triggered — check radar for live location." }
                        else -> decryptedNote
                    }
                    _alertEvents.tryEmit(AlertEvent.UrgentNotification(title, body, ping.id.hashCode()))
                }
            }
        }

        viewModelScope.launch {
            combine(riders, settings) { list, s -> list to s.separationAlertKm }.collect { (list, thresholdKm) ->
                list.filter { it.userId != currentUserId }.forEach { rider ->
                    if (rider.distanceFromMeKm > thresholdKm && alertedSeparationUids.add(rider.userId)) {
                        _alertEvents.tryEmit(
                            AlertEvent.UrgentNotification(
                                title = "Rider Separated: ${rider.displayName}",
                                body = String.format("%.1f km away (threshold %.1f km)", rider.distanceFromMeKm, thresholdKm),
                                id = ("sep_" + rider.userId).hashCode()
                            )
                        )
                    } else if (rider.distanceFromMeKm <= thresholdKm * 0.7) {
                        alertedSeparationUids.remove(rider.userId)
                    }

                    if (rider.batteryPercent <= 15 && !rider.isCharging && alertedLowBatteryUids.add(rider.userId)) {
                        _alertEvents.tryEmit(
                            AlertEvent.UrgentNotification(
                                title = "Low Battery: ${rider.displayName}",
                                body = "${rider.batteryPercent}% remaining — check rider status",
                                id = ("batt_" + rider.userId).hashCode()
                            )
                        )
                    } else if (rider.batteryPercent > 25) {
                        alertedLowBatteryUids.remove(rider.userId)
                    }
                }
            }
        }
    }

    fun selectRider(rider: DecryptedRiderState?) {
        _selectedRiderId.value = rider?.userId
        if (rider != null) {
            val my = _myGpsFix.value
            viewModelScope.launch {
                val eta = routeRepository.computeTrafficEta(
                    LatLng(my.lat, my.lng),
                    LatLng(rider.lat, rider.lng)
                )
                _selectedRiderEta.value = rider.userId to eta
            }
        }
    }

    fun setDirectMessageTarget(rider: DecryptedRiderState?) {
        _directMessageTarget.value = rider
    }

    fun createPack(packName: String, bikeModel: String, phoneNumber: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val profile = userProfile.value
            val fix = _myGpsFix.value
            val batt = batteryState.value
            if (bikeModel.isNotBlank() || phoneNumber.isNotBlank()) {
                repository.updateUserProfile(profile?.displayName ?: "Rider", bikeModel, phoneNumber)
            }
            val res = repository.createPack(
                packName = packName,
                displayName = profile?.displayName ?: "Rider",
                photoUrl = profile?.photoUrl ?: "",
                bikeModel = bikeModel.ifBlank { profile?.bikeModel ?: "Adventure Tourer" },
                phoneNumber = phoneNumber.ifBlank { profile?.phoneNumber ?: "" },
                initialLat = fix.lat,
                initialLng = fix.lng,
                batteryPercent = batt.percent,
                isCharging = batt.isCharging
            )
            _isBusy.value = false
            res.onSuccess {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Pack '${it.name}' created! Share code ${it.inviteCode}"))
            }.onFailure {
                _alertEvents.tryEmit(AlertEvent.StatusBanner(it.localizedMessage ?: "Failed to create pack", true))
            }
        }
    }

    fun joinPack(inviteCode: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val profile = userProfile.value
            val fix = _myGpsFix.value
            val batt = batteryState.value
            val res = repository.joinPackByCode(
                rawCode = inviteCode,
                displayName = profile?.displayName ?: "Rider",
                photoUrl = profile?.photoUrl ?: "",
                bikeModel = profile?.bikeModel ?: "Adventure Tourer",
                phoneNumber = profile?.phoneNumber ?: "",
                initialLat = fix.lat,
                initialLng = fix.lng,
                batteryPercent = batt.percent,
                isCharging = batt.isCharging
            )
            _isBusy.value = false
            res.onSuccess {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Joined ${it.name} (E2E Encrypted)"))
            }.onFailure {
                _alertEvents.tryEmit(AlertEvent.StatusBanner(it.localizedMessage ?: "Invalid invite code", true))
            }
        }
    }

    fun switchPack(packId: String) {
        viewModelScope.launch {
            repository.setActivePackId(packId)
        }
    }

    fun updateRiderProfile(displayName: String, bikeModel: String, phoneNumber: String) {
        viewModelScope.launch {
            val res = repository.updateUserProfile(displayName, bikeModel, phoneNumber)
            res.onSuccess {
                pushOrQueueTelemetry(_myGpsFix.value)
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Rider profile updated"))
            }.onFailure {
                _alertEvents.tryEmit(AlertEvent.StatusBanner(it.localizedMessage ?: "Update failed", true))
            }
        }
    }

    fun ringRider(targetRider: DecryptedRiderState) {
        val pack = activePack.value ?: return
        val myName = userProfile.value?.displayName ?: "Pack Rider"
        viewModelScope.launch {
            val res = repository.sendUrgentPing(
                pack = pack,
                senderName = myName,
                targetUserId = targetRider.userId,
                alertType = "RING_URGENT",
                note = "Urgent ring from $myName — check convoy radar!"
            )
            res.onSuccess {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Urgent ring sent to ${targetRider.displayName}"))
            }.onFailure {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Failed to send ring", true))
            }
        }
    }

    fun broadcastWeatherHazardAlert(headline: String) {
        val pack = activePack.value ?: return
        val myName = userProfile.value?.displayName ?: "Pack Rider"
        viewModelScope.launch {
            repository.sendUrgentPing(
                pack = pack,
                senderName = myName,
                targetUserId = "ALL",
                alertType = "WEATHER_HAZARD",
                note = headline
            )
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Weather hazard alert sent to pack"))
        }
    }

    fun acknowledgePing(pingId: String) {
        val pack = activePack.value ?: return
        viewModelScope.launch {
            repository.acknowledgePing(pack.id, pingId)
        }
    }

    fun sendMessage(text: String, recipientId: String = "") {
        val pack = activePack.value ?: return
        if (text.isBlank()) return
        val myName = userProfile.value?.displayName ?: "Rider"
        val nowMs = System.currentTimeMillis()
        if (settings.value.p2pMeshEnabled && p2pMeshState.value.isActive) {
            val meshMsg = DecryptedPackMessage(
                id = "mesh_${UUID.randomUUID().toString().take(8)}",
                senderId = currentUserId,
                senderName = myName,
                recipientId = recipientId,
                text = text,
                messageType = "text",
                timestampMs = nowMs
            )
            p2pMeshManager.broadcastMessage(
                MeshMessageBody(
                    messageId = meshMsg.id,
                    senderId = currentUserId,
                    senderName = myName,
                    recipientId = recipientId,
                    text = text,
                    messageType = "text",
                    timestampMs = nowMs
                )
            )
            if (isEffectiveOffline.value) {
                _meshMessages.value = _meshMessages.value + meshMsg
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Sent via P2P Convoy Mesh"))
                return
            }
        }
        viewModelScope.launch {
            val res = repository.sendPackMessage(
                pack = pack,
                senderName = myName,
                plainText = text,
                recipientId = recipientId
            )
            res.onFailure {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Message failed to send", true))
            }
        }
    }

    fun startPttRecording(recipientId: String = "") {
        if (activePack.value == null) {
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Create or join a pack before sending Push-to-Talk audio", true))
            return
        }
        activePttRecipientId = recipientId
        val started = pttVoiceEngine.startRecording(
            onMaxDurationReached = {
                stopAndSendPttRecording(activePttRecipientId)
            }
        )
        started.onFailure { err ->
            _alertEvents.tryEmit(
                AlertEvent.StatusBanner(
                    err.localizedMessage ?: "Microphone unavailable on this device",
                    isError = true
                )
            )
        }
    }

    fun stopAndSendPttRecording(recipientId: String = activePttRecipientId) {
        val pack = activePack.value
        val myName = userProfile.value?.displayName ?: "Rider"
        viewModelScope.launch {
            val clipRes = pttVoiceEngine.stopRecordingAndEncode()
            clipRes.onSuccess { clip ->
                if (pack == null) {
                    _alertEvents.tryEmit(AlertEvent.StatusBanner("No active pack selected", true))
                    return@onSuccess
                }
                if (settings.value.p2pMeshEnabled && p2pMeshState.value.isActive) {
                    val meshVoice = DecryptedPackMessage(
                        id = "mesh_voice_${UUID.randomUUID().toString().take(8)}",
                        senderId = currentUserId,
                        senderName = myName,
                        recipientId = recipientId,
                        text = "[Voice Message]",
                        messageType = "voice",
                        timestampMs = System.currentTimeMillis(),
                        audioBase64 = clip.audioBase64,
                        audioCodec = clip.audioCodec,
                        audioDurationMs = clip.durationMs,
                        audioSizeBytes = clip.sizeBytes,
                        audioPeaks = PttVoiceEngine.parsePeaksCsv(clip.peaksCsv)
                    )
                    p2pMeshManager.broadcastMessage(
                        MeshMessageBody(
                            messageId = meshVoice.id,
                            senderId = currentUserId,
                            senderName = myName,
                            recipientId = recipientId,
                            text = "[Voice Message]",
                            messageType = "voice",
                            audioBase64 = clip.audioBase64,
                            audioCodec = clip.audioCodec,
                            audioDurationMs = clip.durationMs,
                            audioSizeBytes = clip.sizeBytes,
                            audioPeaks = clip.peaksCsv,
                            timestampMs = meshVoice.timestampMs
                        )
                    )
                    if (isEffectiveOffline.value) {
                        _meshMessages.value = _meshMessages.value + meshVoice
                    }
                }
                if (isEffectiveOffline.value) {
                    offlineDao.enqueueVoiceMessage(
                        QueuedVoiceMessageEntity(
                            packId = pack.id,
                            senderName = myName,
                            recipientId = recipientId,
                            audioBase64 = clip.audioBase64,
                            audioCodec = clip.audioCodec,
                            audioDurationMs = clip.durationMs,
                            audioSizeBytes = clip.sizeBytes,
                            audioPeaks = clip.peaksCsv
                        )
                    )
                    _alertEvents.tryEmit(
                        AlertEvent.StatusBanner(
                            if (settings.value.p2pMeshEnabled && p2pMeshState.value.isActive) {
                                "Relayed PTT voice over P2P Mesh + queued for cloud"
                            } else {
                                "Offline dead-zone: queued ${PttVoiceEngine.formatCodecBadge(clip.audioCodec, clip.sizeBytes)} voice clip"
                            }
                        )
                    )
                } else {
                    val sendRes = repository.sendPackVoiceMessage(
                        pack = pack,
                        senderName = myName,
                        audioBase64 = clip.audioBase64,
                        audioCodec = clip.audioCodec,
                        audioDurationMs = clip.durationMs,
                        audioSizeBytes = clip.sizeBytes,
                        audioPeaks = clip.peaksCsv,
                        recipientId = recipientId
                    )
                    sendRes.onSuccess {
                        _alertEvents.tryEmit(
                            AlertEvent.StatusBanner(
                                "PTT voice sent (${PttVoiceEngine.formatCodecBadge(clip.audioCodec, clip.sizeBytes)})"
                            )
                        )
                    }.onFailure {
                        offlineDao.enqueueVoiceMessage(
                            QueuedVoiceMessageEntity(
                                packId = pack.id,
                                senderName = myName,
                                recipientId = recipientId,
                                audioBase64 = clip.audioBase64,
                                audioCodec = clip.audioCodec,
                                audioDurationMs = clip.durationMs,
                                audioSizeBytes = clip.sizeBytes,
                                audioPeaks = clip.peaksCsv
                            )
                        )
                        _alertEvents.tryEmit(
                            AlertEvent.StatusBanner("Weak signal: PTT voice clip queued for auto-retry")
                        )
                    }
                }
            }.onFailure { err ->
                _alertEvents.tryEmit(
                    AlertEvent.StatusBanner(
                        err.localizedMessage ?: "Push-to-Talk capture cancelled",
                        isError = false
                    )
                )
            }
        }
    }

    fun cancelPttRecording() {
        pttVoiceEngine.cancelRecording()
        _alertEvents.tryEmit(AlertEvent.StatusBanner("Voice recording cancelled"))
    }

    fun togglePlayVoiceMessage(message: DecryptedPackMessage) {
        if (message.audioBase64.isBlank()) {
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Voice audio unavailable or still syncing", true))
            return
        }
        pttVoiceEngine.togglePlayback(
            messageId = message.id,
            audioBase64 = message.audioBase64,
            codec = message.audioCodec,
            fallbackDurationMs = message.audioDurationMs
        ).onFailure { err ->
            _alertEvents.tryEmit(
                AlertEvent.StatusBanner(err.localizedMessage ?: "Unable to play voice message", true)
            )
        }
    }

    fun setPttAutoPlay(enabled: Boolean) {
        viewModelScope.launch { preferences.setPttAutoPlay(enabled) }
    }

    fun planRoute(routeName: String, destinationLabel: String, destinationLatLng: LatLng? = null) {
        viewModelScope.launch {
            _isPlanningRoute.value = true
            val my = _myGpsFix.value
            val computed = routeRepository.planMotorcycleRoute(
                routeName = routeName,
                origin = LatLng(my.lat, my.lng),
                destinationLabel = destinationLabel,
                destinationLatLng = destinationLatLng
            )
            _plannedRoute.value = computed
            _hudStepOverrideIndex.value = null
            activePack.value?.let { pack ->
                if (!isEffectiveOffline.value) {
                    repository.updatePackRoute(
                        packId = pack.id,
                        routeName = computed.routeName,
                        encodedPolyline = computed.encodedPolyline,
                        distanceKm = computed.distanceKm,
                        durationMin = computed.durationMin.toLong()
                    )
                }
            }
            _isPlanningRoute.value = false
        }
    }

    fun savePlannedRouteOffline() {
        val route = _plannedRoute.value ?: return
        val packId = activePack.value?.id ?: "solo_cache"
        viewModelScope.launch {
            offlineDao.upsertOfflineRoute(
                OfflineRouteEntity(
                    routeId = UUID.randomUUID().toString(),
                    packId = packId,
                    name = route.routeName,
                    originLabel = route.originLabel,
                    destinationLabel = route.destinationLabel,
                    encodedPolyline = route.encodedPolyline,
                    distanceKm = route.distanceKm,
                    durationMin = route.durationMin,
                    trafficDelayMin = route.trafficDelayMin,
                    elevationPointsJson = json.encodeToString(route.elevationSamples),
                    fuelStopsJson = json.encodeToString(route.fuelStations),
                    weatherAlertsJson = json.encodeToString(route.weatherWarnings),
                    navigationStepsJson = json.encodeToString(route.navigationSteps)
                )
            )
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Route, turn steps, elevation, fuel & weather cached offline"))
        }
    }

    fun loadOfflineRoute(entity: OfflineRouteEntity) {
        viewModelScope.launch {
            val pts = runCatching { PolyUtil.decode(entity.encodedPolyline) }.getOrDefault(emptyList())
            val elev = runCatching { json.decodeFromString<List<ElevationSample>>(entity.elevationPointsJson) }.getOrDefault(emptyList())
            val fuels = runCatching { json.decodeFromString<List<FuelStationStop>>(entity.fuelStopsJson) }.getOrDefault(emptyList())
            val weather = runCatching { json.decodeFromString<List<RouteWeatherWarning>>(entity.weatherAlertsJson) }.getOrDefault(emptyList())
            val decodedSteps = runCatching {
                json.decodeFromString<List<RouteNavigationStep>>(entity.navigationStepsJson)
            }.getOrDefault(emptyList())
            val navSteps = decodedSteps.ifEmpty {
                MotoRouteAndWeatherRepository.buildGeometricNavigationSteps(pts, entity.destinationLabel)
            }
            _plannedRoute.value = ComputedMotoRoute(
                routeName = entity.name,
                originLabel = entity.originLabel,
                destinationLabel = entity.destinationLabel,
                points = pts,
                encodedPolyline = entity.encodedPolyline,
                distanceKm = entity.distanceKm,
                durationMin = entity.durationMin,
                trafficDelayMin = entity.trafficDelayMin,
                elevationSamples = elev,
                maxElevationM = elev.maxOfOrNull { it.elevationM } ?: 0.0,
                totalAscentM = elev.zipWithNext { a, b -> (b.elevationM - a.elevationM).coerceAtLeast(0.0) }.sum(),
                fuelStations = fuels,
                weatherWarnings = weather,
                navigationSteps = navSteps,
                isLiveGoogleData = false,
                apiNotice = "Loaded from Offline Route Cache (${navSteps.size} cached turns)"
            )
            _hudStepOverrideIndex.value = null
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Loaded offline route: ${entity.name}"))
        }
    }

    fun createChallenge(title: String, description: String, type: String, targetValue: Double, unit: String) {
        val pack = activePack.value ?: return
        val fix = _myGpsFix.value
        viewModelScope.launch {
            val res = repository.createChallenge(
                pack = pack,
                title = title,
                description = description,
                challengeType = type,
                targetValue = targetValue,
                unit = unit,
                checkpointLat = fix.lat + 0.03,
                checkpointLng = fix.lng - 0.03
            )
            res.onSuccess {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Challenge '$title' launched for pack!"))
            }.onFailure {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Could not create challenge", true))
            }
        }
    }

    fun incrementChallengeProgress(challenge: MapChallenge, delta: Double) {
        val pack = activePack.value ?: return
        if (challenge.status == "completed") return
        viewModelScope.launch {
            repository.updateChallengeProgress(pack.id, challenge, challenge.currentValue + delta)
        }
    }

    fun toggleRideTracking(active: Boolean) {
        _isRideTrackingActive.value = active
        if (active) {
            val startFix = _myGpsFix.value
            _rideStartTimeMs.value = System.currentTimeMillis()
            _sessionRideDistanceKm.value = 0.0
            _sessionElevationGainM.value = 0.0
            rideStartAltitudeM = startFix.altitudeM
            lastSampledElevationDistKm = 0.0
            _sessionElevationSamples.value = listOf(
                ElevationSample(
                    distanceKm = 0.0,
                    elevationM = startFix.altitudeM,
                    lat = startFix.lat,
                    lng = startFix.lng
                )
            )
            rideStartBatteryPercent = batteryState.value.percent
            sessionWeatherEncounters.clear()
            sessionMaxWindGustKmh = 0.0
            sessionWorstWeatherSeverity = "CLEAR"
            _liveWeatherAlert.value?.let { recordWeatherEncounter(it) }
        } else {
            val startMs = _rideStartTimeMs.value ?: System.currentTimeMillis()
            val durationMin = ((System.currentTimeMillis() - startMs) / 60_000L).coerceAtLeast(1L)
            val dist = _sessionRideDistanceKm.value.coerceAtLeast(0.1)
            val elev = _sessionElevationGainM.value
            val fix = _myGpsFix.value
            val netElevChange = fix.altitudeM - (rideStartAltitudeM ?: fix.altitudeM)
            val avgSpeed = (dist / (durationMin / 60.0)).coerceAtMost(220.0)
            val title = _plannedRoute.value?.routeName ?: activePack.value?.name?.let { "$it Ride" } ?: "MotoPack Ride"
            val packId = activePack.value?.id ?: ""
            val weatherSummary = if (sessionWeatherEncounters.isNotEmpty()) {
                sessionWeatherEncounters.take(3).joinToString(" • ")
            } else {
                _liveWeatherAlert.value?.alertHeadline ?: "Clear riding conditions"
            }
            val worstSeverity = if (sessionWeatherEncounters.isNotEmpty()) {
                sessionWorstWeatherSeverity
            } else {
                _liveWeatherAlert.value?.severity ?: "CLEAR"
            }
            val maxGust = if (sessionMaxWindGustKmh > 0.0) {
                sessionMaxWindGustKmh
            } else {
                _liveWeatherAlert.value?.windGustKmh ?: 0.0
            }
            val ecoUsed = settings.value.ecoRideMode || batteryState.value.percent <= 20
            val battUsed = (rideStartBatteryPercent - batteryState.value.percent).coerceAtLeast(0)

            val recordedSamples = _sessionElevationSamples.value.let { list ->
                if (list.size >= 2) {
                    list
                } else {
                    val startAlt = list.firstOrNull()?.elevationM ?: (rideStartAltitudeM ?: fix.altitudeM)
                    listOf(
                        ElevationSample(0.0, startAlt, fix.lat, fix.lng),
                        ElevationSample(dist, fix.altitudeM, fix.lat, fix.lng)
                    )
                }
            }
            _activeTripElevationSamples.value = recordedSamples

            val completedTrip = RideTrip(
                id = "session_${System.currentTimeMillis()}",
                userId = currentUserId,
                packId = packId,
                title = title,
                distanceKm = dist,
                elevationGainM = elev,
                elevationChangeNetM = netElevChange,
                durationMin = durationMin,
                avgSpeedKmh = avgSpeed,
                startLat = fix.lat,
                startLng = fix.lng,
                endLat = fix.lat,
                endLng = fix.lng,
                weatherSummary = weatherSummary,
                weatherSeverity = worstSeverity,
                maxWindGustKmh = maxGust,
                batteryEfficiencyUsed = ecoUsed,
                batteryPercentUsed = battUsed
            )
            _activeTripSummary.value = completedTrip

            viewModelScope.launch {
                val samplesJson = json.encodeToString(recordedSamples)
                offlineDao.upsertTripElevation(CachedTripElevationEntity(completedTrip.id, samplesJson))
                val res = repository.logRideTrip(
                    packId = packId,
                    title = title,
                    distanceKm = dist,
                    elevationGainM = elev,
                    elevationChangeNetM = netElevChange,
                    durationMin = durationMin,
                    avgSpeedKmh = avgSpeed,
                    startLat = fix.lat,
                    startLng = fix.lng,
                    endLat = fix.lat,
                    endLng = fix.lng,
                    weatherSummary = weatherSummary,
                    weatherSeverity = worstSeverity,
                    maxWindGustKmh = maxGust,
                    batteryEfficiencyUsed = ecoUsed,
                    batteryPercentUsed = battUsed
                )
                res.onSuccess { tripId ->
                    offlineDao.upsertTripElevation(CachedTripElevationEntity(tripId, samplesJson))
                    if (_activeTripSummary.value?.id == completedTrip.id) {
                        _activeTripSummary.value = completedTrip.copy(id = tripId)
                    }
                }
                features.onRideDistanceCompleted(dist)
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Trip logged (${String.format("%.1f", dist)} km)"))
            }
            _rideStartTimeMs.value = null
            rideStartAltitudeM = null
            lastSampledElevationDistKm = -1.0
        }
    }

    fun selectTripForSummary(trip: RideTrip) {
        _activeTripSummary.value = trip
        viewModelScope.launch {
            val cached = offlineDao.getTripElevation(trip.id)
            val decoded = cached?.samplesJson?.let { raw ->
                runCatching { json.decodeFromString<List<ElevationSample>>(raw) }.getOrNull()
            }
            _activeTripElevationSamples.value = if (!decoded.isNullOrEmpty() && decoded.size >= 2) {
                decoded
            } else {
                buildEstimatedTripElevationSamples(trip)
            }
        }
    }

    fun dismissTripSummary() {
        _activeTripSummary.value = null
    }

    fun setHudThemeMode(mode: HudThemeMode) {
        viewModelScope.launch { preferences.setHudThemeMode(mode) }
    }

    fun setEcoRideMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setEcoRideMode(enabled) }
    }

    fun setManualOfflineMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setManualOfflineMode(enabled) }
    }

    fun setP2pMeshEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setP2pMeshEnabled(enabled) }
    }

    fun setMeshVpnEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setMeshVpnEnabled(enabled)
            val ctx = appContext ?: return@launch
            if (enabled) {
                val pack = activePack.value
                MotoPackMeshVpnService.start(
                    context = ctx,
                    packName = pack?.name ?: "MotoPack",
                    packId = pack?.id ?: "solo_pack",
                    userId = currentUserId
                )
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Encrypted Mesh VPN tunnel active"))
            } else {
                MotoPackMeshVpnService.stop(ctx)
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Encrypted Mesh VPN disconnected"))
            }
        }
    }

    fun prepareMeshVpnConsentIntent(): Intent? {
        val ctx = appContext ?: return null
        return MotoPackMeshVpnService.prepareConsentIntent(ctx)
    }

    fun connectWifiDirectPeer(deviceAddress: String) {
        p2pMeshManager.connectToWifiDirectPeer(deviceAddress)
    }

    fun addManualMeshPeer(hostAddress: String, port: Int = PackP2pMeshManager.DEFAULT_MESH_PORT) {
        p2pMeshManager.addManualMeshPeer(hostAddress, port)
        _alertEvents.tryEmit(AlertEvent.StatusBanner("Added P2P mesh peer $hostAddress:$port"))
    }

    fun hasRequiredP2pPermissions(): Boolean = p2pMeshManager.hasRequiredP2pPermissions()

    fun setSeparationAlertKm(km: Double) {
        viewModelScope.launch { preferences.setSeparationAlertKm(km) }
    }

    fun setProactiveWeatherPushEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setProactiveWeatherPushEnabled(enabled)
            _alertEvents.tryEmit(
                AlertEvent.StatusBanner(
                    if (enabled) "Proactive route weather alerts enabled" else "Proactive route weather alerts paused"
                )
            )
            if (enabled && _plannedRoute.value != null) {
                scanPlannedRouteWeatherNow(forcePushAlert = false)
            }
        }
    }

    fun scanPlannedRouteWeatherNow(forcePushAlert: Boolean = false) {
        val route = _plannedRoute.value ?: run {
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Plan a route first to scan incoming weather ahead", true))
            return
        }
        if (isEffectiveOffline.value) {
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Offline mode active — forecast push alerts require connectivity", true))
            return
        }
        viewModelScope.launch {
            performRouteWeatherScan(route, forcePushAlert = forcePushAlert)
        }
    }

    fun applyWeatherSuggestedStop(alert: ProactiveRouteWeatherAlert) {
        val stop = alert.suggestedStop ?: run {
            _alertEvents.tryEmit(AlertEvent.StatusBanner("No shelter stop coordinates available", true))
            return
        }
        _proactiveWeatherAlert.value = null
        planRoute(
            routeName = "Safe Stop: ${stop.name}",
            destinationLabel = stop.name,
            destinationLatLng = LatLng(stop.lat, stop.lng)
        )
        _alertEvents.tryEmit(
            AlertEvent.StatusBanner("Rerouting to shelter stop: ${stop.name}")
        )
    }

    fun applyWeatherSuggestedDetour(alert: ProactiveRouteWeatherAlert) {
        val detour = alert.suggestedRouteChange
        _proactiveWeatherAlert.value = null
        planRoute(
            routeName = detour.title,
            destinationLabel = detour.destinationLabel,
            destinationLatLng = LatLng(detour.detourLat, detour.detourLng)
        )
        _alertEvents.tryEmit(
            AlertEvent.StatusBanner("Applied weather detour: ${detour.title}")
        )
    }

    fun dismissProactiveWeatherAlert() {
        _proactiveWeatherAlert.value = null
    }

    private fun observeProactiveRouteWeather() {
        viewModelScope.launch {
            combine(
                _plannedRoute,
                myGpsFix,
                settings,
                isEffectiveOffline
            ) { route, fix, s, offline ->
                RouteWeatherMonitorSnapshot(
                    plannedRoute = route,
                    currentFix = fix,
                    proactivePushEnabled = s.proactiveWeatherPushEnabled,
                    isOffline = offline,
                    ecoMode = s.ecoRideMode
                )
            }.collect { snapshot ->
                RouteWeatherActionBus.updateMonitorSnapshot(snapshot)
            }
        }

        viewModelScope.launch {
            combine(
                _plannedRoute,
                settings.map { it.proactiveWeatherPushEnabled }.distinctUntilChanged(),
                isEffectiveOffline
            ) { route, enabled, offline ->
                Triple(route, enabled, offline)
            }.distinctUntilChanged().collect { (route, enabled, offline) ->
                if (route == null) {
                    _proactiveWeatherAlert.value = null
                    RouteWeatherActionBus.resetPushDeduplication()
                    return@collect
                }
                if (enabled && !offline) {
                    performRouteWeatherScan(route, forcePushAlert = false)
                }
            }
        }

        viewModelScope.launch {
            RouteWeatherActionBus.latestServiceAlert.collect { serviceAlert ->
                if (serviceAlert != null && _plannedRoute.value != null) {
                    _proactiveWeatherAlert.value = serviceAlert
                    _lastRouteWeatherScanMs.value = serviceAlert.detectedAtMs
                }
            }
        }
    }

    private fun observeWeatherNotificationActions() {
        viewModelScope.launch {
            RouteWeatherActionBus.actions.collect { action ->
                when (action) {
                    is RouteWeatherNotificationAction.ApplySuggestedStop -> {
                        _proactiveWeatherAlert.value = null
                        planRoute(
                            routeName = "Safe Stop: ${action.stopName}",
                            destinationLabel = action.stopName,
                            destinationLatLng = LatLng(action.lat, action.lng)
                        )
                        _alertEvents.tryEmit(
                            AlertEvent.StatusBanner("Routing to suggested stop: ${action.stopName}")
                        )
                    }
                    is RouteWeatherNotificationAction.ApplyRouteDetour -> {
                        _proactiveWeatherAlert.value = null
                        planRoute(
                            routeName = action.routeTitle,
                            destinationLabel = action.destinationLabel,
                            destinationLatLng = LatLng(action.lat, action.lng)
                        )
                        _alertEvents.tryEmit(
                            AlertEvent.StatusBanner("Applied weather detour: ${action.routeTitle}")
                        )
                    }
                    is RouteWeatherNotificationAction.OpenRoutePlanner -> {
                        // Navigation handled by MotoPackApp collector
                    }
                }
            }
        }
    }

    private suspend fun performRouteWeatherScan(
        route: ComputedMotoRoute,
        forcePushAlert: Boolean
    ) {
        _isScanningRouteWeather.value = true
        val detected = ProactiveRouteWeatherEngine.analyzeRoute(
            route = route,
            currentFix = _myGpsFix.value,
            routeRepository = routeRepository
        )
        _isScanningRouteWeather.value = false
        _lastRouteWeatherScanMs.value = System.currentTimeMillis()
        _proactiveWeatherAlert.value = detected
        RouteWeatherActionBus.recordServiceAlert(detected)

        if (detected != null &&
            settings.value.proactiveWeatherPushEnabled &&
            RouteWeatherActionBus.tryClaimPushSlot(detected, force = forcePushAlert)
        ) {
            _alertEvents.tryEmit(AlertEvent.RouteWeatherPush(detected))
        }
    }

    private fun observeLiveWeatherForRider() {
        viewModelScope.launch {
            combine(
                myGpsFix,
                hasRealGpsFix,
                isEffectiveOffline,
                settings.map { it.ecoRideMode }.distinctUntilChanged()
            ) { fix, realFix, offline, ecoMode ->
                Pair(Triple(fix, realFix, offline), ecoMode)
            }.collect { (state, ecoMode) ->
                val (fix, realFix, offline) = state
                if (offline) return@collect
                val prev = lastWeatherCoords
                val thresholdKm = if (ecoMode) 10.0 else 2.0
                val movedFar = prev == null || MotoRouteAndWeatherRepository.haversineKm(
                    prev.first,
                    prev.second,
                    fix.lat,
                    fix.lng
                ) >= thresholdKm
                if (movedFar || (realFix && !weatherFetchedWithRealFix)) {
                    if (realFix) weatherFetchedWithRealFix = true
                    refreshWeatherAt(fix.lat, fix.lng)
                }
            }
        }
    }

    fun refreshWeatherNow() {
        val fix = _myGpsFix.value
        viewModelScope.launch {
            refreshWeatherAt(fix.lat, fix.lng)
        }
    }

    private suspend fun refreshWeatherAt(lat: Double, lng: Double) {
        if (isEffectiveOffline.value) return
        _isRefreshingWeather.value = true
        val alert = openWeatherRepository.fetchRiderWeatherAlert(lat, lng)
        _isRefreshingWeather.value = false
        if (alert != null) {
            lastWeatherCoords = lat to lng
            _liveWeatherAlert.value = alert
            recordWeatherEncounter(alert)
            if (alert.severity == "WARNING" && lastAlertedWeatherHeadline != alert.alertHeadline) {
                lastAlertedWeatherHeadline = alert.alertHeadline
                _alertEvents.tryEmit(
                    AlertEvent.UrgentNotification(
                        title = "Rider Weather Warning",
                        body = alert.alertHeadline,
                        id = ("wx_" + alert.locationName).hashCode()
                    )
                )
            }
        }
    }

    private fun recordWeatherEncounter(alert: RiderLiveWeatherAlert) {
        if (!_isRideTrackingActive.value) return
        val desc = "${alert.conditionTitle} (${alert.tempCelsius.toInt()}°C, ${alert.windGustKmh.toInt()} km/h gusts)"
        sessionWeatherEncounters.add(desc)
        if (alert.windGustKmh > sessionMaxWindGustKmh) {
            sessionMaxWindGustKmh = alert.windGustKmh
        }
        if (severityRank(alert.severity) > severityRank(sessionWorstWeatherSeverity)) {
            sessionWorstWeatherSeverity = alert.severity
        }
    }

    private fun severityRank(severity: String): Int = when (severity.uppercase()) {
        "WARNING" -> 2
        "ADVISORY" -> 1
        else -> 0
    }

    fun runAiSafetyBriefing() {
        viewModelScope.launch {
            _aiBriefingState.value = AiBriefingUiState.Loading
            try {
                val briefing = aiCopilotService.generateSafetyBriefing(
                    packName = activePack.value?.name,
                    weather = _liveWeatherAlert.value,
                    riders = riders.value,
                    plannedRoute = _plannedRoute.value,
                    isOffline = isEffectiveOffline.value
                )
                _aiBriefingState.value = AiBriefingUiState.Success(briefing)
            } catch (e: TimeoutCancellationException) {
                Log.e("MotoPackVM", "AI safety briefing timed out", e)
                _aiBriefingState.value = AiBriefingUiState.Error(
                    "Briefing generation timed out after 60 seconds. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MotoPackVM", "AI safety briefing failed", e)
                _aiBriefingState.value = AiBriefingUiState.Error(
                    e.localizedMessage ?: "Failed to generate AI convoy briefing."
                )
            }
        }
    }

    fun askAiCopilot(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            aiCopilotService.streamRiderCopilotAnswer(
                question = question.trim(),
                packName = activePack.value?.name,
                weather = _liveWeatherAlert.value,
                riders = riders.value,
                plannedRoute = _plannedRoute.value,
                isOffline = isEffectiveOffline.value
            ).collect { state ->
                _aiCopilotState.value = state
            }
        }
    }

    fun toggleHudVoice(enabled: Boolean) {
        _hudVoiceEnabled.value = enabled
    }

    fun advanceHudStep(delta: Int) {
        val steps = _plannedRoute.value?.navigationSteps.orEmpty()
        if (steps.isEmpty()) return
        val current = _hudStepOverrideIndex.value ?: hudNavigationState.value.currentStepIndex
        _hudStepOverrideIndex.value = (current + delta).coerceIn(0, steps.lastIndex)
    }

    fun resetHudStepAuto() {
        _hudStepOverrideIndex.value = null
    }

    fun navigateToPackLeader() {
        val otherRider = riders.value
            .filter { it.userId != currentUserId }
            .minByOrNull { it.distanceFromMeKm } ?: run {
            _alertEvents.tryEmit(AlertEvent.StatusBanner("No active pack rider to route to yet", true))
            return
        }
        planRoute(
            routeName = "Catch Up: ${otherRider.displayName.substringBefore(" ")}",
            destinationLabel = "${otherRider.displayName}'s Bike",
            destinationLatLng = LatLng(otherRider.lat, otherRider.lng)
        )
    }

    fun refreshSmartHudCallout() {
        val fix = _myGpsFix.value
        val hud = hudNavigationState.value
        val batt = batteryState.value
        if (isEffectiveOffline.value) {
            _smartHudState.value = SmartHudUiState.Error(
                "Offline dead-zone — local sensor & cached route advisories active."
            )
            return
        }
        viewModelScope.launch {
            _smartHudState.value = SmartHudUiState.Loading
            try {
                val callout = aiCopilotService.generateSmartHudCallout(
                    speedKmh = fix.speedKmh,
                    nextTurnInstruction = if (hud.hasActiveRoute) hud.currentInstruction else null,
                    nextTurnDistanceMeters = if (hud.hasActiveRoute) hud.distanceToNextTurnMeters else null,
                    batteryPercent = batt.percent,
                    packName = activePack.value?.name,
                    weather = _liveWeatherAlert.value,
                    riders = riders.value,
                    plannedRoute = _plannedRoute.value,
                    isOffline = isEffectiveOffline.value
                )
                _smartHudState.value = SmartHudUiState.Success(callout)
            } catch (e: TimeoutCancellationException) {
                Log.e("MotoPackVM", "Smart HUD callout timed out", e)
                _smartHudState.value = SmartHudUiState.Error("Smart HUD AI scan timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MotoPackVM", "Smart HUD callout failed", e)
                _smartHudState.value = SmartHudUiState.Error(
                    e.localizedMessage ?: "Unable to fetch Smart HUD callout."
                )
            }
        }
    }

    private fun buildHudNavigationUiState(
        route: ComputedMotoRoute?,
        fix: LiveGpsFix,
        overrideIdx: Int?,
        safety: HudSafetySnapshot
    ): HudNavigationUiState {
        val advisories = mutableListOf<HudSmartAdvisory>()

        // 1. Pack SOS / Road Hazard Pin Check
        safety.pins
            .filter { it.status == "active" && (it.pinType == "EMERGENCY_SOS" || it.pinType == "HAZARD_WARNING") }
            .map { pin ->
                pin to MotoRouteAndWeatherRepository.haversineKm(fix.lat, fix.lng, pin.lat, pin.lng)
            }
            .filter { it.second <= 8.0 }
            .minByOrNull { it.second }
            ?.let { (pin, distKm) ->
                val distLabel = if (distKm < 1.0) "${(distKm * 1000).toInt()} m" else String.format("%.1f km", distKm)
                advisories += HudSmartAdvisory(
                    severity = if (pin.pinType == "EMERGENCY_SOS" || pin.severity == "DANGER") "CRITICAL" else "CAUTION",
                    title = pin.title,
                    detail = "$distLabel away • ${pin.description.ifBlank { "Pack warning" }}",
                    tag = if (pin.pinType == "EMERGENCY_SOS") "PACK SOS" else "HAZARD"
                )
            }

        // 2. OpenWeatherMap Live Weather Hazard
        safety.weather?.let { wx ->
            if (wx.hasRainAlert || wx.hasHighWindAlert || wx.severity != "CLEAR") {
                advisories += HudSmartAdvisory(
                    severity = if (wx.severity == "WARNING") "CRITICAL" else "CAUTION",
                    title = wx.alertHeadline,
                    detail = String.format("%.0f°C • Gusts %.0f km/h", wx.tempCelsius, wx.windGustKmh),
                    tag = if (wx.hasHighWindAlert) "CROSSWIND" else "WEATHER"
                )
            }
        }

        // 2b. Proactive Corridor Weather Shield Warning Ahead
        safety.routeWeatherAlert?.let { routeAlert ->
            advisories += HudSmartAdvisory(
                severity = if (routeAlert.severity == WeatherAdvisorySeverity.CRITICAL) "CRITICAL" else "CAUTION",
                title = "${routeAlert.hazardType.label} in ~${routeAlert.etaMinutes}m (${String.format("%.1f", routeAlert.distanceAheadKm)} km)",
                detail = routeAlert.suggestedStop?.let { "Stop: ${it.name}" } ?: routeAlert.suggestedRouteChange.title,
                tag = "ROUTE WX"
            )
        }

        // 3. Pack Sweeper Separation Check
        safety.riders
            .filter { it.userId != currentUserId }
            .maxByOrNull { it.distanceFromMeKm }
            ?.let { farthest ->
                if (farthest.distanceFromMeKm > safety.settings.separationAlertKm) {
                    advisories += HudSmartAdvisory(
                        severity = "CAUTION",
                        title = "${farthest.displayName.substringBefore(" ")} lagging ${String.format("%.1f", farthest.distanceFromMeKm)} km",
                        detail = "Exceeds ${String.format("%.1f", safety.settings.separationAlertKm)} km pack threshold",
                        tag = "PACK GAP"
                    )
                }
            }

        // 4. Low Battery Guard
        if (safety.battery.percent <= 20 && !safety.battery.isCharging && !safety.settings.ecoRideMode) {
            advisories += HudSmartAdvisory(
                severity = "CAUTION",
                title = "Phone battery low (${safety.battery.percent}%)",
                detail = "Switch to Battery Eco to protect route GPS.",
                tag = "BATTERY"
            )
        }

        val steps = route?.navigationSteps.orEmpty()
        if (route == null || steps.isEmpty()) {
            return HudNavigationUiState(
                hasActiveRoute = false,
                routeName = route?.routeName ?: "Free Ride HUD",
                currentInstruction = "Free Ride Mode",
                currentManeuver = "STRAIGHT",
                distanceToNextTurnMeters = 0,
                smartAdvisories = advisories
            )
        }

        val resolvedIdx = if (overrideIdx != null) {
            overrideIdx.coerceIn(0, steps.lastIndex)
        } else {
            val upcomingIdx = steps.indexOfFirst { step ->
                MotoRouteAndWeatherRepository.haversineKm(
                    fix.lat,
                    fix.lng,
                    step.endLat,
                    step.endLng
                ) * 1000.0 > 35.0
            }
            if (upcomingIdx >= 0) upcomingIdx else steps.lastIndex
        }

        val currentStep = steps[resolvedIdx]
        val liveDistToStepEndM = (MotoRouteAndWeatherRepository.haversineKm(
            fix.lat,
            fix.lng,
            currentStep.endLat,
            currentStep.endLng
        ) * 1000.0).toInt().coerceAtLeast(10)

        // When user is parked off-corridor or previewing steps manually, blend cleanly with step nominal distance
        val displayTurnDistM = if (overrideIdx != null || liveDistToStepEndM > currentStep.distanceMeters * 3) {
            currentStep.distanceMeters
        } else {
            liveDistToStepEndM
        }

        val progress = (1f - (displayTurnDistM.toFloat() / currentStep.distanceMeters.coerceAtLeast(50).toFloat()))
            .coerceIn(0.05f, 1f)

        // Sharp-Turn Speed Guard
        val isSharpTurn = currentStep.maneuver.contains("SHARP", ignoreCase = true) ||
            currentStep.maneuver.contains("UTURN", ignoreCase = true) ||
            currentStep.maneuver.contains("TURN_LEFT", ignoreCase = true) ||
            currentStep.maneuver.contains("TURN_RIGHT", ignoreCase = true)
        if (isSharpTurn && displayTurnDistM <= 400 && fix.speedKmh >= 55.0) {
            advisories.add(
                0,
                HudSmartAdvisory(
                    severity = "CRITICAL",
                    title = "Ease speed for turn in ${displayTurnDistM}m",
                    detail = "Approaching ${currentStep.instruction} at ${fix.speedKmh.toInt()} km/h",
                    tag = "CORNER GUARD"
                )
            )
        }

        val thenStep = steps.getOrNull(resolvedIdx + 1)
        val remainingMeters = displayTurnDistM + steps.drop(resolvedIdx + 1).sumOf { it.distanceMeters }
        val remainingKm = (remainingMeters / 1000.0).coerceAtLeast(0.1)
        val remainingSec = ((displayTurnDistM.toFloat() / currentStep.distanceMeters.coerceAtLeast(1)) * currentStep.durationSec).toInt() +
            steps.drop(resolvedIdx + 1).sumOf { it.durationSec }
        val remainingMin = (remainingSec / 60).coerceAtLeast(1)

        val nextFuel = route.fuelStations.firstOrNull()?.let { fuel ->
            "${fuel.name.take(18)} (+${String.format("%.1f", fuel.distanceAlongKm)} km)"
        }

        return HudNavigationUiState(
            hasActiveRoute = true,
            routeName = route.routeName,
            currentStepIndex = resolvedIdx,
            totalSteps = steps.size,
            currentInstruction = currentStep.instruction,
            currentManeuver = currentStep.maneuver,
            distanceToNextTurnMeters = displayTurnDistM,
            stepProgressFraction = progress,
            thenInstruction = thenStep?.instruction,
            thenManeuver = thenStep?.maneuver,
            thenDistanceMeters = thenStep?.distanceMeters,
            remainingRouteKm = remainingKm,
            remainingEtaMin = remainingMin,
            nextFuelStopLabel = nextFuel,
            smartAdvisories = advisories
        )
    }

    fun createPackMarker(
        pinType: String,
        title: String,
        description: String,
        lat: Double,
        lng: Double,
        severity: String
    ) {
        val pack = activePack.value ?: run {
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Join or create a Pack to share pins with riders", true))
            return
        }
        val myName = userProfile.value?.displayName ?: "Rider"
        viewModelScope.launch {
            val res = repository.createPackMarker(
                pack = pack,
                creatorName = myName,
                pinType = pinType,
                title = title,
                description = description,
                lat = lat,
                lng = lng,
                severity = severity
            )
            res.onSuccess {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Shared '${title.trim()}' pin with pack"))
            }.onFailure {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Failed to drop pack pin", true))
            }
        }
    }

    fun confirmPackMarker(pin: PackMapPin) {
        val packId = activePack.value?.id ?: pin.packId
        if (packId.isBlank()) return
        viewModelScope.launch {
            repository.confirmPackMarker(packId, pin).onSuccess {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Confirmed '${pin.title}'"))
            }
        }
    }

    fun resolvePackMarker(pin: PackMapPin) {
        val packId = activePack.value?.id ?: pin.packId
        if (packId.isBlank()) return
        viewModelScope.launch {
            repository.resolvePackMarker(packId, pin.id).onSuccess {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Marked '${pin.title}' as cleared"))
            }
        }
    }

    fun deletePackMarker(pin: PackMapPin) {
        val packId = activePack.value?.id ?: pin.packId
        if (packId.isBlank()) return
        viewModelScope.launch {
            repository.deletePackMarker(packId, pin.id).onSuccess {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Deleted pin"))
            }
        }
    }

    fun postPackGalleryPhoto(uri: Uri, caption: String, locationLabel: String) {
        val pack = activePack.value ?: run {
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Join or create a Pack to share gallery photos", true))
            return
        }
        val myName = userProfile.value?.displayName ?: "Rider"
        val fix = _myGpsFix.value
        viewModelScope.launch {
            val encoded = encodeGalleryPhotoBase64(uri)
            if (encoded.isNullOrBlank()) {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Could not read selected photo", true))
                return@launch
            }
            val res = repository.postPackGalleryPhoto(
                pack = pack,
                uploaderName = myName,
                caption = caption,
                locationLabel = locationLabel,
                imageDataBase64 = encoded,
                lat = fix.lat,
                lng = fix.lng
            )
            res.onSuccess {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Ride photo shared to pack gallery"))
            }.onFailure {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Photo upload failed", true))
            }
        }
    }

    fun deletePackGalleryPhoto(photo: PackGalleryPhoto) {
        val packId = activePack.value?.id ?: photo.packId
        if (packId.isBlank()) return
        viewModelScope.launch {
            repository.deletePackGalleryPhoto(packId, photo.id).onSuccess {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Photo removed from pack gallery"))
            }
        }
    }

    private suspend fun encodeGalleryPhotoBase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext null
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                val original = BitmapFactory.decodeStream(stream) ?: return@use null
                val maxDim = 640
                val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
                val scaled = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * scale).toInt().coerceAtLeast(1),
                        (original.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    original
                }
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 65, out)
                if (scaled !== original) scaled.recycle()
                original.recycle()
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        }.getOrNull()
    }

    fun downloadArea(
        title: String,
        center: LatLng,
        radiusKm: Double,
        useActiveRoute: Boolean
    ) {
        val manager = mapAreaManager ?: return
        if (_mapDownloadProgress.value.isDownloading) return
        val packId = activePack.value?.id ?: "solo_cache"
        val activeRoute = if (useActiveRoute) _plannedRoute.value else null

        viewModelScope.launch {
            _mapDownloadProgress.value = MapAreaDownloadProgress(
                isDownloading = true,
                title = title.ifBlank { "Offline Sector" },
                completedTiles = 0,
                totalTiles = 1
            )
            val navBundle = activeRoute ?: routeRepository.fetchSectorNavigationSnapshot(
                center = center,
                radiusKm = radiusKm,
                areaTitle = title
            )
            if (_plannedRoute.value == null) {
                _plannedRoute.value = navBundle
            }
            val routeId = UUID.randomUUID().toString()
            val navStepsJson = json.encodeToString(navBundle.navigationSteps)
            val elevJson = json.encodeToString(navBundle.elevationSamples)
            val fuelJson = json.encodeToString(navBundle.fuelStations)
            val weatherJson = json.encodeToString(navBundle.weatherWarnings)

            offlineDao.upsertOfflineRoute(
                OfflineRouteEntity(
                    routeId = routeId,
                    packId = packId,
                    name = navBundle.routeName,
                    originLabel = navBundle.originLabel,
                    destinationLabel = navBundle.destinationLabel,
                    encodedPolyline = navBundle.encodedPolyline,
                    distanceKm = navBundle.distanceKm,
                    durationMin = navBundle.durationMin,
                    trafficDelayMin = navBundle.trafficDelayMin,
                    elevationPointsJson = elevJson,
                    fuelStopsJson = fuelJson,
                    weatherAlertsJson = weatherJson,
                    navigationStepsJson = navStepsJson
                )
            )

            val res = manager.downloadRouteOrRadiusArea(
                packId = packId,
                title = title.ifBlank { navBundle.routeName },
                center = center,
                routePoints = if (activeRoute != null) activeRoute.points else emptyList(),
                encodedPolyline = navBundle.encodedPolyline,
                radiusKm = radiusKm,
                linkedRouteId = routeId,
                navStepCount = navBundle.navigationSteps.size,
                fuelStopCount = navBundle.fuelStations.size,
                elevationSampleCount = navBundle.elevationSamples.size,
                weatherCount = navBundle.weatherWarnings.size,
                maxElevationM = navBundle.maxElevationM,
                elevationGainM = navBundle.totalAscentM,
                navigationStepsJson = navStepsJson,
                elevationPointsJson = elevJson,
                fuelStopsJson = fuelJson,
                weatherAlertsJson = weatherJson,
                onProgress = { progress -> _mapDownloadProgress.value = progress }
            )
            _mapDownloadProgress.value = MapAreaDownloadProgress()
            res.onSuccess { area ->
                offlineDao.upsertOfflineMapArea(area)
                _activeOfflineArea.value = area
                _alertEvents.tryEmit(
                    AlertEvent.StatusBanner(
                        "Downloaded ${area.tileCount} tiles + ${area.navStepCount} nav turns & elevation for ${area.title}"
                    )
                )
            }.onFailure {
                _alertEvents.tryEmit(AlertEvent.StatusBanner("Failed to download offline area", true))
            }
        }
    }

    fun downloadRouteMapArea() {
        val route = _plannedRoute.value ?: return
        val fix = _myGpsFix.value
        val center = route.points.firstOrNull() ?: LatLng(fix.lat, fix.lng)
        downloadArea(
            title = "${route.routeName} Area",
            center = center,
            radiusKm = 10.0,
            useActiveRoute = true
        )
    }

    fun downloadRadiusMapArea(title: String, radiusKm: Double) {
        val fix = _myGpsFix.value
        downloadArea(
            title = title.ifBlank { "Sector (${radiusKm.toInt()} km)" },
            center = LatLng(fix.lat, fix.lng),
            radiusKm = radiusKm,
            useActiveRoute = false
        )
    }

    fun selectOfflineMapArea(area: OfflineMapAreaEntity?) {
        _activeOfflineArea.value = area
        if (area != null) {
            viewModelScope.launch {
                val linked = if (area.linkedRouteId.isNotBlank()) {
                    offlineDao.getOfflineRouteById(area.linkedRouteId)
                } else {
                    null
                }
                if (linked != null) {
                    loadOfflineRoute(linked)
                } else if (area.routePolyline.isNotBlank() && (_plannedRoute.value == null || isEffectiveOffline.value)) {
                    val pts = runCatching { PolyUtil.decode(area.routePolyline) }.getOrDefault(emptyList())
                    if (pts.isNotEmpty()) {
                        val elev = runCatching {
                            json.decodeFromString<List<ElevationSample>>(area.elevationPointsJson)
                        }.getOrDefault(emptyList())
                        val fuels = runCatching {
                            json.decodeFromString<List<FuelStationStop>>(area.fuelStopsJson)
                        }.getOrDefault(emptyList())
                        val weather = runCatching {
                            json.decodeFromString<List<RouteWeatherWarning>>(area.weatherAlertsJson)
                        }.getOrDefault(emptyList())
                        val steps = runCatching {
                            json.decodeFromString<List<RouteNavigationStep>>(area.navigationStepsJson)
                        }.getOrDefault(emptyList()).ifEmpty {
                            MotoRouteAndWeatherRepository.buildGeometricNavigationSteps(pts, area.title)
                        }
                        _plannedRoute.value = ComputedMotoRoute(
                            routeName = area.title,
                            originLabel = "Cached Sector",
                            destinationLabel = area.title,
                            points = pts,
                            encodedPolyline = area.routePolyline,
                            distanceKm = area.radiusKm,
                            durationMin = ((area.radiusKm / 55.0) * 60.0).toInt().coerceAtLeast(5),
                            trafficDelayMin = 0,
                            elevationSamples = elev,
                            maxElevationM = area.maxElevationM.takeIf { it > 0.0 }
                                ?: (elev.maxOfOrNull { it.elevationM } ?: 0.0),
                            totalAscentM = area.elevationGainM.takeIf { it > 0.0 }
                                ?: elev.zipWithNext { a, b -> (b.elevationM - a.elevationM).coerceAtLeast(0.0) }.sum(),
                            fuelStations = fuels,
                            weatherWarnings = weather,
                            navigationSteps = steps,
                            isLiveGoogleData = false,
                            apiNotice = "Offline Area Active (${area.tileCount} tiles, ${steps.size} nav steps)"
                        )
                        _hudStepOverrideIndex.value = null
                    }
                }
            }
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Offline area '${area.title}' active on radar"))
        }
    }

    fun deleteOfflineMapArea(area: OfflineMapAreaEntity) {
        viewModelScope.launch {
            mapAreaManager?.deleteAreaTiles(area.areaId)
            offlineDao.deleteOfflineMapArea(area.areaId)
            if (_activeOfflineArea.value?.areaId == area.areaId) {
                _activeOfflineArea.value = null
            }
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Deleted offline area '${area.title}'"))
        }
    }

    suspend fun searchOfflineRegions(query: String): List<com.example.data.remote.PlaceSearchSuggestion> {
        val fix = _myGpsFix.value
        return routeRepository.searchPlacesForOfflineArea(
            query = query,
            biasCenter = LatLng(fix.lat, fix.lng),
            context = appContext
        )
    }

    fun triggerEmergencySos(reason: String) {
        val fix = _myGpsFix.value
        val batt = batteryState.value
        val profile = userProfile.value
        val myName = profile?.displayName?.ifBlank { "Pack Rider" } ?: "Pack Rider"
        val myBike = profile?.bikeModel?.ifBlank { "Motorcycle" } ?: "Motorcycle"
        val cleanReason = reason.trim().ifBlank { "Emergency Assistance Needed" }
        val coordsText = String.format("%.5f, %.5f", fix.lat, fix.lng)
        val mapsLink = "https://maps.google.com/?q=${fix.lat},${fix.lng}"

        _isSosActive.value = true
        _sosLastBroadcastMs.value = System.currentTimeMillis()

        viewModelScope.launch {
            pushOrQueueTelemetry(fix)
            val pack = activePack.value
            if (pack != null && !isEffectiveOffline.value) {
                val pingPayload = "SOS|${fix.lat}|${fix.lng}|$cleanReason • $coordsText • $myBike (${batt.percent}% batt)"
                repository.sendUrgentPing(
                    pack = pack,
                    senderName = myName,
                    targetUserId = "ALL",
                    alertType = "EMERGENCY_SOS",
                    note = pingPayload
                )

                val pinResult = repository.createPackMarker(
                    pack = pack,
                    creatorName = myName,
                    pinType = "EMERGENCY_SOS",
                    title = "SOS: $myName Needs Help",
                    description = "$cleanReason • $myBike • Alt ${fix.altitudeM.toInt()}m • Batt ${batt.percent}% • $coordsText",
                    lat = fix.lat,
                    lng = fix.lng,
                    severity = "DANGER"
                )
                pinResult.onSuccess { pinId ->
                    activeSosPinId = pinId
                }

                repository.sendPackMessage(
                    pack = pack,
                    senderName = myName,
                    plainText = "EMERGENCY SOS ($cleanReason): Live GPS $coordsText (Alt ${fix.altitudeM.toInt()}m, Batt ${batt.percent}%). Map: $mapsLink",
                    messageType = "alert"
                )
                _alertEvents.tryEmit(
                    AlertEvent.StatusBanner("SOS live location shared with ${pack.name} — tap Dial/SMS for Emergency Services")
                )
            } else {
                _alertEvents.tryEmit(
                    AlertEvent.StatusBanner(
                        if (pack == null) {
                            "SOS Beacon Active at $coordsText — use Dial or SMS below to alert Emergency Services"
                        } else {
                            "Offline: GPS queued. Use Dial (112/911/999) or SMS below over cellular!"
                        }
                    )
                )
            }
        }
    }

    fun standDownEmergencySos() {
        _isSosActive.value = false
        val pack = activePack.value
        val pinId = activeSosPinId
        activeSosPinId = null
        val myName = userProfile.value?.displayName?.ifBlank { "Pack Rider" } ?: "Pack Rider"

        viewModelScope.launch {
            if (pack != null && !isEffectiveOffline.value) {
                if (!pinId.isNullOrBlank()) {
                    repository.resolvePackMarker(pack.id, pinId)
                }
                repository.sendPackMessage(
                    pack = pack,
                    senderName = myName,
                    plainText = "SAFE UPDATE: $myName stood down the Emergency SOS beacon.",
                    messageType = "alert"
                )
            }
            _alertEvents.tryEmit(AlertEvent.StatusBanner("Emergency SOS beacon ended — marked safe"))
        }
    }

    private data class ParsedSosNote(val lat: Double?, val lng: Double?, val details: String)

    private fun parseSosPayload(raw: String): ParsedSosNote {
        if (!raw.startsWith("SOS|")) return ParsedSosNote(null, null, raw)
        val parts = raw.split("|", limit = 4)
        return if (parts.size >= 4) {
            ParsedSosNote(
                lat = parts[1].toDoubleOrNull(),
                lng = parts[2].toDoubleOrNull(),
                details = parts[3]
            )
        } else {
            ParsedSosNote(null, null, raw.removePrefix("SOS|"))
        }
    }

    private fun observeP2pMeshLifecycleAndPackets() {
        viewModelScope.launch {
            combine(activePack, settings) { pack, s ->
                pack to s.p2pMeshEnabled
            }.distinctUntilChanged().collect { (pack, p2pEnabled) ->
                if (p2pEnabled && pack != null) {
                    p2pMeshManager.startMesh(
                        packId = pack.id,
                        inviteCode = pack.inviteCode,
                        encryptionSalt = pack.encryptionSalt,
                        userId = currentUserId
                    )
                } else {
                    p2pMeshManager.stopMesh()
                }
            }
        }

        viewModelScope.launch {
            p2pMeshManager.incomingEvents.collect { event ->
                when (event) {
                    is MeshIncomingEvent.Telemetry -> {
                        val b = event.body
                        val fix = _myGpsFix.value
                        val distKm = MotoRouteAndWeatherRepository.haversineKm(fix.lat, fix.lng, b.lat, b.lng)
                        val peerState = DecryptedRiderState(
                            userId = b.userId,
                            displayName = b.displayName,
                            photoUrl = b.photoUrl,
                            bikeModel = b.bikeModel,
                            phoneNumber = b.phoneNumber,
                            lat = b.lat,
                            lng = b.lng,
                            speedKmh = b.speedKmh,
                            headingDeg = b.headingDeg,
                            altitudeM = b.altitudeM,
                            batteryPercent = b.batteryPercent,
                            isCharging = b.isCharging,
                            totalDistanceKm = b.totalDistanceKm,
                            elevationClimbedM = b.elevationClimbedM,
                            isOnline = true,
                            isOfflineQueued = false,
                            lastUpdatedMs = b.timestampMs,
                            distanceFromMeKm = distKm
                        )
                        _meshRidersMap.value = _meshRidersMap.value + (b.userId to peerState)
                    }
                    is MeshIncomingEvent.Message -> {
                        val b = event.body
                        val msg = DecryptedPackMessage(
                            id = b.messageId,
                            senderId = b.senderId,
                            senderName = "${b.senderName} (Mesh)",
                            recipientId = b.recipientId,
                            text = b.text,
                            messageType = b.messageType,
                            timestampMs = b.timestampMs,
                            audioBase64 = b.audioBase64,
                            audioCodec = b.audioCodec,
                            audioDurationMs = b.audioDurationMs,
                            audioSizeBytes = b.audioSizeBytes,
                            audioPeaks = if (b.messageType == "voice") PttVoiceEngine.parsePeaksCsv(b.audioPeaks) else emptyList()
                        )
                        _meshMessages.value = (_meshMessages.value + msg).takeLast(60)
                    }
                }
            }
        }
    }

    private fun observeDeadZoneAutoActivation() {
        viewModelScope.launch {
            combine(isEffectiveOffline, offlineMapAreas) { offline, areas ->
                offline to areas
            }.collect { (offline, areas) ->
                if (offline && _activeOfflineArea.value == null && areas.isNotEmpty()) {
                    selectOfflineMapArea(areas.first())
                }
            }
        }
    }

    private fun logStreamError(tag: String, err: Throwable) {
        val firestoreError = generateSequence(err) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && err is CancellationException) return
        Log.w("MotoPackVM", "$tag stream error: ${(firestoreError ?: err).message}", err)
    }

    // --- Biker Events, Biker & Brew Passport, Garage & AI Scenic Route Actions ---

    fun updateScenicPreferences(prefs: ScenicRoutePreferences) = features.updateScenicPreferences(prefs)

    fun cancelScenicRouteSuggestion() = features.cancelScenicSuggestions()

    fun suggestAiScenicRoutes() {
        features.suggestScenicRoutes(
            myGpsFix = _myGpsFix.value,
            currentWeather = _liveWeatherAlert.value,
            plannedRoute = _plannedRoute.value,
            bikeModel = garageBikes.value.firstOrNull { it.isPrimary }?.makeModel
                ?: userProfile.value?.bikeModel
                ?: "Adventure Tourer",
            isOffline = isEffectiveOffline.value
        )
    }

    fun stampPassportStop(location: BikerLocation, brewNote: String) {
        features.stampPassportStop(location, brewNote, _myGpsFix.value)
    }

    fun createBikerEvent(
        title: String,
        description: String,
        eventType: String,
        locationName: String,
        brewSpecialty: String,
        scheduledLabel: String,
        lat: Double,
        lng: Double,
        isPassportEvent: Boolean,
        locationId: String = ""
    ) {
        features.createBikerEvent(
            activePack = activePack.value,
            creatorName = userProfile.value?.displayName ?: "Rider",
            title = title,
            description = description,
            eventType = eventType,
            locationName = locationName,
            brewSpecialty = brewSpecialty,
            scheduledLabel = scheduledLabel,
            lat = lat,
            lng = lng,
            isPassportEvent = isPassportEvent,
            locationId = locationId
        )
    }

    fun toggleEventRsvp(event: BikerEvent) {
        features.toggleEventRsvp(event, activePack.value)
    }

    fun addGarageBike(
        name: String,
        makeModel: String,
        year: Int,
        currentOdometerKm: Double,
        engineCc: Int,
        tirePsiFront: Int,
        tirePsiRear: Int
    ) {
        features.addGarageBike(
            name = name,
            makeModel = makeModel,
            year = year,
            currentOdometerKm = currentOdometerKm,
            engineCc = engineCc,
            tirePsiFront = tirePsiFront,
            tirePsiRear = tirePsiRear
        )
    }

    fun updateBikeOdometer(bikeId: String, newOdometerKm: Double) {
        features.updateBikeOdometer(bikeId, newOdometerKm)
    }

    fun addMaintenanceReminder(
        bike: GarageBike,
        taskType: MaintenanceTaskType,
        title: String,
        intervalKm: Double,
        notes: String
    ) {
        features.addMaintenanceReminder(bike, taskType, title, intervalKm, notes)
    }

    fun completeServiceAndLogHistory(
        bike: GarageBike,
        reminder: MaintenanceReminderItem?,
        taskType: MaintenanceTaskType,
        title: String,
        odometerKm: Double,
        costAmount: Double,
        shopOrDiy: String,
        notes: String
    ) {
        features.completeServiceAndLogHistory(
            bike = bike,
            reminder = reminder,
            taskType = taskType,
            title = title,
            odometerKm = odometerKm,
            costAmount = costAmount,
            shopOrDiy = shopOrDiy,
            notes = notes
        )
    }

    fun deleteMaintenanceReminder(itemId: String) = features.deleteMaintenanceReminder(itemId)

    fun deleteServiceLog(logId: String) = features.deleteServiceLog(logId)

    override fun onCleared() {
        pttVoiceEngine.release()
        p2pMeshManager.release()
        super.onCleared()
    }
}
