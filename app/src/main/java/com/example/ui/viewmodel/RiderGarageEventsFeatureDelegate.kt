package com.example.ui.viewmodel

import com.example.ai.ScenicRoutePreferences
import com.example.ai.ScenicRouteSuggesterService
import com.example.ai.ScenicRouteUiState
import com.example.data.model.BikerBrewCatalog
import com.example.data.model.BikerEvent
import com.example.data.model.BikerLocation
import com.example.data.model.GarageBike
import com.example.data.model.MaintenanceReminderItem
import com.example.data.model.MaintenanceTaskType
import com.example.data.model.PassportStamp
import com.example.data.model.RidePack
import com.example.data.model.ServiceHistoryLog
import com.example.data.remote.ComputedMotoRoute
import com.example.data.remote.OpenWeatherRepository
import com.example.data.remote.RiderLiveWeatherAlert
import com.example.data.repository.BikerEventsRepository
import com.example.data.repository.GarageRepository
import com.example.location.LiveGpsFix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
class RiderGarageEventsFeatureDelegate(
    private val scope: CoroutineScope,
    private val currentUserId: String,
    private val eventsRepository: BikerEventsRepository,
    private val garageRepository: GarageRepository,
    private val scenicService: ScenicRouteSuggesterService,
    private val weatherRepository: OpenWeatherRepository,
    activePackFlow: StateFlow<RidePack?>,
    private val emitBanner: (String, Boolean) -> Unit
) {
    val passportStamps: StateFlow<List<PassportStamp>> = eventsRepository.observePassportStamps(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val personalEvents: StateFlow<List<BikerEvent>> = eventsRepository.observePersonalEvents(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val packEvents: StateFlow<List<BikerEvent>> = activePackFlow
        .flatMapLatest { pack ->
            if (pack == null) flowOf(emptyList())
            else eventsRepository.observePackEvents(pack.id, currentUserId).catch { emit(emptyList()) }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val garageBikes: StateFlow<List<GarageBike>> = garageRepository.observeBikes(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val maintenanceItems: StateFlow<List<MaintenanceReminderItem>> = garageRepository.observeMaintenanceItems(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val serviceLogs: StateFlow<List<ServiceHistoryLog>> = garageRepository.observeServiceLogs(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _scenicPreferences = MutableStateFlow(ScenicRoutePreferences())
    val scenicPreferences: StateFlow<ScenicRoutePreferences> = _scenicPreferences.asStateFlow()

    private val _scenicRouteState = MutableStateFlow<ScenicRouteUiState>(ScenicRouteUiState.Idle)
    val scenicRouteState: StateFlow<ScenicRouteUiState> = _scenicRouteState.asStateFlow()

    private var scenicSuggestionJob: Job? = null

    fun updateScenicPreferences(prefs: ScenicRoutePreferences) {
        _scenicPreferences.value = prefs
    }

    fun cancelScenicSuggestions() {
        scenicSuggestionJob?.cancel()
        scenicSuggestionJob = null
        if (_scenicRouteState.value is ScenicRouteUiState.Loading) {
            _scenicRouteState.value = ScenicRouteUiState.Idle
        }
    }

    fun suggestScenicRoutes(
        myGpsFix: LiveGpsFix,
        currentWeather: RiderLiveWeatherAlert?,
        plannedRoute: ComputedMotoRoute?,
        bikeModel: String,
        isOffline: Boolean
    ) {
        if (isOffline) {
            _scenicRouteState.value = ScenicRouteUiState.Error(
                "Go online to query Gemini AI Scenic Route Suggester with live weather & traffic."
            )
            return
        }
        scenicSuggestionJob?.cancel()
        scenicSuggestionJob = scope.launch {
            _scenicRouteState.value = ScenicRouteUiState.Loading
            try {
                // Never stall AI road analysis on a slow weather endpoint — cap pre-fetch to 2s
                val freshWeather = currentWeather ?: withTimeoutOrNull(2_000L) {
                    runCatching {
                        weatherRepository.fetchRiderWeatherAlert(myGpsFix.lat, myGpsFix.lng)
                    }.getOrNull()
                }
                val brewSummary = BikerBrewCatalog.passportStops.take(4).joinToString("; ") {
                    "${it.name} (${it.cityRegion}, lat=${String.format("%.3f", it.lat)}, lng=${String.format("%.3f", it.lng)})"
                }
                val bundle = scenicService.suggestScenicRoutes(
                    myGpsFix = myGpsFix,
                    weather = freshWeather,
                    trafficDelayMin = plannedRoute?.trafficDelayMin ?: 0,
                    preferences = _scenicPreferences.value,
                    bikeModel = bikeModel,
                    nearbyBrewStopsSummary = brewSummary
                )
                _scenicRouteState.value = ScenicRouteUiState.Success(bundle)
            } catch (_: CancellationException) {
                if (_scenicRouteState.value is ScenicRouteUiState.Loading) {
                    _scenicRouteState.value = ScenicRouteUiState.Idle
                }
            } catch (e: Throwable) {
                val msg = ScenicRouteSuggesterService.formatErrorMessage(e)
                _scenicRouteState.value = ScenicRouteUiState.Error(msg)
            }
        }
    }

    fun stampPassportStop(
        location: BikerLocation,
        brewNote: String,
        currentGpsFix: LiveGpsFix
    ) {
        scope.launch {
            val distKm = BikerBrewCatalog.distanceKmTo(
                currentGpsFix.lat,
                currentGpsFix.lng,
                location.lat,
                location.lng
            )
            val verified = distKm <= BikerBrewCatalog.CHECK_IN_RADIUS_KM
            eventsRepository.stampPassportStop(location, brewNote, verified)
                .onSuccess {
                    val tag = if (verified) "GPS Verified" else "Check-in Logged"
                    emitBanner("Passport Stamped (${location.passportStampCode}): ${location.name} [$tag]", false)
                }
                .onFailure {
                    emitBanner(it.localizedMessage ?: "Failed to save Passport stamp", true)
                }
        }
    }

    fun createBikerEvent(
        activePack: RidePack?,
        creatorName: String,
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
    ) {
        scope.launch {
            eventsRepository.createBikerEvent(
                activePack = activePack,
                creatorName = creatorName,
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
            ).onSuccess {
                emitBanner("Published event: $title", false)
            }.onFailure {
                emitBanner(it.localizedMessage ?: "Failed to create event", true)
            }
        }
    }

    fun toggleEventRsvp(event: BikerEvent, activePack: RidePack?) {
        scope.launch {
            eventsRepository.toggleEventRsvp(event, activePack)
                .onSuccess { joined ->
                    emitBanner(
                        if (joined) "RSVP'd Going: ${event.title}" else "Cancelled RSVP: ${event.title}",
                        false
                    )
                }
                .onFailure {
                    emitBanner(it.localizedMessage ?: "Unable to update RSVP", true)
                }
        }
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
        scope.launch {
            val isFirst = garageBikes.value.isEmpty()
            garageRepository.addBikeWithStarterSchedule(
                name = name,
                makeModel = makeModel,
                year = year,
                currentOdometerKm = currentOdometerKm,
                engineCc = engineCc,
                tirePsiFront = tirePsiFront,
                tirePsiRear = tirePsiRear,
                isPrimary = isFirst
            ).onSuccess { bike ->
                emitBanner("Added ${bike.name} + starter Oil, Chain & Tire reminders", false)
            }.onFailure {
                emitBanner(it.localizedMessage ?: "Failed to add motorcycle", true)
            }
        }
    }

    fun updateBikeOdometer(bikeId: String, newOdometerKm: Double) {
        scope.launch {
            garageRepository.updateBikeOdometer(bikeId, newOdometerKm)
                .onSuccess {
                    emitBanner("Updated odometer to ${newOdometerKm.toInt()} km", false)
                }
                .onFailure {
                    emitBanner(it.localizedMessage ?: "Failed to update odometer", true)
                }
        }
    }

    fun onRideDistanceCompleted(distanceKm: Double) {
        if (distanceKm < 0.2) return
        val primary = garageBikes.value.firstOrNull { it.isPrimary } ?: garageBikes.value.firstOrNull() ?: return
        scope.launch {
            garageRepository.updateBikeOdometer(primary.id, primary.currentOdometerKm + distanceKm)
        }
    }

    fun addMaintenanceReminder(
        bike: GarageBike,
        taskType: MaintenanceTaskType,
        title: String,
        intervalKm: Double,
        notes: String
    ) {
        scope.launch {
            garageRepository.addMaintenanceReminder(bike, taskType, title, intervalKm, notes)
                .onSuccess {
                    emitBanner("Added reminder: $title every ${intervalKm.toInt()} km", false)
                }
                .onFailure {
                    emitBanner(it.localizedMessage ?: "Failed to add reminder", true)
                }
        }
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
        scope.launch {
            garageRepository.completeServiceAndLogHistory(
                bike = bike,
                reminder = reminder,
                taskType = taskType,
                title = title,
                odometerKm = odometerKm,
                costAmount = costAmount,
                shopOrDiy = shopOrDiy,
                notes = notes
            ).onSuccess {
                emitBanner("Logged service '$title' at ${odometerKm.toInt()} km", false)
            }.onFailure {
                emitBanner(it.localizedMessage ?: "Failed to log service", true)
            }
        }
    }

    fun deleteMaintenanceReminder(itemId: String) {
        scope.launch {
            garageRepository.deleteMaintenanceReminder(itemId)
        }
    }

    fun deleteServiceLog(logId: String) {
        scope.launch {
            garageRepository.deleteServiceLog(logId)
        }
    }
}
