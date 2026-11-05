package kxgear.bikeparts.karoo

import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.RideState
import kxgear.bikeparts.extension.BikePartsExtension
import kxgear.bikeparts.integration.karoo.RideRecordingState
import kxgear.bikeparts.integration.karoo.RideStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RideSessionLifecycleTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var kx: KxgearHarness

    @Before
    fun setUp() {
        kx = KxgearHarness(karoo)
    }

    @Test
    fun `the extension listens to ride distance and ride state while running`() {
        kx.startService()

        assertEquals(2, karoo.system.consumerCount)
        assertTrue(karoo.system.hasStreamConsumer(DataType.Type.DISTANCE))
        assertTrue(karoo.system.hasConsumer(RideState.Params))
    }

    @Test
    fun `a ride already recording when the extension starts counts the next reading`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        karoo.system.setRideState(RideState.Recording)

        kx.startService()
        kx.awaitLog("Ride state changed from IDLE to RECORDING")
        kx.distance(250.0)

        assertEquals(250, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 250 }.riddenMileage)
    }

    @Test
    fun `a ride already paused when the extension starts ignores readings`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1)
        karoo.system.setRideState(RideState.Paused(auto = true))

        kx.startService()
        kx.awaitLog("Ride state changed from IDLE to PAUSED")
        kx.distance(900.0)
        kx.awaitLog("ride state is PAUSED")

        assertEquals(0, kx.part(bikeId, "Chain").riddenMileage)
        karoo.assertNoEffect<InRideAlert>()
    }

    @Test
    fun `a ride state replayed from another thread still opens the session`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        karoo.system.replayAsynchronously = true
        karoo.system.setRideState(RideState.Recording)

        kx.startService()
        kx.awaitLog("Ride state changed from IDLE to RECORDING")
        kx.distance(300.0)

        assertEquals(300, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 300 }.riddenMileage)
    }

    @Test
    fun `the ride state store follows the ride for the ui gate`() {
        kx.seedBike("Road", "Chain" to null)
        kx.startService()

        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        assertEquals(RideRecordingState.RECORDING, RideStateStore.state.value)

        kx.rideState(RideState.Paused(auto = false), "RECORDING to PAUSED")
        assertEquals(RideRecordingState.PAUSED, RideStateStore.state.value)

        kx.rideState(RideState.Idle, "PAUSED to IDLE")
        assertEquals(RideRecordingState.IDLE, RideStateStore.state.value)
    }

    @Test
    fun `a stream completing on the karoo flushes the held back distance`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        val before = kx.consumerIds()
        kx.startService()
        val extensionConsumers = kx.newConsumerIds(before)
        assertEquals(2, extensionConsumers.size)

        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(130.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 130 }
        kx.distance(160.0)
        kx.awaitLog("Ride metric deferred")

        assertTrue(karoo.system.completeConsumer(extensionConsumers.first()))

        assertEquals(160, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 160 }.riddenMileage)
        assertEquals(1, kx.logCount("stream completed"))
    }

    @Test
    fun `restarting the extension re-registers the streams and keeps the stored mileage`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        kx.startService()
        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(130.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 130 }
        kx.distance(160.0)
        kx.awaitLog("Ride metric deferred")

        karoo.restart<BikePartsExtension>()
        karoo.awaitStreamConsumer(DataType.Type.DISTANCE)
        karoo.awaitConsumer(RideState.Params)

        assertEquals(160, kx.part(bikeId, "Chain").riddenMileage)
        assertEquals(2, karoo.system.consumerCount)
    }
}
