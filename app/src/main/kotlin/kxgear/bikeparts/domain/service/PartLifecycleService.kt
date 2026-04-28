package kxgear.bikeparts.domain.service

import java.util.UUID
import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.model.RideCursor
import kxgear.bikeparts.domain.repository.BikeRepository
import kxgear.bikeparts.domain.validation.BikePartsValidators
import kxgear.bikeparts.integration.logging.BikePartsLogger

data class BikeDetails(
    val bike: Bike,
    val installedParts: List<Part>,
    val archivedParts: List<Part>,
    val rideCursor: RideCursor = RideCursor(),
)

interface PartLifecycleGateway {
    suspend fun loadBikeDetails(bikeId: String): BikeDetails

    suspend fun getPart(
        bikeId: String,
        partId: String,
    ): Part?

    suspend fun addPart(
        bikeId: String,
        name: String,
        riddenMileage: Int,
    ): BikeDetails

    suspend fun updatePart(
        bikeId: String,
        partId: String,
        name: String,
        riddenMileage: Int,
        targetAlertMileage: Int?,
        alertText: String?,
    ): BikeDetails

    suspend fun archivePart(
        bikeId: String,
        partId: String,
    ): BikeDetails

    suspend fun deletePart(
        bikeId: String,
        partId: String,
    ): BikeDetails

    suspend fun replacePart(
        bikeId: String,
        oldPartId: String,
        name: String,
        riddenMileage: Int,
    ): BikeDetails
}

