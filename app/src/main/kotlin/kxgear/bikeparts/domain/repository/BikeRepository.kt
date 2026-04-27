package kxgear.bikeparts.domain.repository

import kxgear.bikeparts.domain.model.BikeFile

interface BikeRepository {
    suspend fun getBikeFile(bikeId: String): BikeFile?
    suspend fun listBikeFiles(): List<BikeFile>
    suspend fun saveBikeFile(bikeFile: BikeFile)
    suspend fun deleteBikeFile(bikeId: String)
}
