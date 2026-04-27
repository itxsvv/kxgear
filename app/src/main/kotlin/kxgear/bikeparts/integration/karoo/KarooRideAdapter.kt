package kxgear.bikeparts.integration.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kxgear.bikeparts.integration.logging.BikePartsLogger

enum class RideRecordingState {
    IDLE,
    RECORDING,
    PAUSED,
}

data class RideMetricReading(
    val metricValue: Int,
    val receivedAt: Long,
)

interface RideMetricStreamAdapter {
    fun connect(onConnection: ((Boolean) -> Unit)? = null)

    fun disconnect()

    fun startRideDistanceStream(
        onMetricUpdate: (RideMetricReading) -> Unit,
        onStreamComplete: () -> Unit = {},
    ): String

    fun startRideStateStream(
        onRideStateUpdate: (RideRecordingState) -> Unit,
        onStreamComplete: () -> Unit = {},
    ): String

    fun stopRideDistanceStream(consumerId: String)

    fun stopRideStateStream(consumerId: String)
}

class KarooRideAdapter(
    private val karooSystem: KarooSystemService,
    private val logger: BikePartsLogger,
    private val now: () -> Long = System::currentTimeMillis,
) : RideMetricStreamAdapter {
    override fun connect(onConnection: ((Boolean) -> Unit)?) {
        karooSystem.connect(onConnection)
    }

    override fun disconnect() {
        karooSystem.disconnect()
    }

    override fun startRideDistanceStream(
        onMetricUpdate: (RideMetricReading) -> Unit,
        onStreamComplete: () -> Unit,
    ): String =
        karooSystem.addConsumer<OnStreamState>(
            params = OnStreamState.StartStreaming(RIDE_DISTANCE_DATA_TYPE_ID),
            onError = { message -> logger.warn("Ride distance stream error: $message") },
            onComplete = {
                logger.debug("Ride distance stream completed")
                onStreamComplete()
            },
        ) { event ->
            extractRideDistanceReading(event, now())?.let(onMetricUpdate)
        }

    override fun startRideStateStream(
        onRideStateUpdate: (RideRecordingState) -> Unit,
        onStreamComplete: () -> Unit,
    ): String =
        karooSystem.addConsumer<RideState>(
            onError = { message -> logger.warn("Ride state stream error: $message") },
            onComplete = {
                logger.debug("Ride state stream completed")
                onStreamComplete()
            },
        ) { event ->
            onRideStateUpdate(event.toRideRecordingState())
        }

    override fun stopRideDistanceStream(consumerId: String) {
        karooSystem.removeConsumer(consumerId)
    }

    override fun stopRideStateStream(consumerId: String) {
        karooSystem.removeConsumer(consumerId)
    }

    companion object {
        const val RIDE_DISTANCE_DATA_TYPE_ID: String = DataType.Type.DISTANCE

        internal fun extractRideDistanceReading(
            event: OnStreamState,
            receivedAt: Long,
        ): RideMetricReading? {
            val streamState = event.state as? StreamState.Streaming ?: return null
            val dataPoint = streamState.dataPoint
            if (dataPoint.dataTypeId != RIDE_DISTANCE_DATA_TYPE_ID) {
                return null
            }

            val value = dataPoint.singleValue ?: return null
            if (value < 0) {
                return null
            }

            return RideMetricReading(
                metricValue = value.toInt(),
                receivedAt = receivedAt,
            )
        }
    }
}

private fun RideState.toRideRecordingState(): RideRecordingState =
    when (this) {
        RideState.Idle -> RideRecordingState.IDLE
        RideState.Recording -> RideRecordingState.RECORDING
        is RideState.Paused -> RideRecordingState.PAUSED
    }
