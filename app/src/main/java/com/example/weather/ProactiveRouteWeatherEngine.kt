package com.example.weather

import com.example.data.model.BikerBrewCatalog
import com.example.data.model.BikerLocation
import com.example.data.remote.ComputedMotoRoute
import com.example.data.remote.MotoRouteAndWeatherRepository
import com.example.data.remote.OpenMeteoForecastPoint
import com.example.location.LiveGpsFix
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ProactiveWeatherHazardType(val label: String) {
    THUNDERSTORM("Thunderstorm"),
    HEAVY_RAIN("Heavy Rain"),
    HIGH_WINDS("High Crosswinds"),
    ICE_RISK("Black Ice / Freeze"),
    MIXED_STORM("Squall & Gusts")
}

enum class WeatherAdvisorySeverity {
    ADVISORY,
    CRITICAL
}

data class CorridorWeatherSample(
    val tempCelsius: Double,
    val windKmh: Double,
    val gustKmh: Double,
    val rainProbPercent: Int,
    val precipitationMm: Double,
    val wmoCode: Int
)

data class SuggestedWeatherStop(
    val id: String,
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double,
    val distanceAheadKm: Double,
    val etaMinutes: Int,
    val isCoveredCafe: Boolean
)

data class SuggestedRouteChange(
    val title: String,
    val summary: String,
    val destinationLabel: String,
    val detourLat: Double,
    val detourLng: Double,
    val estimatedExtraMinutes: Int
)

data class ProactiveRouteWeatherAlert(
    val alertId: String,
    val routeName: String,
    val checkpointLabel: String,
    val hazardLat: Double,
    val hazardLng: Double,
    val distanceAheadKm: Double,
    val etaMinutes: Int,
    val hazardType: ProactiveWeatherHazardType,
    val severity: WeatherAdvisorySeverity,
    val headline: String,
    val recommendationSummary: String,
    val tempCelsius: Double,
    val windKmh: Double,
    val gustKmh: Double,
    val rainProbPercent: Int,
    val precipitationMm: Double,
    val suggestedStop: SuggestedWeatherStop?,
    val suggestedRouteChange: SuggestedRouteChange,
    val signatureKey: String,
    val detectedAtMs: Long = System.currentTimeMillis()
)

data class HazardEvaluation(
    val hazardType: ProactiveWeatherHazardType,
    val severity: WeatherAdvisorySeverity,
    val headline: String,
    val riskScore: Int
)

object ProactiveRouteWeatherEngine {

    const val DEFAULT_COOLDOWN_MS = 15L * 60L * 1000L

    fun evaluateSample(sample: CorridorWeatherSample): HazardEvaluation? {
        return when {
            sample.wmoCode >= 95 || (sample.wmoCode >= 80 && sample.gustKmh >= 45.0) -> {
                HazardEvaluation(
                    hazardType = ProactiveWeatherHazardType.THUNDERSTORM,
                    severity = WeatherAdvisorySeverity.CRITICAL,
                    headline = "Thunderstorm cell (${sample.gustKmh.toInt()} km/h gusts, ${sample.rainProbPercent}% rain)",
                    riskScore = 100
                )
            }
            sample.tempCelsius <= 0.5 ||
                (sample.tempCelsius <= 2.5 && (sample.rainProbPercent >= 35 || sample.precipitationMm >= 0.2 || sample.wmoCode in 51..86)) -> {
                HazardEvaluation(
                    hazardType = ProactiveWeatherHazardType.ICE_RISK,
                    severity = WeatherAdvisorySeverity.CRITICAL,
                    headline = "Freezing wet surface (${sample.tempCelsius.toInt()}°C) — black ice risk",
                    riskScore = 95
                )
            }
            sample.gustKmh >= 50.0 || sample.windKmh >= 38.0 -> {
                HazardEvaluation(
                    hazardType = ProactiveWeatherHazardType.HIGH_WINDS,
                    severity = WeatherAdvisorySeverity.CRITICAL,
                    headline = "Severe crosswind gusts (${sample.gustKmh.toInt()} km/h, sustained ${sample.windKmh.toInt()} km/h)",
                    riskScore = 90
                )
            }
            sample.precipitationMm >= 4.0 ||
                sample.wmoCode in listOf(65, 67, 75, 81, 82) ||
                (sample.rainProbPercent >= 75 && sample.precipitationMm >= 1.5) -> {
                HazardEvaluation(
                    hazardType = ProactiveWeatherHazardType.HEAVY_RAIN,
                    severity = WeatherAdvisorySeverity.CRITICAL,
                    headline = "Heavy rain expected (${sample.rainProbPercent}% • ${String.format("%.1f", sample.precipitationMm)} mm/h)",
                    riskScore = 85
                )
            }
            sample.gustKmh >= 35.0 &&
                (sample.rainProbPercent >= 55 || sample.precipitationMm >= 0.8 || sample.wmoCode in 51..86) -> {
                HazardEvaluation(
                    hazardType = ProactiveWeatherHazardType.MIXED_STORM,
                    severity = WeatherAdvisorySeverity.ADVISORY,
                    headline = "Rain squall & ${sample.gustKmh.toInt()} km/h gusts approaching",
                    riskScore = 68
                )
            }
            sample.gustKmh >= 34.0 -> {
                HazardEvaluation(
                    hazardType = ProactiveWeatherHazardType.HIGH_WINDS,
                    severity = WeatherAdvisorySeverity.ADVISORY,
                    headline = "High crosswinds on exposed stretches (${sample.gustKmh.toInt()} km/h gusts)",
                    riskScore = 58
                )
            }
            sample.rainProbPercent >= 65 || sample.precipitationMm >= 1.0 || sample.wmoCode in 51..86 -> {
                HazardEvaluation(
                    hazardType = ProactiveWeatherHazardType.HEAVY_RAIN,
                    severity = WeatherAdvisorySeverity.ADVISORY,
                    headline = "Wet road surface & rain moving in (${sample.rainProbPercent}% chance)",
                    riskScore = 54
                )
            }
            else -> null
        }
    }

