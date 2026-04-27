package kxgear.bikeparts.ui.bikes

import kotlinx.coroutines.runBlocking
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeSummary
import kxgear.bikeparts.domain.service.BikeLifecycleGateway
import kxgear.bikeparts.domain.service.BikeOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BikeListViewModelTest {
    @Test
    fun refreshMapsBikesAndActiveSelection() = runBlocking {
        val gateway =
            FakeBikeLifecycleGateway(
                overview =
                    BikeOverview(
                        bikes = listOf(BikeSummary("bike-1", "Road"), BikeSummary("bike-2", "Gravel")),
                        activeBikeId = "bike-2",
                    ),
            )
        val viewModel = BikeListViewModel(gateway)

        viewModel.refresh()

        assertEquals(2, viewModel.uiState.value.bikes.size)
        assertEquals("bike-2", viewModel.uiState.value.bikes.first { it.isActive }.bikeId)
    }

    @Test
    fun selectBikeDelegatesAndRefreshes() = runBlocking {
        val gateway =
            FakeBikeLifecycleGateway(
                overview = BikeOverview(bikes = listOf(BikeSummary("bike-1", "Road")), activeBikeId = null),
            )
        val viewModel = BikeListViewModel(gateway)
        viewModel.refresh()

        viewModel.selectBike("bike-1")

        assertEquals("bike-1", gateway.selectedBikeId)
        assertTrue(viewModel.uiState.value.bikes.single().isActive)
    }

    @Test
    fun addRenameAndDeleteDelegateToGateway() = runBlocking {
        val gateway =
            FakeBikeLifecycleGateway(
                overview = BikeOverview(bikes = emptyList(), activeBikeId = null),
            )
        val viewModel = BikeListViewModel(gateway)

        viewModel.addBike("Road", 1000)
        viewModel.updateBike("bike-1", "Gravel", 2000)
        viewModel.deleteBike("bike-1")

        assertEquals("Road" to 1000, gateway.addedBike)
        assertEquals(Triple("bike-1", "Gravel", 2000), gateway.updatedBike)
        assertEquals("bike-1", gateway.deletedBikeId)
    }

    private class FakeBikeLifecycleGateway(
        initialBike: Bike? = null,
        overview: BikeOverview,
    ) : BikeLifecycleGateway {
        private var bikes =
            overview.bikes.associate { summary ->
                summary.bikeId to
                    Bike(
                        bikeId = summary.bikeId,
                        name = summary.name,
                        createdAt = 1000L,
                        updatedAt = 1000L,
                    )
            }.toMutableMap()
        private var state = overview
        private val fallbackBike = initialBike

        var selectedBikeId: String? = null
            private set
        var addedBike: Pair<String, Int>? = null
            private set
        var updatedBike: Triple<String, String, Int>? = null
            private set
        var deletedBikeId: String? = null
            private set

        override suspend fun loadOverview(): BikeOverview = state

        override suspend fun getBike(bikeId: String): Bike? = bikes[bikeId] ?: fallbackBike

        override suspend fun addBike(
            name: String,
            mileageMeters: Int,
        ): BikeOverview {
            addedBike = name to mileageMeters
            val bike =
                Bike(
                    bikeId = "bike-1",
                    name = name,
                    karooMileageMeters = mileageMeters,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                )
            bikes[bike.bikeId] = bike
            state = BikeOverview(bikes = listOf(BikeSummary(bike.bikeId, bike.name, bike.karooMileageMeters)), activeBikeId = bike.bikeId)
            return state
        }

        override suspend fun updateBike(
            bikeId: String,
            name: String,
            mileageMeters: Int,
        ): BikeOverview {
            updatedBike = Triple(bikeId, name, mileageMeters)
            bikes[bikeId] = checkNotNull(bikes[bikeId]).copy(name = name, karooMileageMeters = mileageMeters)
            state = state.copy(bikes = state.bikes.map { if (it.bikeId == bikeId) it.copy(name = name, mileageMeters = mileageMeters) else it })
            return state
        }

        override suspend fun deleteBike(bikeId: String): BikeOverview {
            deletedBikeId = bikeId
            bikes.remove(bikeId)
            state = state.copy(bikes = state.bikes.filterNot { it.bikeId == bikeId }, activeBikeId = null)
            return state
        }

        override suspend fun selectActiveBike(bikeId: String): BikeOverview {
            selectedBikeId = bikeId
            state = state.copy(activeBikeId = bikeId)
            return state
        }
    }
}
