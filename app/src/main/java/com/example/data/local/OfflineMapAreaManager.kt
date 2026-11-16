package com.example.data.local

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

data class MapAreaDownloadProgress(
    val isDownloading: Boolean = false,
    val title: String = "",
    val completedTiles: Int = 0,
    val totalTiles: Int = 0
) {
    val fraction: Float
        get() = if (totalTiles <= 0) 0f else (completedTiles.toFloat() / totalTiles.toFloat()).coerceIn(0f, 1f)
}

data class OfflineAreaEstimate(
    val tileCount: Int,
    val estimatedBytes: Long,
    val zoomRangeLabel: String
)

data class TileCoord(val z: Int, val x: Int, val y: Int)

class OfflineMapAreaManager(context: Context) {
    private val appContext = context.applicationContext
    private val tileRootDir = File(appContext.filesDir, "offline_map_tiles").apply { mkdirs() }

    private val client = OkHttpClient.Builder().build()

    suspend fun downloadRouteOrRadiusArea(
        packId: String,
        title: String,
        center: LatLng,
        routePoints: List<LatLng>,
        encodedPolyline: String,
        radiusKm: Double,
        linkedRouteId: String = "",
        navStepCount: Int = 0,
        fuelStopCount: Int = 0,
        elevationSampleCount: Int = 0,
        weatherCount: Int = 0,
        maxElevationM: Double = 0.0,
        elevationGainM: Double = 0.0,
        navigationStepsJson: String = "[]",
        elevationPointsJson: String = "[]",
        fuelStopsJson: String = "[]",
        weatherAlertsJson: String = "[]",
        onProgress: (MapAreaDownloadProgress) -> Unit
    ): Result<OfflineMapAreaEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val areaId = "area_${UUID.randomUUID().toString().take(8)}"
            val areaDir = File(tileRootDir, areaId).apply { mkdirs() }

            val (bounds, boundedCoords) = computeBoundsAndCoords(
                center = center,
                routePoints = routePoints,
                radiusKm = radiusKm,
                maxTiles = DEFAULT_MAX_TILES
            )
            var downloadedCount = 0
            var totalBytes = 0L

            onProgress(
                MapAreaDownloadProgress(
                    isDownloading = true,
                    title = title,
                    completedTiles = 0,
                    totalTiles = boundedCoords.size
                )
            )

