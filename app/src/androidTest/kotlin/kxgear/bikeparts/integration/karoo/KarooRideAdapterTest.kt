package kxgear.bikeparts.integration.karoo

import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.service.RideMetricProcessor
import kxgear.bikeparts.domain.service.RideProcessingResult
import kxgear.bikeparts.integration.logging.BikePartsLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KarooRideAdapterTest {
    @Test
    fun extractRideDistanceReadingMapsStreamingDistance() {
        val event =
            OnStreamState(
                StreamState.Streaming(
                    dataPoint =
                        DataPoint(
                            dataTypeId = DataType.Type.DISTANCE,
                            values = mapOf(DataType.Field.DISTANCE to 125.0),
                        ),
                ),
            )

        val reading = KarooRideAdapter.extractRideDistanceReading(event, receivedAt = 5000L)

        requireNotNull(reading)
        assertEquals(125, reading.metricValue)
        assertEquals(5000L, reading.receivedAt)
    }

    @Test
    fun extractRideDistanceReadingIgnoresOtherStreamTypes() {
        val event =
            OnStreamState(
                StreamState.Streaming(
                    dataPoint =
                        DataPoint(
                            dataTypeId = DataType.Type.RIDE_TIME,
                            values = mapOf(DataType.Field.RIDE_TIME to 42.0),
                        ),
                ),
            )

        val reading = KarooRideAdapter.extractRideDistanceReading(event, receivedAt = 5000L)

        assertNull(reading)
    }

    @Test
    fun rideUpdateControllerForwardsMetricsEvenWhenNoBikeIsActive() {
        val adapter = FakeRideMetricStreamAdapter()
        val processor = FakeRideMetricProcessor(RideProcessingResult.NoActiveBike)
        val controller =
            RideUpdateController(
                adapter = adapter,
                rideProcessingService = processor,
                logger = BikePartsLogger(),
                scope = CoroutineScope(Dispatchers.Unconfined),
            )

        controller.start()
        adapter.emit(metricValue = 200, receivedAt = 9000L)

        assertEquals(listOf(200 to 9000L), processor.requests)
        controller.stop()
    }

    private class FakeRideMetricStreamAdapter : RideMetricStreamAdapter {
        private var onMetricUpdate: ((RideMetricReading) -> Unit)? = null

        override fun connect(onConnection: ((Boolean) -> Unit)?) {
            onConnection?.invoke(true)
        }

        override fun disconnect() = Unit

        override fun startRideDistanceStream(onMetricUpdate: (RideMetricReading) -> Unit): String {
            this.onMetricUpdate = onMetricUpdate
            return "consumer-1"
        }

        override fun stopRideDistanceStream(consumerId: String) = Unit

        fun emit(
            metricValue: Int,
            receivedAt: Long,
        ) {
            onMetricUpdate?.invoke(RideMetricReading(metricValue, receivedAt))
        }
    }

    private class FakeRideMetricProcessor(
        private val result: RideProcessingResult,
    ) : RideMetricProcessor {
        val requests = mutableListOf<Pair<Int, Long>>()

        override suspend fun processRideMetric(
            metricValue: Int,
            recordedAt: Long,
        ): RideProcessingResult {
            requests += metricValue to recordedAt
            return result
        }
    }
}
