package kxgear.bikeparts.ui.parts

import kotlinx.coroutines.runBlocking
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.model.RideCursor
import kxgear.bikeparts.domain.service.BikeDetails
import kxgear.bikeparts.domain.service.PartLifecycleGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartReplacementViewModelTest {
    @Test
    fun startLoadsExistingPartContext() = runBlocking {
        val gateway = FakePartLifecycleGateway()
        val viewModel = PartReplacementViewModel(gateway)

        viewModel.start("bike-1", "part-1")

        assertEquals("Chain", viewModel.uiState.value.oldPartName)
        assertEquals(18, viewModel.uiState.value.oldPartMileage)
        assertEquals("0.0", viewModel.uiState.value.riddenMileageInput)
    }

    @Test
    fun submitConvertsKilometerInputToMeters() = runBlocking {
        val gateway = FakePartLifecycleGateway()
        val viewModel = PartReplacementViewModel(gateway)
        viewModel.start("bike-1", "part-1")
        viewModel.updateRiddenMileage("12.5")

        val success = viewModel.submit()

        assertTrue(success)
        assertEquals(12500, gateway.lastReplacementMileage)
    }

    @Test
    fun submitReplacesPartWithDefaultMileageZero() = runBlocking {
        val gateway = FakePartLifecycleGateway()
        val viewModel = PartReplacementViewModel(gateway)
        viewModel.start("bike-1", "part-1")

        val success = viewModel.submit()

        assertTrue(success)
        assertEquals("Chain", gateway.lastReplacementName)
        assertEquals(0, gateway.lastReplacementMileage)
    }

    private class FakePartLifecycleGateway : PartLifecycleGateway {
        private var details =
            BikeDetails(
                bike =
                    Bike(
                        bikeId = "bike-1",
                        name = "Road",
                        createdAt = 1000L,
                        updatedAt = 1000L,
                    ),
                installedParts =
                    listOf(
                        Part(
                            partId = "part-1",
                            name = "Chain",
                            riddenMileage = 18,
                            status = PartStatus.INSTALLED,
                            createdAt = 1000L,
                            updatedAt = 1000L,
                        ),
                    ),
                archivedParts = emptyList(),
                rideCursor = RideCursor(),
            )

        var lastReplacementName: String? = null
            private set
        var lastReplacementMileage: Int? = null
            private set

        override suspend fun loadBikeDetails(bikeId: String): BikeDetails = details

        override suspend fun getPart(
            bikeId: String,
            partId: String,
        ): Part? = (details.installedParts + details.archivedParts).firstOrNull { it.partId == partId }

        override suspend fun addPart(
            bikeId: String,
            name: String,
            riddenMileage: Int,
        ): BikeDetails = details

        override suspend fun updatePart(
            bikeId: String,
            partId: String,
            name: String,
            riddenMileage: Int,
            targetAlertMileage: Int?,
            alertText: String?,
        ): BikeDetails = details

        override suspend fun archivePart(
            bikeId: String,
            partId: String,
        ): BikeDetails = details

        override suspend fun deletePart(
            bikeId: String,
            partId: String,
        ): BikeDetails = details

        override suspend fun replacePart(
            bikeId: String,
            oldPartId: String,
            name: String,
            riddenMileage: Int,
        ): BikeDetails {
            lastReplacementName = name
            lastReplacementMileage = riddenMileage
            val oldPart = details.installedParts.first { it.partId == oldPartId }
            details =
                details.copy(
                    installedParts =
                        details.installedParts.filterNot { it.partId == oldPartId } +
                            oldPart.copy(
                                partId = "part-2",
                                name = name,
                                riddenMileage = riddenMileage,
                                status = PartStatus.INSTALLED,
                            ),
                    archivedParts = details.archivedParts + oldPart.copy(status = PartStatus.ARCHIVED, archivedAt = 2000L),
                )
            return details
        }
    }
}
