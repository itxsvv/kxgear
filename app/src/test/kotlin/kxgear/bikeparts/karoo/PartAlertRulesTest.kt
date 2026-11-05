package kxgear.bikeparts.karoo

import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.InRideAlert
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
class PartAlertRulesTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var kx: KxgearHarness

    @Before
    fun setUp() {
        kx = KxgearHarness(karoo)
    }

    private fun startRecording() {
        kx.startService()
        kx.rideState(RideState.Recording, "IDLE to RECORDING")
    }

    @Test
    fun `a part with its own alert text shows that text`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        kx.setAlert(bikeId, "Chain", alertKm = 1, alertText = "Lube the chain")
        startRecording()

        kx.distance(1_200.0)
        val alert = karoo.awaitEffect<InRideAlert> { it.title == "Chain" }

        assertEquals("Lube the chain", alert.detail)
        assertEquals(0, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 1_200 }.curAlertMileage)
    }

    @Test
    fun `a blank alert text falls back to the default wording`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        kx.setAlert(bikeId, "Chain", alertKm = 1, alertText = "   ")
        startRecording()

        kx.distance(1_200.0)
        val alert = karoo.awaitEffect<InRideAlert> { it.title == "Chain" }

        assertEquals("Chain reached 1.0km on Road", alert.detail)
    }

    @Test
    fun `two parts crossing their threshold in one reading each alert`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1, "Cassette" to 1)
        startRecording()

        kx.distance(1_200.0)

        val titles = mutableSetOf<String>()
        repeat(2) { titles += karoo.awaitEffect<InRideAlert> { it.title !in titles }.title }
        assertEquals(setOf("Chain", "Cassette"), titles)
        val chain = kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 1_200 }
        assertEquals(0, chain.curAlertMileage)
        assertEquals(0, kx.part(bikeId, "Cassette").curAlertMileage)
    }

    @Test
    fun `a part with no threshold counts but never alerts`() {
        val bikeId = kx.seedBike("Road", "Chain" to null)
        startRecording()

        kx.distance(5_000.0)
        val chain = kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 5_000 }

        assertEquals(0, chain.targetAlertMileage)
        assertEquals(0, chain.curAlertMileage)
        karoo.assertNoEffect<InRideAlert>(forMs = 300)
    }

    @Test
    fun `a part that lands exactly on its threshold alerts`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1)
        startRecording()

        kx.distance(1_000.0)

        karoo.awaitEffect<InRideAlert> { it.title == "Chain" }
        assertEquals(0, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 1_000 }.curAlertMileage)
    }

    @Test
    fun `one long jump alerts once and restarts the counter`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1)
        startRecording()
        val mark = karoo.system.effects.size

        kx.distance(5_000.0)

        val alert = karoo.awaitEffect<InRideAlert>(after = mark) { it.title == "Chain" }
        assertEquals("Chain reached 1.0km on Road", alert.detail)
        karoo.assertNoEffect<InRideAlert>(forMs = 300, after = mark + 1)
        assertEquals(0, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 5_000 }.curAlertMileage)
    }

    @Test
    fun `parts with different thresholds alert independently`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1, "Cassette" to 3)
        startRecording()

        kx.distance(1_200.0)
        karoo.awaitEffect<InRideAlert> { it.title == "Chain" }
        karoo.assertNoEffect<InRideAlert>(forMs = 300) { it.title == "Cassette" }

        kx.distance(3_500.0)

        val cassette = karoo.awaitEffect<InRideAlert> { it.title == "Cassette" }
        assertEquals("Cassette reached 3.0km on Road", cassette.detail)
        assertEquals(0, kx.awaitPart(bikeId, "Cassette") { it.curAlertMileage == 0 && it.riddenMileage == 3_500 }.curAlertMileage)
    }

    @Test
    fun `an alert persists the reading even when the jump is below the write threshold`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1)
        startRecording()
        kx.distance(950.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 950 }

        kx.distance(1_010.0)

        karoo.awaitEffect<InRideAlert> { it.title == "Chain" }
        assertEquals(1_010, kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 1_010 }.riddenMileage)
        assertEquals(1_010, kx.bikeFile(bikeId).rideCursor.lastAcceptedMetricValue)
    }

    @Test
    fun `an archived part neither counts nor alerts`() {
        val bikeId = kx.seedBike("Road", "Chain" to null, "Cassette" to 1)
        kx.archivePart(bikeId, "Cassette")
        startRecording()

        kx.distance(2_500.0)
        kx.awaitPart(bikeId, "Chain") { it.riddenMileage == 2_500 }

        assertEquals(0, kx.part(bikeId, "Cassette").riddenMileage)
        karoo.assertNoEffect<InRideAlert>(forMs = 300)
    }

    @Test
    fun `an alert on a bike that is not active never fires`() {
        val roadId = kx.seedBike("Road", "Chain" to 1)
        val gravelId = kx.seedBike("Gravel", "Chain" to null)
        kx.selectBike(gravelId)
        startRecording()

        kx.distance(2_000.0)
        kx.awaitPart(gravelId, "Chain") { it.riddenMileage == 2_000 }

        assertEquals(0, kx.part(roadId, "Chain").riddenMileage)
        karoo.assertNoEffect<InRideAlert>(forMs = 300)
    }
}
