package com.example.ai

import android.util.Log
import com.example.data.remote.RiderLiveWeatherAlert
import com.example.location.LiveGpsFix
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val SCENIC_TAG = "ScenicRouteSuggester"

enum class ScenicStylePreference(val label: String, val promptFocus: String) {
    TWISTIES("Twisty Passes", "technical sweepers, hairpins, mountain switchbacks"),
    SCENIC_LOOPS("Scenic Views", "panoramic moorland ridges, valleys, dams, photo overlooks"),
    CAFE_CRUISE("Café & Brews", "smooth flowing B-roads pairing curves with motorcycle cafés"),
    SUNSET_FLOW("Smooth Flow", "relaxed elevation rollers with wide sightlines and low congestion")
}

data class ScenicRoutePreferences(
    val style: ScenicStylePreference = ScenicStylePreference.TWISTIES,
    val maxDistanceKm: Int = 90,
    val avoidMotorways: Boolean = true,
    val prioritizeDryWeather: Boolean = true
)

data class ScenicRouteRecommendation(
    val routeName: String,
    val destinationLabel: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val curvinessRating: String, // "EXTREME", "SPORTY", "FLOWING"
    val weatherFit: String,      // "DRY_OPTIMAL", "FAIR_BREEZY", "CAUTION_WET"
    val trafficOutlook: String,
    val roadHighlights: String,
    val brewOrFuelStop: String,
    val whyRecommended: String
)

data class ScenicSuggestionBundle(
    val analysisSummary: String,
    val recommendations: List<ScenicRouteRecommendation>
)

sealed interface ScenicRouteUiState {
    data object Idle : ScenicRouteUiState
    data object Loading : ScenicRouteUiState
    data class Success(val bundle: ScenicSuggestionBundle) : ScenicRouteUiState
    data class Error(val message: String) : ScenicRouteUiState
}

class ScenicRouteSuggesterService {

    private data class CachedScenicEntry(
        val bundle: ScenicSuggestionBundle,
        val cachedAtMs: Long
    )

    private val suggestionCache = ConcurrentHashMap<String, CachedScenicEntry>()

