package kxgear.bikeparts.domain.service

import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.model.SharedMetadata
import kxgear.bikeparts.domain.repository.BikeRepository
import kxgear.bikeparts.domain.repository.MetadataRepository
import kxgear.bikeparts.integration.logging.BikePartsLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BikeLifecycleServiceTest {
    private val logger = BikePartsLogger()

    @Test
    fun loadOverviewReturnsLocalBikes() = runBlocking {
        val bikeRepository =
            InMemoryBikeRepository(
                bikeFile("local-1", "Local Only"),
                bikeFile("bike-2", "Gravel", karooBikeId = "karoo-2"),
            )
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "local-1"))
        val service = BikeLifecycleService(bikeRepository, metadataRepository, logger)

        val overview = service.loadOverview()

        assertEquals(listOf("bike-2", "local-1"), overview.bikes.map { it.bikeId })
        assertEquals("local-1", overview.activeBikeId)
    }

    @Test
    fun selectActiveBikeUpdatesMetadataForLocalBike() = runBlocking {
        val bikeRepository =
            InMemoryBikeRepository(
                bikeFile("bike-1", "Road"),
                bikeFile("bike-2", "Gravel"),
            )
        val metadataRepository = InMemoryMetadataRepository()
        val service = BikeLifecycleService(bikeRepository, metadataRepository, logger)

        service.selectActiveBike("bike-2")

        assertEquals("bike-2", metadataRepository.read().activeBikeId)
    }

    @Test
    fun addBikeCreatesLocalBikeAndSetsActiveWhenUnset() = runBlocking {
        val bikeRepository = InMemoryBikeRepository()
        val metadataRepository = InMemoryMetadataRepository()
        val service =
            BikeLifecycleService(
                bikeRepository,
                metadataRepository,
                logger,
                clock = { 2000L },
                idProvider = { "bike-created" },
            )

        val overview = service.addBike(" Road ", 1234)

        assertEquals(listOf("bike-created"), overview.bikes.map { it.bikeId })
        assertEquals("Road", overview.bikes.single().name)
        assertEquals(1234, overview.bikes.single().mileageMeters)
        assertEquals("bike-created", overview.activeBikeId)
        assertEquals("bike-created", metadataRepository.read().activeBikeId)
        assertEquals("Road", bikeRepository.getBikeFile("bike-created")?.bike?.name)
        assertEquals(1234, bikeRepository.getBikeFile("bike-created")?.bike?.karooMileageMeters)
    }

    @Test
    fun updateBikeChangesNameAndMileage() = runBlocking {
        val bikeRepository = InMemoryBikeRepository(bikeFile("bike-1", "Road"))
        val metadataRepository = InMemoryMetadataRepository()
        val service = BikeLifecycleService(bikeRepository, metadataRepository, logger, clock = { 2000L })

        service.updateBike("bike-1", "Gravel", 4321)

        assertEquals("Gravel", bikeRepository.getBikeFile("bike-1")?.bike?.name)
        assertEquals(4321, bikeRepository.getBikeFile("bike-1")?.bike?.karooMileageMeters)
        assertEquals("Gravel", metadataRepository.read().bikeIndex.single().name)
        assertEquals(4321, metadataRepository.read().bikeIndex.single().mileageMeters)
    }

    @Test
    fun deleteBikeRemovesLocalBikeAndClearsActiveSelection() = runBlocking {
        val bikeRepository = InMemoryBikeRepository(bikeFile("bike-1", "Road"))
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val service = BikeLifecycleService(bikeRepository, metadataRepository, logger)

        val overview = service.deleteBike("bike-1")

        assertEquals(emptyList<String>(), overview.bikes.map { it.bikeId })
        assertEquals(null, overview.activeBikeId)
        assertEquals(null, metadataRepository.read().activeBikeId)
        assertEquals(null, bikeRepository.getBikeFile("bike-1"))
    }

    private fun bikeFile(
        bikeId: String,
        name: String,
        karooBikeId: String? = null,
    ): BikeFile =
        BikeFile(
            bike =
                Bike(
                    bikeId = bikeId,
                    karooBikeId = karooBikeId,
                    name = name,
                    karooMileageMeters = 2500,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            parts =
                listOf(
                    Part(
                        partId = "part-1",
                        name = "Chain",
                        riddenMileage = 0,
                        status = PartStatus.INSTALLED,
                        createdAt = 1000L,
                        updatedAt = 1000L,
                    ),
                ),
            lastUpdatedAt = 1000L,
        )

    private class InMemoryBikeRepository(
        vararg bikeFiles: BikeFile,
    ) : BikeRepository {
        private val bikes = linkedMapOf<String, BikeFile>()

        init {
            bikeFiles.forEach { bikes[it.bike.bikeId] = it }
        }

        override suspend fun getBikeFile(bikeId: String): BikeFile? = bikes[bikeId]

        override suspend fun listBikeFiles(): List<BikeFile> = bikes.values.toList()

        override suspend fun saveBikeFile(bikeFile: BikeFile) {
            bikes[bikeFile.bike.bikeId] = bikeFile
        }

        override suspend fun deleteBikeFile(bikeId: String) {
            bikes.remove(bikeId)
        }
    }

    private class InMemoryMetadataRepository(
        private var metadata: SharedMetadata = SharedMetadata(),
    ) : MetadataRepository {
        override suspend fun read(): SharedMetadata = metadata

        override suspend fun save(metadata: SharedMetadata) {
            this.metadata = metadata
        }
    }
}
