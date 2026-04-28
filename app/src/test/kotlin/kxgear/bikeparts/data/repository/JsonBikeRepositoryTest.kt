package kxgear.bikeparts.data.repository

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kxgear.bikeparts.data.storage.AtomicJsonFileStore
import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.model.Bike
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.model.Part
import kxgear.bikeparts.domain.model.PartStatus
import kxgear.bikeparts.domain.model.RideCursor
import kxgear.bikeparts.domain.model.SharedMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonBikeRepositoryTest {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun saveAndReadBikeFileRoundTrips() = runBlocking {
        val root = Files.createTempDirectory("bike-repo-test")
        val repository = JsonBikeRepository(root, json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        val bikeFile = bikeFile()

        repository.saveBikeFile(bikeFile)
        val loaded = repository.getBikeFile("bike-1")

        requireNotNull(loaded)
        assertEquals("Road", loaded.bike.name)
        assertEquals(1000L, loaded.parts.single().createdDate)
        assertEquals(50000, loaded.parts.single().curAlertMileage)
        assertEquals(250000, loaded.parts.single().targetAlertMileage)
        assertEquals("Service chain", loaded.parts.single().alertText)
        assertEquals(1, repository.listBikeFiles().size)
    }

    @Test
    fun readBikeFileFallsBackCreatedDateToCreatedAt() = runBlocking {
        val root = Files.createTempDirectory("bike-repo-legacy-created-date")
        root.resolve("bike-1.json").writeText(
            """
            {
              "version": 1,
              "bike": {
                "bikeId": "bike-1",
                "name": "Road",
                "karooMileageMeters": 0,
                "createdAt": 1000,
                "updatedAt": 1000
              },
              "parts": [
                {
                  "partId": "part-1",
                  "name": "Chain",
                  "riddenMileage": 0,
                  "status": "INSTALLED",
                  "createdAt": 1234,
                  "updatedAt": 1234
                }
              ],
              "rideCursor": {},
              "lastUpdatedAt": 1000
            }
            """.trimIndent(),
        )
        val repository = JsonBikeRepository(root, json, AtomicJsonFileStore(), Dispatchers.Unconfined)

        val loaded = repository.getBikeFile("bike-1")

        requireNotNull(loaded)
        assertEquals(1234L, loaded.parts.single().createdDate)
    }

    @Test
    fun deleteBikeFileRemovesPersistedFile() = runBlocking {
        val root = Files.createTempDirectory("bike-repo-delete")
        val repository = JsonBikeRepository(root, json, AtomicJsonFileStore(), Dispatchers.Unconfined)
        repository.saveBikeFile(bikeFile())

        repository.deleteBikeFile("bike-1")

        assertTrue(repository.getBikeFile("bike-1") == null)
    }

    @Test
    fun malformedBikeFileThrowsCorruptState() = runBlocking {
        val root = Files.createTempDirectory("bike-repo-corrupt")
        root.resolve("bike-1.json").writeText("{not-valid-json")
        val repository = JsonBikeRepository(root, json, AtomicJsonFileStore(), Dispatchers.Unconfined)

        val error = runCatching { repository.getBikeFile("bike-1") }.exceptionOrNull()

        assertEquals(RepositoryError.CorruptState::class.java, error?.javaClass)
    }

    @Test
    fun metadataRepositoryPersistsSharedMetadata() = runBlocking {
        val root = Files.createTempDirectory("metadata-repo")
        val repository = JsonMetadataRepository(
            metadataPath = root.resolve("shared-metadata.json"),
            json = json,
            fileStore = AtomicJsonFileStore(),
            dispatcher = Dispatchers.Unconfined,
        )
        val metadata = SharedMetadata(activeBikeId = "bike-1")

        repository.save(metadata)
        val loaded = repository.read()

        assertEquals("bike-1", loaded.activeBikeId)
    }

    private fun bikeFile(): BikeFile =
        BikeFile(
            bike = Bike(
                bikeId = "bike-1",
                name = "Road",
                createdAt = 1000,
                updatedAt = 1000,
            ),
            parts = listOf(
                Part(
                    partId = "part-1",
                    name = "Chain",
                    riddenMileage = 0,
                    status = PartStatus.INSTALLED,
                    createdAt = 1000,
                    createdDate = 1000,
                    curAlertMileage = 50000,
                    targetAlertMileage = 250000,
                    alertText = "Service chain",
                    updatedAt = 1000,
                ),
            ),
            rideCursor = RideCursor(),
            lastUpdatedAt = 1000,
        )
}
