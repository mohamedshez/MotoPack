package com.example.ai

import android.util.Log
import com.example.data.model.DecryptedRiderState
import com.example.data.remote.ComputedMotoRoute
import com.example.data.remote.RiderLiveWeatherAlert
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "MotoAiCopilot"

data class AiSafetyBriefing(
    val riskLevel: String, // "LOW", "MODERATE", "HIGH"
    val headline: String,
    val tacticalAdvice: List<String>,
    val suggestedAction: String
)

sealed interface AiCopilotUiState {
    data object Idle : AiCopilotUiState
    data object Loading : AiCopilotUiState
    data class Streaming(val text: String) : AiCopilotUiState
    data class Success(val text: String) : AiCopilotUiState
    data class Error(val message: String) : AiCopilotUiState
}

sealed interface AiBriefingUiState {
    data object Idle : AiBriefingUiState
    data object Loading : AiBriefingUiState
    data class Success(val briefing: AiSafetyBriefing) : AiBriefingUiState
    data class Error(val message: String) : AiBriefingUiState
}

data class SmartHudCallout(
    val priority: String, // "INFO", "CAUTION", "CRITICAL"
    val headline: String,
    val spokenTip: String,
    val paceAction: String
)

sealed interface SmartHudUiState {
    data object Idle : SmartHudUiState
    data object Loading : SmartHudUiState
    data class Success(val callout: SmartHudCallout) : SmartHudUiState
    data class Error(val message: String) : SmartHudUiState
}

