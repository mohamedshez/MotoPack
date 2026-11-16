package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

enum class MaintenanceTaskType(
    val code: String,
    val defaultTitle: String,
    val defaultIntervalKm: Double,
    val checklistHint: String
) {
    OIL_CHANGE(
        code = "OIL_CHANGE",
        defaultTitle = "Engine Oil & Filter Change",
        defaultIntervalKm = 5000.0,
        checklistHint = "Drain warm oil, swap OEM filter, torque drain plug & check sight glass."
    ),
    CHAIN_ADJUST(
        code = "CHAIN_ADJUST",
        defaultTitle = "Chain Tension & Lube",
        defaultIntervalKm = 800.0,
        checklistHint = "Clean with kerosene/chain cleaner, adjust 25-35mm slack, apply O-ring wax."
    ),
    BRAKE_PADS(
        code = "BRAKE_PADS",
        defaultTitle = "Brake Pads & Fluid Check",
        defaultIntervalKm = 8000.0,
        checklistHint = "Inspect front/rear pad thickness (>1.5mm) and DOT4 fluid clarity."
    ),
    TIRES(
        code = "TIRES",
        defaultTitle = "Tire Tread & Pressure Check",
        defaultIntervalKm = 3000.0,
        checklistHint = "Verify cold PSI (36F / 42R typical), inspect center wear & sidewall cracks."
    ),
    VALVE_CHECK(
        code = "VALVE_CHECK",
        defaultTitle = "Valve Clearance Inspection",
        defaultIntervalKm = 24000.0,
        checklistHint = "Check intake & exhaust clearances on a cold engine; log shim sizes."
    ),
    AIR_FILTER(
        code = "AIR_FILTER",
        defaultTitle = "Air Filter & Spark Plugs",
        defaultIntervalKm = 12000.0,
        checklistHint = "Clean or replace airbox element and inspect plug gap & electrode color."
    ),
    CUSTOM(
        code = "CUSTOM",
        defaultTitle = "Custom Bike Service",
        defaultIntervalKm = 4000.0,
        checklistHint = "Custom mileage-based preventative inspection item."
    );

    companion object {
        fun fromCode(code: String): MaintenanceTaskType {
            return entries.firstOrNull { it.code == code } ?: CUSTOM
        }
    }
}

enum class MaintenanceHealthStatus {
    OVERDUE,
    DUE_SOON,
    GOOD
}

@IgnoreExtraProperties
data class GarageBike(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val makeModel: String = "",
    val year: Int = 2024,
    val currentOdometerKm: Double = 0.0,
    val engineCc: Int = 890,
    val tirePsiFront: Int = 36,
    val tirePsiRear: Int = 42,
    @get:PropertyName("isPrimary")
    @field:PropertyName("isPrimary")
    val isPrimary: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MaintenanceReminderItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bikeId: String = "",
    val bikeName: String = "",
    val taskType: String = "OIL_CHANGE",
    val title: String = "",
    val notes: String = "",
    val intervalKm: Double = 5000.0,
    val lastServiceOdometerKm: Double = 0.0,
    val nextDueOdometerKm: Double = 5000.0,
    val enabled: Boolean = true,
    val lastServicedAtMs: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ServiceHistoryLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bikeId: String = "",
    val bikeName: String = "",
    val taskType: String = "OIL_CHANGE",
    val title: String = "",
    val odometerKm: Double = 0.0,
    val costAmount: Double = 0.0,
    val shopOrDiy: String = "DIY Garage",
    val notes: String = "",
    val performedAtMs: Long = System.currentTimeMillis(),
    val createdAt: Timestamp? = null
)

data class ComputedMaintenanceStatus(
    val item: MaintenanceReminderItem,
    val currentOdometerKm: Double,
    val remainingKm: Double,
    val progressFraction: Float,
    val status: MaintenanceHealthStatus
)

object GarageMaintenanceCalculator {
    fun evaluate(
        item: MaintenanceReminderItem,
        currentOdometerKm: Double
    ): ComputedMaintenanceStatus {
        val interval = item.intervalKm.coerceAtLeast(100.0)
        val dueAt = item.nextDueOdometerKm
        val remaining = dueAt - currentOdometerKm
        val coveredSinceService = (currentOdometerKm - item.lastServiceOdometerKm).coerceAtLeast(0.0)
        val fraction = (coveredSinceService / interval).toFloat().coerceIn(0f, 1.2f)
        val dueSoonThreshold = (interval * 0.15).coerceIn(150.0, 600.0)
        val status = when {
            remaining <= 0.0 -> MaintenanceHealthStatus.OVERDUE
            remaining <= dueSoonThreshold -> MaintenanceHealthStatus.DUE_SOON
            else -> MaintenanceHealthStatus.GOOD
        }
        return ComputedMaintenanceStatus(
            item = item,
            currentOdometerKm = currentOdometerKm,
            remainingKm = remaining,
            progressFraction = fraction.coerceIn(0f, 1f),
            status = status
        )
    }

    fun starterRemindersForBike(
        userId: String,
        bikeId: String,
        bikeName: String,
        currentOdometerKm: Double
    ): List<MaintenanceReminderItem> {
        val nowMs = System.currentTimeMillis()
        return listOf(
            MaintenanceReminderItem(
                id = "${bikeId}_oil",
                userId = userId,
                bikeId = bikeId,
                bikeName = bikeName,
                taskType = MaintenanceTaskType.OIL_CHANGE.code,
                title = MaintenanceTaskType.OIL_CHANGE.defaultTitle,
                notes = MaintenanceTaskType.OIL_CHANGE.checklistHint,
                intervalKm = 5000.0,
                lastServiceOdometerKm = (currentOdometerKm - 1200.0).coerceAtLeast(0.0),
                nextDueOdometerKm = (currentOdometerKm - 1200.0).coerceAtLeast(0.0) + 5000.0,
                enabled = true,
                lastServicedAtMs = nowMs
            ),
            MaintenanceReminderItem(
                id = "${bikeId}_chain",
                userId = userId,
                bikeId = bikeId,
                bikeName = bikeName,
                taskType = MaintenanceTaskType.CHAIN_ADJUST.code,
                title = MaintenanceTaskType.CHAIN_ADJUST.defaultTitle,
                notes = MaintenanceTaskType.CHAIN_ADJUST.checklistHint,
                intervalKm = 800.0,
                lastServiceOdometerKm = (currentOdometerKm - 680.0).coerceAtLeast(0.0),
                nextDueOdometerKm = (currentOdometerKm - 680.0).coerceAtLeast(0.0) + 800.0,
                enabled = true,
                lastServicedAtMs = nowMs
            ),
            MaintenanceReminderItem(
                id = "${bikeId}_tires",
                userId = userId,
                bikeId = bikeId,
                bikeName = bikeName,
                taskType = MaintenanceTaskType.TIRES.code,
                title = MaintenanceTaskType.TIRES.defaultTitle,
                notes = MaintenanceTaskType.TIRES.checklistHint,
                intervalKm = 3000.0,
                lastServiceOdometerKm = (currentOdometerKm - 900.0).coerceAtLeast(0.0),
                nextDueOdometerKm = (currentOdometerKm - 900.0).coerceAtLeast(0.0) + 3000.0,
                enabled = true,
                lastServicedAtMs = nowMs
            )
        )
    }
}