    private fun rainLabel(sample: CorridorWeatherSample): String =
        "${sample.rainProbPercent}% • ${String.format("%.1f", sample.precipitationMm)} mm/h"

    suspend fun analyzeRoute(
        route: ComputedMotoRoute,
        currentFix: LiveGpsFix,
        routeRepository: MotoRouteAndWeatherRepository,
        nowMs: Long = System.currentTimeMillis()
    ): ProactiveRouteWeatherAlert? {
        return analyzeRouteWithFetcher(
            route = route,
            currentFix = currentFix,
            nowMs = nowMs
        ) { lat, lng, etaHourOffset ->
            routeRepository.fetchCorridorForecastAtHourOffset(lat, lng, etaHourOffset)?.toCorridorSample()
        }
    }

    suspend fun analyzeRouteWithFetcher(
        route: ComputedMotoRoute,
        currentFix: LiveGpsFix,
        cafeCatalog: List<BikerLocation> = BikerBrewCatalog.curatedLocations,
        nowMs: Long = System.currentTimeMillis(),
        forecastFetcher: suspend (lat: Double, lng: Double, etaHourOffset: Int) -> CorridorWeatherSample?
    ): ProactiveRouteWeatherAlert? {
        val pts = route.points
        if (pts.size < 2) return null

        val nearestIdx = pts.indices.minByOrNull { idx ->
            MotoRouteAndWeatherRepository.haversineKm(
                currentFix.lat,
                currentFix.lng,
                pts[idx].latitude,
                pts[idx].longitude
            )
        } ?: 0

        val remainingPts = if (pts.size - nearestIdx >= 4) pts.subList(nearestIdx, pts.size) else pts
        val totalRemainingKm = computeSegmentDistanceKm(remainingPts).coerceAtLeast(route.distanceKm.coerceAtLeast(2.0))
        val estimatedTotalMin = route.durationMin.coerceAtLeast(10)

        val checkpointSpecs = listOf(
            "Ahead (Near Sector)" to 0.20,
            "Mid-Corridor" to 0.45,
            "High Ridge / Approach" to 0.72,
            route.destinationLabel.take(22).ifBlank { "Destination Zone" } to 0.94
        )

        val candidates = coroutineScope {
            checkpointSpecs.map { (label, frac) ->
                async {
                    val ptIdx = ((remainingPts.size - 1) * frac).roundToInt().coerceIn(0, remainingPts.lastIndex)
                    val pt = remainingPts[ptIdx]
                    val distAheadKm = (totalRemainingKm * frac).coerceAtLeast(0.5)
                    val etaMinutes = (estimatedTotalMin * frac).roundToInt().coerceAtLeast(3)
                    val etaHourOffset = (etaMinutes / 60.0).roundToInt().coerceIn(0, 6)

                    val sample = forecastFetcher(pt.latitude, pt.longitude, etaHourOffset) ?: return@async null
                    val eval = evaluateSample(sample) ?: return@async null

                    EvaluatedCheckpoint(
                        label = label,
                        fraction = frac,
                        point = pt,
                        distanceAheadKm = distAheadKm,
                        etaMinutes = etaMinutes,
                        sample = sample,
                        evaluation = eval
                    )
                }
            }.awaitAll().filterNotNull()
        }

        val hit = candidates.maxWithOrNull(
            compareBy<EvaluatedCheckpoint> { it.evaluation.riskScore }
                .thenByDescending { -it.etaMinutes }
        ) ?: return null
        val suggestedStop = findBestSuggestedStop(
            route = route,
            remainingPts = remainingPts,
            totalRemainingKm = totalRemainingKm,
            estimatedTotalMin = estimatedTotalMin,
            hazardFraction = hit.fraction,
            hazardDistAheadKm = hit.distanceAheadKm,
            currentFix = currentFix,
            cafeCatalog = cafeCatalog
        )

        val suggestedChange = computeSuggestedRouteChange(
            route = route,
            remainingPts = remainingPts,
            hazardFraction = hit.fraction,
            hazardPoint = hit.point,
            hazardType = hit.evaluation.hazardType
        )

        val recommendationText = buildRecommendationSummary(
            hazard = hit.evaluation,
            stop = suggestedStop,
            detour = suggestedChange,
            etaMinutes = hit.etaMinutes,
            distAheadKm = hit.distanceAheadKm
        )

        val signature = "${route.routeName}|${hit.evaluation.hazardType.name}|${hit.evaluation.severity.name}"
        return ProactiveRouteWeatherAlert(
            alertId = "wx_push_${route.routeName.hashCode()}_${hit.evaluation.hazardType.name}",
            routeName = route.routeName,
            checkpointLabel = hit.label,
            hazardLat = hit.point.latitude,
            hazardLng = hit.point.longitude,
            distanceAheadKm = hit.distanceAheadKm,
            etaMinutes = hit.etaMinutes,
            hazardType = hit.evaluation.hazardType,
            severity = hit.evaluation.severity,
            headline = hit.evaluation.headline,
            recommendationSummary = recommendationText,
            tempCelsius = hit.sample.tempCelsius,
            windKmh = hit.sample.windKmh,
            gustKmh = hit.sample.gustKmh,
            rainProbPercent = hit.sample.rainProbPercent,
            precipitationMm = hit.sample.precipitationMm,
            suggestedStop = suggestedStop,
            suggestedRouteChange = suggestedChange,
            signatureKey = signature,
            detectedAtMs = nowMs
        )
    }

