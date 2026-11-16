package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.crypto.PackCrypto
import com.example.data.crypto.RiderTelemetryPayload
import com.example.data.model.MapChallenge
import com.example.data.model.PackGalleryPhoto
import com.example.data.model.PackInvite
import com.example.data.model.PackMapPin
import com.example.data.model.PackMemberTelemetry
import com.example.data.model.PackMessage
import com.example.data.model.RidePack
import com.example.data.model.RideTrip
import com.example.data.model.UrgentPing
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID

class MotoPackRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    internal val firestoreDb: FirebaseFirestore get() = db
    internal val firebaseAuth: FirebaseAuth get() = auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                logFlowError(error, OperationType.GET, path, userId)
                throw error
            }
    }

    suspend fun ensureUserProfile(
        displayName: String,
        photoUrl: String,
        bikeModel: String = "Adventure Tourer"
    ): Result<UserProfile> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val safeName = displayName.trim().ifBlank { "Rider" }.take(60)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "photoUrl" to photoUrl.take(500),
                "bikeModel" to bikeModel.take(80),
                "phoneNumber" to "",
                "activePackId" to "",
                "totalDistanceKm" to 0.0,
                "totalElevationM" to 0.0,
                "ridesCompleted" to 0L,
                "earnedMilestones" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            docRef.get().await().toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, userId = uid, displayName = safeName, photoUrl = photoUrl, bikeModel = bikeModel)
        } else {
            snap.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, userId = uid, displayName = displayName, photoUrl = photoUrl)
        }
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "users/${auth.currentUser?.uid}") }

    suspend fun updateUserProfile(
        displayName: String,
        bikeModel: String,
        phoneNumber: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val updates = mapOf(
            "displayName" to displayName.trim().ifBlank { "Rider" }.take(60),
            "bikeModel" to bikeModel.trim().take(80),
            "phoneNumber" to phoneNumber.trim().take(30),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(updates).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "users/${auth.currentUser?.uid}") }

    fun observeUserTrips(userId: String): Flow<List<RideTrip>> {
        val path = "users/$userId/trips"
        return db.collection("users").document(userId).collection("trips")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(RideTrip::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    suspend fun logRideTrip(
        packId: String,
        title: String,
        distanceKm: Double,
        elevationGainM: Double,
        durationMin: Long,
        avgSpeedKmh: Double,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        elevationChangeNetM: Double = 0.0,
        weatherSummary: String = "Clear conditions",
        weatherSeverity: String = "CLEAR",
        maxWindGustKmh: Double = 0.0,
        batteryEfficiencyUsed: Boolean = false,
        batteryPercentUsed: Int = 0
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val tripRef = db.collection("users").document(uid).collection("trips").document()
        val payload = mapOf(
            "userId" to uid,
            "packId" to packId.take(128),
            "title" to title.trim().ifBlank { "Pack Ride" }.take(100),
            "distanceKm" to distanceKm.coerceIn(0.0, 20000.0),
            "elevationGainM" to elevationGainM.coerceAtLeast(0.0),
            "elevationChangeNetM" to elevationChangeNetM,
            "durationMin" to durationMin.coerceAtLeast(0L),
            "avgSpeedKmh" to avgSpeedKmh.coerceAtLeast(0.0),
            "startLat" to startLat,
            "startLng" to startLng,
            "endLat" to endLat,
            "endLng" to endLng,
            "weatherSummary" to weatherSummary.trim().take(300),
            "weatherSeverity" to weatherSeverity.takeIf { it in listOf("CLEAR", "ADVISORY", "WARNING") }.orEmpty().ifBlank { "CLEAR" },
            "maxWindGustKmh" to maxWindGustKmh.coerceAtLeast(0.0),
            "batteryEfficiencyUsed" to batteryEfficiencyUsed,
            "batteryPercentUsed" to batteryPercentUsed.coerceIn(0, 100),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        tripRef.set(payload).await()

        // Update rider's cumulative stats and milestones
        val userRef = db.collection("users").document(uid)
        val userSnap = userRef.get().await()
        val currentDist = (userSnap.getDouble("totalDistanceKm") ?: 0.0) + distanceKm
        val currentElev = (userSnap.getDouble("totalElevationM") ?: 0.0) + elevationGainM
        val currentRides = (userSnap.getLong("ridesCompleted") ?: 0L) + 1L
        val milestones = buildList {
            if (currentDist >= 100.0) add("100km")
            if (currentDist >= 500.0) add("500km")
            if (currentDist >= 1000.0) add("1000km")
            if (currentElev >= 500.0) add("500m_climb")
            if (currentRides >= 5L) add("5_rides")
        }
        userRef.update(
            mapOf(
                "totalDistanceKm" to currentDist,
                "totalElevationM" to currentElev,
                "ridesCompleted" to currentRides,
                "earnedMilestones" to milestones,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        tripRef.id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "users/${auth.currentUser?.uid}/trips") }

    fun observeUserPacks(userId: String): Flow<List<RidePack>> {
        val path = "packs"
        return db.collection("packs")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(RidePack::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    suspend fun createPack(
        packName: String,
        displayName: String,
        photoUrl: String,
        bikeModel: String,
        phoneNumber: String,
        initialLat: Double,
        initialLng: Double,
        batteryPercent: Int,
        isCharging: Boolean
    ): Result<RidePack> = runCatching {
        val uid = requireUserId()
        val packRef = db.collection("packs").document()
        val packId = packRef.id
        val inviteCode = generateInviteCode()
        val salt = UUID.randomUUID().toString().replace("-", "")

        val packPayload = mapOf(
            "name" to packName.trim().ifBlank { "Sunday Riders" }.take(80),
            "inviteCode" to inviteCode,
            "ownerId" to uid,
            "memberUids" to listOf(uid),
            "encryptionSalt" to salt,
            "separationThresholdKm" to 2.0,
            "activeRouteName" to "",
            "activeRoutePolyline" to "",
            "activeRouteDistanceKm" to 0.0,
            "activeRouteDurationMin" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        packRef.set(packPayload).await()

        db.collection("pack_invites").document(inviteCode).set(
            mapOf(
                "packId" to packId,
                "packName" to packName.trim().ifBlank { "Sunday Riders" }.take(80),
                "ownerId" to uid,
                "encryptionSalt" to salt,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()

        val key = PackCrypto.derivePackKey(inviteCode, salt)
        val encrypted = PackCrypto.encryptTelemetry(
            RiderTelemetryPayload(lat = initialLat, lng = initialLng),
            key
        )
        db.collection("packs").document(packId).collection("members").document(uid).set(
            mapOf(
                "userId" to uid,
                "packId" to packId,
                "displayName" to displayName.trim().ifBlank { "Rider" }.take(60),
                "photoUrl" to photoUrl.take(500),
                "bikeModel" to bikeModel.take(80),
                "phoneNumber" to phoneNumber.take(30),
                "encryptedTelemetry" to encrypted,
                "batteryPercent" to batteryPercent.coerceIn(0, 100),
                "isCharging" to isCharging,
                "totalDistanceKm" to 0.0,
                "elevationClimbedM" to 0.0,
                "isOnline" to true,
                "memberUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection("users").document(uid).update(
            mapOf(
                "activePackId" to packId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        packRef.get().await().toObject(RidePack::class.java, ServerTimestampBehavior.ESTIMATE)
            ?: RidePack(
                id = packId,
                name = packName,
                inviteCode = inviteCode,
                ownerId = uid,
                memberUids = listOf(uid),
                encryptionSalt = salt
            )
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "packs") }

    suspend fun joinPackByCode(
        rawCode: String,
        displayName: String,
        photoUrl: String,
        bikeModel: String,
        phoneNumber: String,
        initialLat: Double,
        initialLng: Double,
        batteryPercent: Int,
        isCharging: Boolean
    ): Result<RidePack> = runCatching {
        val uid = requireUserId()
        val inviteCode = rawCode.trim().uppercase()
        require(inviteCode.length in 6..12) { "Invite code must be 6-12 characters." }

        val inviteSnap = db.collection("pack_invites").document(inviteCode).get().await()
        val invite = inviteSnap.toObject(PackInvite::class.java)
            ?: throw IllegalArgumentException("No pack found for invite code $inviteCode.")

        val packRef = db.collection("packs").document(invite.packId)
        packRef.update(
            mapOf(
                "memberUids" to FieldValue.arrayUnion(uid),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        val packSnap = packRef.get().await()
        val pack = packSnap.toObject(RidePack::class.java, ServerTimestampBehavior.ESTIMATE)
            ?: throw IllegalStateException("Pack could not be loaded after joining.")

        val key = PackCrypto.derivePackKey(pack.inviteCode, pack.encryptionSalt)
        val encrypted = PackCrypto.encryptTelemetry(
            RiderTelemetryPayload(lat = initialLat, lng = initialLng),
            key
        )

        db.collection("packs").document(pack.id).collection("members").document(uid).set(
            mapOf(
                "userId" to uid,
                "packId" to pack.id,
                "displayName" to displayName.trim().ifBlank { "Rider" }.take(60),
                "photoUrl" to photoUrl.take(500),
                "bikeModel" to bikeModel.take(80),
                "phoneNumber" to phoneNumber.take(30),
                "encryptedTelemetry" to encrypted,
                "batteryPercent" to batteryPercent.coerceIn(0, 100),
                "isCharging" to isCharging,
                "totalDistanceKm" to 0.0,
                "elevationClimbedM" to 0.0,
                "isOnline" to true,
                "memberUids" to pack.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection("users").document(uid).update(
            mapOf(
                "activePackId" to pack.id,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        pack
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "packs") }

    suspend fun setActivePackId(packId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        db.collection("users").document(uid).update(
            mapOf(
                "activePackId" to packId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "users/${auth.currentUser?.uid}") }

    suspend fun updatePackRoute(
        packId: String,
        routeName: String,
        encodedPolyline: String,
        distanceKm: Double,
        durationMin: Long
    ): Result<Unit> = runCatching {
        requireUserId()
        db.collection("packs").document(packId).update(
            mapOf(
                "activeRouteName" to routeName.take(120),
                "activeRoutePolyline" to encodedPolyline.take(20000),
                "activeRouteDistanceKm" to distanceKm.coerceAtLeast(0.0),
                "activeRouteDurationMin" to durationMin.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "packs/$packId") }

    fun observePackMembers(packId: String, currentUserId: String): Flow<List<PackMemberTelemetry>> {
        val path = "packs/$packId/members"
        return db.collection("packs").document(packId).collection("members")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PackMemberTelemetry::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, currentUserId)
                throw error
            }
    }

    suspend fun publishTelemetry(
        pack: RidePack,
        displayName: String,
        photoUrl: String,
        bikeModel: String,
        phoneNumber: String,
        payload: RiderTelemetryPayload,
        batteryPercent: Int,
        isCharging: Boolean,
        totalDistanceKm: Double,
        elevationClimbedM: Double,
        isOnline: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val key = PackCrypto.derivePackKey(pack.inviteCode, pack.encryptionSalt)
        val encrypted = PackCrypto.encryptTelemetry(payload, key)
        val docRef = db.collection("packs").document(pack.id).collection("members").document(uid)
        val existingSnap = docRef.get().await()
        val memberUids = (pack.memberUids + uid).distinct()

        if (!existingSnap.exists()) {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "packId" to pack.id,
                    "displayName" to displayName.trim().ifBlank { "Rider" }.take(60),
                    "photoUrl" to photoUrl.take(500),
                    "bikeModel" to bikeModel.take(80),
                    "phoneNumber" to phoneNumber.take(30),
                    "encryptedTelemetry" to encrypted,
                    "batteryPercent" to batteryPercent.coerceIn(0, 100),
                    "isCharging" to isCharging,
                    "totalDistanceKm" to totalDistanceKm.coerceAtLeast(0.0),
                    "elevationClimbedM" to elevationClimbedM.coerceAtLeast(0.0),
                    "isOnline" to isOnline,
                    "memberUids" to memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.set(
                mapOf(
                    "displayName" to displayName.trim().ifBlank { "Rider" }.take(60),
                    "photoUrl" to photoUrl.take(500),
                    "bikeModel" to bikeModel.take(80),
                    "phoneNumber" to phoneNumber.take(30),
                    "encryptedTelemetry" to encrypted,
                    "batteryPercent" to batteryPercent.coerceIn(0, 100),
                    "isCharging" to isCharging,
                    "totalDistanceKm" to totalDistanceKm.coerceAtLeast(0.0),
                    "elevationClimbedM" to elevationClimbedM.coerceAtLeast(0.0),
                    "isOnline" to isOnline,
                    "memberUids" to memberUids,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "packs/${pack.id}/members/${auth.currentUser?.uid}") }

    fun observePackMessages(packId: String, currentUserId: String): Flow<List<PackMessage>> {
        val path = "packs/$packId/messages"
        return db.collection("packs").document(packId).collection("messages")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PackMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, currentUserId)
                throw error
            }
    }

    suspend fun sendPackMessage(
        pack: RidePack,
        senderName: String,
        plainText: String,
        recipientId: String = "",
        messageType: String = "chat"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val key = PackCrypto.derivePackKey(pack.inviteCode, pack.encryptionSalt)
        val encrypted = PackCrypto.encryptString(plainText.trim(), key)
        val msgRef = db.collection("packs").document(pack.id).collection("messages").document()
        val memberUids = (pack.memberUids + uid).distinct()
        msgRef.set(
            mapOf(
                "senderId" to uid,
                "senderName" to senderName.trim().ifBlank { "Rider" }.take(60),
                "packId" to pack.id,
                "recipientId" to recipientId.take(128),
                "encryptedBody" to encrypted,
                "messageType" to messageType,
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        msgRef.id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "packs/${pack.id}/messages") }

    suspend fun sendPackVoiceMessage(
        pack: RidePack,
        senderName: String,
        audioBase64: String,
        audioCodec: String,
        audioDurationMs: Int,
        audioSizeBytes: Int,
        audioPeaks: String,
        recipientId: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val key = PackCrypto.derivePackKey(pack.inviteCode, pack.encryptionSalt)
        val durationSec = ((audioDurationMs + 500) / 1000).coerceAtLeast(1)
        val summary = "Voice callout (${durationSec}s • ${String.format("%.1f KB", audioSizeBytes / 1024.0)})"
        val encryptedBody = PackCrypto.encryptString(summary, key)
        val encryptedAudio = PackCrypto.encryptString(audioBase64, key)
        val msgRef = db.collection("packs").document(pack.id).collection("messages").document()
        val memberUids = (pack.memberUids + uid).distinct()
        msgRef.set(
            mapOf(
                "senderId" to uid,
                "senderName" to senderName.trim().ifBlank { "Rider" }.take(60),
                "packId" to pack.id,
                "recipientId" to recipientId.take(128),
                "encryptedBody" to encryptedBody,
                "messageType" to "voice",
                "encryptedAudio" to encryptedAudio.take(65000),
                "audioCodec" to audioCodec.ifBlank { "AMR_NB" },
                "audioDurationMs" to audioDurationMs.coerceIn(200, 30000),
                "audioSizeBytes" to audioSizeBytes.coerceIn(1, 48000),
                "audioPeaks" to audioPeaks.take(120),
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        msgRef.id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "packs/${pack.id}/messages") }

    fun observeUrgentPings(packId: String, currentUserId: String): Flow<List<UrgentPing>> {
        val path = "packs/$packId/pings"
        return db.collection("packs").document(packId).collection("pings")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UrgentPing::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, currentUserId)
                throw error
            }
    }

    suspend fun sendUrgentPing(
        pack: RidePack,
        senderName: String,
        targetUserId: String,
        alertType: String,
        note: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val key = PackCrypto.derivePackKey(pack.inviteCode, pack.encryptionSalt)
        val encryptedNote = if (note.isNotBlank()) PackCrypto.encryptString(note, key) else ""
        val pingRef = db.collection("packs").document(pack.id).collection("pings").document()
        val memberUids = (pack.memberUids + uid).distinct()
        pingRef.set(
            mapOf(
                "senderId" to uid,
                "senderName" to senderName.trim().ifBlank { "Rider" }.take(60),
                "targetUserId" to targetUserId.take(128),
                "packId" to pack.id,
                "alertType" to alertType,
                "encryptedNote" to encryptedNote,
                "acknowledged" to false,
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        pingRef.id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "packs/${pack.id}/pings") }

    suspend fun acknowledgePing(packId: String, pingId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("packs").document(packId).collection("pings").document(pingId).update(
            mapOf(
                "acknowledged" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "packs/$packId/pings/$pingId") }

    fun observeChallenges(packId: String, currentUserId: String): Flow<List<MapChallenge>> {
        val path = "packs/$packId/challenges"
        return db.collection("packs").document(packId).collection("challenges")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(MapChallenge::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, currentUserId)
                throw error
            }
    }

    suspend fun createChallenge(
        pack: RidePack,
        title: String,
        description: String,
        challengeType: String,
        targetValue: Double,
        unit: String,
        checkpointLat: Double = 0.0,
        checkpointLng: Double = 0.0
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("packs").document(pack.id).collection("challenges").document()
        val memberUids = (pack.memberUids + uid).distinct()
        ref.set(
            mapOf(
                "packId" to pack.id,
                "creatorId" to uid,
                "title" to title.trim().ifBlank { "Pack Challenge" }.take(100),
                "description" to description.trim().take(300),
                "challengeType" to challengeType,
                "targetValue" to targetValue.coerceAtLeast(1.0),
                "currentValue" to 0.0,
                "unit" to unit.take(20),
                "checkpointLat" to checkpointLat,
                "checkpointLng" to checkpointLng,
                "status" to "active",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        ref.id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "packs/${pack.id}/challenges") }

    suspend fun updateChallengeProgress(
        packId: String,
        challenge: MapChallenge,
        newValue: Double
    ): Result<Unit> = runCatching {
        requireUserId()
        val clamped = newValue.coerceAtLeast(0.0)
        val completed = clamped >= challenge.targetValue
        db.collection("packs").document(packId).collection("challenges").document(challenge.id).update(
            mapOf(
                "currentValue" to clamped,
                "status" to if (completed) "completed" else "active",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "packs/$packId/challenges/${challenge.id}") }

    fun observePackMarkers(packId: String, currentUserId: String): Flow<List<PackMapPin>> {
        val path = "packs/$packId/markers"
        return db.collection("packs").document(packId).collection("markers")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PackMapPin::class.java, ServerTimestampBehavior.ESTIMATE)
                    .filter { it.status == "active" }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, currentUserId)
                throw error
            }
    }

    suspend fun createPackMarker(
        pack: RidePack,
        creatorName: String,
        pinType: String,
        title: String,
        description: String,
        lat: Double,
        lng: Double,
        severity: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("packs").document(pack.id).collection("markers").document()
        val memberUids = (pack.memberUids + uid).distinct()
        ref.set(
            mapOf(
                "packId" to pack.id,
                "creatorId" to uid,
                "creatorName" to creatorName.trim().ifBlank { "Rider" }.take(60),
                "pinType" to pinType,
                "title" to title.trim().ifBlank { "Pack Pin" }.take(100),
                "description" to description.trim().take(300),
                "lat" to lat.coerceIn(-90.0, 90.0),
                "lng" to lng.coerceIn(-180.0, 180.0),
                "severity" to severity,
                "confirmedByUids" to listOf(uid),
                "status" to "active",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        ref.id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "packs/${pack.id}/markers") }

    suspend fun confirmPackMarker(packId: String, marker: PackMapPin): Result<Unit> = runCatching {
        val uid = requireUserId()
        val updatedConfirmations = (marker.confirmedByUids + uid).distinct().take(30)
        db.collection("packs").document(packId).collection("markers").document(marker.id).update(
            mapOf(
                "confirmedByUids" to updatedConfirmations,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "packs/$packId/markers/${marker.id}") }

    suspend fun resolvePackMarker(packId: String, markerId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("packs").document(packId).collection("markers").document(markerId).update(
            mapOf(
                "status" to "resolved",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "packs/$packId/markers/$markerId") }

    suspend fun deletePackMarker(packId: String, markerId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("packs").document(packId).collection("markers").document(markerId).delete().await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.DELETE, "packs/$packId/markers/$markerId") }

    fun observePackGalleryPhotos(packId: String, currentUserId: String): Flow<List<PackGalleryPhoto>> {
        val path = "packs/$packId/photos"
        return db.collection("packs").document(packId).collection("photos")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PackGalleryPhoto::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, path, currentUserId)
                throw error
            }
    }

    suspend fun postPackGalleryPhoto(
        pack: RidePack,
        uploaderName: String,
        caption: String,
        locationLabel: String,
        imageDataBase64: String,
        lat: Double,
        lng: Double
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("packs").document(pack.id).collection("photos").document()
        val memberUids = (pack.memberUids + uid).distinct()
        ref.set(
            mapOf(
                "packId" to pack.id,
                "uploaderId" to uid,
                "uploaderName" to uploaderName.trim().ifBlank { "Rider" }.take(60),
                "caption" to caption.trim().take(200),
                "locationLabel" to locationLabel.trim().take(100),
                "imageDataBase64" to imageDataBase64.take(349_500),
                "lat" to lat,
                "lng" to lng,
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        ref.id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "packs/${pack.id}/photos") }

    suspend fun deletePackGalleryPhoto(packId: String, photoId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("packs").document(packId).collection("photos").document(photoId).delete().await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.DELETE, "packs/$packId/photos/$photoId") }

    private fun logFlowError(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        return buildString(6) {
            repeat(6) { append(chars[rnd.nextInt(chars.length)]) }
        }
    }
}
