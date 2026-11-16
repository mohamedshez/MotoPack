package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.RideTrip
import com.example.data.remote.ElevationSample
import com.example.data.remote.FuelStationStop
import com.example.ui.theme.TelemetryMonoFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

data class ElevationGraphMetrics(
    val minElevationM: Double,
    val maxElevationM: Double,
    val totalAscentM: Double,
    val totalDescentM: Double,
    val netChangeM: Double,
    val totalDistanceKm: Double,
    val maxGradePercent: Double,
    val segmentGradesPercent: List<Double>,
    val peakIndex: Int
)

fun computeElevationMetrics(samples: List<ElevationSample>): ElevationGraphMetrics {
    if (samples.isEmpty()) {
        return ElevationGraphMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList(), 0)
    }
    val minElev = samples.minOf { it.elevationM }
    val maxElev = samples.maxOf { it.elevationM }
    val peakIdx = samples.indices.maxByOrNull { samples[it].elevationM } ?: 0
    val totalDist = (samples.last().distanceKm - samples.first().distanceKm)
        .coerceAtLeast(samples.last().distanceKm)
        .coerceAtLeast(0.0)

    var ascent = 0.0
    var descent = 0.0
    val grades = mutableListOf<Double>()

    for (i in 1 until samples.size) {
        val prev = samples[i - 1]
        val curr = samples[i]
        val dElev = curr.elevationM - prev.elevationM
        if (dElev > 0) ascent += dElev else descent += abs(dElev)
        val segDistM = ((curr.distanceKm - prev.distanceKm) * 1000.0).coerceAtLeast(35.0)
        val gradePct = ((dElev / segDistM) * 100.0).coerceIn(-35.0, 35.0)
        grades.add(gradePct)
    }

    val maxGrade = grades.maxOfOrNull { abs(it) } ?: 0.0
    val net = samples.last().elevationM - samples.first().elevationM

    return ElevationGraphMetrics(
        minElevationM = minElev,
        maxElevationM = maxElev,
        totalAscentM = ascent,
        totalDescentM = descent,
        netChangeM = net,
        totalDistanceKm = totalDist,
        maxGradePercent = maxGrade,
        segmentGradesPercent = grades,
        peakIndex = peakIdx
    )
}

fun buildEstimatedTripElevationSamples(trip: RideTrip): List<ElevationSample> {
    val totalDist = trip.distanceKm.coerceAtLeast(0.5)
    val baseAlt = 120.0
    val net = trip.elevationChangeNetM
    val climb = trip.elevationGainM.coerceAtLeast(abs(net)).coerceAtLeast(18.0)
    val ridgeAmplitude = (climb - net.coerceAtLeast(0.0)).coerceAtLeast(15.0) * 0.55
    val count = 12
    return (0 until count).map { i ->
        val f = i.toDouble() / (count - 1)
        val elev = baseAlt + net * f + sin(f * Math.PI) * ridgeAmplitude
        ElevationSample(
            distanceKm = totalDist * f,
            elevationM = elev,
            lat = trip.startLat + (trip.endLat - trip.startLat) * f,
            lng = trip.startLng + (trip.endLng - trip.startLng) * f
        )
    }
}

