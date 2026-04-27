package kxgear.bikeparts.data.serialization

import kotlinx.serialization.Serializable
import kxgear.bikeparts.domain.model.BikeSummary
import kxgear.bikeparts.domain.model.SharedMetadata

@Serializable
data class BikeSummaryDto(
    val bikeId: String,
    val name: String,
    val mileageMeters: Int = 0,
)

@Serializable
data class SharedMetadataDto(
    val activeBikeId: String? = null,
    val bikeIndex: List<BikeSummaryDto> = emptyList(),
)

fun SharedMetadata.toDto(): SharedMetadataDto =
    SharedMetadataDto(
        activeBikeId = activeBikeId,
        bikeIndex = bikeIndex.map { BikeSummaryDto(it.bikeId, it.name, it.mileageMeters) },
    )

fun SharedMetadataDto.toDomain(): SharedMetadata =
    SharedMetadata(
        activeBikeId = activeBikeId,
        bikeIndex = bikeIndex.map { BikeSummary(it.bikeId, it.name, it.mileageMeters) },
    )
