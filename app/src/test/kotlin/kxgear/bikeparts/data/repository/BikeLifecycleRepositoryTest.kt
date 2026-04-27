package kxgear.bikeparts.data.repository

import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kxgear.bikeparts.data.storage.AtomicJsonFileStore
import kxgear.bikeparts.domain.service.BikeLifecycleService
import kxgear.bikeparts.integration.logging.BikePartsLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BikeLifecycleRepositoryTest {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun addBikeWritesOneFilePerLocalBike() = runBlocking {
        val root = Files.createTempDirectory("bike-lifecycle-add")
        val bikeRepository = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val metadataRepository =
            JsonMetadataRepository(
                metadataPath = root.resolve("metadata").resolve("shared-metadata.json"),
                json = json,
                fileStore = AtomicJsonFileStore(),
                dispatcher = Dispatchers.Unconfined,
            )
        val service =
            BikeLifecycleService(
                bikeRepository,
                metadataRepository,
                BikePartsLogger(),
                clock = { 1000L },
                idProvider = sequentialIdProvider("bike"),
            )

        service.addBike("Road", 111)
        service.addBike("Gravel", 222)

        assertEquals(2, bikeRepository.listBikeFiles().size)
        assertTrue(Files.exists(root.resolve("bikes").resolve("bike-1.json")))
        assertTrue(Files.exists(root.resolve("bikes").resolve("bike-2.json")))
    }

    @Test
    fun updateBikeUpdatesExistingLocalBikeWithoutDuplicatingFile() = runBlocking {
        val root = Files.createTempDirectory("bike-lifecycle-update")
        val bikeRepository = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val metadataRepository =
            JsonMetadataRepository(
                metadataPath = root.resolve("metadata").resolve("shared-metadata.json"),
                json = json,
                fileStore = AtomicJsonFileStore(),
                dispatcher = Dispatchers.Unconfined,
            )
        val service =
            BikeLifecycleService(
                bikeRepository,
                metadataRepository,
                BikePartsLogger(),
                clock = { 1000L },
                idProvider = { "bike-1" },
            )

        service.addBike("Road", 2500)
        service.updateBike("bike-1", "Road Updated", 3456)

        assertEquals(1, bikeRepository.listBikeFiles().size)
        assertEquals("Road Updated", bikeRepository.getBikeFile("bike-1")?.bike?.name)
        assertEquals(3456, bikeRepository.getBikeFile("bike-1")?.bike?.karooMileageMeters)
    }

    @Test
    fun selectActiveBikePersistsMetadataForLocalBike() = runBlocking {
        val root = Files.createTempDirectory("bike-lifecycle-active")
        val bikeRepository = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val metadataRepository =
            JsonMetadataRepository(
                metadataPath = root.resolve("metadata").resolve("shared-metadata.json"),
                json = json,
                fileStore = AtomicJsonFileStore(),
                dispatcher = Dispatchers.Unconfined,
            )
        val lifecycleService =
            BikeLifecycleService(
                bikeRepository,
                metadataRepository,
                BikePartsLogger(),
                clock = { 1000L },
                idProvider = sequentialIdProvider("bike"),
            )

        lifecycleService.addBike("Road", 111)
        lifecycleService.addBike("Gravel", 222)
        lifecycleService.selectActiveBike("bike-2")

        assertEquals("bike-2", metadataRepository.read().activeBikeId)
    }

    private fun sequentialIdProvider(prefix: String): () -> String {
        var index = 0
        return {
            index += 1
            "$prefix-$index"
        }
    }
}
