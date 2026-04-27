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
    private val pendingMetricsByBikeId = mutableMapOf<String, PendingRideMetric>()

    override suspend fun startNewRideSession() {
        val activeBikeId = metadataRepository.read().activeBikeId
        if (activeBikeId == null) {
            logger.debug("Ignoring ride session start because no active bike is selected")
            return
        }

        pendingMetricsByBikeId.remove(activeBikeId)
        val bikeFile = bikeRepository.getBikeFile(activeBikeId)
        if (bikeFile == null) {
            logger.warn("Ignoring ride session start because active bike file is missing: $activeBikeId")
            return
        }

        if (bikeFile.rideCursor != RideCursor()) {
            bikeRepository.saveBikeFile(bikeFile.copy(rideCursor = RideCursor()))
            logger.debug("Started new ride session for $activeBikeId")
        }
    }

    override suspend fun processRideMetric(
        metricValue: Int,
        recordedAt: Long,
    ): RideProcessingResult {
        val metadata = metadataRepository.read()
        val activeBikeId = metadata.activeBikeId
        if (activeBikeId == null) {
            logger.debug("Ignoring ride metric because no active bike is selected")
            return RideProcessingResult.NoActiveBike
        }

        val bikeFile = bikeRepository.getBikeFile(activeBikeId)
        if (bikeFile == null) {
            logger.warn("Ignoring ride metric because active bike file is missing: $activeBikeId")
            return RideProcessingResult.ActiveBikeMissing
        }

        return try {
            val pendingMetric = pendingMetricsByBikeId[activeBikeId]
            val lastSeenMetricValue = pendingMetric?.metricValue ?: bikeFile.rideCursor.lastAcceptedMetricValue
            val deltaSinceLastSeen =
                bikePartsService.deriveRideDelta(
                    lastAccepted = lastSeenMetricValue,
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

            val distanceSincePersisted =
                bikePartsService.deriveRideDelta(
                    lastAccepted = bikeFile.rideCursor.lastAcceptedMetricValue,
                    incoming = metricValue,
                )
            if (distanceSincePersisted < PERSISTENCE_DISTANCE_METERS && outcome.alerts.isEmpty()) {
                pendingMetricsByBikeId[activeBikeId] = PendingRideMetric(metricValue, recordedAt)
                return RideProcessingResult.Deferred(updated)
            }

            bikeRepository.saveBikeFile(updated)
            pendingMetricsByBikeId.remove(activeBikeId)
            emitAlerts(updated, outcome.alerts)
            RideProcessingResult.Applied(updated)
        } catch (error: RepositoryError.Validation) {
            logger.warn("Rejecting invalid ride metric update", error)
            RideProcessingResult.RejectedInvalid(error.message ?: "Invalid ride metric")
        }
    }

    override suspend fun flushPendingRideMetrics() {
        val pendingMetrics = pendingMetricsByBikeId.toMap()
        pendingMetricsByBikeId.clear()

        pendingMetrics.forEach { (bikeId, pendingMetric) ->
            val bikeFile = bikeRepository.getBikeFile(bikeId)
            if (bikeFile == null) {
                logger.warn("Dropping pending ride metric because bike file is missing: $bikeId")
                return@forEach
            }

            try {
                val outcome =
                    bikePartsService.applyRideUpdate(
                        bikeFile = bikeFile,
                        metricValue = pendingMetric.metricValue,
                        recordedAt = pendingMetric.recordedAt,
                    )
                val updated = outcome.bikeFile
                if (updated != bikeFile) {
                    bikeRepository.saveBikeFile(updated)
                    emitAlerts(updated, outcome.alerts)
                }
            } catch (error: RepositoryError.Validation) {
                logger.warn("Dropping invalid pending ride metric for $bikeId", error)
            }
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

    private data class PendingRideMetric(
        val metricValue: Int,
        val recordedAt: Long,
    )

    companion object {
        private const val PERSISTENCE_DISTANCE_METERS: Int = 100
    }
}
