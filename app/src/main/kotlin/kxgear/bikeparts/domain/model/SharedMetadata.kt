package kxgear.bikeparts.domain.model

data class BikeSummary(
    val bikeId: String,
    val name: String,
    val mileageMeters: Int = 0,
    val hasKarooBikeId: Boolean = false,
)

data class SharedMetadata(
    val activeBikeId: String? = null,
    val bikeIndex: List<BikeSummary> = emptyList(),
)
