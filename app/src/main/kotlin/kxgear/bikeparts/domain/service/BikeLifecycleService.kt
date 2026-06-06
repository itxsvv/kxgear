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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BikeOverview(
    val bikes: List<BikeSummary>,
    val activeBikeId: String?,
)

interface BikeLifecycleGateway {
    suspend fun loadOverview(syncKarooOnStartup: Boolean = false): BikeOverview

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

    suspend fun addBikesFromKaroo(bikes: List<KarooBikeSnapshot>): BikeOverview
}

class BikeLifecycleService(
    private val bikeRepository: BikeRepository,
    private val metadataRepository: MetadataRepository,
    private val logger: BikePartsLogger,
    private val karooBikeCatalogGateway: KarooBikeCatalogGateway? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : BikeLifecycleGateway {
    private val karooSyncMutex = Mutex()

    override suspend fun loadOverview(syncKarooOnStartup: Boolean): BikeOverview {
        val bikeFiles =
            if (syncKarooOnStartup) {
                karooSyncMutex.withLock {
                    syncKarooBikes(bikeRepository.listBikeFiles())
                }
            } else {
                bikeRepository.listBikeFiles()
            }
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
                karooBikeId = null,
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

    override suspend fun addBikesFromKaroo(bikes: List<KarooBikeSnapshot>): BikeOverview {
        if (bikes.isEmpty()) {
            return loadOverview()
        }

        val existingBikeFiles = bikeRepository.listBikeFiles().toMutableList()
        val createdBikeIds = mutableListOf<String>()

        bikes.forEach { karooBike ->
            val normalizedName = requireBikeName(karooBike.name)
            BikePartsValidators.requireWholeMileage(karooBike.mileageMeters)
            BikePartsValidators.requireUniqueBikeName(existingBikeFiles.map { it.bike }, normalizedName)

            val now = clock()
            val bikeId = idProvider()
            val bike =
                Bike(
                    bikeId = bikeId,
                    karooBikeId = karooBike.karooBikeId,
                    name = normalizedName,
                    karooMileageMeters = karooBike.mileageMeters,
                    createdAt = now,
                    updatedAt = now,
                )
            val bikeFile =
                BikeFile(
                    bike = bike,
                    parts = emptyList(),
                    lastUpdatedAt = now,
                )
            bikeRepository.saveBikeFile(bikeFile)
            existingBikeFiles += bikeFile
            createdBikeIds += bikeId
        }

        val currentMetadata = metadataRepository.read()
        val metadataWithActiveImport =
            if (currentMetadata.activeBikeId == null && createdBikeIds.isNotEmpty()) {
                currentMetadata.copy(activeBikeId = createdBikeIds.first())
            } else {
                currentMetadata
            }
        val updatedMetadata = normalizeMetadata(metadataWithActiveImport, bikeRepository.listBikeFiles())
        metadataRepository.save(updatedMetadata)
        logger.debug("Imported ${createdBikeIds.size} Karoo bikes into local catalog")
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
                .map { BikeSummary(it.bike.bikeId, it.bike.name, it.bike.karooMileageMeters, hasKarooBikeId = it.bike.karooBikeId != null) }
                .sortedBy { it.name.lowercase() }
        val activeBikeId = metadata.activeBikeId?.takeIf { activeId -> bikeIndex.any { it.bikeId == activeId } }
        return metadata.copy(
            activeBikeId = activeBikeId,
            bikeIndex = bikeIndex,
        )
    }

    private suspend fun syncKarooBikes(bikeFiles: List<BikeFile>): List<BikeFile> {
        val gateway = karooBikeCatalogGateway ?: return bikeFiles
        val karooBikes =
            runCatching { gateway.listBikes() }
                .onFailure { error -> logger.warn("Unable to load Karoo bikes on startup: ${error.message}") }
                .getOrDefault(emptyList())
        if (karooBikes.isEmpty()) {
            return bikeFiles
        }

        val distinctKarooBikes = linkedMapOf<String, KarooBikeSnapshot>()
        karooBikes.forEach { bike ->
            distinctKarooBikes.putIfAbsent(normalizeBikeName(bike.name), bike)
        }

        val updatedBikeFiles = bikeFiles.toMutableList()
        val importedBikeIds = mutableListOf<String>()
        val currentMetadata = metadataRepository.read()
        val hadNoActiveBike = currentMetadata.activeBikeId == null

        fun rebuildLocalByName(): Map<String, List<BikeFile>> =
            updatedBikeFiles.groupBy { normalizeBikeName(it.bike.name) }

        distinctKarooBikes.values.forEach { karooBike ->
            val normalizedName = normalizeBikeName(karooBike.name)
            val localMatches = rebuildLocalByName()[normalizedName].orEmpty()
            val matchedLocalBike =
                localMatches
                    .sortedWith(
                        compareByDescending<BikeFile> { it.bike.bikeId == currentMetadata.activeBikeId }
                            .thenByDescending { it.parts.isNotEmpty() }
                            .thenByDescending { it.bike.karooBikeId == karooBike.karooBikeId }
                            .thenBy { it.bike.createdAt },
                    ).firstOrNull()
            localMatches
                .filter { candidate ->
                    matchedLocalBike != null &&
                        candidate.bike.bikeId != matchedLocalBike.bike.bikeId &&
                        candidate.bike.bikeId != currentMetadata.activeBikeId &&
                        candidate.parts.isEmpty()
                }.forEach { duplicateBike ->
                    bikeRepository.deleteBikeFile(duplicateBike.bike.bikeId)
                    updatedBikeFiles.removeAll { it.bike.bikeId == duplicateBike.bike.bikeId }
                    logger.debug("Deleted duplicate local bike ${duplicateBike.bike.bikeId} for Karoo bike ${karooBike.karooBikeId}")
                }
            if (matchedLocalBike == null) {
                val now = clock()
                val newBikeFile =
                    BikeFile(
                        bike =
                            Bike(
                                bikeId = idProvider(),
                                karooBikeId = karooBike.karooBikeId,
                                name = requireBikeName(karooBike.name),
                                karooMileageMeters = karooBike.mileageMeters,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        parts = emptyList(),
                        lastUpdatedAt = now,
                    )
                bikeRepository.saveBikeFile(newBikeFile)
                updatedBikeFiles += newBikeFile
                importedBikeIds += newBikeFile.bike.bikeId
                return@forEach
            }
            if (matchedLocalBike.bike.karooBikeId == karooBike.karooBikeId) {
                return@forEach
            }

            val now = clock()
            val updatedBikeFile =
                matchedLocalBike.copy(
                    bike =
                        matchedLocalBike.bike.copy(
                            karooBikeId = karooBike.karooBikeId,
                            updatedAt = now,
                        ),
                    lastUpdatedAt = now,
                )
            bikeRepository.saveBikeFile(updatedBikeFile)
            val index = updatedBikeFiles.indexOfFirst { it.bike.bikeId == updatedBikeFile.bike.bikeId }
            if (index >= 0) {
                updatedBikeFiles[index] = updatedBikeFile
            }
        }

        updatedBikeFiles.toList().forEach { localBikeFile ->
            val normalizedName = normalizeBikeName(localBikeFile.bike.name)
            if (distinctKarooBikes.containsKey(normalizedName)) {
                return@forEach
            }
            if (localBikeFile.bike.karooBikeId == null) {
                return@forEach
            }

            val now = clock()
            val clearedBikeFile =
                localBikeFile.copy(
                    bike =
                        localBikeFile.bike.copy(
                            karooBikeId = null,
                            updatedAt = now,
                        ),
                    lastUpdatedAt = now,
                )
            bikeRepository.saveBikeFile(clearedBikeFile)
            val index = updatedBikeFiles.indexOfFirst { it.bike.bikeId == clearedBikeFile.bike.bikeId }
            if (index >= 0) {
                updatedBikeFiles[index] = clearedBikeFile
            }
        }

        if (hadNoActiveBike && importedBikeIds.isNotEmpty()) {
            metadataRepository.save(currentMetadata.copy(activeBikeId = importedBikeIds.first()))
        }

        return updatedBikeFiles
    }

    private fun normalizeBikeName(name: String): String = name.trim().lowercase()
}
