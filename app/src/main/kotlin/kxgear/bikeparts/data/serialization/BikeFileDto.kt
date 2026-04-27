package kxgear.bikeparts.data.serialization

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.model.RideCursor

@Serializable
data class BikeDto(
    val bikeId: String,
    val karooBikeId: String? = null,
    val name: String,
    val karooMileageMeters: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class PartDto(
    val partId: String,
    val name: String,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("manualMileage")
    val legacyMileageOffsetMeters: Int = 0,
    val riddenMileage: Int,
    val status: String,
    val createdAt: Long,
    val createdDate: Long? = null,
    val alertMileage: Int? = null,
    val alertText: String? = null,
    val lastAlertThresholdMeters: Int? = null,
    val updatedAt: Long,
    val archivedAt: Long? = null,
)

@Serializable
data class RideCursorDto(
    val lastAcceptedMetricValue: Int? = null,
    val lastAcceptedCumulativeDistance: Int? = null,
    val lastAcceptedAt: Long? = null,
)

@Serializable
data class BikeFileDto(
    val version: Int,
    val bike: BikeDto,
    val parts: List<PartDto>,
    val rideCursor: RideCursorDto,
    val lastUpdatedAt: Long,
)

fun BikeFile.toDto(): BikeFileDto =
    BikeFileDto(
        version = version,
        bike = BikeDto(
            bikeId = bike.bikeId,
            karooBikeId = bike.karooBikeId,
            name = bike.name,
            karooMileageMeters = bike.karooMileageMeters,
            createdAt = bike.createdAt,
            updatedAt = bike.updatedAt,
        ),
        parts = parts.map { part ->
            PartDto(
                partId = part.partId,
                name = part.name,
                riddenMileage = part.riddenMileage,
                status = part.status.name,
                createdAt = part.createdAt,
                createdDate = part.createdDate,
                alertMileage = part.alertMileage,
                alertText = part.alertText,
                lastAlertThresholdMeters = part.lastAlertThresholdMeters,
                updatedAt = part.updatedAt,
                archivedAt = part.archivedAt,
            )
        },
        rideCursor = RideCursorDto(
            lastAcceptedMetricValue = rideCursor.lastAcceptedMetricValue,
            lastAcceptedAt = rideCursor.lastAcceptedAt,
        ),
        lastUpdatedAt = lastUpdatedAt,
    )

fun BikeFileDto.toDomain(): BikeFile =
    BikeFile(
        version = version,
        bike = Bike(
            bikeId = bike.bikeId,
            karooBikeId = bike.karooBikeId,
            name = bike.name,
            karooMileageMeters = bike.karooMileageMeters,
            createdAt = bike.createdAt,
            updatedAt = bike.updatedAt,
        ),
        parts = parts.map { part ->
            Part(
                partId = part.partId,
                name = part.name,
                riddenMileage = part.legacyMileageOffsetMeters + part.riddenMileage,
                status = PartStatus.valueOf(part.status),
                createdAt = part.createdAt,
                createdDate = part.createdDate ?: part.createdAt,
                alertMileage = part.alertMileage,
                alertText = part.alertText,
                lastAlertThresholdMeters = part.lastAlertThresholdMeters,
                updatedAt = part.updatedAt,
                archivedAt = part.archivedAt,
            )
        },
        rideCursor = RideCursor(
            lastAcceptedMetricValue = rideCursor.lastAcceptedMetricValue ?: rideCursor.lastAcceptedCumulativeDistance,
            lastAcceptedAt = rideCursor.lastAcceptedAt,
        ),
        lastUpdatedAt = lastUpdatedAt,
    )
