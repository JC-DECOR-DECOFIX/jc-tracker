package br.com.jcdecor.tracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import br.com.jcdecor.tracker.tracking.PendingPoint

@Entity(tableName = "pending_location")
data class PendingLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tracking_session_id") val trackingSessionId: String,
    @ColumnInfo(name = "route_id") val routeId: String?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    val sequence: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float?,
    val bearing: Float?,
    val altitude: Double?,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
)

fun PendingPoint.toEntity(): PendingLocationEntity = PendingLocationEntity(
    id = id,
    trackingSessionId = trackingSessionId,
    routeId = routeId,
    deviceId = deviceId,
    sequence = sequence,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    speed = speed,
    bearing = bearing,
    altitude = altitude,
    recordedAt = recordedAt,
    createdAt = createdAt,
    retryCount = retryCount,
)

fun PendingLocationEntity.toPoint(): PendingPoint = PendingPoint(
    id = id,
    trackingSessionId = trackingSessionId,
    routeId = routeId,
    deviceId = deviceId,
    sequence = sequence,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    speed = speed,
    bearing = bearing,
    altitude = altitude,
    recordedAt = recordedAt,
    createdAt = createdAt,
    retryCount = retryCount,
)
