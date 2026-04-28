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
    val curAlertMileage: Int = 0,
    val targetAlertMileage: Int = 0,
    val alertText: String? = null,
    val updatedAt: Long,
    val archivedAt: Long? = null,
) {
    val currentMileage: Int
        get() = riddenMileage
}
