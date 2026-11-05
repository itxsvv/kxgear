package kxgear.bikeparts.karoo

import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.runBlocking
import kxgear.bikeparts.extension.BikePartsExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RideAccumulationTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var kx: KxgearHarness

    @Before
    fun setUp() {
        kx = KxgearHarness(karoo)
    }

    private fun startService() {
        karoo.host<BikePartsExtension>()
        karoo.awaitStreamConsumer(DataType.Type.DISTANCE)
        kx.awaitRideStateConsumer()
    }

    @Test
    fun `distance while recording accumulates on installed parts only`() {
        val bikeId = kx.seedBike("Road", "Chain" to null, "Cassette" to null)
        runBlocking {
            val cassette = kx.part(bikeId, "Cassette")
            kx.container.partLifecycleService.archivePart(bikeId, cassette.partId)
        }
        startService()

        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(150.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 150 }
        kx.distance(400.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 400 }

        assertEquals(0, kx.part(bikeId, "Cassette").riddenMileage)
        assertEquals(400, kx.bikeFile(bikeId).bike.karooMileageMeters)
        assertEquals(400, kx.bikeFile(bikeId).rideCursor.lastAcceptedMetricValue)
    }

    @Test
    fun `distance before the ride is recording is not counted`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        startService()

        kx.distance(900.0)
        kx.awaitLog("ride state is IDLE")
        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(200.0)

        assertEquals(200, kx.awaitPart(bikeId, "Chain") { it.riddenMileage > 0 }.riddenMileage)
    }

    @Test
    fun `crossing the alert threshold raises one alert with a beep and restarts the count`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1)
        startService()

        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(400.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 400 }
        karoo.assertNoEffect<InRideAlert>(forMs = 100)

        kx.distance(1_200.0)
        val alert = karoo.awaitEffect<InRideAlert> { it.title == "Chain" }
        karoo.awaitEffect<PlayBeepPattern>()

        assertEquals("Chain reached 1.0km on Road", alert.detail)
        val chain = kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 1_200 }
        assertEquals(0, chain.curAlertMileage)
        assertEquals(1, karoo.system.effectsOf<InRideAlert>().size)

        kx.distance(2_300.0)
        karoo.awaitValue { true.takeIf { karoo.system.effectsOf<InRideAlert>().size == 2 } }
    }

    @Test
    fun `a paused ride neither counts distance nor alerts and resumes from the cursor`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1)
        startService()

        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(500.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 500 }

        kx.rideState(RideState.Paused(auto = false), "RECORDING to PAUSED")
        kx.distance(2_000.0)
        kx.awaitLog("ride state is PAUSED")
        karoo.assertNoEffect<InRideAlert>()
        assertEquals(500, kx.part(bikeId, "Chain").riddenMileage)

        kx.rideState(RideState.Recording, "PAUSED to RECORDING")
        kx.distance(1_600.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 1_600 }
        karoo.awaitEffect<InRideAlert> { it.title == "Chain" }
    }

    @Test
    fun `small increments are held back until the ride pauses or ends`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        startService()

        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(130.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 130 }
        kx.distance(150.0)
        kx.awaitLog("Ride metric deferred")
        assertEquals(130, kx.part(bikeId, "Chain").riddenMileage)

        kx.rideState(RideState.Paused(auto = true), "RECORDING to PAUSED")
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 150 }
    }

    @Test
    fun `a second ride starts counting from zero distance and still alerts`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1)
        startService()

        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(400.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 400 }
        kx.rideState(RideState.Idle, "RECORDING to IDLE")
        assertEquals(0, karoo.system.effectsOf<InRideAlert>().size)

        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        assertNull(kx.bikeFile(bikeId).rideCursor.lastAcceptedMetricValue)
        kx.distance(700.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 1_100 }
        karoo.awaitEffect<InRideAlert> { it.title == "Chain" }
    }

    @Test
    fun `destroying the service saves the held back distance`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        startService()

        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(130.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 130 }
        kx.distance(160.0)
        kx.awaitLog("Ride metric deferred")

        karoo.close()

        assertEquals(160, kx.part(bikeId, "Chain").riddenMileage)
    }
}
