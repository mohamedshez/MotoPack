package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.GarageBike
import com.example.data.model.GarageMaintenanceCalculator
import com.example.data.model.MaintenanceReminderItem
import com.example.data.model.MaintenanceTaskType
import com.example.data.model.ServiceHistoryLog
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class GarageRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Google Sign-In required to save Garage records")

    fun observeBikes(userId: String): Flow<List<GarageBike>> {
        val path = "users/$userId/garage_bikes"
        return db.collection("users").document(userId).collection("garage_bikes")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(GarageBike::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { err ->
                if (err !is CancellationException) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    fun observeMaintenanceItems(userId: String): Flow<List<MaintenanceReminderItem>> {
        val path = "users/$userId/maintenance_items"
        return db.collection("users").document(userId).collection("maintenance_items")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(MaintenanceReminderItem::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { err ->
                if (err !is CancellationException) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    fun observeServiceLogs(userId: String): Flow<List<ServiceHistoryLog>> {
        val path = "users/$userId/service_logs"
        return db.collection("users").document(userId).collection("service_logs")
            .orderBy("performedAtMs", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(ServiceHistoryLog::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { err ->
                if (err !is CancellationException) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun addBikeWithStarterSchedule(
        name: String,
        makeModel: String,
        year: Int,
        currentOdometerKm: Double,
        engineCc: Int,
        tirePsiFront: Int,
        tirePsiRear: Int,
        isPrimary: Boolean
    ): Result<GarageBike> = runCatching {
        val uid = requireUid()
        val bikeId = "bike_${UUID.randomUUID().toString().take(10)}"
        val safeName = name.trim().ifBlank { "My Motorcycle" }.take(60)
        val safeModel = makeModel.trim().ifBlank { safeName }.take(80)
        val safeYear = year.coerceIn(1950, 2035)
        val safeOdo = currentOdometerKm.coerceIn(0.0, 1_000_000.0)
        val safeCc = engineCc.coerceIn(49, 3000)
        val safePsiF = tirePsiFront.coerceIn(10, 80)
        val safePsiR = tirePsiRear.coerceIn(10, 80)

        val userDoc = db.collection("users").document(uid)
        val payload = mapOf(
            "userId" to uid,
            "name" to safeName,
            "makeModel" to safeModel,
            "year" to safeYear,
            "currentOdometerKm" to safeOdo,
            "engineCc" to safeCc,
            "tirePsiFront" to safePsiF,
            "tirePsiRear" to safePsiR,
            "isPrimary" to isPrimary,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        userDoc.collection("garage_bikes").document(bikeId).set(payload).await()

        val starters = GarageMaintenanceCalculator.starterRemindersForBike(
            userId = uid,
            bikeId = bikeId,
            bikeName = safeName,
            currentOdometerKm = safeOdo
        )
        val batch = db.batch()
        starters.forEach { item ->
            val itemRef = userDoc.collection("maintenance_items").document(item.id)
            batch.set(
                itemRef,
                mapOf(
                    "userId" to uid,
                    "bikeId" to bikeId,
                    "bikeName" to safeName,
                    "taskType" to item.taskType,
                    "title" to item.title,
                    "notes" to item.notes,
                    "intervalKm" to item.intervalKm,
                    "lastServiceOdometerKm" to item.lastServiceOdometerKm,
                    "nextDueOdometerKm" to item.nextDueOdometerKm,
                    "enabled" to true,
                    "lastServicedAtMs" to item.lastServicedAtMs,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()

        GarageBike(
            id = bikeId,
            userId = uid,
            name = safeName,
            makeModel = safeModel,
            year = safeYear,
            currentOdometerKm = safeOdo,
            engineCc = safeCc,
            tirePsiFront = safePsiF,
            tirePsiRear = safePsiR,
            isPrimary = isPrimary
        )
    }.onFailure {
        if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "users/${auth.currentUser?.uid}/garage_bikes")
    }

    suspend fun updateBikeOdometer(bikeId: String, newOdometerKm: Double): Result<Unit> = runCatching {
        val uid = requireUid()
        val safeOdo = newOdometerKm.coerceIn(0.0, 1_000_000.0)
        db.collection("users").document(uid)
            .collection("garage_bikes").document(bikeId)
            .update(
                mapOf(
                    "currentOdometerKm" to safeOdo,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure {
        if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "users/${auth.currentUser?.uid}/garage_bikes/$bikeId")
    }

    suspend fun addMaintenanceReminder(
        bike: GarageBike,
        taskType: MaintenanceTaskType,
        title: String,
        intervalKm: Double,
        notes: String
    ): Result<String> = runCatching {
        val uid = requireUid()
        val itemId = "maint_${UUID.randomUUID().toString().take(10)}"
        val safeInterval = intervalKm.coerceIn(50.0, 100_000.0)
        val nextDue = bike.currentOdometerKm + safeInterval
        db.collection("users").document(uid)
            .collection("maintenance_items").document(itemId)
            .set(
                mapOf(
                    "userId" to uid,
                    "bikeId" to bike.id,
                    "bikeName" to bike.name.take(80),
                    "taskType" to taskType.code,
                    "title" to title.trim().ifBlank { taskType.defaultTitle }.take(100),
                    "notes" to notes.trim().take(300),
                    "intervalKm" to safeInterval,
                    "lastServiceOdometerKm" to bike.currentOdometerKm,
                    "nextDueOdometerKm" to nextDue,
                    "enabled" to true,
                    "lastServicedAtMs" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        itemId
    }.onFailure {
        if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "users/${auth.currentUser?.uid}/maintenance_items")
    }

    suspend fun completeServiceAndLogHistory(
        bike: GarageBike,
        reminder: MaintenanceReminderItem?,
        taskType: MaintenanceTaskType,
        title: String,
        odometerKm: Double,
        costAmount: Double,
        shopOrDiy: String,
        notes: String
    ): Result<String> = runCatching {
        val uid = requireUid()
        val logId = "log_${UUID.randomUUID().toString().take(12)}"
        val nowMs = System.currentTimeMillis()
        val safeOdo = odometerKm.coerceIn(0.0, 1_000_000.0)
        val userDoc = db.collection("users").document(uid)

        // 1. Append immutable service history log
        userDoc.collection("service_logs").document(logId)
            .set(
                mapOf(
                    "userId" to uid,
                    "bikeId" to bike.id,
                    "bikeName" to bike.name.take(80),
                    "taskType" to taskType.code,
                    "title" to title.trim().ifBlank { taskType.defaultTitle }.take(100),
                    "odometerKm" to safeOdo,
                    "costAmount" to costAmount.coerceIn(0.0, 100_000.0),
                    "shopOrDiy" to shopOrDiy.trim().ifBlank { "DIY Garage" }.take(80),
                    "notes" to notes.trim().take(300),
                    "performedAtMs" to nowMs,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

        // 2. If logged odometer exceeds bike's current reading, advance bike odometer
        if (safeOdo > bike.currentOdometerKm) {
            userDoc.collection("garage_bikes").document(bike.id)
                .update(
                    mapOf(
                        "currentOdometerKm" to safeOdo,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }

        // 3. Reset the linked mileage reminder interval
        if (reminder != null) {
            val nextDue = (safeOdo + reminder.intervalKm).coerceAtMost(1_100_000.0)
            userDoc.collection("maintenance_items").document(reminder.id)
                .update(
                    mapOf(
                        "lastServiceOdometerKm" to safeOdo,
                        "nextDueOdometerKm" to nextDue,
                        "lastServicedAtMs" to nowMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }
        logId
    }.onFailure {
        if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "users/${auth.currentUser?.uid}/service_logs")
    }

    suspend fun deleteMaintenanceReminder(itemId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("users").document(uid)
            .collection("maintenance_items").document(itemId)
            .delete().await()
        Unit
    }.onFailure {
        if (it !is CancellationException) handleFirestoreError(it, OperationType.DELETE, "users/${auth.currentUser?.uid}/maintenance_items/$itemId")
    }

    suspend fun deleteServiceLog(logId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("users").document(uid)
            .collection("service_logs").document(logId)
            .delete().await()
        Unit
    }.onFailure {
        if (it !is CancellationException) handleFirestoreError(it, OperationType.DELETE, "users/${auth.currentUser?.uid}/service_logs/$logId")
    }
}
