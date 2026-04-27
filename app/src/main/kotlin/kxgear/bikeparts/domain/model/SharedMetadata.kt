package kxgear.bikeparts.domain.model

data class BikeSummary(
    val bikeId: String,
    val name: String,
    val mileageMeters: Int = 0,
)

data class SharedMetadata(
    val activeBikeId: String? = null,
    val bikeIndex: List<BikeSummary> = emptyList(),
)