    fun shouldPushNotification(
        newAlert: ProactiveRouteWeatherAlert,
        lastPushedSignature: String?,
        lastPushedAtMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        cooldownMs: Long = DEFAULT_COOLDOWN_MS
    ): Boolean {
        if (lastPushedSignature.isNullOrBlank() || lastPushedAtMs <= 0L) return true
        val elapsed = (nowMs - lastPushedAtMs).coerceAtLeast(0L)
        if (elapsed >= cooldownMs) return true

        val wasCritical = lastPushedSignature.endsWith("|${WeatherAdvisorySeverity.CRITICAL.name}")
        if (newAlert.severity == WeatherAdvisorySeverity.CRITICAL && !wasCritical) {
            return true
        }
        if (newAlert.signatureKey != lastPushedSignature && elapsed >= 60_000L) {
            return true
        }
        return false
    }

    private fun findBestSuggestedStop(
        route: ComputedMotoRoute,
        remainingPts: List<LatLng>,
        totalRemainingKm: Double,
        estimatedTotalMin: Int,
        hazardFraction: Double,
        hazardDistAheadKm: Double,
        currentFix: LiveGpsFix,
        cafeCatalog: List<BikerLocation>
    ): SuggestedWeatherStop? {
        val preHazardFrac = (hazardFraction * 0.65).coerceIn(0.08, 0.78)
        val preHazardIdx = ((remainingPts.size - 1) * preHazardFrac).roundToInt().coerceIn(0, remainingPts.lastIndex)
        val preHazardPt = remainingPts[preHazardIdx]

        // 1. Prefer fuel stations on the planned route positioned before the hazard segment
        val fuelCandidate = route.fuelStations
            .map { station ->
                val distFromMeKm = MotoRouteAndWeatherRepository.haversineKm(
                    currentFix.lat,
                    currentFix.lng,
                    station.lat,
                    station.lng
                )
                station to distFromMeKm
            }
            .filter { (station, distFromMeKm) ->
                val along = station.distanceAlongKm.coerceAtLeast(distFromMeKm)
                along in 0.5..(hazardDistAheadKm + 1.5)
            }
            .minByOrNull { (station, _) ->
                kotlin.math.abs(station.distanceAlongKm - (hazardDistAheadKm * 0.7))
            }

        // 2. Check covered Biker & Brew cafes near the pre-hazard zone
        val cafeCandidate = cafeCatalog
            .map { cafe ->
                val distToSafePointKm = MotoRouteAndWeatherRepository.haversineKm(
                    preHazardPt.latitude,
                    preHazardPt.longitude,
                    cafe.lat,
                    cafe.lng
                )
                val distFromMeKm = MotoRouteAndWeatherRepository.haversineKm(
                    currentFix.lat,
                    currentFix.lng,
                    cafe.lat,
                    cafe.lng
                )
                Triple(cafe, distToSafePointKm, distFromMeKm)
            }
            .filter { (_, distToSafePointKm, distFromMeKm) ->
                distToSafePointKm <= 16.0 && distFromMeKm <= hazardDistAheadKm + 2.0
            }
            .minByOrNull { it.second }

        if (cafeCandidate != null && (fuelCandidate == null || cafeCandidate.second <= 8.0)) {
            val (cafe, _, distKm) = cafeCandidate
            val etaMin = ((distKm / totalRemainingKm.coerceAtLeast(1.0)) * estimatedTotalMin)
                .roundToInt()
                .coerceAtLeast(4)
            return SuggestedWeatherStop(
                id = cafe.id,
                name = cafe.name,
                subtitle = "Biker & Brew Shelter • ${cafe.cityRegion} (~${String.format("%.1f", distKm)} km)",
                lat = cafe.lat,
                lng = cafe.lng,
                distanceAheadKm = distKm,
                etaMinutes = etaMin,
                isCoveredCafe = true
            )
        }

        if (fuelCandidate != null) {
            val (station, distKm) = fuelCandidate
            val aheadKm = station.distanceAlongKm.coerceAtLeast(distKm).coerceAtLeast(0.8)
            val etaMin = ((aheadKm / totalRemainingKm.coerceAtLeast(1.0)) * estimatedTotalMin)
                .roundToInt()
                .coerceAtLeast(3)
            return SuggestedWeatherStop(
                id = station.id,
                name = station.name,
                subtitle = "Covered Fuel Forecourt before hazard (~${String.format("%.1f", aheadKm)} km)",
                lat = station.lat,
                lng = station.lng,
                distanceAheadKm = aheadKm,
                etaMinutes = etaMin,
                isCoveredCafe = false
            )
        }

        // 3. Fallback: Any fuel station on route OR safe pullout before hazard zone
        val fallbackFuel = route.fuelStations.firstOrNull()
        if (fallbackFuel != null) {
            val aheadKm = fallbackFuel.distanceAlongKm.coerceAtLeast(1.0)
            val etaMin = ((aheadKm / totalRemainingKm.coerceAtLeast(1.0)) * estimatedTotalMin)
                .roundToInt()
                .coerceAtLeast(3)
            return SuggestedWeatherStop(
                id = fallbackFuel.id,
                name = fallbackFuel.name,
                subtitle = "Nearest route fuel shelter (~${String.format("%.1f", aheadKm)} km)",
                lat = fallbackFuel.lat,
                lng = fallbackFuel.lng,
                distanceAheadKm = aheadKm,
                etaMinutes = etaMin,
                isCoveredCafe = false
            )
        }

        val safeDistKm = (totalRemainingKm * preHazardFrac).coerceAtLeast(0.8)
        val safeEtaMin = (estimatedTotalMin * preHazardFrac).roundToInt().coerceAtLeast(3)
        return SuggestedWeatherStop(
            id = "safe_pullout_${route.routeName.hashCode()}",
            name = "Pre-Hazard Pullout Zone",
            subtitle = "Safe pause point before weather cell (~${String.format("%.1f", safeDistKm)} km)",
            lat = preHazardPt.latitude,
            lng = preHazardPt.longitude,
            distanceAheadKm = safeDistKm,
            etaMinutes = safeEtaMin,
            isCoveredCafe = false
        )
    }

