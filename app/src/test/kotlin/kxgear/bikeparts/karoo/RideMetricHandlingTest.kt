package kxgear.bikeparts.karoo

import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RideMetricHandlingTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var kx: KxgearHarness

    @Before
    fun setUp() {
        kx = KxgearHarness(karoo)
    }

    private fun recording(bikeId: String, partName: String = "Chain") {
        kx.startService()
        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.awaitPart(bikeId, partName) { true }
    }

    @Test
    fun `a distance reading that goes backwards is rejected and the mileage stays`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        recording(bikeId)
        kx.distance(500.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 500 }

        kx.distance(300.0)
        kx.awaitLog("Ride metric rejected")

        assertEquals(500, kx.part(bikeId, "Chain").riddenMileage)
        assertEquals(1, kx.logCount("Ride metric rejected"))
    }

    @Test
    fun `a repeated reading is ignored as a duplicate`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        recording(bikeId)
        kx.distance(500.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 500 }

        kx.distance(500.0)
        kx.awaitLog("Duplicate ride metric ignored")

        assertEquals(500, kx.part(bikeId, "Chain").riddenMileage)
    }

    @Test
    fun `a negative reading never reaches the bike`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        recording(bikeId)

        kx.distance(-120.0)
        kx.distance(200.0)

        assertEquals(200, kx.awaitPart(bikeId, "Chain") { it.riddenMileage > 0 }.riddenMileage)
        assertEquals(0, kx.logCount("Ride metric rejected"))
    }

    @Test
    fun `an increment below the persistence threshold stays in memory until a later reading`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        recording(bikeId)

        kx.distance(60.0)
        kx.awaitLog("Ride metric deferred")
        assertEquals(0, kx.part(bikeId, "Chain").riddenMileage)

        kx.distance(200.0)

        assertEquals(200, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 200 }.riddenMileage)
        assertEquals(200, kx.bikeFile(bikeId).rideCursor.lastAcceptedMetricValue)
    }

    @Test
    fun `a reading for another data type does not touch the bike`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        recording(bikeId)

        karoo.system.setDataPoint(DataType.Type.POWER, 400.0)
        kx.distance(150.0)

        assertEquals(150, kx.awaitPart(bikeId, "Chain") { it.riddenMileage > 0 }.riddenMileage)
    }

    @Test
    fun `fractional distance is truncated to whole metres`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        recording(bikeId)

        kx.distance(149.9)

        assertEquals(149, kx.awaitPart(bikeId, "Chain") { it.riddenMileage > 0 }.riddenMileage)
        assertEquals(149, kx.bikeFile(bikeId).bike.karooMileageMeters)
    }

    @Test
    fun `only the active bike accumulates distance`() {
        val roadId = kx.seedBike("Road", "Chain" to null)
        val gravelId = kx.seedBike("Gravel", "Chain" to null)
        kx.selectBike(gravelId)

        recording(gravelId)
        kx.distance(400.0)

        assertEquals(400, kx.awaitPart(gravelId, "Chain") { it.riddenMileage == 400 }.riddenMileage)
        assertEquals(0, kx.part(roadId, "Chain").riddenMileage)
        assertEquals(0, kx.bikeFile(roadId).bike.karooMileageMeters)
        assertEquals(400, kx.bikeFile(gravelId).bike.karooMileageMeters)
    }

    @Test
    fun `a distance value larger than the ride is counted in full from the cursor`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        recording(bikeId)

        kx.distance(1_500.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 1_500 }

        kx.distance(1_800.0)

        assertEquals(1_800, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 1_800 }.riddenMileage)
        assertEquals(1_800, kx.bikeFile(bikeId).rideCursor.lastAcceptedMetricValue)
    }
}
