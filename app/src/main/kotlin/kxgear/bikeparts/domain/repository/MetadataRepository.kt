package kxgear.bikeparts.domain.repository

import kxgear.bikeparts.domain.model.SharedMetadata

interface MetadataRepository {
    suspend fun read(): SharedMetadata
    suspend fun save(metadata: SharedMetadata)
}
