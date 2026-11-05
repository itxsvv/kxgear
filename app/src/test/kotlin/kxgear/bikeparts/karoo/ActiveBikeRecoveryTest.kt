package kxgear.bikeparts.karoo

import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.RideState
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
class ActiveBikeRecoveryTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var kx: KxgearHarness

    @Before
    fun setUp() {
        kx = KxgearHarness(karoo)
    }

    @Test
    fun `distance is ignored with no active bike and counted once one exists`() {
        kx.startService()
        kx.rideState(RideState.Recording, "IDLE to RECORDING")

        kx.distance(300.0)
        kx.awaitLog("no active bike is selected")
        assertNull(kx.metadata().activeBikeId)

        val bikeId = kx.seedBike("Road", "Chain" to null)
        kx.distance(500.0)

        assertEquals(500, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 500 }.riddenMileage)
    }

    @Test
    fun `a missing active bike file is reported and recovers when the file returns`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        val storedFile = kx.bikeFile(bikeId)
        kx.startService()
        kx.deleteBikeFile(bikeId)

        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.awaitLog("Ignoring ride session start because active bike file is missing")
        kx.distance(300.0)
        kx.awaitLog("Ignoring ride metric because active bike file is missing")

        kx.saveBikeFile(storedFile)
        kx.distance(400.0)

        assertEquals(400, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 400 }.riddenMileage)
    }

    @Test
    fun `the active bike is remembered after it is chosen before the extension starts`() {
        val roadId = kx.seedBike("Road", "Chain" to null)
        val gravelId = kx.seedBike("Gravel", "Chain" to null)
        kx.selectBike(gravelId)

        kx.startService()
        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(700.0)

        assertEquals(700, kx.awaitPart(gravelId, "Chain") { it.riddenMileage == 700 }.riddenMileage)
        assertEquals(0, kx.part(roadId, "Chain").riddenMileage)
    }

    @Test
    fun `a riding session started with no active bike does not cache one`() {
        kx.startService()
        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.awaitLog("Ignoring ride session start because no active bike is selected")

        val bikeId = kx.seedBike("Road", "Chain" to null)
        kx.rideState(RideState.Idle, "RECORDING to IDLE")
        kx.rideState(RideState.Recording, "IDLE to RECORDING")
        kx.distance(250.0)

        assertEquals(250, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 250 }.riddenMileage)
    }
}
