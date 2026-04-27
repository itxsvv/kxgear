package kxgear.bikeparts.domain.service

import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.model.RideCursor
import kxgear.bikeparts.domain.model.SharedMetadata
import kxgear.bikeparts.domain.repository.BikeRepository
import kxgear.bikeparts.domain.repository.MetadataRepository
import kxgear.bikeparts.integration.logging.BikePartsLogger
import kxgear.bikeparts.integration.notifications.PartAlertNotifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideProcessingServiceTest {
    private val bikePartsService = BikePartsService()
    private val logger = BikePartsLogger()

    @Test
    fun processRideMetricIgnoresWhenNoActiveBike() {
        val bikeRepository = InMemoryBikeRepository()
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata())
        val service = createService(metadataRepository, bikeRepository)

        val result = runBlocking { service.processRideMetric(metricValue = 120, recordedAt = 2000L) }

        assertEquals(RideProcessingResult.NoActiveBike, result)
        assertEquals(0, bikeRepository.saveCount)
    }

    @Test
    fun processRideMetricPersistsUpdatedActiveBike() {
        val initial =
            bikeFile(
                bikeMileageMeters = 1000,
                rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 10)),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val service = createService(metadataRepository, bikeRepository)

        val result = runBlocking { service.processRideMetric(metricValue = 200, recordedAt = 2000L) }

        assertTrue(result is RideProcessingResult.Applied)
        assertEquals(1, bikeRepository.saveCount)
        assertEquals(1100, bikeRepository.requireBike("bike-1").bike.karooMileageMeters)
        assertEquals(110, bikeRepository.requireBike("bike-1").parts.single().riddenMileage)
        assertEquals(200, bikeRepository.requireBike("bike-1").rideCursor.lastAcceptedMetricValue)
    }

    @Test
    fun processRideMetricDefersPersistenceBelowThreshold() {
        val initial =
            bikeFile(
                bikeMileageMeters = 1000,
                rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 10)),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val service = createService(metadataRepository, bikeRepository)

        val result = runBlocking { service.processRideMetric(metricValue = 150, recordedAt = 2000L) }

        assertTrue(result is RideProcessingResult.Deferred)
        result as RideProcessingResult.Deferred
        assertEquals(0, bikeRepository.saveCount)
        assertEquals(1050, result.bikeFile.bike.karooMileageMeters)
        assertEquals(60, result.bikeFile.parts.single().riddenMileage)
        assertEquals(150, result.bikeFile.rideCursor.lastAcceptedMetricValue)
        assertEquals(1000, bikeRepository.requireBike("bike-1").bike.karooMileageMeters)
        assertEquals(10, bikeRepository.requireBike("bike-1").parts.single().riddenMileage)
        assertEquals(100, bikeRepository.requireBike("bike-1").rideCursor.lastAcceptedMetricValue)
    }

    @Test
    fun flushPendingRideMetricsPersistsBelowThresholdDistance() {
        val initial =
            bikeFile(
                bikeMileageMeters = 1000,
                rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 10)),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val service = createService(metadataRepository, bikeRepository)

        runBlocking {
            service.processRideMetric(metricValue = 150, recordedAt = 2000L)
            service.flushPendingRideMetrics()
        }

        assertEquals(1, bikeRepository.saveCount)
        assertEquals(1050, bikeRepository.requireBike("bike-1").bike.karooMileageMeters)
        assertEquals(60, bikeRepository.requireBike("bike-1").parts.single().riddenMileage)
        assertEquals(150, bikeRepository.requireBike("bike-1").rideCursor.lastAcceptedMetricValue)
    }

    @Test
    fun processRideMetricDoesNotPersistDuplicateValues() {
        val initial =
            bikeFile(
                rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 10)),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val service = createService(metadataRepository, bikeRepository)

        val result = runBlocking { service.processRideMetric(metricValue = 100, recordedAt = 2000L) }

        assertTrue(result is RideProcessingResult.IgnoredDuplicate)
        assertEquals(0, bikeRepository.saveCount)
        assertEquals(10, bikeRepository.requireBike("bike-1").parts.single().riddenMileage)
    }

    @Test
    fun processRideMetricPersistsAfterAccumulatingOneHundredMeters() {
        val initial =
            bikeFile(
                bikeMileageMeters = 1000,
                rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 10)),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val service = createService(metadataRepository, bikeRepository)

        val result =
            runBlocking {
                service.processRideMetric(metricValue = 150, recordedAt = 2000L)
                service.processRideMetric(metricValue = 200, recordedAt = 3000L)
            }

        assertTrue(result is RideProcessingResult.Applied)
        assertEquals(1, bikeRepository.saveCount)
        assertEquals(1100, bikeRepository.requireBike("bike-1").bike.karooMileageMeters)
        assertEquals(110, bikeRepository.requireBike("bike-1").parts.single().riddenMileage)
        assertEquals(200, bikeRepository.requireBike("bike-1").rideCursor.lastAcceptedMetricValue)
    }

    @Test
    fun processRideMetricRejectsDecreasingValuesWithoutPersisting() {
        val initial =
            bikeFile(
                rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 10)),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val service = createService(metadataRepository, bikeRepository)

        val result = runBlocking { service.processRideMetric(metricValue = 90, recordedAt = 2000L) }

        assertTrue(result is RideProcessingResult.RejectedInvalid)
        assertEquals(0, bikeRepository.saveCount)
        assertEquals(10, bikeRepository.requireBike("bike-1").parts.single().riddenMileage)
    }

    @Test
    fun startNewRideSessionAllowsDistanceToRestartFromZero() {
        val initial =
            bikeFile(
                rideCursor = RideCursor(lastAcceptedMetricValue = 5000, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 5000)),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val service = createService(metadataRepository, bikeRepository)

        val result =
            runBlocking {
                service.startNewRideSession()
                service.processRideMetric(metricValue = 10, recordedAt = 2000L)
            }

        assertTrue(result is RideProcessingResult.Deferred)
        result as RideProcessingResult.Deferred
        assertEquals(1, bikeRepository.saveCount)
        assertEquals(null, bikeRepository.requireBike("bike-1").rideCursor.lastAcceptedMetricValue)
        assertEquals(5010, result.bikeFile.parts.single().riddenMileage)
        assertEquals(10, result.bikeFile.rideCursor.lastAcceptedMetricValue)
    }

    @Test
    fun processRideMetricEmitsAlertWhenThresholdIsCrossed() {
        val initial =
            bikeFile(
                bikeMileageMeters = 1000,
                rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 995, alertMileage = 1, alertText = "Service chain")),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val notifier = RecordingPartAlertNotifier()
        val service = createService(metadataRepository, bikeRepository, notifier)

        val result = runBlocking { service.processRideMetric(metricValue = 110, recordedAt = 2000L) }

        assertTrue(result is RideProcessingResult.Applied)
        assertEquals(1, notifier.alerts.size)
        assertEquals(1000, notifier.alerts.single().thresholdMeters)
        assertEquals(1005, bikeRepository.requireBike("bike-1").parts.single().riddenMileage)
        assertEquals(1000, bikeRepository.requireBike("bike-1").parts.single().lastAlertThresholdMeters)
    }

    @Test
    fun processRideMetricUsesHighestThresholdForLargeJump() {
        val initial =
            bikeFile(
                rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 900, alertMileage = 1)),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val notifier = RecordingPartAlertNotifier()
        val service = createService(metadataRepository, bikeRepository, notifier)

        runBlocking {
            service.processRideMetric(metricValue = 2200, recordedAt = 2000L)
        }

        assertEquals(1, notifier.alerts.size)
        assertEquals(3000, notifier.alerts.single().thresholdMeters)
    }

    @Test
    fun processRideMetricDoesNotEmitDuplicateAlertForSameThreshold() {
        val initial =
            bikeFile(
                rideCursor = RideCursor(lastAcceptedMetricValue = 1000, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 1005, alertMileage = 1, lastAlertThresholdMeters = 1000)),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val notifier = RecordingPartAlertNotifier()
        val service = createService(metadataRepository, bikeRepository, notifier)

        runBlocking {
            service.processRideMetric(metricValue = 1010, recordedAt = 2000L)
        }

        assertEquals(0, notifier.alerts.size)
    }

    @Test
    fun processRideMetricPersistsImmediatelyWhenAlertTriggersBelowDistanceThreshold() {
        val initial =
            bikeFile(
                rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000L),
                parts = listOf(part(riddenMileage = 995, alertMileage = 1)),
            )
        val bikeRepository = InMemoryBikeRepository(initial)
        val metadataRepository = InMemoryMetadataRepository(SharedMetadata(activeBikeId = "bike-1"))
        val notifier = RecordingPartAlertNotifier()
        val service = createService(metadataRepository, bikeRepository, notifier)

        val result = runBlocking { service.processRideMetric(metricValue = 110, recordedAt = 2000L) }

        assertTrue(result is RideProcessingResult.Applied)
        assertEquals(1, bikeRepository.saveCount)
        assertEquals(1, notifier.alerts.size)
    }

    private fun createService(
        metadataRepository: MetadataRepository,
        bikeRepository: BikeRepository,
        notifier: PartAlertNotifier = RecordingPartAlertNotifier(),
    ): RideProcessingService =
        RideProcessingService(
            metadataRepository = metadataRepository,
            bikeRepository = bikeRepository,
            bikePartsService = bikePartsService,
            partAlertNotifier = notifier,
            logger = logger,
        )

    private fun bikeFile(
        bikeMileageMeters: Int = 0,
        parts: List<Part> = listOf(part()),
        rideCursor: RideCursor = RideCursor(),
    ): BikeFile =
        BikeFile(
            bike =
                Bike(
                    bikeId = "bike-1",
                    name = "Road",
                    karooMileageMeters = bikeMileageMeters,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            parts = parts,
            rideCursor = rideCursor,
            lastUpdatedAt = 1000L,
        )

    private fun part(
        riddenMileage: Int = 0,
        status: PartStatus = PartStatus.INSTALLED,
        alertMileage: Int? = null,
        alertText: String? = null,
        lastAlertThresholdMeters: Int? = null,
    ): Part =
        Part(
            partId = "part-1",
            name = "Chain",
            riddenMileage = riddenMileage,
            status = status,
            createdAt = 1000L,
            alertMileage = alertMileage,
            alertText = alertText,
            lastAlertThresholdMeters = lastAlertThresholdMeters,
            updatedAt = 1000L,
            archivedAt = null,
        )

    private class RecordingPartAlertNotifier : PartAlertNotifier {
        data class AlertRecord(
            val bikeName: String,
            val partName: String,
            val alertText: String?,
            val thresholdMeters: Int,
            val currentMileageMeters: Int,
        )

        val alerts = mutableListOf<AlertRecord>()

        override fun showAlert(
            bikeName: String,
            partName: String,
            alertText: String?,
            thresholdMeters: Int,
            currentMileageMeters: Int,
        ) {
            alerts += AlertRecord(bikeName, partName, alertText, thresholdMeters, currentMileageMeters)
        }
    }

    private class InMemoryMetadataRepository(
        private var metadata: SharedMetadata,
    ) : MetadataRepository {
        override suspend fun read(): SharedMetadata = metadata

        override suspend fun save(metadata: SharedMetadata) {
            this.metadata = metadata
        }
    }

    private class InMemoryBikeRepository(
        bikeFile: BikeFile? = null,
    ) : BikeRepository {
        private val bikes = linkedMapOf<String, BikeFile>()
        var saveCount: Int = 0
            private set

        init {
            if (bikeFile != null) {
                bikes[bikeFile.bike.bikeId] = bikeFile
            }
        }

        override suspend fun getBikeFile(bikeId: String): BikeFile? = bikes[bikeId]

        override suspend fun listBikeFiles(): List<BikeFile> = bikes.values.toList()

        override suspend fun saveBikeFile(bikeFile: BikeFile) {
            saveCount += 1
            bikes[bikeFile.bike.bikeId] = bikeFile
        }

        override suspend fun deleteBikeFile(bikeId: String) {
            bikes.remove(bikeId)
        }

        fun requireBike(bikeId: String): BikeFile = checkNotNull(bikes[bikeId])
    }
}
