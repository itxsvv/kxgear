package kxgear.bikeparts.ui.parts

import kotlinx.coroutines.runBlocking
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.model.RideCursor
import kxgear.bikeparts.domain.service.BikeDetails
import kxgear.bikeparts.domain.service.PartLifecycleGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartDetailsViewModelTest {
    @Test
    fun loadBikeSeparatesInstalledAndArchivedParts() = runBlocking {
        val gateway =
            FakePartLifecycleGateway(
                details =
                    bikeDetails(
                        installedParts = listOf(part("part-1", "Chain")),
                        archivedParts = listOf(part("part-2", "Old Chain", status = PartStatus.ARCHIVED)),
                    ),
            )
        val viewModel = PartDetailsViewModel(gateway)

        viewModel.loadBike("bike-1")

        assertEquals(1, viewModel.uiState.value.installedParts.size)
        assertEquals(1, viewModel.uiState.value.archivedParts.size)
        assertFalse(viewModel.uiState.value.installedParts.single().isArchived)
        assertTrue(viewModel.uiState.value.archivedParts.single().isArchived)
        assertEquals(1000L, viewModel.uiState.value.installedParts.single().createdDate)
    }

    @Test
    fun loadBikeMapsRideCursorIntoUiState() = runBlocking {
        val gateway =
            FakePartLifecycleGateway(
                details = bikeDetails(rideCursor = RideCursor(lastAcceptedMetricValue = 160, lastAcceptedAt = 5000L)),
            )
        val viewModel = PartDetailsViewModel(gateway)

        viewModel.loadBike("bike-1")

        assertEquals(160, viewModel.uiState.value.lastAcceptedMetricValue)
        assertEquals(5000L, viewModel.uiState.value.lastAcceptedAt)
        assertEquals(2500, viewModel.uiState.value.bikeMileageMeters)
    }

    @Test
    fun confirmDeleteRemovesArchivedPart() = runBlocking {
        val gateway =
            FakePartLifecycleGateway(
                details =
                    bikeDetails(
                        archivedParts = listOf(part("part-2", "Old Chain", status = PartStatus.ARCHIVED)),
                    ),
            )
        val viewModel = PartDetailsViewModel(gateway)

        viewModel.loadBike("bike-1")
        viewModel.requestDelete("part-2")
        viewModel.confirmDelete()

        assertEquals(0, viewModel.uiState.value.archivedParts.size)
        assertEquals(null, viewModel.uiState.value.pendingDeletePart)
    }

    @Test
    fun submitCreateDefaultsBlankMileageToZero() = runBlocking {
        val gateway = FakePartLifecycleGateway(details = bikeDetails())
        val viewModel = PartFormViewModel(gateway)
        viewModel.startCreate("bike-1")
        viewModel.updateName("Tire")
        viewModel.updateRiddenMileage("")

        val success = viewModel.submit()

        assertTrue(success)
        assertEquals(0, gateway.lastAddedMileage)
        assertEquals("Tire", gateway.lastAddedName)
    }

    @Test
    fun submitEditConvertsKilometerInputToMeters() = runBlocking {
        val gateway =
            FakePartLifecycleGateway(
                details = bikeDetails(installedParts = listOf(part("part-1", "Cassette", riddenMileage = 4000))),
            )
        val viewModel = PartFormViewModel(gateway)
        viewModel.startEdit("bike-1", "part-1")
        assertEquals("4.0", viewModel.uiState.value.riddenMileageInput)

        viewModel.updateRiddenMileage("12.5")

        val success = viewModel.submit()

        assertTrue(success)
        assertEquals(12500, gateway.lastUpdatedMileage)
    }

    @Test
    fun startEditLoadsAlertConfiguration() = runBlocking {
        val gateway =
            FakePartLifecycleGateway(
                details =
                    bikeDetails(
                        installedParts =
                            listOf(
                                part(
                                    "part-1",
                                    "Cassette",
                                    curAlertMileage = 50000,
                                    targetAlertMileage = 250000,
                                    alertText = "Service cassette",
                                ),
                            ),
                    ),
            )
        val viewModel = PartFormViewModel(gateway)

        viewModel.startEdit("bike-1", "part-1")

        assertEquals("250", viewModel.uiState.value.targetAlertMileageInput)
        assertEquals("Service cassette", viewModel.uiState.value.alertText)
        assertEquals("Alert 50.0km / 250.0km", viewModel.uiState.value.alertButtonLabel)
    }

    @Test
    fun saveAlertConfigRejectsEmptyMileage() = runBlocking {
        val gateway = FakePartLifecycleGateway(details = bikeDetails(installedParts = listOf(part("part-1", "Cassette"))))
        val viewModel = PartFormViewModel(gateway)
        viewModel.startEdit("bike-1", "part-1")
        viewModel.showAlertDialog()
        viewModel.updateTargetAlertMileage("")

        val success = viewModel.saveAlertConfig()

        assertFalse(success)
        assertEquals("Target alert mileage is required", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun removeAlertClearsConfiguredAlert() = runBlocking {
        val gateway =
            FakePartLifecycleGateway(
                details =
                    bikeDetails(
                        installedParts =
                            listOf(
                                part(
                                    "part-1",
                                    "Cassette",
                                    curAlertMileage = 50000,
                                    targetAlertMileage = 250000,
                                    alertText = "Service cassette",
                                ),
                            ),
                    ),
            )
        val viewModel = PartFormViewModel(gateway)
        viewModel.startEdit("bike-1", "part-1")
        viewModel.showAlertDialog()

        val success = viewModel.removeAlert()

        assertTrue(success)
        assertEquals("", viewModel.uiState.value.targetAlertMileageInput)
        assertEquals("", viewModel.uiState.value.alertText)
        assertEquals("Alert 0.0km / 0.0km", viewModel.uiState.value.alertButtonLabel)
    }

    @Test
    fun submitEditPersistsAlertConfiguration() = runBlocking {
        val gateway = FakePartLifecycleGateway(details = bikeDetails(installedParts = listOf(part("part-1", "Cassette"))))
        val viewModel = PartFormViewModel(gateway)
        viewModel.startEdit("bike-1", "part-1")
        viewModel.updateTargetAlertMileage("300")
        viewModel.updateAlertText("Service cassette")

        val success = viewModel.submit()

        assertTrue(success)
        assertEquals(300, gateway.lastUpdatedTargetAlertMileage)
        assertEquals("Service cassette", gateway.lastUpdatedAlertText)
    }

    private fun bikeDetails(
        installedParts: List<Part> = emptyList(),
        archivedParts: List<Part> = emptyList(),
        rideCursor: RideCursor = RideCursor(),
    ): BikeDetails =
        BikeDetails(
            bike =
                Bike(
                    bikeId = "bike-1",
                    name = "Road",
                    karooMileageMeters = 2500,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            installedParts = installedParts,
            archivedParts = archivedParts,
            rideCursor = rideCursor,
        )

    private fun part(
        partId: String,
        name: String,
        riddenMileage: Int = 0,
        status: PartStatus = PartStatus.INSTALLED,
        curAlertMileage: Int = 0,
        targetAlertMileage: Int = 0,
        alertText: String? = null,
    ): Part =
        Part(
            partId = partId,
            name = name,
            riddenMileage = riddenMileage,
            status = status,
            createdAt = 1000L,
            createdDate = 1000L,
            curAlertMileage = curAlertMileage,
            targetAlertMileage = targetAlertMileage,
            alertText = alertText,
            updatedAt = 1000L,
            archivedAt = if (status == PartStatus.ARCHIVED) 1500L else null,
        )

    private class FakePartLifecycleGateway(
        details: BikeDetails,
    ) : PartLifecycleGateway {
        private var bikeDetails = details

        var lastAddedName: String? = null
            private set
        var lastAddedMileage: Int? = null
            private set
        var lastUpdatedMileage: Int? = null
            private set
        var lastUpdatedTargetAlertMileage: Int? = null
            private set
        var lastUpdatedAlertText: String? = null
            private set

        override suspend fun loadBikeDetails(bikeId: String): BikeDetails = bikeDetails

        override suspend fun getPart(
            bikeId: String,
            partId: String,
        ): Part? = (bikeDetails.installedParts + bikeDetails.archivedParts).firstOrNull { it.partId == partId }

        override suspend fun addPart(
            bikeId: String,
            name: String,
            riddenMileage: Int,
        ): BikeDetails {
            lastAddedName = name
            lastAddedMileage = riddenMileage
            bikeDetails =
                bikeDetails.copy(
                    installedParts =
                        bikeDetails.installedParts +
                            Part(
                                partId = "created",
                                name = name,
                                riddenMileage = riddenMileage,
                                status = PartStatus.INSTALLED,
                                createdAt = 1000L,
                                createdDate = 1000L,
                                updatedAt = 1000L,
                            ),
                )
            return bikeDetails
        }

        override suspend fun updatePart(
            bikeId: String,
            partId: String,
            name: String,
            riddenMileage: Int,
            targetAlertMileage: Int?,
            alertText: String?,
        ): BikeDetails {
            lastUpdatedMileage = riddenMileage
            lastUpdatedTargetAlertMileage = targetAlertMileage
            lastUpdatedAlertText = alertText
            bikeDetails =
                bikeDetails.copy(
                    installedParts =
                        bikeDetails.installedParts.map { part ->
                            if (part.partId == partId) {
                                part.copy(
                                    name = name,
                                    riddenMileage = riddenMileage,
                                    curAlertMileage = if (targetAlertMileage == null) 0 else part.curAlertMileage,
                                    targetAlertMileage = if (targetAlertMileage == null) 0 else targetAlertMileage * 1000,
                                    alertText = alertText,
                                )
                            } else {
                                part
                            }
                        },
                )
            return bikeDetails
        }

        override suspend fun archivePart(
            bikeId: String,
            partId: String,
        ): BikeDetails {
            val installed = bikeDetails.installedParts.first { it.partId == partId }
            bikeDetails =
                bikeDetails.copy(
                    installedParts = bikeDetails.installedParts.filterNot { it.partId == partId },
                    archivedParts = bikeDetails.archivedParts + installed.copy(status = PartStatus.ARCHIVED, archivedAt = 2000L),
                )
            return bikeDetails
        }

        override suspend fun deletePart(
            bikeId: String,
            partId: String,
        ): BikeDetails {
            bikeDetails =
                bikeDetails.copy(
                    archivedParts = bikeDetails.archivedParts.filterNot { it.partId == partId },
                )
            return bikeDetails
        }

        override suspend fun replacePart(
            bikeId: String,
            oldPartId: String,
            name: String,
            riddenMileage: Int,
        ): BikeDetails {
            val installed = bikeDetails.installedParts.first { it.partId == oldPartId }
            bikeDetails =
                bikeDetails.copy(
                    installedParts =
                        bikeDetails.installedParts.filterNot { it.partId == oldPartId } +
                            installed.copy(
                                partId = "replacement",
                                name = name,
                                riddenMileage = riddenMileage,
                                status = PartStatus.INSTALLED,
                                archivedAt = null,
                            ),
                    archivedParts = bikeDetails.archivedParts + installed.copy(status = PartStatus.ARCHIVED, archivedAt = 2000L),
                )
            return bikeDetails
        }
    }
}
