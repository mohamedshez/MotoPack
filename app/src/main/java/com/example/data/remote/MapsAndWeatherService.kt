package com.example.data.remote

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.example.BuildConfig
import com.example.data.model.BikerBrewCatalog
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// --- Domain Output Models ---

@Serializable
data class ElevationSample(
    val distanceKm: Double,
    val elevationM: Double,
    val lat: Double,
    val lng: Double
)

@Serializable
data class FuelStationStop(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val distanceAlongKm: Double,
    val isOpenNow: Boolean? = null
)

@Serializable
data class RouteWeatherWarning(
    val locationLabel: String,
    val severity: String, // "ADVISORY", "WARNING", "CLEAR"
    val headline: String,
    val tempCelsius: Double,
    val windKmh: Double,
    val gustKmh: Double,
    val rainProbPercent: Int
)

@Serializable
data class RouteNavigationStep(
    val instruction: String,
    val maneuver: String,
    val distanceMeters: Int,
    val durationSec: Int,
    val endLat: Double,
    val endLng: Double
)

data class ComputedMotoRoute(
    val routeName: String,
    val originLabel: String,
    val destinationLabel: String,
    val points: List<LatLng>,
    val encodedPolyline: String,
    val distanceKm: Double,
    val durationMin: Int,
    val trafficDelayMin: Int,
    val elevationSamples: List<ElevationSample>,
    val maxElevationM: Double,
    val totalAscentM: Double,
    val fuelStations: List<FuelStationStop>,
    val weatherWarnings: List<RouteWeatherWarning>,
    val navigationSteps: List<RouteNavigationStep> = emptyList(),
    val isLiveGoogleData: Boolean,
    val apiNotice: String? = null
)

data class TrafficEtaResult(
    val etaMinutes: Int,
    val distanceKm: Double,
    val trafficLabel: String
)

data class OpenMeteoForecastPoint(
    val tempCelsius: Double,
    val windKmh: Double,
    val gustKmh: Double,
    val rainProbPercent: Int,
    val precipitationMm: Double,
    val wmoCode: Int
)

data class PlaceSearchSuggestion(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double,
    val isCuratedPass: Boolean = false
)

// --- Retrofit DTOs for Google Routes API ---