            boundedCoords.forEachIndexed { idx, coord ->
                val outFile = File(areaDir, "${coord.z}_${coord.x}_${coord.y}.png")
                val url = "https://tile.openstreetmap.org/${coord.z}/${coord.x}/${coord.y}.png"
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "MotoPack-Android/1.0")
                        .build()
                    client.newCall(request).execute().use { response ->
                        val bytes = response.body?.bytes()
                        if (response.isSuccessful && bytes != null && bytes.isNotEmpty()) {
                            outFile.writeBytes(bytes)
                            downloadedCount++
                            totalBytes += bytes.size
                        }
                    }
                } catch (_: Exception) {
                    // Continue downloading remaining corridor tiles on transient error
                }
                onProgress(
                    MapAreaDownloadProgress(
                        isDownloading = true,
                        title = title,
                        completedTiles = idx + 1,
                        totalTiles = boundedCoords.size
                    )
                )
            }

            val allPoints = if (routePoints.isNotEmpty()) routePoints else listOf(center)
            val avgLat = allPoints.map { it.latitude }.average()
            val avgLng = allPoints.map { it.longitude }.average()

            OfflineMapAreaEntity(
                areaId = areaId,
                packId = packId,
                title = title.ifBlank { "Offline Sector" },
                centerLat = avgLat,
                centerLng = avgLng,
                minLat = bounds[0],
                maxLat = bounds[1],
                minLng = bounds[2],
                maxLng = bounds[3],
                radiusKm = radiusKm,
                zoomRangeLabel = "Z11–Z14",
                tileCount = downloadedCount,
                storageBytes = totalBytes,
                routePolyline = encodedPolyline,
                linkedRouteId = linkedRouteId,
                navStepCount = navStepCount,
                fuelStopCount = fuelStopCount,
                elevationSampleCount = elevationSampleCount,
                weatherCount = weatherCount,
                maxElevationM = maxElevationM,
                elevationGainM = elevationGainM,
                navigationStepsJson = navigationStepsJson,
                elevationPointsJson = elevationPointsJson,
                fuelStopsJson = fuelStopsJson,
                weatherAlertsJson = weatherAlertsJson
            )
        }
    }

    fun deleteAreaTiles(areaId: String) {
        File(tileRootDir, areaId).deleteRecursively()
    }

    fun createOfflineTileProvider(areaIds: List<String>): TileProvider {
        return TileProvider { x, y, zoom ->
            for (areaId in areaIds) {
                val file = File(tileRootDir, "$areaId/${zoom}_${x}_${y}.png")
                if (file.exists() && file.length() > 0L) {
                    val bytes = runCatching { file.readBytes() }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        return@TileProvider Tile(256, 256, bytes)
                    }
                }
            }
            TileProvider.NO_TILE
        }
    }

    companion object {
        const val DEFAULT_MAX_TILES = 120
        private const val AVG_TILE_BYTES = 18_432L
        private const val NAV_BUNDLE_OVERHEAD_BYTES = 24_576L

        fun estimateAreaDownload(
            center: LatLng,
            routePoints: List<LatLng>,
            radiusKm: Double,
            maxTiles: Int = DEFAULT_MAX_TILES
        ): OfflineAreaEstimate {
            val coords = computeAreaTileCoords(center, routePoints, radiusKm, maxTiles)
            return OfflineAreaEstimate(
                tileCount = coords.size,
                estimatedBytes = coords.size * AVG_TILE_BYTES + NAV_BUNDLE_OVERHEAD_BYTES,
                zoomRangeLabel = "Z11–Z14"
            )
        }

        fun computeAreaTileCoords(
            center: LatLng,
            routePoints: List<LatLng>,
            radiusKm: Double,
            maxTiles: Int = DEFAULT_MAX_TILES
        ): List<TileCoord> = computeBoundsAndCoords(center, routePoints, radiusKm, maxTiles).second

        private fun computeBoundsAndCoords(
            center: LatLng,
            routePoints: List<LatLng>,
            radiusKm: Double,
            maxTiles: Int
        ): Pair<DoubleArray, List<TileCoord>> {
            val allPoints = if (routePoints.isNotEmpty()) routePoints else listOf(center)
            val latDelta = radiusKm / 111.0
            val lngDelta = radiusKm / (111.0 * cos(Math.toRadians(center.latitude)).coerceAtLeast(0.25))

            val minLat = (allPoints.minOf { it.latitude } - latDelta * 0.45).coerceIn(-85.0, 85.0)
            val maxLat = (allPoints.maxOf { it.latitude } + latDelta * 0.45).coerceIn(-85.0, 85.0)
            val minLng = (allPoints.minOf { it.longitude } - lngDelta * 0.45).coerceIn(-180.0, 180.0)
            val maxLng = (allPoints.maxOf { it.longitude } + lngDelta * 0.45).coerceIn(-180.0, 180.0)

            val zoomLevels = listOf(11, 12, 13)
            val coordsToFetch = linkedSetOf<TileCoord>()

            if (routePoints.size >= 2) {
                val step = max(1, routePoints.size / 14)
                val sampled = routePoints.filterIndexed { idx, _ -> idx % step == 0 || idx == routePoints.lastIndex }
                for (z in zoomLevels) {
                    sampled.forEach { pt ->
                        val c = latLngToTile(pt.latitude, pt.longitude, z)
                        val pad = if (z <= 11) 1 else 0
                        for (dx in -pad..pad) {
                            for (dy in -pad..pad) {
                                coordsToFetch.add(TileCoord(z, c.x + dx, c.y + dy))
                            }
                        }
                    }
                }
            } else {
                for (z in zoomLevels) {
                    val nw = latLngToTile(maxLat, minLng, z)
                    val se = latLngToTile(minLat, maxLng, z)
                    val xStart = min(nw.x, se.x)
                    val xEnd = max(nw.x, se.x)
                    val yStart = min(nw.y, se.y)
                    val yEnd = max(nw.y, se.y)
                    for (x in xStart..xEnd) {
                        for (y in yStart..yEnd) {
                            coordsToFetch.add(TileCoord(z, x, y))
                        }
                    }
                }
            }

            val z14Anchors = if (routePoints.size >= 2) {
                listOf(routePoints.first(), routePoints[routePoints.size / 2], routePoints.last())
            } else {
                listOf(center)
            }
            z14Anchors.forEach { pt ->
                val c14 = latLngToTile(pt.latitude, pt.longitude, 14)
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        coordsToFetch.add(TileCoord(14, c14.x + dx, c14.y + dy))
                    }
                }
            }

            return doubleArrayOf(minLat, maxLat, minLng, maxLng) to coordsToFetch.take(maxTiles)
        }

        fun latLngToTile(lat: Double, lng: Double, zoom: Int): TileCoord {
            val n = 1 shl zoom
            val x = floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
            return TileCoord(zoom, x, y)
        }
    }
}