    private fun computeSuggestedRouteChange(
        route: ComputedMotoRoute,
        remainingPts: List<LatLng>,
        hazardFraction: Double,
        hazardPoint: LatLng,
        hazardType: ProactiveWeatherHazardType
    ): SuggestedRouteChange {
        val anchorIdx = ((remainingPts.size - 1) * (hazardFraction * 0.5).coerceAtLeast(0.1))
            .roundToInt()
            .coerceIn(0, remainingPts.lastIndex)
        val anchorPt = remainingPts[anchorIdx]
        val endPt = remainingPts.lastOrNull() ?: hazardPoint

        val bearingDeg = MotoRouteAndWeatherRepository.bearingDegrees(anchorPt, endPt)
        val lateralBearingRad = Math.toRadians((bearingDeg + 90.0) % 360.0)

        // Offset ~5.5 km laterally toward lower-elevation / valley bypass corridor
        val offsetDegLat = 0.048 * cos(lateralBearingRad)
        val offsetDegLng = (0.048 / cos(Math.toRadians(endPt.latitude)).coerceAtLeast(0.35)) * sin(lateralBearingRad)

        val detourLat = (endPt.latitude + offsetDegLat).coerceIn(-85.0, 85.0)
        val detourLng = (endPt.longitude + offsetDegLng).coerceIn(-179.0, 179.0)

        val bypassLabel = when (hazardType) {
            ProactiveWeatherHazardType.HIGH_WINDS -> "Sheltered Valley Bypass"
            ProactiveWeatherHazardType.THUNDERSTORM -> "Storm-Cell Perimeter Detour"
            ProactiveWeatherHazardType.ICE_RISK -> "Low-Altitude Salted Route"
            ProactiveWeatherHazardType.HEAVY_RAIN,
            ProactiveWeatherHazardType.MIXED_STORM -> "Dry-Line Valley Alternate"
        }

        val extraMin = when (hazardType) {
            ProactiveWeatherHazardType.THUNDERSTORM,
            ProactiveWeatherHazardType.ICE_RISK -> 12
            ProactiveWeatherHazardType.HIGH_WINDS -> 9
            else -> 8
        }

        val cleanRouteShort = route.routeName.take(20).ifBlank { "Corridor" }
        return SuggestedRouteChange(
            title = "$bypassLabel (+${extraMin} min)",
            summary = "Reroute around exposed ${hazardPoint.latitude.format3()}, ${hazardPoint.longitude.format3()} zone to lower wind/rain exposure.",
            destinationLabel = "$bypassLabel • $cleanRouteShort",
            detourLat = detourLat,
            detourLng = detourLng,
            estimatedExtraMinutes = extraMin
        )
    }