@Composable
fun ElevationGraphCard(
    title: String,
    samples: List<ElevationSample>,
    sourceLabel: String,
    fuelStations: List<FuelStationStop> = emptyList(),
    modifier: Modifier = Modifier,
    testTagPrefix: String = "elevation_graph"
) {
    val metrics = remember(samples) { computeElevationMetrics(samples) }
    var inspectedIndex by remember(samples) { mutableStateOf<Int?>(null) }

    val gentleColor = MaterialTheme.colorScheme.secondary
    val moderateColor = MaterialTheme.colorScheme.primary
    val steepColor = MaterialTheme.colorScheme.error

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terrain,
                        contentDescription = null,
                        tint = gentleColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = gentleColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = gentleColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Live Scrub / Telemetry Inspect Strip
            val activeIdx = inspectedIndex?.coerceIn(0, (samples.size - 1).coerceAtLeast(0))
            val inspectedSample = activeIdx?.let { samples.getOrNull(it) }
            val inspectedGrade = activeIdx?.let { idx ->
                when {
                    metrics.segmentGradesPercent.isEmpty() -> 0.0
                    idx == 0 -> metrics.segmentGradesPercent.first()
                    else -> metrics.segmentGradesPercent.getOrElse(idx - 1) { 0.0 }
                }
            }
            val nearbyFuel = inspectedSample?.let { s ->
                fuelStations.minByOrNull { abs(it.distanceAlongKm - s.distanceKm) }
                    ?.takeIf { abs(it.distanceAlongKm - s.distanceKm) <= (metrics.totalDistanceKm * 0.08).coerceAtLeast(0.8) }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_inspect_bar")
            ) {
                if (inspectedSample != null && inspectedGrade != null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(
                                "%.1f km • %.0f m • %+.1f%% grade",
                                inspectedSample.distanceKm,
                                inspectedSample.elevationM,
                                inspectedGrade
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = TelemetryMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (nearbyFuel != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalGasStation,
                                    contentDescription = nearbyFuel.name,
                                    tint = moderateColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = nearbyFuel.name.take(14),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = moderateColor
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Drag or tap profile to inspect altitude & grade %",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            GradeDotLegend("<3%", gentleColor)
                            GradeDotLegend("3-7%", moderateColor)
                            GradeDotLegend(">7%", steepColor)
                        }
                    }
                }
            }

            InteractiveElevationGraph(
                samples = samples,
                metrics = metrics,
                fuelStations = fuelStations,
                inspectedIndex = activeIdx,
                onInspectIndexChange = { inspectedIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .testTag("${testTagPrefix}_canvas")
            )

            // Telemetry Metric Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ElevationStatPill(
                    label = "Summit",
                    value = String.format("%.0fm", metrics.maxElevationM),
                    accent = moderateColor,
                    modifier = Modifier.weight(1f)
                )
                ElevationStatPill(
                    label = "Climb",
                    value = String.format("+%.0fm", metrics.totalAscentM),
                    accent = gentleColor,
                    modifier = Modifier.weight(1f)
                )
                ElevationStatPill(
                    label = "Descent",
                    value = String.format("-%.0fm", metrics.totalDescentM),
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                ElevationStatPill(
                    label = "Max Grade",
                    value = String.format("%.1f%%", metrics.maxGradePercent),
                    accent = if (metrics.maxGradePercent >= 7.0) steepColor else moderateColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun InteractiveElevationGraph(
    samples: List<ElevationSample>,
    metrics: ElevationGraphMetrics = remember(samples) { computeElevationMetrics(samples) },
    fuelStations: List<FuelStationStop> = emptyList(),
    inspectedIndex: Int? = null,
    onInspectIndexChange: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gentleColor = MaterialTheme.colorScheme.secondary
    val moderateColor = MaterialTheme.colorScheme.primary
    val steepColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val crosshairColor = MaterialTheme.colorScheme.onSurface

    if (samples.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Ride or plan a route to display elevation terrain",
                style = MaterialTheme.typography.bodySmall,
                color = labelColor
            )
        }
        return
    }

    val minElev = metrics.minElevationM
    val maxElev = metrics.maxElevationM
    val span = (maxElev - minElev).coerceAtLeast(25.0)
    val midElev = (minElev + maxElev) / 2.0
    val totalDist = metrics.totalDistanceKm.coerceAtLeast(0.1)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Y-axis altitude labels
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${maxElev.roundToInt()}m",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TelemetryMonoFontFamily,
                    color = moderateColor
                )
                Text(
                    text = "${midElev.roundToInt()}m",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TelemetryMonoFontFamily,
                    color = labelColor
                )
                Text(
                    text = "${minElev.roundToInt()}m",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TelemetryMonoFontFamily,
                    color = labelColor
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(samples) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val idx = (frac * (samples.size - 1)).roundToInt().coerceIn(0, samples.lastIndex)
                            onInspectIndexChange(if (inspectedIndex == idx) null else idx)
                        }
                    }
                    .pointerInput(samples) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val idx = (frac * (samples.size - 1)).roundToInt().coerceIn(0, samples.lastIndex)
                            onInspectIndexChange(idx)
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val padY = 12.dp.toPx()
                val usableH = (h - padY * 2f).coerceAtLeast(12f)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                // Horizontal reference gridlines
                listOf(0f, 0.5f, 1f).forEach { frac ->
                    val y = padY + usableH * frac
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }

                val coords = samples.mapIndexed { idx, s ->
                    val x = (idx.toFloat() / (samples.size - 1)) * w
                    val normY = ((s.elevationM - minElev) / span).toFloat().coerceIn(0f, 1f)
                    val y = padY + usableH * (1f - normY)
                    Offset(x, y)
                }

                // Fill terrain silhouette beneath curve
                val fillPath = Path().apply {
                    moveTo(coords.first().x, h)
                    coords.forEach { pt -> lineTo(pt.x, pt.y) }
                    lineTo(coords.last().x, h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            moderateColor.copy(alpha = 0.34f),
                            gentleColor.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )

                // Grade-colored route terrain segments
                for (i in 1 until coords.size) {
                    val grade = abs(metrics.segmentGradesPercent.getOrElse(i - 1) { 0.0 })
                    val segColor = when {
                        grade >= 7.0 -> steepColor
                        grade >= 3.0 -> moderateColor
                        else -> gentleColor
                    }
                    drawLine(
                        color = segColor,
                        start = coords[i - 1],
                        end = coords[i],
                        strokeWidth = 3.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Plot Fuel Station pins along the terrain profile
                fuelStations.forEach { station ->
                    val frac = (station.distanceAlongKm / totalDist).toFloat().coerceIn(0f, 1f)
                    val sampleIdx = (frac * (coords.size - 1)).roundToInt().coerceIn(0, coords.lastIndex)
                    val pt = coords[sampleIdx]
                    drawCircle(
                        color = Color.White,
                        radius = 5.5.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = Color(0xFFFFB300),
                        radius = 3.8.dp.toPx(),
                        center = pt
                    )
                }

                // Summit peak marker
                val peakPt = coords[metrics.peakIndex.coerceIn(0, coords.lastIndex)]
                drawCircle(
                    color = moderateColor.copy(alpha = 0.3f),
                    radius = 8.dp.toPx(),
                    center = peakPt
                )
                drawCircle(
                    color = moderateColor,
                    radius = 4.dp.toPx(),
                    center = peakPt
                )

                // Scrubber cursor & active point halo
                if (inspectedIndex != null && inspectedIndex in coords.indices) {
                    val focus = coords[inspectedIndex]
                    drawLine(
                        color = crosshairColor.copy(alpha = 0.65f),
                        start = Offset(focus.x, 0f),
                        end = Offset(focus.x, h),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(
                        color = crosshairColor,
                        radius = 6.dp.toPx(),
                        center = focus
                    )
                    drawCircle(
                        color = moderateColor,
                        radius = 3.5.dp.toPx(),
                        center = focus
                    )
                }
            }
        }

        // X-axis distance ticks aligned under canvas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 50.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format("%.1f km", samples.first().distanceKm),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TelemetryMonoFontFamily,
                color = labelColor
            )
            Text(
                text = String.format("%.1f km", (samples.first().distanceKm + totalDist * 0.5)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TelemetryMonoFontFamily,
                color = labelColor
            )
            Text(
                text = String.format("%.1f km", samples.last().distanceKm),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TelemetryMonoFontFamily,
                color = labelColor
            )
        }
    }
}

@Composable
fun CompactElevationSparkline(
    samples: List<ElevationSample>,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.secondary
    val peakAccent = MaterialTheme.colorScheme.primary
    if (samples.size < 2) return

    val minElev = samples.minOf { it.elevationM }
    val maxElev = samples.maxOf { it.elevationM }
    val span = (maxElev - minElev).coerceAtLeast(15.0)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = 3.dp.toPx()
        val usableH = (h - padY * 2f).coerceAtLeast(4f)
        val linePath = Path()
        val fillPath = Path()

        samples.forEachIndexed { idx, s ->
            val x = (idx.toFloat() / (samples.size - 1)) * w
            val normY = ((s.elevationM - minElev) / span).toFloat().coerceIn(0f, 1f)
            val y = padY + usableH * (1f - normY)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (idx == samples.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(peakAccent.copy(alpha = 0.28f), accent.copy(alpha = 0.08f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            brush = Brush.horizontalGradient(listOf(accent, peakAccent)),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun GradeDotLegend(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ElevationStatPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = TelemetryMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1
            )
        }
    }
}
