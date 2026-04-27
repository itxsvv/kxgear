package kxgear.bikeparts.domain.service

import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.BikeSummary
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.model.RideCursor
import kxgear.bikeparts.domain.model.SharedMetadata
import kxgear.bikeparts.domain.validation.BikePartsValidators

class BikePartsService {
    data class PartAlert(
        val partId: String,
        val partName: String,
        val alertText: String?,
        val thresholdMeters: Int,
        val currentMileageMeters: Int,
    )

    data class RideUpdateOutcome(
        val bikeFile: BikeFile,
        val alerts: List<PartAlert> = emptyList(),
    )

    fun currentMileage(part: Part): Int = part.currentMileage

    fun deriveRideDelta(
        lastAccepted: Int?,
        incoming: Int,
    ): Int = BikePartsValidators.deriveRideDelta(lastAccepted, incoming)

    fun applyRideUpdate(
        bikeFile: BikeFile,
        metricValue: Int,
        recordedAt: Long,
    ): RideUpdateOutcome {
        val delta = BikePartsValidators.deriveRideDelta(
            lastAccepted = bikeFile.rideCursor.lastAcceptedMetricValue,
            incoming = metricValue,
        )
        if (delta == 0) {
            return RideUpdateOutcome(bikeFile = bikeFile)
        }

        val alerts = mutableListOf<PartAlert>()
        val updatedParts = bikeFile.parts.map { part ->
            if (part.status == PartStatus.INSTALLED) {
                val updatedMileage = part.riddenMileage + delta
                val crossedThreshold = highestCrossedThreshold(part, updatedMileage)
                if (crossedThreshold != null) {
                    alerts +=
                        PartAlert(
                            partId = part.partId,
                            partName = part.name,
                            alertText = part.alertText,
                            thresholdMeters = crossedThreshold,
                            currentMileageMeters = updatedMileage,
                        )
                }
                part.copy(
                    riddenMileage = updatedMileage,
                    lastAlertThresholdMeters = crossedThreshold ?: part.lastAlertThresholdMeters,
                    updatedAt = recordedAt,
                )
            } else {
                part
            }
        }
        return RideUpdateOutcome(
            bikeFile =
                bikeFile.copy(
                    bike =
                        bikeFile.bike.copy(
                            karooMileageMeters = bikeFile.bike.karooMileageMeters + delta,
                            updatedAt = recordedAt,
                        ),
                    parts = updatedParts,
                    rideCursor = RideCursor(
                        lastAcceptedMetricValue = metricValue,
                        lastAcceptedAt = recordedAt,
                    ),
                    lastUpdatedAt = recordedAt,
                ),
            alerts = alerts,
        )
    }

    private fun highestCrossedThreshold(
        part: Part,
        updatedMileage: Int,
    ): Int? {
        val alertMileageKm = part.alertMileage ?: return null
        if (alertMileageKm <= 0) {
            return null
        }

        val intervalMeters = alertMileageKm * 1000
        val highestCrossed = (updatedMileage / intervalMeters) * intervalMeters
        if (highestCrossed <= part.riddenMileage) {
            return null
        }

        val lastAlertThresholdMeters = part.lastAlertThresholdMeters ?: 0
        if (highestCrossed <= lastAlertThresholdMeters) {
            return null
        }

        return highestCrossed
    }

    fun archivePart(
        bikeFile: BikeFile,
        partId: String,
        archivedAt: Long,
    ): BikeFile {
        var found = false
        val updatedParts = bikeFile.parts.map { part ->
            if (part.partId == partId) {
                found = true
                if (part.status == PartStatus.ARCHIVED) {
                    part
                } else {
                    part.copy(
                        status = PartStatus.ARCHIVED,
                        archivedAt = archivedAt,
                        updatedAt = archivedAt,
                    )
                }
            } else {
                part
            }
        }
        if (!found) {
            throw RepositoryError.NotFound("Part not found: $partId")
        }
        return bikeFile.copy(parts = updatedParts, lastUpdatedAt = archivedAt)
    }

    fun replacePart(
        bikeFile: BikeFile,
        oldPartId: String,
        newPart: Part,
        replacedAt: Long,
    ): BikeFile {
        val archived = archivePart(bikeFile, oldPartId, replacedAt)
        return archived.copy(
            parts = archived.parts + newPart.copy(updatedAt = replacedAt),
            lastUpdatedAt = replacedAt,
        )
    }

    fun deleteBike(
        metadata: SharedMetadata,
        bikeId: String,
    ): SharedMetadata =
        metadata.copy(
            activeBikeId = metadata.activeBikeId.takeUnless { it == bikeId },
            bikeIndex = metadata.bikeIndex.filterNot { it.bikeId == bikeId },
        )

    fun updateBikeIndex(
        metadata: SharedMetadata,
        bike: Bike,
    ): SharedMetadata {
        val filtered = metadata.bikeIndex.filterNot { it.bikeId == bike.bikeId }
        return metadata.copy(
            bikeIndex =
                (filtered + BikeSummary(bike.bikeId, bike.name, bike.karooMileageMeters))
                    .sortedBy { it.name.lowercase() },
        )
    }
}
