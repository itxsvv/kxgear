package kxgear.bikeparts.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kxgear.bikeparts.data.serialization.BikeFileDto
import kxgear.bikeparts.data.serialization.toDomain
import kxgear.bikeparts.data.serialization.toDto
import kxgear.bikeparts.data.storage.AtomicJsonFileStore
import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.model.BikeFile
import kxgear.bikeparts.domain.repository.BikeRepository
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries

class JsonBikeRepository(
    private val bikesDirectory: Path,
    private val json: Json,
    private val fileStore: AtomicJsonFileStore,
    private val dispatcher: CoroutineDispatcher,
) : BikeRepository {
    override suspend fun getBikeFile(bikeId: String): BikeFile? =
        withContext(dispatcher) {
            fileStore.read(bikePath(bikeId))
                ?.let { content ->
                    decode(content)
                }
        }

    override suspend fun listBikeFiles(): List<BikeFile> =
        withContext(dispatcher) {
            bikesDirectory.createDirectories()
            bikesDirectory.listDirectoryEntries("*.json")
                .sortedBy { it.fileName.toString() }
                .mapNotNull { path -> fileStore.read(path)?.let(::decode) }
        }

    override suspend fun saveBikeFile(bikeFile: BikeFile) =
        withContext(dispatcher) {
            fileStore.write(
                bikePath(bikeFile.bike.bikeId),
                json.encodeToString(BikeFileDto.serializer(), bikeFile.toDto()),
            )
        }

    override suspend fun deleteBikeFile(bikeId: String) =
        withContext(dispatcher) {
            fileStore.delete(bikePath(bikeId))
        }

    private fun bikePath(bikeId: String): Path = bikesDirectory.resolve("$bikeId.json")

    private fun decode(content: String): BikeFile =
        try {
            json.decodeFromString(BikeFileDto.serializer(), content).toDomain()
        } catch (error: Exception) {
            throw RepositoryError.CorruptState("Failed to decode bike file JSON", error)
        }
}
