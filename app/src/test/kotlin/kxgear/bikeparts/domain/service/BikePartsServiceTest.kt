package kxgear.bikeparts.domain.service

import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.model.RideCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BikePartsServiceTest {
    private val service = BikePartsService()

    @Test
    fun currentMileageUsesRiddenMileage() {
        val part = part(riddenMileage = 150)

        assertEquals(150, service.currentMileage(part))
    }

    @Test
    fun applyRideUpdateIncrementsInstalledPartsOnly() {
        val bikeFile = bikeFile(
            bikeMileageMeters = 1000,
            parts = listOf(
                part(partId = "installed", riddenMileage = 10, status = PartStatus.INSTALLED),
                part(partId = "archived", riddenMileage = 20, status = PartStatus.ARCHIVED),
            ),
            rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000),
        )

        val updated = service.applyRideUpdate(bikeFile, metricValue = 130, recordedAt = 2000).bikeFile

        assertEquals(1030, updated.bike.karooMileageMeters)
        assertEquals(2000, updated.bike.updatedAt)
        assertEquals(40, updated.parts.first { it.partId == "installed" }.riddenMileage)
        assertEquals(20, updated.parts.first { it.partId == "archived" }.riddenMileage)
        assertEquals(130, updated.rideCursor.lastAcceptedMetricValue)
    }

    @Test
    fun applyRideUpdateRejectsDecreasingDistance() {
        val bikeFile = bikeFile(rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000))

        val error = runCatching {
            service.applyRideUpdate(bikeFile, metricValue = 90, recordedAt = 2000)
        }.exceptionOrNull()

        assertEquals(RepositoryError.Validation::class.java, error?.javaClass)
    }

    @Test
    fun applyRideUpdateTreatsDuplicateDistanceAsNoOpForMileage() {
        val bikeFile = bikeFile(
            parts = listOf(part(riddenMileage = 50)),
            rideCursor = RideCursor(lastAcceptedMetricValue = 100, lastAcceptedAt = 1000),
        )

        val updated = service.applyRideUpdate(bikeFile, metricValue = 100, recordedAt = 3000).bikeFile

        assertEquals(0, updated.bike.karooMileageMeters)
        assertEquals(1000L, updated.bike.updatedAt)
        assertEquals(50, updated.parts.single().riddenMileage)
        assertEquals(100, updated.rideCursor.lastAcceptedMetricValue)
        assertEquals(1000L, updated.rideCursor.lastAcceptedAt)
        assertEquals(1000L, updated.lastUpdatedAt)
    }

    @Test
    fun deleteBikeClearsActiveSelectionAndIndexEntry() {
        val updated = service.deleteBike(
            metadata = kxgear.bikeparts.domain.model.SharedMetadata(
                activeBikeId = "bike-1",
                bikeIndex = listOf(
                    kxgear.bikeparts.domain.model.BikeSummary("bike-1", "Road"),
                    kxgear.bikeparts.domain.model.BikeSummary("bike-2", "Gravel"),
                ),
            ),
            bikeId = "bike-1",
        )

        assertNull(updated.activeBikeId)
        assertEquals(listOf("bike-2"), updated.bikeIndex.map { it.bikeId })
    }

    @Test
    fun replacePartArchivesOldPartAndAddsNewOne() {
        val bikeFile = bikeFile(parts = listOf(part(partId = "old", name = "Chain")))

        val updated = service.replacePart(
            bikeFile = bikeFile,
            oldPartId = "old",
            newPart = part(partId = "new", name = "Cassette"),
            replacedAt = 4000,
        )

        assertEquals(2, updated.parts.size)
        assertEquals(PartStatus.ARCHIVED, updated.parts.first { it.partId == "old" }.status)
        assertEquals(PartStatus.INSTALLED, updated.parts.first { it.partId == "new" }.status)
    }

    private fun bikeFile(
        bikeMileageMeters: Int = 0,
        parts: List<Part> = listOf(part()),
        rideCursor: RideCursor = RideCursor(),
    ): BikeFile =
        BikeFile(
            bike = Bike(
                bikeId = "bike-1",
                name = "Road",
                karooMileageMeters = bikeMileageMeters,
                createdAt = 1000,
                updatedAt = 1000,
            ),
            parts = parts,
            rideCursor = rideCursor,
            lastUpdatedAt = 1000,
        )

    private fun part(
        partId: String = "part-1",
        name: String = "Chain",
        riddenMileage: Int = 0,
        status: PartStatus = PartStatus.INSTALLED,
    ): Part =
        Part(
            partId = partId,
            name = name,
            riddenMileage = riddenMileage,
            status = status,
            createdAt = 1000,
            updatedAt = 1000,
            archivedAt = null,
        )
}
