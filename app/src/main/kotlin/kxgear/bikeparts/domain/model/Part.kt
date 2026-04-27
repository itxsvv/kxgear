package kxgear.bikeparts.domain.model

enum class PartStatus {
    INSTALLED,
    ARCHIVED,
}

data class Part(
    val partId: String,
    val name: String,
    val riddenMileage: Int,
    val status: PartStatus,
    val createdAt: Long,
    val createdDate: Long = createdAt,
    val alertMileage: Int? = null,
    val alertText: String? = null,
    val lastAlertThresholdMeters: Int? = null,
    val updatedAt: Long,
    val archivedAt: Long? = null,
) {
    val currentMileage: Int
        get() = riddenMileage
}
