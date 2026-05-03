package kxgear.bikeparts.domain.service

import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.RideCursor
import kxgear.bikeparts.domain.repository.BikeRepository
import kxgear.bikeparts.domain.repository.MetadataRepository
import kxgear.bikeparts.integration.notifications.PartAlertNotifier
import kxgear.bikeparts.integration.logging.BikePartsLogger

sealed interface RideProcessingResult {
    data object NoActiveBike : RideProcessingResult
    data object ActiveBikeMissing : RideProcessingResult
    data class Applied(val bikeFile: BikeFile) : RideProcessingResult
    data class Deferred(val bikeFile: BikeFile) : RideProcessingResult
    data class IgnoredDuplicate(val bikeFile: BikeFile) : RideProcessingResult
    data class RejectedInvalid(val reason: String) : RideProcessingResult
}

interface RideMetricProcessor {
    suspend fun processRideMetric(
        metricValue: Int,
        recordedAt: Long,
    ): RideProcessingResult

    suspend fun startNewRideSession()

    suspend fun flushPendingRideMetrics()
}

class RideProcessingService(
    private val metadataRepository: MetadataRepository,
    private val bikeRepository: BikeRepository,
    private val bikePartsService: BikePartsService,
    private val partAlertNotifier: PartAlertNotifier,
    private val logger: BikePartsLogger,
) : RideMetricProcessor {
    private var cachedActiveBikeId: String? = null
    private val cachedBikeFilesByBikeId = mutableMapOf<String, BikeFile>()
    private val persistedBikeFilesByBikeId = mutableMapOf<String, BikeFile>()

    override suspend fun startNewRideSession() {
        val activeBikeId = loadActiveBikeId()
        if (activeBikeId == null) {
            logger.debug("Ignoring ride session start because no active bike is selected")
            return
        }

        val bikeFile = bikeRepository.getBikeFile(activeBikeId)
        if (bikeFile == null) {
            logger.warn("Ignoring ride session start because active bike file is missing: $activeBikeId")
            clearBikeCache(activeBikeId)
            return
        }

        if (bikeFile.rideCursor != RideCursor()) {
            val reset = bikeFile.copy(rideCursor = RideCursor())
            bikeRepository.saveBikeFile(reset)
            cachedBikeFilesByBikeId[activeBikeId] = reset
            persistedBikeFilesByBikeId[activeBikeId] = reset
            logger.debug("Started new ride session for $activeBikeId")
        } else {
            cachedBikeFilesByBikeId[activeBikeId] = bikeFile
            persistedBikeFilesByBikeId[activeBikeId] = bikeFile
        }
    }

    override suspend fun processRideMetric(
        metricValue: Int,
        recordedAt: Long,
    ): RideProcessingResult {
        val activeBikeId = loadActiveBikeId()
        if (activeBikeId == null) {
            logger.debug("Ignoring ride metric because no active bike is selected")
            return RideProcessingResult.NoActiveBike
        }

        val bikeFile = loadBikeFile(activeBikeId)
        if (bikeFile == null) {
            logger.warn("Ignoring ride metric because active bike file is missing: $activeBikeId")
            clearBikeCache(activeBikeId)
            return RideProcessingResult.ActiveBikeMissing
        }

        return try {
            val deltaSinceLastSeen =
                bikePartsService.deriveRideDelta(
                    lastAccepted = bikeFile.rideCursor.lastAcceptedMetricValue,
                    incoming = metricValue,
                )
            if (deltaSinceLastSeen == 0) {
                return RideProcessingResult.IgnoredDuplicate(bikeFile)
            }

            val outcome =
                bikePartsService.applyRideUpdate(
                    bikeFile = bikeFile,
                    metricValue = metricValue,
                    recordedAt = recordedAt,
                )
            val updated = outcome.bikeFile
            cachedBikeFilesByBikeId[activeBikeId] = updated

            val distanceSincePersisted =
                bikePartsService.deriveRideDelta(
                    lastAccepted = persistedBikeFilesByBikeId[activeBikeId]?.rideCursor?.lastAcceptedMetricValue,
                    incoming = metricValue,
                )
            if (distanceSincePersisted < PERSISTENCE_DISTANCE_METERS && outcome.alerts.isEmpty()) {
                return RideProcessingResult.Deferred(updated)
            }

            bikeRepository.saveBikeFile(updated)
            persistedBikeFilesByBikeId[activeBikeId] = updated
            emitAlerts(updated, outcome.alerts)
            RideProcessingResult.Applied(updated)
        } catch (error: RepositoryError.Validation) {
            logger.warn("Rejecting invalid ride metric update", error)
            RideProcessingResult.RejectedInvalid(error.message ?: "Invalid ride metric")
        }
    }

    override suspend fun flushPendingRideMetrics() {
        cachedBikeFilesByBikeId.toMap().forEach { (bikeId, cachedBikeFile) ->
            val persistedBikeFile = persistedBikeFilesByBikeId[bikeId]
            if (persistedBikeFile == null || cachedBikeFile == persistedBikeFile) {
                return@forEach
            }

            bikeRepository.saveBikeFile(cachedBikeFile)
            persistedBikeFilesByBikeId[bikeId] = cachedBikeFile
        }
    }

    private fun emitAlerts(
        bikeFile: BikeFile,
        alerts: List<BikePartsService.PartAlert>,
    ) {
        alerts.forEach { alert ->
            partAlertNotifier.showAlert(
                bikeName = bikeFile.bike.name,
                partName = alert.partName,
                alertText = alert.alertText,
                thresholdMeters = alert.thresholdMeters,
                currentMileageMeters = alert.currentMileageMeters,
            )
        }
    }

    private suspend fun loadBikeFile(bikeId: String): BikeFile? {
        cachedBikeFilesByBikeId[bikeId]?.let { return it }

        val bikeFile = bikeRepository.getBikeFile(bikeId) ?: return null
        cachedBikeFilesByBikeId[bikeId] = bikeFile
        persistedBikeFilesByBikeId[bikeId] = bikeFile
        return bikeFile
    }

    private suspend fun loadActiveBikeId(): String? {
        cachedActiveBikeId?.let { return it }

        val activeBikeId = metadataRepository.read().activeBikeId
        cachedActiveBikeId = activeBikeId
        return activeBikeId
    }

    private fun clearBikeCache(bikeId: String) {
        if (cachedActiveBikeId == bikeId) {
            cachedActiveBikeId = null
        }
        cachedBikeFilesByBikeId.remove(bikeId)
        persistedBikeFilesByBikeId.remove(bikeId)
    }

    companion object {
        private const val PERSISTENCE_DISTANCE_METERS: Int = 100
    }
}
