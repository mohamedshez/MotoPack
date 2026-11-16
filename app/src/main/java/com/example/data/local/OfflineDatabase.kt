package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offline_routes")
data class OfflineRouteEntity(
    @PrimaryKey val routeId: String,
    val packId: String,
    val name: String,
    val originLabel: String,
    val destinationLabel: String,
    val encodedPolyline: String,
    val distanceKm: Double,
    val durationMin: Int,
    val trafficDelayMin: Int,
    val elevationPointsJson: String,
    val fuelStopsJson: String,
    val weatherAlertsJson: String,
    val navigationStepsJson: String = "[]",
    val savedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "queued_breadcrumbs")
data class QueuedBreadcrumbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packId: String,
    val userId: String,
    val lat: Double,
    val lng: Double,
    val speedKmh: Double,
    val headingDeg: Float,
    val altitudeM: Double,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val recordedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_riders")
data class CachedRiderEntity(
    @PrimaryKey val userId: String,
    val packId: String,
    val displayName: String,
    val photoUrl: String,
    val bikeModel: String,
    val phoneNumber: String,
    val lat: Double,
    val lng: Double,
    val speedKmh: Double,
    val altitudeM: Double,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val totalDistanceKm: Double,
    val elevationClimbedM: Double,
    val lastSeenMs: Long
)

@Entity(tableName = "cached_pack_pins")
data class CachedPackPinEntity(
    @PrimaryKey val pinId: String,
    val packId: String,
    val creatorId: String,
    val creatorName: String,
    val pinType: String,
    val title: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val severity: String,
    val confirmedCount: Int,
    val status: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "offline_map_areas")
data class OfflineMapAreaEntity(
    @PrimaryKey val areaId: String,
    val packId: String,
    val title: String,
    val centerLat: Double,
    val centerLng: Double,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
    val radiusKm: Double,
    val zoomRangeLabel: String,
    val tileCount: Int,
    val storageBytes: Long,
    val routePolyline: String,
    val linkedRouteId: String = "",
    val navStepCount: Int = 0,
    val fuelStopCount: Int = 0,
    val elevationSampleCount: Int = 0,
    val weatherCount: Int = 0,
    val maxElevationM: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val navigationStepsJson: String = "[]",
    val elevationPointsJson: String = "[]",
    val fuelStopsJson: String = "[]",
    val weatherAlertsJson: String = "[]",
    val downloadedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_trip_elevations")
data class CachedTripElevationEntity(
    @PrimaryKey val tripId: String,
    val samplesJson: String,
    val savedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "queued_voice_messages")
data class QueuedVoiceMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packId: String,
    val senderName: String,
    val recipientId: String,
    val audioBase64: String,
    val audioCodec: String,
    val audioDurationMs: Int,
    val audioSizeBytes: Int,
    val audioPeaks: String,
    val recordedAtMs: Long = System.currentTimeMillis()
)

@Dao
interface OfflineDao {
    @Query("SELECT * FROM offline_routes WHERE packId = :packId ORDER BY savedAtMs DESC")
    fun observeRoutesForPack(packId: String): Flow<List<OfflineRouteEntity>>

    @Query("SELECT * FROM offline_routes WHERE routeId = :routeId LIMIT 1")
    suspend fun getOfflineRouteById(routeId: String): OfflineRouteEntity?

    @Query("SELECT * FROM offline_routes ORDER BY savedAtMs DESC LIMIT 1")
    fun observeLatestOfflineRoute(): Flow<OfflineRouteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflineRoute(route: OfflineRouteEntity)

    @Query("DELETE FROM offline_routes WHERE routeId = :routeId")
    suspend fun deleteOfflineRoute(routeId: String)

    @Query("SELECT * FROM queued_breadcrumbs ORDER BY recordedAtMs ASC")
    suspend fun getPendingBreadcrumbs(): List<QueuedBreadcrumbEntity>

    @Query("SELECT COUNT(*) FROM queued_breadcrumbs")
    fun observePendingBreadcrumbCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueBreadcrumb(breadcrumb: QueuedBreadcrumbEntity)

    @Query("DELETE FROM queued_breadcrumbs WHERE id IN (:ids)")
    suspend fun deleteBreadcrumbsByIds(ids: List<Long>)

    @Query("SELECT * FROM cached_riders WHERE packId = :packId")
    fun observeCachedRiders(packId: String): Flow<List<CachedRiderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedRiders(riders: List<CachedRiderEntity>)

    @Query("SELECT * FROM cached_pack_pins WHERE packId = :packId AND status = 'active'")
    fun observeCachedPackPins(packId: String): Flow<List<CachedPackPinEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedPackPins(pins: List<CachedPackPinEntity>)

    @Query("SELECT * FROM offline_map_areas ORDER BY downloadedAtMs DESC")
    fun observeOfflineMapAreas(): Flow<List<OfflineMapAreaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflineMapArea(area: OfflineMapAreaEntity)

    @Query("DELETE FROM offline_map_areas WHERE areaId = :areaId")
    suspend fun deleteOfflineMapArea(areaId: String)

    @Query("SELECT * FROM cached_trip_elevations WHERE tripId = :tripId LIMIT 1")
    suspend fun getTripElevation(tripId: String): CachedTripElevationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTripElevation(entry: CachedTripElevationEntity)

    @Query("SELECT * FROM queued_voice_messages ORDER BY recordedAtMs ASC")
    suspend fun getPendingVoiceMessages(): List<QueuedVoiceMessageEntity>

    @Query("SELECT COUNT(*) FROM queued_voice_messages")
    fun observePendingVoiceCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueVoiceMessage(voice: QueuedVoiceMessageEntity)

    @Query("DELETE FROM queued_voice_messages WHERE id IN (:ids)")
    suspend fun deleteVoiceMessagesByIds(ids: List<Long>)
}

@Database(
    entities = [
        OfflineRouteEntity::class,
        QueuedBreadcrumbEntity::class,
        CachedRiderEntity::class,
        CachedPackPinEntity::class,
        OfflineMapAreaEntity::class,
        CachedTripElevationEntity::class,
        QueuedVoiceMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MotoPackDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        @Volatile
        private var instance: MotoPackDatabase? = null

        fun getInstance(context: Context): MotoPackDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MotoPackDatabase::class.java,
                    "motopack_offline.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
