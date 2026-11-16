package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val bikeModel: String = "Adventure Tourer",
    val phoneNumber: String = "",
    val activePackId: String = "",
    val totalDistanceKm: Double = 0.0,
    val totalElevationM: Double = 0.0,
    val ridesCompleted: Long = 0L,
    val earnedMilestones: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RideTrip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val packId: String = "",
    val title: String = "",
    val distanceKm: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val elevationChangeNetM: Double = 0.0,
    val durationMin: Long = 0L,
    val avgSpeedKmh: Double = 0.0,
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val endLat: Double = 0.0,
    val endLng: Double = 0.0,
    val weatherSummary: String = "",
    val weatherSeverity: String = "CLEAR",
    val maxWindGustKmh: Double = 0.0,
    val batteryEfficiencyUsed: Boolean = false,
    val batteryPercentUsed: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RidePack(
    @DocumentId val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val encryptionSalt: String = "",
    val separationThresholdKm: Double = 2.0,
    val activeRouteName: String = "",
    val activeRoutePolyline: String = "",
    val activeRouteDistanceKm: Double = 0.0,
    val activeRouteDurationMin: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PackInvite(
    @DocumentId val id: String = "",
    val packId: String = "",
    val packName: String = "",
    val ownerId: String = "",
    val encryptionSalt: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PackMemberTelemetry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val packId: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val bikeModel: String = "",
    val phoneNumber: String = "",
    val encryptedTelemetry: String = "",
    val batteryPercent: Long = 100L,
    val isCharging: Boolean = false,
    val totalDistanceKm: Double = 0.0,
    val elevationClimbedM: Double = 0.0,
    val isOnline: Boolean = true,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PackMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val packId: String = "",
    val recipientId: String = "",
    val encryptedBody: String = "",
    val messageType: String = "chat",
    val encryptedAudio: String = "",
    val audioCodec: String = "",
    val audioDurationMs: Int = 0,
    val audioSizeBytes: Int = 0,
    val audioPeaks: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UrgentPing(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val targetUserId: String = "",
    val packId: String = "",
    val alertType: String = "RING_URGENT",
    val encryptedNote: String = "",
    val acknowledged: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MapChallenge(
    @DocumentId val id: String = "",
    val packId: String = "",
    val creatorId: String = "",
    val title: String = "",
    val description: String = "",
    val challengeType: String = "ELEVATION",
    val targetValue: Double = 500.0,
    val currentValue: Double = 0.0,
    val unit: String = "m",
    val checkpointLat: Double = 0.0,
    val checkpointLng: Double = 0.0,
    val status: String = "active",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DecryptedRiderState(
    val userId: String,
    val displayName: String,
    val photoUrl: String,
    val bikeModel: String,
    val phoneNumber: String,
    val lat: Double,
    val lng: Double,
    val speedKmh: Double,
    val headingDeg: Float,
    val altitudeM: Double,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val totalDistanceKm: Double,
    val elevationClimbedM: Double,
    val isOnline: Boolean,
    val isOfflineQueued: Boolean,
    val lastUpdatedMs: Long,
    val distanceFromMeKm: Double = 0.0,
    val trafficEtaMinutes: Int? = null,
    val trafficConditionLabel: String = "Calculating..."
)

data class DecryptedPackMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String,
    val text: String,
    val messageType: String,
    val timestampMs: Long,
    val audioBase64: String = "",
    val audioCodec: String = "",
    val audioDurationMs: Int = 0,
    val audioSizeBytes: Int = 0,
    val audioPeaks: List<Int> = emptyList()
)

@IgnoreExtraProperties
data class PackMapPin(
    @DocumentId val id: String = "",
    val packId: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val pinType: String = "GAS_STOP", // "GAS_STOP", "REST_BREAK", "HAZARD_WARNING"
    val title: String = "",
    val description: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val severity: String = "INFO", // "INFO", "CAUTION", "DANGER"
    val confirmedByUids: List<String> = emptyList(),
    val status: String = "active", // "active", "resolved"
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PackGalleryPhoto(
    @DocumentId val id: String = "",
    val packId: String = "",
    val uploaderId: String = "",
    val uploaderName: String = "",
    val caption: String = "",
    val locationLabel: String = "",
    val imageDataBase64: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
