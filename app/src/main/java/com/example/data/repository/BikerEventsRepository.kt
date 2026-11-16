package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BikerEvent
import com.example.data.model.BikerLocation
import com.example.data.model.PassportStamp
import com.example.data.model.RidePack
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

class BikerEventsRepository(
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
        auth.currentUser?.uid ?: throw IllegalStateException("Google Sign-In required for Biker Events & Passport")

    fun observePassportStamps(userId: String): Flow<List<PassportStamp>> {
        val path = "users/$userId/passport_stamps"
        return db.collection("users").document(userId).collection("passport_stamps")
            .orderBy("stampedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(PassportStamp::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { err ->
                if (err !is CancellationException) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    fun observePersonalEvents(userId: String): Flow<List<BikerEvent>> {
        val path = "users/$userId/biker_events"
        return db.collection("users").document(userId).collection("biker_events")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(BikerEvent::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { err ->
                if (err !is CancellationException) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    fun observePackEvents(packId: String, currentUserId: String): Flow<List<BikerEvent>> {
        val path = "packs/$packId/events"
        return db.collection("packs").document(packId).collection("events")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(BikerEvent::class.java, ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { err ->
                if (err !is CancellationException) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun stampPassportStop(
        location: BikerLocation,
        brewNote: String,
        verifiedGps: Boolean
    ): Result<PassportStamp> = runCatching {
        val uid = requireUid()
        val stampId = location.id.take(128)
        val docRef = db.collection("users").document(uid)
            .collection("passport_stamps").document(stampId)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val payload = mapOf(
                "userId" to uid,
                "locationId" to location.id.take(128),
                "locationName" to location.name.take(100),
                "stampCode" to location.passportStampCode.ifBlank { "BB-STOP" }.take(24),
                "brewNote" to brewNote.trim().take(200),
                "lat" to location.lat,
                "lng" to location.lng,
                "verifiedGps" to verifiedGps,
                "stampedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } else {
            docRef.update(
                mapOf(
                    "brewNote" to brewNote.trim().take(200),
                    "verifiedGps" to verifiedGps,
                    "stampedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        docRef.get().await().toObject(PassportStamp::class.java, ServerTimestampBehavior.ESTIMATE)
            ?: PassportStamp(
                id = stampId,
                userId = uid,
                locationId = location.id,
                locationName = location.name,
                stampCode = location.passportStampCode,
                brewNote = brewNote,
                lat = location.lat,
                lng = location.lng,
                verifiedGps = verifiedGps
            )
    }.onFailure {
        if (it !is CancellationException) {
            handleFirestoreError(it, OperationType.WRITE, "users/${auth.currentUser?.uid}/passport_stamps")
        }
    }

    suspend fun createBikerEvent(
        activePack: RidePack?,
        creatorName: String,
        title: String,
        description: String,
        eventType: String,
        locationName: String,
        brewSpecialty: String,
        scheduledLabel: String,
        lat: Double,
        lng: Double,
        isPassportEvent: Boolean,
        locationId: String = ""
    ): Result<String> = runCatching {
        val uid = requireUid()
        val eventId = "evt_${UUID.randomUUID().toString().take(12)}"
        val safeTitle = title.trim().ifBlank { "Biker & Brew Meetup" }.take(100)
        val safeLoc = locationName.trim().ifBlank { "Ridge Coffee Hub" }.take(100)
        val safeCreator = creatorName.trim().ifBlank { "Rider" }.take(60)
        val safeType = if (eventType in listOf("BIKER_BREW", "BIKE_NIGHT", "RIDE_OUT", "SCENIC_MEET")) {
            eventType
        } else "BIKER_BREW"

        if (activePack != null) {
            val members = (activePack.memberUids + uid).distinct().take(30)
            val payload = hashMapOf<String, Any>(
                "packId" to activePack.id,
                "creatorId" to uid,
                "creatorName" to safeCreator,
                "title" to safeTitle,
                "description" to description.trim().take(300),
                "eventType" to safeType,
                "locationId" to locationId.take(128),
                "locationName" to safeLoc,
                "brewSpecialty" to brewSpecialty.trim().take(120),
                "scheduledLabel" to scheduledLabel.trim().ifBlank { "Sat • 09:00 AM" }.take(80),
                "lat" to lat.coerceIn(-90.0, 90.0),
                "lng" to lng.coerceIn(-180.0, 180.0),
                "attendeeUids" to listOf(uid),
                "memberUids" to members,
                "isPassportEvent" to isPassportEvent,
                "status" to "upcoming",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("packs").document(activePack.id)
                .collection("events").document(eventId)
                .set(payload).await()
        } else {
            val payload = hashMapOf<String, Any>(
                "packId" to "personal",
                "creatorId" to uid,
                "creatorName" to safeCreator,
                "title" to safeTitle,
                "description" to description.trim().take(300),
                "eventType" to safeType,
                "locationId" to locationId.take(128),
                "locationName" to safeLoc,
                "brewSpecialty" to brewSpecialty.trim().take(120),
                "scheduledLabel" to scheduledLabel.trim().ifBlank { "Sat • 09:00 AM" }.take(80),
                "lat" to lat.coerceIn(-90.0, 90.0),
                "lng" to lng.coerceIn(-180.0, 180.0),
                "attendeeUids" to listOf(uid),
                "memberUids" to listOf(uid),
                "isPassportEvent" to isPassportEvent,
                "status" to "upcoming",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid)
                .collection("biker_events").document(eventId)
                .set(payload).await()
        }
        eventId
    }.onFailure {
        if (it !is CancellationException) {
            handleFirestoreError(it, OperationType.CREATE, "biker_events")
        }
    }

    suspend fun toggleEventRsvp(
        event: BikerEvent,
        activePack: RidePack?
    ): Result<Boolean> = runCatching {
        val uid = requireUid()
        val isAttending = uid in event.attendeeUids
        val updatedAttendees = if (isAttending) {
            event.attendeeUids.filterNot { it == uid }
        } else {
            (event.attendeeUids + uid).distinct().take(30)
        }

        if (event.packId == "curated" || event.creatorId == "motopack_curated") {
            val docId = event.id.ifBlank { "curated_rsvp" }
            val personalRef = db.collection("users").document(uid)
                .collection("biker_events").document(docId)
            val snap = personalRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "packId" to "curated",
                    "creatorId" to uid,
                    "creatorName" to event.creatorName.ifBlank { "Biker & Brew Guild" }.take(60),
                    "title" to event.title.take(100),
                    "description" to event.description.take(300),
                    "eventType" to event.eventType,
                    "locationId" to event.locationId.take(128),
                    "locationName" to event.locationName.take(100),
                    "brewSpecialty" to event.brewSpecialty.take(120),
                    "scheduledLabel" to event.scheduledLabel.take(80),
                    "lat" to event.lat,
                    "lng" to event.lng,
                    "attendeeUids" to listOf(uid),
                    "memberUids" to listOf(uid),
                    "isPassportEvent" to event.isPassportEvent,
                    "status" to "upcoming",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                personalRef.set(payload).await()
                return@runCatching true
            } else {
                personalRef.update(
                    mapOf(
                        "attendeeUids" to updatedAttendees,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return@runCatching !isAttending
            }
        } else if (event.packId == "personal" || event.packId.isBlank()) {
            db.collection("users").document(uid)
                .collection("biker_events").document(event.id)
                .update(
                    mapOf(
                        "attendeeUids" to updatedAttendees,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            return@runCatching !isAttending
        } else {
            val packId = activePack?.id ?: event.packId
            db.collection("packs").document(packId)
                .collection("events").document(event.id)
                .update(
                    mapOf(
                        "attendeeUids" to updatedAttendees,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            return@runCatching !isAttending
        }
    }.onFailure {
        if (it !is CancellationException) {
            handleFirestoreError(it, OperationType.UPDATE, "biker_events/${event.id}")
        }
    }
}
