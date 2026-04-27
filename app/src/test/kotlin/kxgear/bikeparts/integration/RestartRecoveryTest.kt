package kxgear.bikeparts.integration

import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kxgear.bikeparts.data.repository.JsonBikeRepository
import kxgear.bikeparts.data.repository.JsonMetadataRepository
import kxgear.bikeparts.data.storage.AtomicJsonFileStore
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.service.BikeLifecycleService
import kxgear.bikeparts.domain.service.BikePartsService
import kxgear.bikeparts.domain.service.PartLifecycleService
import kxgear.bikeparts.domain.service.RideProcessingResult
import kxgear.bikeparts.domain.service.RideProcessingService
import kxgear.bikeparts.integration.logging.BikePartsLogger
import kxgear.bikeparts.integration.notifications.NoOpPartAlertNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartRecoveryTest {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun restartReloadsActiveBikeAndUpdatedParts() = runBlocking {
        val root = Files.createTempDirectory("restart-recovery")
        val repositories = repositories(root)
        val bikeLifecycleService =
            BikeLifecycleService(
                bikeRepository = repositories.bikeRepository,
                metadataRepository = repositories.metadataRepository,
                logger = BikePartsLogger(),
                clock = sequentialClock(),
                idProvider = sequentialIdProvider("bike"),
            )
        val partLifecycleService =
            PartLifecycleService(
                bikeRepository = repositories.bikeRepository,
                bikePartsService = BikePartsService(),
                logger = BikePartsLogger(),
                idProvider = sequentialIdProvider("part"),
                clock = sequentialClock(start = 2_000L),
            )
        val rideProcessingService =
            RideProcessingService(
                metadataRepository = repositories.metadataRepository,
                bikeRepository = repositories.bikeRepository,
                bikePartsService = BikePartsService(),
                partAlertNotifier = NoOpPartAlertNotifier(),
                logger = BikePartsLogger(),
            )

        bikeLifecycleService.addBike("Road", 2500)
        bikeLifecycleService.addBike("Gravel", 4500)
        val roadBike = checkNotNull(bikeLifecycleService.getBike("bike-1"))
        bikeLifecycleService.selectActiveBike(roadBike.bikeId)
        val created = partLifecycleService.addPart(roadBike.bikeId, "Chain", 5)
        val partId = created.installedParts.single().partId
        val applied = rideProcessingService.processRideMetric(metricValue = 125, recordedAt = 3_000L)
        assertTrue(applied is RideProcessingResult.Applied)
        partLifecycleService.replacePart(
            bikeId = roadBike.bikeId,
            oldPartId = partId,
            name = "Chain 2",
            riddenMileage = 1,
        )

        val reloadedRepositories = repositories(root)
        val restartedBikeLifecycleService =
            BikeLifecycleService(
                bikeRepository = reloadedRepositories.bikeRepository,
                metadataRepository = reloadedRepositories.metadataRepository,
                logger = BikePartsLogger(),
            )
        val restartedPartLifecycleService =
            PartLifecycleService(
                bikeRepository = reloadedRepositories.bikeRepository,
                bikePartsService = BikePartsService(),
                logger = BikePartsLogger(),
            )

        val overview = restartedBikeLifecycleService.loadOverview()
        val details = restartedPartLifecycleService.loadBikeDetails(roadBike.bikeId)

        assertEquals(roadBike.bikeId, overview.activeBikeId)
        assertEquals(listOf("Gravel", "Road"), overview.bikes.map { it.name })
        assertEquals(1, details.installedParts.size)
        assertEquals("Chain 2", details.installedParts.single().name)
        assertEquals(1, details.installedParts.single().riddenMileage)
        assertEquals(1, details.archivedParts.size)
        assertEquals(PartStatus.ARCHIVED, details.archivedParts.single().status)
        assertEquals(130, details.archivedParts.single().currentMileage)
        assertEquals(2625, details.bike.karooMileageMeters)
        assertEquals(125, details.rideCursor.lastAcceptedMetricValue)
        assertEquals(3_000L, details.rideCursor.lastAcceptedAt)
    }

    @Test
    fun restartClearsStaleActiveBikeWhenFileIsMissing() = runBlocking {
        val root = Files.createTempDirectory("restart-stale-active")
        val repositories = repositories(root)
        val bikeLifecycleService =
            BikeLifecycleService(
                bikeRepository = repositories.bikeRepository,
                metadataRepository = repositories.metadataRepository,
                logger = BikePartsLogger(),
                clock = sequentialClock(),
                idProvider = { "bike-1" },
            )

        bikeLifecycleService.addBike("Road", 2500)
        val bike = checkNotNull(bikeLifecycleService.getBike("bike-1"))
        bikeLifecycleService.selectActiveBike(bike.bikeId)
        Files.delete(root.resolve("bikes").resolve("${bike.bikeId}.json"))

        val reloadedRepositories = repositories(root)
        val restartedBikeLifecycleService =
            BikeLifecycleService(
                bikeRepository = reloadedRepositories.bikeRepository,
                metadataRepository = reloadedRepositories.metadataRepository,
                logger = BikePartsLogger(),
            )

        val overview = restartedBikeLifecycleService.loadOverview()

        assertNull(overview.activeBikeId)
        assertTrue(overview.bikes.isEmpty())
        assertNull(reloadedRepositories.metadataRepository.read().activeBikeId)
    }

    private fun repositories(root: java.nio.file.Path): TestRepositories =
        TestRepositories(
            bikeRepository =
                JsonBikeRepository(
                    bikesDirectory = root.resolve("bikes"),
                    json = json,
                    fileStore = AtomicJsonFileStore(),
                    dispatcher = Dispatchers.Unconfined,
                ),
            metadataRepository =
                JsonMetadataRepository(
                    metadataPath = root.resolve("metadata").resolve("shared-metadata.json"),
                    json = json,
                    fileStore = AtomicJsonFileStore(),
                    dispatcher = Dispatchers.Unconfined,
                ),
        )

    private fun sequentialClock(start: Long = 1_000L): () -> Long {
        var current = start
        return {
            current += 1_000L
            current
        }
    }

    private fun sequentialIdProvider(prefix: String): () -> String {
        var index = 0
        return {
            index += 1
            "$prefix-$index"
        }
    }

    private data class TestRepositories(
        val bikeRepository: JsonBikeRepository,
        val metadataRepository: JsonMetadataRepository,
    )
}
