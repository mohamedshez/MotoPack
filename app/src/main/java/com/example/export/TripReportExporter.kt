package com.example.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.RideTrip
import com.example.data.remote.ElevationSample
import com.example.ui.screens.buildEstimatedTripElevationSamples
import com.example.ui.screens.computeElevationMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object TripReportExporter {
    private const val PAGE_WIDTH_PT = 595
    private const val PAGE_HEIGHT_PT = 842
    private const val MARGIN_PT = 32f

    fun buildSuggestedPdfFileName(trip: RideTrip): String {
        val cleanTitle = trip.title.ifBlank { "MotoPack_Ride" }
            .replace(Regex("[^a-zA-Z0-9_\\-]+"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "MotoPack_Ride" }
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
            .format(trip.createdAt?.toDate() ?: Date())
        return "${cleanTitle}_${dateStamp}.pdf"
    }

    fun buildSocialShareCaption(
        trip: RideTrip,
        riderName: String,
        bikeModel: String
    ): String {
        val safeTitle = trip.title.ifBlank { "MotoPack Ride" }
        val riderLine = listOf(riderName.trim(), bikeModel.trim())
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        return buildString {
            append("🏍️ MotoPack Ride Report: $safeTitle")
            if (riderLine.isNotBlank()) {
                append("\nRider: $riderLine")
            }
            append(
                String.format(
                    Locale.US,
                    "\n📏 %.1f km  |  ⚡ %.0f km/h avg  |  ⛰️ +%.0f m climb (%+.0f m net)  |  ⏱️ %d min",
                    trip.distanceKm,
                    trip.avgSpeedKmh,
                    trip.elevationGainM,
                    trip.elevationChangeNetM,
                    trip.durationMin
                )
            )
            if (trip.weatherSummary.isNotBlank()) {
                append("\n🌤️ Weather (${trip.weatherSeverity}): ${trip.weatherSummary}")
            }
            append("\n#MotoPack #MotorcycleRide #TwoWheels")
        }
    }

    suspend fun createShareablePdfFile(
        context: Context,
        trip: RideTrip,
        elevationSamples: List<ElevationSample>,
        riderName: String,
        bikeModel: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outFile = File(exportDir, buildSuggestedPdfFileName(trip))
            FileOutputStream(outFile).use { stream ->
                writeTripReportPdfToStream(
                    outputStream = stream,
                    trip = trip,
                    elevationSamples = elevationSamples,
                    riderName = riderName,
                    bikeModel = bikeModel
                )
            }
            outFile
        }
    }

    suspend fun writeTripReportPdfToUri(
        context: Context,
        destinationUri: Uri,
        trip: RideTrip,
        elevationSamples: List<ElevationSample>,
        riderName: String,
        bikeModel: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IllegalStateException("Unable to open destination document for PDF export.")
            stream.use { output ->
                writeTripReportPdfToStream(
                    outputStream = output,
                    trip = trip,
                    elevationSamples = elevationSamples,
                    riderName = riderName,
                    bikeModel = bikeModel
                )
            }
            Unit
        }
    }

    fun launchShareTripReportChooser(
        context: Context,
        pdfFile: File,
        trip: RideTrip,
        riderName: String,
        bikeModel: String
    ) {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, pdfFile)
        val caption = buildSocialShareCaption(trip, riderName, bikeModel)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "MotoPack Ride Report: ${trip.title.ifBlank { "Ride" }}")
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = ClipData.newUri(context.contentResolver, "MotoPack Trip Report", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Ride Report PDF").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    fun writeTripReportPdfToStream(
        outputStream: OutputStream,
        trip: RideTrip,
        elevationSamples: List<ElevationSample>,
        riderName: String,
        bikeModel: String
    ) {
        val resolvedSamples = if (elevationSamples.size >= 2) {
            elevationSamples
        } else {
            buildEstimatedTripElevationSamples(trip)
        }
        val elevMetrics = computeElevationMetrics(resolvedSamples)

        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            drawReportPage(
                canvas = canvas,
                trip = trip,
                samples = resolvedSamples,
                hasTrackedSamples = elevationSamples.size >= 2,
                metrics = elevMetrics,
                riderName = riderName,
                bikeModel = bikeModel
            )

            document.finishPage(page)
            document.writeTo(outputStream)
            outputStream.flush()
        } finally {
            document.close()
        }
    }

    private fun drawReportPage(
        canvas: Canvas,
        trip: RideTrip,
        samples: List<ElevationSample>,
        hasTrackedSamples: Boolean,
        metrics: com.example.ui.screens.ElevationGraphMetrics,
        riderName: String,
        bikeModel: String
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH_PT.toFloat(), PAGE_HEIGHT_PT.toFloat(), bgPaint)

        val contentWidth = PAGE_WIDTH_PT - MARGIN_PT * 2f

        // 1. Header Banner
        val headerRect = RectF(MARGIN_PT, MARGIN_PT, MARGIN_PT + contentWidth, MARGIN_PT + 116f)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                headerRect.left,
                headerRect.top,
                headerRect.right,
                headerRect.bottom,
                Color.parseColor("#0F172A"),
                Color.parseColor("#1E293B"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(headerRect, 16f, 16f, headerPaint)

        // Emerald accent bar on left side of header
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#10B981") }
        canvas.drawRoundRect(
            RectF(headerRect.left, headerRect.top, headerRect.left + 8f, headerRect.bottom),
            4f,
            4f,
            accentPaint
        )

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34D399")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("MOTOPACK • OFFICIAL TRIP SUMMARY REPORT", headerRect.left + 22f, headerRect.top + 28f, badgeTextPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val safeTitle = trip.title.ifBlank { "MotoPack Group Ride" }.take(38)
        canvas.drawText(safeTitle, headerRect.left + 22f, headerRect.top + 56f, titlePaint)

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 11f
        }
        val dateText = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US)
            .format(trip.createdAt?.toDate() ?: Date())
        val riderLine = listOfNotNull(
            riderName.trim().takeIf { it.isNotBlank() }?.let { "Rider: $it" },
            bikeModel.trim().takeIf { it.isNotBlank() }?.let { "Bike: $it" }
        ).joinToString("   •   ")
        canvas.drawText(
            if (riderLine.isNotBlank()) riderLine else "Rider Telemetry Verified",
            headerRect.left + 22f,
            headerRect.top + 78f,
            metaPaint
        )
        canvas.drawText("Logged: $dateText", headerRect.left + 22f, headerRect.top + 96f, metaPaint)

        // Duration pill in top-right of header
        val durText = "${trip.durationMin} MIN"
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#10B981") }
        val pillRect = RectF(headerRect.right - 92f, headerRect.top + 16f, headerRect.right - 16f, headerRect.top + 40f)
        canvas.drawRoundRect(pillRect, 10f, 10f, pillPaint)
        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#052E16")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(durText, pillRect.centerX(), pillRect.centerY() + 4f, pillTextPaint)

        // 2. 2x2 Telemetry Cards
        var cursorY = headerRect.bottom + 18f
        val gap = 12f
        val cardW = (contentWidth - gap) / 2f
        val cardH = 74f

        drawMetricCard(
            canvas = canvas,
            rect = RectF(MARGIN_PT, cursorY, MARGIN_PT + cardW, cursorY + cardH),
            label = "DISTANCE COVERED",
            value = String.format(Locale.US, "%.1f km", trip.distanceKm),
            sub = "GPS Route Telemetry",
            accentColor = Color.parseColor("#2563EB")
        )
        drawMetricCard(
            canvas = canvas,
            rect = RectF(MARGIN_PT + cardW + gap, cursorY, MARGIN_PT + contentWidth, cursorY + cardH),
            label = "AVERAGE SPEED",
            value = String.format(Locale.US, "%.0f km/h", trip.avgSpeedKmh),
            sub = "Moving Convoy Pace",
            accentColor = Color.parseColor("#059669")
        )

        cursorY += cardH + gap
        drawMetricCard(
            canvas = canvas,
            rect = RectF(MARGIN_PT, cursorY, MARGIN_PT + cardW, cursorY + cardH),
            label = "ELEVATION PROFILE",
            value = String.format(Locale.US, "%+.0f m net", trip.elevationChangeNetM),
            sub = String.format(Locale.US, "+%.0f m total ascent", trip.elevationGainM),
            accentColor = Color.parseColor("#D97706")
        )
        drawMetricCard(
            canvas = canvas,
            rect = RectF(MARGIN_PT + cardW + gap, cursorY, MARGIN_PT + contentWidth, cursorY + cardH),
            label = "POWER PROFILE",
            value = if (trip.batteryEfficiencyUsed) "Eco 20s Mode" else "Live 5s Mode",
            sub = if (trip.batteryPercentUsed > 0) "-${trip.batteryPercentUsed}% battery used" else "Minimal battery drain",
            accentColor = Color.parseColor("#7C3AED")
        )

        // 3. Vector Elevation Terrain Graph Card
        cursorY += cardH + 18f
        val graphCardHeight = 236f
        val graphCardRect = RectF(MARGIN_PT, cursorY, MARGIN_PT + contentWidth, cursorY + graphCardHeight)
        drawElevationGraphSection(
            canvas = canvas,
            cardRect = graphCardRect,
            trip = trip,
            samples = samples,
            hasTrackedSamples = hasTrackedSamples,
            metrics = metrics
        )

        // 4. Weather Conditions Card
        cursorY = graphCardRect.bottom + 16f
        val wxCardRect = RectF(MARGIN_PT, cursorY, MARGIN_PT + contentWidth, cursorY + 92f)
        drawWeatherSection(canvas, wxCardRect, trip)

        // 5. GPS Coordinates & Footer Card
        cursorY = wxCardRect.bottom + 16f
        val coordRect = RectF(MARGIN_PT, cursorY, MARGIN_PT + contentWidth, cursorY + 76f)
        drawCoordinatesAndFooter(canvas, coordRect, trip)
    }

    private fun drawMetricCard(
        canvas: Canvas,
        rect: RectF,
        label: String,
        value: String,
        sub: String,
        accentColor: Int
    ) {
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRoundRect(rect, 12f, 12f, cardPaint)
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)

        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRoundRect(
            RectF(rect.left + 10f, rect.top + 12f, rect.left + 14f, rect.bottom - 12f),
            2f,
            2f,
            stripePaint
        )

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 10f
        }

        canvas.drawText(label, rect.left + 24f, rect.top + 22f, labelPaint)
        canvas.drawText(value, rect.left + 24f, rect.top + 45f, valuePaint)
        canvas.drawText(sub, rect.left + 24f, rect.top + 62f, subPaint)
    }

    private fun drawElevationGraphSection(
        canvas: Canvas,
        cardRect: RectF,
        trip: RideTrip,
        samples: List<ElevationSample>,
        hasTrackedSamples: Boolean,
        metrics: com.example.ui.screens.ElevationGraphMetrics
    ) {
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRoundRect(cardRect, 14f, 14f, cardPaint)
        canvas.drawRoundRect(cardRect, 14f, 14f, borderPaint)

        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("RIDE ELEVATION & GRADE PROFILE", cardRect.left + 16f, cardRect.top + 24f, sectionTitlePaint)

        val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#059669")
            textSize = 10f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            if (hasTrackedSamples) "GPS TRACKED SAMPLES" else "TELEMETRY ESTIMATE",
            cardRect.right - 16f,
            cardRect.top + 24f,
            sourcePaint
        )

        val plotLeft = cardRect.left + 56f
        val plotRight = cardRect.right - 18f
        val plotTop = cardRect.top + 42f
        val plotBottom = cardRect.top + 166f
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(10f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(10f)

        val minElev = metrics.minElevationM
        val maxElev = metrics.maxElevationM
        val span = (maxElev - minElev).coerceAtLeast(25.0)
        val midElev = (minElev + maxElev) / 2.0

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        }
        val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.MONOSPACE
        }

        listOf(0f to maxElev, 0.5f to midElev, 1f to minElev).forEach { (frac, elevVal) ->
            val y = plotTop + plotHeight * frac
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
            canvas.drawText("${elevVal.roundToInt()}m", plotLeft - 6f, y + 3f, axisTextPaint)
        }

        if (samples.size >= 2) {
            val pts = samples.mapIndexed { idx, sample ->
                val x = plotLeft + (idx.toFloat() / (samples.size - 1)) * plotWidth
                val normY = ((sample.elevationM - minElev) / span).toFloat().coerceIn(0f, 1f)
                val y = plotTop + plotHeight * (1f - normY)
                x to y
            }

            val fillPath = Path().apply {
                moveTo(pts.first().first, plotBottom)
                pts.forEach { (x, y) -> lineTo(x, y) }
                lineTo(pts.last().first, plotBottom)
                close()
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    plotTop,
                    0f,
                    plotBottom,
                    Color.parseColor("#552563EB"),
                    Color.parseColor("#1110B981"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(fillPath, fillPaint)

            val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
            }
            for (i in 1 until pts.size) {
                val grade = abs(metrics.segmentGradesPercent.getOrElse(i - 1) { 0.0 })
                segPaint.color = when {
                    grade >= 7.0 -> Color.parseColor("#DC2626")
                    grade >= 3.0 -> Color.parseColor("#2563EB")
                    else -> Color.parseColor("#059669")
                }
                canvas.drawLine(pts[i - 1].first, pts[i - 1].second, pts[i].first, pts[i].second, segPaint)
            }

            // Summit marker
            val peakIdx = metrics.peakIndex.coerceIn(0, pts.lastIndex)
            val (px, py) = pts[peakIdx]
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#332563EB") }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563EB") }
            canvas.drawCircle(px, py, 7f, haloPaint)
            canvas.drawCircle(px, py, 3.8f, dotPaint)
        }

        // X-axis distance labels
        val distPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
        }
        val firstDist = samples.firstOrNull()?.distanceKm ?: 0.0
        val totalDist = metrics.totalDistanceKm.coerceAtLeast(trip.distanceKm)
        distPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(String.format(Locale.US, "%.1f km", firstDist), plotLeft, plotBottom + 14f, distPaint)
        distPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(String.format(Locale.US, "%.1f km", firstDist + totalDist * 0.5), (plotLeft + plotRight) / 2f, plotBottom + 14f, distPaint)
        distPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(String.format(Locale.US, "%.1f km", firstDist + totalDist), plotRight, plotBottom + 14f, distPaint)

        // Bottom 4 elevation stats pills
        val pillTop = plotBottom + 24f
        val pillBottom = cardRect.bottom - 12f
        val pillGap = 8f
        val pillW = (cardRect.width() - 32f - pillGap * 3f) / 4f
        val stats = listOf(
            "SUMMIT" to String.format(Locale.US, "%.0f m", metrics.maxElevationM),
            "CLIMB" to String.format(Locale.US, "+%.0f m", metrics.totalAscentM),
            "DESCENT" to String.format(Locale.US, "-%.0f m", metrics.totalDescentM),
            "MAX GRADE" to String.format(Locale.US, "%.1f%%", metrics.maxGradePercent)
        )
        val statBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F1F5F9") }
        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        stats.forEachIndexed { idx, (label, value) ->
            val left = cardRect.left + 16f + idx * (pillW + pillGap)
            val r = RectF(left, pillTop, left + pillW, pillBottom)
            canvas.drawRoundRect(r, 8f, 8f, statBgPaint)
            canvas.drawText(label, r.centerX(), r.top + 13f, statLabelPaint)
            canvas.drawText(value, r.centerX(), r.top + 27f, statValuePaint)
        }
    }

    private fun drawWeatherSection(canvas: Canvas, rect: RectF, trip: RideTrip) {
        val severity = trip.weatherSeverity.ifBlank { "CLEAR" }.uppercase(Locale.US)
        val accentHex = when (severity) {
            "WARNING" -> "#DC2626"
            "ADVISORY" -> "#D97706"
            else -> "#059669"
        }
        val accentColor = Color.parseColor(accentHex)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 140
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
        }
        canvas.drawRoundRect(rect, 14f, 14f, bgPaint)
        canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("WEATHER CONDITIONS ENCOUNTERED", rect.left + 16f, rect.top + 24f, titlePaint)

        val badgeRect = RectF(rect.right - 98f, rect.top + 12f, rect.right - 16f, rect.top + 32f)
        val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor; alpha = 35 }
        val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 10f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawRoundRect(badgeRect, 6f, 6f, badgeBg)
        canvas.drawText(severity, badgeRect.centerX(), badgeRect.centerY() + 3.5f, badgeText)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 11.5f
        }
        val summary = trip.weatherSummary.ifBlank { "Clear riding conditions along convoy route" }.take(86)
        canvas.drawText(summary, rect.left + 16f, rect.top + 50f, bodyPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 10f
        }
        val windLine = if (trip.maxWindGustKmh > 0.0) {
            String.format(Locale.US, "Peak wind gusts recorded: %.0f km/h", trip.maxWindGustKmh)
        } else {
            "Calm to moderate crosswind profile"
        }
        canvas.drawText(windLine, rect.left + 16f, rect.top + 72f, subPaint)
    }

    private fun drawCoordinatesAndFooter(canvas: Canvas, rect: RectF, trip: RideTrip) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
        canvas.drawRoundRect(rect, 14f, 14f, bgPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34D399")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText("START & END GPS ANCHORS", rect.left + 16f, rect.top + 22f, labelPaint)
        canvas.drawText(
            String.format(
                Locale.US,
                "Start: %.4f, %.4f    •    Finish: %.4f, %.4f",
                trip.startLat,
                trip.startLng,
                trip.endLat,
                trip.endLng
            ),
            rect.left + 16f,
            rect.top + 42f,
            coordPaint
        )
        canvas.drawText(
            "Generated by MotoPack • Shareable Rider Telemetry Report",
            rect.left + 16f,
            rect.top + 61f,
            footerPaint
        )
    }
}
