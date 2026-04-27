package kxgear.bikeparts.integration.karoo

import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KarooRideAdapterTest {
    @Test
    fun extractRideDistanceReadingMapsStreamingDistance() {
        val event = OnStreamState(
            StreamState.Streaming(
                dataPoint = DataPoint(
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
        val event = OnStreamState(
            StreamState.Streaming(
                dataPoint = DataPoint(
                    dataTypeId = DataType.Type.RIDE_TIME,
                    values = mapOf(DataType.Field.RIDE_TIME to 42.0),
                ),
            ),
        )

        val reading = KarooRideAdapter.extractRideDistanceReading(event, receivedAt = 5000L)

        assertNull(reading)
    }

    @Test
    fun extractRideDistanceReadingIgnoresNonStreamingStates() {
        val event = OnStreamState(StreamState.Idle)

        val reading = KarooRideAdapter.extractRideDistanceReading(event, receivedAt = 5000L)

        assertNull(reading)
    }
}
