package kxgear.bikeparts.karoo

import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.Bikes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.service.BikeOverview
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
class BikeCatalogSyncTest {
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

    private fun localBikes(): List<BikeFile> = runBlocking { kx.container.bikeRepository.listBikeFiles() }

    @Test
    fun `karoo bikes are imported with their odometer and the first becomes active`() {
        karoo.system.setBikes(listOf(Bikes.Bike("k-road", "Road", 1_234_567.6), Bikes.Bike("k-gravel", "Gravel", 800.0)))

        val overview = syncFromKaroo()

        val byName = localBikes().associateBy { it.bike.name }
        assertEquals(setOf("Road", "Gravel"), byName.keys)
        assertEquals("k-road", byName.getValue("Road").bike.karooBikeId)
        assertEquals(1_234_568, byName.getValue("Road").bike.karooMileageMeters)
        assertEquals(byName.getValue("Road").bike.bikeId, overview.activeBikeId)
    }

    @Test
    fun `a bike that differs only by case or spaces links to the local one instead of duplicating`() {
        val localId = kx.seedBike("Road", "Chain" to null)
        runBlocking {
            kx.container.bikeRepository.saveBikeFile(
                BikeFile(bike = Bike("stale", null, "road ", 0, 1, 1), parts = emptyList(), lastUpdatedAt = 1),
            )
        }
        karoo.system.setBikes(listOf(Bikes.Bike("k-road", "Road", 500.0), Bikes.Bike("k-road-2", " ROAD", 900.0)))

        val overview = syncFromKaroo()

        val bikes = localBikes()
        assertEquals(listOf(localId), bikes.map { it.bike.bikeId })
        assertEquals("k-road", bikes.single().bike.karooBikeId)
        assertEquals(localId, overview.activeBikeId)

        syncFromKaroo()
        assertEquals(listOf(localId), localBikes().map { it.bike.bikeId })
    }

    @Test
    fun `a bike removed on the karoo loses its link but keeps its parts`() {
        karoo.system.setBikes(listOf(Bikes.Bike("k-road", "Road", 0.0)))
        syncFromKaroo()
        val roadId = localBikes().single().bike.bikeId
        runBlocking { kx.container.partLifecycleService.addPart(roadId, "Chain", 0) }

        karoo.system.setBikes(listOf(Bikes.Bike("k-gravel", "Gravel", 0.0)))
        syncFromKaroo()

        val byName = localBikes().associateBy { it.bike.name }
        assertNull(byName.getValue("Road").bike.karooBikeId)
        assertEquals(listOf("Chain"), byName.getValue("Road").parts.map { it.name })
        assertEquals("k-gravel", byName.getValue("Gravel").bike.karooBikeId)
    }
}