class MotoAiCopilotService {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are MotoPack Copilot, an expert motorcycle convoy strategist and safety advisor. " +
                        "Give concise, practical, road-tested advice for motorcycle riders on asphalt and mountain passes. " +
                        "Always factor in real-time weather (rain grip, hydroplaning, crosswind lean angles), rider spacing, " +
                        "fuel range, elevation climbs, and phone battery conservation."
                )
            }
        )
    }

    private val briefingSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "riskLevel" to Schema.enumeration(
                    values = listOf("LOW", "MODERATE", "HIGH"),
                    description = "Overall motorcycle riding risk right now"
                ),
                "headline" to Schema.string(
                    description = "One-sentence tactical summary of current pack and weather conditions"
                ),
                "tacticalAdvice" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 4 concise bullet recommendations for the riders"
                ),
                "suggestedAction" to Schema.string(
                    description = "Single immediate action for the pack leader"
                )
            )
        )
    }

    private val briefingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                maxOutputTokens = 512
                responseMimeType = "application/json"
                responseSchema = briefingSchema
            }
        )
    }

    private val hudCalloutSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "priority" to Schema.enumeration(
                    values = listOf("INFO", "CAUTION", "CRITICAL"),
                    description = "Urgency of the HUD callout based on turn sharpness, speed, weather, and pack status"
                ),
                "headline" to Schema.string(
                    description = "Ultra-short glanceable HUD alert headline (max 8 words)"
                ),
                "spokenTip" to Schema.string(
                    description = "Concise one-sentence voice intercom callout for a helmet speaker (max 22 words)"
                ),
                "paceAction" to Schema.enumeration(
                    values = listOf("HOLD PACE", "EASE THROTTLE", "FUEL SOON", "REGROUP PACK", "WET GRIP"),
                    description = "Immediate single action badge for the rider"
                )
            )
        )
    }

    private val hudCalloutModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.3f
                maxOutputTokens = 256
                responseMimeType = "application/json"
                responseSchema = hudCalloutSchema
            }
        )
    }

    suspend fun generateSafetyBriefing(
        packName: String?,
        weather: RiderLiveWeatherAlert?,
        riders: List<DecryptedRiderState>,
        plannedRoute: ComputedMotoRoute?,
        isOffline: Boolean
    ): AiSafetyBriefing {
        val prompt = buildContextPrompt(
            userQuestion = "Generate a structured motorcycle convoy safety & tactical briefing based on our live telemetry below.",
            packName = packName,
            weather = weather,
            riders = riders,
            plannedRoute = plannedRoute,
            isOffline = isOffline
        )

        val response = withTimeout(60_000L) {
            briefingModel.generateContent(prompt)
        }
        val rawJson = response.text
            ?: throw IllegalStateException("AI briefing model returned an empty response.")
        val json = JSONObject(rawJson)
        val adviceArr = json.getJSONArray("tacticalAdvice")
        val adviceList = (0 until adviceArr.length()).map { adviceArr.getString(it) }

        return AiSafetyBriefing(
            riskLevel = json.getString("riskLevel"),
            headline = json.getString("headline"),
            tacticalAdvice = adviceList,
            suggestedAction = json.getString("suggestedAction")
        )
    }

    suspend fun generateSmartHudCallout(
        speedKmh: Double,
        nextTurnInstruction: String?,
        nextTurnDistanceMeters: Int?,
        batteryPercent: Int,
        packName: String?,
        weather: RiderLiveWeatherAlert?,
        riders: List<DecryptedRiderState>,
        plannedRoute: ComputedMotoRoute?,
        isOffline: Boolean
    ): SmartHudCallout {
        val baseContext = buildContextPrompt(
            userQuestion = "Produce an immediate glanceable Rider HUD callout and brief helmet intercom spoken tip.",
            packName = packName,
            weather = weather,
            riders = riders,
            plannedRoute = plannedRoute,
            isOffline = isOffline
        )
        val hudSpecific = buildString {
            appendLine(baseContext)
            appendLine()
            appendLine("--- RIDER HUD LIVE SNAPSHOT ---")
            appendLine("Current GPS Speed: ${speedKmh.toInt()} km/h | Phone Battery: $batteryPercent%")
            if (!nextTurnInstruction.isNullOrBlank()) {
                appendLine("Upcoming Turn: $nextTurnInstruction in ${nextTurnDistanceMeters ?: 0} m")
            } else {
                appendLine("Active Turn-by-Turn Route: None selected")
            }
        }
        val response = withTimeout(30_000L) {
            hudCalloutModel.generateContent(hudSpecific)
        }
        val rawJson = response.text
            ?: throw IllegalStateException("Smart HUD model returned an empty response.")
        val json = JSONObject(rawJson)
        return SmartHudCallout(
            priority = json.getString("priority"),
            headline = json.getString("headline"),
            spokenTip = json.getString("spokenTip"),
            paceAction = json.getString("paceAction")
        )
    }

    fun streamRiderCopilotAnswer(
        question: String,
        packName: String?,
        weather: RiderLiveWeatherAlert?,
        riders: List<DecryptedRiderState>,
        plannedRoute: ComputedMotoRoute?,
        isOffline: Boolean
    ): Flow<AiCopilotUiState> = flow {
        emit(AiCopilotUiState.Loading)
        val accumulated = StringBuilder()
        try {
            val prompt = buildContextPrompt(
                userQuestion = question,
                packName = packName,
                weather = weather,
                riders = riders,
                plannedRoute = plannedRoute,
                isOffline = isOffline
            )
            withTimeout(60_000L) {
                chatModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiCopilotUiState.Streaming(accumulated.toString()))
                    }
                }
            }
            if (accumulated.isNotBlank()) {
                emit(AiCopilotUiState.Success(accumulated.toString()))
            } else {
                emit(AiCopilotUiState.Error("Gemini returned an empty response."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI Copilot streaming timed out", e)
            emit(AiCopilotUiState.Error("AI response timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic error", e)
            emit(AiCopilotUiState.Error(e.localizedMessage ?: "AI Copilot request failed."))
        }
    }

    private fun buildContextPrompt(
        userQuestion: String,
        packName: String?,
        weather: RiderLiveWeatherAlert?,
        riders: List<DecryptedRiderState>,
        plannedRoute: ComputedMotoRoute?,
        isOffline: Boolean
    ): String = buildString {
        appendLine("Rider Request: $userQuestion")
        appendLine()
        appendLine("--- LIVE CONVOY TELEMETRY ---")
        appendLine("Pack: ${packName ?: "Solo Ride"} (${riders.size} active riders)")
        appendLine("Network Mode: ${if (isOffline) "Offline / Dead-Zone" else "Online"}")

        if (riders.isNotEmpty()) {
            val maxSep = riders.maxOfOrNull { it.distanceFromMeKm } ?: 0.0
            val minBatt = riders.minOfOrNull { it.batteryPercent } ?: 100
            appendLine("Max Rider Separation: ${String.format("%.1f", maxSep)} km | Lowest Phone Battery: $minBatt%")
            riders.take(6).forEach { r ->
                appendLine("  - ${r.displayName} (${r.bikeModel}): ${String.format("%.1f", r.distanceFromMeKm)} km away, ${r.speedKmh.toInt()} km/h, Alt ${r.altitudeM.toInt()}m, Batt ${r.batteryPercent}%")
            }
        }

        appendLine()
        appendLine("--- REAL-TIME WEATHER ---")
        if (weather != null) {
            appendLine("Location: ${weather.locationName} (${weather.providerLabel})")
            appendLine("Severity: ${weather.severity} | ${weather.alertHeadline}")
            appendLine("Temp: ${weather.tempCelsius.toInt()}°C | Wind: ${weather.windSpeedKmh.toInt()} km/h (gusts ${weather.windGustKmh.toInt()} km/h) | Rain: ${String.format("%.1f", weather.rainMmPerHour)} mm/h")
        } else {
            appendLine("Weather feed pending.")
        }

        if (plannedRoute != null) {
            appendLine()
            appendLine("--- PLANNED ROUTE ---")
            appendLine("Route: ${plannedRoute.routeName} (${String.format("%.1f", plannedRoute.distanceKm)} km, ${plannedRoute.durationMin} min, +${plannedRoute.trafficDelayMin}m traffic)")
            appendLine("Elevation: Peak ${plannedRoute.maxElevationM.toInt()}m, Total Climb +${plannedRoute.totalAscentM.toInt()}m")
            appendLine("Fuel Stops Found: ${plannedRoute.fuelStations.size} (${plannedRoute.fuelStations.take(3).joinToString { it.name }})")
            if (plannedRoute.weatherWarnings.isNotEmpty()) {
                appendLine("Corridor Hazards: ${plannedRoute.weatherWarnings.joinToString(" | ") { "${it.locationLabel}: ${it.headline}" }}")
            }
        }
    }
}
