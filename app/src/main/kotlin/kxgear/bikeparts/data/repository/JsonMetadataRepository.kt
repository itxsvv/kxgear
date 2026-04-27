package kxgear.bikeparts.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kxgear.bikeparts.data.serialization.SharedMetadataDto
import kxgear.bikeparts.data.serialization.toDomain
import kxgear.bikeparts.data.serialization.toDto
import kxgear.bikeparts.data.storage.AtomicJsonFileStore
import kxgear.bikeparts.data.storage.RepositoryError
import kxgear.bikeparts.domain.model.SharedMetadata
import kxgear.bikeparts.domain.repository.MetadataRepository
import java.nio.file.Path

class JsonMetadataRepository(
    private val metadataPath: Path,
    private val json: Json,
    private val fileStore: AtomicJsonFileStore,
    private val dispatcher: CoroutineDispatcher,
) : MetadataRepository {
    override suspend fun read(): SharedMetadata =
        withContext(dispatcher) {
            fileStore.read(metadataPath)?.let(::decode) ?: SharedMetadata()
        }

    override suspend fun save(metadata: SharedMetadata) =
        withContext(dispatcher) {
            fileStore.write(
                metadataPath,
                json.encodeToString(SharedMetadataDto.serializer(), metadata.toDto()),
            )
        }

    private fun decode(content: String): SharedMetadata =
        try {
            json.decodeFromString(SharedMetadataDto.serializer(), content).toDomain()
        } catch (error: Exception) {
            throw RepositoryError.CorruptState("Failed to decode shared metadata JSON", error)
        }
}
