package kxgear.bikeparts.data.repository

import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kxgear.bikeparts.data.storage.AtomicJsonFileStore
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.model.RideCursor
import kxgear.bikeparts.domain.model.SharedMetadata
import kxgear.bikeparts.domain.service.BikePartsService
import kxgear.bikeparts.domain.service.PartLifecycleService
import kxgear.bikeparts.domain.service.RideProcessingResult
import kxgear.bikeparts.domain.service.RideProcessingService
import kxgear.bikeparts.integration.logging.BikePartsLogger
import kxgear.bikeparts.integration.notifications.NoOpPartAlertNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideUpdateRepositoryTest {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun processRideMetricPersistsRideCursorAfterReload() = runBlocking {
        val root = Files.createTempDirectory("ride-update-repository")
        val bikeRepository = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val metadataRepository =
            JsonMetadataRepository(
                metadataPath = root.resolve("metadata").resolve("shared-metadata.json"),
                json = json,
                fileStore = AtomicJsonFileStore(),
                dispatcher = Dispatchers.Unconfined,
            )
        bikeRepository.saveBikeFile(bikeFile())
        metadataRepository.save(SharedMetadata(activeBikeId = "bike-1"))

        val service =
            RideProcessingService(
                metadataRepository,
                bikeRepository,
                BikePartsService(),
                NoOpPartAlertNotifier(),
                BikePartsLogger(),
            )

        val result = service.processRideMetric(metricValue = 200, recordedAt = 3000L)
        val reloaded = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val persisted = reloaded.getBikeFile("bike-1")

        assertTrue(result is RideProcessingResult.Applied)
        assertEquals(200, persisted?.rideCursor?.lastAcceptedMetricValue)
        assertEquals(3000L, persisted?.rideCursor?.lastAcceptedAt)
        assertEquals(100, persisted?.parts?.single()?.riddenMileage)
    }

    @Test
    fun flushPendingRideMetricPersistsRideCursorAfterReload() = runBlocking {
        val root = Files.createTempDirectory("ride-update-repository-flush")
        val bikeRepository = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val metadataRepository =
            JsonMetadataRepository(
                metadataPath = root.resolve("metadata").resolve("shared-metadata.json"),
                json = json,
                fileStore = AtomicJsonFileStore(),
                dispatcher = Dispatchers.Unconfined,
            )
        bikeRepository.saveBikeFile(bikeFile())
        metadataRepository.save(SharedMetadata(activeBikeId = "bike-1"))

        val service =
            RideProcessingService(
                metadataRepository,
                bikeRepository,
                BikePartsService(),
                NoOpPartAlertNotifier(),
                BikePartsLogger(),
            )

        val result = service.processRideMetric(metricValue = 130, recordedAt = 3000L)
        service.flushPendingRideMetrics()
        val reloaded = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val persisted = reloaded.getBikeFile("bike-1")

        assertTrue(result is RideProcessingResult.Deferred)
        result as RideProcessingResult.Deferred
        assertEquals(130, result.bikeFile.rideCursor.lastAcceptedMetricValue)
        assertEquals(30, result.bikeFile.parts.single().riddenMileage)
        assertEquals(130, persisted?.rideCursor?.lastAcceptedMetricValue)
        assertEquals(3000L, persisted?.rideCursor?.lastAcceptedAt)
        assertEquals(30, persisted?.parts?.single()?.riddenMileage)
    }

    @Test
    fun replacePartPersistsArchivedAndReplacementStates() = runBlocking {
        val root = Files.createTempDirectory("ride-replacement-repository")
        val bikeRepository = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        bikeRepository.saveBikeFile(
            bikeFile(
                parts =
                    listOf(
                        Part(
                            partId = "part-1",
                            name = "Chain",
                            riddenMileage = 50,
                            status = PartStatus.INSTALLED,
                            createdAt = 1000L,
                            updatedAt = 1000L,
                        ),
                    ),
            ),
        )

        val service =
            PartLifecycleService(
                bikeRepository = bikeRepository,
                bikePartsService = BikePartsService(),
                logger = BikePartsLogger(),
                idProvider = { "part-2" },
                clock = { 4000L },
            )

        service.replacePart(
            bikeId = "bike-1",
            oldPartId = "part-1",
            name = "Cassette",
            riddenMileage = 7,
        )

        val reloaded = JsonBikeRepository(root.resolve("bikes"), json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val persisted = reloaded.getBikeFile("bike-1")
        requireNotNull(persisted)
        val archived = persisted.parts.first { it.partId == "part-1" }
        val replacement = persisted.parts.first { it.partId == "part-2" }

        assertEquals(PartStatus.ARCHIVED, archived.status)
        assertEquals(50, archived.currentMileage)
        assertEquals(PartStatus.INSTALLED, replacement.status)
        assertEquals(7, replacement.riddenMileage)
    }

    private fun bikeFile(
        parts: List<Part> = listOf(part()),
        rideCursor: RideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000L),
    ): BikeFile =
        BikeFile(
            bike =
                Bike(
                    bikeId = "bike-1",
                    name = "Road",
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            parts = parts,
            rideCursor = rideCursor,
            lastUpdatedAt = 1000L,
        )

    private fun part(): Part =
        Part(
            partId = "part-1",
            name = "Chain",
            riddenMileage = 0,
            status = PartStatus.INSTALLED,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
}