@JsonClass(generateAdapter = true)
data class ComputeRoutesRequest(
    val origin: RouteWaypoint,
    val destination: RouteWaypoint,
    val travelMode: String = "TWO_WHEELER",
    val routingPreference: String = "TRAFFIC_AWARE",
    val computeAlternativeRoutes: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RouteWaypoint(
    val location: RouteLocation? = null,
    val address: String? = null
)

@JsonClass(generateAdapter = true)
data class RouteLocation(
    val latLng: RouteLatLng
)

@JsonClass(generateAdapter = true)
data class RouteLatLng(
    val latitude: Double,
    val longitude: Double
)

@JsonClass(generateAdapter = true)
data class ComputeRoutesResponse(
    val routes: List<RouteDto>? = null
)

@JsonClass(generateAdapter = true)
data class RouteDto(
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val staticDuration: String? = null,
    val polyline: RoutePolylineDto? = null,
    val legs: List<RouteLegDto>? = null
)

@JsonClass(generateAdapter = true)
data class RoutePolylineDto(
    val encodedPolyline: String? = null
)

@JsonClass(generateAdapter = true)
data class RouteLegDto(
    val steps: List<RouteStepDto>? = null
)

@JsonClass(generateAdapter = true)
data class RouteStepDto(
    val distanceMeters: Int? = null,
    val staticDuration: String? = null,
    val navigationInstruction: NavigationInstructionDto? = null,
    val endLocation: RouteLocation? = null
)

@JsonClass(generateAdapter = true)
data class NavigationInstructionDto(
    val maneuver: String? = null,
    val instructions: String? = null
)

// --- Retrofit DTOs for Google Elevation API ---

@JsonClass(generateAdapter = true)
data class GoogleElevationResponse(
    val results: List<GoogleElevationPoint>? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleElevationPoint(
    val elevation: Double = 0.0,
    val location: GoogleLatLngLiteral? = null
)

@JsonClass(generateAdapter = true)
data class GoogleLatLngLiteral(
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

// --- Retrofit DTOs for Google Places API (New) ---

@JsonClass(generateAdapter = true)
data class PlacesSearchRequest(
    val textQuery: String,
    val locationBias: PlacesLocationBias,
    val maxResultCount: Int = 6
)

@JsonClass(generateAdapter = true)
data class PlacesLocationBias(
    val circle: PlacesCircle
)

@JsonClass(generateAdapter = true)
data class PlacesCircle(
    val center: RouteLatLng,
    val radius: Double
)

@JsonClass(generateAdapter = true)
data class PlacesSearchResponse(
    val places: List<PlaceResultDto>? = null
)

@JsonClass(generateAdapter = true)
data class PlaceResultDto(
    val id: String? = null,
    val displayName: LocalizedTextDto? = null,
    val formattedAddress: String? = null,
    val location: RouteLatLng? = null,
    val currentOpeningHours: OpeningHoursDto? = null
)

@JsonClass(generateAdapter = true)
data class LocalizedTextDto(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class OpeningHoursDto(
    val openNow: Boolean? = null
)

// --- Retrofit DTOs for Open-Meteo (Weather + Open Elevation Fallback) ---

@JsonClass(generateAdapter = true)
data class OpenMeteoForecastResponse(
    val current: OpenMeteoCurrentDto? = null,
    val hourly: OpenMeteoHourlyDto? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoCurrentDto(
    val temperature_2m: Double? = null,
    val wind_speed_10m: Double? = null,
    val wind_gusts_10m: Double? = null,
    val weather_code: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourlyDto(
    val precipitation_probability: List<Int>? = null,
    val precipitation: List<Double>? = null,
    val wind_speed_10m: List<Double>? = null,
    val wind_gusts_10m: List<Double>? = null,
    val weather_code: List<Int>? = null,
    val temperature_2m: List<Double>? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoElevationResponse(
    val elevation: List<Double>? = null
)

// --- Retrofit Interfaces ---

interface GoogleRoutesApi {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String,
        @Body request: ComputeRoutesRequest
    ): Response<ComputeRoutesResponse>
}

interface GoogleMapsWebApi {
    @GET("maps/api/elevation/json")
    suspend fun getElevationPath(
        @Query("path") path: String,
        @Query("samples") samples: Int,
        @Query("key") apiKey: String
    ): Response<GoogleElevationResponse>
}

interface GooglePlacesApi {
    @POST("v1/places:searchText")
    suspend fun searchFuelStations(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String,
        @Body request: PlacesSearchRequest
    ): Response<PlacesSearchResponse>
}

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,wind_speed_10m,wind_gusts_10m,weather_code",
        @Query("hourly") hourly: String = "precipitation_probability,precipitation,wind_speed_10m,wind_gusts_10m,weather_code,temperature_2m",
        @Query("forecast_days") forecastDays: Int = 1
    ): Response<OpenMeteoForecastResponse>

    @GET("v1/elevation")
    suspend fun getElevation(
        @Query("latitude") latitudes: String,
        @Query("longitude") longitudes: String
    ): Response<OpenMeteoElevationResponse>
}

class MotoRouteAndWeatherRepository {
    private data class CachedForecastEntry(
        val response: OpenMeteoForecastResponse,
        val cachedAtMs: Long
    )

    private val forecastCache = ConcurrentHashMap<String, CachedForecastEntry>()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val routesApi: GoogleRoutesApi = Retrofit.Builder()
        .baseUrl("https://routes.googleapis.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GoogleRoutesApi::class.java)

    private val mapsWebApi: GoogleMapsWebApi = Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GoogleMapsWebApi::class.java)

    private val placesApi: GooglePlacesApi = Retrofit.Builder()
        .baseUrl("https://places.googleapis.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GooglePlacesApi::class.java)

    private val openMeteoApi: OpenMeteoApi = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoApi::class.java)

    private fun validMapsApiKey(): String? {
        val key = BuildConfig.MAPS_API_KEY
        return if (key.isBlank() || key == "MY_GOOGLE_MAPS_API_KEY") null else key
    }

    suspend fun searchPlacesForOfflineArea(
        query: String,
        biasCenter: LatLng,
        context: Context? = null
    ): List<PlaceSearchSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val curatedRegions = curatedOfflineRidingRegions() + BikerBrewCatalog.curatedLocations.map {
            PlaceSearchSuggestion(
                name = it.name,
                subtitle = "${it.cityRegion} • Biker & Brew Hub",
                lat = it.lat,
                lng = it.lng,
                isCuratedPass = true
            )
        }

        if (trimmed.isBlank()) {
            return@withContext curatedRegions.take(6)
        }

        val qLower = trimmed.lowercase(Locale.ROOT)
        val curatedMatches = curatedRegions.filter {
            it.name.lowercase(Locale.ROOT).contains(qLower) ||
                it.subtitle.lowercase(Locale.ROOT).contains(qLower)
        }

        val results = LinkedHashMap<String, PlaceSearchSuggestion>()
        curatedMatches.forEach { item ->
            results[String.format(Locale.US, "%.2f,%.2f", item.lat, item.lng)] = item
        }

        val apiKey = validMapsApiKey()
        if (apiKey != null) {
            withTimeoutOrNull(3_000L) {
                try {
                    val res = placesApi.searchFuelStations(
                        apiKey = apiKey,
                        fieldMask = "places.id,places.displayName,places.formattedAddress,places.location",
                        request = PlacesSearchRequest(
                            textQuery = trimmed,
                            locationBias = PlacesLocationBias(
                                circle = PlacesCircle(
                                    center = RouteLatLng(biasCenter.latitude, biasCenter.longitude),
                                    radius = 50000.0
                                )
                            ),
                            maxResultCount = 6
                        )
                    )
                    res.body()?.places.orEmpty().forEach { place ->
                        val loc = place.location ?: return@forEach
                        val name = place.displayName?.text?.ifBlank { null } ?: trimmed
                        val subtitle = place.formattedAddress ?: "Searched Location"
                        val key = String.format(Locale.US, "%.2f,%.2f", loc.latitude, loc.longitude)
                        results.putIfAbsent(
                            key,
                            PlaceSearchSuggestion(name = name, subtitle = subtitle, lat = loc.latitude, lng = loc.longitude)
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (context != null && results.size < 5) {
            withTimeoutOrNull(2_800L) {
                geocodePlaces(context, trimmed).forEach { geo ->
                    val key = String.format(Locale.US, "%.2f,%.2f", geo.lat, geo.lng)
                    results.putIfAbsent(key, geo)
                }
            }
        }

        results.values.toList()
    }

    @Suppress("DEPRECATION")
    private suspend fun geocodePlaces(context: Context, query: String): List<PlaceSearchSuggestion> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocationName(query, 5) { addresses ->
                        val mapped = addresses.mapNotNull { addr ->
                            val title = addr.featureName ?: addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: return@mapNotNull null
                            val sub = listOfNotNull(addr.locality, addr.adminArea, addr.countryName)
                                .distinct()
                                .joinToString(", ")
                                .ifBlank { String.format(Locale.US, "%.4f, %.4f", addr.latitude, addr.longitude) }
                            PlaceSearchSuggestion(name = title, subtitle = sub, lat = addr.latitude, lng = addr.longitude)
                        }
                        if (cont.isActive) cont.resume(mapped)
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            }
        } else {
            runCatching {
                geocoder.getFromLocationName(query, 5).orEmpty().mapNotNull { addr ->
                    val title = addr.featureName ?: addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: return@mapNotNull null
                    val sub = listOfNotNull(addr.locality, addr.adminArea, addr.countryName)
                        .distinct()
                        .joinToString(", ")
                        .ifBlank { String.format(Locale.US, "%.4f, %.4f", addr.latitude, addr.longitude) }
                    PlaceSearchSuggestion(name = title, subtitle = sub, lat = addr.latitude, lng = addr.longitude)
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun curatedOfflineRidingRegions(): List<PlaceSearchSuggestion> = listOf(
        PlaceSearchSuggestion("Snake Pass Summit (A57)", "Peak District National Park, UK", 53.4339, -1.8732, true),
        PlaceSearchSuggestion("Cat & Fiddle Pass (A537)", "Buxton & Macclesfield Ridge, UK", 53.2435, -1.9964, true),
        PlaceSearchSuggestion("Matlock Bath Gorge", "Derbyshire Rider Strip, UK", 53.1225, -1.5594, true),
        PlaceSearchSuggestion("Kirkstone Pass", "Lake District National Park, UK", 54.4648, -2.9235, true),
        PlaceSearchSuggestion("Llanberis Pass (Eryri)", "Snowdonia National Park, Wales", 53.0853, -4.0226, true),
        PlaceSearchSuggestion("Buttertubs Pass", "Yorkshire Dales, UK", 54.3578, -2.2039, true),
        PlaceSearchSuggestion("Cotswolds Escarpment", "Gloucestershire Scenic Loop, UK", 51.8330, -1.8433, true),
        PlaceSearchSuggestion("Applecross Bealach na Bà", "Scottish Highlands NC500, UK", 57.4139, -5.7024, true)
    )

    suspend fun computeTrafficEta(origin: LatLng, destination: LatLng): TrafficEtaResult =
        withContext(Dispatchers.IO) {
            val straightKm = haversineKm(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
            val apiKey = validMapsApiKey()
            if (apiKey != null) {
                try {
                    val req = ComputeRoutesRequest(
                        origin = RouteWaypoint(location = RouteLocation(RouteLatLng(origin.latitude, origin.longitude))),
                        destination = RouteWaypoint(location = RouteLocation(RouteLatLng(destination.latitude, destination.longitude))),
                        travelMode = "DRIVE",
                        routingPreference = "TRAFFIC_AWARE"
                    )
                    val res = routesApi.computeRoutes(
                        apiKey = apiKey,
                        fieldMask = "routes.distanceMeters,routes.duration,routes.staticDuration",
                        request = req
                    )
                    val route = res.body()?.routes?.firstOrNull()
                    if (res.isSuccessful && route != null) {
                        val distKm = (route.distanceMeters ?: 0) / 1000.0
                        val durSec = parseDurationSeconds(route.duration)
                        val staticSec = parseDurationSeconds(route.staticDuration)
                        val delayMin = ((durSec - staticSec) / 60).coerceAtLeast(0)
                        val label = when {
                            delayMin >= 10 -> "Heavy traffic (+${delayMin}m)"
                            delayMin >= 3 -> "Moderate traffic (+${delayMin}m)"
                            else -> "Light traffic"
                        }
                        return@withContext TrafficEtaResult(
                            etaMinutes = (durSec / 60).coerceAtLeast(1),
                            distanceKm = distKm,
                            trafficLabel = label
                        )
                    }
                } catch (_: Exception) {
                }
            }
            val estRoadKm = straightKm * 1.25
            val estMin = ((estRoadKm / 62.0) * 60.0).toInt().coerceAtLeast(1)
            TrafficEtaResult(
                etaMinutes = estMin,
                distanceKm = estRoadKm,
                trafficLabel = if (apiKey == null) "Est. ETA (add MAPS_API_KEY for live traffic)" else "Estimated ETA"
            )
        }

    suspend fun planMotorcycleRoute(
        routeName: String,
        origin: LatLng,
        destinationLabel: String,
        destinationLatLng: LatLng? = null
    ): ComputedMotoRoute = withContext(Dispatchers.IO) {
        val apiKey = validMapsApiKey()
        var points: List<LatLng> = emptyList()
        var encodedPoly = ""
        var distKm = 0.0
        var durMin = 0
        var trafficDelayMin = 0
        var liveGoogle = false
        var apiNotice: String? = null
        var parsedSteps: List<RouteNavigationStep> = emptyList()

        if (apiKey != null) {
            try {
                val destWaypoint = if (destinationLatLng != null) {
                    RouteWaypoint(location = RouteLocation(RouteLatLng(destinationLatLng.latitude, destinationLatLng.longitude)))
                } else {
                    RouteWaypoint(address = destinationLabel)
                }
                val req = ComputeRoutesRequest(
                    origin = RouteWaypoint(location = RouteLocation(RouteLatLng(origin.latitude, origin.longitude))),
                    destination = destWaypoint,
                    travelMode = "DRIVE",
                    routingPreference = "TRAFFIC_AWARE"
                )
                val res = routesApi.computeRoutes(
                    apiKey = apiKey,
                    fieldMask = "routes.distanceMeters,routes.duration,routes.staticDuration,routes.polyline.encodedPolyline,routes.legs.steps.distanceMeters,routes.legs.steps.staticDuration,routes.legs.steps.navigationInstruction,routes.legs.steps.endLocation",
                    request = req
                )
                val route = res.body()?.routes?.firstOrNull()
                if (res.isSuccessful && route?.polyline?.encodedPolyline != null) {
                    encodedPoly = route.polyline.encodedPolyline
                    points = PolyUtil.decode(encodedPoly)
                    distKm = (route.distanceMeters ?: 0) / 1000.0
                    val durSec = parseDurationSeconds(route.duration)
                    val staticSec = parseDurationSeconds(route.staticDuration)
                    durMin = (durSec / 60).coerceAtLeast(1)
                    trafficDelayMin = ((durSec - staticSec) / 60).coerceAtLeast(0)
                    parsedSteps = route.legs
                        ?.flatMap { it.steps.orEmpty() }
                        ?.mapNotNull { step ->
                            val end = step.endLocation?.latLng ?: return@mapNotNull null
                            val instr = step.navigationInstruction?.instructions
                                ?.replace("\n", " ")
                                ?.trim()
                                .orEmpty()
                            if (instr.isBlank() && (step.distanceMeters ?: 0) <= 0) return@mapNotNull null
                            RouteNavigationStep(
                                instruction = instr.ifBlank { "Continue along route" },
                                maneuver = step.navigationInstruction?.maneuver ?: "STRAIGHT",
                                distanceMeters = (step.distanceMeters ?: 0).coerceAtLeast(10),
                                durationSec = parseDurationSeconds(step.staticDuration).coerceAtLeast(1),
                                endLat = end.latitude,
                                endLng = end.longitude
                            )
                        }
                        .orEmpty()
                    liveGoogle = true
                } else {
                    apiNotice = "Routes API error (${res.code()}). Using local corridor preview."
                }
            } catch (e: Exception) {
                apiNotice = "Offline or network error (${e.localizedMessage ?: "unreachable"}). Using local corridor."
            }
        } else {
            apiNotice = "Configure MAPS_API_KEY in AI Studio Secrets for live Google Routes, Elevation & Fuel Places."
        }

        if (points.isEmpty()) {
            val dest = destinationLatLng ?: resolveFallbackDestination(origin, destinationLabel)
            points = buildCurvedMotorcyclePath(origin, dest, steps = 16)
            encodedPoly = PolyUtil.encode(points)
            distKm = computePathDistanceKm(points)
            durMin = ((distKm / 58.0) * 60.0).toInt().coerceAtLeast(5)
        }

        val finalNavSteps = if (parsedSteps.isNotEmpty()) {
            parsedSteps
        } else {
            buildGeometricNavigationSteps(points, destinationLabel)
        }

        val sampledIndices = samplePointIndices(points, 12)
        val sampledPts = sampledIndices.map { points[it] }
        val midpoint = points[points.size / 2]
        val endPoint = points.last()

        val (elevationSamples, fuelStations, weatherWarnings) = coroutineScope {
            val elevDeferred = async {
                withTimeoutOrNull(5_500L) { fetchElevationProfile(sampledPts, distKm, apiKey) }
                    ?: buildSyntheticElevation(sampledPts, distKm)
            }
            val fuelDeferred = async {
                withTimeoutOrNull(5_500L) { fetchFuelStationsAlongRoute(points, midpoint, distKm, apiKey) }
                    ?: buildFallbackFuelStations(points, midpoint, distKm)
            }
            val wxDeferred = async {
                withTimeoutOrNull(5_500L) { fetchWeatherAlongRoute(origin, midpoint, endPoint, destinationLabel) }
                    ?: listOf(fallbackWeather("Start"), fallbackWeather("Midpoint / Pass"), fallbackWeather(destinationLabel.take(24)))
            }
            Triple(elevDeferred.await(), fuelDeferred.await(), wxDeferred.await())
        }
        val maxElev = elevationSamples.maxOfOrNull { it.elevationM } ?: 0.0
        val totalAscent = elevationSamples.zipWithNext { a, b -> (b.elevationM - a.elevationM).coerceAtLeast(0.0) }.sum()

        ComputedMotoRoute(
            routeName = routeName.ifBlank { destinationLabel },
            originLabel = String.format("%.3f, %.3f", origin.latitude, origin.longitude),
            destinationLabel = destinationLabel,
            points = points,
            encodedPolyline = encodedPoly,
            distanceKm = distKm,
            durationMin = durMin,
            trafficDelayMin = trafficDelayMin,
            elevationSamples = elevationSamples,
            maxElevationM = maxElev,
            totalAscentM = totalAscent,
            fuelStations = fuelStations,
            weatherWarnings = weatherWarnings,
            navigationSteps = finalNavSteps,
            isLiveGoogleData = liveGoogle,
            apiNotice = apiNotice
        )
    }

    suspend fun fetchSectorNavigationSnapshot(
        center: LatLng,
        radiusKm: Double,
        areaTitle: String
    ): ComputedMotoRoute = withContext(Dispatchers.IO) {
        val apiKey = validMapsApiKey()
        val latSpan = (radiusKm / 111.0).coerceIn(0.02, 0.45)
        val lngSpan = (radiusKm / (111.0 * cos(Math.toRadians(center.latitude)).coerceAtLeast(0.25))).coerceIn(0.02, 0.65)
        val sectorOrigin = LatLng(center.latitude - latSpan * 0.45, center.longitude - lngSpan * 0.55)
        val sectorDest = LatLng(center.latitude + latSpan * 0.45, center.longitude + lngSpan * 0.55)
        val points = buildCurvedMotorcyclePath(sectorOrigin, sectorDest, steps = 16)
        val encodedPoly = PolyUtil.encode(points)
        val distKm = computePathDistanceKm(points).coerceAtLeast(radiusKm)
        val durMin = ((distKm / 55.0) * 60.0).toInt().coerceAtLeast(6)

        val sampledIndices = samplePointIndices(points, 14)
        val sampledPts = sampledIndices.map { points[it] }
        val (elevationSamples, fuelStations, weatherWarnings) = coroutineScope {
            val elevDeferred = async {
                withTimeoutOrNull(5_500L) { fetchElevationProfile(sampledPts, distKm, apiKey) }
                    ?: buildSyntheticElevation(sampledPts, distKm)
            }
            val fuelDeferred = async {
                withTimeoutOrNull(5_500L) { fetchFuelStationsAlongRoute(points, center, distKm, apiKey) }
                    ?: buildFallbackFuelStations(points, center, distKm)
            }
            val wxDeferred = async {
                withTimeoutOrNull(5_500L) { fetchWeatherAlongRoute(sectorOrigin, center, sectorDest, areaTitle) }
                    ?: listOf(fallbackWeather("Start"), fallbackWeather("Midpoint / Pass"), fallbackWeather(areaTitle.take(24)))
            }
            Triple(elevDeferred.await(), fuelDeferred.await(), wxDeferred.await())
        }
        val maxElev = elevationSamples.maxOfOrNull { it.elevationM } ?: 0.0
        val totalAscent = elevationSamples.zipWithNext { a, b -> (b.elevationM - a.elevationM).coerceAtLeast(0.0) }.sum()
        val navSteps = buildGeometricNavigationSteps(points, areaTitle)

        ComputedMotoRoute(
            routeName = areaTitle.ifBlank { "Offline Sector" },
            originLabel = String.format("%.3f, %.3f", sectorOrigin.latitude, sectorOrigin.longitude),
            destinationLabel = "$areaTitle (${radiusKm.toInt()} km sector)",
            points = points,
            encodedPolyline = encodedPoly,
            distanceKm = distKm,
            durationMin = durMin,
            trafficDelayMin = 0,
            elevationSamples = elevationSamples,
            maxElevationM = maxElev,
            totalAscentM = totalAscent,
            fuelStations = fuelStations,
            weatherWarnings = weatherWarnings,
            navigationSteps = navSteps,
            isLiveGoogleData = apiKey != null,
            apiNotice = "Cached Sector Navigation Bundle (${navSteps.size} turns, ${fuelStations.size} fuel stops)"
        )
    }

    private suspend fun fetchElevationProfile(
        sampledPts: List<LatLng>,
        totalDistKm: Double,
        apiKey: String?
    ): List<ElevationSample> {
        if (sampledPts.isEmpty()) return emptyList()
        if (apiKey != null && sampledPts.size >= 2) {
            try {
                val pathParam = "${sampledPts.first().latitude},${sampledPts.first().longitude}|${sampledPts.last().latitude},${sampledPts.last().longitude}"
                val res = mapsWebApi.getElevationPath(path = pathParam, samples = 16, apiKey = apiKey)
                val results = res.body()?.results
                if (res.isSuccessful && !results.isNullOrEmpty()) {
                    return results.mapIndexed { idx, pt ->
                        val frac = idx.toDouble() / (results.size - 1).coerceAtLeast(1)
                        ElevationSample(
                            distanceKm = totalDistKm * frac,
                            elevationM = pt.elevation,
                            lat = pt.location?.lat ?: sampledPts.first().latitude,
                            lng = pt.location?.lng ?: sampledPts.first().longitude
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        // Fallback to live Open-Meteo Elevation API (free, no key required)
        try {
            val lats = sampledPts.joinToString(",") { String.format("%.5f", it.latitude) }
            val lngs = sampledPts.joinToString(",") { String.format("%.5f", it.longitude) }
            val res = openMeteoApi.getElevation(lats, lngs)
            val elevs = res.body()?.elevation
            if (res.isSuccessful && !elevs.isNullOrEmpty()) {
                return sampledPts.mapIndexed { idx, pt ->
                    val frac = idx.toDouble() / (sampledPts.size - 1).coerceAtLeast(1)
                    ElevationSample(
                        distanceKm = totalDistKm * frac,
                        elevationM = elevs.getOrElse(idx) { 120.0 },
                        lat = pt.latitude,
                        lng = pt.longitude
                    )
                }
            }
        } catch (_: Exception) {
        }
        return buildSyntheticElevation(sampledPts, totalDistKm)
    }

    private fun buildSyntheticElevation(sampledPts: List<LatLng>, totalDistKm: Double): List<ElevationSample> {
        return sampledPts.mapIndexed { idx, pt ->
            val frac = idx.toDouble() / (sampledPts.size - 1).coerceAtLeast(1)
            val hill = 110.0 + sin(frac * Math.PI) * 280.0
            ElevationSample(totalDistKm * frac, hill, pt.latitude, pt.longitude)
        }
    }

    private suspend fun fetchFuelStationsAlongRoute(
        points: List<LatLng>,
        midpoint: LatLng,
        totalDistKm: Double,
        apiKey: String?
    ): List<FuelStationStop> {
        if (apiKey != null) {
            try {
                val res = placesApi.searchFuelStations(
                    apiKey = apiKey,
                    fieldMask = "places.id,places.displayName,places.formattedAddress,places.location,places.currentOpeningHours.openNow",
                    request = PlacesSearchRequest(
                        textQuery = "petrol gas station fuel",
                        locationBias = PlacesLocationBias(
                            circle = PlacesCircle(
                                center = RouteLatLng(midpoint.latitude, midpoint.longitude),
                                radius = 25000.0
                            )
                        ),
                        maxResultCount = 6
                    )
                )
                val places = res.body()?.places
                if (res.isSuccessful && !places.isNullOrEmpty()) {
                    return places.mapNotNull { place ->
                        val loc = place.location ?: return@mapNotNull null
                        val start = points.firstOrNull() ?: midpoint
                        val distAlong = haversineKm(start.latitude, start.longitude, loc.latitude, loc.longitude)
                            .coerceAtMost(totalDistKm)
                        FuelStationStop(
                            id = place.id ?: "${loc.latitude}_${loc.longitude}",
                            name = place.displayName?.text ?: "Fuel Station",
                            address = place.formattedAddress ?: "Along mapped route",
                            lat = loc.latitude,
                            lng = loc.longitude,
                            distanceAlongKm = distAlong,
                            isOpenNow = place.currentOpeningHours?.openNow
                        )
                    }.sortedBy { it.distanceAlongKm }
                }
            } catch (_: Exception) {
            }
        }
        return buildFallbackFuelStations(points, midpoint, totalDistKm)
    }

    private fun buildFallbackFuelStations(
        points: List<LatLng>,
        midpoint: LatLng,
        totalDistKm: Double
    ): List<FuelStationStop> {
        val q1 = points.getOrElse((points.size * 0.25).toInt()) { midpoint }
        val q3 = points.getOrElse((points.size * 0.72).toInt()) { midpoint }
        return listOf(
            FuelStationStop(
                id = "fuel_1",
                name = "Corridor Fuel Stop (1/4 mark)",
                address = String.format("%.4f, %.4f", q1.latitude, q1.longitude),
                lat = q1.latitude,
                lng = q1.longitude,
                distanceAlongKm = totalDistKm * 0.25,
                isOpenNow = true
            ),
            FuelStationStop(
                id = "fuel_2",
                name = "Summit Fuel Stop (3/4 mark)",
                address = String.format("%.4f, %.4f", q3.latitude, q3.longitude),
                lat = q3.latitude,
                lng = q3.longitude,
                distanceAlongKm = totalDistKm * 0.72,
                isOpenNow = true
            )
        )
    }

    private suspend fun getCachedOrFetchOpenMeteo(lat: Double, lng: Double): OpenMeteoForecastResponse? {
        val key = String.format(Locale.US, "%.2f,%.2f", lat, lng)
        val nowMs = System.currentTimeMillis()
        forecastCache[key]?.let { cached ->
            if ((nowMs - cached.cachedAtMs) <= 3L * 60L * 1000L) {
                return cached.response
            }
        }
        val res = openMeteoApi.getForecast(lat, lng)
        val body = res.body()
        if (res.isSuccessful && body != null) {
            forecastCache[key] = CachedForecastEntry(body, nowMs)
            return body
        }
        return null
    }

    private suspend fun fetchWeatherAlongRoute(
        start: LatLng,
        midpoint: LatLng,
        end: LatLng,
        destinationLabel: String
    ): List<RouteWeatherWarning> = coroutineScope {
        val checkpoints = listOf(
            "Start" to start,
            "Midpoint / Pass" to midpoint,
            destinationLabel.take(24) to end
        )
        checkpoints.map { (label, pt) ->
            async {
                try {
                    val body = getCachedOrFetchOpenMeteo(pt.latitude, pt.longitude)
                    val cur = body?.current
                    val rainProb = body?.hourly?.precipitation_probability?.firstOrNull() ?: 0
                    if (cur != null) {
                        val temp = cur.temperature_2m ?: 14.0
                        val wind = cur.wind_speed_10m ?: 10.0
                        val gusts = cur.wind_gusts_10m ?: wind
                        val code = cur.weather_code ?: 0
                        val (severity, headline) = evaluateMotoWeatherHazard(temp, wind, gusts, rainProb, code)
                        RouteWeatherWarning(
                            locationLabel = label,
                            severity = severity,
                            headline = headline,
                            tempCelsius = temp,
                            windKmh = wind,
                            gustKmh = gusts,
                            rainProbPercent = rainProb
                        )
                    } else {
                        fallbackWeather(label)
                    }
                } catch (_: Exception) {
                    fallbackWeather(label)
                }
            }
        }.awaitAll()
    }

    private fun evaluateMotoWeatherHazard(
        tempC: Double,
        windKmh: Double,
        gustKmh: Double,
        rainProb: Int,
        wmoCode: Int
    ): Pair<String, String> {
        return when {
            wmoCode >= 95 -> "WARNING" to "Thunderstorm & lightning cell on route"
            tempC <= 3.0 -> "WARNING" to "Near-freezing surface (${tempC.toInt()}°C) — black ice risk"
            gustKmh >= 45.0 -> "WARNING" to "Severe crosswind gusts (${gustKmh.toInt()} km/h)"
            wmoCode in 51..86 || rainProb >= 60 -> "ADVISORY" to "Wet tarmac / rain likely ($rainProb%)"
            gustKmh >= 30.0 -> "ADVISORY" to "Moderate crosswinds (${gustKmh.toInt()} km/h gusts)"
            else -> "CLEAR" to "Clear riding conditions (${tempC.toInt()}°C, ${windKmh.toInt()} km/h wind)"
        }
    }

    suspend fun fetchCorridorForecastAtHourOffset(
        lat: Double,
        lng: Double,
        etaHourOffset: Int
    ): OpenMeteoForecastPoint? = withContext(Dispatchers.IO) {
        runCatching {
            val body = getCachedOrFetchOpenMeteo(lat, lng) ?: return@runCatching null
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val idx = (currentHour + etaHourOffset.coerceAtLeast(0)).coerceIn(0, 23)
            val hourly = body.hourly
            val cur = body.current

            val temp = hourly?.temperature_2m?.getOrNull(idx) ?: cur?.temperature_2m ?: 14.0
            val wind = hourly?.wind_speed_10m?.getOrNull(idx) ?: cur?.wind_speed_10m ?: 10.0
            val gusts = hourly?.wind_gusts_10m?.getOrNull(idx) ?: cur?.wind_gusts_10m ?: wind
            val rainProb = hourly?.precipitation_probability?.getOrNull(idx)
                ?: hourly?.precipitation_probability?.firstOrNull()
                ?: 0
            val precipMm = hourly?.precipitation?.getOrNull(idx) ?: 0.0
            val code = hourly?.weather_code?.getOrNull(idx) ?: cur?.weather_code ?: 0

            OpenMeteoForecastPoint(
                tempCelsius = temp,
                windKmh = wind,
                gustKmh = gusts,
                rainProbPercent = rainProb,
                precipitationMm = precipMm,
                wmoCode = code
            )
        }.getOrNull()
    }

    private fun fallbackWeather(label: String) = RouteWeatherWarning(
        locationLabel = label,
        severity = "ADVISORY",
        headline = "Offline — check horizon & crosswinds manually",
        tempCelsius = 14.0,
        windKmh = 12.0,
        gustKmh = 18.0,
        rainProbPercent = 10
    )

    private fun parseDurationSeconds(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return raw.removeSuffix("s").toDoubleOrNull()?.toInt() ?: 0
    }

    private fun resolveFallbackDestination(origin: LatLng, label: String): LatLng {
        val hash = label.hashCode()
        val dLat = ((hash % 40) / 100.0).let { if (it == 0.0) 0.22 else it }
        val dLng = (((hash / 40) % 40) / 100.0).let { if (it == 0.0) -0.28 else it }
        return LatLng(origin.latitude + dLat, origin.longitude + dLng)
    }

    private fun buildCurvedMotorcyclePath(start: LatLng, end: LatLng, steps: Int): List<LatLng> {
        return (0..steps).map { i ->
            val t = i.toDouble() / steps
            val bend = sin(t * Math.PI * 2.0) * 0.035
            LatLng(
                start.latitude + (end.latitude - start.latitude) * t + bend,
                start.longitude + (end.longitude - start.longitude) * t - bend * 0.7
            )
        }
    }

    private fun samplePointIndices(points: List<LatLng>, targetCount: Int): List<Int> {
        if (points.size <= targetCount) return points.indices.toList()
        return (0 until targetCount).map { i ->
            ((i.toDouble() / (targetCount - 1)) * (points.size - 1)).toInt()
        }.distinct()
    }

    private fun computePathDistanceKm(points: List<LatLng>): Double {
        return points.zipWithNext { a, b -> haversineKm(a.latitude, a.longitude, b.latitude, b.longitude) }.sum()
    }

    companion object {
        fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }

        fun bearingDegrees(from: LatLng, to: LatLng): Double {
            val lat1 = Math.toRadians(from.latitude)
            val lat2 = Math.toRadians(to.latitude)
            val dLon = Math.toRadians(to.longitude - from.longitude)
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0)
        }

        fun compassCardinal(bearingDeg: Double): String {
            val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val idx = (((bearingDeg + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)
            return dirs[idx]
        }

        fun buildGeometricNavigationSteps(
            points: List<LatLng>,
            destinationLabel: String
        ): List<RouteNavigationStep> {
            if (points.size < 2) return emptyList()
            val steps = mutableListOf<RouteNavigationStep>()
            var segStartIdx = 0
            var accMeters = 0.0

            for (i in 1 until points.size) {
                accMeters += haversineKm(
                    points[i - 1].latitude,
                    points[i - 1].longitude,
                    points[i].latitude,
                    points[i].longitude
                ) * 1000.0

                val isLast = i == points.lastIndex
                if (!isLast && i < points.lastIndex) {
                    val prevBearing = bearingDegrees(points[segStartIdx], points[i])
                    val nextBearing = bearingDegrees(points[i], points[i + 1])
                    val delta = ((nextBearing - prevBearing + 540.0) % 360.0) - 180.0
                    val absDelta = abs(delta)

                    // Group minor jitter and emit meaningful turns or periodic check-ins
                    if (absDelta >= 24.0 || accMeters >= 2400.0) {
                        val (maneuver, label) = when {
                            absDelta >= 120.0 && delta < 0 -> "TURN_SHARP_LEFT" to "Sharp left bend ahead"
                            absDelta >= 120.0 && delta > 0 -> "TURN_SHARP_RIGHT" to "Sharp right bend ahead"
                            absDelta >= 55.0 && delta < 0 -> "TURN_LEFT" to "Turn left along corridor"
                            absDelta >= 55.0 && delta > 0 -> "TURN_RIGHT" to "Turn right along corridor"
                            absDelta >= 24.0 && delta < 0 -> "TURN_SLIGHT_LEFT" to "Bear left into curve"
                            absDelta >= 24.0 && delta > 0 -> "TURN_SLIGHT_RIGHT" to "Bear right into curve"
                            else -> "STRAIGHT" to "Continue straight (${compassCardinal(nextBearing)})"
                        }
                        val distM = accMeters.toInt().coerceAtLeast(80)
                        steps += RouteNavigationStep(
                            instruction = label,
                            maneuver = maneuver,
                            distanceMeters = distM,
                            durationSec = (distM / 15).coerceAtLeast(10),
                            endLat = points[i].latitude,
                            endLng = points[i].longitude
                        )
                        segStartIdx = i
                        accMeters = 0.0
                    }
                } else if (isLast) {
                    val distM = accMeters.toInt().coerceAtLeast(80)
                    steps += RouteNavigationStep(
                        instruction = "Arrive at ${destinationLabel.ifBlank { "Destination" }}",
                        maneuver = "ARRIVE",
                        distanceMeters = distM,
                        durationSec = (distM / 14).coerceAtLeast(10),
                        endLat = points.last().latitude,
                        endLng = points.last().longitude
                    )
                }
            }
            return steps
        }
    }
}