    private val scenicSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "analysisSummary" to Schema.string(
                    description = "1 concise sentence summarizing local weather, wind, and traffic."
                ),
                "recommendations" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "routeName" to Schema.string(description = "Motorcycle route name"),
                            "destinationLabel" to Schema.string(description = "Road pass or town name"),
                            "destinationLat" to Schema.double(description = "Destination latitude"),
                            "destinationLng" to Schema.double(description = "Destination longitude"),
                            "distanceKm" to Schema.double(description = "Distance km"),
                            "estimatedMinutes" to Schema.integer(description = "Duration minutes"),
                            "curvinessRating" to Schema.enumeration(
                                values = listOf("EXTREME", "SPORTY", "FLOWING"),
                                description = "Cornering rating"
                            ),
                            "weatherFit" to Schema.enumeration(
                                values = listOf("DRY_OPTIMAL", "FAIR_BREEZY", "CAUTION_WET"),
                                description = "Weather grip fit"
                            ),
                            "trafficOutlook" to Schema.string(description = "Short traffic note"),
                            "roadHighlights" to Schema.string(description = "Key road bends/elevation"),
                            "brewOrFuelStop" to Schema.string(description = "Coffee or fuel stop"),
                            "whyRecommended" to Schema.string(description = "Short 1-sentence reason")
                        )
                    ),
                    description = "Exactly 3 motorcycle route recommendations"
                )
            )
        )
    }

    private val scenicModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = content {
                text(
                    "You are a fast motorcycle route architect. " +
                        "Suggest 3 real scenic road loops/passes near the rider's coordinates weighing wind/rain grip, traffic delays, style, and motorway avoidance. Keep descriptions concise."
                )
            },
            generationConfig = generationConfig {
                temperature = 0.35f
                maxOutputTokens = 768
                responseMimeType = "application/json"
                responseSchema = scenicSchema
            }
        )
    }

    suspend fun suggestScenicRoutes(
        myGpsFix: LiveGpsFix,
        weather: RiderLiveWeatherAlert?,
        trafficDelayMin: Int,
        preferences: ScenicRoutePreferences,
        bikeModel: String,
        nearbyBrewStopsSummary: String
    ): ScenicSuggestionBundle {
        val cacheKey = String.format(
            Locale.US,
            "%.2f,%.2f|%s|%d|%b|%b",
            myGpsFix.lat,
            myGpsFix.lng,
            preferences.style.name,
            preferences.maxDistanceKm,
            preferences.avoidMotorways,
            preferences.prioritizeDryWeather
        )
        val nowMs = System.currentTimeMillis()
        suggestionCache[cacheKey]?.let { entry ->
            if ((nowMs - entry.cachedAtMs) <= SCENIC_CACHE_TTL_MS) {
                return entry.bundle
            }
        }

        val prompt = buildString {
            appendLine("Suggest 3 motorcycle routes near lat=${String.format(Locale.US, "%.3f", myGpsFix.lat)}, lng=${String.format(Locale.US, "%.3f", myGpsFix.lng)}.")
            appendLine("Bike: ${bikeModel.ifBlank { "Sport Tourer" }} | Style: ${preferences.style.label} (${preferences.style.promptFocus})")
            appendLine("MaxDistanceKm: ${preferences.maxDistanceKm} | AvoidMotorways: ${preferences.avoidMotorways} | PreferDry: ${preferences.prioritizeDryWeather}")
            if (weather != null) {
                appendLine("Weather: ${weather.severity} ${weather.tempCelsius.toInt()}C wind=${weather.windSpeedKmh.toInt()}km/h gusts=${weather.windGustKmh.toInt()}km/h rain=${String.format(Locale.US, "%.1f", weather.rainMmPerHour)}mm/h")
            }
            appendLine("TrafficDelayMin: $trafficDelayMin")
            if (nearbyBrewStopsSummary.isNotBlank()) {
                appendLine("NearbyStops: $nearbyBrewStopsSummary")
            }
        }

        val response = withTimeout(30_000L) {
            scenicModel.generateContent(prompt)
        }
        val rawJson = response.text
            ?: throw IllegalStateException("AI Scenic Route model returned an empty response.")
        val bundle = parseScenicJson(rawJson, myGpsFix)
        suggestionCache[cacheKey] = CachedScenicEntry(bundle, nowMs)
        return bundle
    }

    companion object {
        private const val SCENIC_CACHE_TTL_MS = 3L * 60L * 1000L

        fun parseScenicJson(rawJson: String, anchorGps: LiveGpsFix): ScenicSuggestionBundle {
            val cleaned = rawJson.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val root = JSONObject(cleaned)
            val summary = root.optString("analysisSummary", "Analyzed local weather, road curves, and traffic.")
            val arr = root.optJSONArray("recommendations") ?: JSONArray()
            val recs = (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val lat = obj.optDouble("destinationLat", anchorGps.lat + 0.08).coerceIn(-85.0, 85.0)
                val lng = obj.optDouble("destinationLng", anchorGps.lng - 0.12).coerceIn(-180.0, 180.0)
                val name = obj.optString("routeName", "Scenic Ridge Loop").ifBlank { "Scenic Ridge Loop" }
                ScenicRouteRecommendation(
                    routeName = name,
                    destinationLabel = obj.optString("destinationLabel", name).ifBlank { name },
                    destinationLat = lat,
                    destinationLng = lng,
                    distanceKm = obj.optDouble("distanceKm", 48.0).coerceIn(5.0, 400.0),
                    estimatedMinutes = obj.optInt("estimatedMinutes", 55).coerceIn(10, 480),
                    curvinessRating = obj.optString("curvinessRating", "SPORTY"),
                    weatherFit = obj.optString("weatherFit", "DRY_OPTIMAL"),
                    trafficOutlook = obj.optString("trafficOutlook", "Clear country tarmac"),
                    roadHighlights = obj.optString("roadHighlights", "Sweeping bends and valley overlook"),
                    brewOrFuelStop = obj.optString("brewOrFuelStop", "Roadside espresso & fuel check"),
                    whyRecommended = obj.optString("whyRecommended", "Balanced grip, elevation, and low traffic.")
                )
            }
            if (recs.isEmpty()) {
                throw IllegalStateException("AI returned zero route recommendations.")
            }
            return ScenicSuggestionBundle(
                analysisSummary = summary,
                recommendations = recs
            )
        }

        fun formatErrorMessage(e: Throwable): String = when (e) {
            is TimeoutCancellationException -> "AI Scenic Suggester timed out after 30s. Check connection and retry."
            is CancellationException -> throw e
            else -> {
                Log.e(SCENIC_TAG, "Scenic route generation failed", e)
                e.localizedMessage ?: "Unable to generate AI scenic routes right now."
            }
        }
    }
}
