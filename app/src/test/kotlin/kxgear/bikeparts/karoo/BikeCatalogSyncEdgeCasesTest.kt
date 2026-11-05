package kxgear.bikeparts.karoo

import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.Bikes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.service.BikeOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BikeCatalogSyncEdgeCasesTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var kx: KxgearHarness

    @Before
    fun setUp() {
        kx = KxgearHarness(karoo)
    }

    private fun syncFromKaroo(): BikeOverview {
        val pending = CoroutineScope(Dispatchers.Default).async {
            kx.container.bikeLifecycleService.loadOverview(syncKarooOnStartup = true)
        }
        return karoo.awaitValue { pending.takeIf { it.isCompleted }?.getCompleted() }
    }

    /** Runs a sync but ends its bike consumer through [terminal] instead of publishing a list. */
    private fun syncWithTerminal(terminal: (String) -> Boolean): BikeOverview {
        val before = kx.consumerIds()
        val pending = CoroutineScope(Dispatchers.Default).async {
            kx.container.bikeLifecycleService.loadOverview(syncKarooOnStartup = true)
        }
        karoo.awaitValue { true.takeIf { karoo.system.hasConsumer(Bikes.Params) } }
        assertTrue(terminal(kx.newConsumerIds(before).single()))
        return karoo.awaitValue { pending.takeIf { it.isCompleted }?.getCompleted() }
    }

    @Test
    fun `two karoo bikes with the same name import the first one only`() {
        karoo.system.setBikes(
            listOf(Bikes.Bike("k-road", "Road", 100.0), Bikes.Bike("k-road-2", " road ", 900.0)),
        )

        syncFromKaroo()

        val bikes = kx.allBikeFiles()
        assertEquals(1, bikes.size)
        assertEquals("Road", bikes.single().bike.name)
        assertEquals("k-road", bikes.single().bike.karooBikeId)
        assertEquals(100, bikes.single().bike.karooMileageMeters)
    }

    @Test
    fun `an empty karoo list is treated as no data and keeps the existing links`() {
        karoo.system.setBikes(listOf(Bikes.Bike("k-road", "Road", 0.0)))
        syncFromKaroo()
        val bikeId = kx.allBikeFiles().single().bike.bikeId

        karoo.system.setBikes(emptyList())
        val overview = syncFromKaroo()

        assertEquals("k-road", kx.bikeFile(bikeId).bike.karooBikeId)
        assertEquals(listOf(bikeId), overview.bikes.map { it.bikeId })
    }

    @Test
    fun `a karoo sync failure leaves the local catalog unchanged and logs a warning`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1)

        val overview = syncWithTerminal { karoo.system.errorConsumer(it, "bike service gone") }

        assertEquals(listOf(bikeId), kx.allBikeFiles().map { it.bike.bikeId })
        assertNull(kx.bikeFile(bikeId).bike.karooBikeId)
        assertEquals(bikeId, overview.activeBikeId)
        assertEquals(
            "Karoo bike list error: bike service gone",
            kx.awaitLog("Karoo bike list error: bike service gone"),
        )
    }

    @Test
    fun `a karoo bike stream that completes without data leaves the catalog unchanged`() {
        val bikeId = kx.seedBike("Road", "Chain" to 1)

        val overview = syncWithTerminal(karoo.system::completeConsumer)

        assertEquals(listOf(bikeId), kx.allBikeFiles().map { it.bike.bikeId })
        assertNull(kx.bikeFile(bikeId).bike.karooBikeId)
        assertEquals(bikeId, overview.activeBikeId)
    }

    @Test
    fun `a sync after a failure imports the bikes normally`() {
        kx.seedBike("Road", "Chain" to 1)
        syncWithTerminal { karoo.system.errorConsumer(it, "transient") }

        karoo.system.setBikes(listOf(Bikes.Bike("k-road", "Road", 2_500.0)))
        syncFromKaroo()

        val bikes = kx.allBikeFiles()
        assertEquals(1, bikes.size)
        assertEquals("k-road", bikes.single().bike.karooBikeId)
    }

    @Test
    fun `the karoo odometer is rounded to whole metres`() {
        karoo.system.setBikes(
            listOf(Bikes.Bike("k-down", "Down", 999.4), Bikes.Bike("k-up", "Up", 1_000.5)),
        )

        syncFromKaroo()

        val byName = kx.allBikeFiles().associateBy { it.bike.name }
        assertEquals(999, byName.getValue("Down").bike.karooMileageMeters)
        assertEquals(1_001, byName.getValue("Up").bike.karooMileageMeters)
    }

    @Test
    fun `a karoo bike that changes its id relinks the local bike instead of duplicating`() {
        karoo.system.setBikes(listOf(Bikes.Bike("k-old", "Road", 0.0)))
        syncFromKaroo()
        val bikeId = kx.allBikeFiles().single().bike.bikeId

        karoo.system.setBikes(listOf(Bikes.Bike("k-new", "ROAD", 0.0)))
        syncFromKaroo()

        assertEquals(listOf(bikeId), kx.allBikeFiles().map { it.bike.bikeId })
        assertEquals("k-new", kx.bikeFile(bikeId).bike.karooBikeId)
    }

    @Test
    fun `an empty duplicate of a named bike is folded in while one holding parts survives`() {
        val activeId = kx.seedBike("Road", "Chain" to null)
        kx.saveBikeFile(bikeFileNamed("empty-dup", "road", createdAt = 5))
        kx.saveBikeFile(bikeFileNamed("parts-dup", "Road", createdAt = 6, parts = kx.bikeFile(activeId).parts))
        karoo.system.setBikes(listOf(Bikes.Bike("k-road", "Road", 0.0)))

        syncFromKaroo()

        val byId = kx.allBikeFiles().associateBy { it.bike.bikeId }
        assertEquals(setOf(activeId, "parts-dup"), byId.keys)
        assertEquals("k-road", byId.getValue(activeId).bike.karooBikeId)
        assertEquals(listOf("Chain"), byId.getValue("parts-dup").parts.map { it.name })
        assertEquals(PartStatus.INSTALLED, byId.getValue("parts-dup").parts.single().status)
    }

    @Test
    fun `an empty duplicate is dropped without touching the bike that holds the active slot`() {
        val roadId = kx.seedBike("Road")
        val gravelId = kx.seedBike("Gravel")
        kx.selectBike(gravelId)
        kx.saveBikeFile(bikeFileNamed("empty-dup", "gravel", createdAt = 9))
        karoo.system.setBikes(listOf(Bikes.Bike("k-gravel", "Gravel", 0.0)))

        val overview = syncFromKaroo()

        val ids = kx.allBikeFiles().map { it.bike.bikeId }
        assertTrue(ids.containsAll(listOf(roadId, gravelId)))
        assertFalse(ids.contains("empty-dup"))
        assertEquals("k-gravel", kx.bikeFile(gravelId).bike.karooBikeId)
        assertEquals(gravelId, overview.activeBikeId)
    }

    private fun bikeFileNamed(
        id: String,
        name: String,
        createdAt: Long,
        parts: List<Part> = emptyList(),
    ): BikeFile =
        BikeFile(
            bike = Bike(bikeId = id, name = name, createdAt = createdAt, updatedAt = createdAt),
            parts = parts,
            lastUpdatedAt = createdAt,
        )
}
