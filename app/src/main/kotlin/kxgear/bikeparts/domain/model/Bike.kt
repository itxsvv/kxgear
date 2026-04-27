package kxgear.bikeparts.domain.model

data class Bike(
    val bikeId: String,
    val karooBikeId: String? = null,
    val name: String,
    val karooMileageMeters: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
