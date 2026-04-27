package kxgear.bikeparts.integration.karoo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kxgear.bikeparts.domain.service.RideMetricProcessor
import kxgear.bikeparts.domain.service.RideProcessingResult
import kxgear.bikeparts.integration.logging.BikePartsLogger
import org.junit.Assert.assertEquals
import org.junit.Test

class RideUpdateControllerTest {
    @Test
    fun ignoresDistanceUntilRideIsRecording() =
        runBlocking {
            val adapter = FakeRideMetricStreamAdapter()
            val processor = FakeRideMetricProcessor()
            RideUpdateController(
                adapter = adapter,
                rideProcessingService = processor,
                logger = BikePartsLogger(),
                scope = CoroutineScope(Dispatchers.Unconfined),
            ).start()

            adapter.emitDistance(10)
            adapter.emitRideState(RideRecordingState.RECORDING)
            adapter.emitDistance(25)

            assertEquals(1, processor.startNewRideSessionCount)
            assertEquals(listOf(25), processor.processedMetrics)
        }

    @Test
    fun flushesPendingMetricsWhenRecordingStops() =
        runBlocking {
            val adapter = FakeRideMetricStreamAdapter()
            val processor = FakeRideMetricProcessor()
            RideUpdateController(
                adapter = adapter,
                rideProcessingService = processor,
                logger = BikePartsLogger(),
                scope = CoroutineScope(Dispatchers.Unconfined),
            ).start()

            adapter.emitRideState(RideRecordingState.RECORDING)
            adapter.emitRideState(RideRecordingState.IDLE)

            assertEquals(1, processor.startNewRideSessionCount)
            assertEquals(1, processor.flushCount)
        }

    @Test
    fun publishesRideStateUpdatesForUiGate() =
        runBlocking {
            val adapter = FakeRideMetricStreamAdapter()
            val events = mutableListOf<String>()
            val processor = FakeRideMetricProcessor(events)
            val rideStateSink = FakeRideStateSink(events)
            RideUpdateController(
                adapter = adapter,
                rideProcessingService = processor,
                logger = BikePartsLogger(),
                scope = CoroutineScope(Dispatchers.Unconfined),
                rideStateSink = rideStateSink,
            ).start()

            adapter.emitRideState(RideRecordingState.RECORDING)
            adapter.emitRideState(RideRecordingState.PAUSED)
            adapter.emitRideState(RideRecordingState.IDLE)

            assertEquals(
                listOf(
                    "state:RECORDING",
                    "flush",
                    "state:PAUSED",
                    "state:IDLE",
                ),
                events,
            )
            assertEquals(
                listOf(
                    RideRecordingState.RECORDING,
                    RideRecordingState.PAUSED,
                    RideRecordingState.IDLE,
                ),
                rideStateSink.states,
            )
        }

    private class FakeRideMetricStreamAdapter : RideMetricStreamAdapter {
        private var distanceHandler: ((RideMetricReading) -> Unit)? = null
        private var rideStateHandler: ((RideRecordingState) -> Unit)? = null

        override fun connect(onConnection: ((Boolean) -> Unit)?) {
            onConnection?.invoke(true)
        }

        override fun disconnect() = Unit

        override fun startRideDistanceStream(
            onMetricUpdate: (RideMetricReading) -> Unit,
            onStreamComplete: () -> Unit,
        ): String {
            distanceHandler = onMetricUpdate
            return "distance"
        }

        override fun startRideStateStream(
            onRideStateUpdate: (RideRecordingState) -> Unit,
            onStreamComplete: () -> Unit,
        ): String {
            rideStateHandler = onRideStateUpdate
            return "state"
        }

        override fun stopRideDistanceStream(consumerId: String) = Unit

        override fun stopRideStateStream(consumerId: String) = Unit

        fun emitDistance(distanceMeters: Int) {
            distanceHandler?.invoke(RideMetricReading(metricValue = distanceMeters, receivedAt = 1000L))
        }

        fun emitRideState(state: RideRecordingState) {
            rideStateHandler?.invoke(state)
        }
    }

    private class FakeRideMetricProcessor(
        private val events: MutableList<String> = mutableListOf(),
    ) : RideMetricProcessor {
        val processedMetrics = mutableListOf<Int>()
        var startNewRideSessionCount = 0
            private set
        var flushCount = 0
            private set

        override suspend fun processRideMetric(
            metricValue: Int,
            recordedAt: Long,
        ): RideProcessingResult {
            processedMetrics += metricValue
            return RideProcessingResult.NoActiveBike
        }

        override suspend fun startNewRideSession() {
            startNewRideSessionCount += 1
        }

        override suspend fun flushPendingRideMetrics() {
            flushCount += 1
            events += "flush"
        }
    }

    private class FakeRideStateSink(
        private val events: MutableList<String> = mutableListOf(),
    ) : RideStateSink {
        val states = mutableListOf<RideRecordingState>()

        override fun update(state: RideRecordingState) {
            states += state
            events += "state:$state"
        }
    }
}
