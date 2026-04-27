package kxgear.bikeparts.integration.karoo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kxgear.bikeparts.domain.service.RideMetricProcessor
import kxgear.bikeparts.domain.service.RideProcessingResult
import kxgear.bikeparts.integration.logging.BikePartsLogger

class RideUpdateController(
    private val adapter: RideMetricStreamAdapter,
    private val rideProcessingService: RideMetricProcessor,
    private val logger: BikePartsLogger,
    private val scope: CoroutineScope,
    private val rideStateSink: RideStateSink = RideStateStore,
) {
    private var distanceConsumerId: String? = null
    private var rideStateConsumerId: String? = null
    private var rideState: RideRecordingState = RideRecordingState.IDLE

    fun start() {
        if (distanceConsumerId != null || rideStateConsumerId != null) {
            return
        }
        adapter.connect()
        rideStateConsumerId =
            adapter.startRideStateStream(
                onRideStateUpdate = { nextState ->
                    scope.launch {
                        handleRideState(nextState)
                    }
                },
                onStreamComplete = {
                    scope.launch {
                        rideProcessingService.flushPendingRideMetrics()
                    }
                },
            )
        distanceConsumerId =
            adapter.startRideDistanceStream(
                onMetricUpdate = { reading ->
                    if (rideState != RideRecordingState.RECORDING) {
                        logger.debug("Ride metric ignored because ride state is $rideState")
                        return@startRideDistanceStream
                    }
                    scope.launch {
                        when (val result = rideProcessingService.processRideMetric(reading.metricValue, reading.receivedAt)) {
                            RideProcessingResult.NoActiveBike -> logger.debug("Ride metric ignored with no active bike")
                            RideProcessingResult.ActiveBikeMissing -> logger.warn("Ride metric ignored because active bike file was missing")
                            is RideProcessingResult.Applied -> logger.debug("Ride metric applied to ${result.bikeFile.bike.bikeId}")
                            is RideProcessingResult.Deferred -> logger.debug("Ride metric deferred for ${result.bikeFile.bike.bikeId}")
                            is RideProcessingResult.IgnoredDuplicate -> logger.debug("Duplicate ride metric ignored for ${result.bikeFile.bike.bikeId}")
                            is RideProcessingResult.RejectedInvalid -> logger.warn("Ride metric rejected: ${result.reason}")
                        }
                    }
                },
                onStreamComplete = {
                    scope.launch {
                        rideProcessingService.flushPendingRideMetrics()
                    }
                },
            )
    }

    fun stop() {
        distanceConsumerId?.let(adapter::stopRideDistanceStream)
        rideStateConsumerId?.let(adapter::stopRideStateStream)
        runBlocking {
            rideProcessingService.flushPendingRideMetrics()
        }
        distanceConsumerId = null
        rideStateConsumerId = null
        rideState = RideRecordingState.IDLE
        rideStateSink.update(RideRecordingState.IDLE)
        adapter.disconnect()
    }

    private suspend fun handleRideState(nextState: RideRecordingState) {
        val previousState = rideState
        if (previousState == nextState) {
            rideStateSink.update(nextState)
            return
        }

        when {
            previousState == RideRecordingState.IDLE && nextState == RideRecordingState.RECORDING -> {
                rideStateSink.update(nextState)
                rideProcessingService.startNewRideSession()
            }

            previousState == RideRecordingState.RECORDING && nextState != RideRecordingState.RECORDING -> {
                rideProcessingService.flushPendingRideMetrics()
                rideStateSink.update(nextState)
            }

            else -> {
                rideStateSink.update(nextState)
            }
        }
        rideState = nextState
        logger.debug("Ride state changed from $previousState to $nextState")
    }
}
