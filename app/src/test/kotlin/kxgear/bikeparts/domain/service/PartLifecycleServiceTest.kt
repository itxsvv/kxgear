package kxgear.bikeparts.domain.service

import kotlinx.coroutines.runBlocking
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.repository.BikeRepository
import kxgear.bikeparts.integration.logging.BikePartsLogger
import org.junit.Assert.assertEquals
import org.junit.Test

class PartLifecycleServiceTest {
    private val logger = BikePartsLogger()

    @Test
    fun addPartAllowsDuplicateNamesWithinBike() = runBlocking {
        val repository = InMemoryBikeRepository(bikeFile(parts = listOf(part(partId = "part-1", name = "Chain"))))
        val service = createService(repository)

        val details = service.addPart("bike-1", "chain", 0)

        assertEquals(listOf("chain", "Chain"), details.installedParts.map { it.name })
        assertEquals(2000L, details.installedParts.first().createdDate)
    }

    @Test
    fun updatePartChangesNameAndRiddenMileage() = runBlocking {
        val repository =
            InMemoryBikeRepository(
                bikeFile(
                    parts =
                        listOf(
                            part(
                                partId = "part-1",
                                name = "Cassette",
                                riddenMileage = 34,
                            ),
                        ),
                ),
            )
        val service = createService(repository)

        val details = service.updatePart("bike-1", "part-1", "Cassette", 50, null, null)
        val updated = details.installedParts.single()

        assertEquals(50, updated.riddenMileage)
        assertEquals(50, updated.currentMileage)
    }

    @Test
    fun updatePartPersistsAlertConfiguration() = runBlocking {
        val repository = InMemoryBikeRepository(bikeFile(parts = listOf(part(partId = "part-1", name = "Chain"))))
        val service = createService(repository)

        val details = service.updatePart("bike-1", "part-1", "Chain", 0, 250, "Service chain")

        assertEquals(250000, details.installedParts.single().targetAlertMileage)
        assertEquals(0, details.installedParts.single().curAlertMileage)
        assertEquals("Service chain", details.installedParts.single().alertText)
        assertEquals(250000, repository.requireBike("bike-1").parts.single().targetAlertMileage)
    }

    @Test
    fun updatePartResetsAlertProgressWhenTargetChanges() = runBlocking {
        val repository =
            InMemoryBikeRepository(
                bikeFile(
                    parts =
                        listOf(
                            part(
                                partId = "part-1",
                                name = "Chain",
                                curAlertMileage = 50000,
                                targetAlertMileage = 250000,
                            ),
                        ),
                ),
            )
        val service = createService(repository)

        val details = service.updatePart("bike-1", "part-1", "Chain", 0, 500, "Service chain")

        assertEquals(0, details.installedParts.single().curAlertMileage)
        assertEquals(500000, details.installedParts.single().targetAlertMileage)
    }

    @Test
    fun loadBikeDetailsSortsPartsByAddedDateDescending() = runBlocking {
        val repository =
            InMemoryBikeRepository(
                bikeFile(
                    parts =
                        listOf(
                            part(partId = "part-1", name = "Older", createdDate = 1000L),
                            part(partId = "part-2", name = "Newest", createdDate = 3000L),
                            part(partId = "part-3", name = "Middle", createdDate = 2000L, status = PartStatus.ARCHIVED),
                            part(partId = "part-4", name = "Archived Newest", createdDate = 4000L, status = PartStatus.ARCHIVED),
                        ),
                ),
            )
        val service = createService(repository)

        val details = service.loadBikeDetails("bike-1")

        assertEquals(listOf("part-2", "part-1"), details.installedParts.map { it.partId })
        assertEquals(listOf("part-4", "part-3"), details.archivedParts.map { it.partId })
    }

    @Test
    fun archivePartMovesInstalledPartWithoutReplacement() = runBlocking {
        val repository = InMemoryBikeRepository(bikeFile(parts = listOf(part(partId = "part-1", name = "Tire"))))
        val service = createService(repository)

        val details = service.archivePart("bike-1", "part-1")

        assertEquals(0, details.installedParts.size)
        assertEquals(1, details.archivedParts.size)
        assertEquals(PartStatus.ARCHIVED, repository.requireBike("bike-1").parts.single().status)
    }

    @Test
    fun deletePartRemovesArchivedPart() = runBlocking {
        val repository =
            InMemoryBikeRepository(
                bikeFile(parts = listOf(part(partId = "part-1", name = "Old Tire", status = PartStatus.ARCHIVED))),
            )
        val service = createService(repository)

        val details = service.deletePart("bike-1", "part-1")

        assertEquals(0, details.archivedParts.size)
        assertEquals(0, repository.requireBike("bike-1").parts.size)
    }

    @Test
    fun replacePartAllowsReusingArchivedPartName() = runBlocking {
        val repository =
            InMemoryBikeRepository(
                bikeFile(parts = listOf(part(partId = "part-1", name = "Chain", riddenMileage = 25))),
            )
        val service = createService(repository)

        val details = service.replacePart("bike-1", "part-1", "Chain", 0)

        assertEquals(1, details.installedParts.size)
        assertEquals("Chain", details.installedParts.single().name)
        assertEquals(2000L, details.installedParts.single().createdDate)
        assertEquals(1, details.archivedParts.size)
        assertEquals("Chain", details.archivedParts.single().name)
        assertEquals(1000L, details.archivedParts.single().createdDate)
        assertEquals(PartStatus.ARCHIVED, details.archivedParts.single().status)
    }

    private fun createService(repository: InMemoryBikeRepository): PartLifecycleService =
        PartLifecycleService(
            bikeRepository = repository,
            bikePartsService = BikePartsService(),
            logger = logger,
            idProvider = { "created-part" },
            clock = { 2000L },
        )

    private fun bikeFile(parts: List<Part> = emptyList()): BikeFile =
        BikeFile(
            bike =
                Bike(
                    bikeId = "bike-1",
                    name = "Road",
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            parts = parts,
            lastUpdatedAt = 1000L,
        )

    private fun part(
        partId: String,
        name: String,
        riddenMileage: Int = 0,
        status: PartStatus = PartStatus.INSTALLED,
        createdDate: Long = 1000L,
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
            createdDate = createdDate,
            curAlertMileage = curAlertMileage,
            targetAlertMileage = targetAlertMileage,
            alertText = alertText,
            updatedAt = 1000L,
        )

    private class InMemoryBikeRepository(
        vararg bikeFiles: BikeFile,
    ) : BikeRepository {
        private val bikes = linkedMapOf<String, BikeFile>()

        init {
            bikeFiles.forEach { bikes[it.bike.bikeId] = it }
        }

        override suspend fun getBikeFile(bikeId: String): BikeFile? = bikes[bikeId]

        override suspend fun listBikeFiles(): List<BikeFile> = bikes.values.toList()

        override suspend fun saveBikeFile(bikeFile: BikeFile) {
            bikes[bikeFile.bike.bikeId] = bikeFile
        }

        override suspend fun deleteBikeFile(bikeId: String) {
            bikes.remove(bikeId)
        }

        fun requireBike(bikeId: String): BikeFile = checkNotNull(bikes[bikeId])
    }
}
