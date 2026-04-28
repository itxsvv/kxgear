package kxgear.bikeparts.data.repository

import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kxgear.bikeparts.data.storage.AtomicJsonFileStore
import kxgear.bikeparts.domain.service.BikeLifecycleService
import kxgear.bikeparts.domain.service.BikePartsService
import kxgear.bikeparts.domain.service.PartLifecycleService
import kxgear.bikeparts.integration.logging.BikePartsLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PartRepositoryTest {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun addPartPersistsInsideBikeFile() = runBlocking {
        val root = Files.createTempDirectory("part-repository-create")
        val bikeRepository = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val bikeLifecycleService = createBikeLifecycleService(root, bikeRepository)
        val service = createPartLifecycleService(bikeRepository)

        bikeLifecycleService.addBike("Road", 2500)
        service.addPart("bike-1", "Chain", 15)

        val persisted = bikeRepository.getBikeFile("bike-1")

        assertNotNull(persisted)
        assertEquals(1, persisted?.parts?.size)
        assertEquals("Chain", persisted?.parts?.single()?.name)
        assertEquals(15, persisted?.parts?.single()?.riddenMileage)
        assertEquals(2000L, persisted?.parts?.single()?.createdDate)
    }

    @Test
    fun archivePartPersistsArchivedReadsAfterReload() = runBlocking {
        val root = Files.createTempDirectory("part-repository-archive")
        val bikeRepository = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val bikeLifecycleService = createBikeLifecycleService(root, bikeRepository)
        val service = createPartLifecycleService(bikeRepository)

        bikeLifecycleService.addBike("Road", 2500)
        service.addPart("bike-1", "Cassette", 0)
        service.archivePart("bike-1", "part-1")

        val reloaded = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val bikeFile = reloaded.getBikeFile("bike-1")

        assertEquals(1, bikeFile?.parts?.size)
        assertEquals("ARCHIVED", bikeFile?.parts?.single()?.status?.name)
    }

    @Test
    fun updatePartPersistsAlertConfigurationAfterReload() = runBlocking {
        val root = Files.createTempDirectory("part-repository-alert")
        val bikeRepository = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val bikeLifecycleService = createBikeLifecycleService(root, bikeRepository)
        val service = createPartLifecycleService(bikeRepository)

        bikeLifecycleService.addBike("Road", 2500)
        service.addPart("bike-1", "Chain", 15)
        service.updatePart("bike-1", "part-1", "Chain", 15, 250, "Service chain")

        val reloaded = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val bikeFile = reloaded.getBikeFile("bike-1")

        assertEquals(0, bikeFile?.parts?.single()?.curAlertMileage)
        assertEquals(250000, bikeFile?.parts?.single()?.targetAlertMileage)
        assertEquals("Service chain", bikeFile?.parts?.single()?.alertText)
    }

    private fun createBikeLifecycleService(
        root: java.nio.file.Path,
        bikeRepository: JsonBikeRepository,
    ): BikeLifecycleService =
        BikeLifecycleService(
            bikeRepository = bikeRepository,
            metadataRepository =
                JsonMetadataRepository(
                    metadataPath = root.resolve("metadata").resolve("shared-metadata.json"),
                    json = json,
                    fileStore = AtomicJsonFileStore(),
                    dispatcher = Dispatchers.Unconfined,
                ),
            logger = BikePartsLogger(),
            clock = { 1000L },
            idProvider = { "bike-1" },
        )

    private fun createPartLifecycleService(bikeRepository: JsonBikeRepository): PartLifecycleService =
        PartLifecycleService(
            bikeRepository = bikeRepository,
            bikePartsService = BikePartsService(),
            logger = BikePartsLogger(),
            idProvider = { "part-1" },
            clock = { 2000L },
        )
}
