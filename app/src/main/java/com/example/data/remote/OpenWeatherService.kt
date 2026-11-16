package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenWeatherCurrentResponse(
    val name: String? = null,
    val weather: List<OpenWeatherConditionDto>? = null,
    val main: OpenWeatherMainDto? = null,
    val wind: OpenWeatherWindDto? = null,
    val rain: OpenWeatherRainDto? = null,
    val snow: OpenWeatherRainDto? = null,
    val visibility: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenWeatherConditionDto(
    val id: Int = 800,
    val main: String? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenWeatherMainDto(
    val temp: Double? = null,
    @param:Json(name = "feels_like") val feelsLike: Double? = null,
    val humidity: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenWeatherWindDto(
    val speed: Double? = null, // m/s when units=metric
    val gust: Double? = null,
    val deg: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenWeatherRainDto(
    @param:Json(name = "1h") val oneHourMm: Double? = null,
    @param:Json(name = "3h") val threeHourMm: Double? = null
)

interface OpenWeatherMapApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Response<OpenWeatherCurrentResponse>
}

data class RiderLiveWeatherAlert(
    val locationName: String,
    val conditionTitle: String,
    val alertHeadline: String,
    val severity: String, // "CLEAR", "ADVISORY", "WARNING"
    val hasRainAlert: Boolean,
    val hasHighWindAlert: Boolean,
    val tempCelsius: Double,
    val windSpeedKmh: Double,
    val windGustKmh: Double,
    val rainMmPerHour: Double,
    val visibilityKm: Double,
    val lat: Double,
    val lng: Double,
    val providerLabel: String,
    val apiNotice: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

class OpenWeatherRepository {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private data class CachedWeatherEntry(
        val alert: RiderLiveWeatherAlert,
        val cachedAtMs: Long
    )

    private val weatherCache = ConcurrentHashMap<String, CachedWeatherEntry>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val openWeatherApi: OpenWeatherMapApi = Retrofit.Builder()
        .baseUrl("https://api.openweathermap.org/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenWeatherMapApi::class.java)

    private val openMeteoFallbackApi: OpenMeteoApi = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoApi::class.java)

    private fun validOpenWeatherApiKey(): String? {
        val key = BuildConfig.OPENWEATHER_API_KEY
        return if (key.isBlank() || key == "MY_OPENWEATHER_API_KEY") null else key
    }

    suspend fun fetchRiderWeatherAlert(lat: Double, lng: Double): RiderLiveWeatherAlert? =
        withContext(Dispatchers.IO) {
            val cacheKey = String.format(Locale.US, "%.2f,%.2f", lat, lng)
            val nowMs = System.currentTimeMillis()
            weatherCache[cacheKey]?.let { entry ->
                if ((nowMs - entry.cachedAtMs) <= WEATHER_CACHE_TTL_MS) {
                    return@withContext entry.alert
                }
            }

            val apiKey = validOpenWeatherApiKey()
            var notice: String? = null

            if (apiKey != null) {
                try {
                    val res = openWeatherApi.getCurrentWeather(lat = lat, lon = lng, apiKey = apiKey)
                    val body = res.body()
                    if (res.isSuccessful && body != null) {
                        val cond = body.weather?.firstOrNull()
                        val temp = body.main?.temp ?: 15.0
                        val windKmh = (body.wind?.speed ?: 0.0) * 3.6
                        val gustKmh = ((body.wind?.gust ?: body.wind?.speed ?: 0.0) * 3.6)
                            .coerceAtLeast(windKmh)
                        val rainMm = body.rain?.oneHourMm
                            ?: (body.rain?.threeHourMm?.div(3.0))
                            ?: 0.0
                        val condId = cond?.id ?: 800
                        val condMain = cond?.main ?: "Clear"
                        val condDesc = cond?.description?.replaceFirstChar { it.uppercase() } ?: condMain
                        val visKm = (body.visibility ?: 10000) / 1000.0

                        val isRainWeather = condId in 200..599 ||
                            rainMm >= 0.2 ||
                            condMain.equals("Rain", ignoreCase = true) ||
                            condMain.equals("Drizzle", ignoreCase = true) ||
                            condMain.equals("Thunderstorm", ignoreCase = true)
                        val isHighWind = gustKmh >= 42.0 || windKmh >= 32.0

                        val (severity, headline) = evaluateOpenWeatherHazard(
                            condId = condId,
                            condDesc = condDesc,
                            tempC = temp,
                            windKmh = windKmh,
                            gustKmh = gustKmh,
                            rainMm = rainMm,
                            visKm = visKm,
                            isRain = isRainWeather,
                            isHighWind = isHighWind
                        )

                        val result = RiderLiveWeatherAlert(
                            locationName = body.name?.takeIf { it.isNotBlank() } ?: "Current Sector",
                            conditionTitle = condDesc,
                            alertHeadline = headline,
                            severity = severity,
                            hasRainAlert = isRainWeather,
                            hasHighWindAlert = isHighWind,
                            tempCelsius = temp,
                            windSpeedKmh = windKmh,
                            windGustKmh = gustKmh,
                            rainMmPerHour = rainMm,
                            visibilityKm = visKm,
                            lat = lat,
                            lng = lng,
                            providerLabel = "OpenWeatherMap Live"
                        )
                        weatherCache[cacheKey] = CachedWeatherEntry(result, nowMs)
                        return@withContext result
                    } else {
                        notice = "OpenWeatherMap HTTP ${res.code()} — using live Open-Meteo fallback"
                    }
                } catch (e: Exception) {
                    notice = "OpenWeatherMap unreachable (${e.localizedMessage ?: "network"}) — using fallback"
                }
            } else {
                notice = "Add OPENWEATHER_API_KEY in AI Studio Secrets for OpenWeatherMap live feed"
            }

            // Live meteorological fallback via Open-Meteo when OPENWEATHER_API_KEY is absent/pending
            try {
                val res = openMeteoFallbackApi.getForecast(lat, lng)
                val body = res.body()
                val cur = body?.current
                if (res.isSuccessful && cur != null) {
                    val temp = cur.temperature_2m ?: 15.0
                    val wind = cur.wind_speed_10m ?: 12.0
                    val gusts = (cur.wind_gusts_10m ?: wind).coerceAtLeast(wind)
                    val code = cur.weather_code ?: 0
                    val rainProb = body.hourly?.precipitation_probability?.firstOrNull() ?: 0
                    val isRain = code in 51..86 || code >= 95 || rainProb >= 55
                    val isWind = gusts >= 42.0 || wind >= 32.0
                    val (severity, headline) = when {
                        code >= 95 -> "WARNING" to "Storm cells nearby — wet tarmac & lightning"
                        isWind && isRain -> "WARNING" to "Rain ($rainProb%) + ${gusts.toInt()} km/h gusts"
                        gusts >= 50.0 -> "WARNING" to "Severe crosswind gusts (${gusts.toInt()} km/h)"
                        isRain -> "ADVISORY" to "Rain expected ($rainProb%) — increase braking gap"
                        isWind -> "ADVISORY" to "High winds (${gusts.toInt()} km/h gusts) — watch lean"
                        temp <= 3.0 -> "WARNING" to "Near freezing (${temp.toInt()}°C) — black ice risk"
                        else -> "CLEAR" to "Clear riding weather (${temp.toInt()}°C, ${wind.toInt()} km/h wind)"
                    }
                    val fallbackResult = RiderLiveWeatherAlert(
                        locationName = "Rider Sector",
                        conditionTitle = if (isRain) "Rain / Wet" else if (isWind) "High Wind" else "Clear",
                        alertHeadline = headline,
                        severity = severity,
                        hasRainAlert = isRain,
                        hasHighWindAlert = isWind,
                        tempCelsius = temp,
                        windSpeedKmh = wind,
                        windGustKmh = gusts,
                        rainMmPerHour = if (isRain) 1.5 else 0.0,
                        visibilityKm = 10.0,
                        lat = lat,
                        lng = lng,
                        providerLabel = "Open-Meteo Fallback",
                        apiNotice = notice
                    )
                    weatherCache[cacheKey] = CachedWeatherEntry(fallbackResult, nowMs)
                    return@withContext fallbackResult
                }
            } catch (_: Exception) {
            }
            null
        }

    companion object {
        private const val WEATHER_CACHE_TTL_MS = 3L * 60L * 1000L
    }

    private fun evaluateOpenWeatherHazard(
        condId: Int,
        condDesc: String,
        tempC: Double,
        windKmh: Double,
        gustKmh: Double,
        rainMm: Double,
        visKm: Double,
        isRain: Boolean,
        isHighWind: Boolean
    ): Pair<String, String> {
        return when {
            condId in 200..232 -> "WARNING" to "Thunderstorm alert ($condDesc) — seek shelter"
            isRain && isHighWind -> "WARNING" to "Rain + ${gustKmh.toInt()} km/h crosswinds — reduce speed"
            rainMm >= 2.5 || condId in 502..531 -> "WARNING" to "Heavy rain (${String.format("%.1f", rainMm)} mm/h) — hydroplane risk"
            gustKmh >= 50.0 || windKmh >= 40.0 -> "WARNING" to "High wind warning (${gustKmh.toInt()} km/h gusts)"
            tempC <= 3.0 -> "WARNING" to "Near-freezing (${tempC.toInt()}°C) — watch for black ice"
            visKm < 1.5 -> "WARNING" to "Low visibility (${String.format("%.1f", visKm)} km) — turn on hazards"
            isRain -> "ADVISORY" to "Rain alert ($condDesc) — smooth throttle & braking"
            isHighWind -> "ADVISORY" to "Gusty crosswinds (${gustKmh.toInt()} km/h) on exposed roads"
            else -> "CLEAR" to "$condDesc • ${tempC.toInt()}°C, wind ${windKmh.toInt()} km/h"
        }
    }
}