    private fun buildRecommendationSummary(
        hazard: HazardEvaluation,
        stop: SuggestedWeatherStop?,
        detour: SuggestedRouteChange,
        etaMinutes: Int,
        distAheadKm: Double
    ): String {
        val stopPart = if (stop != null) {
            "Pull in at ${stop.name} (~${String.format("%.1f", stop.distanceAheadKm)} km)"
        } else {
            "Hold before storm sector"
        }
        return "${hazard.hazardType.label} in ~$etaMinutes min (${String.format("%.1f", distAheadKm)} km ahead). $stopPart or switch to ${detour.title}."
    }

    private fun computeSegmentDistanceKm(points: List<LatLng>): Double {
        return points.zipWithNext { a, b ->
            MotoRouteAndWeatherRepository.haversineKm(a.latitude, a.longitude, b.latitude, b.longitude)
        }.sum()
    }

    private fun OpenMeteoForecastPoint.toCorridorSample(): CorridorWeatherSample {
        return CorridorWeatherSample(
            tempCelsius = tempCelsius,
            windKmh = windKmh,
            gustKmh = gustKmh,
            rainProbPercent = rainProbPercent,
            precipitationMm = precipitationMm,
            wmoCode = wmoCode
        )
    }

    private fun Double.format3(): String = String.format("%.3f", this)

    private data class EvaluatedCheckpoint(
        val label: String,
        val fraction: Double,
        val point: LatLng,
        val distanceAheadKm: Double,
        val etaMinutes: Int,
        val sample: CorridorWeatherSample,
        val evaluation: HazardEvaluation
    )
}