class PartLifecycleService(
    private val bikeRepository: BikeRepository,
    private val bikePartsService: BikePartsService,
    private val logger: BikePartsLogger,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PartLifecycleGateway {
    override suspend fun loadBikeDetails(bikeId: String): BikeDetails = loadRequiredBikeFile(bikeId).toBikeDetails()

    override suspend fun getPart(
        bikeId: String,
        partId: String,
    ): Part? = loadRequiredBikeFile(bikeId).parts.firstOrNull { it.partId == partId }

    override suspend fun addPart(
        bikeId: String,
        name: String,
        riddenMileage: Int,
    ): BikeDetails {
        val bikeFile = loadRequiredBikeFile(bikeId)
        val normalizedName = validatePartName(name)
        BikePartsValidators.requireWholeMileage(riddenMileage)

        val now = clock()
        val part =
            Part(
                partId = idProvider(),
                name = normalizedName,
                riddenMileage = riddenMileage,
                status = PartStatus.INSTALLED,
                createdAt = now,
                createdDate = now,
                updatedAt = now,
            )

        val updated = bikeFile.withUpdatedBike(now).copy(parts = bikeFile.parts + part, lastUpdatedAt = now)
        bikeRepository.saveBikeFile(updated)
        logger.debug("Added part ${part.partId} to bike $bikeId")
        return updated.toBikeDetails()
    }

    override suspend fun updatePart(
        bikeId: String,
        partId: String,
        name: String,
        riddenMileage: Int,
        targetAlertMileage: Int?,
        alertText: String?,
    ): BikeDetails {
        val bikeFile = loadRequiredBikeFile(bikeId)
        val normalizedName = validatePartName(name)
        BikePartsValidators.requireWholeMileage(riddenMileage)
        val normalizedAlertConfig = normalizeAlertConfig(targetAlertMileage = targetAlertMileage, alertText = alertText)

        var found = false
        val now = clock()
        val updatedParts =
            bikeFile.parts.map { part ->
                if (part.partId == partId) {
                    found = true
                    part.copy(
                        name = normalizedName,
                        riddenMileage = riddenMileage,
                        targetAlertMileage = normalizedAlertConfig?.first ?: 0,
                        curAlertMileage =
                            when {
                                normalizedAlertConfig == null -> 0
                                part.targetAlertMileage != normalizedAlertConfig.first -> 0
                                else -> part.curAlertMileage
                            },
                        alertText = normalizedAlertConfig?.second,
                        updatedAt = now,
                    )
                } else {
                    part
                }
            }
        if (!found) {
            throw RepositoryError.NotFound("Part not found: $partId")
        }

        val updated = bikeFile.withUpdatedBike(now).copy(parts = updatedParts, lastUpdatedAt = now)
        bikeRepository.saveBikeFile(updated)
        logger.debug("Updated part $partId on bike $bikeId")
        return updated.toBikeDetails()
    }

    override suspend fun archivePart(
        bikeId: String,
        partId: String,
    ): BikeDetails {
        val now = clock()
        val bikeFile = loadRequiredBikeFile(bikeId)
        val updated =
            bikePartsService
                .archivePart(
                    bikeFile = bikeFile.withUpdatedBike(now),
                    partId = partId,
                    archivedAt = now,
                ).copy(lastUpdatedAt = now)
        bikeRepository.saveBikeFile(updated)
        logger.debug("Archived part $partId on bike $bikeId")
        return updated.toBikeDetails()
    }

    override suspend fun deletePart(
        bikeId: String,
        partId: String,
    ): BikeDetails {
        val bikeFile = loadRequiredBikeFile(bikeId)
        val targetPart = bikeFile.parts.firstOrNull { it.partId == partId }
            ?: throw RepositoryError.NotFound("Part not found: $partId")
        if (targetPart.status != PartStatus.ARCHIVED) {
            throw RepositoryError.Validation("Only archived parts can be deleted")
        }

        val now = clock()
        val updated =
            bikeFile
                .withUpdatedBike(now)
                .copy(
                    parts = bikeFile.parts.filterNot { it.partId == partId },
                    lastUpdatedAt = now,
                )
        bikeRepository.saveBikeFile(updated)
        logger.debug("Deleted archived part $partId on bike $bikeId")
        return updated.toBikeDetails()
    }

    override suspend fun replacePart(
        bikeId: String,
        oldPartId: String,
        name: String,
        riddenMileage: Int,
    ): BikeDetails {
        val bikeFile = loadRequiredBikeFile(bikeId)
        val normalizedName = validatePartName(name)
        BikePartsValidators.requireWholeMileage(riddenMileage)
        val now = clock()
        val newPart =
            Part(
                partId = idProvider(),
                name = normalizedName,
                riddenMileage = riddenMileage,
                status = PartStatus.INSTALLED,
                createdAt = now,
                createdDate = now,
                updatedAt = now,
            )

        val updated =
            bikePartsService.replacePart(
                bikeFile = bikeFile.withUpdatedBike(now),
                oldPartId = oldPartId,
                newPart = newPart,
                replacedAt = now,
            )
        bikeRepository.saveBikeFile(updated)
        logger.debug("Replaced part $oldPartId on bike $bikeId with ${newPart.partId}")
        return updated.toBikeDetails()
    }

    private suspend fun loadRequiredBikeFile(bikeId: String): BikeFile =
        bikeRepository.getBikeFile(bikeId) ?: throw RepositoryError.NotFound("Bike not found: $bikeId")

    private fun validatePartName(name: String): String = name.trim().requireNonBlank("Part name is required")

    private fun normalizeAlertConfig(
        targetAlertMileage: Int?,
        alertText: String?,
    ): Pair<Int, String?>? {
        val trimmedText = alertText?.trim().orEmpty().ifBlank { null }
        if (targetAlertMileage == null) {
            return null
        }
        if (targetAlertMileage <= 0) {
            throw RepositoryError.Validation("Target alert mileage must be a positive whole number in kilometers")
        }
        return targetAlertMileage * 1000 to trimmedText
    }

    private fun String.requireNonBlank(message: String): String {
        if (isBlank()) {
            throw RepositoryError.Validation(message)
        }
        return this
    }

    private fun BikeFile.withUpdatedBike(updatedAt: Long): BikeFile =
        copy(
            bike = bike.copy(updatedAt = updatedAt),
            lastUpdatedAt = updatedAt,
        )

    private fun BikeFile.toBikeDetails(): BikeDetails =
        BikeDetails(
            bike = bike,
            installedParts = parts.filter { it.status == PartStatus.INSTALLED }.sortedByDescending { it.createdDate },
            archivedParts = parts.filter { it.status == PartStatus.ARCHIVED }.sortedByDescending { it.createdDate },
            rideCursor = rideCursor,
        )
}
