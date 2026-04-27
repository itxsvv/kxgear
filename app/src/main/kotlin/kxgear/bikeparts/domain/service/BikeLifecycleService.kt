package kxgear.bikeparts.domain.service

import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.BikeSummary
import kxgear.bikeparts.domain.model.SharedMetadata
import kxgear.bikeparts.domain.repository.BikeRepository
import kxgear.bikeparts.domain.repository.MetadataRepository
import kxgear.bikeparts.domain.validation.BikePartsValidators
import kxgear.bikeparts.integration.logging.BikePartsLogger
import java.util.UUID

data class BikeOverview(
    val bikes: List<BikeSummary>,
    val activeBikeId: String?,
)

interface BikeLifecycleGateway {
    suspend fun loadOverview(): BikeOverview

    suspend fun getBike(bikeId: String): Bike?

    suspend fun addBike(
        name: String,
        mileageMeters: Int,
    ): BikeOverview

    suspend fun updateBike(
        bikeId: String,
        name: String,
        mileageMeters: Int,
    ): BikeOverview

    suspend fun deleteBike(bikeId: String): BikeOverview

    suspend fun selectActiveBike(bikeId: String): BikeOverview
}

class BikeLifecycleService(
    private val bikeRepository: BikeRepository,
    private val metadataRepository: MetadataRepository,
    private val logger: BikePartsLogger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : BikeLifecycleGateway {
    override suspend fun loadOverview(): BikeOverview {
        val bikeFiles = bikeRepository.listBikeFiles()
        val metadata = metadataRepository.read()
        val normalized = normalizeMetadata(metadata, bikeFiles)
        if (normalized != metadata) {
            metadataRepository.save(normalized)
        }
        return BikeOverview(
            bikes = normalized.bikeIndex,
            activeBikeId = normalized.activeBikeId,
        )
    }

    override suspend fun getBike(bikeId: String): Bike? = bikeRepository.getBikeFile(bikeId)?.bike

    override suspend fun addBike(
        name: String,
        mileageMeters: Int,
    ): BikeOverview {
        val normalizedName = requireBikeName(name)
        BikePartsValidators.requireWholeMileage(mileageMeters)
        val bikeFiles = bikeRepository.listBikeFiles()
        BikePartsValidators.requireUniqueBikeName(bikeFiles.map { it.bike }, normalizedName)

        val now = clock()
        val bike =
            Bike(
                bikeId = idProvider(),
                name = normalizedName,
                karooMileageMeters = mileageMeters,
                createdAt = now,
                updatedAt = now,
            )
        bikeRepository.saveBikeFile(
            BikeFile(
                bike = bike,
                parts = emptyList(),
                lastUpdatedAt = now,
            ),
        )

        val updatedMetadata =
            normalizeMetadata(
                metadata =
                    metadataRepository.read().let { metadata ->
                        if (metadata.activeBikeId == null) {
                            metadata.copy(activeBikeId = bike.bikeId)
                        } else {
                            metadata
                        }
                    },
                bikeFiles = bikeRepository.listBikeFiles(),
            )
        metadataRepository.save(updatedMetadata)
        logger.debug("Added local bike ${bike.bikeId}")
        return BikeOverview(updatedMetadata.bikeIndex, updatedMetadata.activeBikeId)
    }

    override suspend fun updateBike(
        bikeId: String,
        name: String,
        mileageMeters: Int,
    ): BikeOverview {
        val normalizedName = requireBikeName(name)
        BikePartsValidators.requireWholeMileage(mileageMeters)
        val bikeFiles = bikeRepository.listBikeFiles()
        val bikeFile = bikeFiles.firstOrNull { it.bike.bikeId == bikeId } ?: throw RepositoryError.NotFound("Bike not found: $bikeId")
        BikePartsValidators.requireUniqueBikeName(bikeFiles.map { it.bike }, normalizedName, ignoreBikeId = bikeId)

        val now = clock()
        bikeRepository.saveBikeFile(
            bikeFile.copy(
                bike =
                    bikeFile.bike.copy(
                        name = normalizedName,
                        karooMileageMeters = mileageMeters,
                        updatedAt = now,
                    ),
                lastUpdatedAt = now,
            ),
        )
        val updatedMetadata = normalizeMetadata(metadataRepository.read(), bikeRepository.listBikeFiles())
        metadataRepository.save(updatedMetadata)
        logger.debug("Updated local bike $bikeId")
        return BikeOverview(updatedMetadata.bikeIndex, updatedMetadata.activeBikeId)
    }

    override suspend fun deleteBike(bikeId: String): BikeOverview {
        val bikeFile = bikeRepository.getBikeFile(bikeId) ?: throw RepositoryError.NotFound("Bike not found: $bikeId")
        bikeRepository.deleteBikeFile(bikeId)
        val metadata = metadataRepository.read()
        val updatedMetadata =
            normalizeMetadata(
                metadata = metadata.copy(activeBikeId = metadata.activeBikeId.takeUnless { it == bikeId }),
                bikeFiles = bikeRepository.listBikeFiles(),
            )
        metadataRepository.save(updatedMetadata)
        logger.debug("Deleted local bike ${bikeFile.bike.bikeId}")
        return BikeOverview(updatedMetadata.bikeIndex, updatedMetadata.activeBikeId)
    }

    override suspend fun selectActiveBike(bikeId: String): BikeOverview {
        val bike =
            bikeRepository.getBikeFile(bikeId)?.bike
                ?: throw RepositoryError.NotFound("Bike not found: $bikeId")
        val bikeFiles = bikeRepository.listBikeFiles()
        val updatedMetadata =
            normalizeMetadata(
                metadata = metadataRepository.read().copy(activeBikeId = bike.bikeId),
                bikeFiles = bikeFiles,
            )
        metadataRepository.save(updatedMetadata)
        logger.debug("Selected active bike ${bike.bikeId}")
        return BikeOverview(updatedMetadata.bikeIndex, updatedMetadata.activeBikeId)
    }

    private fun requireBikeName(name: String): String {
        val normalized = name.trim()
        if (normalized.isBlank()) {
            throw RepositoryError.Validation("Bike name is required")
        }
        return normalized
    }

    private fun normalizeMetadata(
        metadata: SharedMetadata,
        bikeFiles: List<BikeFile>,
    ): SharedMetadata {
        val bikeIndex =
            bikeFiles
                .map { BikeSummary(it.bike.bikeId, it.bike.name, it.bike.karooMileageMeters) }
                .sortedBy { it.name.lowercase() }
        val activeBikeId = metadata.activeBikeId?.takeIf { activeId -> bikeIndex.any { it.bikeId == activeId } }
        return metadata.copy(
            activeBikeId = activeBikeId,
            bikeIndex = bikeIndex,
        )
    }
}
